/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui.panels

import com.android.build.attribution.ui.DescriptionWithHelpLinkLabel
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.TaskIssueReporter
import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.issueIcon
import com.android.build.attribution.ui.percentageString
import com.android.build.attribution.ui.warningIcon
import com.android.tools.adtui.TabularLayout
import com.android.utils.HtmlBuilder
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.text.StringUtil.pluralize
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

const val CRITICAL_PATH_LINK = "https://developer.android.com/r/tools/build-attribution/critical-path"

interface TreeLinkListener<T> {
  fun clickedOn(target: T)
}

fun pluginInfoPanel(
  pluginUiData: CriticalPathPluginUiData,
  listener: TreeLinkListener<TaskIssueType>,
  analytics: BuildAttributionUiAnalytics
): JComponent = JBPanel<JBPanel<*>>(VerticalLayout(0)).apply {
  val pluginText = HtmlBuilder()
    .openHtmlBody()
    .add(
      "This plugin has ${pluginUiData.criticalPathTasks.size} ${pluralize("task", pluginUiData.criticalPathTasks.size)} " +
      "of total duration ${pluginUiData.criticalPathDuration.durationString()} (${pluginUiData.criticalPathDuration.percentageString()})"
    )
    .newline()
    .add("determining this build's duration.")
    .closeHtmlBody()
  add(DescriptionWithHelpLinkLabel(pluginText.html, CRITICAL_PATH_LINK, analytics))
  add(JBPanel<JBPanel<*>>(VerticalLayout(6)).apply {
    border = JBUI.Borders.emptyTop(15)
    add(JBLabel("Warnings detected").withFont(JBUI.Fonts.label().asBold()))
    for (issueGroup in pluginUiData.issues) {
      add(HyperlinkLabel("${issueGroup.type.uiName} (${issueGroup.size})").apply {
        addHyperlinkListener { listener.clickedOn(issueGroup.type) }
        border = JBUI.Borders.emptyLeft(15)
        setIcon(issueIcon(issueGroup.type))
      })
    }
    if (pluginUiData.issues.isEmpty()) {
      add(JLabel("No warnings detected"))
    }
  })
  withPreferredWidth(400)
}

fun taskInfoPanel(
  taskData: TaskUiData,
  analytics: BuildAttributionUiAnalytics,
  issueReporter: TaskIssueReporter
): JPanel {
  val taskDescription = htmlTextLabel(
    HtmlBuilder()
      .openHtmlBody()
      .add(
        if (taskData.onLogicalCriticalPath)
          "This task frequently determines build duration because of dependencies between its inputs/outputs and other tasks."
        else
          "This task occasionally determines build duration because of parallelism constraints introduced by number of cores or other tasks in the same module."
      )
      .closeHtmlBody()
      .html
  )
  val taskInfo = htmlTextLabel(
    HtmlBuilder()
      .openHtmlBody()
      .add("Duration: ${taskData.executionTime.durationString()} / ${taskData.executionTime.percentageString()}")
      .newline()
      .add("Module: ${taskData.module}")
      .newline()
      .add("Plugin: ${taskData.pluginName}")
      .newline()
      .add("Type: ${taskData.taskType}")
      .newline()
      .add("Executed incrementally: ${if (taskData.executedIncrementally) "Yes" else "No"}")
      .newline()
      .add("Determined this build’s duration: ${if (taskData.onExtendedCriticalPath) "Yes" else "No"}")
      .closeHtmlBody()
      .html
  )

  val reasonsToRunHeader = JBLabel("Reason task ran").withFont(JBUI.Fonts.label().asBold())
  val reasonsList = reasonsToRunList(taskData)

  // Use tabular layout of two columns two make things wrap nicely.
  // Elements with fixed width are just added to the first column and determine it's width.
  // Elements that can and should wrap are added with 'colSpan=2' so that they can grab extra horizontal space when available.
  val infoPanel = JBPanel<JBPanel<*>>(TabularLayout("Fit,*").setVGap(10))
  var row = 0
  infoPanel.add(taskDescription, TabularLayout.Constraint(row++, 0, colSpan = 2))
  infoPanel.add(taskInfo, TabularLayout.Constraint(row++, 0))
  infoPanel.add(JBLabel("Warnings").withFont(JBUI.Fonts.label().asBold()), TabularLayout.Constraint(row++, 0))
  if (taskData.issues.isEmpty()) {
    infoPanel.add(JLabel("No warnings found"), TabularLayout.Constraint(row++, 0))
  }
  else {
    for ((index, issue) in taskData.issues.withIndex()) {
      infoPanel.add(taskWarningDescriptionPanel(issue, analytics, index > 0), TabularLayout.Constraint(row++, 0, colSpan = 2))
    }
    if (taskData.sourceType != PluginSourceType.BUILD_SRC) {
      infoPanel.add(generateReportLinkLabel(analytics, issueReporter, taskData), TabularLayout.Constraint(row++, 0))
    }
  }
  infoPanel.add(reasonsToRunHeader, TabularLayout.Constraint(row++, 0))
  infoPanel.add(reasonsList, TabularLayout.Constraint(row, 0, colSpan = 2))

  return infoPanel
}

