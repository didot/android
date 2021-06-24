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
package org.jetbrains.android.uipreview.classloading

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.AndroidManifestPackageNameUtils
import com.android.ide.common.resources.ResourceRepository
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.model.Namespacing
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.ResourceClassRegistry
import com.android.tools.idea.res.ResourceIdManager
import com.android.tools.idea.res.ResourceIdManager.Companion.get
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.util.VirtualFileSystemOpener.recognizes
import com.android.tools.idea.util.toVirtualFile
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import org.jetbrains.android.dom.manifest.getPackageName
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.uipreview.ModuleClassLoader
import org.jetbrains.android.util.AndroidUtils
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.regex.Pattern

/**
 * Finds the `packageName` for a given external library.
 */
private fun ExternalAndroidLibrary.getResolvedPackageName(): String? {
  if (packageName != null) {
    return packageName
  }
  return manifestFile?.let {
    return try {
      AndroidManifestPackageNameUtils.getPackageNameFromManifestFile(it)
    }
    catch (ignore: IOException) {
      // Workaround for https://issuetracker.google.com/127647973
      // Until fixed, the VFS might have an outdated view of the gradle cache directory. Some manifests might appear as missing but they
      // are actually there. In those cases, we issue a refresh call to fix the problem.
      if (recognizes(it)) {
        manifestFile.toVirtualFile(true)
        try {
          return AndroidManifestPackageNameUtils.getPackageNameFromManifestFile(it)
        }
        catch (_: IOException) {
        }
      }
      null
    }
  }
}

/**
 * Register this [ExternalAndroidLibrary] with the [ResourceClassRegistry].
 */
private fun ExternalAndroidLibrary.registerLibraryResources(
  repositoryManager: ResourceRepositoryManager,
  classRegistry: ResourceClassRegistry,
  idManager: ResourceIdManager) {
  val appResources = repositoryManager.appResources

  // Choose which resources should be in the generated R class. This is described in the JavaDoc of ResourceClassGenerator.
  val rClassContents: ResourceRepository
  val resourcesNamespace: ResourceNamespace
  val packageName: String?
  if (repositoryManager.namespacing === Namespacing.DISABLED) {
    packageName = getResolvedPackageName() ?: return
    rClassContents = appResources
    resourcesNamespace = ResourceNamespace.RES_AUTO
  }
  else {
    val aarResources = repositoryManager.findLibraryResources(this) ?: return
    rClassContents = aarResources
    resourcesNamespace = aarResources.namespace
    packageName = aarResources.packageName
  }
  classRegistry.addLibrary(rClassContents, idManager, packageName, resourcesNamespace)
}

/**
 * Register all the [Module] resources, including libraries and dependencies with the [ResourceClassRegistry].
 */
private fun registerResources(module: Module) {
  val androidFacet: AndroidFacet = AndroidFacet.getInstance(module) ?: return
  val repositoryManager = ResourceRepositoryManager.getInstance(androidFacet)
  val idManager = get(module)
  val classRegistry = ResourceClassRegistry.get(module.project)

  // If final ids are used, we will read the real class from disk later (in loadAndParseRClass), using this class loader. So we
  // can't treat it specially here, or we will read the wrong bytecode later.
  if (!idManager.finalIdsUsed) {
    classRegistry.addLibrary(repositoryManager.appResources,
                             idManager,
                             ReadAction.compute<String?, RuntimeException> {
                               getPackageName(androidFacet)
                             },
                             repositoryManager.namespace)
  }

  AndroidUtils.getAllAndroidDependencies(module, false)
    .distinct()
    .forEach { facet ->
      classRegistry.addLibrary(repositoryManager.appResources,
                               idManager,
                               ReadAction.compute<String?, RuntimeException> {
                                 getPackageName(facet)
                               },
                               repositoryManager.namespace)
    }

  module.getModuleSystem().getAndroidLibraryDependencies()
    .filter { it.hasResources }
    .forEach { it.registerLibraryResources(repositoryManager, classRegistry, idManager) }
}

// matches foo.bar.R or foo.bar.R$baz
private val RESOURCE_CLASS_NAME = Pattern.compile(".+\\.R(\\$[^.]+)?$")
private fun isResourceClassName(className: String): Boolean = RESOURCE_CLASS_NAME.matcher(className).matches()

/**
 * [ClassLoader] responsible for loading the `R` class from libraries and dependencies of the given module.
 */
class LibraryResourceClassLoader(parent: ClassLoader?, module: Module) : ClassLoader(parent) {
  val moduleRef = WeakReference(module)

  init {
    registerResources(module)
  }

  private fun findResourceClass(name: String): Class<*> {
    val module = moduleRef.get() ?: throw ClassNotFoundException(name)
    if (!isResourceClassName(name)) {
      throw ClassNotFoundException(name)
    }

    val facet: AndroidFacet = AndroidFacet.getInstance(module) ?: throw ClassNotFoundException(name)
    val repositoryManager = ResourceRepositoryManager.getInstance(facet)
    val data = ResourceClassRegistry.get(module.project).findClassDefinition(name, repositoryManager) ?: throw ClassNotFoundException(name)
    Logger.getInstance(ModuleClassLoader::class.java).debug("  Defining class from AAR registry")
    return defineClass(name, data, 0, data.size)
  }

  override fun findClass(name: String): Class<*> =
    try {
      super.findClass(name)
    }
    catch (e: ClassNotFoundException) {
      findResourceClass(name)
    }
}