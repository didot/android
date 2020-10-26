/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.resolve.calls.callUtil.getParameterForArgument

private const val LABEL_PARAMETER = "label"
private const val PROP_KEY_SUFFIX = "PropKey"
private const val COMPOSE_ANIMATION_PACKAGE = "androidx.compose.animation"

/**
 * Inspection to verify that Compose animations PropKeys have their `label` parameter set. This parameter is used by the animation tooling
 * to identify the PropKeys when inspecting animations in the Animation Preview.
 */
class PropKeysLabelInspection : AbstractKotlinInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    if (StudioFlags.COMPOSE_ANIMATION_PREVIEW_LABEL_INSPECTION.get() && session.file.androidFacet != null) {
      object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
          super.visitCallExpression(expression)
          // First, check we're analyzing a PropKey
          val propKey = (expression.firstChild as? KtNameReferenceExpression)
                    ?.resolveToCall()?.resultingDescriptor?.returnType?.fqName ?: return
          if (!propKey.shortName().toString().endsWith(PROP_KEY_SUFFIX)) return
          // Next, double-check they're defined within the Compose Animation package
          if (!propKey.parent().toString().startsWith(COMPOSE_ANIMATION_PACKAGE)) return

          // Finally, verify the PropKey has the `label` parameter set, otherwise show a weak warning.
          val resolvedExpression = expression.resolveToCall()
          if (expression.valueArguments.any { resolvedExpression?.getParameterForArgument(it)?.name?.asString() == LABEL_PARAMETER }) {
            // PropKey has the label parameter set.
            return
          }
          holder.registerProblem(
            expression,
            message("inspection.no.label.parameter.set.description"),
            ProblemHighlightType.WEAK_WARNING,
            AddLabelFieldQuickFix(expression))
        }
      }
    }
    else {
      PsiElementVisitor.EMPTY_VISITOR
    }

  /**
   * Add the `label` parameter to a PropKey expression. For example:
   * `FloatPropKey()` -> `FloatPropKey(label = "")`
   */
  private class AddLabelFieldQuickFix(propKeyExpression: KtCallExpression) : LocalQuickFixOnPsiElement(propKeyExpression) {
    override fun getFamilyName() = message("inspection.group.name")

    override fun getText() = message("inspection.no.label.parameter.set.quick.fix.text")

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
      val psiFactory = KtPsiFactory(project)
      val statementText = "$LABEL_PARAMETER = \"\""
      (startElement as KtCallExpression).valueArgumentList?.addArgument(psiFactory.createArgument(statementText))
    }
  }
}