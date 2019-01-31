/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License",
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
package com.android.tools.idea.editors

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.generation.OverrideImplementsAnnotationsHandler
import com.intellij.codeInsight.intention.AddAnnotationPsiFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNameValuePair

class AndroidOverrideAnnotationsHandler : OverrideImplementsAnnotationsHandler {
  override fun getAnnotations(project: Project): Array<String> {
    return arrayOf(
      "androidx.annotation.NonNull",
      "androidx.annotation.Nullable",
      "android.support.annotation.NonNull",
      "android.support.annotation.Nullable",
      "androidx.annotation.RecentlyNonNull",
      "androidx.annotation.RecentlyNullable",
      "android.annotation.NonNull",
      "android.annotation.Nullable")
  }

  override fun cleanup(source: PsiModifierListOwner, targetClass: PsiElement?, target: PsiModifierListOwner) {
    // Rename @RecentlyX annotations to @X
    rename(target, "androidx.annotation.RecentlyNullable", "androidx.annotation.Nullable")
    rename(target, "androidx.annotation.RecentlyNonNull", "androidx.annotation.NonNull")
    // Also, android.annotation.X maps to androidx.annotation.X: the android.annotations are hidden
    // but part of android.jar rather than the support library, though hidden from app developers
    rename(target, "android.annotation.Nullable", "androidx.annotation.Nullable")
    rename(target, "android.annotation.NonNull", "androidx.annotation.NonNull")
  }

  private fun rename(target: PsiModifierListOwner, old: String, new: String) {
    val modifierList = target.modifierList ?: return

    while (true) { // Loop to ensure we remove repeats which can happen with TYPE_USE annotations
      val annotation = AnnotationUtil.findAnnotation(target, setOf(old), true)
      if (annotation != null && !AnnotationUtil.isInferredAnnotation(annotation)) {
        annotation.delete()
        AddAnnotationPsiFix.addPhysicalAnnotation(new, PsiNameValuePair.EMPTY_ARRAY, modifierList)
      }
      else {
        break
      }
    }
  }
}
