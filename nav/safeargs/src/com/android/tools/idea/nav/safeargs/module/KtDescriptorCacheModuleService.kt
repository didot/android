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
package com.android.tools.idea.nav.safeargs.module

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.index.NavXmlData
import com.android.tools.idea.nav.safeargs.index.NavXmlIndex
import com.android.tools.idea.nav.safeargs.psi.kotlin.KtArgsPackageDescriptor
import com.android.tools.idea.nav.safeargs.psi.kotlin.KtDirectionsPackageDescriptor
import com.android.tools.idea.nav.safeargs.psi.kotlin.getKotlinType
import com.android.tools.idea.nav.safeargs.psi.xml.SafeArgsXmlTag
import com.android.tools.idea.nav.safeargs.psi.xml.XmlSourceElement
import com.android.tools.idea.nav.safeargs.psi.xml.findXmlTagById
import com.android.tools.idea.nav.safeargs.safeArgsMode
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.xml.XmlTagImpl
import com.intellij.psi.xml.XmlFile
import com.intellij.util.PlatformIcons
import net.jcip.annotations.GuardedBy
import org.jetbrains.android.dom.manifest.getPackageName
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.impl.PackageFragmentDescriptorImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.storage.StorageManager

/**
 * A module service which stores safe args kt package descriptors([KtArgsPackageDescriptor]s and
 * [KtDirectionsPackageDescriptor]s) by querying from [NavXmlIndex].
 *
 */
class KtDescriptorCacheModuleService(val module: Module) {
  private val LOG get() = Logger.getInstance(KtDescriptorCacheModuleService::class.java)
  private val lock = Any()

  @GuardedBy("lock")
  private var lastModificationCount = -1L

  private data class QualifiedDescriptor(val fqName: FqName, val descriptor: PackageFragmentDescriptor)

  class NavEntryKt(
    val file: VirtualFile,
    val data: NavXmlData
  )

  companion object {
    @JvmStatic
    fun getInstance(module: Module) = module.getService(KtDescriptorCacheModuleService::class.java)!!
  }

  @GuardedBy("lock")
  var descriptorsCache = emptyMap<FqName, List<PackageFragmentDescriptor>>()

  fun getDescriptors(moduleDescriptor: ModuleDescriptor): Map<FqName, List<PackageFragmentDescriptor>> {
    ProgressManager.checkCanceled()

    if (module.androidFacet?.safeArgsMode != SafeArgsMode.KOTLIN) return emptyMap()

    if (DumbService.isDumb(module.project)) {
      LOG.warn("Safe Args classes may by temporarily stale due to indices not being ready right now.")
      return descriptorsCache
    }

    val packageFqName = AndroidFacet.getInstance(module)?.let {
      val packageName = getPackageName(it) ?: return@let null
      FqName(packageName)
    } ?: return descriptorsCache

    synchronized(lock) {
      val now = ModuleNavigationResourcesModificationTracker.getInstance(module).modificationCount

      if (lastModificationCount != now) {
        lastModificationCount = now
        val moduleNavResources = getNavResourceFromIndex()

        val packageResourceData = SafeArgSyntheticPackageResourceData(moduleNavResources)

        val packageDescriptors = packageResourceData.moduleNavResource
          .asSequence()
          .flatMap { navEntry ->
            val backingXmlFile = PsiManager.getInstance(module.project).findFile(navEntry.file)
            val sourceElement = backingXmlFile?.let { XmlSourceElement(it) } ?: SourceElement.NO_SOURCE

            val packages = createArgsPackages(moduleDescriptor, navEntry, sourceElement, packageFqName.asString()) +
                           createDirectionsPackages(moduleDescriptor, navEntry, sourceElement, packageFqName.asString())

            packages.asSequence()
          }
          .groupBy({ it.fqName }, { it.descriptor })

        descriptorsCache = packageDescriptors
      }

      // TODO(b/159950623): Consolidate with SafeArgsCacheModuleService
      return descriptorsCache
    }
  }

