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
package com.android.tools.idea.testing

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.testutils.MockitoKt
import com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors
import com.android.tools.idea.gradle.run.MakeBeforeRunTask
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider
import com.android.tools.idea.run.AndroidProgramRunner
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.deployment.AndroidExecutionTarget
import com.google.common.truth.Truth
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.runInEdtAndWait
import org.mockito.Mockito
import javax.swing.Icon

fun AndroidRunConfigurationBase.executeMakeBeforeRunStepInTest(device: IDevice) =
  executeMakeBeforeRunStepInTest(DeviceFutures.forDevices(listOf(device)))

fun AndroidRunConfigurationBase.executeMakeBeforeRunStepInTest(deviceFutures: DeviceFutures? = null) {
  val project = project
  val disposable = Disposer.newDisposable()

  // Make build failures visible in the test output.
  injectBuildOutputDumpingBuildViewManager(project, disposable)

  try {
    val makeBeforeRunTask = beforeRunTasks.filterIsInstance<MakeBeforeRunTask>().single()
    val factory = factory!!
    val runnerAndConfigurationSettings = RunManager.getInstance(project).createConfiguration(this, factory)

    // Set up ExecutionTarget infrastructure.
    ApplicationManager.getApplication().invokeAndWait {
      val target = object : AndroidExecutionTarget() {
        override fun getId(): String = "target"
        override fun getDisplayName(): String = "target"
        override fun getIcon(): Icon? = null
        override fun isApplicationRunning(packageName: String): Boolean = false
        override fun getAvailableDeviceCount(): Int = 1
        override fun getRunningDevices(): Collection<IDevice> = emptyList()
        override fun canRun(configuration: RunConfiguration): Boolean = configuration === this@executeMakeBeforeRunStepInTest
      }
      ExecutionTargetManager.getInstance(this.project).activeTarget = target
    }

    val programRunner = object : AndroidProgramRunner() {
      override fun getRunnerId(): String = "runner_id"
      override fun canRunWithMultipleDevices(executorId: String): Boolean = false
    }

    val executionEnvironment = ExecutionEnvironment(
      DefaultRunExecutor.getRunExecutorInstance(),
      programRunner,
      runnerAndConfigurationSettings,
      project
    )
    deviceFutures?.let { executionEnvironment.putCopyableUserData(DeviceFutures.KEY, deviceFutures) }
    try {
      Truth.assertThat(
        BeforeRunTaskProvider.getProvider(project, MakeBeforeRunTaskProvider.ID)!!
          .executeTask(
            DataContext.EMPTY_CONTEXT,
            this,
            executionEnvironment,
            makeBeforeRunTask
          )
      ).isTrue()
    }
    finally {
      runInEdtAndWait {
        AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
      }
    }
  }
  finally {
    Disposer.dispose(disposable)
  }
}

@JvmOverloads
fun mockDeviceFor(androidVersion: Int, abis: List<Abi>, density: Int? = null): IDevice {
  val device = MockitoKt.mock<IDevice>()
  Mockito.`when`(device.abis).thenReturn(abis.map { it.toString() })
  Mockito.`when`(device.version).thenReturn(AndroidVersion(androidVersion))
  density?.let { Mockito.`when`(device.density).thenReturn(density) }
  return device
}

fun withSimulatedSyncError(errorMessage: String, block: () -> Unit) {
  SimulatedSyncErrors.registerSyncErrorToSimulate(errorMessage)
  try {
    block()
  }
  finally {
    SimulatedSyncErrors.clear() // May leak to tests running afterwards.
  }
}