private fun taskWarningDescriptionPanel(
  issue: TaskIssueUiData,
  analytics: BuildAttributionUiAnalytics,
  needSeparatorInFront: Boolean
): JComponent = JPanel(TabularLayout("Fit,*").setVGap(10)).apply {
  name = "warning-${issue.type.name}"
  if (needSeparatorInFront) {
    add(horizontalRuler(), TabularLayout.Constraint(0, 1))
  }
  add(JBLabel(warningIcon()).withBorder(JBUI.Borders.emptyRight(5)), TabularLayout.Constraint(1, 0))
  add(JBLabel(issue.type.uiName).withFont(JBUI.Fonts.label().asBold()), TabularLayout.Constraint(1, 1))
  add(DescriptionWithHelpLinkLabel(issue.explanation, issue.helpLink, analytics), TabularLayout.Constraint(2, 1))
  add(htmlTextLabel("<b>Recommendation</b> ${issue.buildSrcRecommendation}"), TabularLayout.Constraint(3, 1))
}

fun generateReportLinkLabel(
  analytics: BuildAttributionUiAnalytics,
  issueReporter: TaskIssueReporter,
  taskData: TaskUiData
): JComponent = object : HyperlinkLabel() {
  override fun getTextOffset(): Int {
    return 0
  }
}.apply {
  addHyperlinkListener {
    analytics.bugReportLinkClicked()
    issueReporter.reportIssue(taskData)
  }
  setHyperlinkText("Consider filing a bug to report this issue to the plugin developer. ", "Generate report.", "")
}

private fun reasonsToRunList(taskData: TaskUiData) = htmlTextLabel(createReasonsText(taskData.reasonsToRun))

private fun createReasonsText(reasons: List<String>): String = if (reasons.isEmpty()) {
  "No info"
}
else {
  reasons.joinToString(separator = "<br/>") { wrapPathToSpans(it).replace("\n", "<br>") }
}

/**
 * Wraps long path to spans to make it possible to auto-wrap to a new line
 */
fun wrapPathToSpans(text: String): String = "<p>${text.replace("/", "<span>/</span>")}</p>"

fun verticalRuler(): JPanel = JBPanel<JBPanel<*>>()
  .withBackground(OnePixelDivider.BACKGROUND)
  .withPreferredWidth(1)
  .withMaximumWidth(1)
  .withMinimumWidth(1)

fun horizontalRuler(): JPanel = JBPanel<JBPanel<*>>()
  .withBackground(OnePixelDivider.BACKGROUND)
  .withPreferredHeight(1)
  .withMaximumHeight(1)
  .withMinimumHeight(1)

fun createIssueTypeListPanel(issuesGroup: TaskIssuesGroup, listener: TreeLinkListener<TaskIssueUiData>): JComponent =
  JBPanel<JBPanel<*>>().apply {
    layout = VerticalLayout(6)
    issuesGroup.issues.forEach {
      add(HyperlinkLabel(it.task.taskPath).apply { addHyperlinkListener { _ -> listener.clickedOn(it) } })
    }
  }

fun criticalPathHeader(prefix: String, duration: String): JComponent =
  headerLabel("${prefix} determining this build's duration (${duration})")

fun headerLabel(text: String): JLabel = JBLabel(text).withFont(JBUI.Fonts.label(13f).asBold()).apply {
  name = "pageHeader"
}

/**
 * Label with auto-wrapping turned on that accepts html text.
 * Used in Build Analyzer to render long multi-line text.
 */
fun htmlTextLabel(html: String): JBLabel = JBLabel(html).apply {
  setAllowAutoWrapping(true)
  setCopyable(true)
  isFocusable = false
  verticalTextPosition = SwingConstants.TOP
}
