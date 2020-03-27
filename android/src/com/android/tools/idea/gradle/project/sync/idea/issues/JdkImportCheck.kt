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
@file:JvmName("JdkImportCheck")
package com.android.tools.idea.gradle.project.sync.idea.issues

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler.updateUsageTracker
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.Jdks
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.io.File
import java.util.concurrent.CompletableFuture

class JdkImportCheckException(reason: String) : AndroidSyncException(reason)

/**
 * Validates the state of the JDK that is set in studio before the Gradle import is started.
 *
 * If we find that the JDK is not valid then we throw a [JdkImportCheckException] which is then
 * caught in the [JdkImportIssueChecker] which creates an errors message with the appropriate
 * quick fixes.
 */
fun validateJdk() {
  val jdkValidationError = validateJdk(IdeSdks.getInstance().jdk) ?: return // Valid jdk
  throw JdkImportCheckException(jdkValidationError)
}

class JdkImportIssueChecker : GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = when {
      issueData.error is JdkImportCheckException -> issueData.error.message!!
      issueData.error.message?.contains("Unsupported major.minor version 52.0") == true -> {
        // TODO(151215857): Replace once updating the usage no longer requires a project.
        for (project in ProjectManager.getInstance().openProjects) {
          if (project.basePath == issueData.projectPath) {
            invokeLater {
              updateUsageTracker(project, GradleSyncFailure.JDK8_REQUIRED)
            }
            break
          }
        }
        "${issueData.error.message!!}\nPlease use JDK 8 or newer."
      }
      else -> return null
    }

    val messageComposer = MessageComposer(message).apply {
      if (IdeInfo.getInstance().isAndroidStudio) {
        val ideSdks = IdeSdks.getInstance()
        if (!ideSdks.isUsingJavaHomeJdk) {
          val jdkFromHome = IdeSdks.getJdkFromJavaHome()
          if (jdkFromHome != null && ideSdks.validateJdkPath(File(jdkFromHome)) != null) {
            addQuickFix(UseJavaHomeAsJdkQuickFix(jdkFromHome))
          }
        }

        if (quickFixes.isEmpty()) {
          val embeddedJdkPath = EmbeddedDistributionPaths.getInstance().tryToGetEmbeddedJdkPath()
          // TODO: Check we REALLY need to check isJdkRunnableOnPlatform. This spawns a process.
          if (embeddedJdkPath != null && Jdks.isJdkRunnableOnPlatform(embeddedJdkPath.absolutePath)) {
            addQuickFix(UseEmbeddedJdkQuickFix())
          } else {
            addQuickFix(DownloadAndroidStudioQuickFix())
          }
        }
      }

      addQuickFix(SelectJdkFromFileSystemQuickFix())
      addQuickFix(DownloadJdk8QuickFix())
    }

    return object : BuildIssue {
      override val title: String = "Invalid Jdk"
      override val description: String = messageComposer.buildMessage()
      override val quickFixes: List<BuildIssueQuickFix> = messageComposer.quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}

private class UseJavaHomeAsJdkQuickFix(val javaHome: String) : DescribedBuildIssueQuickFix {
  override val description: String = "Set Android Studio to use the same JDK as Gradle and sync project"
  override val id: String = "use.java.home.as.jdk"

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    val future = CompletableFuture<Nothing>()
    invokeLater {
      runWriteAction { IdeSdks.getInstance().setJdkPath(File(javaHome)) }
      GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_JDK_CHANGED_TO_CURRENT)
      future.complete(null)
    }
    return future
  }
}

private class UseEmbeddedJdkQuickFix : DescribedBuildIssueQuickFix {
  override val description: String = "Use embedded JDK"
  override val id: String = "use.embedded.jdk"

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    val future = CompletableFuture<Nothing>()
    invokeLater {
      runWriteAction { IdeSdks.getInstance().setUseEmbeddedJdk() }
      GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_JDK_CHANGED_TO_EMBEDDED)
      future.complete(null)
    }
    return future
  }
}

private class DownloadAndroidStudioQuickFix : DescribedBuildIssueQuickFix {
  override val description: String = "See Android Studio download options"
  override val id: String = "download.android.studio"

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    BrowserUtil.browse("http://developer.android.com/studio/index.html#downloads")
    return CompletableFuture.completedFuture(null)
  }
}

private class DownloadJdk8QuickFix : DescribedBuildIssueQuickFix {
  override val description: String = "Download JDK 8"
  override val id: String = "download.jdk8"

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    BrowserUtil.browse(Jdks.DOWNLOAD_JDK_8_URL)
    return CompletableFuture.completedFuture(null)
  }
}

private class SelectJdkFromFileSystemQuickFix : DescribedBuildIssueQuickFix {
  override val description: String = "Select a JDK from the File System"
  override val id: String = "select.jdk.from.new.psd"

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    val service = ProjectSettingsService.getInstance(project)
    if (service is AndroidProjectSettingsService) {
      service.chooseJdkLocation()
    } else {
      service.chooseAndSetSdk()
    }
    return CompletableFuture.completedFuture(null)
  }
}

/**
 * Verify the Jdk in the following ways,
 * 1. Jdk location has been set and has a valid Jdk home directory.
 * 2. The selected Jdk has the same version with IDE, this is to avoid serialization problems.
 * 3. The Jdk installation is complete, i.e. the has java executable, runtime and etc.
 * 4. The selected Jdk is compatible with current platform.
 * Returns null if the [Sdk] is valid, an error message otherwise.
 */
private fun validateJdk(jdk: Sdk?): String? {
  if (jdk == null) {
    return "Jdk location is not set."
  }
  val jdkHomePath = jdk.homePath ?: return "Could not find valid Jdk home from the selected Jdk location."
  val selectedJdkMsg = "Selected Jdk location is $jdkHomePath.\n"
  // Check if the version of selected Jdk is the same with the Jdk IDE uses.
  val runningJdkVersion = IdeSdks.getInstance().runningVersionOrDefault
  if (!StudioFlags.ALLOW_DIFFERENT_JDK_VERSION.get() && !IdeSdks.isJdkSameVersion(File(jdkHomePath), runningJdkVersion)) {
    return "The version of selected Jdk doesn't match the Jdk used by Studio. Please choose a valid Jdk " +
           runningJdkVersion.description + " directory.\n" + selectedJdkMsg
  }
  // Check Jdk installation is complete.
  if (!JdkUtil.checkForJdk(jdkHomePath)) {
    return "The Jdk installation is invalid.\n$selectedJdkMsg"
  }
  // Check if the Jdk is compatible with platform.
  return if (!Jdks.isJdkRunnableOnPlatform(jdk)) {
    "The selected Jdk could not run on current OS.\n" +
    "If you are using embedded Jdk, please make sure to download Android Studio bundle compatible\n" +
    "with the current OS. For example, for x86 systems please choose a 32 bits download option.\n" +
    selectedJdkMsg
  }
  else null
}