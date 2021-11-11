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
package com.android.tools.idea.compose.annotator

import com.android.tools.idea.compose.preview.BasePreviewAnnotationInspection
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.sourcePsiElement
import org.jetbrains.uast.toUElement

/**
 * LocalInspection for the Compose @Preview annotation.
 *
 * Outlines IDE-specific issues with the annotation's contents (i.e: the library has independent Lint checks of its own).
 */
class PreviewPickerAnnotationInspection : BasePreviewAnnotationInspection() {

  override fun visitPreviewAnnotatedFunction(holder: ProblemsHolder, function: KtNamedFunction, previewAnnotation: KtAnnotationEntry) {
    if (previewAnnotation.getModuleSystem()?.isPickerEnabled() != true) return

    val result = PreviewAnnotationCheck.checkPreviewAnnotationIfNeeded(previewAnnotation)

    if (!result.hasIssues) return

    val messageBuffer = StringBuffer()
    val groupedIssues = result.issues.groupBy { it::class.java.kotlin }

    groupedIssues.entries.forEach { entry ->
      val messagePrefix =
        when (entry.key) {
          BadType::class -> message("picker.preview.annotator.lint.error.type")
          Missing::class -> message("picker.preview.annotator.lint.error.missing")
          Unknown::class -> message("picker.preview.annotator.lint.error.unknown")
          Repeated::class -> message("picker.preview.annotator.lint.error.repeated")
          Failure::class -> {
            val failureMessage = entry.value.filterIsInstance<Failure>().joinToString("\n") { it.failureMessage }
            Logger.getInstance(PreviewPickerAnnotationInspection::class.java).warn("Failed when checking annotation: $failureMessage")
            return@forEach
          }
          else -> return@forEach
        } + ": "
      addSimpleMessage(messagePrefix, entry.value, messageBuffer)

      if (entry.key == BadType::class) {
        addMessageForBadTypeParameters(entry.value.map { it as BadType }, messageBuffer)
      }
      messageBuffer.append('\n')
    }

    if (messageBuffer.isNotEmpty() && result.proposedFix != null) {
      val message = messageBuffer.toString().trim()

      val uElement = (previewAnnotation.toUElement() as? UAnnotation) ?: return
      val deviceValueExpression = uElement.findDeclaredAttributeValue("device") ?: return
      val deviceValueElement = deviceValueExpression.sourcePsiElement ?: return

      holder.registerProblem(
        deviceValueElement,
        message,
        ProblemHighlightType.ERROR,
        PreviewParametersQuickFix(deviceValueElement, result.proposedFix)
      )
    }
  }
}

private class PreviewParametersQuickFix(
  deviceValueElement: PsiElement,
  private val resultingString: String
) : LocalQuickFixOnPsiElement(deviceValueElement) {
  override fun getText(): String = message("picker.preview.annotator.fix.replace", resultingString)

  override fun getFamilyName(): String = message("picker.preview.annotator.fix.family")

  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    try {
      startElement.replace(KtPsiFactory(project, true).createLiteralStringTemplateEntry(resultingString))
    }
    catch (e: IncorrectOperationException) {
      Logger.getInstance(PreviewPickerAnnotationInspection::class.java).error("Unable to apply fix to @Preview 'device' parameter", e)
    }
  }
}

private fun addMessageForBadTypeParameters(issues: List<BadType>, messageBuffer: StringBuffer) {
  val parametersByType = issues.groupBy { it.expected }.mapValues { it.value.map(BadType::parameterName) }
  val messagePrefix = message("picker.preview.annotator.lint.error.type.prefix")

  parametersByType.entries.forEach { entry ->
    @Suppress("MoveVariableDeclarationIntoWhen") // The suggested pattern is harder to read/understand
    val supportedType = entry.key
    when (supportedType) {
      SupportedType.Integer -> {
        val messagePostfix = message("picker.preview.annotator.lint.error.type.integer")
        entry.value.joinTo(buffer = messageBuffer, separator = ", ", prefix = "$messagePrefix: ", postfix = " $messagePostfix\n")
      }
      SupportedType.Shape,
      SupportedType.DimUnit -> {
        val valuesExamples = supportedType.acceptableValues.joinToString(", ")
        val messagePostfix = message("picker.preview.annotator.lint.error.type.options", valuesExamples)
        entry.value.joinTo(buffer = messageBuffer, separator = ", ", prefix = "$messagePrefix: ", postfix = " $messagePostfix\n")
      }
    }
  }
}

private fun addSimpleMessage(prefix: String, issues: List<IssueReason>, messageBuffer: StringBuffer) {
  val allParameters = issues.map(IssueReason::parameterName)
  if (allParameters.isEmpty()) return

  allParameters.joinTo(buffer = messageBuffer, separator = ", ", prefix = prefix, postfix = ".\n\n")
}