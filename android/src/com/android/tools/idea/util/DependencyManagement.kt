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
@file:JvmName("DependencyManagementUtil")

package com.android.tools.idea.util

import com.android.SdkConstants
import com.android.annotations.VisibleForTesting
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.support.AndroidxName
import com.android.support.AndroidxNameUtils
import com.android.tools.idea.projectsystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil

/**
 * Returns true iff the dependency with [artifactId] is transitively available to this [module].
 * This function returns false if the project's dependency model is unavailable and therefore dependencies
 * could not be checked (e.g. Project is syncing with build system or any dependency management error occurs).
 * To handle dependency management errors, use methods defined in [AndroidProjectSystem] and catch
 * [DependencyManagementException].
 * @param artifactId the dependency's maven artifact id.
 */
fun Module.dependsOn(artifactId: GoogleMavenArtifactId): Boolean {
  try {
    // TODO this artifact to coordinate translation is temporary and will be removed when GradleCoordinates are swapped in for GoogleMavenArtifactId.
    val coordinate = GradleCoordinate(artifactId.mavenGroupId, artifactId.mavenArtifactId, "+")
    return getModuleSystem().getResolvedDependency(coordinate) != null
  }
  catch (e: DependencyManagementException) {
    Logger.getInstance(this.javaClass.name).warn(e.message)
  }
  return false
}

fun Module.getDependencies(): Sequence<GoogleMavenArtifactId> {
  try {
    return project.getProjectSystem().getModuleSystem(this).getDependencies()
  }
  catch (e: DependencyManagementException) {
    Logger.getInstance(this.javaClass.name).warn(e.message)
  }
  return emptySequence()
}

/**
 * Returns whether this module depends on the new support library artifacts (androidx)
 */
fun Module.dependsOnAndroidx(): Boolean = this.getDependencies().any { it.mavenGroupId.startsWith(SdkConstants.ANDROIDX_PKG) }

/**
 * Returns whether this module depends on the old support library artifacts (androidx)
 */
fun Module.dependsOnOldSupportLib(): Boolean = this.getDependencies().any { it.mavenGroupId.startsWith(SdkConstants.SUPPORT_LIB_GROUP_ID) }

fun Module?.mapAndroidxName(name: AndroidxName): String {
  val dependsOnAndroidx = this?.dependsOnAndroidx() ?: return name.defaultName()
  return if (dependsOnAndroidx) name.newName() else name.oldName()
}

fun Module.mapGradleCoordinateToAndroidx(coordinate: String): String {
  if (this.isDisposed || coordinate.isEmpty()) {
    return coordinate
  }

  return if (this.dependsOnAndroidx()) {
    AndroidxNameUtils.getCoordinateMapping(coordinate)
  }
  else coordinate
}

/**
 * Add artifacts with given artifact ids as dependencies; this method will show a dialog prompting the user for confirmation if
 * [promptUserBeforeAdding] is set to true and return with no-op if user chooses to not add the dependencies. If any of the dependencies
 * are added successfully and [requestSync] is set to true, this method will request a sync to make sure the artifacts are resolved.
 * In this case, the sync will happen asynchronously and this method will not wait for it to finish before returning.
 *
 * This method shows no confirmation dialog and performs a no-op if the list of artifacts is the empty list.
 * This method does not trigger a sync if none of the artifacts were added successfully or if [requestSync] is false.
 * @return list of artifacts that were not successfully added. i.e. If the returned list is empty, then all were added successfully.
 */
@JvmOverloads
fun Module.addDependencies(artifactIds: List<GoogleMavenArtifactId>, promptUserBeforeAdding: Boolean, requestSync: Boolean = true,
                           includePreview: Boolean = false)
    : List<GoogleMavenArtifactId> {

  if (artifactIds.isEmpty()) {
    return listOf()
  }

  val distinctArtifactIds = artifactIds.distinct()

  if (promptUserBeforeAdding && !userWantsToAdd(project, distinctArtifactIds)) {
    return distinctArtifactIds
  }

  val moduleSystem = getModuleSystem()
  val artifactsNotAdded = mutableListOf<GoogleMavenArtifactId>()
  val platformSupportLibVersion: GoogleMavenArtifactVersion? by lazy {
    GoogleMavenArtifactId.values()
      .filter { it.isPlatformSupportLibrary }
      .mapNotNull {
        moduleSystem.getDeclaredDependency(it.getCoordinate("+"))
      }
      .mapNotNull {
        //TODO This object creation here is a very temporary solution and will be replaced with GradleVersion with other parts
        //     of project-system's dependency management also uses GradleVersion.
        DependencyVersion(it.version)
      }
      .firstOrNull()
  }

  for (id in distinctArtifactIds) {
    try {
      if (id.isPlatformSupportLibrary) {
        moduleSystem.addDependencyWithoutSync(id, platformSupportLibVersion)
      }
      else {
        moduleSystem.addDependencyWithoutSync(id, null, includePreview)
      }
    }
    catch (e: DependencyManagementException) {
      Logger.getInstance(this.javaClass.name).warn(e.message)
      artifactsNotAdded.add(id)
    }
  }

  if (requestSync && distinctArtifactIds.size != artifactsNotAdded.size) {
    project.getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED, true)
  }

  return artifactsNotAdded
}

private data class DependencyVersion(override val mavenVersion: GradleVersion?) : GoogleMavenArtifactVersion {
  override fun equals(other: Any?) = other is GoogleMavenArtifactVersion && other.mavenVersion == mavenVersion
  override fun hashCode() = mavenVersion?.hashCode() ?: 0
}

private fun userWantsToAdd(project: Project, artifactIds: List<GoogleMavenArtifactId>): Boolean {
  if (ApplicationManager.getApplication().isUnitTestMode) {
    return true
  }
  return Messages.OK == Messages.showOkCancelDialog(project, createAddDependencyMessage(artifactIds), "Add Project Dependency", Messages.getErrorIcon())
}

@VisibleForTesting
fun createAddDependencyMessage(artifactIds: List<GoogleMavenArtifactId>): String {
  val libraryNames = artifactIds.joinToString(", ") { it.toString() }
  return "This operation requires the ${StringUtil.pluralize("library", artifactIds.size)} $libraryNames. \n\n" +
      "Would you like to add ${StringUtil.pluralize("this", artifactIds.size)} now?"
}
