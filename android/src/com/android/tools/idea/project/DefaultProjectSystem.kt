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
package com.android.tools.idea.project

import com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT
import com.android.SdkConstants.SUPPORT_LIB_ARTIFACT
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.model.MergedManifest
import com.android.tools.idea.projectsystem.*
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.res.AndroidResourceClassPsiElementFinder
import com.android.tools.idea.res.ProjectLightResourceClassService
import com.android.tools.idea.res.ResourceTypeClassFinder
import com.android.tools.idea.sdk.AndroidSdks
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementFinder
import com.intellij.ui.AppUIUtil
import org.jetbrains.android.facet.AndroidFacet
import java.nio.file.Path

/**
 * This implementation of AndroidProjectSystem is used for projects where the build system is not
 * recognized. It provides a minimal set of capabilities and opts out of most optional behaviors.
 */
class DefaultProjectSystem(val project: Project) : AndroidProjectSystem, AndroidProjectSystemProvider {
  override val id: String = ""

  override fun getDefaultApkFile(): VirtualFile? = null

  override fun getPathToAapt(): Path {
    return AaptInvoker.getPathToAapt(AndroidSdks.getInstance().tryToChooseSdkHandler(), LogWrapper(DefaultProjectSystem::class.java))
  }

  override fun isApplicable() = false

  override fun allowsFileCreation() = false

  override fun buildProject() {
  }

  override fun getSyncManager(): ProjectSystemSyncManager = object: ProjectSystemSyncManager {
    override fun syncProject(reason: SyncReason, requireSourceGeneration: Boolean): ListenableFuture<SyncResult> {
      AppUIUtil.invokeLaterIfProjectAlive(project, {
        project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS)
      })
      return Futures.immediateFuture(SyncResult.SUCCESS)
    }

    override fun isSyncInProgress() = false
    override fun isSyncNeeded() = false
    override fun getLastSyncResult() = SyncResult.SUCCESS
  }

  override fun mergeBuildFiles(dependencies: String, destinationContents: String, supportLibVersionFilter: String?): String {
    return destinationContents
  }

  override val projectSystem = this

  override fun upgradeProjectToSupportInstantRun(): Boolean {
    return false
  }

  override fun getAvailableDependency(coordinate: GradleCoordinate, includePreview: Boolean): GradleCoordinate? = null

  override fun getModuleSystem(module: Module): AndroidModuleSystem {
    return object : AndroidModuleSystem {
      override fun registerDependency(coordinate: GradleCoordinate) {}

      override fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate? = null

      override fun getResolvedDependency(coordinate: GradleCoordinate): GradleCoordinate? {
        // TODO(b/79883422): Replace the following code with the correct logic for detecting .aar dependencies.
        // The following if / else if chain maintains previous support for supportlib and appcompat until
        // we can determine it's safe to take away.
        if (SUPPORT_LIB_ARTIFACT == "${coordinate.groupId}:${coordinate.artifactId}") {
          val entries = ModuleRootManager.getInstance(module).orderEntries
          for (orderEntry in entries) {
            if (orderEntry is LibraryOrderEntry) {
              val classes = orderEntry.getRootFiles(OrderRootType.CLASSES)
              for (file in classes) {
                if (file.name == "android-support-v4.jar") {
                  return GoogleMavenArtifactId.SUPPORT_V4.getCoordinate("+")
                }
              }
            }
          }
        }
        else if (APPCOMPAT_LIB_ARTIFACT == "${coordinate.groupId}:${coordinate.artifactId}") {
          val entries = ModuleRootManager.getInstance(module).orderEntries
          for (orderEntry in entries) {
            if (orderEntry is ModuleOrderEntry) {
              val moduleForEntry = orderEntry.module
              if (moduleForEntry == null || moduleForEntry == module) {
                continue
              }
              AndroidFacet.getInstance(moduleForEntry) ?: continue
              val manifestInfo = MergedManifest.get(moduleForEntry)
              if ("android.support.v7.appcompat" == manifestInfo.`package`) {
                return GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
              }
            }
          }
        }

        return null
      }

      override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
        return emptyList()
      }

      override fun canGeneratePngFromVectorGraphics(): CapabilityStatus {
        return CapabilityNotSupported()
      }

      override fun getInstantRunSupport(): CapabilityStatus {
        return CapabilityNotSupported()
      }
    }
  }

  override fun getPsiElementFinders(): List<PsiElementFinder> {
    return if (StudioFlags.IN_MEMORY_R_CLASSES.get()) {
      listOf(
        ResourceTypeClassFinder.INSTANCE,
        AndroidResourceClassPsiElementFinder(ProjectLightResourceClassService.getInstance(project))
      )
    } else {
      listOf(ResourceTypeClassFinder.INSTANCE)
    }
  }

  override fun getAugmentRClasses() = !StudioFlags.IN_MEMORY_R_CLASSES.get()
}
