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
package com.android.tools.idea.nav.safeargs.finder

import com.android.tools.idea.nav.safeargs.isSafeArgsEnabled
import com.android.tools.idea.nav.safeargs.module.SafeArgsCacheModuleService
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.ResolveScopeEnlarger
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.caches.resolve.util.KotlinResolveScopeEnlarger

/**
 * Scope enlarger for safe args, necessary to allow the IntelliJ framework to add safe args light
 * classes to their corresponding module scope.
 */
class SafeArgsScopeEnlarger : ResolveScopeEnlarger() {
  override fun getAdditionalResolveScope(file: VirtualFile, project: Project): SearchScope? {
    val module = ModuleUtil.findModuleForFile(file, project) ?: return null
    val facet = module.androidFacet?.takeIf { it.isSafeArgsEnabled() } ?: return null

    return getAdditionalResolveScope(facet)
  }

  internal fun getAdditionalResolveScope(facet: AndroidFacet): SearchScope? {
    val module = facet.module
    val project = module.project
    return CachedValuesManager.getManager(project).getCachedValue(module) {
      val lightClasses = mutableListOf<PsiClass>()

      val moduleCache = SafeArgsCacheModuleService.getInstance(facet)
      lightClasses.addAll(moduleCache.directions)
      lightClasses.addAll(moduleCache.args)

      // Light classes don't exist on disk, so you have to use their view provider to get a
      // corresponding virtual file. This same virtual file should be used by finders to verify
      // that classes they are returning belong to the current scope.
      val virtualFiles = lightClasses.map { it.containingFile!!.viewProvider.virtualFile }
      val localScope = GlobalSearchScope.filesWithoutLibrariesScope(project, virtualFiles)

      val scopeIncludingDeps = ModuleRootManager.getInstance(module)
        .getDependencies(false)
        .mapNotNull { module -> module.androidFacet?.takeIf { it.isSafeArgsEnabled() } }
        .mapNotNull { facet -> getAdditionalResolveScope(facet) }
        .fold(localScope) { scopeAccum, depScope -> scopeAccum.union(depScope) }

      CachedValueProvider.Result.create(scopeIncludingDeps, PsiModificationTracker.MODIFICATION_COUNT)
    }
  }
}

/**
 * Additional scope enlarger for Kotlin
 *
 * Kotlin needs its own scope enlarger - it can't simply use the [SafeArgsScopeEnlarger] above.
 * Therefore, we provide one here that simply delegates to it.
 */
class SafeArgsKotlinScopeEnlarger : KotlinResolveScopeEnlarger() {
  private val delegateEnlarger = ResolveScopeEnlarger.EP_NAME.findExtensionOrFail(SafeArgsScopeEnlarger::class.java)

  override fun getAdditionalResolveScope(module: Module, isTestScope: Boolean): SearchScope? {
    val facet = module.androidFacet?.takeIf { it.isSafeArgsEnabled() } ?: return null
    return delegateEnlarger.getAdditionalResolveScope(facet)
  }
}
