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
package com.android.tools.idea.imports

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.override
import com.android.tools.idea.testing.getIntentionAction
import com.android.tools.idea.testing.highlightedAs
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.dom.inspections.AndroidUnresolvableTagInspection
import org.jetbrains.android.refactoring.setAndroidxProperties
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Tests for functions defined in `AnalyticsUtils.kt`.
 */
@RunsInEdt
class AnalyticsUtilsKtTest {
  private val projectRule = AndroidProjectRule.onDisk()
  private lateinit var tracker: TestUsageTracker

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  @Before
  fun setUp() {
    StudioFlags.ENABLE_AUTO_IMPORT.override(true, projectRule.fixture.testRootDisposable)
    tracker = TestUsageTracker(VirtualTimeScheduler())
    UsageTracker.setWriterForTest(tracker)
    WriteCommandAction.runWriteCommandAction(projectRule.project) { projectRule.project.setAndroidxProperties("true") }
  }

  @After
  fun tearDown() {
    tracker.close()
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun verifyExpectedAnalytics_resolveCode() {
    projectRule.fixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val view = RecyclerView() // Here RecyclerView is an unresolvable symbol
      """.trimIndent())

    val action = AndroidMavenImportIntentionAction()
    val element = projectRule.fixture.moveCaret("RecyclerView|")

    action.perform(projectRule.project, projectRule.fixture.editor, element, false)
    verify("androidx.recyclerview:recyclerview")
  }

  @Test
  fun verifyExpectedAnalytics_resolveXmlTag() {
    projectRule.fixture.enableInspections(AndroidUnresolvableTagInspection::class.java)
    val psiFile = projectRule.fixture.addFileToProject(
      "res/layout/my_layout.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <${
        "androidx.recyclerview.widget.RecyclerView".highlightedAs(HighlightSeverity.ERROR,
                                                                  "Cannot resolve class androidx.recyclerview.widget.RecyclerView")
      } />
      """.trimIndent()
    )

    projectRule.fixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    projectRule.fixture.checkHighlighting(true, false, false)
    projectRule.fixture.moveCaret("Recycler|View")
    val action = projectRule.fixture.getIntentionAction("Add dependency on androidx.recyclerview:recyclerview")!!

    WriteCommandAction.runWriteCommandAction(projectRule.project, Runnable {
      action.invoke(projectRule.project, projectRule.fixture.editor, projectRule.fixture.file)
    })

    verify("androidx.recyclerview:recyclerview")
  }

  private fun verify(artifactId: String) {
    val event = tracker.usages
      .map { it.studioEvent }
      .filter { it.kind == AndroidStudioEvent.EventKind.AUTO_IMPORT_EVENT }
      .map { it.autoImportEvent }
      .single()

    assertThat(event.artifactId).isEqualTo(artifactId)
  }
}