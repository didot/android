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
package com.android.build.attribution.ui.data.builder

import com.android.build.attribution.analyzers.BuildEventsAnalysisResult
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.ui.data.InterTaskIssueUiData
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import java.util.EnumMap


/**
 * This class holds [TaskIssueUiData] representations for issues detected by Gradle build analyzers.
 * Clients may assume there is only one [TaskIssueUiData] for each issue detected.
 * It gets build analysis results from [buildAnalysisResult].
 */
class TaskIssueUiDataContainer(
  private val buildAnalysisResult: BuildEventsAnalysisResult
) {

  private val issuesByTask: MutableMap<TaskData, MutableList<TaskIssueUiData>> = HashMap()
  private val issuesByType: MutableMap<TaskIssueType, MutableList<TaskIssueUiData>> = EnumMap(TaskIssueType::class.java)
  private val issuesByPlugin: MutableMap<PluginData, MutableList<TaskIssueUiData>> = HashMap()

  fun populate(tasksUiDataContainer: TaskUiDataContainer) {
    buildAnalysisResult.getAlwaysRunTasks().forEach {
      addNewIssue(
        it.taskData,
        if (it.rerunReason == AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE) {
          AlwaysRunUpToDateOverride(tasksUiDataContainer.getByTaskData(it.taskData))
        }
        else {
          AlwaysRunNoOutputIssue(tasksUiDataContainer.getByTaskData(it.taskData))
        }
      )
    }
    buildAnalysisResult.getTasksSharingOutput().forEach { taskSharingIssue ->
      taskSharingIssue.taskList.forEach { task ->
        addNewIssue(
          taskData = task,
          issueUiData = TaskSetupIssue(
            task = tasksUiDataContainer.getByTaskData(task),
            connectedTask = tasksUiDataContainer.getByTaskData(taskSharingIssue.taskList.first { it != task }),
            outputFolder = taskSharingIssue.outputFilePath
          )
        )
      }
    }
  }

  private fun addNewIssue(taskData: TaskData, issueUiData: TaskIssueUiData) {
    issuesByTask.computeIfAbsent(taskData) { ArrayList() }.add(issueUiData)
    issuesByPlugin.computeIfAbsent(taskData.originPlugin) { ArrayList() }.add(issueUiData)
    issuesByType.computeIfAbsent(issueUiData.type) { ArrayList() }.add(issueUiData)
  }

  fun issuesForTask(taskData: TaskData): List<TaskIssueUiData> = issuesByTask[taskData] ?: emptyList()

  /**
   * Returns all [TaskIssueUiData] representations for detected issues grouped by issue type.
   */
  fun allIssueGroups(): List<TaskIssuesGroup> = issuesByType
    .map { (issueType, issuesList) -> toTaskIssueGroup(issueType, issuesList) }
    .sortedBy { it.type.ordinal }

  private fun toTaskIssueGroup(issueType: TaskIssueType, issuesList: List<TaskIssueUiData>): TaskIssuesGroup = object : TaskIssuesGroup {
    override val type = issueType
    override val issues: List<TaskIssueUiData> = issuesList.sortedByDescending { it.task.executionTime }
    override val timeContribution =
      TimeWithPercentage(issues.map { it.task.executionTime.timeMs }.sum(), buildAnalysisResult.getTotalBuildTimeMs())
  }

  /**
   * Returns all [TaskIssueUiData] representations for detected issues for plugin passed as [pluginData] grouped by issue type.
   * [pluginData] is a plugin object from gradle build analysers.
   */
  fun pluginIssueGroups(pluginData: PluginData): List<TaskIssuesGroup> = issuesByPlugin
    .getOrDefault(pluginData, emptyList<TaskIssueUiData>())
    .groupBy { it.type }
    .map { (issueType, issuesList) -> toTaskIssueGroup(issueType, issuesList) }
    .sortedBy { it.type.ordinal }

  private inner class TaskSetupIssue(
    override val task: TaskUiData,
    override val connectedTask: TaskUiData,
    val outputFolder: String
  ) : InterTaskIssueUiData {
    override val type = TaskIssueType.TASK_SETUP_ISSUE
    override val explanation = """
This task declares the same output directory as task ${connectedTask.taskPath}: <span>${outputFolder}</span>.
As a result, these tasks are not able to take advantage of incremental build optimizations,
and might need to run with each subsequent build.
"""
    override val helpLink = "https://d.android.com/r/tools/build-attribution/duplicate-output-folder"
  }

  private inner class AlwaysRunNoOutputIssue(
    override val task: TaskUiData
  ) : TaskIssueUiData {
    override val type = TaskIssueType.ALWAYS_RUN_TASKS
    override val explanation: String = "This task runs on every build because it declares no outputs, which it must do in order to support incremental builds."
    override val helpLink = "https://d.android.com/r/tools/build-attribution/no-task-outputs-declared"
  }

  private inner class AlwaysRunUpToDateOverride(
    override val task: TaskUiData
  ) : TaskIssueUiData {
    override val type = TaskIssueType.ALWAYS_RUN_TASKS
    override val explanation: String = """
This task might be setting its up-to-date check to always return <code>false</code>,
which means that it must regenerate its output during every build.
For example, the task might set the following: <code>outputs.upToDateWhen { false }</code>.
To optimize task execution with up-to-date checks, remove the <code>upToDateWhen</code> enclosure.
"""
    override val helpLink = "https://d.android.com/r/tools/build-attribution/upToDateWhen-equals-false"
  }
}
