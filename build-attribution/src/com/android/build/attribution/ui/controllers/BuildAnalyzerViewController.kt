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
package com.android.build.attribution.ui.controllers

import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer.AlwaysRunNoOutputIssue
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer.AlwaysRunUpToDateOverride
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer.TaskSetupIssue
import com.android.build.attribution.ui.model.AnnotationProcessorDetailsNodeDescriptor
import com.android.build.attribution.ui.model.AnnotationProcessorsRootNodeDescriptor
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.android.build.attribution.ui.model.TaskDetailsPageType
import com.android.build.attribution.ui.model.TaskWarningDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TaskWarningTypeNodeDescriptor
import com.android.build.attribution.ui.model.TasksDataPageModel.Grouping
import com.android.build.attribution.ui.model.TasksPageId
import com.android.build.attribution.ui.model.TasksTreeNode
import com.android.build.attribution.ui.model.WarningsTreeNode
import com.android.build.attribution.ui.model.WarningsTreePresentableNodeDescriptor
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType

class BuildAnalyzerViewController(
  val model: BuildAnalyzerViewModel,
  private val analytics: BuildAttributionUiAnalytics,
  private val issueReporter: TaskIssueReporter
) : ViewActionHandlers {

  init {
    analytics.initFirstPage(model)
  }

  override fun dataSetComboBoxSelectionUpdated(newSelectedData: BuildAnalyzerViewModel.DataSet) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    model.selectedData = newSelectedData
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.DATA_VIEW_COMBO_SELECTED)
  }

  override fun changeViewToTasksLinkClicked(targetGrouping: Grouping) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    model.selectedData = BuildAnalyzerViewModel.DataSet.TASKS
    model.tasksPageModel.selectGrouping(targetGrouping)
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.PAGE_CHANGE_LINK_CLICK)
  }

  override fun changeViewToWarningsLinkClicked() {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    model.selectedData = BuildAnalyzerViewModel.DataSet.WARNINGS
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.PAGE_CHANGE_LINK_CLICK)
  }

  override fun tasksGroupingSelectionUpdated(grouping: Grouping) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    model.tasksPageModel.selectGrouping(grouping)
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.GROUPING_CHANGED)
  }

  override fun tasksTreeNodeSelected(tasksTreeNode: TasksTreeNode) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    model.tasksPageModel.selectNode(tasksTreeNode)
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.PAGE_CHANGE_TREE_CLICK)
  }

  override fun tasksDetailsLinkClicked(taskPageId: TasksPageId) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    // Make sure tasks page open.
    model.selectedData = BuildAnalyzerViewModel.DataSet.TASKS
    // Update selection in the tasks page model.
    model.tasksPageModel.selectPageById(taskPageId)
    // Track page change in analytics.
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.PAGE_CHANGE_LINK_CLICK)
  }

  override fun warningsTreeNodeSelected(warningTreeNode: WarningsTreeNode) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    // Update selection in the model.
    model.warningsPageModel.selectNode(warningTreeNode)
    // Track page change in analytics.
    val newAnalyticsPage = analytics.getStateFromModel(model)
    analytics.pageChange(currentAnalyticsPage, newAnalyticsPage, BuildAttributionUiEvent.EventType.PAGE_CHANGE_TREE_CLICK)
  }

  override fun helpLinkClicked(linkTarget: BuildAnalyzerBrowserLinks) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    analytics.helpLinkClicked(currentAnalyticsPage, linkTarget)
  }

  override fun generateReportClicked(taskData: TaskUiData) {
    val currentAnalyticsPage = analytics.getStateFromModel(model)
    analytics.bugReportLinkClicked(currentAnalyticsPage)
    issueReporter.reportIssue(taskData)
  }
}
