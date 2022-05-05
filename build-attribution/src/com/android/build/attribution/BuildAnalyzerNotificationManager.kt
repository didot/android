/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.attribution

import com.android.build.attribution.analyzers.ConfigurationCacheCompatibilityTestFlow
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.model.shouldShowWarning
import com.android.tools.idea.flags.StudioFlags
import com.intellij.build.BuildContentManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindowBalloonShowOptions
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.event.HyperlinkEvent

class BuildAnalyzerNotificationManager(
  private val project: Project,
  private val uiAnalytics: BuildAttributionUiAnalytics
) {

  private val alreadyNotifiedAbout: MutableSet<WarningType> = mutableSetOf()

  fun showToolWindowBalloonIfNeeded(reportUiData: BuildAttributionReportUiData, viewDetailsLinkClickListener: () -> Unit) {
    if (reportUiData.isBuildAnalyzerSpecialBuild()) return
    val warningTypesInCurrentBuild = reportUiData.warningTypes().filter { it.triggerNotification }
    if (!alreadyNotifiedAbout.containsAll(warningTypesInCurrentBuild)) {
      showBalloon(viewDetailsLinkClickListener)
      alreadyNotifiedAbout.addAll(warningTypesInCurrentBuild)
    }
  }

  private fun showBalloon(viewDetailsLinkClickListener: () -> Unit) {
    val balloonOptions = ToolWindowBalloonShowOptions(
      toolWindowId = BuildContentManager.TOOL_WINDOW_ID,
      type = MessageType.WARNING,
      icon = AllIcons.General.BalloonWarning,
      htmlBody = """
            <b>Build Analyzer detected new build performance issues</b>
            <a href=''>Review to improve build performance</a>
          """.trimIndent(),
      listener = { event -> if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) viewDetailsLinkClickListener() }
    )
    ToolWindowManager.getInstance(project).notifyByBalloon(balloonOptions)
    uiAnalytics.toolWindowBalloonShown()
  }
}

private enum class WarningType(val triggerNotification: Boolean){
  ALWAYS_RUN_TASK(triggerNotification = true),
  TASK_SETUP_ISSUE(triggerNotification = true),
  NON_INCREMENTAL_ANNOTATION_PROCESSOR(triggerNotification = true),
  CONFIGURATION_CACHE(triggerNotification = false),
  JETIFIER_USAGE(triggerNotification = true)
}

private fun BuildAttributionReportUiData.isBuildAnalyzerSpecialBuild(): Boolean =
  confCachingData == ConfigurationCacheCompatibilityTestFlow ||
  jetifierData.checkJetifierBuild

private fun BuildAttributionReportUiData.warningTypes(): Set<WarningType> {
  val issueTypes = HashSet<WarningType>()
  this.issues.filter { it.warningCount > 0 }.map { when(it.type) {
    TaskIssueType.ALWAYS_RUN_TASKS -> WarningType.ALWAYS_RUN_TASK
    TaskIssueType.TASK_SETUP_ISSUE -> WarningType.TASK_SETUP_ISSUE
  }}.let { issueTypes.addAll(it) }
  if (this.annotationProcessors.issueCount > 0) issueTypes.add(WarningType.NON_INCREMENTAL_ANNOTATION_PROCESSOR)
  if (this.confCachingData.shouldShowWarning()) issueTypes.add(WarningType.CONFIGURATION_CACHE)
  if (this.jetifierData.shouldShowWarning()) issueTypes.add(WarningType.JETIFIER_USAGE)
  return issueTypes
}