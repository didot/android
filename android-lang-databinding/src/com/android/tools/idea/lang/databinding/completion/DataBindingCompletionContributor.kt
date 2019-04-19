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
package com.android.tools.idea.lang.databinding.completion

import com.android.ide.common.resources.DataBindingResourceType
import com.android.tools.idea.databinding.BrUtil
import com.android.tools.idea.databinding.DataBindingUtil
import com.android.tools.idea.databinding.analytics.api.DataBindingTracker
import com.android.tools.idea.lang.databinding.config.DbFile
import com.android.tools.idea.lang.databinding.getDataBindingLayoutInfo
import com.android.tools.idea.lang.databinding.model.ModelClassResolvable
import com.android.tools.idea.lang.databinding.psi.PsiDbFunctionRefExpr
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DataBindingEvent.DataBindingContext.DATA_BINDING_CONTEXT_LAMBDA
import com.google.wireless.android.sdk.stats.DataBindingEvent.DataBindingContext.DATA_BINDING_CONTEXT_METHOD_REFERENCE
import com.google.wireless.android.sdk.stats.DataBindingEvent.DataBindingContext.UNKNOWN_CONTEXT
import com.google.wireless.android.sdk.stats.DataBindingEvent.EventType.DATA_BINDING_COMPLETION_ACCEPTED
import com.google.wireless.android.sdk.stats.DataBindingEvent.EventType.DATA_BINDING_COMPLETION_SUGGESTED
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.JavaLookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.intellij.util.ProcessingContext

/**
 * This handles completion in the data binding expressions (inside `@{}`).
 *
 *
 * Completion for everything under `<data>` tag is in
 * [org.jetbrains.android.AndroidXmlCompletionContributor.completeDataBindingTypeAttr].
 */
open class DataBindingCompletionContributor : CompletionContributor() {

