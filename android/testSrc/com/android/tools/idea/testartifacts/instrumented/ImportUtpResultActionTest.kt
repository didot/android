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

import com.android.flags.junit.RestoreFlagRule
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.DisposableRule

@RunWith(JUnit4::class)
@RunsInEdt
class ImportUtpResultActionTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)
    .around(RestoreFlagRule(StudioFlags.UTP_TEST_RESULT_SUPPORT))

  private val importUtpResultAction = ImportUtpResultAction()

  @org.junit.Ignore("b/155928822")
  @Test
  fun importUtpResults() {
    importUtpResultAction.parseResultsAndDisplay("".byteInputStream(), disposableRule.disposable, projectRule.project)
    val toolWindow = ToolWindowManager.getInstance(projectRule.project).getToolWindow(ToolWindowId.RUN)!!
    assertThat(toolWindow.contentManager.contents).isNotEmpty()
  }

  @org.junit.Ignore("b/155928822")
  @Test
  fun importUtpResultPreCreateContentManager() {
    RunContentManager.getInstance(projectRule.project)
    val toolWindow = ToolWindowManager.getInstance(projectRule.project).getToolWindow(ToolWindowId.RUN)!!
    assertThat(toolWindow.contentManager.contents).isEmpty()
    importUtpResultAction.parseResultsAndDisplay("".byteInputStream(), disposableRule.disposable, projectRule.project)
    assertThat(toolWindow.contentManager.contents).isNotEmpty()
  }

  @Test
  fun enableUtpResultSupport() {
    StudioFlags.UTP_TEST_RESULT_SUPPORT.override(true)
    val anActionEvent = AnActionEvent(null, DataContext{ projectRule.project },
                                      ActionPlaces.UNKNOWN, Presentation(),
                                      ActionManager.getInstance(), 0)
    ActionManager.getInstance().getAction("ImportUtpResultAction").update(anActionEvent)
    assertThat(anActionEvent.presentation.isEnabled).isTrue()
  }

  @Test
  fun defaultDisableUtpResultSupport() {
    val anActionEvent = AnActionEvent(null, DataContext{ projectRule.project },
                                      ActionPlaces.UNKNOWN, Presentation(),
                                      ActionManager.getInstance(), 0)
    ActionManager.getInstance().getAction("ImportUtpResultAction").update(anActionEvent)
    assertThat(anActionEvent.presentation.isEnabled).isFalse()
  }
}