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
import com.android.tools.deployer.DeployerException
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.deployer.model.component.WatchFace.ShellCommand.SHOW_WATCH_FACE
import com.android.tools.deployer.model.component.WatchFace.ShellCommand.UNSET_WATCH_FACE
import com.android.tools.deployer.model.component.WearComponent.CommandResultReceiver
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.run.AndroidProcessHandler
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfiguration
import com.intellij.execution.ExecutionException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

private const val WATCH_FACE_MIN_DEBUG_SURFACE_VERSION = 2

class AndroidWatchFaceConfigurationExecutor(environment: ExecutionEnvironment) : AndroidConfigurationExecutorBase(environment) {
  override val configuration = environment.runProfile as AndroidWatchFaceConfiguration

  @WorkerThread
  override fun doOnDevices(devices: List<IDevice>): Promise<RunContentDescriptor> {
    val isDebug = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID
    if (isDebug && devices.size > 1) {
      throw ExecutionException("Debugging is allowed only for single device")
    }
    val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    Disposer.register(project, console)
    val applicationInstaller = getApplicationInstaller(console)
    val mode = if (isDebug) AppComponent.Mode.DEBUG else AppComponent.Mode.RUN
    val processHandler = AndroidProcessHandler(project, appId, getStopWatchFaceCallback(console, isDebug))
    devices.forEach { device ->
      terminatePreviousAppInstance(device)
      processHandler.addTargetDevice(device)
      val version = device.getWearDebugSurfaceVersion()
      if (version < WATCH_FACE_MIN_DEBUG_SURFACE_VERSION) {
        throw SurfaceVersionException(WATCH_FACE_MIN_DEBUG_SURFACE_VERSION, version, device.isEmulator)
      }
      val app = installWatchFace(device, applicationInstaller)
      if (isDebug) {
        val promise = AsyncPromise<RunContentDescriptor>()
        executeOnPooledThread {
          startDebugSession(devices.single(), console)
            .onError(promise::setError)
            .then { it.runContentDescriptor }.processed(promise)
        }
        setWatchFace(app, mode)
        showWatchFace(device, console)
        return promise
      }
      setWatchFace(app, mode)
      showWatchFace(device, console)
    }
    ProgressManager.checkCanceled()
    return createRunContentDescriptor(processHandler, console, environment)
  }

  private fun installWatchFace(device: IDevice, applicationInstaller: ApplicationInstaller): App {
    ProgressIndicatorProvider.getGlobalProgressIndicator()?.apply {
      checkCanceled()
      text = "Installing the watch face"
    }
    return applicationInstaller.installAppOnDevice(device, appId, getApkPaths(device), configuration.installFlags)
  }

  private fun setWatchFace(app: App, mode: AppComponent.Mode) {
    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()?.apply {
      checkCanceled()
      text = "Launching the watch face"
    }
    val outputReceiver = RecordOutputReceiver { indicator?.isCanceled == true }
    try {
      app.activateComponent(configuration.componentType, configuration.componentName!!, mode, outputReceiver)
    }
    catch (ex: DeployerException) {
      throw ExecutionException("Error while launching watch face, message: ${outputReceiver.getOutput().ifEmpty { ex.details }}", ex)
    }
  }

  private fun startDebugSession(
    device: IDevice,
    console: ConsoleView
  ): Promise<XDebugSessionImpl> {
    checkAndroidVersionForWearDebugging(device.version, console)
    return DebugSessionStarter(environment).attachDebuggerToClient(device, getStopWatchFaceCallback(console, true), console)
  }
}

internal fun showWatchFace(device: IDevice, console: ConsoleView) {
  ProgressIndicatorProvider.getGlobalProgressIndicator()?.apply {
    checkCanceled()
    text = "Jumping to the watch face"
  }
  val resultReceiver = CommandResultReceiver()
  device.executeShellCommand(SHOW_WATCH_FACE, console, resultReceiver)
  if (resultReceiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
    console.printError("Warning: Launch was successful, but you may need to bring up the watch face manually")
  }
}

private fun getStopWatchFaceCallback(console: ConsoleView, isDebug: Boolean): (IDevice) -> Unit = { device: IDevice ->
  val receiver = CommandResultReceiver()
  device.executeShellCommand(UNSET_WATCH_FACE, console, receiver)
  if (receiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
    console.printError("Warning: Watch face was not stopped.")
  }
  if (isDebug) {
    stopDebugApp(device)
  }
}