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
package com.android.tools.idea.compose.gradle.preview

import com.android.flags.junit.RestoreFlagRule
import com.android.testutils.ImageDiffUtil
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.PreviewElementProvider
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.fast.CompilationResult
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.testing.deleteLine
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.moveCaretLines
import com.android.tools.idea.testing.replaceText
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import kotlin.test.assertFalse

class ComposePreviewRepresentationGradleTest {
  @get:Rule
  val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)
  @get:Rule
  val resetFastPreviewFlag = RestoreFlagRule(StudioFlags.COMPOSE_FAST_PREVIEW)
  private val project: Project
    get() = projectRule.project
  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private lateinit var psiMainFile: PsiFile
  private lateinit var composePreviewRepresentation: ComposePreviewRepresentation
  private lateinit var previewView: TestComposePreviewView
  private lateinit var fakeUi: FakeUi

  @Before
  fun setUp() {
    val mainFile = project.guessProjectDir()!!
      .findFileByRelativePath(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)!!
    psiMainFile = runReadAction { PsiManager.getInstance(project).findFile(mainFile)!! }

    previewView = TestComposePreviewView(fixture.testRootDisposable, project)
    composePreviewRepresentation = ComposePreviewRepresentation(psiMainFile, object : PreviewElementProvider<PreviewElement> {
      override suspend fun previewElements(): Sequence<PreviewElement> =
        AnnotationFilePreviewElementFinder.findPreviewMethods(project, psiMainFile.virtualFile).asSequence()
    }, PreferredVisibility.SPLIT) { _, _, _, _, _, _, _, _ -> previewView }
    Disposer.register(fixture.testRootDisposable, composePreviewRepresentation)

    invokeAndWaitIfNeeded {
      fakeUi = FakeUi(JPanel().apply {
        layout = BorderLayout()
        size = Dimension(1000, 800)
        add(previewView, BorderLayout.CENTER)
      }, 1.0, true)
      fakeUi.root.validate()
    }
    composePreviewRepresentation.onActivate()

    runBlocking {
      composePreviewRepresentation.forceRefresh()!!.join()
      previewView.updateVisibilityAndNotifications()
    }
    waitForRefreshToFinish()
    assertTrue(previewView.hasRendered)
    assertTrue(previewView.hasContent)
    assertTrue(!composePreviewRepresentation.status().isRefreshing)
    assertTrue(!composePreviewRepresentation.status().hasErrors)
    assertTrue(!composePreviewRepresentation.status().hasSyntaxErrors)
    assertTrue(!composePreviewRepresentation.status().isOutOfDate)

    validate()
  }

  /**
   * Wait for any running refreshes to complete.
   */
  private fun waitForRefreshToFinish() = runBlocking {
    // Wait for refresh to finish
    while (composePreviewRepresentation.status().isRefreshing) delay(500)
  }

  /**
   * Finds the render result of the [SceneViewPeerPanel] with the given [name].
   */
  private fun findSceneViewRenderWithName(@Suppress("SameParameterValue") name: String): BufferedImage {
    val sceneViewPanel = fakeUi.findComponent<SceneViewPeerPanel> { it.displayName == name }!!
    return fakeUi.render().getSubimage(
      sceneViewPanel.x, sceneViewPanel.y,
      sceneViewPanel.width, sceneViewPanel.height)
  }

  /**
   * Validates the UI to ensure is up to date.
   */
  private fun validate() =
    invokeAndWaitIfNeeded {
      fakeUi.root.validate()
      previewView.mainSurface.zoomToFit()
      fakeUi.root.validate()
    }

  /**
   * Runs the [runnable]. The [runnable] is expected to trigger a refresh and this method will return once the refresh has
   * happened.
   */
  private fun runAndWaitForRefresh(timeout: Duration = Duration.ofSeconds(40), runnable: () -> Unit) = runBlocking {
    // Wait for any on going refreshes to finish
    waitForRefreshToFinish()
    val onRefreshCompletable = previewView.getOnRefreshCompletable()
    withTimeout(timeout.toMillis()) {
      runnable()
      onRefreshCompletable.await()
    }
    waitForRefreshToFinish()
  }

  /**
   * Builds the project and waits for the preview panel to refresh. It also does zoom to fit.
   */
  private fun buildAndRefresh() {
    runAndWaitForRefresh {
      projectRule.buildAndAssertIsSuccessful()
    }
    validate()
  }

  @Test
  fun `panel renders correctly first time`() {
    assertEquals(
      """
        DefaultPreview
        TwoElementsPreview
        NavigatablePreview
        OnlyATextNavigation
      """.trimIndent(),
      fakeUi.findAllComponents<SceneViewPeerPanel>()
        .filter { it.isShowing }.joinToString("\n") { it.displayName })

    val output = fakeUi.render()

    val defaultPreviewSceneViewPeerPanel = fakeUi.findComponent<SceneViewPeerPanel> { it.displayName == "DefaultPreview" }!!
    val defaultPreviewRender = output.getSubimage(
      defaultPreviewSceneViewPeerPanel.x, defaultPreviewSceneViewPeerPanel.y,
      defaultPreviewSceneViewPeerPanel.width, defaultPreviewSceneViewPeerPanel.height)
    ImageDiffUtil.assertImageSimilar(
      Paths.get("${fixture.testDataPath}/${SIMPLE_COMPOSE_PROJECT_PATH}/defaultRender-withPanel.png"),
      defaultPreviewRender,
      10.0,
      20
    )
  }

  @Test
  fun `removing preview makes it disappear without refresh`() {
    invokeAndWaitIfNeeded {
      fixture.openFileInEditor(psiMainFile.virtualFile)
    }

    runAndWaitForRefresh(Duration.ofSeconds(15)) {
      // Remove the @Preview from the NavigatablePreview
      runWriteActionAndWait {
        fixture.moveCaret("NavigatablePreview|")
        // Move to the line with the annotation
        fixture.editor.moveCaretLines(-2)
        fixture.editor.executeAndSave { fixture.editor.deleteLine() }
        PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }
    invokeAndWaitIfNeeded {
      fakeUi.root.validate()
    }

    assertEquals(
      """
        DefaultPreview
        TwoElementsPreview
        OnlyATextNavigation
      """.trimIndent(),
      fakeUi.findAllComponents<SceneViewPeerPanel>()
        .filter { it.isShowing }.joinToString("\n") { it.displayName })
  }

  @Test
  fun `changes to code are reflected in the preview`() {
    invokeAndWaitIfNeeded {
      fixture.openFileInEditor(psiMainFile.virtualFile)
    }

    val firstRender = findSceneViewRenderWithName("TwoElementsPreview")

    // Make a change to the preview
    runWriteActionAndWait {
      fixture.moveCaret("Text(\"Hello 2\")|")
      fixture.editor.executeAndSave {
        insertText("\nText(\"Hello 3\")\n")
      }
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    assertTrue(composePreviewRepresentation.buildWillTriggerRefresh())
    buildAndRefresh()
    assertFalse(composePreviewRepresentation.needsRefreshOnSuccessfulBuild())

    val secondRender = findSceneViewRenderWithName("TwoElementsPreview")
    assertTrue(
      "Second image expected at least 10% higher but were second=${secondRender.height} first=${firstRender.height}",
      secondRender.height > (firstRender.height * 1.10))
    try {
      ImageDiffUtil.assertImageSimilar("testImage", firstRender, secondRender, 10.0, 20)
      fail("First render and second render are expected to be different")
    }
    catch (_: AssertionError) {
    }

    // Restore to the initial state and verify
    runWriteActionAndWait {
      fixture.editor.executeAndSave {
        replaceText("Text(\"Hello 3\")\n", "")
      }
    }

    buildAndRefresh()

    val thirdRender = findSceneViewRenderWithName("TwoElementsPreview")
    ImageDiffUtil.assertImageSimilar("testImage", firstRender, thirdRender, 10.0, 20)
  }

  @Test
  fun `build clean triggers needs refresh`() {
    assertFalse(composePreviewRepresentation.needsRefreshOnSuccessfulBuild())
    GradleBuildInvoker.getInstance(projectRule.project).cleanProject().get(2, TimeUnit.SECONDS)
    assertTrue(composePreviewRepresentation.needsRefreshOnSuccessfulBuild())
    assertTrue(composePreviewRepresentation.buildWillTriggerRefresh())
  }

  @Test
  fun `updating different file triggers needs refresh`() {
    val otherFile = VfsUtil.findRelativeFile(SimpleComposeAppPaths.APP_OTHER_PREVIEWS.path,
                                             ProjectRootManager.getInstance(projectRule.project).contentRoots[0])!!

    assertFalse(composePreviewRepresentation.needsRefreshOnSuccessfulBuild())

    runWriteActionAndWait {
      projectRule.fixture.openFileInEditor(otherFile)
      projectRule.fixture.moveCaret("Text(\"Line3\")|")
      projectRule.fixture.type("\nText(\"added during test execution\")")
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    assertTrue(composePreviewRepresentation.buildWillTriggerRefresh())
    runBlocking {
      projectRule.buildAndAssertIsSuccessful()
    }
    assertFalse(composePreviewRepresentation.needsRefreshOnSuccessfulBuild())
  }

  @Test
  fun `refresh returns null if ComposePreviewRepresentation is disposed`() {
    var refreshJob = runBlocking { composePreviewRepresentation.forceRefresh(true) }
    assertNotNull(refreshJob)

    runInEdtAndWait { Disposer.dispose(composePreviewRepresentation) }
    refreshJob = runBlocking { composePreviewRepresentation.forceRefresh(true) }
    assertNull(refreshJob)
  }
  @Test
  fun `fast preview request`() {
    StudioFlags.COMPOSE_FAST_PREVIEW.override(true)
    runWriteActionAndWait {
      projectRule.fixture.openFileInEditor(psiMainFile.virtualFile)
      projectRule.fixture.moveCaret("Text(\"Hello 2\")|")
      projectRule.fixture.type("\nText(\"added during test execution\")")
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    runBlocking {
      val result = composePreviewRepresentation.requestFastPreviewRefreshAsync()?.await()
      ?: fail("fast preview refresh request was rejected")

      assertTrue(result is CompilationResult.Success)
    }
  }
}