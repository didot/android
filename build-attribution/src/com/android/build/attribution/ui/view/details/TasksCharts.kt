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
package com.android.build.attribution.ui.view.details

import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.TasksPageId
import com.android.build.attribution.ui.panels.TimeDistributionChart
import com.android.build.attribution.ui.tree.createPluginChartItems
import com.android.build.attribution.ui.tree.createTaskChartItems
import com.intellij.ui.CardLayoutPanel

/**
 * This is temporal solution to show old time distribution visualisation on a tasks page of new navigation model.
 * By the design visualisation should be integrated to the task list itself and this will be implemented as the next step.
 */
class ChartsPanel(
  reportData: BuildAttributionReportUiData
) : CardLayoutPanel<TasksPageId, TasksPageId, TimeDistributionChart<*>>() {

  val ungroupedChartItems: List<TimeDistributionChart.ChartDataItem<TaskUiData>> = createTaskChartItems(reportData.criticalPathTasks)

  val pluginChartItems: List<TimeDistributionChart.ChartDataItem<CriticalPathPluginUiData>> = createPluginChartItems(
    reportData.criticalPathPlugins)

  val pageIdToTaskChartElement: Map<TasksPageId, TimeDistributionChart.ChartDataItem<TaskUiData>> = ungroupedChartItems
    .asSequence().flatMap { item ->
      when (item) {
        is TimeDistributionChart.SingularChartDataItem<TaskUiData> ->
          sequenceOf(TasksPageId.task(item.underlyingData, TasksDataPageModel.Grouping.UNGROUPED) to item)
        is TimeDistributionChart.AggregatedChartDataItem<TaskUiData> ->
          item.underlyingData.asSequence().map { taskData -> TasksPageId.task(taskData, TasksDataPageModel.Grouping.UNGROUPED) to item }
        else -> emptySequence()
      }
    }.toMap()

  val pageIdToPluginChartElement: Map<TasksPageId, TimeDistributionChart.ChartDataItem<CriticalPathPluginUiData>> = pluginChartItems
    .asSequence().flatMap { item ->
      fun pluginAndItsTasksIds(plugin: CriticalPathPluginUiData) = sequence {
        yield(TasksPageId.plugin(plugin))
        yieldAll(
          plugin.criticalPathTasks.tasks.asSequence().map {
            TasksPageId.task(it, TasksDataPageModel.Grouping.BY_PLUGIN)
          }
        )
      }
      when (item) {
        is TimeDistributionChart.SingularChartDataItem<CriticalPathPluginUiData> ->
          pluginAndItsTasksIds(item.underlyingData).map { it to item }
        is TimeDistributionChart.AggregatedChartDataItem<CriticalPathPluginUiData> ->
          item.underlyingData.asSequence().flatMap { pluginAndItsTasksIds(it) }.map { it to item }
        else -> emptySequence()
      }
    }.toMap()


  override fun prepare(key: TasksPageId): TasksPageId = key

  override fun create(id: TasksPageId): TimeDistributionChart<*>? = when (id.grouping) {
    TasksDataPageModel.Grouping.UNGROUPED -> pageIdToTaskChartElement[id]?.let {
      TimeDistributionChart(ungroupedChartItems, it, false).apply { name = "task-chart-selected-${it.text()}" }
    }
    TasksDataPageModel.Grouping.BY_PLUGIN -> pageIdToPluginChartElement[id]?.let {
      TimeDistributionChart(pluginChartItems, it, false).apply { name = "plugin-chart-selected-${it.text()}" }
    }
  }
}