  @VisibleForTesting
  open val onCompletionHandler: InsertHandler<LookupElement>? = InsertHandler { context, _ ->
    val tracker = DataBindingTracker.getInstance(context.project)

    val childElement = context.file.findElementAt(context.startOffset)!!
    when (childElement.parent.parent) {
      is PsiDbFunctionRefExpr -> tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_ACCEPTED,
                                                                    DATA_BINDING_CONTEXT_METHOD_REFERENCE)
      is PsiDbRefExpr -> tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_ACCEPTED, DATA_BINDING_CONTEXT_LAMBDA)
      else -> tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_ACCEPTED, UNKNOWN_CONTEXT)
    }
  }

  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        // During first invocation, only suggest valid options. During subsequent invocations, also suggest invalid options
        // such as private members or instance methods on class objects.
        val onlyValidCompletions = parameters.invocationCount <= 1

        val tracker = DataBindingTracker.getInstance(parameters.editor.project!!)

        var position = parameters.originalPosition ?: parameters.position

        // The position is a PsiElement identifier. Its parent is a PsiDbId, which is a child of the overall expression, whose type we use
        // to choose what kind of completion logic to carry out.
        // For example user types @{model::g<caret>}. Position is the LeafPsiElement "g". Parent is the PsiDbId "g". Grandparent is the
        // whole expression "model:g".
        val parent = position.parent
        if (parent.references.isEmpty()) {
          // try to replace parent
          val grandParent = parent.parent
          if (grandParent is PsiDbRefExpr) {
            val ownerExpr = grandParent.expr
            if (ownerExpr == null) {
              autoCompleteVariablesAndUnqualifiedFunctions(getFile(grandParent), result)
              return
            }
            result.addAllElements(populateFieldReferenceCompletions(ownerExpr, onlyValidCompletions))
            result.addAllElements(populateMethodReferenceCompletions(ownerExpr, onlyValidCompletions))
            tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_SUGGESTED, DATA_BINDING_CONTEXT_LAMBDA)
          }
          else if (grandParent is PsiDbFunctionRefExpr) {
            result.addAllElements(populateMethodReferenceCompletions(grandParent.expr, onlyValidCompletions, false))
            tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_SUGGESTED, DATA_BINDING_CONTEXT_METHOD_REFERENCE)
          }
        }
        else {
          //TODO(b/129497876): improve completion experience for variables and static functions
          result.addAllElements(populateFieldReferenceCompletions(parent, onlyValidCompletions))
          result.addAllElements(populateMethodReferenceCompletions(parent, onlyValidCompletions))
          tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_SUGGESTED, UNKNOWN_CONTEXT)
        }
      }
    })
  }

  private fun getFile(element: PsiElement): DbFile {
    var element = element
    while (element !is DbFile) {
      element = element.parent ?: throw IllegalArgumentException()
    }
    return element
  }

  private fun autoCompleteVariablesAndUnqualifiedFunctions(file: DbFile, result: CompletionResultSet) {
    autoCompleteUnqualifiedFunctions(result)

    val dataBindingLayoutInfo = getDataBindingLayoutInfo(file) ?: return
    for ((name, _, xmlTag) in dataBindingLayoutInfo.getItems(DataBindingResourceType.VARIABLE).values) {
      val elementBuilder = LookupElementBuilder.create(xmlTag,
                                                       DataBindingUtil.convertToJavaFieldName(name))
        .withInsertHandler(onCompletionHandler)
      result.addElement(elementBuilder)
    }
  }

  private fun autoCompleteUnqualifiedFunctions(result: CompletionResultSet) {
    result.addElement(LookupElementBuilder.create("safeUnbox").withInsertHandler(onCompletionHandler))
  }

  /**
   * Given a data binding expression, return a list of [LookupElement] which are the field references of the given expression.
   * If onlyValidCompletions is false, private and mismatched context fields are also suggested.
   */
  private fun populateFieldReferenceCompletions(referenceExpression: PsiElement, onlyValidCompletions: Boolean): List<LookupElement> {
    val completionSuggestionsList = mutableListOf<LookupElement>()
    val childReferences = referenceExpression.references
    for (reference in childReferences) {
      val ref = reference as ModelClassResolvable
      val resolvedType = ref.resolvedType?.unwrapped ?: continue
      for (psiModelField in resolvedType.allFields) {
        if (onlyValidCompletions) {
          if (!psiModelField.isPublic || ref.isStatic != psiModelField.isStatic) {
            continue
          }
        }
        // Pass resolvedType.getPsiClass into JavaLookupElementBuilder.forField as qualifierClass
        // so that only fields declared in current class are bold.
        val lookupBuilder = JavaLookupElementBuilder
          .forField(psiModelField.psiField, psiModelField.psiField.name,
                    resolvedType.psiClass)
          .withTypeText(
            PsiFormatUtil.formatVariable(psiModelField.psiField, PsiFormatUtilBase.SHOW_TYPE, PsiSubstitutor.EMPTY))
          .withInsertHandler(onCompletionHandler)
        completionSuggestionsList.add(lookupBuilder)
      }
    }
    return completionSuggestionsList
  }

  /**
   * Given a data binding expression, return a list of [LookupElement] which are method references of the given expression.
   * If onlyValidCompletions is false, private and mismatched context fields are also suggested.
   */
  private fun populateMethodReferenceCompletions(referenceExpression: PsiElement,
                                                 onlyValidCompletions: Boolean,
                                                 completeBrackets: Boolean = true): List<LookupElement> {
    val completionSuggestionsList = mutableListOf<LookupElement>()
    val childReferences = referenceExpression.references
    for (reference in childReferences) {
      if (reference is ModelClassResolvable) {
        val ref = reference as ModelClassResolvable
        var resolvedType = ref.resolvedType?.unwrapped ?: continue
        for (psiModelMethod in resolvedType.allMethods) {
          val psiMethod = psiModelMethod.psiMethod
          if (psiMethod.isConstructor) {
            continue
          }
          else if (onlyValidCompletions) {
            if (ref.isStatic != psiModelMethod.isStatic || !psiModelMethod.isPublic) {
              continue
            }
          }
          var name = psiModelMethod.name
          if (completeBrackets) {
            name += "()"
            if (BrUtil.isGetter(psiMethod)) {
              name = psiModelMethod.name.substring(3).decapitalize()
            }
            else if (BrUtil.isBooleanGetter(psiMethod)) {
              name = psiModelMethod.name.substring(2).decapitalize()
            }
          }
          // Pass resolvedType.getPsiClass into JavaLookupElementBuilder.forMethod as qualifierClass
          // so that only methods declared in current class are bold.
          val lookupBuilder = JavaLookupElementBuilder.forMethod(psiMethod, name, PsiSubstitutor.EMPTY,
                                                                 resolvedType.psiClass).withInsertHandler(onCompletionHandler)
          completionSuggestionsList.add(lookupBuilder)
        }
      }
    }
    return completionSuggestionsList
  }
}
