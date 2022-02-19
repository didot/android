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

import com.android.AndroidProjectTypes
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.IDevice
import com.android.tools.deployer.model.App
import com.android.tools.idea.run.AndroidLaunchTaskContributor
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.ApplicationTerminator
import com.android.tools.idea.run.activity.InstantAppStartActivityFlagsProvider
import com.android.tools.idea.run.activity.launch.ActivityLaunchOptionState
import com.android.tools.idea.run.configuration.isDebug
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.concurrency.Promise

class AndroidActivityConfigurationExecutor(environment: ExecutionEnvironment) : AndroidConfigurationExecutorBase(environment) {

  override val configuration = environment.runProfile as AndroidRunConfiguration
  private val facet = AndroidFacet.getInstance(configuration.module!!)!!

  @WorkerThread
  override fun doOnDevices(devices: List<IDevice>): Promise<RunContentDescriptor> {
    val isDebug = environment.executor.isDebug
    if (isDebug && devices.size > 1) {
      throw ExecutionException("Debugging is allowed only for single device")
    }
    val console = createConsole()
    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()
    val applicationInstaller = getApplicationInstaller(console)
    val processHandler = ActivityProcessHandler(appId, console)
    devices.forEach { device ->
      val terminator = ApplicationTerminator(device, appId)
      if (!terminator.killApp()) {
        throw ExecutionException("Could not terminate running app $appId")
      }
      processHandler.addDevice(device)
      ProgressManager.checkCanceled()
      indicator?.text = "Installing app"
      val app = applicationInstaller.installAppOnDevice(device, appId, getApkPaths(device), configuration.PM_INSTALL_OPTIONS)
      activateActivity(device, app, console)
    }
    ProgressManager.checkCanceled()
    if (isDebug) {
      return startDebugSession(devices.single(), processHandler, console).then { it.runContentDescriptor }
    }
    return createRunContentDescriptor(processHandler, console, environment)
  }

  private fun StringBuilder.appendWithSpace(text: String) {
    if (text.isNotEmpty()) {
      append(if (this.isEmpty()) "" else " ")
      append(text)
    }
  }

  private fun getFlags(device: IDevice): String {
    if (facet.configuration.projectType == AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP) {
      return InstantAppStartActivityFlagsProvider().getFlags(device)
    }

    val amStartOptions = StringBuilder()
    for (taskContributor in AndroidLaunchTaskContributor.EP_NAME.extensions) {
      val amOptions = taskContributor.getAmStartOptions(appId, configuration, device, environment.executor)
      amStartOptions.appendWithSpace(amOptions)
      // TODO: use contributors launch tasks
    }

    amStartOptions.appendWithSpace(configuration.ACTIVITY_EXTRA_FLAGS)

    return amStartOptions.toString()
  }

  private fun activateActivity(device: IDevice, app: App, console: ConsoleView) {
    val state: ActivityLaunchOptionState = configuration.getLaunchOptionState(configuration.MODE)!!

    state.launch(device, app, configuration, environment.executor.isDebug, getFlags(device), console)
  }
}

class ActivityProcessHandler(private val appId: String, private val console: ConsoleView) : AndroidProcessHandlerForDevices() {
  override fun destroyProcessOnDevice(device: IDevice) {
    device.executeShellCommand("am force-stop $appId", console)
  }
}
