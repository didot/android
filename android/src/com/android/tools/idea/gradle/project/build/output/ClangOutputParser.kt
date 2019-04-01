/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.build.output

import com.android.tools.idea.gradle.project.build.output.ClangDiagnosticClass.Companion.fromTag
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Consumer

/** See https://clang.llvm.org/docs/UsersManual.html#diagnostic-mappings for possible diagnostic classes by Clang. */
private enum class ClangDiagnosticClass(val tag: String) {
  IGNORED("ignored"), NOTE("note"), REMARK("remark"), WARNING("warning"), ERROR("error"), FATAL("fatal error");

  fun toMessageEventKind() = when (this) {
    NOTE, REMARK, IGNORED -> MessageEvent.Kind.INFO
    WARNING -> MessageEvent.Kind.WARNING
    ERROR, FATAL -> MessageEvent.Kind.ERROR
  }

  companion object {
    private val tagMap = values().associateBy { it.tag }
    fun fromTag(tag: String) = tagMap.getValue(tag)
  }
}

/**
 * Pattern matching Gradle output indicating the start of a native build task. For example
 *
 * > Task :app:externalNativeBuildDebug
 */
private val nativeBuildTaskPattern = Regex("> Task (:[^:]+)*:externalNativeBuild([^ ]+)(?: [-A-Z]+)?")

/**
 * Pattern matching a line output by ninja when it changes the working directory.
 */
private val ninjaEnterDirectoryPattern = Regex("ninja: Entering directory `([^']+)'")

/**
 * Pattern matching a line output by NDK build which declares what it is about to do.
 */
private val ndkBuildAbiAnnouncementPattern = Regex("\\[(arm64-v8a|armeabi-v7a|x86|x86_64)] .+")

/**
 * Regex matching the diagnostic line from Clang compiler. For example
 *
 * ```
 * ../path/to/source.cc:42:5: warning: My cat is not happy.
 * ```
 *
 * The requirement of a non-space start character is to skip output from Gradle repeating compiler output on errors, which are always
 * indented by two spaces.
 */
private val diagnosticMessagePattern = Regex(
  "((?:[A-Z]:)?[^\\s][^:]+):(\\d+):(\\d+): (${ClangDiagnosticClass.values().joinToString("|") { it.tag }}): (.*)")
/**
 * For files included by another file, clang outputs the including files. For example, assuming common.h is included by feature.h, which
 * is further included by feature.cpp. Clang would print:
 *
 * ```
 * In file included from /path/to/feature.cpp:1:
 * In file included from /path/to/feature.h:2:
 * /path/to/common.h:3:4: error: something is wrong
 * ```
 */
private val fileInclusionPattern = Regex("In file included from (.+):(\\d+):")

private val linkerErrorPattern = Regex("clang(\\+\\+)?(\\.exe)?: error: linker command failed with exit code 1.*")
private val linkerErrorDiagnosticPattern = Regex(
  "((?:[A-Z]:)?[^\\s][^:]+)(?::(\\d+))?: (${ClangDiagnosticClass.values().joinToString("|") { it.tag }})?: (.+)")

private const val COMPILER_MESSAGES_GROUP_PREFIX = "Clang Compiler"
fun compilerMessageGroup(gradleProject: String, variant: String, abi: String?) =
  "$COMPILER_MESSAGES_GROUP_PREFIX [${listOfNotNull(gradleProject, variant, abi).joinToString(" ")}]"

