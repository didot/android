/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.configuration.execution

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.android.ddmlib.MultiReceiver
import com.android.tools.deployer.DeployerException
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.deployer.model.component.Tile
import com.android.tools.deployer.model.component.Tile.ShellCommand.SHOW_TILE_COMMAND
import com.android.tools.deployer.model.component.WearComponent.CommandResultReceiver
import com.android.tools.idea.run.AndroidProcessHandler
import com.android.tools.idea.run.configuration.AndroidTileConfiguration
import com.intellij.execution.ExecutionException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.concurrency.Promise
import java.time.Duration

private const val TILE_MIN_DEBUG_SURFACE_VERSION = 2
private const val TILE_RECOMMENDED_DEBUG_SURFACE_VERSION = 3

class AndroidTileConfigurationExecutor(environment: ExecutionEnvironment) : AndroidConfigurationExecutorBase(environment) {
  override val configuration = environment.runProfile as AndroidTileConfiguration

  @WorkerThread
  override fun doOnDevices(devices: List<IDevice>): Promise<RunContentDescriptor> {
    val isDebug = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID
    if (isDebug && devices.size > 1) {
      throw ExecutionException("Debugging is allowed only for a single device")
    }
    val console = createConsole()
    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()
    val applicationInstaller = getApplicationInstaller(console)
    val mode = if (isDebug) AppComponent.Mode.DEBUG else AppComponent.Mode.RUN
    val tileName = AppComponent.getFQEscapedName(appId, configuration.componentName!!)
    val processHandler = AndroidProcessHandler(project, appId, getStopTileCallback(tileName, console, isDebug))
    devices.forEach { device ->
      terminatePreviousAppInstance(device)
      processHandler.addTargetDevice(device)
      val version = device.getWearDebugSurfaceVersion()
      if (version < TILE_MIN_DEBUG_SURFACE_VERSION) {
        throw SurfaceVersionException(TILE_MIN_DEBUG_SURFACE_VERSION, version, device.isEmulator)
      }
      if (version < TILE_RECOMMENDED_DEBUG_SURFACE_VERSION) {
        console.printError(AndroidBundle.message("android.run.configuration.debug.surface.warn"))
      }
      indicator?.checkCanceled()
      indicator?.text = "Installing app"
      val app = applicationInstaller.installAppOnDevice(device, appId, getApkPaths(device), configuration.installFlags)
      // TODO(b/226550406): Only add this sleep for older versions where the race condition exists.
      Thread.sleep(Duration.ofSeconds(2).toMillis())
      val tileIndex = setWatchTile(app, mode, indicator, console)
      val showTileCommand = SHOW_TILE_COMMAND + tileIndex!!
      val showTileReceiver = CommandResultReceiver()
      device.executeShellCommand(showTileCommand, console, showTileReceiver)
      verifyResponse(showTileReceiver, console)
    }
    ProgressManager.checkCanceled()
    if (isDebug) {
      return startDebugSession(devices.single(), console).then { it.runContentDescriptor }
    }
    return createRunContentDescriptor(processHandler, console, environment)
  }

  private fun setWatchTile(app: App, mode: AppComponent.Mode, indicator: ProgressIndicator?, console: ConsoleView): Int? {
    val outputReceiver = RecordOutputReceiver { indicator?.isCanceled == true }
    val consoleReceiver = ConsoleOutputReceiver({ indicator?.isCanceled == true }, console)
    val indexReceiver = AddTileCommandResultReceiver { indicator?.isCanceled == true }
    val receiver = MultiReceiver(outputReceiver, consoleReceiver, indexReceiver)
    try {
      app.activateComponent(configuration.componentType, configuration.componentName!!, mode, receiver)
    }
    catch (ex: DeployerException) {
      throw ExecutionException("Error while setting the tile, message: ${outputReceiver.getOutput()}", ex)
    }

    if (indexReceiver.index == null) {
      throw ExecutionException("Tile index was not found.")
    }
    return indexReceiver.index!!
  }

  private fun verifyResponse(receiver: CommandResultReceiver, console: ConsoleView) {
    if (receiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
      console.printError("Warning: Launch was successful, but you may need to bring up the tile manually.")
    }
  }

  private fun startDebugSession(
    device: IDevice,
    console: ConsoleView
  ): Promise<XDebugSessionImpl> {
    checkAndroidVersionForWearDebugging(device.version, console)
    val tileName = AppComponent.getFQEscapedName(appId, configuration.componentName!!)
    return DebugSessionStarter(environment).attachDebuggerToClient(device, getStopTileCallback(tileName, console, true), console)
  }
}

private class AddTileCommandResultReceiver(private val isCancelledCheck: () -> Boolean) : MultiLineReceiver() {
  private val indexPattern = "Index=\\[(\\d+)]".toRegex()
  var index: Int? = null

  override fun isCancelled(): Boolean = isCancelledCheck()

  override fun processNewLines(lines: Array<String>) = lines.forEach { line ->
    extractPattern(line, indexPattern)?.let { index = it.toInt() }
  }
}

private fun getStopTileCallback(tileName: String, console: ConsoleView, isDebug: Boolean): (IDevice) -> Unit = { device: IDevice ->
  val receiver = CommandResultReceiver()
  val removeTileCommand = Tile.ShellCommand.UNSET_TILE + tileName
  device.executeShellCommand(removeTileCommand, console, receiver)
  if (receiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
    console.printError("Warning: Tile was not stopped.")
  }
  if (isDebug) {
    stopDebugApp(device)
  }
}
