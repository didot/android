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
package com.android.tools.idea.compose.gradle.preview

import com.android.tools.idea.editors.build.ProjectBuildStatusManagerTest
import com.android.flags.junit.SetFlagRule
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.editors.build.ProjectBuildStatusManager
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executor

class ProjectBuildStatusManagerTest {
  @get:Rule
  val edtRule = EdtRule()

  @get:Rule
  val fastPreviewFlagRule = SetFlagRule(StudioFlags.COMPOSE_FAST_PREVIEW, false)

  @get:Rule
  val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)
  val project: Project
    get() = projectRule.project

  @Before
  fun setup() {
    LiveEditApplicationConfiguration.getInstance().mode = LiveEditApplicationConfiguration.LiveEditMode.LIVE_LITERALS
  }

  @After
  fun tearDown() {
    LiveEditApplicationConfiguration.getInstance().resetDefault()
  }

  @RunsInEdt
  @Test
  fun testProjectStatusManagerStates() {
    val mainFile = projectRule.project.guessProjectDir()!!
      .findFileByRelativePath(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)!!
    WriteAction.run<Throwable> {
      projectRule.fixture.openFileInEditor(mainFile)
    }

    val statusManager = ProjectBuildStatusManager.create(
      projectRule.fixture.testRootDisposable,
      projectRule.fixture.file,
      scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()))
    assertTrue("Project must compile correctly", projectRule.build().isBuildSuccessful)
    assertTrue("Builds status is not Ready after successful build", statusManager.status == ProjectStatus.Ready)

    // Status of files created after a build should be NeedsBuild until a new build happens
    val newFile = projectRule.fixture.addFileToProject("${SimpleComposeAppPaths.APP_SIMPLE_APPLICATION_DIR}/newFile", "")
    val newStatusManager = ProjectBuildStatusManager.create(
      projectRule.fixture.testRootDisposable,
      newFile,
      scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()))
    assertEquals(ProjectStatus.NeedsBuild, newStatusManager.status)
    projectRule.buildAndAssertIsSuccessful()
    assertEquals(ProjectStatus.Ready, newStatusManager.status)

    // Status should change to OutOfDate when introducing a change, only for the manager of the modified file
    val documentManager = PsiDocumentManager.getInstance(projectRule.project)
    WriteCommandAction.runWriteCommandAction(project) {
      documentManager.getDocument(projectRule.fixture.file)!!.insertString(0, "// A change")
      documentManager.commitAllDocuments()
    }
    FileDocumentManager.getInstance().saveAllDocuments()
    assertEquals(ProjectStatus.OutOfDate, statusManager.status)
    assertEquals(ProjectStatus.Ready, newStatusManager.status)

    // Status should change to NeedsBuild for all managers after a build clean
    projectRule.clean()
    assertEquals(ProjectStatus.NeedsBuild, statusManager.status)
    assertEquals(ProjectStatus.NeedsBuild, newStatusManager.status)
  }

  @RunsInEdt
  @Test
  fun testProjectStatusManagerStatesFailureModes() {
    val mainFile = projectRule.project.guessProjectDir()!!
      .findFileByRelativePath(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)!!

    val documentManager = PsiDocumentManager.getInstance(projectRule.project)

    // Force clean
    projectRule.clean()
    WriteCommandAction.runWriteCommandAction(project) {
      projectRule.fixture.openFileInEditor(mainFile)

      // Break the compilation
      documentManager.getDocument(projectRule.fixture.file)!!.insertString(0, "<<Invalid>>")
      documentManager.commitAllDocuments()
    }
    FileDocumentManager.getInstance().saveAllDocuments()

    val statusManager = ProjectBuildStatusManager.create(
      projectRule.fixture.testRootDisposable,
      projectRule.fixture.file,
      scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()))
    assertEquals(ProjectStatus.NeedsBuild, statusManager.status)
    assertFalse(projectRule.build().isBuildSuccessful)
    assertEquals(ProjectStatus.NeedsBuild, statusManager.status)

    WriteCommandAction.runWriteCommandAction(project) {
      // Fix the build
      documentManager.getDocument(projectRule.fixture.file)!!.deleteString(0, "<<Invalid>>".length)
      documentManager.commitAllDocuments()
    }
    FileDocumentManager.getInstance().saveAllDocuments()

    assertEquals(ProjectStatus.NeedsBuild, statusManager.status)
    projectRule.buildAndAssertIsSuccessful()
    assertTrue("Builds status is not Ready after successful build", statusManager.status == ProjectStatus.Ready)
  }

  @RunsInEdt
  @Test
  fun testFilteringChange() {
    val mainFile = projectRule.project.guessProjectDir()!!
      .findFileByRelativePath(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)!!
    WriteAction.run<Throwable> {
      projectRule.fixture.openFileInEditor(mainFile)
    }

    val fileFilter = ProjectBuildStatusManagerTest.TestFilter()
    val statusManager = ProjectBuildStatusManager.create(
      projectRule.fixture.testRootDisposable,
      projectRule.fixture.file,
      fileFilter,
      scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()))
    projectRule.buildAndAssertIsSuccessful()
    assertEquals("Builds status is not Ready after successful build", ProjectStatus.Ready, statusManager.status)

    var filterWasInvoked = false
    fileFilter.filter = { filterWasInvoked = true; it !is KtLiteralStringTemplateEntry }
    assertEquals(ProjectStatus.Ready, statusManager.status)
    assertFalse("Filter should not have been invoked since change was not notified", filterWasInvoked)
    // Notify the filter update
    fileFilter.incModificationCount()
    assertEquals(ProjectStatus.Ready, statusManager.status)
    assertTrue("Filter should have been re-invoked after the change notification", filterWasInvoked)
  }
}