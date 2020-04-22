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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * Unit tests for [DetailsViewContentView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class DetailsViewContentViewTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)

  @Test
  fun testNoRetentionView() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project)
    view.setAndroidTestCaseResult(AndroidTestCaseResult.PASSED)
    view.setAndroidDevice(device("device id", "device name"))
    assertThat(view.myRetentionTab.isHidden).isTrue()
  }

  @Test
  fun testWithRetentionView() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project)
    view.setAndroidTestCaseResult(AndroidTestCaseResult.FAILED)
    view.setAndroidDevice(device("device id", "device name"))
    view.setRetentionSnapshot(File("foo"))
    assertThat(view.myRetentionTab.isHidden).isFalse()
  }

  @Test
  fun testResultLabelOnPassing() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project)
    view.setAndroidTestCaseResult(AndroidTestCaseResult.PASSED)
    view.setAndroidDevice(device("device id", "device name"))
    assertThat(view.myTestResultLabel.text)
      .isEqualTo("<html><font color='#6cad74'>Passed</font> on device name</html>")
  }

  @Test
  fun testResultLabelOnFailing() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project)
    view.setAndroidTestCaseResult(AndroidTestCaseResult.FAILED)
    view.setAndroidDevice(device("device id", "device name"))
    assertThat(view.myTestResultLabel.text).isEqualTo(
      "<html><font size='+1'></font><br><font color='#b81708'>Failed</font> on device name</html>")
  }

  @Test
  fun testResultLabelOnRunning() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project)
    view.setAndroidTestCaseResult(AndroidTestCaseResult.IN_PROGRESS)
    view.setAndroidDevice(device("device id", "device name"))
    assertThat(view.myTestResultLabel.text).isEqualTo("Running on device name")
  }

  @Test
  fun logsView() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project)

    view.setLogcat("test logcat message")
    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\n")
  }

  @Test
  fun logsViewWithErrorStackTrace() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project)

    view.setLogcat("test logcat message")
    view.setErrorStackTrace("error stack trace")
    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\nerror stack trace")
  }

  @Test
  fun logsViewShouldClearPreviousMessage() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project)

    view.setLogcat("test logcat message")
    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\n")

    view.setLogcat("test logcat message 2")
    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message 2\n")
  }

  @Test
  fun benchmarkTab() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project)

    view.setBenchmarkText("test benchmark message")
    view.myBenchmarkView.waitAllRequests()

    assertThat(view.myBenchmarkView.text).isEqualTo("test benchmark message")
    assertThat(view.myBenchmarkTab.isHidden).isFalse()
  }

  @Test
  fun benchmarkTabIsHiddenIfNoOutput() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project)

    view.setBenchmarkText("")
    view.myBenchmarkView.waitAllRequests()

    assertThat(view.myBenchmarkView.text).isEqualTo("")
    assertThat(view.myBenchmarkTab.isHidden).isTrue()
  }

  private fun device(id: String, name: String): AndroidDevice {
    return AndroidDevice(id, name, AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(29))
  }
}