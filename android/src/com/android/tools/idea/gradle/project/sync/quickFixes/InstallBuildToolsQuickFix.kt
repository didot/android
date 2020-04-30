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
package com.android.tools.idea.gradle.project.sync.quickFixes

import com.android.repository.Revision
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.issues.processor.FixBuildToolsProcessor
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.CompletableFuture

class InstallBuildToolsQuickFix(private val version: String,
                                private val buildFiles: List<VirtualFile>,
                                private val removeBuildTools: Boolean): BuildIssueQuickFix {
  override val id = "install.build.tools"

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    val future = CompletableFuture<Any>()
    invokeLater {
      val minBuildToolsVersion = Revision.parseRevision(version)
      val dialog = SdkQuickfixUtils.createDialogForPaths(project, listOf(DetailsTypes.getBuildToolsPath(minBuildToolsVersion)))
      if (dialog != null && dialog.showAndGet()) {
        if (buildFiles.isNotEmpty()) {
          val processor = FixBuildToolsProcessor(project, buildFiles, version, true, removeBuildTools)
          processor.setPreviewUsages(true)
          processor.run()
        }
        else {
          GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_BUILD_TOOLS_INSTALLED)
        }
      }
      future.complete(null)
    }
    return future
  }
}