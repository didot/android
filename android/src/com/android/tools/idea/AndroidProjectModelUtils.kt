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

/**
 * Helper functions for functionality that should be easy to do with the universal project model, but for now needs to be implemented using
 * whatever we have.
 *
 * TODO: remove all of this once we have the project model.
 */
@file:JvmName("AndroidProjectModelUtils")

package com.android.tools.idea

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.DOT_AAR
import com.android.SdkConstants.FD_JARS
import com.android.SdkConstants.LIBS_FOLDER
import com.android.builder.model.AaptOptions
import com.android.projectmodel.ExternalLibrary
import com.android.tools.idea.projectsystem.FilenameConstants
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

/**
 * Returns information about all [ExternalLibrary] dependencies that contribute resources in the project, indexed by
 * [ExternalLibrary.address] which is unique within a project.
 *
 * TODO: ExternalLibrary.address is unique within an [AndroidProject], not necessarily within a [Project]
 */
fun findAllLibrariesWithResources(project: Project): Map<String, ExternalLibrary> {
  return ModuleManager.getInstance(project)
    .modules
    .asSequence()
    .map(::findDependenciesWithResources)
    .fold(HashMap()) { inProject, inModule ->
      inProject.putAll(inModule)
      inProject
    }
}

/**
 * Returns information about all [ExternalLibrary] dependencies that contribute resources in a given module, indexed by
 * [ExternalLibrary.address] which is unique within a project.
 */
fun findDependenciesWithResources(module: Module): Map<String, ExternalLibrary> {
  return module.getModuleSystem()
    .getDependentLibraries()
    .filterIsInstance<ExternalLibrary>()
    .filter { it.hasResources }
    .associateBy { library -> library.address }
}

/**
 * Tries to find the resources folder corresponding to a given `classes.jar` file extracted from an AAR.
 *
 * TODO: make it private and part of building the model for legacy projects where guessing is the best we can do.
 */
@Deprecated("Use AndroidProjectModelUtils.findAllLibrariesWithResources instead of processing jar files and looking for resources.")
fun findResFolder(jarFile: File): File? {
  // We need to figure out the layout of the resources relative to the jar file. This changed over time, so we check for different
  // layouts until we find one we recognize.
  var resourcesDirectory: File? = null

  var aarDir = jarFile.parentFile
  if (aarDir.path.endsWith(DOT_AAR) || aarDir.path.contains(FilenameConstants.EXPLODED_AAR)) {
    if (aarDir.path.contains(FilenameConstants.EXPLODED_AAR)) {
      if (aarDir.path.endsWith(LIBS_FOLDER)) {
        // Some libraries recently started packaging jars inside a sub libs folder inside jars
        aarDir = aarDir.parentFile
      }
      // Gradle plugin version 1.2.x and later has classes in aar-dir/jars/
      if (aarDir.path.endsWith(FD_JARS)) {
        aarDir = aarDir.parentFile
      }
    }
    val path = aarDir.path
    if (path.endsWith(DOT_AAR) || path.contains(FilenameConstants.EXPLODED_AAR)) {
      resourcesDirectory = aarDir
    }
  }

  if (resourcesDirectory == null) {
    // Build cache? We need to compute the package name in a slightly different way.
    val parentFile = aarDir.parentFile
    if (parentFile != null) {
      val manifest = File(parentFile, ANDROID_MANIFEST_XML)
      if (manifest.exists()) {
        resourcesDirectory = parentFile
      }
    }
    if (resourcesDirectory == null) {
      return null
    }
  }
  return resourcesDirectory
}

/**
 * Checks namespacing of the module with the given [AndroidFacet].
 */
val AndroidFacet.namespacing: AaptOptions.Namespacing get() = configuration.model?.namespacing ?: AaptOptions.Namespacing.DISABLED
