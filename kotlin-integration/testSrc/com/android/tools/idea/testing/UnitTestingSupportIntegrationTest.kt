/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.testartifacts.TestConfigurationTesting.createContext
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.TestRun
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.impl.ExecutionManagerKtImpl
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.TestRunnerUtil
import com.intellij.testFramework.runInEdtAndWait
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Whether to print debug "status updates" to stdout.
 *
 * This may be useful when debugging, since the steps in this test can take quite some time.
 */
private const val PRINT_LOG = false

private fun log(message: String) {
  @Suppress("ConstantConditionIf")
  if (PRINT_LOG) {
    println(message)
  }
}

/**
 * Integration test that invokes our unit test run configurations.
 *
 * Test code is executed on the main thread, not EDT. This way we can block waiting for testing to finish.
 *
 * The test project uses 3.0 features, so you may need to set STUDIO_CUSTOM_REPO to point to a recent build of our gradle plugin.
 */
class UnitTestingSupportIntegrationTest : AndroidGradleTestCase() {
  override fun runInDispatchThread(): Boolean = false
  override fun invokeTestRunnable(runnable: Runnable) = runnable.run()

  override fun setUp() {
    TestRunnerUtil.replaceIdeEventQueueSafely() // See UsefulTestCase#runBare which should be the stack frame above this one.
    runInEdtAndWait {
      super.setUp()
      loadProject(TestProjectPaths.UNIT_TESTING)

      // Without this, the execution manager will not invoke gradle to compile the project, so no tests will be found.
      val executionManager = ExecutionManagerImpl.getInstance(project)
      if (executionManager is ExecutionManagerKtImpl) {
        executionManager.forceCompilationInTests = true
      }
      log("project imported")

      // Calling the code below makes sure there are no mistakes in the test project and that all class files are in place. Doing this
      // (and syncing VFS) seems to fix huge flakiness that we observe otherwise.
      // TODO(b/64667992): try to listen for a finished gradle build (the one from "run before" in the run config) and sync VFS then.
      val result = invokeGradleTasks(project, "test")
      assertTrue(result.isBuildSuccessful)
      log("Command-line tests done")
      VirtualFileManager.getInstance().syncRefresh()
      log("Vfs synced")
    }
  }

  override fun tearDown() {
    try {
      UsageTracker.cleanAfterTesting()
    } finally {
      super.tearDown()
    }
  }

  fun testAppModule() {
    checkTestClass(
        "com.example.app.AppJavaUnitTest",
        expectedTests = setOf(
            "java:test://com.example.app.AppJavaUnitTest/aarDependencies",
            "java:test://com.example.app.AppJavaUnitTest/assertions",
            "java:test://com.example.app.AppJavaUnitTest/commonsLogging",
            "java:test://com.example.app.AppJavaUnitTest/enums",
            "java:test://com.example.app.AppJavaUnitTest/exceptions",
            "java:test://com.example.app.AppJavaUnitTest/instanceFields",
            "java:test://com.example.app.AppJavaUnitTest/javaLibJavaResourcesOnClasspath",
            "java:test://com.example.app.AppJavaUnitTest/javaResourcesOnClasspath",
            "java:test://com.example.app.AppJavaUnitTest/libJavaResourcesOnClasspath",
            "java:test://com.example.app.AppJavaUnitTest/mockFinalClass",
            "java:test://com.example.app.AppJavaUnitTest/mockFinalMethod",
            "java:test://com.example.app.AppJavaUnitTest/mockInnerClass",
            "java:test://com.example.app.AppJavaUnitTest/prodJavaResourcesOnClasspath",
            "java:test://com.example.app.AppJavaUnitTest/prodRClass",
            "java:test://com.example.app.AppJavaUnitTest/referenceJavaLibJavaClass",
            "java:test://com.example.app.AppJavaUnitTest/referenceJavaLibKotlinClass",
            "java:test://com.example.app.AppJavaUnitTest/referenceLibraryCode",
            "java:test://com.example.app.AppJavaUnitTest/referenceLibraryKotlinCode",
            "java:test://com.example.app.AppJavaUnitTest/referenceProductionCode",
            "java:test://com.example.app.AppJavaUnitTest/referenceProductionKotlinCode",
            "java:test://com.example.app.AppJavaUnitTest/workingDir"
        )
    )

    checkTestClass(
        "com.example.app.AppKotlinUnitTest",
        expectedTests = setOf(
            "java:test://com.example.app.AppKotlinUnitTest/aarDependencies",
            "java:test://com.example.app.AppKotlinUnitTest/assertions",
            "java:test://com.example.app.AppKotlinUnitTest/commonsLogging",
            "java:test://com.example.app.AppKotlinUnitTest/enums",
            "java:test://com.example.app.AppKotlinUnitTest/exceptions",
            "java:test://com.example.app.AppKotlinUnitTest/instanceFields",
            "java:test://com.example.app.AppKotlinUnitTest/javaLibJavaResourcesOnClasspath",
            "java:test://com.example.app.AppKotlinUnitTest/javaResourcesOnClasspath",
            "java:test://com.example.app.AppKotlinUnitTest/libJavaResourcesOnClasspath",
            "java:test://com.example.app.AppKotlinUnitTest/mockFinalClass",
            "java:test://com.example.app.AppKotlinUnitTest/mockFinalMethod",
            "java:test://com.example.app.AppKotlinUnitTest/mockInnerClass",
            "java:test://com.example.app.AppKotlinUnitTest/prodJavaResourcesOnClasspath",
            "java:test://com.example.app.AppKotlinUnitTest/prodRClass",
            "java:test://com.example.app.AppKotlinUnitTest/referenceJavaLibJavaClass",
            "java:test://com.example.app.AppKotlinUnitTest/referenceJavaLibKotlinClass",
            "java:test://com.example.app.AppKotlinUnitTest/referenceLibraryCode",
            "java:test://com.example.app.AppKotlinUnitTest/referenceLibraryKotlinCode",
            "java:test://com.example.app.AppKotlinUnitTest/referenceProductionCode",
            "java:test://com.example.app.AppKotlinUnitTest/referenceProductionKotlinCode",
            "java:test://com.example.app.AppKotlinUnitTest/workingDir"
        )
    )
  }

