/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.repository.Revision
import com.android.tools.idea.gradle.project.sync.hyperlink.FixNdkVersionHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallNdkHyperlink
import com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.openapi.project.Project

import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.FAILED_TO_INSTALL_NDK_BUNDLE
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.NDK_NOT_CONFIGURED
import com.intellij.openapi.module.ModuleManager
import java.lang.RuntimeException

class MissingNdkErrorHandler : BaseSyncErrorHandler() {
  override fun findErrorMessage(rootCause: Throwable, project: Project): String? {
    val message = rootCause.message ?: return null
    return when {
      tryExtractPreferredNdkDownloadVersion(message) != null -> {
        SyncErrorHandler.updateUsageTracker(project, NDK_NOT_CONFIGURED)
        message
      }
      matchesNdkNotConfigured(message) -> {
        SyncErrorHandler.updateUsageTracker(project, NDK_NOT_CONFIGURED)
        "NDK not configured."
      }
      matchesKnownLocatorIssue(message) -> {
        SyncErrorHandler.updateUsageTracker(project, NDK_NOT_CONFIGURED)
        message
      }
      matchesTriedInstall(message) -> {
        SyncErrorHandler.updateUsageTracker(project, FAILED_TO_INSTALL_NDK_BUNDLE)
        message
      }
      else -> null
    }
  }

  /**
   * @param errorMessage first line of the error message
   * @return whether or not this error message indicates that the NDK was found not to be configured
   */
  private fun matchesNdkNotConfigured(errorMessage: String): Boolean {
    return errorMessage.startsWith("NDK not configured.") ||
           errorMessage.startsWith("NDK location not found.") ||
           errorMessage.startsWith("Requested NDK version") ||
           errorMessage.startsWith("No version of NDK matched the requested version")
  }

  /**
   * Messages that indicate the user messed up something in build.gradle android.ndkVersion.
   */
  private fun matchesKnownLocatorIssue(errorMessage: String): Boolean {
    return (errorMessage.startsWith("Specified android.ndkVersion")
              && errorMessage.contains("does not have enough precision")) ||
           (errorMessage.startsWith("Location specified by ndk.dir"))
  }

  /**
   * @param errorMessage the error message
   * @return whether the given error message was generated by the Android Gradle Plugin failing to download the ndk-bundle package.
   */
  private fun matchesTriedInstall(errorMessage: String): Boolean {
    return (errorMessage.startsWith(
      "Failed to install the following Android SDK packages as some licences have not been accepted.") || errorMessage.startsWith(
      "Failed to install the following SDK components:")) && errorMessage.contains("NDK")
  }

  override fun getQuickFixHyperlinks(project: Project, text: String): List<NotificationHyperlink> {
    val hyperlinks = mutableListOf<NotificationHyperlink>()
    val gradleBuildFiles = ModuleManager.getInstance(project).modules.mapNotNull { getGradleBuildFile(it) }
    val preferredVersion = tryExtractPreferredNdkDownloadVersion(text)
    if (preferredVersion != null) {
      val localNdk = IdeSdks.getInstance().getSpecificLocalPackage(preferredVersion)
      if (localNdk != null) {
        hyperlinks += FixNdkVersionHyperlink(localNdk.version.toString(), gradleBuildFiles)
      }
      else {
        hyperlinks += InstallNdkHyperlink(preferredVersion.toString(), gradleBuildFiles)
      }
    } else {
      val highestLocalNonPreviewNdk =
        IdeSdks.getInstance().getHighestLocalNdkPackage(false)
      if (highestLocalNonPreviewNdk != null) {
        hyperlinks += FixNdkVersionHyperlink(highestLocalNonPreviewNdk.version.toString(), gradleBuildFiles)
      }
      else {
        val highestLocalNdkIncludingPreviews =
          IdeSdks.getInstance().getHighestLocalNdkPackage(true)
        if (highestLocalNdkIncludingPreviews != null) {
          hyperlinks += FixNdkVersionHyperlink(highestLocalNdkIncludingPreviews.version.toString(), gradleBuildFiles)
        } else {
          hyperlinks += InstallNdkHyperlink(null, gradleBuildFiles)
        }
      }
    }
    return hyperlinks
  }
}

private const val VERSION_PATTERN = "(?<version>([0-9]+)(?:\\.([0-9]+)(?:\\.([0-9]+))?)?([\\s-]*)?(?:(rc|alpha|beta|\\.)([0-9]+))?)"
private val PREFERRED_VERSION_PATTERNS = listOf(
  "NDK not configured. Download it with SDK manager. Preferred NDK version is '$VERSION_PATTERN'.*".toRegex(),
  "No version of NDK matched the requested version $VERSION_PATTERN.*".toRegex())

/**
 * Try to recover preferred NDK version from the error message
 */
fun tryExtractPreferredNdkDownloadVersion(text : String) : Revision? {
  for(pattern in PREFERRED_VERSION_PATTERNS) {
    val result = pattern.matchEntire(text) ?: continue
    val version = result.groups["version"]!!.value
    return Revision.parseRevision(version)
  }
  return null
}


