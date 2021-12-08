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
package com.android.tools.compose.code.completion.constraintlayout.provider

import com.android.tools.compose.code.completion.constraintlayout.ConstrainAnchorTemplate
import com.android.tools.compose.code.completion.constraintlayout.InsertionFormat
import com.android.tools.compose.code.completion.constraintlayout.JsonNewObjectTemplate
import com.android.tools.compose.code.completion.constraintlayout.JsonStringValueTemplate
import com.android.tools.compose.code.completion.constraintlayout.KeyWords
import com.android.tools.compose.code.completion.constraintlayout.LiteralNewLineFormat
import com.android.tools.compose.code.completion.constraintlayout.LiteralWithCaretFormat
import com.android.tools.compose.code.completion.constraintlayout.LiveTemplateFormat
import com.android.tools.compose.code.completion.constraintlayout.StandardAnchor
import com.android.tools.compose.code.completion.constraintlayout.inserthandler.FormatWithCaretInsertHandler
import com.android.tools.compose.code.completion.constraintlayout.inserthandler.FormatWithLiveTemplateInsertHandler
import com.android.tools.compose.code.completion.constraintlayout.inserthandler.FormatWithNewLineInsertHandler
import com.android.tools.compose.code.completion.constraintlayout.provider.model.ConstraintSetModel
import com.android.tools.compose.code.completion.constraintlayout.provider.model.ConstraintSetsPropertyModel
import com.android.tools.compose.code.completion.constraintlayout.provider.model.ConstraintsModel
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext

/**
 * Completion provider that looks for the 'ConstraintSets' declaration and passes a model that provides useful functions for inheritors that
 * want to provide completions based on the contents of the 'ConstraintSets' [JsonProperty].
 */
internal abstract class BaseConstraintSetsCompletionProvider : CompletionProvider<CompletionParameters>() {
  final override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val constraintSetsModel = createConstraintSetsModel(parameters.position)
    if (constraintSetsModel != null) {
      ProgressManager.checkCanceled()
      addCompletions(constraintSetsModel, parameters, result)
    }
  }

  /**
   * Inheritors should implement this function that may pass a reference to the ConstraintSets property.
   */
  abstract fun addCompletions(
    constraintSetsPropertyModel: ConstraintSetsPropertyModel,
    parameters: CompletionParameters,
    result: CompletionResultSet
  )

  /**
   * From the element being invoked, returns the [JsonProperty] parent that also includes the [JsonProperty] from which completion is
   * triggered.
   */
  protected fun getJsonPropertyParent(parameters: CompletionParameters): JsonProperty? =
    parameters.position.parentOfType<JsonProperty>(withSelf = true)?.parentOfType<JsonProperty>(withSelf = false)


  /**
   * Finds the [JsonProperty] for the 'ConstraintSets' declaration and returns its model.
   */
  private fun createConstraintSetsModel(initialElement: PsiElement): ConstraintSetsPropertyModel? {
    // The most immediate JsonProperty, including the initialElement if applicable
    val closestProperty = initialElement.parentOfType<JsonProperty>(true) ?: return null
    var current = closestProperty
    var constraintSetsCandidate = current.parentOfType<JsonProperty>(withSelf = false)
    while (constraintSetsCandidate != null && constraintSetsCandidate.name != KeyWords.ConstraintSets) {
      // TODO(b/207030860): Consider creating the model even if there's no property that is explicitly called 'ConstraintSets'
      //    ie: imply that the root JsonObject is the ConstraintSets object, with the downside that figuring out the correct context would
      //    be much more difficult
      current = constraintSetsCandidate
      constraintSetsCandidate = current.parentOfType<JsonProperty>(withSelf = false)
      ProgressManager.checkCanceled()
    }
    return constraintSetsCandidate?.let { ConstraintSetsPropertyModel(it) }
  }
}

/**
 * Provides options to autocomplete constraint IDs for constraint set declarations, based on the IDs already defined by the user in other
 * constraint sets.
 */
internal object ConstraintSetFieldsProvider : BaseConstraintSetsCompletionProvider() {
  override fun addCompletions(
    constraintSetsPropertyModel: ConstraintSetsPropertyModel,
    parameters: CompletionParameters,
    result: CompletionResultSet
  ) {
    val currentConstraintSet = getJsonPropertyParent(parameters)?.let { ConstraintSetModel(it) } ?: return
    val currentSetName = currentConstraintSet.name ?: return
    constraintSetsPropertyModel.getRemainingFieldsForConstraintSet(currentSetName).forEach { fieldName ->
      val template = if (fieldName == KeyWords.Extends) JsonStringValueTemplate else JsonNewObjectTemplate
      result.addLookupElement(name = fieldName, tailText = null, template)
    }
  }
}

/**
 * Autocomplete options with the names of all available ConstraintSets, except from the one the autocomplete was invoked from.
 */
internal object ConstraintSetNamesProvider : BaseConstraintSetsCompletionProvider() {
  override fun addCompletions(
    constraintSetsPropertyModel: ConstraintSetsPropertyModel,
    parameters: CompletionParameters,
    result: CompletionResultSet
  ) {
    val currentConstraintSet = getJsonPropertyParent(parameters)?.let { ConstraintSetModel(it) }
    val currentSetName = currentConstraintSet?.name
    val names = constraintSetsPropertyModel.getConstraintSetNames().toMutableSet()
    if (currentSetName != null) {
      names.remove(currentSetName)
    }
    names.forEach(result::addLookupElement)
  }
}

/**
 * Autocomplete options used to define the constraints of a widget (defined by the ID) within a ConstraintSet
 */
internal object ConstraintsProvider : BaseConstraintSetsCompletionProvider() {
  override fun addCompletions(
    constraintSetsPropertyModel: ConstraintSetsPropertyModel,
    parameters: CompletionParameters,
    result: CompletionResultSet
  ) {
    val currentConstraintsModel = getJsonPropertyParent(parameters)?.let { ConstraintsModel(it) }
    val existingFields = currentConstraintsModel?.declaredFieldNames?.toHashSet() ?: emptySet<String>()
    StandardAnchor.values().forEach {
      if (!existingFields.contains(it.keyWord)) {
        result.addLookupElement(name = it.keyWord, tailText = " [...]", format = ConstrainAnchorTemplate)
      }
    }
    // TODO(b/207030860): Add all other supported fields
  }
}

private fun CompletionResultSet.addLookupElement(name: String, tailText: String? = null, format: InsertionFormat? = null) {
  var lookupBuilder = if (format == null) {
    LookupElementBuilder.create(name)
  }
  else {
    val insertionHandler = when (format) {
      is LiteralWithCaretFormat -> FormatWithCaretInsertHandler(format)
      is LiteralNewLineFormat -> FormatWithNewLineInsertHandler(format)
      is LiveTemplateFormat -> FormatWithLiveTemplateInsertHandler(format)
    }
    LookupElementBuilder.create(name).withInsertHandler(insertionHandler)
  }
  lookupBuilder = lookupBuilder.withCaseSensitivity(false)
  if (tailText != null) {
    lookupBuilder = lookupBuilder.withTailText(tailText, true)
  }
  addElement(lookupBuilder)
}