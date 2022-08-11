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
package com.android.tools.idea.device

import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_RELEASE
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_SDK
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_CPU_ABI
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MANUFACTURER
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MODEL
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellV2Protocol
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.tools.idea.testingutils.FakeAdbServiceRule
import com.android.tools.idea.util.StudioPathManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.awt.Dimension
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime

/**
 * Allows tests to use [FakeEmulator] instead of the real one.
 */
internal class FakeScreenSharingAgentRule : TestRule {
  private var deviceCounter = 0
  private val devices = mutableListOf<FakeDevice>()
  private val projectRule = ProjectRule()
  private val fakeAdbRule: FakeAdbRule
  private val fakeAdbServiceRule: FakeAdbServiceRule
  private val testEnvironment = object : ExternalResource() {
    override fun before() {
      val binDir = Paths.get(StudioPathManager.getBinariesRoot())
      // Create fake screen-sharing-agent.jar and libscreen-sharing-agent.so files if they don't exist.
      createEmptyFileIfNotExists(binDir.resolve("$SCREEN_SHARING_AGENT_SOURCE_PATH/$SCREEN_SHARING_AGENT_JAR_NAME"))
      for (deviceAbi in listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")) {
        createEmptyFileIfNotExists(binDir.resolve("$SCREEN_SHARING_AGENT_SOURCE_PATH/native/$deviceAbi/$SCREEN_SHARING_AGENT_SO_NAME"))
      }
    }

    override fun after() {
      while (devices.isNotEmpty()) {
        disconnectDevice(devices.last())
      }
    }
  }

  @Suppress("UnstableApiUsage")
  val testRootDisposable: Disposable
    get() = projectRule.project.earlyDisposable

  val project: ProjectEx
    get() = projectRule.project

  init {
    fakeAdbRule = FakeAdbRule().apply {
      withDeviceCommandHandler(DeviceCommandHandler("sync"))
      withDeviceCommandHandler(object: DeviceCommandHandler("shell,v2") {
        override fun invoke(server: FakeAdbServer, socket: Socket, deviceState: DeviceState, args: String) {
          if (args.contains("$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_JAR_NAME")) {
            val device = devices.find { it.serialNumber == deviceState.deviceId }!!
            val shellProtocol = ShellV2Protocol(socket)
            writeOkay(socket.outputStream)
            runBlocking { device.agent.run(shellProtocol, args, device.hostPort!!) }
          }
          else {
            writeOkay(socket.outputStream)
            ShellV2Protocol(socket).writeExitCode(0)
          }
        }
      })
      withDeviceCommandHandler(object: DeviceCommandHandler("reverse") {
        override fun invoke(server: FakeAdbServer, socket: Socket, deviceState: DeviceState, args: String) {
          val device = devices.find { it.serialNumber == deviceState.deviceId }!!
          if (args.startsWith("forward:")) {
            val parts = args.split(';')
            val hostParts = parts[1].split(':')
            device.hostPort = hostParts[1].toInt()
          }
          else if (args.startsWith("killforward:")) {
            device.hostPort = null
          }
          val stream = socket.outputStream
          writeOkay(stream)
          writeOkay(stream)
        }
      })
    }

    fakeAdbServiceRule = FakeAdbServiceRule(projectRule::project, fakeAdbRule)
  }

  override fun apply(base: Statement, description: Description): Statement {
    return projectRule.apply(
        fakeAdbRule.apply(
            fakeAdbServiceRule.apply(
                testEnvironment.apply(base, description),
                description),
            description),
        description)
  }

  fun connectDevice(model: String,
                    apiLevel: Int,
                    displaySize: Dimension,
                    abi: String,
                    additionalDeviceProperties: Map<String, String> = emptyMap(),
                    manufacturer: String = "Google",
                    hostConnectionType: DeviceState.HostConnectionType = DeviceState.HostConnectionType.USB): FakeDevice {
    val serialNumber = (++deviceCounter).toString()
    val release = "Sweet dessert"
    val deviceState = fakeAdbRule.attachDevice(serialNumber, manufacturer, model, release, apiLevel.toString(), abi, hostConnectionType)
    val deviceProperties = mapOf(
      RO_BUILD_VERSION_RELEASE to release,
      RO_BUILD_VERSION_SDK to apiLevel.toString(),
      RO_PRODUCT_CPU_ABI to abi,
      RO_PRODUCT_MANUFACTURER to manufacturer,
      RO_PRODUCT_MODEL to model,
    )
    val device = FakeDevice(serialNumber, displaySize, deviceState, deviceProperties + additionalDeviceProperties)
    devices.add(device)
    return device
  }

  fun disconnectDevice(device: FakeDevice) {
    fakeAdbRule.disconnectDevice(device.serialNumber)
    Disposer.dispose(device.agent)
    devices.remove(device)
  }

  private fun createEmptyFileIfNotExists(file: Path) {
    if (Files.notExists(file)) {
      Files.createDirectories(file.parent)
      Files.createFile(file)
      // Set very old modified time to make sure that the file gets replaced by the build.
      Files.setLastModifiedTime(file, FileTime.fromMillis(0))
    }
  }

  class FakeDevice(
    val serialNumber: String,
    val displaySize: Dimension,
    val deviceState: DeviceState,
    val deviceProperties: Map<String, String>,
  ) {
    val agent: FakeScreenSharingAgent = FakeScreenSharingAgent(displaySize, deviceState)
    var hostPort: Int? = null
  }
}