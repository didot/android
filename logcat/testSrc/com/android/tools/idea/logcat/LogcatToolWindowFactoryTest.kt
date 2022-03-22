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
package com.android.tools.idea.logcat

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig
import com.android.tools.idea.logcat.filters.LogcatFilterColorSettingsPage
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.LogcatColorSettingsPage
import com.android.tools.idea.logcat.messages.TagFormat
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.colors.ColorSettingsPages
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify

@RunsInEdt
class LogcatToolWindowFactoryTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  private val settings = LogcatExperimentalSettings()

  @Before
  fun setUp() {
    ApplicationManager.getApplication().replaceService(LogcatExperimentalSettings::class.java, settings, projectRule.project)
  }

  @Test
  fun isApplicable() {
    assertThat(LogcatToolWindowFactory().isApplicable(projectRule.project)).isTrue()
  }

  @Test
  fun isApplicable_legacy() {
    settings.logcatV2Enabled = false

    assertThat(LogcatToolWindowFactory().isApplicable(projectRule.project)).isFalse()
  }

  @Test
  fun isApplicable_nonAndroidEnvironment() {
    val mockIdeInfo = spy(IdeInfo.getInstance())
    `when`(mockIdeInfo.isAndroidStudio).thenReturn(false)
    ApplicationManager.getApplication().replaceService(IdeInfo::class.java, mockIdeInfo, projectRule.project)

    assertThat(LogcatToolWindowFactory().isApplicable(projectRule.project)).isFalse()
  }

  @Test
  fun generateTabName_noPreExistingNames() {
    assertThat(LogcatToolWindowFactory().generateTabName(emptySet())).isEqualTo("Logcat")
  }

  @Test
  fun generateTabName_defaultNameAlreadyUsed() {
    assertThat(LogcatToolWindowFactory().generateTabName(setOf("Logcat"))).isEqualTo("Logcat (2)")
  }

  @Test
  fun createChildComponent_isLogcatMainPanel() {
    val childComponent = LogcatToolWindowFactory()
      .createChildComponent(projectRule.project, ActionGroup.EMPTY_GROUP, clientState = null)

    assertThat(childComponent).isInstanceOf(LogcatMainPanel::class.java)
    Disposer.dispose(childComponent as Disposable)
  }

  @Test
  fun createChildComponent_parsesState() {
    val logcatPanelConfig = LogcatPanelConfig(
      device = null,
      FormattingConfig.Custom(FormattingOptions(tagFormat = TagFormat(15))),
      "filter",
      isSoftWrap = false)

    val logcatMainPanel = LogcatToolWindowFactory()
      .createChildComponent(projectRule.project, ActionGroup.EMPTY_GROUP, clientState = LogcatPanelConfig.toJson(logcatPanelConfig))

    // It's enough to assert on just one field in the config. We test more thoroughly in LogcatMainPanelTest
    assertThat(logcatMainPanel.formattingOptions).isEqualTo(logcatPanelConfig.formattingConfig.toFormattingOptions())
    Disposer.dispose(logcatMainPanel)
  }

  @Test
  fun createChildComponent_invalidState() {
    val logcatMainPanel = LogcatToolWindowFactory()
      .createChildComponent(projectRule.project, ActionGroup.EMPTY_GROUP, clientState = "invalid state")

    assertThat(logcatMainPanel.formattingOptions).isEqualTo(FormattingOptions())
    Disposer.dispose(logcatMainPanel)
  }

  @Test
  fun colorSettingsPagesRegistration() {
    // We have to use a mock because there is no clean way to clean up ColorSettingsPages
    val mockColorSettingsPages = mock<ColorSettingsPages>()
    ApplicationManager.getApplication().replaceService(ColorSettingsPages::class.java, mockColorSettingsPages, projectRule.project)

    LogcatToolWindowFactory()
    AndroidLogcatToolWindowFactory()

    verify(mockColorSettingsPages).registerPage(any(LogcatColorSettingsPage::class.java))
    verify(mockColorSettingsPages).registerPage(any(LogcatFilterColorSettingsPage::class.java))
    verify(mockColorSettingsPages, never()).registerPage(any(AndroidLogcatColorPage::class.java))
  }

  @Test
  fun colorSettingsPagesRegistration_legacy() {
    // We have to use a mock because there is no clean way to clean up ColorSettingsPages
    val mockColorSettingsPages = mock<ColorSettingsPages>()
    ApplicationManager.getApplication().replaceService(ColorSettingsPages::class.java, mockColorSettingsPages, projectRule.project)
    settings.logcatV2Enabled = false

    LogcatToolWindowFactory()
    AndroidLogcatToolWindowFactory()

    verify(mockColorSettingsPages, never()).registerPage(any(LogcatColorSettingsPage::class.java))
    verify(mockColorSettingsPages, never()).registerPage(any(LogcatFilterColorSettingsPage::class.java))
    verify(mockColorSettingsPages).registerPage(any(AndroidLogcatColorPage::class.java))
  }
}
