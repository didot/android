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

import com.android.tools.idea.util.oneOf
import com.android.tools.idea.util.plus
import com.android.tools.idea.util.repeated
import com.android.tools.idea.util.someInt
import com.android.tools.idea.util.useGenerated
import com.google.common.truth.Truth
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import kotlin.random.Random

class GenericFileFilterTest {

  private val localFileSystem = mock(LocalFileSystem::class.java)
  private val project = mock(Project::class.java)
  private val filter = GenericFileFilter(project, localFileSystem)

  @Before
  fun setUp() {
    `when`(localFileSystem.findFileByPathIfCached(ArgumentMatchers.anyString())).thenAnswer { invocation ->
      val pathString = invocation.arguments.single() as String
      mock(VirtualFile::class.java).apply {
        `when`(this.path).thenReturn(pathString)
      }
    }
  }

  @Test
  fun `recognize simple Linux path`() = getFilterResultAndCheckHighlightPositions("""
    | /path/to/file
      ^^^^^^^^^^^^^
  """.trimIndent())
    .checkFileLinks("/path/to/file")

  @Test
  fun `recognize simple Windows path`() = getFilterResultAndCheckHighlightPositions("""
    | C:\path\to\file
      ^^^^^^^^^^^^^^^
  """.trimIndent())
    .checkFileLinks("""C:\path\to\file""")

  @Test
  fun `recognize simple Windows path with forward slashes`() = getFilterResultAndCheckHighlightPositions("""
    | C:/path/to/file
      ^^^^^^^^^^^^^^^
  """.trimIndent())
    .checkFileLinks("""C:/path/to/file""")

  @Test
  fun `recognize path with line number`() = getFilterResultAndCheckHighlightPositions("""
    | /path/to/file.c:3
      ^^^^^^^^^^^^^^^^^
  """.trimIndent())
    .checkFileLinks("/path/to/file.c")

  @Test
  fun `recognize path with line number and column`() = getFilterResultAndCheckHighlightPositions("""
    | /path/to/file.c:3:7
      ^^^^^^^^^^^^^^^^^^^
  """.trimIndent())
    .checkFileLinks("/path/to/file.c")

  @Test
  fun `recognize path in middle of a line`() = getFilterResultAndCheckHighlightPositions("""
    | blah blah /path/to/file blah blah
                ^^^^^^^^^^^^^
  """.trimIndent())
    .checkFileLinks("/path/to/file")

  @Test
  fun `recognize multiple paths in a line`() = getFilterResultAndCheckHighlightPositions("""
    | blah blah /path/to/file blah blah /another/path/to/file
                ^^^^^^^^^^^^^           ^^^^^^^^^^^^^^^^^^^^^
  """.trimIndent())
    .checkFileLinks(
      "/path/to/file",
      "/another/path/to/file"
    )

  @Test
  fun `multiple lines and multiple links`() = getFilterResultAndCheckHighlightPositions("""
    | blah blah /path/to/file1 blah blah /path/to/file2:3.
                ^^^^^^^^^^^^^^           ^^^^^^^^^^^^^^^^
    | blah blah blah /path/to/file3:1:2: error: blah blah C:\path\to\file4
                     ^^^^^^^^^^^^^^^^^^                   ^^^^^^^^^^^^^^^^
  """.trimIndent())
    .checkFileLinks(
      "/path/to/file1",
      "/path/to/file2",
      "/path/to/file3",
      """C:\path\to\file4"""
    )

  @Test
  fun `skip files not cached by local file system`() {
    `when`(localFileSystem.findFileByPathIfCached("/path/to/uncached/file")).thenReturn(null)
    getFilterResultAndCheckHighlightPositions("""
      | /path/to/cached/file and /path/to/uncached/file
        ^^^^^^^^^^^^^^^^^^^^
    """.trimIndent())
      .checkFileLinks(
        "/path/to/cached/file"
      )
  }

  @Test
  fun `real world case 1`() {
    `when`(localFileSystem.findFileByPathIfCached(ArgumentMatchers.argThat {
      it !in setOf(
        "/usr/local/google/home/tgeng/x/test-projects/SimpleJni1/app/src/main/cpp/native-lib.cpp",
        "/usr/local/google/home/tgeng/Android/Sdk/ndk/19.2.5345600/toolchains/llvm/prebuilt/linux-x86_64"
      )
    })).thenReturn(null)
    getFilterResultAndCheckHighlightPositions("""
      | FAILURE: Build failed with an exception.
      |
      | * What went wrong:
      | Execution failed for task ':app:externalNativeBuildDebug'.
      | > Build command failed.
      |   Error while executing process /usr/local/google/home/tgeng/Android/Sdk/cmake/3.10.2.4988404/bin/ninja with arguments
      |     {-C /usr/local/google/home/tgeng/x/test-projects/SimpleJni1/app/.cxx/cmake/debug/armeabi-v7a native-lib}
      |   ninja: Entering directory `/usr/local/google/home/tgeng/x/test-projects/SimpleJni1/app/.cxx/cmake/debug/armeabi-v7a'
      |   [1/2] Building CXX object CMakeFiles/native-lib.dir/native-lib.cpp.o
      |   FAILED: CMakeFiles/native-lib.dir/native-lib.cpp.o
      |   /usr/local/google/home/tgeng/Android/Sdk/ndk/19.2.5345600/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++
      |     --target=armv7-none-linux-androideabi19
      |     --gcc-toolchain=/usr/local/google/home/tgeng/Android/Sdk/ndk/19.2.5345600/toolchains/llvm/prebuilt/linux-x86_64
                            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
      |     ... -c /usr/local/google/home/tgeng/x/test-projects/SimpleJni1/app/src/main/cpp/native-lib.cpp
                   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
      |   /usr/local/google/home/tgeng/x/test-projects/SimpleJni1/app/src/main/cpp/native-lib.cpp:21:5: error: use of undeclared identifier 'yoo'
      |   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
      |       yoo;
      |       ^
      | 1 error generated.
      | ninja: build stopped: subcommand failed.
    """.trimIndent())
      .checkFileLinks(
        "/usr/local/google/home/tgeng/Android/Sdk/ndk/19.2.5345600/toolchains/llvm/prebuilt/linux-x86_64",
        "/usr/local/google/home/tgeng/x/test-projects/SimpleJni1/app/src/main/cpp/native-lib.cpp",
        "/usr/local/google/home/tgeng/x/test-projects/SimpleJni1/app/src/main/cpp/native-lib.cpp"
      )
  }

