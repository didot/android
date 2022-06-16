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
package com.android.tools.idea.device

import com.android.adblib.AdbDeviceServices
import com.android.adblib.DeviceSelector
import com.android.adblib.RemoteFileMode
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.SocketSpec
import com.android.adblib.shellV2AsLines
import com.android.adblib.syncSend
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.util.StudioPathManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import java.awt.Dimension
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission

internal const val SCREEN_SHARING_AGENT_JAR_NAME = "screen-sharing-agent.jar"
internal const val SCREEN_SHARING_AGENT_SO_NAME = "libscreen-sharing-agent.so"
internal const val SCREEN_SHARING_AGENT_SOURCE_PATH = "tools/adt/idea/emulator/screen-sharing-agent"
internal const val DEVICE_PATH_BASE = "/data/local/tmp/.studio"

internal class DeviceClient(
  disposableParent: Disposable,
  private val deviceSerialNumber: String,
  private val deviceAbi: String,
  private val project: Project
) : Disposable {

  private val coroutineScope = AndroidCoroutineScope(this)
  private lateinit var controlChannel: SuspendingSocketChannel
  private lateinit var videoChannel: SuspendingSocketChannel
  lateinit var deviceController: DeviceController
    private set
  internal var startTime = 0L
  internal var pushTime = 0L
  internal var startAgentTime = 0L
  internal var connectionTime = 0L
  private val logger = thisLogger()

  init {
    Disposer.register(disposableParent, this)
  }

  suspend fun startAgentAndConnect(initialDisplayOrientation: Int?) {
    startTime = System.currentTimeMillis()
    val adb = AdbLibService.getSession(project).deviceServices
    val deviceSelector = DeviceSelector.fromSerialNumber(deviceSerialNumber)
    val agentPushed = coroutineScope { async {
      pushAgent(deviceSelector, adb)
    }}
    pushTime = System.currentTimeMillis()
    val deviceSocket = SocketSpec.LocalAbstract("screen-sharing-agent")
    @Suppress("BlockingMethodInNonBlockingContext")
    val asyncChannel = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0))
    val port = (asyncChannel.localAddress as InetSocketAddress).port
    thisLogger().debug("Using port $port")
    SuspendingServerSocketChannel(asyncChannel).use { serverSocketChannel ->
      ClosableReverseForwarding(deviceSelector, deviceSocket, SocketSpec.Tcp(port), adb).use {
        it.startForwarding()
        agentPushed.await()
        startAgent(deviceSelector, adb, initialDisplayOrientation)
        videoChannel = serverSocketChannel.accept()
        connectionTime = System.currentTimeMillis()
        controlChannel = serverSocketChannel.accept()
        controlChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
        // Port forwarding can be removed since the already established connections will continue to work without it.
      }
    }
    deviceController = DeviceController(this, controlChannel)
  }

  fun createVideoDecoder(maxOutputSize: Dimension) : VideoDecoder =
      VideoDecoder(videoChannel, maxOutputSize)

  /**
   * Starts decoding of the video stream. Video decoding continues until the video socket
   * connection is closed, for example, by a [disconnect] call.
   */
  fun startVideoDecoding(decoder: VideoDecoder) =
      decoder.start(coroutineScope)

  override fun dispose() {
    // Disconnect socket channels asynchronously.
    CoroutineScope(Dispatchers.Default).launch { disconnect() }
  }

  suspend fun disconnect() {
    coroutineScope {
      val videoChannelClosed = async {
        try {
          if (::videoChannel.isInitialized) {
            videoChannel.close()
          }
        }
        catch (e: IOException) {
          thisLogger().warn(e)
        }
      }
      try {
        if (::controlChannel.isInitialized) {
          controlChannel.close()
        }
      }
      catch (e: IOException) {
        thisLogger().warn(e)
      }
      videoChannelClosed.await()
    }
  }

  private suspend fun pushAgent(deviceSelector: DeviceSelector, adb: AdbDeviceServices) {
    val soFile: Path
    val jarFile: Path
    if (StudioPathManager.isRunningFromSources()) {
      // Development environment.
      val projectDir = project.guessProjectDir()?.toNioPath()
      if (projectDir != null && projectDir.endsWith(SCREEN_SHARING_AGENT_SOURCE_PATH)) {
        // Development environment for the screen sharing agent.
        // Use the agent built by running "Build > Make Project" in Studio.
        val facet = project.allModules().firstNotNullOfOrNull { AndroidFacet.getInstance(it) }
        val buildVariant = facet?.properties?.SELECTED_BUILD_VARIANT ?: "debug"
        soFile = projectDir.resolve(
            "app/build/intermediates/stripped_native_libs/$buildVariant/out/lib/$deviceAbi/$SCREEN_SHARING_AGENT_SO_NAME")
        val apkName = if (buildVariant == "debug") "app-debug.apk" else "app-release-unsigned.apk"
        jarFile = projectDir.resolve("app/build/outputs/apk/$buildVariant/$apkName")
      }
      else {
        // Development environment for Studio.
        // Use the agent built by running "bazel build //tools/adt/idea/emulator/screen-sharing-agent:bundle"
        val binDir = Paths.get(StudioPathManager.getBinariesRoot())
        soFile = binDir.resolve("$SCREEN_SHARING_AGENT_SOURCE_PATH/native/$deviceAbi/$SCREEN_SHARING_AGENT_SO_NAME")
        jarFile = binDir.resolve("$SCREEN_SHARING_AGENT_SOURCE_PATH/$SCREEN_SHARING_AGENT_JAR_NAME")
      }
    }
    else {
      // Installed Studio.
      val pluginDir = PluginPathManager.getPluginHome("android").toPath()
      soFile = pluginDir.resolve("resources/screen-sharing-agent/$deviceAbi/$SCREEN_SHARING_AGENT_SO_NAME")
      jarFile = pluginDir.resolve("resources/screen-sharing-agent/$SCREEN_SHARING_AGENT_JAR_NAME")
    }

    coroutineScope {
      this@DeviceClient.thisLogger()
      val command = "mkdir -p $DEVICE_PATH_BASE; chmod 700 $DEVICE_PATH_BASE"
      adb.shellV2AsLines(deviceSelector, command).collect {
        if (it is ShellCommandOutputElement.ExitCode && it.exitCode != 0) {
          logger.warn("Unable to create $DEVICE_PATH_BASE directory: ${it.exitCode}")
        }
      }
      val permissions = RemoteFileMode.fromPosixPermissions(PosixFilePermission.OWNER_READ)
      val nativeLibraryPushed = async {
        adb.syncSend(deviceSelector, soFile, "$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_SO_NAME", permissions)
      }
      adb.syncSend(deviceSelector, jarFile, "$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_JAR_NAME", permissions)
      nativeLibraryPushed.await()
    }
  }

  private suspend fun startAgent(deviceSelector: DeviceSelector, adb: AdbDeviceServices, initialDisplayOrientation: Int?) {
    startAgentTime = System.currentTimeMillis()
    val orientationArg = initialDisplayOrientation?.let {" --orientation=$it" } ?: ""
    val command = "CLASSPATH=$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_JAR_NAME app_process $DEVICE_PATH_BASE" +
                  " com.android.tools.screensharing.Main" +
                  orientationArg +
                  " --log=${StudioFlags.DEVICE_MIRRORING_AGENT_LOG_LEVEL.get()}" +
                  " --codec=${StudioFlags.DEVICE_MIRRORING_VIDEO_CODEC.get()}"
    // Use a coroutine scope that not linked to the lifecycle of the client to make sure that
    // the agent has a chance to terminate gracefully when the client is disposed rather than
    // be killed by adb.
    CoroutineScope(Dispatchers.Unconfined).launch {
      val log = Logger.getInstance("ScreenSharingAgent")
      try {
        adb.shellV2AsLines(deviceSelector, command).collect {
          when (it) {
            is ShellCommandOutputElement.StdoutLine -> if (it.contents.isNotBlank()) log.info(it.contents)
            is ShellCommandOutputElement.StderrLine -> if (it.contents.isNotBlank()) log.warn(it.contents)
            is ShellCommandOutputElement.ExitCode ->
              if (it.exitCode == 0) log.info("terminated") else log.warn("terminated with code ${it.exitCode}")
          }
        }
      }
      catch (_: EOFException) {
        // Device disconnected. This is not an error.
      }
    }
  }

  private class ClosableReverseForwarding(
    val deviceSelector: DeviceSelector,
    val deviceSocket: SocketSpec,
    val localSocket: SocketSpec,
    val adb: AdbDeviceServices,
    ) : SuspendingCloseable {

    var opened = false

    suspend fun startForwarding() {
      adb.reverseForward(deviceSelector, deviceSocket, localSocket, rebind = true)
      opened = true
    }

    override suspend fun close() {
      if (opened) {
        opened = false
        adb.reverseKillForward(deviceSelector, deviceSocket)
      }
    }
  }
}
