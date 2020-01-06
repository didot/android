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
package com.android.build.attribution.ui.tree

import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.CriticalPathPluginsUiData
import com.android.build.attribution.ui.data.CriticalPathTasksUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.build.attribution.ui.panels.CriticalPathChartLegend
import com.android.build.attribution.ui.panels.TimeDistributionChart
import com.android.build.attribution.ui.pluginIcon
import com.android.build.attribution.ui.taskIcon
import com.intellij.util.ui.UIUtil
import java.util.ArrayList
import javax.swing.Icon


const val MAX_ITEMS_SHOWN_SEPARATELY = 15

fun createTaskChartItems(data: CriticalPathTasksUiData): List<TimeDistributionChart.ChartDataItem<TaskUiData>> {
  val result = ArrayList<TimeDistributionChart.ChartDataItem<TaskUiData>>()
  val aggregatedTasks = ArrayList<TaskUiData>()
  data.tasks
    .sortedByDescending { it.executionTime.timeMs }
    .forEach { taskData ->
      if (result.size < MAX_ITEMS_SHOWN_SEPARATELY) {
        result.add(TaskChartItem(
          taskData = taskData,
          assignedColor = CriticalPathChartLegend.resolveTaskColor(taskData)
        ))
      }
      else {
        aggregatedTasks.add(taskData)
      }
    }

  when {
    aggregatedTasks.size > 1 -> result.add(OtherChartItem(
      time = TimeWithPercentage(aggregatedTasks.map { it.executionTime.timeMs }.sum(), data.criticalPathDuration.totalMs),
      textPrefix = "Other tasks",
      aggregatedItems = aggregatedTasks,
      assignedColor = CriticalPathChartLegend.OTHER_TASKS_COLOR
    ))
    aggregatedTasks.size == 1 -> result.add(TaskChartItem(
      taskData = aggregatedTasks[0],
      assignedColor = CriticalPathChartLegend.resolveTaskColor(aggregatedTasks[0])
    ))
  }

  result.add(MiscGradleStepsChartItem(data.miscStepsTime))
  return result
}

fun createPluginChartItems(data: CriticalPathPluginsUiData): List<TimeDistributionChart.ChartDataItem<CriticalPathPluginUiData>> {
  val result = ArrayList<TimeDistributionChart.ChartDataItem<CriticalPathPluginUiData>>()
  val palette = CriticalPathChartLegend.PluginColorPalette()
  val aggregatedPlugins = ArrayList<CriticalPathPluginUiData>()
  data.plugins
    .sortedByDescending { it.criticalPathDuration.timeMs }
    .forEach { pluginData ->
      if (result.size < MAX_ITEMS_SHOWN_SEPARATELY) {
        result.add(PluginChartItem(
          pluginData = pluginData,
          assignedColor = palette.newColor))
      }
      else {
        aggregatedPlugins.add(pluginData)
      }
    }

  when {
    aggregatedPlugins.size > 1 -> result.add(OtherChartItem(
      time = TimeWithPercentage(aggregatedPlugins.map { it.criticalPathDuration.timeMs }.sum(), data.criticalPathDuration.totalMs),
      textPrefix = "Other plugins",
      aggregatedItems = aggregatedPlugins,
      assignedColor = palette.newColor
    ))
    aggregatedPlugins.size == 1 -> result.add(PluginChartItem(
      pluginData = aggregatedPlugins[0],
      assignedColor = palette.newColor
    ))
  }

  result.add(MiscGradleStepsChartItem(data.miscStepsTime))
  return result
}


private class TaskChartItem(
  private val taskData: TaskUiData,
  private val assignedColor: CriticalPathChartLegend.ChartColor
) : TimeDistributionChart.SingularChartDataItem<TaskUiData> {

  override fun time(): TimeWithPercentage {
    return taskData.executionTime
  }

  override fun text(): String {
    return taskData.taskPath
  }

  override fun getTableIcon() = taskIcon(taskData)

  override fun getLegendColor(): CriticalPathChartLegend.ChartColor {
    return assignedColor
  }

  override fun chartBoxText(): String? {
    return null
  }

  override fun getUnderlyingData(): TaskUiData {
    return taskData
  }
}

private class PluginChartItem(
  private val pluginData: CriticalPathPluginUiData,
  private val assignedColor: CriticalPathChartLegend.ChartColor
) : TimeDistributionChart.SingularChartDataItem<CriticalPathPluginUiData> {

  override fun time(): TimeWithPercentage {
    return pluginData.criticalPathDuration
  }

  override fun text(): String {
    return pluginData.name
  }

  override fun getTableIcon(): Icon? = pluginIcon(pluginData)

  override fun getLegendColor(): CriticalPathChartLegend.ChartColor {
    return assignedColor
  }

  override fun chartBoxText(): String? {
    return null
  }

  override fun getUnderlyingData(): CriticalPathPluginUiData {
    return pluginData
  }
}

private class OtherChartItem<T>(
  private val time: TimeWithPercentage,
  private val textPrefix: String,
  private val aggregatedItems: List<T>,
  private val assignedColor: CriticalPathChartLegend.ChartColor
) : TimeDistributionChart.AggregatedChartDataItem<T> {

  override fun time(): TimeWithPercentage {
    return time
  }

  override fun text(): String {
    return textPrefix + String.format(" (%d)", aggregatedItems.size)
  }

  override fun getTableIcon(): Icon? {
    return null
  }

  override fun getLegendColor(): CriticalPathChartLegend.ChartColor {
    return assignedColor
  }

  override fun chartBoxText(): String {
    return textPrefix
  }

  override fun getUnderlyingData(): List<T> {
    return aggregatedItems
  }
}

class MiscGradleStepsChartItem<T>(val miscStepsTime: TimeWithPercentage) : TimeDistributionChart.ChartDataItem<T> {

  override fun time(): TimeWithPercentage {
    return miscStepsTime
  }

  override fun text(): String {
    return "Misc Gradle steps"
  }

  override fun getTableIcon(): Icon? {
    return null
  }

  override fun getLegendColor(): CriticalPathChartLegend.ChartColor {
    return CriticalPathChartLegend.MISC_COLOR
  }

  override fun chartBoxText(): String? {
    return null
  }

  override fun selectedTextColor() = UIUtil.getInactiveTextColor()

  override fun unselectedTextColor() = UIUtil.getInactiveTextColor()
}
