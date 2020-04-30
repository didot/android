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

import com.android.build.attribution.ui.MockUiData
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.mockTask
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.android.build.attribution.ui.model.TaskDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.TasksPageId
import com.android.build.attribution.ui.model.TasksTreeNode
import com.android.build.attribution.ui.model.WarningsTreeNode
import com.android.testutils.MockitoKt.eq
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.UUID

class BuildAnalyzerViewControllerTest {
  @get:Rule
  val projectRule: ProjectRule = ProjectRule()

  @get:Rule
  val edtRule = EdtRule()

  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  val task1 = mockTask(":app", "compile", "compiler.plugin", 2000).apply {
    issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
  }
  val task2 = mockTask(":app", "resources", "resources.plugin", 1000)
  val task3 = mockTask(":lib", "compile", "compiler.plugin", 1000)

  val model = BuildAnalyzerViewModel(MockUiData(tasksList = listOf(task1, task2, task3)))
  val analytics = BuildAttributionUiAnalytics(projectRule.project)
  val buildSessionId = UUID.randomUUID().toString()
  val issueReporter = Mockito.mock(TaskIssueReporter::class.java)

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(tracker)
    analytics.newReportSessionId(buildSessionId)
  }

  @After
  fun tearDown() {
    UsageTracker.cleanAfterTesting()
  }

  @Test
  @RunsInEdt
  fun testDataSetComboBoxSelectionUpdatedToTasks() {
    model.selectedData = BuildAnalyzerViewModel.DataSet.OVERVIEW

    val controller = BuildAnalyzerViewController(model, analytics, issueReporter)
    controller.dataSetComboBoxSelectionUpdated(BuildAnalyzerViewModel.DataSet.TASKS)

    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.TASKS)

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }

    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.verifyComboBoxPageChangeEvent(
      from = BuildAttributionUiEvent.Page.PageType.BUILD_SUMMARY,
      to = BuildAttributionUiEvent.Page.PageType.CRITICAL_PATH_TASKS_ROOT
    )
  }

  @Test
  @RunsInEdt
  fun testDataSetComboBoxSelectionUpdatedToWarnings() {
    model.selectedData = BuildAnalyzerViewModel.DataSet.OVERVIEW

    val controller = BuildAnalyzerViewController(model, analytics, issueReporter)
    controller.dataSetComboBoxSelectionUpdated(BuildAnalyzerViewModel.DataSet.WARNINGS)

    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.WARNINGS)

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }

    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.verifyComboBoxPageChangeEvent(
      from = BuildAttributionUiEvent.Page.PageType.BUILD_SUMMARY,
      to = BuildAttributionUiEvent.Page.PageType.WARNINGS_ROOT
    )
  }

  @Test
  @RunsInEdt
  fun testDataSetComboBoxSelectionUpdatedToOverview() {
    model.selectedData = BuildAnalyzerViewModel.DataSet.WARNINGS

    val controller = BuildAnalyzerViewController(model, analytics, issueReporter)
    controller.dataSetComboBoxSelectionUpdated(BuildAnalyzerViewModel.DataSet.OVERVIEW)

    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.OVERVIEW)

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }

    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.verifyComboBoxPageChangeEvent(
      from = BuildAttributionUiEvent.Page.PageType.WARNINGS_ROOT,
      to = BuildAttributionUiEvent.Page.PageType.BUILD_SUMMARY
    )
  }

  @Test
  @RunsInEdt
  fun testTasksGroupingSelectionUpdated() {
    val controller = BuildAnalyzerViewController(model, analytics, issueReporter)

    // Act
    controller.tasksGroupingSelectionUpdated(TasksDataPageModel.Grouping.BY_PLUGIN)

    // Assert
    assertThat(model.tasksPageModel.selectedGrouping).isEqualTo(TasksDataPageModel.Grouping.BY_PLUGIN)
    // TODO (b/154988129): what metrics should be tracked here?
  }

  @Test
  @RunsInEdt
  fun testTasksNodeSelectionUpdated() {
    val controller = BuildAnalyzerViewController(model, analytics, issueReporter)
    // Second node in current (ungrouped) tasks tree.
    val nodeToSelect = model.tasksPageModel.selectedNode!!.nextNode as TasksTreeNode

    // Act
    controller.tasksTreeNodeSelected(nodeToSelect)

    // Assert
    assertThat(model.tasksPageModel.selectedNode).isEqualTo(nodeToSelect)
    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.apply {
      assertThat(eventType).isEqualTo(BuildAttributionUiEvent.EventType.PAGE_CHANGE_TREE_CLICK)
      assertThat(targetPage.pageType).isEqualTo(BuildAttributionUiEvent.Page.PageType.CRITICAL_PATH_TASK_PAGE)
    }
  }

  @Test
  @RunsInEdt
  fun testTasksDetailsLinkClicked() {
    val controller = BuildAnalyzerViewController(model, analytics, issueReporter)
    // Second node in current (ungrouped) tasks tree.
    val nodeToSelect = model.tasksPageModel.selectedNode!!.nextNode as TasksTreeNode

    // Act
    controller.tasksDetailsLinkClicked(nodeToSelect.descriptor.pageId)

    // Assert
    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.TASKS)
    assertThat(model.tasksPageModel.selectedNode).isEqualTo(nodeToSelect)

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.apply {
      assertThat(eventType).isEqualTo(BuildAttributionUiEvent.EventType.PAGE_CHANGE_LINK_CLICK)
      assertThat(targetPage.pageType).isEqualTo(BuildAttributionUiEvent.Page.PageType.CRITICAL_PATH_TASK_PAGE)
    }
  }

  @Test
  @RunsInEdt
  fun testTasksDetailsLinkClickedOnPlugin() {
    val controller = BuildAnalyzerViewController(model, analytics, issueReporter)
    val pluginPageId = TasksPageId.plugin(model.reportUiData.criticalPathPlugins.plugins[0])

    // Act
    controller.tasksDetailsLinkClicked(pluginPageId)

    // Assert
    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.TASKS)
    assertThat(model.tasksPageModel.selectedNode!!.descriptor.pageId).isEqualTo(pluginPageId)
    assertThat(model.tasksPageModel.selectedGrouping).isEqualTo(TasksDataPageModel.Grouping.BY_PLUGIN)
    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.apply {
      assertThat(eventType).isEqualTo(BuildAttributionUiEvent.EventType.PAGE_CHANGE_LINK_CLICK)
      assertThat(targetPage.pageType).isEqualTo(BuildAttributionUiEvent.Page.PageType.PLUGIN_PAGE)
    }
  }

  @Test
  @RunsInEdt
  fun testWarningsNodeSelectionUpdated() {
    val controller = BuildAnalyzerViewController(model, analytics, issueReporter)
    // Second node in current (ungrouped) tasks tree.
    val nodeToSelect = model.warningsPageModel.selectedNode!!.nextNode as WarningsTreeNode

    // Act
    controller.warningsTreeNodeSelected(nodeToSelect)

    // Assert
    assertThat(model.warningsPageModel.selectedNode).isEqualTo(nodeToSelect)
    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.apply {
      assertThat(eventType).isEqualTo(BuildAttributionUiEvent.EventType.PAGE_CHANGE_TREE_CLICK)
      assertThat(targetPage.pageType).isEqualTo(BuildAttributionUiEvent.Page.PageType.ALWAYS_RUN_NO_OUTPUTS_PAGE)
    }
  }


  @Test
  @RunsInEdt
  fun testGenerateReportClicked() {
    val controller = BuildAnalyzerViewController(model, analytics, issueReporter)
    val taskData = (model.tasksPageModel.selectedNode!!.descriptor as TaskDetailsNodeDescriptor).taskData

    // Act
    controller.generateReportClicked(taskData)

    // Assert
    Mockito.verify(issueReporter).reportIssue(eq(taskData))
    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.apply {
      assertThat(eventType).isEqualTo(BuildAttributionUiEvent.EventType.GENERATE_REPORT_LINK_CLICKED)
    }
  }

  @Test
  @RunsInEdt
  fun testHelpLinkClicked() {
    val controller = BuildAnalyzerViewController(model, analytics, issueReporter)

    // Act
    controller.helpLinkClicked()

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.apply {
      assertThat(eventType).isEqualTo(BuildAttributionUiEvent.EventType.HELP_LINK_CLICKED)
    }
  }

  private fun BuildAttributionUiEvent.verifyComboBoxPageChangeEvent(
    from: BuildAttributionUiEvent.Page.PageType,
    to: BuildAttributionUiEvent.Page.PageType
  ) {
    assertThat(buildAttributionReportSessionId).isEqualTo(buildSessionId)
    // TODO (b/154988129): update type to combo-box usage when ready
    assertThat(eventType).isEqualTo(BuildAttributionUiEvent.EventType.UNKNOWN_TYPE)
    assertThat(currentPage.pageType).isEqualTo(from)
    assertThat(currentPage.pageEntryIndex).isEqualTo(1)
    assertThat(targetPage.pageType).isEqualTo(to)
    assertThat(targetPage.pageEntryIndex).isEqualTo(1)
  }
}