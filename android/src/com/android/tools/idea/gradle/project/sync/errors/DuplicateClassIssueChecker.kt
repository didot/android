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

import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.gradle.tooling.BuildException
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler

/**
 * Replaces href for more information related to duplicate classes to the id of an [OpenLinkQuickFix] so the IDE can open the link
 */
class DuplicateClassIssueChecker: GradleIssueChecker {
  private val HREF = "d.android.com/r/tools/classpath-sync-errors"
  private val SUFFIX = "Go to the documentation to learn how to <a href=\"$HREF\">Fix dependency resolution errors</a>."

  override fun check(issueData: GradleIssueData): BuildIssue? {
    if (issueData.error !is BuildException) {
      return null
    }
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    if (rootCause !is RuntimeException) {
      return null
    }
    var message = rootCause.message ?: return null
    if (!message.startsWith("Duplicate class ") || !message.endsWith(SUFFIX)) {
      return null
    }
    val urlLink = OpenLinkQuickFix("http://$HREF")
    message = message.replace(HREF, urlLink.id)

    return object : BuildIssue {
      override val title = "Duplicate class found"
      override val description = message
      override val quickFixes = listOf(urlLink)
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}