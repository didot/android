/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu.atrace

import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.CpuThreadInfo
import com.android.tools.profilers.cpu.atrace.AtraceFrameFilterConfig.APP_MAIN_THREAD_FRAME_ID_MPLUS
import com.google.common.collect.Iterables
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AtraceParserTest {

  val myParser = AtraceParser(TEST_PID)
  lateinit var myCapture: CpuCapture

  @Before
  fun setup() {
    myCapture = myParser.parse(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"), 0)
  }

  @Test
  fun testGetParseRange() {
    // Value comes from atrace.ctrace file first entry and last entry.
    val expected = Range(EXPECTED_MIN_RANGE, EXPECTED_MAX_RANGE)
    val actual = myParser.range
    assertThat(actual.min).isWithin(DELTA).of(expected.min)
    assertThat(actual.max).isWithin(DELTA).of(expected.max)
    assertThat(actual.length).isWithin(DELTA).of(expected.length)
  }

  @Test
  fun testGetCaptureTrees() {
    val range = myParser.range
    val result = myParser.captureTrees
    assertThat(result).hasSize(20)
    val cpuThreadInfo = Iterables.find(result.keys, { key -> key?.id == TEST_PID })
    assertThat(cpuThreadInfo.id).isEqualTo(TEST_PID)
    // Atrace only contains the last X characters, in the log file.
    assertThat(cpuThreadInfo.name).isEqualTo("splayingbitmaps")
    assertThat(cpuThreadInfo.id).isEqualTo(TEST_PID)
    // Validate capture trees sets the process name and id for threads.
    assertThat(cpuThreadInfo).isInstanceOf(CpuThreadSliceInfo::class.java)
    val cpuProcessInfo = cpuThreadInfo as CpuThreadSliceInfo
    assertThat(cpuProcessInfo.processName).isEqualTo("splayingbitmaps")
    assertThat(cpuProcessInfo.processId).isEqualTo(TEST_PID)
    assertThat(cpuProcessInfo.isMainThread).isTrue()

    // Base node is a root node that is equivlant to the length of capture.
    val captureNode = result.get(cpuThreadInfo)!!
    assertThat(captureNode.startGlobal).isEqualTo(range.min.toLong())
    assertThat(captureNode.endGlobal).isEqualTo(range.max.toLong())
    assertThat(captureNode.childCount).isEqualTo(EXPECTED_CHILD_COUNT)
    assertThat(captureNode.getChildAt(0).start).isEqualTo(SINGLE_CHILD_EXPECTED_START)
    assertThat(captureNode.getChildAt(0).end).isEqualTo(SINGLE_CHILD_EXPECTED_END)
    assertThat(captureNode.getChildAt(0).data.name).isEqualTo(EXPECTED_METHOD_NAME)
    assertThat(captureNode.getChildAt(0).start).isGreaterThan(range.min.toLong())
    assertThat(captureNode.getChildAt(0).start).isLessThan(range.max.toLong())
    assertThat(captureNode.getChildAt(0).end).isGreaterThan(range.min.toLong())
    assertThat(captureNode.getChildAt(0).end).isLessThan(range.max.toLong())
    assertThat(captureNode.getChildAt(0).depth).isEqualTo(1)
    assertThat(captureNode.getChildAt(2).getChildAt(0).depth).isEqualTo(2)
  }

  @Test
  fun testGetCaptureTreesSetsThreadTime() {
    val result = myParser.captureTrees
    val cpuThreadInfo = Iterables.find(result.keys, { key -> key?.id == TEST_PID })

    val captureNode = result[cpuThreadInfo]!!
    // Grab the element at index 1 and use its child because, the child is the element that has idle cpu time.
    val child = captureNode.getChildAt(1)
    // Validate our child's thread time starts at our global start.
    assertThat(child.startThread).isEqualTo(child.startGlobal)
    // Validate our end time does not equal our global end time.
    assertThat(child.endThread).isNotEqualTo(child.endGlobal)
    assertThat(child.endThread).isEqualTo(EXPECTED_THREAD_END_TIME)
  }

  @Test
  fun testGetThreadStateDataSeries() {
    val dataSeries = myParser.threadStateDataSeries
    assertThat(dataSeries).hasSize(THREAD_STATE_SERIES_SIZE)
    assertThat(dataSeries[THREAD_ID]!!.size).isEqualTo(THREAD_STATE_SIZE)
    assertThat(dataSeries[THREAD_ID]!!.get(0).x).isGreaterThan(EXPECTED_MIN_RANGE.toLong())
    // Waking / Runnable = RUNNABLE.
    assertThat(dataSeries[THREAD_ID]!!.get(0).value).isEqualTo(CpuProfilerStage.ThreadState.RUNNABLE_CAPTURED)
    // Running = RUNNING
    assertThat(dataSeries[THREAD_ID]!!.get(1).value).isEqualTo(CpuProfilerStage.ThreadState.RUNNING_CAPTURED)
  }

  @Test
  fun testGetCpuUtilizationDataSeries() {
    val dataSeries = myParser.cpuUtilizationSeries
    val size = 100 / myParser.cpuThreadSliceInfoStates.size.toDouble()
    // No values should exceed the bounds
    for (data in dataSeries) {
      assertThat(data.value).isAtLeast(0)
      assertThat(data.value).isAtMost(100)
      assertThat(data.value % size).isEqualTo(0.0)
    }
  }

  @Test
  fun testGetCpuProcessData() {
    val dataSeries = myParser.cpuThreadSliceInfoStates
    assertThat(dataSeries).hasSize(4)
    for (i in 0..3) {
      assertThat(dataSeries.containsKey(i))
    }
    // Verify that we have a perfd process, null process, then rcu process.
    // Verifying the null process is important as it ensures we render the data properly.
    assertThat(dataSeries[0]!![0].value.name).matches("perfd")
    assertThat(dataSeries[0]!![1].value).isEqualTo(CpuThreadSliceInfo.NULL_THREAD)
    assertThat(dataSeries[0]!![2].value.name).matches("rcu_preempt")
  }

  @Test
  fun testInvalidProcessIdThrows() {
    var expectedExceptionCaught = false
    try {
      val parser = AtraceParser(AtraceParser.INVALID_PROCESS)
      parser.parse(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"), 0)
    } catch (e: IllegalArgumentException) {
      expectedExceptionCaught = true
    }
    assertThat(expectedExceptionCaught).isTrue()
  }

  @Test
  fun framesEndWithEmptyFrame() {
    val frameFilter = AtraceFrameFilterConfig(APP_MAIN_THREAD_FRAME_ID_MPLUS, AtraceTestUtils.TEST_PID,
                                              TimeUnit.MILLISECONDS.toMicros(30));
    val frames = myParser.getFrames(frameFilter)
    // Each frame has a empty frame after it for spacing.
    assertThat(frames).hasSize(122 * 2)
    for (i in 0 until frames.size step 2) {
      assertThat(frames[i].value).isNotEqualTo(AtraceFrame.EMPTY)
      assertThat(frames[i + 1].value).isEqualTo(AtraceFrame.EMPTY)
    }
  }

  @Test
  fun getProcessListReturnsProcessList() {
    val headOfListExpected = arrayOf(CpuThreadInfo(1510, "system_server"),
                                     CpuThreadInfo(2652, "splayingbitmaps"),
                                     CpuThreadInfo(1371, "surfaceflinger"))
    val tailOfListExpected = arrayOf(CpuThreadInfo(1404, "<1404>"),
                                     CpuThreadInfo(2732, "<2732>"),
                                     CpuThreadInfo(2713, "<2713>"))
    val parser = AtraceParser(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"))
    val processes = parser.getProcessList("")
    // Validate the head of our list is organized as expected
    for (i in headOfListExpected.indices) {
      val threadInfo = headOfListExpected[i]
      assertThat(processes[i].id).isEqualTo(threadInfo.id)
      assertThat(processes[i].name).isEqualTo(threadInfo.name)
    }
    // Validate the tail of the list has all the <> values.
    for (i in tailOfListExpected.indices) {
      val threadInfo = tailOfListExpected[i]
      val endIndex = processes.size - (i + 1)
      assertThat(processes[endIndex].id).isEqualTo(threadInfo.id)
      assertThat(processes[endIndex].name).isEqualTo(threadInfo.name)
    }
  }

  @Test
  fun hintedProcessNameIsTop() {
    val parser = AtraceParser(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"))
    // No hint is alphabetical
    var processes = parser.getProcessList("")
    assertThat(processes[0].processName).isEqualTo("system_server")
    // No matching hint is still alphabetical.
    processes = parser.getProcessList("something.crazy.nothing.matches")
    assertThat(processes[0].processName).isEqualTo("system_server")
    // Substring matches
    processes = parser.getProcessList("com.google.package.atrace")
    assertThat(processes[0].processName).isEqualTo("atrace")
    // Exact string
    processes = parser.getProcessList("atrace")
    assertThat(processes[0].processName).isEqualTo("atrace")
  }

  @Test
  fun settingSelectedProcessReturnsParseWithThatProesssId() {
    val parser = AtraceParser(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"))
    parser.setSelectProcess(parser.getProcessList("")[0])
    val parsedFile = parser.parse(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"), 0)
    assertThat(parsedFile.mainThreadId).isEqualTo(parser.getProcessList("")[0].id)
  }

  @Test
  fun processNameThatWouldBePidUsesThreadNameInstead() {
    val parser = AtraceParser(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"))
    val info = parser.getProcessList(".gms.persistent")[0]
    assertThat(info.processName).isEqualTo(".gms.persistent")
  }

  @Test
  fun missingDataCaptureReturnsMissingdata() {
    val parser = AtraceParser(CpuProfilerTestUtils.getTraceFile("atrace_processid_1.ctrace"))
    assertThat(parser.isMissingData).isTrue()
  }

  companion object {
    private val DELTA = .00000001

    // Setting const for atrace file in one location so if we update file we can update const in one location.
    private val EXPECTED_MIN_RANGE = 8.7688546852E10
    private val EXPECTED_MAX_RANGE = 8.7701855476E10
    private val SINGLE_CHILD_EXPECTED_START = 87691109724
    private val SINGLE_CHILD_EXPECTED_END = 87691109942
    private val EXPECTED_THREAD_END_TIME = 87691120728
    private val EXPECTED_CHILD_COUNT = 213
    private val EXPECTED_METHOD_NAME = "setupGridItem"
    private val TEST_PID = 2652
    private val THREAD_ID = 2659
    private val THREAD_STATE_SERIES_SIZE = 20
    private val THREAD_STATE_SIZE = 1316
  }
}
