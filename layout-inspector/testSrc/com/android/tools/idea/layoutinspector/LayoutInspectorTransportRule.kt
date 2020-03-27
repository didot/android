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
package com.android.tools.idea.layoutinspector

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.ClientData
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.FeaturesHandler
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.FpsTimer
import com.android.tools.idea.layoutinspector.legacydevice.LegacyClient
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.transport.DefaultInspectorClient
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.idea.layoutinspector.util.ConfigurationBuilder
import com.android.tools.idea.layoutinspector.util.DemoExample
import com.android.tools.idea.layoutinspector.util.TestStringTable
import com.android.tools.idea.layoutinspector.util.TreeBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.AgentData.Status.ATTACHED
import com.android.tools.profiler.proto.Common.AgentData.Status.UNATTACHABLE
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

val DEFAULT_PROCESS = Common.Process.newBuilder().apply {
  name = "myProcess"
  pid = 12345
  deviceId = 123456
  state = Common.Process.State.ALIVE
}.build()!!

val DEFAULT_DEVICE = Common.Device.newBuilder().apply {
  deviceId = 123456
  model = "My Model"
  manufacturer = "Google"
  serial = "123456"
  featureLevel = 29
  state = Common.Device.State.ONLINE
}.build()!!

val LEGACY_DEVICE = Common.Device.newBuilder().apply {
  deviceId = 123488
  model = "My Legacy Model"
  manufacturer = "Google"
  serial = "123488"
  apiLevel = 27
  state = Common.Device.State.ONLINE
}.build()!!

val DEFAULT_STREAM = Common.Stream.newBuilder().apply {
  device = DEFAULT_DEVICE
  streamId = 123456
  type = Common.Stream.Type.DEVICE
}.build()!!

/**
 * Rule providing mechanisms for testing the layout inspector. Notably, users of this rule should use [advanceTime] instead of using [timer]
 * or calling [com.android.tools.idea.transport.poller.TransportEventPoller.poll()] directly.
 *
 * Any passed-in objects shouldn't be registered as [org.junit.Rule]s by the caller: [LayoutInspectorTransportRule] will call them as
 * needed.
 */
