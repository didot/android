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
package com.android.tools.idea.codenavigation

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.ClassUtil

/**
 * The [PsiNavSource] uses the IntelliJ internal representation of Java classes and interfaces to
 * jump to code locations. Using the PSI navigation types, it is possible to navigate to the
 * disassembled versions of classes.
 */
class PsiNavSource(private val project: Project): NavSource {
  override fun lookUp(location: CodeLocation, arch: String?): Navigatable? {
    var psiClass = findClass(location) ?: return null

    if (location.lineNumber >= 0) {
      // If the specified CodeLocation has a line number, navigatable is that line
      return OpenFileDescriptor(project,
                                psiClass.navigationElement.containingFile.virtualFile,
                                location.lineNumber,
                                0)
    }

    // If we fail to find the method, fall back to the class.
    return findMethod(psiClass, location) ?: psiClass
  }

  /**
   * Looks for the class definition that covers [location]. Returns null if no class can be
   * found (e.g. not java/kotlin code).
   */
  private fun findClass(location: CodeLocation): PsiClass? {
    if (location.className.isNullOrEmpty()) {
      return null
    }

    val manager = PsiManager.getInstance(project)

    var psiClass = ClassUtil.findPsiClassByJVMName(manager, location.className!!);

    if (psiClass == null && location.lineNumber >= 0) {
      // There has been at least one case where the PsiManager could not find an inner class in
      // Kotlin code, which caused us to abort navigating. However, if we have the outer class
      // (which is easier for PsiManager to find) and a line number, that's enough information to
      // help us navigate. So, to be more robust against PsiManager error, we try one more time.
      psiClass = ClassUtil.findPsiClassByJVMName(manager, location.outerClassName!!);
    }

    return psiClass
  }

  private fun findMethod(psiClass: PsiClass, location: CodeLocation,): PsiMethod? {
    if (location.methodName == null || location.signature == null) {
      return null
    }

    val methods = psiClass.findMethodsByName(location.methodName, true)
    return methods.firstOrNull{ location.signature == TraceSignatureConverter.getTraceSignature(it) }
  }
}