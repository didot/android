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
package com.android.tools.idea.run.tasks

import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.util.LaunchStatus
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import kotlin.test.fail

/**
 * Tests for [ClearAppStorageTask]
 */
class ClearAppStorageTaskTest {
  val project = mock<Project>()
  val executor = mock<Executor>()
  val launchStatus = mock<LaunchStatus>()
  val printer = mock<ConsolePrinter>()
  val handler = mock<ProcessHandler>()
  val indicator = mock<ProgressIndicator>()

  @Test
  fun appExists_success() {
    val device = mockDevice("com.company.application")
    val task = ClearAppStorageTask("com.company.application")

    val result = task.run(launchContext(device))

    assertThat(result.result).isEqualTo(LaunchResult.Result.SUCCESS)
    verify(device).executeShellCommand(eq("pm clear com.company.application"), any())
  }

  @Test
  fun appExists_failure() {
    val device = mockDevice("com.company.application", clearAppStorageSuccess = false)
    val task = ClearAppStorageTask("com.company.application")

    val result = task.run(launchContext(device))

    assertThat(result.result).isEqualTo(LaunchResult.Result.WARNING)
    assertThat(result.message).isEqualTo("Failed to clear app storage for com.company.application on device device1")
    assertThat(result.consoleMessage).isEqualTo("Failed to clear app storage for com.company.application on device device1")
    verify(device).executeShellCommand(eq("pm clear com.company.application"), any())
  }

  @Test
  fun appDoesNotExists() {
    val device = mockDevice("com.company.application1")
    val task = ClearAppStorageTask("com.company.application")

    val result = task.run(launchContext(device))

    assertThat(result.result).isEqualTo(LaunchResult.Result.SUCCESS)
    verify(device, never()).executeShellCommand(eq("pm clear com.company.application"), any())
  }

  private fun launchContext(device: IDevice): LaunchContext =
    LaunchContext(project, executor, device, launchStatus, printer, handler, indicator)
}

private fun mockDevice(packageName: String, clearAppStorageSuccess: Boolean = true): IDevice {
  val mock = mock<IDevice>()
  `when`(mock.executeShellCommand(any(), any())).thenAnswer {
    val command = it.arguments[0] as String
    val receiver = it.arguments[1] as CollectingOutputReceiver
    val result = when {
      command == "pm clear $packageName" -> if (clearAppStorageSuccess) "Success" else "Failed"
      command.startsWith("pm list packages ") -> if (command.endsWith(" $packageName")) "package:$packageName" else ""
      else -> fail("""Command "$command" not setup in mock""")
    }
    `when`(mock.toString()).thenReturn("device1")

    receiver.addOutput(result.toByteArray(), 0, result.length)
    receiver.flush()
  }
  return mock
}
