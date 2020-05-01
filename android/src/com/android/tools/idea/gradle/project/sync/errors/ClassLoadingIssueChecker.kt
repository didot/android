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

import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler.updateUsageTracker
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenProjectStructureQuickfix
import com.android.tools.idea.gradle.project.sync.quickFixes.SyncProjectRefreshingDependenciesQuickFix
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.sdk.IdeSdks
import com.google.common.base.Splitter
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.CANNOT_BE_CAST_TO
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.CLASS_NOT_FOUND
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.METHOD_NOT_FOUND
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler

class ClassLoadingIssueChecker: GradleIssueChecker {
  private val CLASS_NOT_FOUND_PATTERN = Pattern.compile("(.+) not found.")

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    val message = rootCause.message ?: ""

    var buildIssueComposer = BuildIssueComposer(getExceptionMessage(rootCause, message, issueData.projectPath) ?: return null)

    val syncProjectQuickFix = SyncProjectRefreshingDependenciesQuickFix()
    val stopGradleDaemonQuickFix = StopGradleDaemonQuickFix()

    val jdk7Hint = buildString {
      val jdk = IdeSdks.getInstance().jdk ?: return@buildString
      val jdkHomePath = jdk.homePath
      val jdkVersion = if (jdkHomePath != null) SdkVersionUtil.detectJdkVersion(jdkHomePath) else null

      if (JavaSdkVersion.JDK_1_7 != JavaSdk.getInstance().getVersion(jdk)) return@buildString
      // Otherwise, we are using Jdk7.
      when (jdkVersion) {
        null -> append("Some versions of JDK 1.7 (e.g. 1.7.0_10) may cause class loading errors in Gradle. \n" +
                       "Please update to a newer version (e.g. 1.7.0_67).")
        else -> append("You are using JDK version '${jdkVersion}'.")
      }
    }

    buildIssueComposer.addDescription(Splitter.on("\n").omitEmptyStrings().trimResults().splitToList(message)[0])
    if (jdk7Hint.isNotEmpty()) {
      buildIssueComposer.apply {
        addDescription("Possible causes for this unexpected error include:")
        addDescription(jdk7Hint)
        addQuickFix("Open JDK Settings", OpenProjectStructureQuickfix())
      }
    }
    buildIssueComposer.apply {
      addDescription("Gradle's dependency cache may be corrupt (this sometimes occurs after a network connection timeout.)")
      addQuickFix(syncProjectQuickFix.linkText, syncProjectQuickFix)
      addDescription("The state of a Gradle build process (daemon) may be corrupt. Stopping all Gradle daemons may solve this problem.")
      when (ApplicationManager.getApplication().isRestartCapable) {
        true -> addQuickFix("Stop Gradle build processes (requires restart)", stopGradleDaemonQuickFix)
        false -> addQuickFix("Open Gradle Daemon documentation", stopGradleDaemonQuickFix)
      }
      addDescription("Your project may be using a third-party plugin which is not compatible with the other " +
                     "plugins in the project or the version of Gradle requested by the project.\n\n" +
                     "In the case of corrupt Gradle processes, you can also try closing the IDE and then killing all Java processes.")
    }

    return buildIssueComposer.composeBuildIssue()
  }

  private fun getExceptionMessage(exception: Throwable, message: String, projectPath: String): String? {
    when (exception) {
      is ClassNotFoundException -> {
        var className = message
        val matcher = CLASS_NOT_FOUND_PATTERN.matcher(className)
        if (matcher.matches()) {
          className = matcher.group(1)
          // Log metrics.
          invokeLater {
            updateUsageTracker(projectPath, CLASS_NOT_FOUND)
          }
          return "Unable to load class '${className}'"
        }
      }
      is NoSuchMethodError -> {
        // Log metrics.
        invokeLater {
          updateUsageTracker(projectPath, METHOD_NOT_FOUND)
        }
        return "Unable to find method '$message'"
      }
      else -> {
        if (message.contains("cannot be cast to")) {
          // Log metrics.
          invokeLater {
            updateUsageTracker(projectPath, CANNOT_BE_CAST_TO)
          }
          return message
        }
      }
    }
    return null
  }

  class StopGradleDaemonQuickFix : BuildIssueQuickFix {
    override val id = "stop.gradle.daemons"

    override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
      val future = CompletableFuture<Any>()

      if (ApplicationManager.getApplication().isRestartCapable) {
        val title = "Stop Gradle Daemons"
        val message = """
          Stopping all Gradle daemons will terminate any running Gradle builds (e.g. from the command line).
          This action will also restart the IDE.
          Do you want to continue?
          """.trimIndent()
        val answer = Messages.showYesNoDialog(project, message, title,  Messages.getQuestionIcon())
        if (answer == Messages.YES) {
          invokeLater {
            GradleUtil.stopAllGradleDaemonsAndRestart()
            future.complete(null)
          }
        }
      }
      else {
        invokeLater {
          BrowserUtil.browse("http://www.gradle.org/docs/current/userguide/gradle_daemon.html")
          future.complete(null)
        }
      }

      return future
    }
  }


}