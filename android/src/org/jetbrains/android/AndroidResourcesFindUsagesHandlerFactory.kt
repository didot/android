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
package org.jetbrains.android

import com.android.ide.common.resources.ResourceRepository
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.res.psi.AndroidResourceToPsiResolver
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.res.psi.ResourceReferencePsiElement.Companion.RESOURCE_CONTEXT_ELEMENT
import com.android.tools.idea.res.psi.ResourceReferencePsiElement.Companion.RESOURCE_CONTEXT_SCOPE
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor

/**
 * Provides a custom [FindUsagesHandler] that understands how to search for all relevant Android Resources.
 *
 * This works by creating a [ResourceReferencePsiElement] from the element in the editor, if not a [ResourceReferencePsiElement] already.
 * XML usages are found via a ReferencesSearch on the [ResourceReferencePsiElement].
 * File usages are manually added from [ResourceRepository] as they are not found in the ReferencesSearch. This is done in
 * processElementUsages().
 *
 * TODO(lukeegan): manually find all styleable attr fields when search for an Attr resource.
 */
class AndroidResourcesFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
  override fun canFindUsages(element: PsiElement): Boolean {
    return StudioFlags.RESOLVE_USING_REPOS.get() && ResourceReferencePsiElement.create(element) != null
  }

  override fun createFindUsagesHandler(originalElement: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
    val resourceReferencePsiElement = ResourceReferencePsiElement.create(originalElement) ?: return null
    return object : FindUsagesHandler(resourceReferencePsiElement) {

      override fun processElementUsages(element: PsiElement, processor: Processor<UsageInfo>, options: FindUsagesOptions): Boolean {
        if (element !is ResourceReferencePsiElement) {
          return true
        }
        if (!forHighlightUsages) {
          // When highlighting the current file, any possible resources to be highlighted will be found by the default [ReferencesSearch]
          // on the ResourceReferencePsiElement.
          val reducedScope = (psiElement as? ResourceReferencePsiElement)?.getCopyableUserData(RESOURCE_CONTEXT_SCOPE)
          if (reducedScope != null) {
            options.searchScope = options.searchScope.intersectWith(reducedScope)
          }
          val contextElement = originalElement.getCopyableUserData(RESOURCE_CONTEXT_ELEMENT)
          if (contextElement != null) {
            runReadAction {
              AndroidResourceToPsiResolver.getInstance()
                .getGotoDeclarationFileBasedTargets(element.resourceReference, contextElement)
                .forEach { processor.process(UsageInfo(it, false)) }
            }
          }
        }
        return super.processElementUsages(element, processor, options)
      }
    }
  }
}