  fun testLibModule() {
    checkTestClass(
        "com.example.lib.LibJavaUnitTest",
        expectedTests = setOf(
            "java:test://com.example.lib.LibJavaUnitTest/aarDependencies",
            "java:test://com.example.lib.LibJavaUnitTest/assertions",
            "java:test://com.example.lib.LibJavaUnitTest/commonsLogging",
            "java:test://com.example.lib.LibJavaUnitTest/enums",
            "java:test://com.example.lib.LibJavaUnitTest/exceptions",
            "java:test://com.example.lib.LibJavaUnitTest/instanceFields",
            "java:test://com.example.lib.LibJavaUnitTest/javaLibJavaResourcesOnClasspath",
            "java:test://com.example.lib.LibJavaUnitTest/javaResourcesOnClasspath",
            "java:test://com.example.lib.LibJavaUnitTest/libJavaResourcesOnClasspath",
            "java:test://com.example.lib.LibJavaUnitTest/mockFinalClass",
            "java:test://com.example.lib.LibJavaUnitTest/mockFinalMethod",
            "java:test://com.example.lib.LibJavaUnitTest/mockInnerClass",
            "java:test://com.example.lib.LibJavaUnitTest/prodJavaResourcesOnClasspath",
            "java:test://com.example.lib.LibJavaUnitTest/prodRClass",
            "java:test://com.example.lib.LibJavaUnitTest/referenceJavaLibJavaClass",
            "java:test://com.example.lib.LibJavaUnitTest/referenceJavaLibKotlinClass",
            "java:test://com.example.lib.LibJavaUnitTest/referenceLibraryCode",
            "java:test://com.example.lib.LibJavaUnitTest/referenceLibraryKotlinCode",
            "java:test://com.example.lib.LibJavaUnitTest/referenceProductionCode",
            "java:test://com.example.lib.LibJavaUnitTest/referenceProductionKotlinCode",
            "java:test://com.example.lib.LibJavaUnitTest/workingDir"
        )
    )

    checkTestClass(
        "com.example.lib.LibKotlinUnitTest",
        expectedTests = setOf(
            "java:test://com.example.lib.LibKotlinUnitTest/aarDependencies",
            "java:test://com.example.lib.LibKotlinUnitTest/assertions",
            "java:test://com.example.lib.LibKotlinUnitTest/commonsLogging",
            "java:test://com.example.lib.LibKotlinUnitTest/enums",
            "java:test://com.example.lib.LibKotlinUnitTest/exceptions",
            "java:test://com.example.lib.LibKotlinUnitTest/instanceFields",
            "java:test://com.example.lib.LibKotlinUnitTest/javaLibJavaResourcesOnClasspath",
            "java:test://com.example.lib.LibKotlinUnitTest/javaResourcesOnClasspath",
            "java:test://com.example.lib.LibKotlinUnitTest/libJavaResourcesOnClasspath",
            "java:test://com.example.lib.LibKotlinUnitTest/mockFinalClass",
            "java:test://com.example.lib.LibKotlinUnitTest/mockFinalMethod",
            "java:test://com.example.lib.LibKotlinUnitTest/mockInnerClass",
            "java:test://com.example.lib.LibKotlinUnitTest/prodJavaResourcesOnClasspath",
            "java:test://com.example.lib.LibKotlinUnitTest/prodRClass",
            "java:test://com.example.lib.LibKotlinUnitTest/referenceJavaLibJavaClass",
            "java:test://com.example.lib.LibKotlinUnitTest/referenceJavaLibKotlinClass",
            "java:test://com.example.lib.LibKotlinUnitTest/referenceLibraryCode",
            "java:test://com.example.lib.LibKotlinUnitTest/referenceLibraryKotlinCode",
            "java:test://com.example.lib.LibKotlinUnitTest/referenceProductionCode",
            "java:test://com.example.lib.LibKotlinUnitTest/referenceProductionKotlinCode",
            "java:test://com.example.lib.LibKotlinUnitTest/workingDir"
        )
    )
  }

