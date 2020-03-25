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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.Projects.getBaseDirPath
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo.findFromBuildFiles
import com.android.tools.idea.gradle.project.sync.idea.issues.MessageComposer
import com.android.tools.idea.gradle.project.sync.issues.processor.AddRepoProcessor
import com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile
import com.android.tools.idea.npw.invokeLater
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.util.concurrent.CompletableFuture

class MissingAndroidPluginIssueChecker : GradleIssueChecker {
  private val PATTERN = "Could not find com.android.tools.build:gradle:"

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = issueData.error.message ?: return null
    if (!message.startsWith(PATTERN)) return null

    val description = MessageComposer(message).apply {
      // Display the link to the quickFix, but it will only effectively write to the build file is the block doesn't exist already.
      addQuickFix("Add google Maven repository and sync project", AddGoogleMavenRepositoryQuickFix())
      addQuickFix("Open File", OpenPluginBuildFileQuickFix())
    }
    return object : BuildIssue {
      override val title = "Gradle Sync issues."
      override val description = description.buildMessage()
      override val quickFixes: List<BuildIssueQuickFix> = description.quickFixes
      override fun getNavigatable(project: Project) = null
    }
  }

  class AddGoogleMavenRepositoryQuickFix : BuildIssueQuickFix {
    override val id = "add.google.maven.repo"

    override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
      invokeLater {
        if (project.isInitialized) {
          val pluginInfo = findFromBuildFiles(project)
          val buildFile =
            if (pluginInfo != null) pluginInfo.pluginBuildFile else getGradleBuildFile(getBaseDirPath(project)) ?: return@invokeLater
          // Only add the google Maven repository if it doesn't already exist.
          // TODO(karimai): Could there be a case when this condition is not always true ?
          val projectBuildModel = ProjectBuildModel.getOrLog(project) ?: return@invokeLater
          val gradleBuildModel = projectBuildModel.getModuleBuildModel(buildFile!!)
          if (!gradleBuildModel.buildscript().repositories().hasGoogleMavenRepository()) {
            val processor = AddRepoProcessor(project, listOf(buildFile), AddRepoProcessor.Repository.GOOGLE, true)
            processor.setPreviewUsages(true)
            processor.run()
          }
        }
        else Messages.showErrorDialog(project, "Failed to add Google Maven repository.", "Quick Fix")
      }
      return CompletableFuture.completedFuture<Any>(null)
    }
  }

  class OpenPluginBuildFileQuickFix : BuildIssueQuickFix {
    override val id = "open.plugin.build.file"

    override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
      invokeLater {
        if (project.isInitialized) {
          val pluginInfo = findFromBuildFiles(project) ?: return@invokeLater
          if (pluginInfo.pluginBuildFile != null) {
            val openFile = OpenFileDescriptor(project, pluginInfo.pluginBuildFile!!, -1, -1, false)
            if (openFile.canNavigate()) openFile.navigate(true)
          }
        }
        else Messages.showErrorDialog(project, "Failed to find plugin version on Gradle files.", "Quick Fix")
      }
      return CompletableFuture.completedFuture<Any>(null)
    }
  }
}