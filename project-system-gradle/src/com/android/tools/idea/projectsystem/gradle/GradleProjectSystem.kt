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
package com.android.tools.idea.projectsystem.gradle

import com.android.builder.model.AndroidProject.PROJECT_TYPE_APP
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.idea.gradle.npw.project.GradleAndroidProjectPaths
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystemProvider
import com.android.tools.idea.projectsystem.AndroidSourceSet
import com.android.tools.idea.sdk.AndroidSdks
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Contract
import java.nio.file.Path

class GradleProjectSystem(val project: Project) : AndroidProjectSystem, AndroidProjectSystemProvider {
  val ID = "com.android.tools.idea.GradleProjectSystem"

  override val id: String
    get() = ID

  override fun getPathToAapt(): Path {
    return AaptInvoker.getPathToAapt(AndroidSdks.getInstance().tryToChooseSdkHandler(), LogWrapper(GradleProjectSystem::class.java))
  }

  override fun isApplicable(): Boolean {
    return GradleProjectInfo.getInstance(project).isBuildWithGradle
  }

  override fun allowsFileCreation() = true

  override fun getDefaultApkFile(): VirtualFile? {
    return ModuleManager.getInstance(project).modules.asSequence()
        .mapNotNull { AndroidModuleModel.get(it) }
        .filter { it.androidProject.projectType == PROJECT_TYPE_APP }
        .flatMap { it.selectedVariant.mainArtifact.outputs.asSequence() }
        .map { it.mainOutputFile.outputFile }
        .find { it.exists() }
        ?.let { VfsUtil.findFileByIoFile(it, true) }
  }

  override fun buildProject() {
    GradleProjectBuilder.getInstance(project).compileJava()
  }

  override fun syncProject(reason: AndroidProjectSystem.SyncReason, requireSourceGeneration: Boolean): ListenableFuture<AndroidProjectSystem.SyncResult> {
    val syncResult = SettableFuture.create<AndroidProjectSystem.SyncResult>()

    if (GradleSyncState.getInstance(project).isSyncInProgress) {
      syncResult.setException(RuntimeException("A sync was requested while one is already in progress. Use"
          + "GradleSyncState.isSyncInProgress to detect this scenario."))
    }
    else if (project.isInitialized) {
      syncResult.setFuture(requestSync(reason, requireSourceGeneration))

    }
    else {
      StartupManager.getInstance(project).runWhenProjectIsInitialized {
        if (!GradleProjectInfo.getInstance(project).isNewOrImportedProject) {
          // http://b/62543184
          // If the project was created with the "New Project" wizard, there is no need to sync again.
          syncResult.setFuture(requestSync(reason, requireSourceGeneration))
        }
        else {
          syncResult.set(AndroidProjectSystem.SyncResult.SKIPPED)
        }
      }
    }

    return syncResult
  }

  @Contract(pure = true)
  private fun convertReasonToTrigger(reason: AndroidProjectSystem.SyncReason): GradleSyncStats.Trigger {
    return if (reason === AndroidProjectSystem.SyncReason.PROJECT_LOADED) {
      GradleSyncStats.Trigger.TRIGGER_PROJECT_LOADED
    }
    else if (reason === AndroidProjectSystem.SyncReason.PROJECT_MODIFIED) {
      GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED
    }
    else {
      GradleSyncStats.Trigger.TRIGGER_USER_REQUEST
    }
  }

  private fun requestSync(reason: AndroidProjectSystem.SyncReason, requireSourceGeneration: Boolean): ListenableFuture<AndroidProjectSystem.SyncResult> {
    val trigger = convertReasonToTrigger(reason)
    val syncResult = SettableFuture.create<AndroidProjectSystem.SyncResult>()

    val listener = object : GradleSyncListener.Adapter() {
      override fun syncSucceeded(project: Project) {
        syncResult.set(AndroidProjectSystem.SyncResult.SUCCESS)
      }

      override fun syncFailed(project: Project, errorMessage: String) {
        syncResult.set(AndroidProjectSystem.SyncResult.FAILURE)
      }

      override fun syncSkipped(project: Project) {
        syncResult.set(AndroidProjectSystem.SyncResult.SKIPPED)
      }
    }

    val request = GradleSyncInvoker.Request().setTrigger(trigger)
        .setGenerateSourcesOnSuccess(requireSourceGeneration).setRunInBackground(true)

    if (GradleProjectInfo.getInstance(project).isNewOrImportedProject) {
      request.setNewOrImportedProject()
    }

    try {
      GradleSyncInvoker.getInstance().requestProjectSync(project, request, listener)
    }
    catch (t: Throwable) {
      syncResult.setException(t)
    }

    return syncResult
  }

  override val projectSystem = this

  override fun getSourceSets(module: Module, targetDirectory: VirtualFile?): List<AndroidSourceSet> {
    return GradleAndroidProjectPaths.getSourceSets(module, targetDirectory)
  }
}
