/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.gradle.fast

import com.android.flags.junit.SetFlagRule
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.editors.fast.CompilationResult
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.isError
import com.android.tools.idea.editors.fast.isSuccess
import com.android.tools.idea.editors.fast.toFileNameSet
import com.android.tools.idea.editors.literals.LiteralUsageReference
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.LIVE_LITERALS
import com.android.tools.idea.editors.literals.LiveLiteralsMonitorHandler
import com.android.tools.idea.editors.literals.LiveLiteralsService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.classloading.ProjectConstantRemapper
import com.android.tools.idea.testing.moveCaret
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.kotlin.name.FqName
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LiveLiteralsAndFastPreviewIntegrationTest {
  @get:Rule
  val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  @get:Rule
  val fastPreviewFlagRule = SetFlagRule(StudioFlags.COMPOSE_FAST_PREVIEW, true)

  lateinit var psiMainFile: PsiFile
  lateinit var fastPreviewManager: FastPreviewManager

  @Before
  fun setUp() {
    LiveEditApplicationConfiguration.getInstance().mode = LIVE_LITERALS
    val mainFile = projectRule.project.guessProjectDir()!!
      .findFileByRelativePath(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)!!
    psiMainFile = runReadAction { PsiManager.getInstance(projectRule.project).findFile(mainFile)!! }
    fastPreviewManager = FastPreviewManager.getInstance(projectRule.project)
  }

  @After
  fun tearDown() {
    runBlocking {
      fastPreviewManager.stopAllDaemons().join()
    }
    LiveEditApplicationConfiguration.getInstance().resetDefault()
  }

  /**
   * Runs [runnable] and ensures that a document was added to the tracking of the [liveLiteralsService] after the call.
   */
  private fun runAndWaitForDocumentAdded(liveLiteralsService: LiveLiteralsService, runnable: () -> Unit) {
    val documentAdded = CountDownLatch(1)
    val disposable = Disposer.newDisposable(projectRule.fixture.testRootDisposable, "DocumentAddDiposable")
    DumbService.getInstance(projectRule.project).waitForSmartMode()
    try {
      liveLiteralsService.addOnDocumentsUpdatedListener(disposable) {
        documentAdded.countDown()
      }
      runnable()
      // We wait for a maximum of 20 seconds. Finding literals runs in a non-blocking action. During tests
      // indexing can be triggered which can delay the test for a few seconds. Reducing this number will cause
      // the test to become flaky because of some cases where indexing interferes with the test.
      documentAdded.await(20, TimeUnit.SECONDS)
    }
    finally {
      Disposer.dispose(disposable) // Remove listener
    }
  }

  private suspend fun compileAndListOutputFiles() = withContext(AndroidDispatchers.workerThread) {
    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    fastPreviewManager.compileRequest(psiMainFile, module).let { (result, outputPath) ->
      assertEquals(CompilationResult.Success, result)
      assertTrue(result.isSuccess)
      assertFalse(result.isError)
      ModuleClassLoaderOverlays.getInstance(module).overlayPath = File(outputPath).toPath()

      result to File(outputPath).toPath().toFileNameSet()
    }
  }

  @Ignore("b/161091273") // This test is flaky
  @Test
  fun `verify literals in overlay file`() = runBlocking {
    val liveLiteralsService = LiveLiteralsService.getInstance(projectRule.project)
    val (_, outputFiles) = compileAndListOutputFiles()
    assertTrue(outputFiles.any { it.contains("LiveLiterals") })
    assertTrue(liveLiteralsService.allConstants().isEmpty())
    assertFalse(liveLiteralsService.isAvailable)
    runAndWaitForDocumentAdded(liveLiteralsService) {
      liveLiteralsService.liveLiteralsMonitorStarted("TestDevice", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }
    Logger.getInstance(LiveLiteralsAndFastPreviewIntegrationTest::class.java).warn("After update")
    assertTrue(liveLiteralsService.isAvailable)
    assertEquals(6, liveLiteralsService.allConstants().size)

    liveLiteralsService.liveLiteralsMonitorStopped("TestDevice")

    runWriteActionAndWait {
      projectRule.fixture.moveCaret("Text(\"Hello 2\")|")
      // Add a new literal
      projectRule.fixture.type("\nText(\"Hello 3\")|")
    }
    compileAndListOutputFiles()
    runAndWaitForDocumentAdded(liveLiteralsService) {
      liveLiteralsService.liveLiteralsMonitorStarted("TestDevice", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }
    assertEquals(7, liveLiteralsService.allConstants().size)
  }

  @Test
  fun `disabled live literals does not generate LiveLiterals classes`() = runBlocking {
    LiveEditApplicationConfiguration.getInstance().mode = LIVE_LITERALS
    val (_, outputFiles) = compileAndListOutputFiles()
    assertTrue(outputFiles.isNotEmpty() && outputFiles.none { it.endsWith(".class") && it.contains("LiveLiterals") })
  }

  @Test
  fun `verify live literals constants are cleared`() = runBlocking {
    ProjectConstantRemapper.getInstance(projectRule.project).addConstant(
      null, LiteralUsageReference(FqName("test.constant"), "filename.kt", TextRange(1, 10), 10), 0, 0)
    assertTrue(ProjectConstantRemapper.getInstance(projectRule.project).hasConstants())
    val (result, outputFiles) = compileAndListOutputFiles()
    assertTrue(result == CompilationResult.Success)
    assertFalse("Constants should have been cleared after a successful build",
                ProjectConstantRemapper.getInstance(projectRule.project).hasConstants())
  }
}