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

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler.fetchIdeaProjectForGradleProject
import com.android.tools.idea.gradle.project.sync.idea.issues.MessageComposer
import com.android.tools.idea.gradle.project.sync.quickFixes.CreateGradleWrapperQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.tools.idea.projectsystem.AndroidProjectRootUtil
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.FilePosition
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.text.StringUtil
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.plugins.gradle.GradleManager
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler.FIX_GRADLE_VERSION
import org.jetbrains.plugins.gradle.settings.DistributionType
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

class UnsupportedGradleVersionIssueChecker: GradleIssueChecker {
  private val UNSUPPORTED_GRADLE_VERSION_PATTERN_1 = Pattern.compile("Minimum supported Gradle version is (.*)\\. Current version is.*?")
  private val UNSUPPORTED_GRADLE_VERSION_PATTERN_2 = Pattern.compile("Gradle version (.*) is required.*?")

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = issueData.error.message ?: return null
    val error = issueData.error
    if (error !is UnsupportedVersionException &&
        !(error is UnsupportedMethodException && (message.isNotEmpty() && message.contains("GradleProject.getBuildScript"))) &&
        !(error is ClassNotFoundException && (message.isNotEmpty() && message.contains(ToolingModelBuilderRegistry::class.java.name))))
      return null

    // Log metrics.
    invokeLater {
      SyncErrorHandler.updateUsageTracker(issueData.projectPath, GradleSyncFailure.UNSUPPORTED_GRADLE_VERSION)
    }
    val description = if (message.isNotEmpty()) {
      MessageComposer(formatMessage(message))
    }
    else {
      MessageComposer("The project is using an unsupported version of Gradle.\n$FIX_GRADLE_VERSION")
    }

    // Get QuickFixes.
    val ideaProject = fetchIdeaProjectForGradleProject(issueData.projectPath)

    if (ideaProject != null) {
      val gradleWrapper = GradleWrapper.find(ideaProject)
      val gradleVersion = getSupportedGradleVersion(message)
      if (gradleWrapper != null) {
        // It's likely that we need to fix the model version as well.
        description.addQuickFix("Fix Gradle wrapper and re-import project", FixGradleVersionInWrapperQuickFix(gradleWrapper, gradleVersion))
        val propertiesFile = gradleWrapper.propertiesFilePath
        if (propertiesFile.exists()) description.addQuickFix(
          "Open Gradle wrapper properties", OpenFileAtLocationQuickFix(FilePosition(propertiesFile, -1, -1)))
      }
      else {
        val gradleProjectSettings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(ideaProject)
        if (gradleProjectSettings != null && gradleProjectSettings.distributionType == DistributionType.LOCAL) {
          description.addQuickFix("Migrate to Gradle wrapper and sync project", CreateGradleWrapperQuickFix())
        }
      }
    }
    // Also offer quickFix to open Gradle settings. In case we can't find IDEA project, we can still offer this one.
    description.addQuickFix("Gradle Settings.", OpenGradleSettingsQuickFix())

    return object : BuildIssue {
      override val title = "Gradle Sync issues."
      override val description = description.buildMessage()
      override val quickFixes = description.quickFixes
      override fun getNavigatable(project: Project) = null
    }
  }

  private fun formatMessage(message: String) : String {
    val formattedMsg = StringBuilder()
    if (UNSUPPORTED_GRADLE_VERSION_PATTERN_1.matcher(message).matches() ||
        UNSUPPORTED_GRADLE_VERSION_PATTERN_2.matcher(message).matches()) {
      val index = message.indexOf("If using the gradle wrapper")
      if (index != -1) {
        formattedMsg.append(message.substring(0, index).trim())
      }
      else formattedMsg.append(message)
      if (formattedMsg.isNotEmpty() && !formattedMsg.endsWith('.')) formattedMsg.append('.')
      formattedMsg.append("\n\nPlease fix the project's Gradle settings.")
    }
    return formattedMsg.toString()
  }

  private fun getSupportedGradleVersion(message: String): String? {
    for (pattern in listOf(UNSUPPORTED_GRADLE_VERSION_PATTERN_1, UNSUPPORTED_GRADLE_VERSION_PATTERN_2)) {
      val matcher = pattern.matcher(message)
      if (matcher.matches()) {
        val version = matcher.group(1)
        if (StringUtil.isNotEmpty(version)) {
          return version
        }
      }
    }
    return null
  }

  class OpenGradleSettingsQuickFix: BuildIssueQuickFix {
    override val id = "open.gradle.settings"

    override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
      val future = CompletableFuture<Any>()
      invokeLater {
        val manager = ExternalSystemApiUtil.getManager(GradleUtil.GRADLE_SYSTEM_ID)
        assert(manager is GradleManager)
        val configurable = (manager as GradleManager).getConfigurable(project)
        ShowSettingsUtil.getInstance().editConfigurable(project, configurable)
        future.complete(null)
      }
      return future
    }
  }

  class FixGradleVersionInWrapperQuickFix(private var gradleWrapper: GradleWrapper?, private val gradleVersion: String?): BuildIssueQuickFix {
    override val id = "fix.gradle.version.in.wrapper"

    override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
      val future = CompletableFuture<Any>()
      invokeLater {
        if (gradleWrapper == null) gradleWrapper = GradleWrapper.find(project) ?: return@invokeLater
        gradleWrapper!!.updateDistributionUrlAndDisplayFailure(gradleVersion ?: SdkConstants.GRADLE_LATEST_VERSION)
        // Set the distribution type and request sync.
        val settings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project)
        if (settings != null) {
          settings.distributionType = DistributionType.DEFAULT_WRAPPED
        }
        GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_WRAPPER_GRADLE_VERSION_FIXED)
        future.complete(null)
      }
      return future
    }
  }
}