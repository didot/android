/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.sessions

import com.android.testutils.TestUtils
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.stdui.CommonAction
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profiler.proto.MemoryProfiler
import com.android.tools.profiler.protobuf3jarjar.ByteString
import com.android.tools.profilers.*
import com.android.tools.profilers.cpu.*
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.*
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.android.tools.profilers.memory.adapters.LegacyAllocationCaptureObject
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.event.ActionEvent
import java.util.concurrent.TimeUnit

class SessionsViewTest {

  private val VALID_TRACE_PATH = "tools/adt/idea/profilers-ui/testData/valid_trace.trace"

  private val myProfilerService = FakeProfilerService(false)
  private val myMemoryService = FakeMemoryService()
  private val myCpuService = FakeCpuService()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel(
    "SessionsViewTestChannel",
    myProfilerService,
    myMemoryService,
    myCpuService,
    FakeEventService(),
    FakeNetworkService.newBuilder().build()
  )

  private lateinit var myTimer: FakeTimer
  private lateinit var myProfilers: StudioProfilers
  private lateinit var mySessionsManager: SessionsManager
  private lateinit var mySessionsView: SessionsView

  @Before
  fun setup() {
    myTimer = FakeTimer()
    myProfilers = StudioProfilers(myGrpcChannel.client, FakeIdeProfilerServices(), myTimer)
    mySessionsManager = myProfilers.sessionsManager
    mySessionsView = SessionsView(myProfilers, FakeIdeProfilerComponents())
  }

  @Test
  fun testSessionsListUpToDate() {
    val sessionsPanel = mySessionsView.sessionsPanel
    assertThat(sessionsPanel.componentCount).isEqualTo(0)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()

    myProfilerService.setTimestampNs(1)
    mySessionsManager.beginSession(device, process1)
    var session1 = mySessionsManager.selectedSession
    assertThat(sessionsPanel.componentCount).isEqualTo(1)
    var sessionItem0 = sessionsPanel.getComponent(0) as SessionItemView
    assertThat(sessionItem0.artifact.session).isEqualTo(session1)

    mySessionsManager.endCurrentSession()
    session1 = mySessionsManager.selectedSession
    assertThat(sessionsPanel.componentCount).isEqualTo(1)
    sessionItem0 = sessionsPanel.getComponent(0) as SessionItemView
    assertThat(sessionItem0.artifact.session).isEqualTo(session1)

    myProfilerService.setTimestampNs(2)
    mySessionsManager.beginSession(device, process2)
    val session2 = mySessionsManager.selectedSession
    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    // Sessions are sorted in descending order.
    sessionItem0 = sessionsPanel.getComponent(0) as SessionItemView
    var sessionItem1 = sessionsPanel.getComponent(1) as SessionItemView
    assertThat(sessionItem0.artifact.session).isEqualTo(session2)
    assertThat(sessionItem1.artifact.session).isEqualTo(session1)

    // Add the heap dump and CPU capture, expand the first session and make sure the artifacts are shown in the list
    val heapDumpTimestamp = 10L
    val cpuTraceTimestamp = 20L
    val heapDumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(heapDumpTimestamp).setEndTime(heapDumpTimestamp + 1).build()
    val cpuTraceInfo = CpuProfiler.TraceInfo.newBuilder().setFromTimestamp(cpuTraceTimestamp).setToTimestamp(cpuTraceTimestamp + 1).build()
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo)
    myCpuService.addTraceInfo(cpuTraceInfo)
    mySessionsManager.update()

