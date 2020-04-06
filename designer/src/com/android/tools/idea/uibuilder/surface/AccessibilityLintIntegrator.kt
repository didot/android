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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssueProvider
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.validator.ValidatorData
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableCollection
import com.intellij.lang.annotation.HighlightSeverity
import java.util.stream.Stream

class AccessibilityLintIntegrator(private val issueModel: IssueModel) {

  private val ACCESSIBILITY_CATEGORY = "Accessibility"
  private val ACCESSIBILITY_ERROR = "Accessibility Error"
  private val ACCESSIBILITY_WARNING = "Accessibility Warning"
  private val ACCESSIBILITY_INFO = "Accessibility Info"

  private val issueProvider: IssueProvider = object : IssueProvider() {
    override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
      issues.forEach {
        issueListBuilder.add(it)
      }
    }
  }

  @VisibleForTesting
  val issues = ArrayList<Issue>()

  /**
   * Clear all lints and disable accessibility lint.
   */
  fun disableAccessibilityLint() {
    issueModel.removeIssueProvider(issueProvider)
    issues.clear()
  }

  /**
   * Populate lints based on issues created through [createAnIssue]
   */
  fun populateLints() {
    issueModel.addIssueProvider(issueProvider)
  }

  private fun forceUpdate() {
    issueModel.removeIssueProvider(issueProvider)
    issueModel.addIssueProvider(issueProvider)
  }

  /**
   * Creates a single issue/lint that matches given parameters. Must call [populateLints] in order for issues to be visible.
   */
  fun createIssue(result: ValidatorData.Issue, source: NlComponent?) {
    issues.add(object : Issue() {
      override fun getSummary(): String {
        return when (result.mLevel) {
          ValidatorData.Level.ERROR -> ACCESSIBILITY_ERROR
          ValidatorData.Level.WARNING -> ACCESSIBILITY_WARNING
          else -> ACCESSIBILITY_INFO
        }
      }

      override fun getDescription(): String {
        return result.mMsg
      }

      override fun getSeverity(): HighlightSeverity {
        return when (result.mLevel) {
          ValidatorData.Level.ERROR -> HighlightSeverity.ERROR
          ValidatorData.Level.WARNING -> HighlightSeverity.WARNING
          else ->  HighlightSeverity.INFORMATION
        }
      }

      override fun getSource(): NlComponent? {
        return source
      }

      override fun getCategory(): String {
        return ACCESSIBILITY_CATEGORY
      }

      override fun getFixes(): Stream<Fix> {
        return convertToFix(this, source, result)
      }
    })
  }

  private fun convertToFix(issue: Issue, component: NlComponent?, result: ValidatorData.Issue): Stream<Issue.Fix> {
    // TODO b/150331000 Implement this based on result later.
    val fixes = ArrayList<Issue.Fix>()
    return fixes.stream()
  }
}
