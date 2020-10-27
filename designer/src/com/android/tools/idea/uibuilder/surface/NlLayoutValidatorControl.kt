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

import com.android.tools.idea.common.error.IssuePanel
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorResult

/**
 * Controls the NlLayoutValidator in context of NlDesignSurface.
 */
class NlLayoutValidatorControl(
  private val surface: NlDesignSurface,
  private val validator: NlLayoutValidator): LayoutValidatorControl {

  /** Listener for issue panel open/close */
  private val issuePanelListener = IssuePanel.MinimizeListener {
    if (it) {
      // Minimized
      validator.disable()
      surface.sceneManager?.isLayoutValidationEnabled = false
    }
    else if (surface.sceneManager?.isLayoutValidationEnabled == false) {
      surface.sceneManager?.isLayoutValidationEnabled = true
      surface.forceUserRequestedRefresh()
    }
  }

  init {
    surface.issuePanel.addMinimizeListener(issuePanelListener)
  }

  override fun runLayoutValidation() {
    surface.sceneManager?.isLayoutValidationEnabled = true
    surface.forceUserRequestedRefresh()
    surface.analyticsManager.trackShowIssuePanel()
    surface.setShowIssuePanel(true)
  }
}

// For debugging
fun ValidatorResult.toDetailedString(): String? {
  val builder: StringBuilder = StringBuilder().append("Result containing ").append(issues.size).append(
    " issues:\n")
  val var2: Iterator<*> = this.issues.iterator()
  while (var2.hasNext()) {
    val issue = var2.next() as ValidatorData.Issue
    if (issue.mLevel == ValidatorData.Level.ERROR) {
      builder.append(" - [E::").append(issue.mLevel.name).append("] ").append(issue.mMsg).append("\n")
    } else {
      builder.append(" - [W::").append(issue.mLevel.name).append("] ").append(issue.mMsg).append("\n")
    }
  }
  return builder.toString()
}