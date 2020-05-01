/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.adapter

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.ddmlib.testrunner.IInstrumentationResultParser.StatusKeys.DDMLIB_LOGCAT
import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.TestIdentifier
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.androidCoroutineExceptionHandler
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * An adapter to translate [ITestRunListener] callback methods into [AndroidTestResultListener].
 */
class DdmlibTestRunListenerAdapter(device: IDevice,
                                   private val listener: AndroidTestResultListener) : ITestRunListener {

  companion object {
    const val BENCHMARK_TEST_METRICS_KEY = "android.studio.display.benchmark"
    private val benchmarkPrefixRegex = "^benchmark:".toRegex(RegexOption.MULTILINE)

    /**
     * Retrieves benchmark output text from a given [testMetrics].
     */
    private fun getBenchmarkOutput(testMetrics: MutableMap<String, String>): String {
      // Workaround solution for b/154322086.
      return benchmarkPrefixRegex
        .replace(testMetrics.getOrDefault(BENCHMARK_TEST_METRICS_KEY, ""), "")
        .trimIndent()
    }

    /**
     * Executes a given shell command on a given device. This function blocks caller
     * until the command finishes or times out and returns output in string.
     */
    @WorkerThread
    private fun executeShellCommandSync(device: IDevice, command: String): String {
      val latch = CountDownLatch(1)
      val receiver = CollectingOutputReceiver(latch)
      device.executeShellCommand(command, receiver, 10, TimeUnit.SECONDS)
      latch.await(10, TimeUnit.SECONDS)
      return receiver.output.trim()
    }
  }

  private val myDevice = AndroidDevice(device.serialNumber,
                                       device.avdName ?: device.serialNumber,
                                       if (device.isEmulator) { AndroidDeviceType.LOCAL_EMULATOR }
                                       else { AndroidDeviceType.LOCAL_PHYSICAL_DEVICE },
                                       device.version,
                                       Collections.synchronizedMap(LinkedHashMap())).apply {
    CoroutineScope(AndroidDispatchers.workerThread + androidCoroutineExceptionHandler).launch {
      additionalInfo["RAM"] = executeShellCommandSync(
        device,
        """awk '( ${'$'}1 == "MemTotal:" ) { printf "%.1f GB", ${'$'}2/1000/1000 }' /proc/meminfo""")
      additionalInfo["Processor"] = executeShellCommandSync(
        device,
        """cat /proc/cpuinfo | grep 'model name' | awk -F':' '{ print ${'$'}2 }' | uniq""")
      additionalInfo["Manufacturer"] = executeShellCommandSync(
        device, "getprop ro.product.manufacturer")
    }
  }

  private lateinit var myTestSuite: AndroidTestSuite
  private val myTestCases = mutableMapOf<TestIdentifier, AndroidTestCase>()

  init {
    listener.onTestSuiteScheduled(myDevice)
  }

  override fun testRunStarted(runName: String, testCount: Int) {
    myTestSuite = AndroidTestSuite(runName, runName, testCount)
    listener.onTestSuiteStarted(myDevice, myTestSuite)
  }

  override fun testStarted(testId: TestIdentifier) {
    val testCase = AndroidTestCase(testId.toString(), testId.toString(), AndroidTestCaseResult.IN_PROGRESS)
    myTestCases[testId] = testCase
    listener.onTestCaseStarted(myDevice, myTestSuite, testCase)
  }

  override fun testFailed(testId: TestIdentifier, trace: String) {
    val testCase = myTestCases.getValue(testId)
    testCase.result = AndroidTestCaseResult.FAILED
    testCase.errorStackTrace = trace
    myTestSuite.result = AndroidTestSuiteResult.FAILED
  }

  override fun testAssumptionFailure(testId: TestIdentifier, trace: String) {
    val testCase = myTestCases.getValue(testId)
    testCase.result = AndroidTestCaseResult.FAILED
    testCase.errorStackTrace = trace
    myTestSuite.result = AndroidTestSuiteResult.FAILED
  }

  override fun testIgnored(testId: TestIdentifier) {
    val testCase = myTestCases.getValue(testId)
    testCase.result = AndroidTestCaseResult.SKIPPED
  }

  override fun testEnded(testId: TestIdentifier, testMetrics: MutableMap<String, String>) {
    val testCase = myTestCases.getValue(testId)
    if (!testCase.result.isTerminalState) {
      testCase.result = AndroidTestCaseResult.PASSED
    }
    testCase.logcat = testMetrics.getOrDefault(DDMLIB_LOGCAT, "")
    testCase.benchmark = getBenchmarkOutput(testMetrics)
    listener.onTestCaseFinished(myDevice, myTestSuite, testCase)
  }

  override fun testRunFailed(errorMessage: String) {
    myTestSuite.result = AndroidTestSuiteResult.ABORTED
  }

  override fun testRunStopped(elapsedTime: Long) {
    myTestSuite.result = AndroidTestSuiteResult.CANCELLED
  }

  override fun testRunEnded(elapsedTime: Long, runMetrics: MutableMap<String, String>) {
    // Ddmlib calls testRunEnded() callback if the target app process has crashed or
    // killed manually. (For example, if you click "stop" run button from Android Studio,
    // it kills the app process. Thus, we update test results to cancelled for all
    // pending tests.
    for (testCase in myTestCases.values) {
      if (!testCase.result.isTerminalState) {
        testCase.result = AndroidTestCaseResult.CANCELLED
        myTestSuite.result = myTestSuite.result ?: AndroidTestSuiteResult.CANCELLED
      }
    }

    myTestSuite.result = myTestSuite.result ?: AndroidTestSuiteResult.PASSED
    listener.onTestSuiteFinished(myDevice, myTestSuite)
  }
}