class LayoutInspectorTransportRule(
  private val timer: FakeTimer = FakeTimer(),
  private val adbRule: FakeAdbRule = FakeAdbRule(),
  private val transportService: FakeTransportService = FakeTransportService(timer),
  private val grpcServer: FakeGrpcServer =
    FakeGrpcServer.createFakeGrpcServer("LayoutInspectorTestChannel", transportService, transportService),
  private val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
) : TestRule {

  lateinit var inspector: LayoutInspector
  lateinit var inspectorClient: InspectorClient
  lateinit var inspectorModel: InspectorModel
  val project: Project get() = projectRule.project

  /** If you set this to false before attaching a device, the attach will fail (return [UNATTACHABLE]) */
  var shouldConnectSuccessfully = true

  /**
   * By default we get an empty model.
   *
   * If a connected default device is requested, this initial root will be setup instead.
   */
  var initialRoot = view(0L)

  // "2" since it's called for debug_view_attributes and debug_view_attributes_application_package
  private val unsetSettingsLatch = CountDownLatch(2)
  private val scheduler = VirtualTimeScheduler()
  private var inspectorClientFactory: () -> InspectorClient = {
    DefaultInspectorClient(inspectorModel, projectRule.fixture.projectDisposable, grpcServer.name, scheduler)
  }
  private val commandHandlers = mutableMapOf<
    LayoutInspectorProto.LayoutInspectorCommand.Type,
    (Commands.Command, MutableList<Common.Event>) -> Unit>()

  private var attachHandler: CommandHandler = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      if (command.type == Commands.Command.CommandType.ATTACH_AGENT) {
        events.add(
          Common.Event.newBuilder().apply {
            pid = command.pid
            kind = Common.Event.Kind.AGENT
            agentData = Common.AgentData.newBuilder().setStatus(if (shouldConnectSuccessfully) ATTACHED else UNATTACHABLE).build()
          }.build()
        )
      }
    }
  }

  private val startedLatch = CountDownLatch(1)
  private val startHandler : CommandHandler = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      if (command.layoutInspector.type == LayoutInspectorProto.LayoutInspectorCommand.Type.START) {
        startedLatch.countDown()
      }
    }
  }

  private var inspectorHandler: CommandHandler = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      val handler = commandHandlers[command.layoutInspector.type]
      handler?.invoke(command, events) ?: startHandler.handleCommand(command, events)
    }
  }

  private val initialActions = mutableListOf<() -> Unit>()
  private val beforeActions = mutableListOf<() -> Unit>()

  init {
    adbRule.withDeviceCommandHandler(JdwpCommandHandler().addPacketHandler(
      FeaturesHandler.CHUNK_TYPE, FeaturesHandler(emptyMap(), listOf(ClientData.FEATURE_VIEW_HIERARCHY))))
    adbRule.withDeviceCommandHandler(object : DeviceCommandHandler("shell") {
      override fun accept(server: FakeAdbServer, socket: Socket, device: DeviceState, command: String, args: String): Boolean {
        when (args) {
          "settings put global debug_view_attributes 1",
          "settings put global debug_view_attributes_application_package com.example",
          "settings delete global debug_view_attributes",
          "settings delete global debug_view_attributes_application_package" -> {
            com.android.fakeadbserver.CommandHandler.writeOkay(socket.getOutputStream())
            if (args.startsWith("settings delete")) {
              unsetSettingsLatch.countDown()
            }
            return true
          }

          else -> return false
        }
      }
    })

    scheduler.scheduleAtFixedRate({ timer.step() }, 0, FpsTimer.ONE_FRAME_IN_NS, TimeUnit.NANOSECONDS)
  }

  /**
   * Create a [LegacyClient] rather than a [DefaultInspectorClient]
   */
  fun withLegacyClient() = apply { inspectorClientFactory = { LegacyClient(projectRule.fixture.projectDisposable) } }

  /**
   * The default attach handler just attaches (or fails if [shouldConnectSuccessfully] is false). Use this if you want to do something else.
   */
  fun withAttachHandler(handler: CommandHandler) = apply { attachHandler = handler }

  /**
   * Add a specific [LayoutInspectorProto.LayoutInspectorCommand] handler.
   */
  fun withCommandHandler(type: LayoutInspectorProto.LayoutInspectorCommand.Type,
                         handler: (Commands.Command, MutableList<Common.Event>) -> Unit) =
    apply { commandHandlers[type] = handler }

  fun withDefaultDevice(connected: Boolean) = apply {
    beforeActions.add {
      if (inspectorClient is DefaultInspectorClient) {
        addProcess(DEFAULT_DEVICE, DEFAULT_PROCESS)
      }
      else if (inspectorClient is LegacyClient) {
        addProcess(LEGACY_DEVICE, DEFAULT_PROCESS)
      }
    }
    if (connected) {
        beforeActions.add {
          inspectorClient.attach(DEFAULT_STREAM, DEFAULT_PROCESS)
          if (inspectorClient is DefaultInspectorClient) {
            advanceTime(1100, TimeUnit.MILLISECONDS)
            waitForStart()
            transportService.addEventToStream(DEFAULT_STREAM.streamId, createComponentTreeEvent(initialRoot))
            advanceTime(1100, TimeUnit.MILLISECONDS)
          }
        }
    }
  }

  /**
   * Add the demo layout from [DemoExample] and include views if the connected option is chosen.
   */
  fun withDemoLayout() = apply {
    initialActions.add {
      initialRoot = model(project, DemoExample.setUpDemo(projectRule.fixture)).root
    }
  }

  /**
   * Advance the virtual time of the test. This will cause the [transportService] poller to fire, and will also advance [timer].
   */
  fun advanceTime(interval: Long, unit: TimeUnit) {
    scheduler.advanceBy(interval, unit)
  }

  /**
   * Wait until the device receives [LayoutInspectorProto.LayoutInspectorCommand.Type.START] (necessary if the request is made on a worker
   * thread).
   */
  fun waitForStart() {
    if (!shouldConnectSuccessfully) {
      return
    }
    assertThat(startedLatch.await(30, TimeUnit.SECONDS)).isTrue()
  }

  /**
   * Add the given process and stream to the transport service.
   */
  fun addProcess(device: Common.Device, process: Common.Process) {
    val deviceState = adbRule.attachDevice(device.deviceId.toString(), device.manufacturer, device.model, device.version,
                                           device.apiLevel.toString(), DeviceState.HostConnectionType.USB)
    val uid = System.currentTimeMillis().toInt()
    deviceState.startClient(process.pid, uid, process.name, "${process.name}.com.example.myapplication", true)
    waitUntilProcessIsAvailable(device, process)
    if (device.featureLevel >= 29) {
      transportService.addDevice(device)
      transportService.addProcess(device, process)
    }
  }

  private fun waitUntilProcessIsAvailable(device: Common.Device, process: Common.Process) {
    var times = 20
    while (!isProcessAvailable(device, process)) {
      Thread.sleep(100)
      if (--times <= 0) {
        error("Timeout waiting for process to be available")
      }
    }
  }

  private fun isProcessAvailable(device: Common.Device, process: Common.Process): Boolean {
    val bridge = AndroidDebugBridge.getBridge()!!
    val iDevice = bridge.devices.first { it.serialNumber == device.serial } ?: return false
    return iDevice.clients.any { it.clientData.pid == process.pid && it.clientData.hasFeature(ClientData.FEATURE_VIEW_HIERARCHY) }
  }

  override fun apply(base: Statement, description: Description): Statement {
    return grpcServer.apply(projectRule.apply(adbRule.apply(//disposableRule.apply(
      object: Statement() {
        override fun evaluate() {
          before()
          try {
            base.evaluate()
          }
          finally {
            after()
          }
        }
      }, description
    ), description), description)//, description)
  }

  private fun before() {
    initialActions.forEach { it() }
    inspectorModel = InspectorModel(project)
    inspectorClientFactory.let {
      inspectorClient = it()
      InspectorClient.clientFactory = { _, _ -> inspectorClient }
    }
    inspector = LayoutInspector(inspectorModel, project)
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, attachHandler)
    transportService.setCommandHandler(Commands.Command.CommandType.LAYOUT_INSPECTOR, inspectorHandler)
    beforeActions.forEach { it() }
  }

  private fun after() {
    if (inspectorClient.isConnected) {
      val processDone = CountDownLatch(1)
      inspectorClient.registerProcessChanged { processDone.countDown() }
      inspectorClient.disconnect()
      assertThat(processDone.await(30, TimeUnit.SECONDS)).isTrue()
      if (inspectorClient is DefaultInspectorClient) {
        waitForUnsetSettings()
      }
    }
  }

  private fun waitForUnsetSettings() {
    assertThat(unsetSettingsLatch.await(30, TimeUnit.SECONDS)).isTrue()
  }

  private fun createComponentTreeEvent(rootView: ViewNode): Common.Event {
    val strings = TestStringTable()
    val tree = TreeBuilder(strings)
    val config = ConfigurationBuilder(strings)
    return Common.Event.newBuilder().apply {
      kind = Common.Event.Kind.LAYOUT_INSPECTOR
      pid = DEFAULT_PROCESS.pid
      groupId = Common.Event.EventGroupIds.COMPONENT_TREE.number.toLong()
      layoutInspectorEventBuilder.treeBuilder.apply {
        root = tree.makeViewTree(rootView)
        resources = config.makeDummyConfiguration(project)
        addAllString(strings.asEntryList())
        addAllWindowIds(rootView.drawId)
      }
    }.build()
  }
}