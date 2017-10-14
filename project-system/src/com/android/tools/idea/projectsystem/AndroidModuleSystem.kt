/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provides a build-system-agnostic interface to the build system. Instances of this interface
 * contain methods that apply to a specific [Module].
 */
interface AndroidModuleSystem {
  /**
   * Requests information about the folder layout for the module. This can be used to determine
   * where files of various types should be written.
   *
   * TODO: Figure out and document the rest of the contracts for this method, such as how targetDirectory is used,
   * what the source set names are used for, and why the result is a list
   *
   * @param targetDirectory to filter the relevant source providers from the android facet.
   * @return a list of templates created from each of the android facet's source providers.
   * In cases where the source provider returns multiple paths, we always take the first match.
   */
  fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate>

  /**
   * Adds a dependency to the module.
   *
   * TODO: Figure out and document the format for the dependency strings
   */
  fun addDependency(dependency: String)

  /**
   * Returns the version of the given [artifactId] as accessible to sources contained in this module, or null if that dependency is
   * not available to sources contained in this module.
   */
  @Throws(DependencyManagementException::class)
  fun getResolvedVersion(artifactId: GoogleMavenArtifactId): GoogleMavenArtifactVersion?

  /**
   * Returns the version of the given [artifactId] accessible to sources contained in this module as declared in the build system,
   * or null if it is not specified. Build systems such as Gradle allow users to specify a dependency such as x.y.+, which it will
   * resolve to a specific version at build time. This method returns the version declared in the build script.
   * Use [AndroidProjectSystem.getResolvedVersion] if you want the resolved version.
   */
  @Throws(DependencyManagementException::class)
  fun getDeclaredVersion(artifactId: GoogleMavenArtifactId): GoogleMavenArtifactVersion?

  /**
   * Determines whether or not the underlying build system is capable of generating a PNG
   * from vector graphics.
   */
  fun canGeneratePngFromVectorGraphics(): CapabilityStatus

  /**
   * Determines whether or not the underlying build system supports instant run.
   */
  fun getInstantRunSupport(): CapabilityStatus
}
