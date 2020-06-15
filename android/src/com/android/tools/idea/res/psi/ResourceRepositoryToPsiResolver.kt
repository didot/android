/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res.psi

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.sampledata.SampleDataManager
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.android.tools.idea.res.resolve
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.xml.XmlElement
import com.intellij.util.containers.toArray
import org.jetbrains.android.dom.resources.ResourceValue
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidResourceUtil

object ResourceRepositoryToPsiResolver : AndroidResourceToPsiResolver {
  override fun getGotoDeclarationFileBasedTargets(resourceReference: ResourceReference, context: PsiElement): Array<PsiFile> {
    return ResourceRepositoryManager.getInstance(context)
      ?.allResources
      ?.getResources(resourceReference)
      ?.filter { it.isFileBased }
      ?.mapNotNull { resolveToDeclaration(it, context.project) }
      ?.filterIsInstance(PsiFile::class.java)
      .orEmpty()
      .toTypedArray()
  }

  override fun resolveToDeclaration(resource: ResourceItem, project: Project): PsiElement? {
    return if (resource.isFileBased) {
      resource.getSourceAsVirtualFile()?.let(PsiManager.getInstance(project)::findFile)
    }
    else {
      AndroidResourceUtil.getDeclaringAttributeValue(project, resource)
    }
  }

  /**
   * Resolves the reference to a {@link ResourceReferencePsiElement} if any matching resources exist. We should avoid using the
   * [ResourceValue] parameter and instead provide [ResourceReference] via the other implementation below.
   */
  override fun resolveReference(
    resourceValue: ResourceValue,
    context: XmlElement,
    facet: AndroidFacet
  ): Array<out ResolveResult> {
    return resolveReference(resourceValue, context, facet, false)
  }

  override fun resolveReferenceWithDynamicFeatureModules(
    resourceValue: ResourceValue,
    element: XmlElement,
    facet: AndroidFacet
  ): Array<out ResolveResult> {
    return resolveReference(resourceValue, element, facet, true)
  }

  fun resolveReference(
    resourceValue: ResourceValue,
    context: XmlElement,
    facet: AndroidFacet,
    includeDynamicFeatures: Boolean
  ): Array<out ResolveResult> {
    var resourceName = resourceValue.resourceName ?: return ResolveResult.EMPTY_ARRAY
    val resourceType = resourceValue.type ?: return ResolveResult.EMPTY_ARRAY
    if (resourceType == ResourceType.SAMPLE_DATA) {
      resourceName = SampleDataManager.getResourceNameFromSampleReference(resourceName)
    }
    val resourceReference =
      ResourceUrl.create(resourceValue.`package`, resourceType, resourceName).resolve(context) ?: return ResolveResult.EMPTY_ARRAY
    return resolveReference(resourceReference, context, facet, includeDynamicFeatures)
  }

  @JvmOverloads
  fun resolveReference(
    resourceReference: ResourceReference,
    context: PsiElement,
    facet: AndroidFacet,
    includeDynamicFeatures: Boolean = false
  ): Array<out ResolveResult> {
    val allResources = ResourceRepositoryManager.getInstance(facet).allResources ?: return ResolveResult.EMPTY_ARRAY
    val allItems = mutableListOf<ResolveResult>()
    if (allResources.hasResources(resourceReference.namespace, resourceReference.resourceType, resourceReference.name)) {
      allItems.add(PsiElementResolveResult(ResourceReferencePsiElement(resourceReference, context.manager)))
    }
    if (includeDynamicFeatures) {
      val moduleSystem = context.getModuleSystem() ?: return ResolveResult.EMPTY_ARRAY
      val dynamicFeatureModules = moduleSystem.getDynamicFeatureModules()
      for (module in dynamicFeatureModules) {
        val moduleResources = ResourceRepositoryManager.getModuleResources(module) ?: continue
        if (moduleResources.hasResources(resourceReference.namespace, resourceReference.resourceType, resourceReference.name)) {
          allItems.add(PsiElementResolveResult(ResourceReferencePsiElement(resourceReference, context.manager)))
        }
      }
    }
    return allItems.toArray(ResolveResult.EMPTY_ARRAY)
  }

  override fun getXmlAttributeNameGotoDeclarationTargets(
    attributeName: String,
    namespace: ResourceNamespace,
    context: PsiElement
  ): Array<out PsiElement> {
    return getGotoDeclarationTargets(ResourceReference.attr(namespace, attributeName), context)
  }

  override fun getGotoDeclarationTargets(resourceReference: ResourceReference, context: PsiElement): Array<out PsiElement> {
    return ResourceRepositoryManager.getInstance(context)
      ?.allResources
      ?.getResources(resourceReference)
      ?.mapNotNull { resolveToDeclaration(it, context.project) }
      .orEmpty()
      .toTypedArray()
  }

  /**
   * Returns the [SearchScope] for a resource based on the context element. This scope contains files that can contain references to the
   * same resource as the context element. This is necessary for a ReferencesSearch to only find references to resources that are in
   * modules which are in use scope.
   *
   * @param resourceReference [ResourceReference] of a resource.
   * @param context           [PsiElement] context element from which an action is being performed.
   * @return [SearchScope] a scope that contains the files of the project which can reference same resource as context element.
   */
  @JvmStatic
  fun getResourceSearchScope(resourceReference: ResourceReference, context: PsiElement): SearchScope {
    val gotoDeclarationTargets = getGotoDeclarationTargets(resourceReference, context)
    val allScopes = gotoDeclarationTargets.mapNotNull { ModuleUtilCore.findModuleForPsiElement(it)?.moduleWithDependentsScope }
    return if (allScopes.isEmpty()) {
      ProjectScope.getAllScope(context.project)
    }
    else {
      GlobalSearchScope.union(allScopes)
    }
  }

  /**
   * For areas of the IDE which want to pick a single resource declaration to navigate to, we pick the best possible [ResourceItem] based on
   * the supplied [FolderConfiguration], and if none exists, returns the first item returned by the [ResourceRepository]
   *
   * @param resourceReference [ResourceReference] of a resource.
   * @param context           [PsiElement] context element from which an action is being performed.
   * @param configuration     [FolderConfiguration] configuration provided that is used to pick a matching [ResourceItem].
   * @return [PsiElement] of the best matching resource declaration.
   */
  fun getBestGotoDeclarationTarget(
    resourceReference: ResourceReference,
    context: PsiElement,
    configuration: FolderConfiguration
  ): PsiElement? {
    val resources = ResourceRepositoryManager.getInstance(context)?.allResources?.getResources(resourceReference) ?: return null
    val resourceItem = configuration.findMatchingConfigurable(resources) ?: resources.firstOrNull() ?: return null
    return resolveToDeclaration(resourceItem, context.project)
  }
}
