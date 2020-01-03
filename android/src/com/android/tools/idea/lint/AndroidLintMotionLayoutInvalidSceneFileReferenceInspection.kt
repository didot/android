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
package com.android.tools.idea.lint

import com.android.resources.ResourceUrl
import com.android.tools.lint.checks.MotionLayoutDetector
import com.android.tools.lint.detector.api.LintFix
import com.intellij.psi.PsiElement
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix
import org.jetbrains.android.util.AndroidBundle

class AndroidLintMotionLayoutInvalidSceneFileReferenceInspection : AndroidLintInspectionBase(
  AndroidBundle.message("android.lint.inspections.motion.layout.invalid.scene.file.reference"),
  MotionLayoutDetector.INVALID_SCENE_FILE_REFERENCE) {

  override fun getQuickFixes(startElement: PsiElement,
                             endElement: PsiElement,
                             message: String,
                             fixData: LintFix?): Array<AndroidLintQuickFix> {
    return generateMotionSceneFix(fixData) ?: super.getQuickFixes(startElement, endElement, message, fixData)
  }

  private fun generateMotionSceneFix(fixData: LintFix?): Array<AndroidLintQuickFix>? {
    val urlString = LintFix.getData(fixData, String::class.java) ?: return null
    val url = ResourceUrl.parse(urlString) ?: return null
    return arrayOf(GenerateMotionSceneFix(url))
  }
}
