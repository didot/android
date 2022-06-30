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

import com.android.ddmlib.IDevice
import com.android.tools.deployer.Deployer
import com.android.tools.deployer.DeployerException
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.run.ApkFileUnit
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.tasks.DeployTask
import com.intellij.execution.ExecutionException
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.Project
import java.util.stream.Collectors

interface ApplicationDeployer {
  fun fullDeploy(device: IDevice, packages: Collection<ApkInfo>, deployOptions: DeployOptions): Deployer.Result
  fun applyChangesDeploy(device: IDevice, packages: Collection<ApkInfo>, deployOptions: DeployOptions): Deployer.Result
  fun applyCodeChangesDeploy(device: IDevice, packages: Collection<ApkInfo>, deployOptions: DeployOptions): Deployer.Result
}

data class DeployOptions(
  val disabledDynamicFeatures: List<String>,
  val pmInstallFlags: String,
  val installOnAllUsers: Boolean,
  val alwaysInstallWithPm: Boolean
)

class ApplicationDeployerImpl(private val project: Project,
                              val console: ConsoleView) : ApplicationDeployer {
  private val LOG = Logger.getInstance(this::class.java)

  override fun fullDeploy(device: IDevice, packages: Collection<ApkInfo>, deployOptions: DeployOptions): Deployer.Result {

    ProgressIndicatorProvider.getGlobalProgressIndicator()?.checkCanceled()
    ProgressIndicatorProvider.getGlobalProgressIndicator()?.text = "Installing app"

    // Add packages to the deployment, filtering out any dynamic features that are disabled.
    val filtered: List<ApkInfo> = packages.map { apkInfo: ApkInfo ->
      filterDisabledFeatures(apkInfo, deployOptions.disabledDynamicFeatures)
    }
    val deployTask = DeployTask(
      project,
      filtered,
      deployOptions.pmInstallFlags,
      deployOptions.installOnAllUsers,
      deployOptions.alwaysInstallWithPm)
    //TODO: figure out in what cases we have more than one app
    try {
      return deployTask.run(device, console, LogWithConsole(LOG, console)).first()
    }
    catch (e: DeployerException) {
      throw ExecutionException("Failed to install app '${filtered.first().applicationId}'. ${e.details ?: ""}", e)
    }
  }

  override fun applyChangesDeploy(device: IDevice, packages: Collection<ApkInfo>, deployOptions: DeployOptions): Deployer.Result {
     throw RuntimeException("Unsupported operation")
  }

  override fun applyCodeChangesDeploy(device: IDevice, packages: Collection<ApkInfo>, deployOptions: DeployOptions): Deployer.Result {
     throw RuntimeException("Unsupported operation")
  }

  private fun filterDisabledFeatures(apkInfo: ApkInfo, disabledFeatures: List<String>): ApkInfo {
    return if (apkInfo.files.size > 1) {
      val filtered = apkInfo.files.stream()
        .filter { feature: ApkFileUnit? ->
          DynamicAppUtils.isFeatureEnabled(disabledFeatures,
                                           feature!!)
        }
        .collect(Collectors.toList())
      ApkInfo(filtered, apkInfo.applicationId)
    }
    else {
      apkInfo
    }
  }

  private class LogWithConsole(logger: Logger, val console: ConsoleView) : LogWrapper(logger) {
    override fun info(msgFormat: String, vararg args: Any?) { // print to user console commands that we run on device
      if (msgFormat.contains("$ adb")) {
        console.print(msgFormat + "\n")
      }
      super.info(msgFormat, *args)
    }

    override fun warning(msgFormat: String, vararg args: Any?) { // print to user console commands that we run on device
      console.printError(msgFormat + "\n")
      super.info(msgFormat, *args)
    }
  }
}

