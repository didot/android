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
package com.android.tools.idea.testartifacts.instrumented

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.util.LaunchStatus
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessHandler
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations.openMocks

/**
 * Unit tests for [GradleAndroidTestApplicationLaunchTask].
 */
@RunWith(JUnit4::class)
class GradleAndroidTestApplicationLaunchTaskTest {
  @get:Rule
  val gradleProjectRule = AndroidGradleProjectRule()

  @Mock lateinit var mockExecutor: Executor
  @Mock lateinit var mockHandler: ProcessHandler
  @Mock lateinit var mockLaunchStatus: LaunchStatus
  @Mock lateinit var mockPrinter: ConsolePrinter
  @Mock lateinit var mockProcessHandler: ProcessHandler
  @Mock lateinit var mockAndroidTestResultListener:  AndroidTestResultListener

  @Before
  fun setup() {
    openMocks(this)
  }

  private fun createMockDevice(serialNumber: String): IDevice {
    val mockDevice = mock(IDevice::class.java)
    `when`(mockDevice.serialNumber).thenReturn(serialNumber)
    `when`(mockDevice.version).thenReturn(AndroidVersion(29))
    return mockDevice
  }

  private fun createMockGradleAndroidTestInvoker(selectedDevices: Int): GradleConnectedAndroidTestInvoker {
    val mockGradleConnectedAndroidTestInvoker = mock(GradleConnectedAndroidTestInvoker::class.java)
    val devices = ArrayList<IDevice>()
    for (i in 1..selectedDevices) {
      val device = createMockDevice("SERIAL_NUMBER_$i")
      devices.add(device)
    }
    `when`(mockGradleConnectedAndroidTestInvoker.getDevices()).thenReturn(devices)
    return mockGradleConnectedAndroidTestInvoker
  }

  @Test
  fun testTaskReturnsSuccessForAllInModuleTest() {
    val mockProject = gradleProjectRule.project
    val mockDevice = createMockDevice("SERIAL_NUMBER_1")
    val gradleConnectedAndroidTestInvoker = createMockGradleAndroidTestInvoker(1)
    `when`(gradleConnectedAndroidTestInvoker.run(mockDevice)).thenReturn(true)
    `when`(mockProcessHandler.getCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY)).thenReturn(mockAndroidTestResultListener)

    val launchTask = GradleAndroidTestApplicationLaunchTask.allInModuleTest(
      mockProject,
      "taskId",
      /*waitForDebugger*/false,
      mockProcessHandler,
      mockPrinter,
      mockDevice,
      gradleConnectedAndroidTestInvoker)

    assertThat(launchTask.run(LaunchContext(mockProject, mockExecutor, mockDevice, mockLaunchStatus, mockPrinter, mockHandler))?.success)
      .isTrue()
  }

  @Test
  fun checkGradleExecutionSettingsForAllInPackageTestWithSingleDevice() {
    val mockDevice = createMockDevice("SERIAL_NUMBER_1")
    val mockProject = gradleProjectRule.project
    val mockPackageName = "packageName"
    val gradleConnectedAndroidTestInvoker = createMockGradleAndroidTestInvoker(1)

    val launchTask = GradleAndroidTestApplicationLaunchTask.allInPackageTest(
      mockProject,
      "taskId",
      /*waitForDebugger*/false,
      mockProcessHandler,
      mockPrinter,
      mockDevice,
      mockPackageName,
      gradleConnectedAndroidTestInvoker)

    assertThat(launchTask.getGradleExecutionSettings().arguments).contains(
      "-Pandroid.testInstrumentationRunnerArguments.package=$mockPackageName")
    assertThat(launchTask.getGradleExecutionSettings().env).containsEntry("ANDROID_SERIAL","SERIAL_NUMBER_1")
  }

  @Test
  fun checkGradleExecutionSettingsForClassTestWithMultipleDevices() {
    val mockDevice = createMockDevice("SERIAL_NUMBER_2")
    val mockProject = gradleProjectRule.project
    val mockClassName = "className"
    val gradleConnectedAndroidTestInvoker = createMockGradleAndroidTestInvoker(2)

    val launchTask = GradleAndroidTestApplicationLaunchTask.classTest(
      mockProject,
      "taskId",
      /*waitForDebugger*/false,
      mockProcessHandler,
      mockPrinter,
      mockDevice,
      mockClassName,
      gradleConnectedAndroidTestInvoker)

    assertThat(launchTask.getGradleExecutionSettings().arguments).contains(
      "-Pandroid.testInstrumentationRunnerArguments.class=$mockClassName")
    assertThat(launchTask.getGradleExecutionSettings().env).containsEntry("ANDROID_SERIAL","SERIAL_NUMBER_1,SERIAL_NUMBER_2")
  }

  @Test
  fun checkGradleExecutionSettingsForMethodTestWithDebugger() {
    val mockDevice = createMockDevice("SERIAL_NUMBER_1")
    val mockProject = gradleProjectRule.project
    val mockClassName = "className"
    val mockMethodName = "methodName"
    val gradleConnectedAndroidTestInvoker = createMockGradleAndroidTestInvoker(1)

    val launchTask = GradleAndroidTestApplicationLaunchTask.methodTest(
      mockProject,
      "taskId",
      /*waitForDebugger*/true,
      mockProcessHandler,
      mockPrinter,
      mockDevice,
      mockClassName,
      mockMethodName,
      gradleConnectedAndroidTestInvoker)

    assertThat(launchTask.getGradleExecutionSettings().arguments).contains(
      "-Pandroid.testInstrumentationRunnerArguments.class=$mockClassName#$mockMethodName")
    assertThat(launchTask.getGradleExecutionSettings().arguments).contains(
      "-Pandroid.testInstrumentationRunnerArguments.debug=true")
  }
}