    assertThat(sessionsPanel.componentCount).isEqualTo(6)
    sessionItem0 = sessionsPanel.getComponent(0) as SessionItemView
    val cpuCaptureItem0 = sessionsPanel.getComponent(1) as CpuCaptureArtifactView
    val hprofItem0 = sessionsPanel.getComponent(2) as HprofArtifactView
    sessionItem1 = sessionsPanel.getComponent(3) as SessionItemView
    val cpuCaptureItem1 = sessionsPanel.getComponent(4) as CpuCaptureArtifactView
    val hprofItem1 = sessionsPanel.getComponent(5) as HprofArtifactView
    assertThat(sessionItem0.artifact.session).isEqualTo(session2)
    assertThat(hprofItem0.artifact.session).isEqualTo(session2)
    assertThat(cpuCaptureItem0.artifact.session).isEqualTo(session2)
    assertThat(sessionItem1.artifact.session).isEqualTo(session1)
    assertThat(hprofItem1.artifact.session).isEqualTo(session1)
    assertThat(cpuCaptureItem1.artifact.session).isEqualTo(session1)
  }

  @Test
  fun testProcessDropdownUpToDate() {
    val device1 = Common.Device.newBuilder()
      .setDeviceId(1).setManufacturer("Manufacturer1").setModel("Model1").setState(Common.Device.State.ONLINE).build()
    val device2 = Common.Device.newBuilder()
      .setDeviceId(2).setManufacturer("Manufacturer2").setModel("Model2").setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(1).setName("Process1").setState(Common.Process.State.ALIVE).build()
    val otherProcess1 = Common.Process.newBuilder()
      .setPid(20).setDeviceId(1).setName("Other1").setState(Common.Process.State.ALIVE).build()
    val otherProcess2 = Common.Process.newBuilder()
      .setPid(30).setDeviceId(2).setName("Other2").setState(Common.Process.State.ALIVE).build()
    // Process* is preferred, Other* should be in the other processes flyout.
    myProfilers.setPreferredProcess("Manufacturer1 Model1", "Process", null)

    var selectionAction = mySessionsView.processSelectionAction
    assertThat(selectionAction.childrenActionCount).isEqualTo(3)
    var loadAction = selectionAction.childrenActions.first { c -> c.text == "Load from file..." }
    assertThat(loadAction.isEnabled).isTrue()
    assertThat(loadAction.childrenActionCount).isEqualTo(0)
    assertThat(selectionAction.childrenActions[1]).isInstanceOf(CommonAction.SeparatorAction::class.java)
    assertThat(selectionAction.childrenActions[2].text).isEqualTo(SessionsView.NO_SUPPORTED_DEVICES)
    assertThat(selectionAction.childrenActions[2].isEnabled).isFalse()

    myProfilerService.addDevice(device1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(3)
    assertThat(selectionAction.childrenActions[1]).isInstanceOf(CommonAction.SeparatorAction::class.java)
    loadAction = selectionAction.childrenActions.first { c -> c.text == "Load from file..." }
    assertThat(loadAction.isEnabled).isTrue()
    assertThat(loadAction.childrenActionCount).isEqualTo(0)
    var deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(1)
    assertThat(deviceAction1.childrenActions[0].text).isEqualTo(SessionsView.NO_DEBUGGABLE_PROCESSES)
    assertThat(deviceAction1.childrenActions[0].isEnabled).isFalse()

    myProfilerService.addProcess(device1, process1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myProfilers.process = process1
    assertThat(selectionAction.childrenActionCount).isEqualTo(3)
    deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(1)
    var processAction1 = deviceAction1.childrenActions.first { c -> c.text == "Process1 (10)" }
    assertThat(processAction1.childrenActionCount).isEqualTo(0)

    myProfilerService.addProcess(device1, otherProcess1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(3)
    deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(3)  // process1 + separator + "other processes"
    processAction1 = deviceAction1.childrenActions.first { c -> c.text == "Process1 (10)" }
    assertThat(deviceAction1.childrenActions[1]).isInstanceOf(CommonAction.SeparatorAction::class.java)
    var processAction2 = deviceAction1.childrenActions
      .first { c -> c.text == "Other processes" }.childrenActions
      .first { c -> c.text == "Other1 (20)" }

    // Test the reverse case of having only "other" processes
    myProfilerService.addDevice(device2)
    myProfilerService.addProcess(device2, otherProcess2)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(4)
    deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(3)  // process1 + separator + "other processes"
    processAction1 = deviceAction1.childrenActions.first { c -> c.text == "Process1 (10)" }
    assertThat(deviceAction1.childrenActions[1]).isInstanceOf(CommonAction.SeparatorAction::class.java)
    processAction2 = deviceAction1.childrenActions
      .first { c -> c.text == "Other processes" }.childrenActions
      .first { c -> c.text == "Other1 (20)" }
    var deviceAction2 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer2 Model2" }
    assertThat(deviceAction2.isEnabled).isTrue()
    assertThat(deviceAction2.childrenActionCount).isEqualTo(1) // There should be no separator in this case.
    var processAction3 = deviceAction2.childrenActions
      .first { c -> c.text == "Other processes" }.childrenActions
      .first { c -> c.text == "Other2 (30)" }
  }

  @Test
  fun testProcessDropdownHideDeadDevicesAndProcesses() {
    val deadDevice = Common.Device.newBuilder()
      .setDeviceId(1).setManufacturer("Manufacturer1").setModel("Model1").setState(Common.Device.State.DISCONNECTED).build()
    val onlineDevice = Common.Device.newBuilder()
      .setDeviceId(2).setManufacturer("Manufacturer2").setModel("Model2").setState(Common.Device.State.ONLINE).build()
    val deadProcess1 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(1).setName("Process1").setState(Common.Process.State.DEAD).build()
    val aliveProcess1 = Common.Process.newBuilder()
      .setPid(20).setDeviceId(1).setName("Process2").setState(Common.Process.State.ALIVE).build()
    val deadProcess2 = Common.Process.newBuilder()
      .setPid(30).setDeviceId(2).setName("Process3").setState(Common.Process.State.DEAD).build()
    val aliveProcess2 = Common.Process.newBuilder()
      .setPid(40).setDeviceId(2).setName("Process4").setState(Common.Process.State.ALIVE).build()
    val deadProcess3 = Common.Process.newBuilder()
      .setPid(50).setDeviceId(2).setName("Dead").setState(Common.Process.State.DEAD).build()
    // Also test processes that can be grouped in the fly-out menu.
    myProfilers.setPreferredProcess("Manufacturer2 Model2", "Process4", null)

    myProfilerService.addDevice(deadDevice)
    myProfilerService.addDevice(onlineDevice)
    myProfilerService.addProcess(deadDevice, deadProcess1)
    myProfilerService.addProcess(deadDevice, aliveProcess1)
    myProfilerService.addProcess(onlineDevice, deadProcess2)
    myProfilerService.addProcess(onlineDevice, aliveProcess2)
    myProfilerService.addProcess(onlineDevice, deadProcess3)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    var selectionAction = mySessionsView.processSelectionAction
    assertThat(selectionAction.childrenActions.any { c -> c.text == "Manufacturer1 Model1" }).isFalse()
    val aliveDeviceAction = selectionAction.childrenActions.first { c -> c.text == "Manufacturer2 Model2" }
    assertThat(aliveDeviceAction.childrenActionCount).isEqualTo(1)
    var processAction1 = aliveDeviceAction.childrenActions.first { c -> c.text == "Process4 (40)" }
    assertThat(processAction1.childrenActionCount).isEqualTo(0)
  }

  @Test
  fun testDropdownActionsTriggerProcessChange() {
    val device1 = Common.Device.newBuilder()
      .setDeviceId(1).setManufacturer("Manufacturer1").setModel("Model1").setState(Common.Device.State.ONLINE).build()
    val device2 = Common.Device.newBuilder()
      .setDeviceId(2).setManufacturer("Manufacturer2").setModel("Model2").setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(1).setName("Process1").setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder()
      .setPid(20).setDeviceId(1).setName("Process2").setState(Common.Process.State.ALIVE).build()
    val process3 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(2).setName("Process3").setState(Common.Process.State.ALIVE).build()
    // Mark all process as preferred processes as we are not testing the other processes flyout here.
    myProfilers.setPreferredProcess(null, "Process", null)

    myProfilerService.addDevice(device1)
    myProfilerService.addProcess(device1, process1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    myProfilerService.addProcess(device1, process2)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    var selectionAction = mySessionsView.processSelectionAction
    var processAction2 = selectionAction.childrenActions
      .first { c -> c.text == "Manufacturer1 Model1" }.childrenActions
      .first { c -> c.text == "Process2 (20)" }
    processAction2.actionPerformed(ActionEvent(processAction2, 0, ""))
    assertThat(myProfilers.device).isEqualTo(device1)
    assertThat(myProfilers.process).isEqualTo(process2)

    myProfilerService.addDevice(device2)
    myProfilerService.addProcess(device2, process3)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    var processAction3 = selectionAction.childrenActions
      .first { c -> c.text == "Manufacturer2 Model2" }.childrenActions
      .first { c -> c.text == "Process3 (10)" }
    processAction3.actionPerformed(ActionEvent(processAction3, 0, ""))
    assertThat(myProfilers.device).isEqualTo(device2)
    assertThat(myProfilers.process).isEqualTo(process3)
  }

  @Test
  fun testStopProfiling() {
    val device1 = Common.Device.newBuilder()
      .setDeviceId(1).setManufacturer("Manufacturer1").setModel("Model1").setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(1).setName("Process1").setState(Common.Process.State.ALIVE).build()

    val stopProfilingButton = mySessionsView.stopProfilingButton
    assertThat(stopProfilingButton.isEnabled).isFalse()

    myProfilerService.addDevice(device1)
    myProfilerService.addProcess(device1, process1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myProfilers.device = device1
    myProfilers.process = process1

    val session = myProfilers.session
    assertThat(stopProfilingButton.isEnabled).isTrue()
    assertThat(mySessionsManager.profilingSession).isNotEqualTo(Common.Session.getDefaultInstance())
    assertThat(mySessionsManager.profilingSession).isEqualTo(session)

    stopProfilingButton.doClick()
    assertThat(stopProfilingButton.isEnabled).isFalse()
    assertThat(myProfilers.device).isNull()
    assertThat(myProfilers.process).isNull()
    assertThat(mySessionsManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myProfilers.session.sessionId).isEqualTo(session.sessionId)
  }

  @Test
  fun testImportSessionsFromHprofFile() {
    val sessionsPanel = mySessionsView.sessionsPanel
    assertThat(sessionsPanel.componentCount).isEqualTo(0)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    mySessionsManager.beginSession(device, process1)
    val session1 = mySessionsManager.selectedSession
    assertThat(sessionsPanel.componentCount).isEqualTo(1)
    assertThat((sessionsPanel.getComponent(0) as SessionItemView).artifact.session).isEqualTo(session1)

    val session = mySessionsManager.createImportedSession("fake.hprof", Common.SessionMetaData.SessionType.MEMORY_CAPTURE, 0, 0, 0)
    mySessionsManager.update()
    mySessionsManager.setSession(session)
    assertThat(sessionsPanel.componentCount).isEqualTo(2)

    val selectedSession = mySessionsManager.selectedSession
    assertThat(session).isEqualTo(selectedSession)
    assertThat(myProfilers.sessionsManager.selectedSessionMetaData.type).isEqualTo(Common.SessionMetaData.SessionType.MEMORY_CAPTURE)
  }

  @Test
  fun testSessionItemSelection() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)
    val ui = FakeUi(sessionsPanel)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()
    val heapDumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(10).setEndTime(11).build()
    val cpuTraceInfo = CpuProfiler.TraceInfo.newBuilder().setFromTimestamp(20).setToTimestamp(21).build()
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo)
    myCpuService.addTraceInfo(cpuTraceInfo)

    myProfilerService.setTimestampNs(1)
    mySessionsManager.beginSession(device, process1)
    mySessionsManager.endCurrentSession()
    val session1 = mySessionsManager.selectedSession
    myProfilerService.setTimestampNs(2)
    mySessionsManager.beginSession(device, process2)
    mySessionsManager.endCurrentSession()
    val session2 = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(6)
    // Sessions are sorted in descending order.
    var sessionItem0 = sessionsPanel.getComponent(0) as SessionItemView
    val cpuCaptureItem0 = sessionsPanel.getComponent(1) as CpuCaptureArtifactView
    val hprofItem0 = sessionsPanel.getComponent(2) as HprofArtifactView
    var sessionItem1 = sessionsPanel.getComponent(3) as SessionItemView
    var cpuCaptureItem1 = sessionsPanel.getComponent(4) as CpuCaptureArtifactView
    var hprofItem1 = sessionsPanel.getComponent(5) as HprofArtifactView
    assertThat(sessionItem0.artifact.session).isEqualTo(session2)
    assertThat(hprofItem0.artifact.session).isEqualTo(session2)
    assertThat(cpuCaptureItem0.artifact.session).isEqualTo(session2)
    assertThat(sessionItem1.artifact.session).isEqualTo(session1)
    assertThat(hprofItem1.artifact.session).isEqualTo(session1)
    assertThat(cpuCaptureItem1.artifact.session).isEqualTo(session1)

    // Selecting on the second item should select the session.
    assertThat(mySessionsManager.selectedSession).isEqualTo(session2)
    ui.layout()
    ui.mouse.click(sessionItem1.bounds.x + 1, sessionItem1.bounds.y + 1)
    assertThat(mySessionsManager.selectedSession).isEqualTo(session1)
  }

  @Test
  fun testCpuCaptureItemSelection() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)
    val ui = FakeUi(sessionsPanel)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val traceInfoId = 13

    val cpuTraceInfo = CpuProfiler.TraceInfo.newBuilder()
      .setTraceId(traceInfoId)
      .setFromTimestamp(TimeUnit.MINUTES.toNanos(1))
      .setToTimestamp(TimeUnit.MINUTES.toNanos(2))
      .setProfilerType(CpuProfiler.CpuProfilerType.SIMPLEPERF)
      .build()
    myCpuService.addTraceInfo(cpuTraceInfo)

    myProfilerService.setTimestampNs(0)
    mySessionsManager.beginSession(device, process)
    mySessionsManager.endCurrentSession()
    val session = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    val sessionItem = sessionsPanel.getComponent(0) as SessionItemView
    val cpuCaptureItem = sessionsPanel.getComponent(1) as CpuCaptureArtifactView
    assertThat(sessionItem.artifact.session).isEqualTo(session)
    assertThat(cpuCaptureItem.artifact.session).isEqualTo(session)
    assertThat(cpuCaptureItem.artifact.isOngoingCapture).isFalse()
    assertThat(cpuCaptureItem.artifact.name).isEqualTo(ProfilingConfiguration.SIMPLEPERF_ARTIFACT)
    assertThat(cpuCaptureItem.artifact.subtitle).isEqualTo("00:01:00.000")

    // Prepare FakeCpuService to return a valid trace.
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS)
    myCpuService.setValidTrace(true)
    val traceBytes = ByteString.copyFrom(TestUtils.getWorkspaceFile(VALID_TRACE_PATH).readBytes())
    myCpuService.setTrace(traceBytes)

    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java) // Makes sure we're in monitor stage
    // Selecting the CpuCaptureSessionArtifact should open CPU profiler and select the capture
    ui.layout()
    ui.mouse.click(cpuCaptureItem.bounds.x + 1, cpuCaptureItem.bounds.y + 1)
    assertThat(myProfilers.stage).isInstanceOf(CpuProfilerStage::class.java) // Makes sure CPU profiler stage is now open
    val selectedCapture = (myProfilers.stage as CpuProfilerStage).capture
    // Makes sure that there is a capture selected and it's the one we clicked.
    assertThat(selectedCapture).isNotNull()
    assertThat(selectedCapture!!.traceId).isEqualTo(traceInfoId)
    assertThat(myProfilers.timeline.isStreaming).isFalse()
  }

  @Test
  fun testCpuOngoingCaptureItemSelection() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)
    val ui = FakeUi(sessionsPanel)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val sessionStartNs = 1L

    // Sets an ongoing profiling configuration in the service
    val configuration = CpuProfiler.CpuProfilerConfiguration.newBuilder().setProfilerType(CpuProfiler.CpuProfilerType.ATRACE).build()
    myCpuService.setOngoingCaptureConfiguration(configuration, sessionStartNs + 1)

    myProfilerService.setTimestampNs(sessionStartNs)
    mySessionsManager.beginSession(device, process)
    val session = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    val sessionItem = sessionsPanel.getComponent(0) as SessionItemView
    val cpuCaptureItem = sessionsPanel.getComponent(1) as CpuCaptureArtifactView
    assertThat(sessionItem.artifact.session).isEqualTo(session)
    assertThat(cpuCaptureItem.artifact.session).isEqualTo(session)
    assertThat(cpuCaptureItem.artifact.isOngoingCapture).isTrue()
    assertThat(cpuCaptureItem.artifact.name).isEqualTo(ProfilingConfiguration.ATRACE)
    assertThat(cpuCaptureItem.artifact.subtitle).isEqualTo(SessionArtifact.CAPTURING_SUBTITLE)

    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java) // Makes sure we're in monitor stage
    // Selecting on the CpuCaptureSessionArtifact should open CPU profiler and select the capture
    ui.layout()
    ui.mouse.click(cpuCaptureItem.bounds.x + 1, cpuCaptureItem.bounds.y + 1)
    assertThat(myProfilers.stage).isInstanceOf(CpuProfilerStage::class.java) // Makes sure CPU profiler stage is now open
    val selectedCapture = (myProfilers.stage as CpuProfilerStage).capture
    // Makes sure that there is no capture selected, because the ongoing capture was not generated by a trace just yet.
    assertThat(selectedCapture).isNull()
    assertThat(myProfilers.timeline.isStreaming).isTrue()
  }

  @Test
  fun testMemoryHeapDumpSelection() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)
    val ui = FakeUi(sessionsPanel)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()

    val heapDumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(10).setEndTime(11).build()
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo)

    myProfilerService.setTimestampNs(1)
    mySessionsManager.beginSession(device, process)
    mySessionsManager.endCurrentSession()
    val session = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    var sessionItem = sessionsPanel.getComponent(0) as SessionItemView
    var hprofItem = sessionsPanel.getComponent(1) as HprofArtifactView
    assertThat(sessionItem.artifact.session).isEqualTo(session)
    assertThat(hprofItem.artifact.session).isEqualTo(session)

    myMemoryService.setExplicitSnapshotBuffer(ByteArray(0))
    myMemoryService.setExplicitHeapDumpStatus(MemoryProfiler.TriggerHeapDumpResponse.Status.SUCCESS)
    myMemoryService.setExplicitDumpDataStatus(MemoryProfiler.DumpDataResponse.Status.SUCCESS)

    // Makes sure we're in monitor stage.
    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java)
    // Selecting on the HprofSessionArtifact should open Memory profiler and select the capture.
    ui.layout()
    ui.mouse.click(hprofItem.bounds.x + 1, hprofItem.bounds.y + 1)
    // Makes sure memory profiler stage is now open.
    assertThat(myProfilers.stage).isInstanceOf(MemoryProfilerStage::class.java)
    // Makes sure a HeapDumpCaptureObject is loaded.
    assertThat((myProfilers.stage as MemoryProfilerStage).selectedCapture).isInstanceOf(HeapDumpCaptureObject::class.java)
  }

  @Test
  fun testMemoryOngoingHeapDumpItemSelection() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)
    val ui = FakeUi(sessionsPanel)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()

    val heapDumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(10).setEndTime(Long.MAX_VALUE).build()
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo)

    mySessionsManager.beginSession(device, process)
    val session = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    var sessionItem = sessionsPanel.getComponent(0) as SessionItemView
    var hprofItem = sessionsPanel.getComponent(1) as HprofArtifactView
    assertThat(sessionItem.artifact.session).isEqualTo(session)
    assertThat(hprofItem.artifact.session).isEqualTo(session)

    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java) // Makes sure we're in monitor stage
    // Selecting on the HprofSessionArtifact should open Memory profiler.
    ui.layout()
    ui.mouse.click(hprofItem.bounds.x + 1, hprofItem.bounds.y + 1)
    // Makes sure memory profiler stage is now open.
    assertThat(myProfilers.stage).isInstanceOf(MemoryProfilerStage::class.java)
    // Makes sure that there is no capture selected.
    assertThat((myProfilers.stage as MemoryProfilerStage).selectedCapture).isNull()
    assertThat(myProfilers.timeline.isStreaming).isTrue()
  }

  @Test
  fun testMemoryLegacyAllocationsSelection() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)
    val ui = FakeUi(sessionsPanel)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()

    var allocationInfo = MemoryProfiler.MemoryData.newBuilder()
      .addAllocationsInfo(MemoryProfiler.AllocationsInfo.newBuilder().setStartTime(10).setEndTime(11).setLegacy(true).build())
      .build()
    myMemoryService.setMemoryData(allocationInfo)

    mySessionsManager.beginSession(device, process)
    mySessionsManager.endCurrentSession()
    val session = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    var sessionItem = sessionsPanel.getComponent(0) as SessionItemView
    var allocationItem = sessionsPanel.getComponent(1) as LegacyAllocationsArtifactView
    assertThat(sessionItem.artifact.session).isEqualTo(session)
    assertThat(allocationItem.artifact.session).isEqualTo(session)

    // Makes sure we're in monitor stage.
    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java)
    // Selecting on the HprofSessionArtifact should open Memory profiler and select the capture.
    ui.layout()
    ui.mouse.click(allocationItem.bounds.x + 1, allocationItem.bounds.y + 1)
    // Makes sure memory profiler stage is now open.
    assertThat(myProfilers.stage).isInstanceOf(MemoryProfilerStage::class.java)
    // Makes sure a HeapDumpCaptureObject is loaded.
    assertThat((myProfilers.stage as MemoryProfilerStage).selectedCapture).isInstanceOf(LegacyAllocationCaptureObject::class.java)
  }

  @Test
  fun testMemoryOngoingLegacyAllocationsSelection() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)
    val ui = FakeUi(sessionsPanel)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()

    var allocationInfo = MemoryProfiler.MemoryData.newBuilder()
      .addAllocationsInfo(MemoryProfiler.AllocationsInfo.newBuilder().setStartTime(10).setEndTime(Long.MAX_VALUE).setLegacy(true).build())
      .build()
    myMemoryService.setMemoryData(allocationInfo)

    mySessionsManager.beginSession(device, process)
    val session = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    var sessionItem = sessionsPanel.getComponent(0) as SessionItemView
    var allocationItem = sessionsPanel.getComponent(1) as LegacyAllocationsArtifactView
    assertThat(sessionItem.artifact.session).isEqualTo(session)
    assertThat(allocationItem.artifact.session).isEqualTo(session)

    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java) // Makes sure we're in monitor stage
    // Selecting on the HprofSessionArtifact should open Memory profiler.
    ui.layout()
    ui.mouse.click(allocationItem.bounds.x + 1, allocationItem.bounds.y + 1)
    // Makes sure memory profiler stage is now open.
    assertThat(myProfilers.stage).isInstanceOf(MemoryProfilerStage::class.java)
    // Makes sure that there is no capture selected.
    assertThat((myProfilers.stage as MemoryProfilerStage).selectedCapture).isNull()
    assertThat(myProfilers.timeline.isStreaming).isTrue()
  }
}