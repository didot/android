/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.filters

import com.android.ddmlib.Log
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.PACKAGE_NAMES_PROVIDER_KEY
import com.android.tools.idea.logcat.TAGS_PROVIDER_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.REGEX_KVALUE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.STRING_KVALUE
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.PlatformPatterns.or
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.TokenType.ERROR_ELEMENT
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.VisibleForTesting

private const val MY_PACKAGE_VALUE = "mine"

private const val PACKAGE_KEY = "package"

private const val MY_PACKAGE = "$PACKAGE_KEY:$MY_PACKAGE_VALUE "

private val PACKAGE_KEYS = PACKAGE_KEY.getKeyVariants().toSet()

private const val TAG_KEY = "tag"
private val TAG_KEYS = TAG_KEY.getKeyVariants().toSet()

private val STRING_KEYS = listOf(
  "line",
  "message",
  PACKAGE_KEY,
  TAG_KEY,
)

private const val LEVEL_KEY = "level:"

private const val AGE_KEY = "age:"

private const val IS_KEY = "is:"

// The following are getters so they can be tested. If they are consts, the value is fixed before we can override the flag
private val KEYS
  get() = STRING_KEYS.map { "$it:" } + LEVEL_KEY + AGE_KEY + maybeAddIsKey()
private val ALL_KEYS
  get() = STRING_KEYS.map(String::getKeyVariants).flatten() + LEVEL_KEY + AGE_KEY + maybeAddIsKey()

private fun maybeAddIsKey() = if (StudioFlags.LOGCAT_IS_FILTER.get()) listOf(IS_KEY) else emptyList()

private val LEVEL_LOOKUPS_LOWERCASE = Log.LogLevel.values()
  .map { it.name.lowercase().toLookupElement(suffix = " ") }

private val LEVEL_LOOKUPS_UPPERCASE = Log.LogLevel.values()
  .map { it.name.uppercase().toLookupElement(suffix = " ") }

private val IS_LOOKUPS = listOf("crash", "stacktrace").map { it.toLookupElement(suffix = " ") }

// Do not complete a key if previous char is one of these
private const val NON_KEY_MARKER = "'\")"

private val HINTS = listOf(
  LogcatBundle.message("logcat.filter.completion.hint1"),
  LogcatBundle.message("logcat.filter.completion.hint2"),
  LogcatBundle.message("logcat.filter.completion.hint3"),
  LogcatBundle.message("logcat.filter.completion.hint4"),
  LogcatBundle.message("logcat.filter.completion.hint5"),
  LogcatBundle.message("logcat.filter.completion.hint6"),
)

/**
 * A [CompletionContributor] for the Logcat Filter Language.
 */
internal class LogcatFilterCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, psiElement(LogcatFilterTypes.VALUE),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               // We have to exclude a few special cases where we do not want to complete.

               val text = parameters.position.text
               if (text.startsWith('"') || text.startsWith('\'')) {
                 // Do not complete keys inside a quoted text.
                 return
               }
               if (PsiTreeUtil.findSiblingBackward(parameters.position, ERROR_ELEMENT, null) != null) {
                 // Do not complete a key if there is an error in the current level of the tree. This happens when we are in an
                 // unterminated quoted string.
                 return
               }
               // Offset of beginning of the current psi element.
               val pos = parameters.offset - parameters.getRealTextLength()
               if (pos > 0) {
                 // Do not complete a key right after certain chars.
                 val c = parameters.originalFile.text[pos - 1]
                 if (NON_KEY_MARKER.contains(c)) {
                   return
                 }
               }
               result.addAllElements(
                 if (text == DUMMY_IDENTIFIER_TRIMMED) KEYS.lookupsWithHistory() else ALL_KEYS.lookupsWithHistory())
               if (hasAndroidProject(parameters.editor)) {
                 result.addElement(MY_PACKAGE.toLookupElement())
               }
               result.addHints()
             }
           })
    extend(CompletionType.BASIC, psiElement(LogcatFilterTypes.KVALUE),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               when {
                 parameters.findPreviousText() == LEVEL_KEY -> result.addLevelLookups()
                 parameters.findPreviousText() == IS_KEY && StudioFlags.LOGCAT_IS_FILTER.get() -> result.addAllElements(IS_LOOKUPS)
               }
               result.addHints()
             }
           })
    extend(CompletionType.BASIC, or(psiElement(STRING_KVALUE), psiElement(REGEX_KVALUE)),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               when (parameters.findPreviousText()) {
                 "$PACKAGE_KEY:" -> {
                   result.addAllElements((parameters.getPackageNames()).map { it.toLookupElement(suffix = " ") })
                   if (hasAndroidProject(parameters.editor)) {
                     result.addElement(MY_PACKAGE_VALUE.toLookupElement(suffix = " "))
                   }
                 }
                 in PACKAGE_KEYS ->
                   result.addAllElements((parameters.getPackageNames()).map { it.toLookupElement(suffix = " ") })
                 in TAG_KEYS ->
                   result.addAllElements(parameters.getTags().filter(String::isNotBlank).map { it.toLookupElement(suffix = " ") })
               }
               result.addHints()
             }
           })
  }
}

private fun CompletionResultSet.addLevelLookups() {
  val prefix = prefixMatcher.prefix
  val lookups = if (prefix.isEmpty() || prefix.first().isLowerCase()) LEVEL_LOOKUPS_LOWERCASE else LEVEL_LOOKUPS_UPPERCASE
  addAllElements(lookups)
}

private fun CompletionResultSet.addHints() {
  HINTS.forEach { addLookupAdvertisement(it) }
}

@VisibleForTesting
internal fun String.getKeyVariants() = listOf("$this:", "-$this:", "$this~:", "-$this~:")

private fun String.toLookupElement(suffix: String = "") = LookupElementBuilder.create("$this$suffix")

private fun CompletionParameters.findPreviousText() = PsiTreeUtil.skipWhitespacesBackward(position)?.text

private fun CompletionParameters.getTags() =
  editor.getUserData(TAGS_PROVIDER_KEY)?.getTags() ?: throw IllegalStateException("Missing PackageNamesProvider")

private fun CompletionParameters.getPackageNames() =
  editor.getUserData(PACKAGE_NAMES_PROVIDER_KEY)?.getPackageNames() ?: throw IllegalStateException("Missing PackageNamesProvider")

private fun CompletionParameters.getRealTextLength(): Int {
  val text = position.text
  val len = text.indexOf(DUMMY_IDENTIFIER_TRIMMED)
  return if (len < 0) text.length else len
}

private fun hasAndroidProject(editor: Editor): Boolean {
  val project = editor.project ?: return false
  return editor.getUserData(AndroidProjectDetector.KEY)?.isAndroidProject(project) ?: false
}

private fun List<String>.lookupsWithHistory(): List<LookupElementBuilder> {
  val history = AndroidLogcatFilterHistory.getInstance()
  val lookups = mutableSetOf<String>()
  lookups.addAll(this)
  lookups.addAll(history.favorites)
  lookups.addAll(history.nonFavorites)

  return lookups.map(String::toLookupElement)
}