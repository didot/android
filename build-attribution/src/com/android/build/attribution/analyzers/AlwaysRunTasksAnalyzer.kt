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
package com.android.build.attribution.analyzers

import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.data.TaskData
import org.gradle.api.internal.changedetection.TaskExecutionMode
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult

/**
 * Analyzer for reporting tasks that always run due to misconfiguration.
 */
class AlwaysRunTasksAnalyzer(override val warningsFilter: BuildAttributionWarningsFilter, taskContainer: TaskContainer)
  : BaseTasksAnalyzer(taskContainer), BuildEventsAnalyzer {
  private val alwaysRunTasksSet = HashSet<AlwaysRunTaskData>()
  lateinit var alwaysRunTasks: List<AlwaysRunTaskData>
    private set

  override fun receiveEvent(event: ProgressEvent) {
    if (event is TaskFinishEvent && event.result is TaskSuccessResult) {
      (event.result as TaskSuccessResult).executionReasons?.forEach {
        if (it == TaskExecutionMode.NO_OUTPUTS_WITHOUT_ACTIONS.rebuildReason.get() ||
            it == TaskExecutionMode.NO_OUTPUTS_WITH_ACTIONS.rebuildReason.get()) {
          alwaysRunTasksSet.add(AlwaysRunTaskData(getTask(event), it))
        }
      }
    }
  }

  override fun onBuildSuccess() {
    alwaysRunTasks = alwaysRunTasksSet.filter { warningsFilter.applyAlwaysRunTaskFilter(it.taskData) }
    alwaysRunTasksSet.clear()
  }

  override fun onBuildFailure() {
    alwaysRunTasksSet.clear()
  }

  data class AlwaysRunTaskData(val taskData: TaskData, val reason: String)
}
