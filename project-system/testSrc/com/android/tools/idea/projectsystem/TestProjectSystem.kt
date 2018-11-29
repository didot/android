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

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.util.PathString
import com.android.projectmodel.Library
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.google.common.collect.HashMultimap
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.AppUIUtil
import java.nio.file.Path
import java.util.concurrent.CountDownLatch

/**
 * This implementation of AndroidProjectSystem is used during integration tests and includes methods
 * to stub project system functionalities.
 */
class TestProjectSystem @JvmOverloads constructor(val project: Project,
                                                  availableDependencies: List<GradleCoordinate> = listOf(),
                                                  @Volatile private var lastSyncResult: SyncResult = SyncResult.SUCCESS)
  : AndroidProjectSystem, AndroidProjectSystemProvider {

  private val dependenciesByModule: HashMultimap<Module, GradleCoordinate> = HashMultimap.create()
  private val availablePreviewDependencies: List<GradleCoordinate>
  private val availableStableDependencies: List<GradleCoordinate>

  init {
    val sortedHighToLowDeps = availableDependencies.sortedWith(GradleCoordinate.COMPARE_PLUS_HIGHER).reversed()
    val (previewDeps, stableDeps) = sortedHighToLowDeps.partition(GradleCoordinate::isPreview)
    availablePreviewDependencies = previewDeps
    availableStableDependencies = stableDeps
  }

  /**
   * Adds the given artifact to the given module's list of dependencies.
   */
  fun addDependency(artifactId: GoogleMavenArtifactId, module: Module, mavenVersion: GradleVersion) {
    val coordinate = artifactId.getCoordinate(mavenVersion.toString())
    dependenciesByModule.put(module, coordinate)
  }

  /**
   * @return the set of dependencies added to the given module.
   */
  fun getAddedDependencies(module: Module): Set<GradleCoordinate> = dependenciesByModule.get(module)

  override val id: String = "com.android.tools.idea.projectsystem.TestProjectSystem"

  override val projectSystem = this

  override fun isApplicable(): Boolean = true

  override fun getModuleSystem(module: Module): AndroidModuleSystem {
    return object : AndroidModuleSystem {
      override fun analyzeDependencyCompatibility(dependenciesToAdd: List<GradleCoordinate>)
        : Triple<List<GradleCoordinate>, List<GradleCoordinate>, String> {
        val found = mutableListOf<GradleCoordinate>()
        val missing = mutableListOf<GradleCoordinate>()
        for (dependency in dependenciesToAdd) {
          val wildcardCoordinate = GradleCoordinate(dependency.groupId!!, dependency.artifactId!!, "+")
          val lookup = availableStableDependencies.firstOrNull { it.matches(wildcardCoordinate) }
                       ?: availablePreviewDependencies.firstOrNull { it.matches(wildcardCoordinate) }
          if (lookup != null) {
            found.add(lookup)
          }
          else {
            missing.add(dependency)
          }
        }
        return Triple(found, missing, "")
      }

      override fun getResolvedDependentLibraries(): Collection<Library> {
        return emptySet()
      }

      override fun canRegisterDependency(type: DependencyType): CapabilityStatus {
        return CapabilitySupported()
      }

      override fun registerDependency(coordinate: GradleCoordinate) {
        registerDependency(coordinate, DependencyType.IMPLEMENTATION)
      }

      override fun registerDependency(coordinate: GradleCoordinate, type: DependencyType) {
        dependenciesByModule.put(module, coordinate)
      }

      override fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate? =
        dependenciesByModule[module].firstOrNull { it.matches(coordinate) }

      override fun getResolvedDependency(coordinate: GradleCoordinate): GradleCoordinate? =
        dependenciesByModule[module].firstOrNull { it.matches(coordinate) }

      override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
        return emptyList()
      }

      override fun canGeneratePngFromVectorGraphics(): CapabilityStatus {
        return CapabilityNotSupported()
      }

      override fun getInstantRunSupport(): CapabilityStatus {
        return CapabilityNotSupported()
      }

      override fun findClassFile(fqcn: String): VirtualFile? = null

      override fun getOrCreateSampleDataDirectory(): PathString? = null

      override fun getSampleDataDirectory(): PathString? = null
    }
  }

  fun emulateSync(result: SyncResult) {
    val latch = CountDownLatch(1)

    AppUIUtil.invokeLaterIfProjectAlive(project) {
      lastSyncResult = result
      project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(result)
      latch.countDown()
    }

    latch.await()
  }

  override fun getSyncManager(): ProjectSystemSyncManager = object : ProjectSystemSyncManager {
    override fun syncProject(reason: SyncReason, requireSourceGeneration: Boolean): ListenableFuture<SyncResult> {
      emulateSync(SyncResult.SUCCESS)
      return Futures.immediateFuture(SyncResult.SUCCESS)
    }

    override fun isSyncInProgress() = false

    override fun isSyncNeeded() = !lastSyncResult.isSuccessful

    override fun getLastSyncResult() = lastSyncResult
  }

  override fun getDefaultApkFile(): VirtualFile? {
    TODO("not implemented")
  }

  override fun getPathToAapt(): Path {
    TODO("not implemented")
  }

  override fun buildProject() {
    TODO("not implemented")
  }

  override fun allowsFileCreation(): Boolean {
    TODO("not implemented")
  }

  override fun upgradeProjectToSupportInstantRun(): Boolean {
    TODO("not implemented")
  }

  override fun mergeBuildFiles(dependencies: String, destinationContents: String, supportLibVersionFilter: String?): String {
    TODO("not implemented")
  }

  override fun getPsiElementFinders() = emptyList<PsiElementFinder>()

  override fun getAugmentRClasses() = true

  override fun getLightResourceClassService(): LightResourceClassService {
    return object : LightResourceClassService {
      override fun getLightRClasses(qualifiedName: String, scope: GlobalSearchScope) = emptyList<PsiClass>()
      override fun getLightRClassesAccessibleFromModule(module: Module, includeTests: Boolean) = emptyList<PsiClass>()
      override fun getLightRClassesContainingModuleResources(module: Module) = emptyList<PsiClass>()
      override fun findRClassPackage(qualifiedName: String): PsiPackage? = null
      override fun getAllLightRClasses() = emptyList<PsiClass>()
    }
  }
}