  private fun createDirectionsPackages(
    moduleDescriptor: ModuleDescriptor,
    entry: NavEntryKt,
    sourceElement: SourceElement,
    modulePackage: String,
    storageManager: StorageManager = LockBasedStorageManager.NO_LOCKS
  ): Collection<QualifiedDescriptor> {
    return entry.data.root.allDestinations
      .asSequence()
      .filter { destination -> destination.actions.isNotEmpty() }
      .mapNotNull { destination ->
        val fqName = destination.name.let { name ->
          val resolvedName = if (!name.startsWith('.')) name else "$modulePackage$name"
          resolvedName + "Directions"
        }

        val className = fqName.substringAfterLast('.').let { Name.identifier(it) }
        val packageName = FqName(fqName.substringBeforeLast('.'))

        val resolvedSourceElement = (sourceElement.getPsi() as? XmlFile)
                                      ?.findXmlTagById(destination.id)
                                      ?.let {
                                        XmlSourceElement(
                                          SafeArgsXmlTag(it as XmlTagImpl, PlatformIcons.CLASS_ICON, className.asString()))
                                      }
                                    ?: sourceElement

        val packageDescriptor = KtDirectionsPackageDescriptor(SafeArgsModuleInfo(moduleDescriptor, module), packageName, className,
                                                              destination, entry.data, resolvedSourceElement, storageManager)

        QualifiedDescriptor(packageName, packageDescriptor)
      }
      .toList()
  }

  private fun createArgsPackages(
    moduleDescriptor: ModuleDescriptor,
    entry: NavEntryKt,
    sourceElement: SourceElement,
    modulePackage: String,
    storageManager: StorageManager = LockBasedStorageManager.NO_LOCKS
  ): Collection<QualifiedDescriptor> {
    return entry.data.root.allDestinations
      .asSequence()
      .filter { fragment -> fragment.arguments.isNotEmpty() }
      .mapNotNull { fragment ->

        val fqName = fragment.name.let { name ->
          val resolvedName = if (!name.startsWith('.')) name else "$modulePackage$name"
          resolvedName + "Args"
        }

        val className = fqName.substringAfterLast('.').let { Name.identifier(it) }
        val packageName = FqName(fqName.substringBeforeLast('.'))

        val resolvedSourceElement = (sourceElement.getPsi() as? XmlFile)
                                      ?.findXmlTagById(fragment.id)
                                      ?.let {
                                        XmlSourceElement(
                                          SafeArgsXmlTag(it as XmlTagImpl, PlatformIcons.CLASS_ICON, className.asString()))
                                      }
                                    ?: sourceElement

        val superTypesProvider = { packageDescriptor: PackageFragmentDescriptorImpl ->
          val ktType = packageDescriptor.builtIns.getKotlinType("androidx.navigation.NavArgs", null, packageDescriptor.module)
          listOf(ktType)
        }

        val packageDescriptor = KtArgsPackageDescriptor(SafeArgsModuleInfo(moduleDescriptor, module), packageName, className, fragment,
                                                        superTypesProvider, resolvedSourceElement, storageManager)

        QualifiedDescriptor(packageName, packageDescriptor)
      }
      .toList()
  }

  private fun getNavResourceFromIndex(): List<NavEntryKt> {
    val facet = AndroidFacet.getInstance(module) ?: return emptyList()
    val moduleResources = ResourceRepositoryManager.getModuleResources(facet)
    val navResources = moduleResources.getResources(ResourceNamespace.RES_AUTO, ResourceType.NAVIGATION)

    return navResources.values()
      .mapNotNull { resource ->
        val file = resource.getSourceAsVirtualFile() ?: return@mapNotNull null
        val project = facet.module.project
        val data = NavXmlIndex.getDataForFile(project, file) ?: return@mapNotNull null
        NavEntryKt(file, data)
      }
  }
}

class SafeArgSyntheticPackageResourceData(val moduleNavResource: Collection<KtDescriptorCacheModuleService.NavEntryKt>)
class SafeArgsModuleInfo(val moduleDescriptor: ModuleDescriptor, val module: Module)