  @Test
  fun `real world case 2`() {
    getFilterResultAndCheckHighlightPositions("""
    | CMake Error at /Users/jomof/projects/GunBox/GunBox/Sources/Engine/CMakeLists.txt:156 (include):
                     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    |   include could not find load file:
    |
    |     /Users/jomof/projects/GunBox/GunBox/ExternalLibraries/__cmake/ExternalLibraries.cmake
          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    |
    | CMake Error at /Users/jomof/projects/GunBox/GunBox/Sources/Engine/CMakeLists.txt:156 (include):
                     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    |   include could not find load file:
    """.trimIndent())
      .checkFileLinks(
        "/Users/jomof/projects/GunBox/GunBox/Sources/Engine/CMakeLists.txt",
        "/Users/jomof/projects/GunBox/GunBox/ExternalLibraries/__cmake/ExternalLibraries.cmake",
        "/Users/jomof/projects/GunBox/GunBox/Sources/Engine/CMakeLists.txt"
      )
  }

  @Test
  fun `fuzz test`() {
    val allGeneratedPaths = mutableListOf<String>()

    val wordGen = oneOf(('a'..'z').asIterable() + ".-_()[]吃葡萄不吐葡萄皮".asIterable()).repeated(3..8)
    val linuxPathGen = ('/' + wordGen).repeated(2..10)
      .useGenerated { allGeneratedPaths += it }
    val windowsPathGen = (oneOf('A'..'Z') + ":" + ('\\' + wordGen).repeated(2..10))
      .useGenerated { allGeneratedPaths += it }
    val fileNumberGen = ':' + someInt()
    val pathWithLineNumberGen = oneOf(linuxPathGen, windowsPathGen) + fileNumberGen
    val pathWithLineAndColumnNumberGen = pathWithLineNumberGen + fileNumberGen
    val sentenceGen = oneOf(
      wordGen.repeated(1..10, " "),
      oneOf(linuxPathGen, windowsPathGen, pathWithLineNumberGen, pathWithLineAndColumnNumberGen)
    ).repeated(1..5, " ")
    val paragraphGen = sentenceGen.repeated(50..60, "\n")

    getFilterResultAndCheckHighlightPositions(Random.paragraphGen(), checkHighlights = false)
      .checkFileLinks(*allGeneratedPaths.toTypedArray())
  }

  private fun getFilterResultAndCheckHighlightPositions(content: String, checkHighlights: Boolean = true): List<Filter.Result> {
    var totalLength = 0
    var previousInputLine = ""
    var previousInputStartIndex = 0
    val results = mutableListOf<Filter.Result?>()
    content.lines().forEach { line ->
      if (!checkHighlights || line.startsWith('|')) {
        val inputLine = line.removePrefix("| ") + "\n"
        previousInputLine = inputLine
        previousInputStartIndex = totalLength
        totalLength += inputLine.length
        results += filter.applyFilter(inputLine, totalLength)
      }
      else {
        val actualHighlighted = results.last()!!.resultItems.map { item ->
          previousInputLine.substring(item.getHighlightStartOffset() - previousInputStartIndex,
                                      item.getHighlightEndOffset() - previousInputStartIndex)
        }
        val indicatorLine = line.removePrefix("  ")
        val expectedHighlighted = previousInputLine
          .mapIndexed { i, c -> if (indicatorLine.getOrNull(i) == '^') c else ' ' }
          .joinToString("")
          .split(Regex(" +"))
          .filter { it.isNotEmpty() }
        Truth.assertThat(actualHighlighted).isEqualTo(expectedHighlighted)
      }
    }
    return results.filterNotNull()
  }

  private fun List<Filter.Result>.checkFileLinks(vararg paths: String) {
    Truth.assertThat(
      // Unfortunately there is no way to read the line and column numbers passed to the OpenFileHyperlinkInfo since those are private
      flatMap { it.resultItems }.map { (it.getHyperlinkInfo() as OpenFileHyperlinkInfo).virtualFile!!.path })
      .containsExactlyElementsIn(paths)
      .inOrder()
  }
}
