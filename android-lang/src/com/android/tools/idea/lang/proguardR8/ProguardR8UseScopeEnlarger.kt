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
package com.android.tools.idea.lang.proguardR8

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Adds Proguard/R8 files to a search scope for PsiMember/KtProperty.
 *
 * We need to extend useScope [com.intellij.psi.search.PsiSearchHelper.getUseScope] for a non-public PsiMember(includes PsiClasses) and
 * KtProperty because they can be used in a Proguard/R8 files.
 */
class ProguardR8UseScopeEnlarger : UseScopeEnlarger() {
  override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
    if (element is PsiMember || element is KtProperty) {
      val project = element.project

      val cachedValuesManager = CachedValuesManager.getManager(project)
      val files = cachedValuesManager.getCachedValue(project) {
        val proguardFiles = FileTypeIndex.getFiles(ProguardR8FileType.INSTANCE, GlobalSearchScope.allScope(project))
        CachedValueProvider.Result(proguardFiles, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
      }
      if (files.isEmpty()) return null else GlobalSearchScope.filesScope(project, files)
    }
    return null
  }
}