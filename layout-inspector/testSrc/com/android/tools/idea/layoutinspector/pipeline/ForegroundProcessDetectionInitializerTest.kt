/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.TransportProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.toDeviceDescriptor
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.layoutinspector.metrics.ForegroundProcessDetectionMetrics
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.replaceService
import com.intellij.util.concurrency.SameThreadExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import layout_inspector.LayoutInspector
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ForegroundProcessDetectionInitializerTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  @get:Rule
  val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("ForegroundProcessDetectionInitializerTest", transportService)

  private val device1 = FakeDevice(serial = "1")
  private val device2 = FakeDevice(serial = "2")

  private lateinit var processModel: ProcessesModel
  private lateinit var deviceModel: DeviceModel

  private lateinit var fakeStream1: Common.Stream
  private lateinit var fakeStream2: Common.Stream

  private lateinit var fakeProcess1: ProcessDescriptor
  private lateinit var fakeProcess2: ProcessDescriptor
  private lateinit var fakeProcess3: ProcessDescriptor

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().initAndroid(false)

  @Before
  fun setup() {
    ApplicationManager.getApplication().replaceService(TransportService::class.java, mock(), projectRule.testRootDisposable)

    val testProcessDiscovery = TestProcessDiscovery()
    processModel = ProcessesModel(testProcessDiscovery)
    deviceModel = DeviceModel(processModel)

    fakeStream1 = createFakeStream(1, device1)
    fakeStream2 = createFakeStream(2, device2)
    fakeProcess1 = fakeStream1.createFakeProcess("process1", 1)
    fakeProcess2 = fakeStream2.createFakeProcess("process2", 2)
    fakeProcess3 = fakeStream2.createFakeProcess("process3", 3)
    testProcessDiscovery.fireConnected(fakeProcess1)
    testProcessDiscovery.fireConnected(fakeProcess2)

    testProcessDiscovery.addDevice(fakeStream1.device.toDeviceDescriptor())
    testProcessDiscovery.addDevice(fakeStream2.device.toDeviceDescriptor())
  }

  @Test
  fun testNewForegroundProcessSetsSelectedProcess() {
    val foregroundProcessListener = ForegroundProcessDetectionInitializer.getDefaultForegroundProcessListener(processModel)
    ForegroundProcessDetectionInitializer.initialize(
      processModel = processModel,
      deviceModel = deviceModel,
      coroutineScope = CoroutineScope(SameThreadExecutor.INSTANCE.asCoroutineDispatcher()),
      foregroundProcessListener = foregroundProcessListener,
      metrics = ForegroundProcessDetectionMetrics(LayoutInspectorMetrics(projectRule.project)),
    )

    foregroundProcessListener.onNewProcess(device1, ForegroundProcess(1, "process1"))
    assertThat(processModel.selectedProcess).isEqualTo(fakeProcess1)

    foregroundProcessListener.onNewProcess(device1, ForegroundProcess(2, "process2"))
    assertThat(processModel.selectedProcess).isEqualTo(fakeProcess2)
  }

  @Test
  fun testStartPollingOnDeviceWhenProcessIsSelectedFromOutside() {
    val transportClient = TransportClient(grpcServerRule.name)
    val latch1 = CountDownLatch(1)
    val latch2 = CountDownLatch(1)

    val handshakeLatch1 = CountDownLatch(1)
    val handshakeLatch2 = CountDownLatch(1)

    val observedConnectedStreamIds = mutableListOf<Long>()
    val observedDisconnectedStreamIds = mutableListOf<Long>()

    transportService.setCommandHandler(Commands.Command.CommandType.IS_TRACKING_FOREGROUND_PROCESS_SUPPORTED) { command ->
      val event = Common.Event.newBuilder()
        .setKind(Common.Event.Kind.LAYOUT_INSPECTOR_TRACKING_FOREGROUND_PROCESS_SUPPORTED)
        .setLayoutInspectorTrackingForegroundProcessSupported(
          Common.Event.newBuilder().layoutInspectorTrackingForegroundProcessSupportedBuilder
            .setSupportType(LayoutInspector.TrackingForegroundProcessSupported.SupportType.SUPPORTED).build()
        )
        .build()

      transportService.addEventToStream(command.streamId, event)

      when (command.streamId) {
        fakeStream1.streamId -> handshakeLatch1.countDown()
        fakeStream2.streamId -> handshakeLatch2.countDown()
        else -> throw RuntimeException("Received handshake command from unexpected stream.")
      }
    }
    transportService.setCommandHandler(Commands.Command.CommandType.START_TRACKING_FOREGROUND_PROCESS) { command ->
      observedConnectedStreamIds.add(command.streamId)
      when (command.streamId) {
        fakeStream1.streamId -> latch1.countDown()
        fakeStream2.streamId -> latch2.countDown()
        else -> throw RuntimeException("Received start command from unexpected stream.")
      }
    }
    transportService.setCommandHandler(Commands.Command.CommandType.STOP_TRACKING_FOREGROUND_PROCESS) { command ->
      observedDisconnectedStreamIds.add(command.streamId)
    }

    connectStream(fakeStream1)

    val foregroundProcessDetection = ForegroundProcessDetectionInitializer.initialize(
      processModel = processModel,
      deviceModel = deviceModel,
      coroutineScope = projectRule.project.coroutineScope,
      transportClient =  transportClient,
      metrics = ForegroundProcessDetectionMetrics(LayoutInspectorMetrics(projectRule.project)),
    )

    handshakeLatch1.await(5, TimeUnit.SECONDS)

    foregroundProcessDetection.startListeningForEvents()

    latch1.await(5, TimeUnit.SECONDS)

    assertThat(observedConnectedStreamIds).containsExactly(fakeStream1.streamId)
    assertThat(observedDisconnectedStreamIds).isEmpty()

    connectStream(fakeStream2)

    handshakeLatch2.await(5, TimeUnit.SECONDS)

    // setting process from outside ForegroundProcessDetection should start polling on the process's device
    processModel.selectedProcess = fakeProcess2

    latch2.await(5, TimeUnit.SECONDS)

    assertThat(observedConnectedStreamIds).containsExactly(fakeStream1.streamId, fakeStream2.streamId)
    assertThat(observedDisconnectedStreamIds).containsExactly(fakeStream1.streamId)
  }

  private fun Common.Stream.createFakeProcess(name: String? = null, pid: Int = 0): ProcessDescriptor {
    return TransportProcessDescriptor(this, FakeTransportService.FAKE_PROCESS.toBuilder()
      .setDeviceId(streamId)
      .setName(name ?: FakeTransportService.FAKE_PROCESS_NAME)
      .setPid(pid)
      .build())
  }

  private fun createFakeStream(streamId: Long, fakeDevice: FakeDevice): Common.Stream {
    return Common.Stream.newBuilder()
      .setStreamId(streamId)
      .setDevice(fakeDevice.toTransport(streamId))
      .build()
  }

  private fun FakeTransportService.setCommandHandler(command: Commands.Command.CommandType, block: (Commands.Command) -> Unit) {
    setCommandHandler(command, object : CommandHandler(timer) {
      override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
        block.invoke(command)
      }
    })
  }

  private fun connectStream(stream: Common.Stream) {
    transportService.addDevice(stream.device)
  }

  private fun sendEvent(stream: Common.Stream, event: Common.Event) {
    transportService.addEventToStream(stream.streamId, event)
  }

  private fun createStreamConnectedEvent(stream: Common.Stream): Common.Event {
    val eventBuilder = Common.Event.newBuilder()
    return eventBuilder
      .setKind(Common.Event.Kind.STREAM)
      .setTimestamp(1)
      .setGroupId(stream.streamId)
      .setStream(
        eventBuilder.streamBuilder.setStreamConnected(
          eventBuilder.streamBuilder.streamConnectedBuilder
            .setStream(stream)
        )
      ).build()
  }

  private fun FakeDevice.toTransport(id: Long): Common.Device {
    return Common.Device.newBuilder()
      .setDeviceId(id)
      .setSerial(serial)
      .setApiLevel(apiLevel)
      .setFeatureLevel(apiLevel)
      .setModel(model)
      .setCpuAbi("arm64-v8a")
      .setState(Common.Device.State.ONLINE)
      .build()
  }

  private data class FakeDevice(override val manufacturer: String = "manufacturer",
                                override val model: String = "model",
                                override val serial: String = "serial",
                                override val isEmulator: Boolean = false,
                                override val apiLevel: Int = 1,
                                override val version: String = "version",
                                override val codename: String? = "codename") : DeviceDescriptor
}