  fun testJavaLibModule() {
    checkTestClass(
        "com.example.javalib.JavaLibJavaTest",
        expectedTests = setOf(
            "java:test://com.example.javalib.JavaLibJavaTest/assertions",
            "java:test://com.example.javalib.JavaLibJavaTest/javaResourcesOnClasspath",
            "java:test://com.example.javalib.JavaLibJavaTest/prodJavaResourcesOnClasspath",
            "java:test://com.example.javalib.JavaLibJavaTest/referenceJavaLibJavaClass",
            "java:test://com.example.javalib.JavaLibJavaTest/referenceJavaLibKotlinClass",
            "java:test://com.example.javalib.JavaLibJavaTest/workingDir"
        )
    )
    // TODO(b/64667992): check JavaLibKotlinTest once the Kotlin setup works.
  }

  private fun checkTestClass(className: String, expectedTests: Set<String>) {
    val passingTests = ConcurrentSkipListSet<String>()
    val failingTests = ConcurrentSkipListSet<String>()
    val testingFinished = CountDownLatch(1)
    val failure = AtomicReference<Throwable>()

    val tracker = TestUsageTracker(VirtualTimeScheduler())
    UsageTracker.setWriterForTest(tracker)

    runInEdtAndWait {
      try {
        val busConnection = myFixture.project.messageBus.connect(project)

        busConnection.subscribe(SMTRunnerEventsListener.TEST_STATUS, object : SMTRunnerEventsAdapter() {
          override fun onTestingStarted(testsRoot: SMTestProxy.SMRootTestProxy) {
            log("Testing $className started.")
          }

          override fun onTestStarted(test: SMTestProxy) {
            log("${test.name} started")
          }

          override fun onTestFinished(test: SMTestProxy) {
            log("${test.name} finished")
            val set = if (test.isPassed) passingTests else failingTests
            set.add(test.locationUrl ?: return)
          }

          override fun onTestingFinished(testsRoot: SMTestProxy.SMRootTestProxy) {
            log("Testing $className finished.")
            testingFinished.countDown()
            busConnection.disconnect()
          }
        })

        log("Running $className")
        ExecutionUtil.runConfiguration(
            createContext(project, myFixture.findClass(className)).configuration!!,
            DefaultRunExecutor.getRunExecutorInstance()
        )
      }
      catch (t: Throwable) {
        log("failed!")
        failure.set(t)
        testingFinished.countDown()
      }
    }

    // Make sure we don't hang the entire build here.
    assertTrue("Timed out", testingFinished.await(1, TimeUnit.MINUTES))
    UsageTracker.cleanAfterTesting()

    failure.get()?.let { throw it }

    log("Checking $className")
    assertThat(passingTests).containsExactlyElementsIn(expectedTests)
    assertThat(failingTests).isEmpty()

    val events = tracker.usages
      .map { it.studioEvent }
      .filter { it.kind == AndroidStudioEvent.EventKind.TEST_RUN }

    assertThat(events).hasSize(1)
    val testRun = events.single().testRun

    assertThat(testRun.testInvocationType).isEqualTo(TestRun.TestInvocationType.ANDROID_STUDIO_TEST)
    assertThat(testRun.testKind).isEqualTo(TestRun.TestKind.UNIT_TEST)
    assertThat(testRun.numberOfTestsExecuted).isEqualTo(expectedTests.size)
    assertThat(testRun.testLibraries.mockitoVersion).isEqualTo("2.7.1")
  }
}
