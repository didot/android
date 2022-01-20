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
package com.android.tools.idea.logcat.actions

import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.adtui.swing.popup.FakeJBPopup.ShowStyle.SHOW_UNDERNEATH_OF
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.logcat.FakeLogcatPresenter
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Tests for [LogcatFormatAction]
 */
@RunsInEdt
class LogcatFormatActionTest {
  private val projectRule = ProjectRule()

  private val popupRule = JBPopupRule()

  @get:Rule
  val rule = RuleChain(projectRule, popupRule)

  private val fakeLogcatPresenter = FakeLogcatPresenter()

  @Before
  fun setUp() {
    enableHeadlessDialogs(projectRule.project)
  }

  @Test
  fun presentation() {
    val action = LogcatFormatAction(projectRule.project, fakeLogcatPresenter)

    assertThat(action.templatePresentation.text).isNull()
    assertThat(action.templatePresentation.description).isEqualTo("Configure Logcat formatting options")
    assertThat(action.templatePresentation.icon).isSameAs(AllIcons.Actions.Properties)
  }

  @Test
  fun actionPerformed() {
    val action = LogcatFormatAction(projectRule.project, fakeLogcatPresenter)
    val component = JPanel()

    action.actionPerformed(anActionEvent(component))

    val popup = popupRule.fakePopupFactory.getPopup<AnAction>(0)
    assertThat(popup.items.map { it::class })
      .containsExactly(
        LogcatFormatPresetAction.Standard::class,
        LogcatFormatPresetAction.Compact::class,
        LogcatFormatCustomViewAction::class,
      ).inOrder()
    assertThat(popup.showStyle).isEqualTo(SHOW_UNDERNEATH_OF)
    assertThat(popup.showArgs).containsExactly(component)
  }
}

private fun anActionEvent(component: JComponent) =
  AnActionEvent(MouseEvent(component, 0, 0L, 0, 0, 0, 1, true), EMPTY_CONTEXT, "place", Presentation(), ActionManager.getInstance(), 0)