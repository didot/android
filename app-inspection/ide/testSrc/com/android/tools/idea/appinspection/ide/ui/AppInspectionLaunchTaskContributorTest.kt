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
package com.android.tools.idea.appinspection.ide.ui

import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.DeviceImpl
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.run.AndroidLaunchTasksProvider
import com.android.tools.idea.run.AndroidProcessHandler
import com.android.tools.idea.run.AndroidRemoteDebugProcessHandler
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.DefaultStudioProgramRunner
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.DebuggerManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`

class AppInspectionLaunchTaskContributorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testRecentProcess() {
    val project = projectRule.project
    val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)
    val runner = DefaultStudioProgramRunner()
    val env = ExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance(), runner, mock(), project)
    val applicationIdProvider: ApplicationIdProvider = object : ApplicationIdProvider {
      override fun getPackageName(): String = "com.example"
      override fun getTestPackageName(): String? = null
    }

    val launchOptions = LaunchOptions.builder()
      .setClearLogcatBeforeStart(false)
      .build()

    val config = AndroidRunConfigurationType.getInstance().factory.createTemplateConfiguration(project) as AndroidRunConfiguration
    val launchTaskProvider = AndroidLaunchTasksProvider(
      config,
      env,
      AndroidFacet.getInstance(projectRule.module)!!,
      applicationIdProvider,
      mock(),
      launchOptions
    )

    val tasks = launchTaskProvider.getTasks(device, mock(), mock())
      .filterIsInstance(AppInspectionLaunchTask::class.java)

    // Make sure the LayoutInspectorLaunchTaskContributor is registered:
    assertThat(tasks).hasSize(1)
    val task = tasks.single()

    // Start process "p1"
    val handler1 = AndroidProcessHandler(project, "p1")
    val status1 = ProcessHandlerLaunchStatus(handler1)
    val launchContext1 = LaunchContext(project, DefaultRunExecutor(), device, status1, mock(), handler1, mock())
    task.run(launchContext1)
    handler1.startNotify()

    // Make sure that the process p1 is recorded as the recent process:
    assertThat(RecentProcess.get(project)!!.device).isSameAs(device)
    assertThat(RecentProcess.get(project)!!.packageName).isEqualTo("p1")

    // Start process "p2"
    val handler2 = AndroidProcessHandler(project, "p2")
    val status2 = ProcessHandlerLaunchStatus(handler2)
    val launchContext2 = LaunchContext(project, DefaultRunExecutor(), device, status2, mock(), handler2, mock())
    task.run(launchContext2)
    handler2.startNotify()

    // Make sure that the process p2 is now recorded as the recent process:
    assertThat(RecentProcess.get(project)!!.device).isSameAs(device)
    assertThat(RecentProcess.get(project)!!.packageName).isEqualTo("p2")

    // Kill process p1 and check that the recent process is still p2:
    handler1.killProcess()
    assertThat(RecentProcess.get(project)!!.device).isSameAs(device)
    assertThat(RecentProcess.get(project)!!.packageName).isEqualTo("p2")

    // Simulate that process2 was started in the debugger.
    // The ProcessHandler will be switched See ConnectJavaDebuggerTask.launchDebugger.
    // Assert that the recent process is still p2.
    val debugManager = projectRule.mockProjectService(DebuggerManager::class.java)
    `when`(debugManager.getDebugProcess(any(ProcessHandler::class.java))).thenReturn(mock())
    val debugHandler2 = AndroidRemoteDebugProcessHandler(project, mock(), false)
    debugHandler2.startNotify()
    status2.processHandler = debugHandler2
    handler2.killProcess()
    assertThat(RecentProcess.get(project)!!.device).isSameAs(device)
    assertThat(RecentProcess.get(project)!!.packageName).isEqualTo("p2")

    // Destroy process p2 and check that there are no recent processes since p2 is gone:
    debugHandler2.destroyProcess()
    assertThat(RecentProcess.get(project)).isNull()
  }
}