/** Parser that parses Clang output and emit [BuildEvent] indicating with compiler diagnostic messages. */
class ClangOutputParser : BuildOutputParser {
  /**
   * Parses an build output while it's being streamed from external build systems.
   *
   * @param ignored the most recent line acquired from the passed in [BuildOutputInstantReader]
   * @param reader a reader that is useful to actively consuming more build output or peek previous output. This can be used by parsers that
   * needs more than the current line to work. Also note that all state changes made to the reader will affect other parsers. That is, if
   * this parser reads several lines from the reader without reset the reader's state, other parsers won't be able to read such consumed
   * states. This is useful if the parser knows other parsers won't be interested in the consumed build outputs.
   * @param messageConsumer consumer of build events emitted by this parser. For example, upon encountering a syntax error in a source code
   * file, this parser can emit a [FileMessageEventImpl] so that the IDE will show a corresponding entry in the 'Build Output' UI.
   */
  override fun parse(ignored: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    val previousLine = reader.peekPrevious() ?: return false
    val nativeBuildTaskMatch = nativeBuildTaskPattern.matchEntire(previousLine) ?: return false
    val (gradleProject, variant) = nativeBuildTaskMatch.capturedRegexGroupValues

    var workingDir: Path? = null
    var abi: String? = null
    /** Resolves a string path against the working directory, if available. */
    fun resolveAgainstWorkingDir(s: String): Path {
      val path = workingDir?.resolve(s) ?: Paths.get(s)
      try {
        return path.toRealPath()
      }
      catch (e: IOException) {
        // If the file cannot be accessed, we fallback to heuristic based path normalization to provide a more readable path.
        return path.normalize()
      }
    }

    // Track where the file referenced by the current line is included. This is needed since Clang only output the inclusion once per
    // file even if there are multiple diagnostic messages for that file. A map is used because NDK output interleaved results. This
    // would not solve the interleaving problem, we are just trying to provide as much context as we can.
    val inclusionContextMap = mutableMapOf<Path, List<LineInFile>>().withDefault { emptyList() }
    val currentInclusion = mutableListOf<LineInFile>()
    reader.pushBack() // Push back so that reader.readLine() in the loop can process the current line as well.
    var isUsingCmake = false
    while (true) {
      val line = reader.readLine()?.trimEnd() ?: return true
      if (nativeBuildStopped(line)) {
        // Push back because this line contains message about a gradle task and it should not be consumed by this parser. We want to
        // given other parsers an opportunity to parse it.
        reader.pushBack()
        return true // return true to indicate all lines before the gradle task line should be consumed by this parser.
      }

      val ninjaEnteringDirectoryMatch = ninjaEnterDirectoryPattern.matchEntire(line)
      if (ninjaEnteringDirectoryMatch != null) {
        val workingDirString = ninjaEnteringDirectoryMatch.capturedRegexGroupValues[0]
        workingDir = Paths.get(workingDirString)
        abi = workingDir?.fileName.toString()
        isUsingCmake = true
        continue
      }

      if (!isUsingCmake) {
        val ndkBuildAbiAnnouncementMatch = ndkBuildAbiAnnouncementPattern.matchEntire(line)
        if (ndkBuildAbiAnnouncementMatch != null) {
          abi = ndkBuildAbiAnnouncementMatch.capturedRegexGroupValues[0]
          continue
        }
      }

      val fileInclusionMatch = fileInclusionPattern.matchEntire(line)
      if (fileInclusionMatch != null) {
        val (pathString, lineString) = fileInclusionMatch.capturedRegexGroupValues
        currentInclusion.add(LineInFile(resolveAgainstWorkingDir(pathString), lineString.toInt()))
        continue
      }

      val diagnosticMessageMatch = diagnosticMessagePattern.matchEntire(line)
      val compilerMessageGroup = compilerMessageGroup(gradleProject, variant, abi)
      if (diagnosticMessageMatch != null) {
        val (pathString, lineString, colString, diagnosticClassString, diagnosticMessage) = diagnosticMessageMatch.capturedRegexGroupValues
        val path = resolveAgainstWorkingDir(pathString)
        if (!currentInclusion.isEmpty()) {
          inclusionContextMap[path] = currentInclusion.toList()
          currentInclusion.clear()
        }
        val lineNumber = lineString.toInt()
        val colNumber = colString.toInt()
        val diagnosticClass = ClangDiagnosticClass.fromTag(diagnosticClassString)
        val detailedMessage = inclusionContextMap.getValue(path).let { inclusionContext ->
          val fileMessage = "$path:$lineNumber:$colNumber: ${diagnosticClass.tag}: $diagnosticMessage"
          val inclusionContextMessage = if (inclusionContext.isEmpty()) {
            ""
          }
          else {
            "\n\nThis file is included from the following inclusion chain:\n" +
            inclusionContext.joinToString("") { "$it\n" }
          }
          fileMessage + inclusionContextMessage
        }
        messageConsumer.accept(
          FileMessageEventImpl(reader.buildId,
                               diagnosticClass.toMessageEventKind(),
                               compilerMessageGroup,
                               diagnosticMessage,
                               detailedMessage,
                               FilePosition(path.toFile(), lineNumber - 1, colNumber - 1)))
      }

      linkerErrorPattern.matchEntire(line) ?: continue
      val linkerErrorLine = reader.peekPrevious() ?: continue
      val (pathString, optionalLineNumber, optionalDiagnosticClassString, message) = linkerErrorLine.let {
        linkerErrorDiagnosticPattern.matchEntire(it)?.capturedOptionalRegexGroupValues
      } ?: continue
      val path = resolveAgainstWorkingDir(pathString!!)
      val lineNumber = optionalLineNumber?.toInt()
      val diagnosticClass = optionalDiagnosticClassString?.let(::fromTag) ?: ClangDiagnosticClass.ERROR
      val diagnosticMessage = message!!
      if (lineNumber != null) {
        messageConsumer.accept(
          FileMessageEventImpl(reader.buildId,
                               diagnosticClass.toMessageEventKind(),
                               compilerMessageGroup,
                               diagnosticMessage,
                               linkerErrorLine,
                               FilePosition(path.toFile(), lineNumber - 1, 0)))
      }
      else {
        messageConsumer.accept(
          MessageEventImpl(reader.buildId,
                           diagnosticClass.toMessageEventKind(),
                           compilerMessageGroup,
                           diagnosticMessage,
                           linkerErrorLine))
      }
    }
  }

  /**
   * Uses some heuristics to determine if native build has stopped. Specifically, this method does so checking if a another gradle task
   * has started.
   */
  private fun nativeBuildStopped(line: String): Boolean = line.startsWith("> Task ") || line == "FAILURE: Build failed with an exception."

  /** Simple data class representing a line in a file. */
  private data class LineInFile(val path: Path, val lineNumber: Int) {
    override fun toString() = "$path:$lineNumber"
  }
}

private fun BuildOutputInstantReader.peekPrevious(): String? {
  pushBack()
  try {
    return currentLine?.trimEnd()
  }
  catch (e: IndexOutOfBoundsException) {
    // It's sad that BuildOutputInstantReader does not have an API to test if there is a previous line.
    return null
  }
  finally {
    readLine() // advance the reader to get it back to where it was
  }
}

private val MatchResult.capturedRegexGroupValues: List<String>
  get() = capturedOptionalRegexGroupValues.map { it!! }

private val MatchResult.capturedOptionalRegexGroupValues: List<String?>
  get() = groups.toList()
    .drop(1) // Drop first group, which is the entire match.
    .map { it?.value }
