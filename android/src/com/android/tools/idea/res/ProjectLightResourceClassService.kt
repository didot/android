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
package com.android.tools.idea.res

import com.android.builder.model.AaptOptions
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.projectmodel.AarLibrary
import com.android.tools.idea.findAarDependencies
import com.android.tools.idea.findAllAarsLibraries
import com.android.tools.idea.projectsystem.LightResourceClassService
import com.android.tools.idea.res.aar.AarResourceRepositoryCache
import com.android.tools.idea.util.androidFacet
import com.android.utils.concurrency.getAndUnwrap
import com.android.utils.concurrency.retainAll
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import com.intellij.ProjectTopics
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import org.jetbrains.android.dom.manifest.AndroidManifestUtils
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import java.io.IOException
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.annotation.concurrent.GuardedBy
import kotlin.concurrent.read
import kotlin.concurrent.write

private data class ResourceClasses(val namespaced: PsiClass?, val nonNamespaced: PsiClass?)

/**
 * A [LightResourceClassService] that provides R classes for local modules by finding manifests of all Android modules in the project.
 */
class ProjectLightResourceClassService(
  private val project: Project,
  private val psiManager: PsiManager,
  private val projectFacetManager: ProjectFacetManager,
  private val aarResourceRepositoryCache: AarResourceRepositoryCache,
  private val androidLightPackagesCache: AndroidLightPackage.InstanceCache
) : LightResourceClassService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = ServiceManager.getService(project, ProjectLightResourceClassService::class.java)!!
  }

  /** Cache of AAR package names. */
  private val aarPackageNamesCache: Cache<AarLibrary, String> = CacheBuilder.newBuilder().build()

  /** Cache of created classes for a given AAR. */
  private val aarClassesCache: Cache<AarLibrary, ResourceClasses> = CacheBuilder.newBuilder().build()

  /** Cache of created classes for a given AAR. */
  private val moduleClassesCache: Cache<AndroidFacet, ResourceClasses> = CacheBuilder.newBuilder().weakKeys().build()

  /**
   * [Multimap] of all [AarLibrary] dependencies in the project, indexed by their package name (read from Manifest).
   */
  @GuardedBy("aarLocationsLock")
  private var aarLocationsCache: Multimap<String, AarLibrary>? = null
  private val aarLocationsLock = ReentrantReadWriteLock()

  init {
    // Currently findAllAarsLibraries creates new (equal) instances of AarLibrary every time it's called, so we have to keep hard references
    // to AarLibrary keys, otherwise the entries will be collected. We can release unused light classes after a sync removes a library from
    // the project.
    project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object: ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent?) {
        val aars = getAllAars().values()
        aarPackageNamesCache.retainAll(aars)
        aarClassesCache.retainAll(aars)
      }
    })
  }

  override fun getLightRClasses(qualifiedName: String, scope: GlobalSearchScope): List<PsiClass> {
    val packageName = qualifiedName.dropLast(2)
    return (getModuleRClasses(packageName) + getAarRClasses(packageName))
      .flatMap { classes -> sequenceOf(classes.namespaced, classes.nonNamespaced) }
      .filterNotNull()
      .filter { PsiSearchScopeUtil.isInScope(scope, it) }
      .toList()
  }

  override fun getLightRClassesAccessibleFromModule(module: Module): Collection<PsiClass> {
    val namespacing = ResourceRepositoryManager.getOrCreateInstance(module)?.namespacing ?: return emptySet()
    val androidFacet = module.androidFacet ?: return emptySet()

    val result = mutableListOf<ResourceClasses>()

    result.add(getModuleRClasses(androidFacet))

    for (dependency in AndroidUtils.getAllAndroidDependencies(module, false)) {
      result.add(getModuleRClasses(dependency))
    }

    for (aarLibrary in findAarDependencies(module).values) {
      result.add(getAarRClasses(aarLibrary, getAarPackageName(aarLibrary)))
    }

    return result.mapNotNull { (namespaced, nonNamespaced) ->
      when (namespacing) {
        AaptOptions.Namespacing.REQUIRED -> namespaced
        AaptOptions.Namespacing.DISABLED -> nonNamespaced
      }
    }
  }

  private fun getModuleRClasses(packageName: String): Sequence<ResourceClasses> {
    return findAndroidFacetsWithPackageName(packageName).asSequence().map(::getModuleRClasses)
  }

  private fun getModuleRClasses(facet: AndroidFacet): ResourceClasses {
    return moduleClassesCache.getAndUnwrap(facet) {
      ResourceClasses(
        namespaced = ModulePackageRClass(psiManager, facet.module, AaptOptions.Namespacing.REQUIRED),
        nonNamespaced = ModulePackageRClass(psiManager, facet.module, AaptOptions.Namespacing.DISABLED)
      )
    }
  }

  private fun getAarRClasses(packageName: String): Sequence<ResourceClasses> {
    return getAllAars().get(packageName).asSequence().map { aarLibrary -> getAarRClasses(aarLibrary, packageName) }
  }

  private fun getAarRClasses(aarLibrary: AarLibrary, packageName: String): ResourceClasses {
    // Build the classes from what is currently on disk. They may be null if the necessary files are not there, e.g. the res.apk file
    // is required to build the namespaced class.
    return aarClassesCache.getAndUnwrap(aarLibrary) {
      ResourceClasses(
        namespaced = aarLibrary.resApkFile?.toFile()?.takeIf { it.exists() }?.let { resApk ->
          NamespacedAarPackageRClass(
            psiManager,
            packageName,
            aarResourceRepositoryCache.getProtoRepository(resApk, aarLibrary.address),
            ResourceNamespace.fromPackageName(packageName)
          )
        },
        nonNamespaced = aarLibrary.symbolFile.toFile()?.takeIf { it.exists() }?.let { symbolFile ->
          NonNamespacedAarPackageRClass(psiManager, packageName, symbolFile)
        }
      )
    }
  }

  override fun findRClassPackage(packageName: String): PsiPackage? {
    return if (getAllAars().containsKey(packageName) || findAndroidFacetsWithPackageName(packageName).isNotEmpty()) {
      androidLightPackagesCache.get(packageName)
    }
    else {
      null
    }
  }

  private fun findAndroidFacetsWithPackageName(packageName: String): List<AndroidFacet> {
    // TODO(b/77801019): cache this and figure out how to invalidate that cache.
    return projectFacetManager.getFacets(AndroidFacet.ID).filter { AndroidManifestUtils.getPackageName(it) == packageName }
  }

  private fun getAllAars(): Multimap<String, AarLibrary> {
    return aarLocationsLock.read {
      aarLocationsCache ?: aarLocationsLock.write {
        /** Check aarLocationsCache again, see [kotlin.concurrent.write]. */
        aarLocationsCache ?: run {
          Multimaps.index(findAllAarsLibraries(project).values) { getAarPackageName(it!!) }.also {
            aarLocationsCache = it
          }
        }
      }
    }
  }

  private fun getAarPackageName(aarLibrary: AarLibrary): String {
    return aarPackageNamesCache.getAndUnwrap(aarLibrary) {
      val fromManifest = try {
        aarLibrary.manifestFile.let(AndroidManifestUtils::getPackageNameFromManifestFile)
      }
      catch (e: IOException) {
        null
      }
      fromManifest ?: ""
    }
  }
}
