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
package org.jetbrains.android.refactoring.namespaces

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import com.android.resources.ResourceType
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.packageToRClass
import com.google.common.collect.Maps
import com.google.common.collect.Table
import com.google.common.collect.Tables
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMigration
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.refactoring.findOrCreateClass

/**
 * Information about an Android resource reference.
 *
 * Once they are all found, [inferredPackage] is computed in the context of the module being refactored. If it can be determined, it is
 * set to a non-null value by the time the refactoring processor performs the refactoring.
 */
internal abstract class ResourceUsageInfo : UsageInfo {
  constructor(element: PsiElement, startOffset: Int, endOffset: Int) : super(element, startOffset, endOffset)
  constructor(element: PsiElement) : super(element)

  abstract val resourceType: ResourceType
  abstract val name: String
  var inferredPackage: String? = null
}

/**
 * [ResourceUsageInfo] for references to R class fields in Java/Kotlin.
 */
internal class CodeUsageInfo(
  /** The field reference, used in "find usages" view. */
  fieldReferenceExpression: PsiElement,

  /** The "R" reference itself, will be rebound to the right class. */
  val classReference: PsiReference,

  override val resourceType: ResourceType,
  override val name: String
) : ResourceUsageInfo(fieldReferenceExpression) {
  fun updateClassReference(psiMigration: PsiMigration) {
    val reference = classReference
    reference.bindToElement(
      findOrCreateClass(
        classReference.element.project,
        psiMigration,
        packageToRClass(inferredPackage ?: return),

        // We're dealing with light R classes, so need to pick the right scope here. This will be handled by
        // AndroidResolveScopeEnlarger.
        scope = reference.element.resolveScope
      )
    )
  }
}

/**
 * Finds usages of the R classes defined by the module corresponding to [facet]. This includes the `androidTest` R class.
 */
internal fun findUsagesOfRClassesFromModule(facet: AndroidFacet): Collection<CodeUsageInfo> {
  val result = mutableListOf<CodeUsageInfo>()
  val module = facet.module
  val moduleRepo = ResourceRepositoryManager.getModuleResources(facet)

  val rClasses = module.project.getProjectSystem()
    .getLightResourceClassService()
    .getLightRClassesDefinedByModule(module, true)

  for (rClass in rClasses) {
    for (psiReference in ReferencesSearch.search(rClass, rClass.useScope)) {
      // TODO(b/137180850): handle Kotlin
      val classRef = psiReference.element as? PsiReferenceExpression ?: continue
      val typeRef = classRef.parent as? PsiReferenceExpression ?: continue
      val nameRef = typeRef.parent as? PsiReferenceExpression ?: continue

      // Make sure the PSI structure is as expected for something like "R.string.app_name":
      if (nameRef.qualifierExpression != typeRef || typeRef.qualifierExpression != classRef) continue

      val name = nameRef.referenceName ?: continue
      val resourceType = ResourceType.fromClassName(typeRef.referenceName ?: continue) ?: continue
      if (!moduleRepo.hasResources(ResourceNamespace.RES_AUTO, resourceType, name)) {
        result += CodeUsageInfo(
          fieldReferenceExpression = nameRef,
          classReference = psiReference,
          resourceType = resourceType,
          name = name
        )
      }
    }
  }

  return result
}

internal fun inferPackageNames(
  facet: AndroidFacet,
  result: Collection<ResourceUsageInfo>,
  progressIndicator: ProgressIndicator
) {
  val leafRepos = ResourceRepositoryManager.getAppResources(facet).leafResourceRepositories

  val inferredNamespaces: Table<ResourceType, String, String> =
    Tables.newCustomTable(Maps.newEnumMap(ResourceType::class.java)) { mutableMapOf<String, String>() }

  val total = result.size.toDouble()

  // TODO(b/78765120): try doing this in parallel using a thread pool.
  result.forEachIndexed { index, resourceUsageInfo ->
    ProgressManager.checkCanceled()

    resourceUsageInfo.inferredPackage = inferredNamespaces.row(resourceUsageInfo.resourceType).computeIfAbsent(resourceUsageInfo.name) {
      for (repo in leafRepos) {
        if (repo.hasResources(ResourceNamespace.RES_AUTO, resourceUsageInfo.resourceType, resourceUsageInfo.name)) {
          // TODO(b/78765120): check other repos and build a list of unresolved or conflicting references, to display in a UI later.
          return@computeIfAbsent (repo as SingleNamespaceResourceRepository).packageName
        }
      }

      null
    }

    progressIndicator.fraction = (index + 1) / total
  }
}
