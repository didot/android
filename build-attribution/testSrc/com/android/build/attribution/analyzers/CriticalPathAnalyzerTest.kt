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
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskContainer
import com.android.build.attribution.data.TaskData
import com.google.common.truth.Truth.assertThat
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import org.junit.Test

class CriticalPathAnalyzerTest {

  @Test
  fun testCriticalPathAnalyzer() {
    val pluginContainer = PluginContainer()
    val analyzer = CriticalPathAnalyzer(BuildAttributionWarningsFilter(), TaskContainer(), pluginContainer)

    val pluginA = createBinaryPluginIdentifierStub("pluginA")
    val pluginB = createBinaryPluginIdentifierStub("pluginB")

    analyzer.onBuildStart()

    // Given tasks (A, B, C, D, E, F, CLEAN, MID1, MID2, MID3, LAST) with the following dependencies and execution times
    // A(10) -> B(20) -> D(20) -> F(10)
    // |                 ^
    // -------> C(30) -----> E(20)
    //
    // CLEAN(5)
    //
    // MID1(4) -> MID2(4)
    //
    // MID3(6)
    //
    // LAST(7)

    val taskClean = createTaskFinishEventStub(":clean", pluginA, emptyList(), 0, 5)
    var taskA = createTaskFinishEventStub(":app:taskA", pluginA, emptyList(), 10, 20)
    val taskMid1 = createTaskFinishEventStub(":app:mid1", pluginA, emptyList(), 21, 25)
    val taskMid2 = createTaskFinishEventStub(":app:mid2", pluginB, listOf(taskMid1), 26, 30)
    val taskMid3 = createTaskFinishEventStub(":app:mid3", pluginB, emptyList(), 22, 28)
    var taskB = createTaskFinishEventStub(":app:taskB", pluginB, listOf(taskA), 30, 50)
    var taskC = createTaskFinishEventStub(":lib:taskC", pluginA, listOf(taskA), 30, 60)
    var taskD = createTaskFinishEventStub(":app:taskD", pluginB, listOf(taskB, taskC), 60, 80)
    var taskE = createTaskFinishEventStub(":app:taskE", pluginA, listOf(taskC), 60, 80)
    var taskF = createTaskFinishEventStub(":lib:taskF", pluginB, listOf(taskD), 80, 90)
    val taskLast = createTaskFinishEventStub(":lib:taskLast", pluginA, emptyList(), 100, 117)

    analyzer.receiveEvent(taskClean)
    analyzer.receiveEvent(taskA)
    analyzer.receiveEvent(taskMid1)
    analyzer.receiveEvent(taskMid2)
    analyzer.receiveEvent(taskMid3)
    analyzer.receiveEvent(taskB)
    analyzer.receiveEvent(taskC)
    analyzer.receiveEvent(taskD)
    analyzer.receiveEvent(taskE)
    analyzer.receiveEvent(taskF)
    analyzer.receiveEvent(taskLast)

    // When the build is finished successfully and the analyzer is run

    analyzer.onBuildSuccess()

    // Then the analyzer should find this critical path
    // CLEAN(5) -> A(10) -> MID1(4) -> MID2(4) -> C(30) -> D(20) -> F(10) -> LAST(17)

    assertThat(analyzer.tasksDeterminingBuildDuration.sumByLong { it.executionTime }).isEqualTo(100)

    assertThat(analyzer.tasksDeterminingBuildDuration).isEqualTo(
      listOf(TaskData.createTaskData(taskClean, pluginContainer),
             TaskData.createTaskData(taskA, pluginContainer),
             TaskData.createTaskData(taskMid1, pluginContainer),
             TaskData.createTaskData(taskMid2, pluginContainer),
             TaskData.createTaskData(taskC, pluginContainer),
             TaskData.createTaskData(taskD, pluginContainer),
             TaskData.createTaskData(taskF, pluginContainer),
             TaskData.createTaskData(taskLast, pluginContainer)))

    assertThat(analyzer.pluginsDeterminingBuildDuration).hasSize(2)
    assertThat(analyzer.pluginsDeterminingBuildDuration[0].plugin).isEqualTo(PluginData(pluginA, ""))
    assertThat(analyzer.pluginsDeterminingBuildDuration[0].buildDuration).isEqualTo(66)
    assertThat(analyzer.pluginsDeterminingBuildDuration[1].plugin).isEqualTo(PluginData(pluginB, ""))
    assertThat(analyzer.pluginsDeterminingBuildDuration[1].buildDuration).isEqualTo(34)


    // A subsequent build has started, the analyzer should reset its state and prepare for the next build data
    analyzer.onBuildStart()

    // Given tasks (A, B, C, D, E, F) with the following dependencies and execution times
    // A(10) -> B(5) -> D(25) -> F(10)
    // |                V
    // -------> C(40)   --> E(15)

    taskA = createTaskFinishEventStub(":app:taskA", pluginA, emptyList(), 0, 10)
    taskB = createTaskFinishEventStub(":app:taskB", pluginB, listOf(taskA), 10, 15)
    taskC = createTaskFinishEventStub(":lib:taskC", pluginA, listOf(taskA), 10, 50)
    taskD = createTaskFinishEventStub(":app:taskD", pluginB, listOf(taskB), 15, 40)
    taskE = createTaskFinishEventStub(":app:taskE", pluginA, listOf(taskD), 40, 55)
    taskF = createTaskFinishEventStub(":lib:taskF", pluginB, listOf(taskD), 40, 50)

    analyzer.receiveEvent(taskA)
    analyzer.receiveEvent(taskB)
    analyzer.receiveEvent(taskC)
    analyzer.receiveEvent(taskD)
    analyzer.receiveEvent(taskE)
    analyzer.receiveEvent(taskF)

    // When the build is finished successfully and the analyzer is run

    analyzer.onBuildSuccess()

    // Then the analyzer should find this critical path
    // A(10) -> B(5) -> D(25)
    //                  V
    //                  --> E(15)

    assertThat(analyzer.tasksDeterminingBuildDuration.sumByLong { it.executionTime }).isEqualTo(55)

    assertThat(analyzer.tasksDeterminingBuildDuration).isEqualTo(
      listOf(TaskData.createTaskData(taskA, pluginContainer), TaskData.createTaskData(taskB, pluginContainer),
             TaskData.createTaskData(taskD, pluginContainer),
             TaskData.createTaskData(taskE, pluginContainer)))

    assertThat(analyzer.pluginsDeterminingBuildDuration).hasSize(2)
    assertThat(analyzer.pluginsDeterminingBuildDuration[0].plugin).isEqualTo(PluginData(pluginB, ""))
    assertThat(analyzer.pluginsDeterminingBuildDuration[0].buildDuration).isEqualTo(30)
    assertThat(analyzer.pluginsDeterminingBuildDuration[1].plugin).isEqualTo(PluginData(pluginA, ""))
    assertThat(analyzer.pluginsDeterminingBuildDuration[1].buildDuration).isEqualTo(25)
  }
}
