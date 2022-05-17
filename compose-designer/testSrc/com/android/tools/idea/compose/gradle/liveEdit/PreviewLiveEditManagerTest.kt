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
package com.android.tools.idea.compose.gradle.liveEdit

import com.android.flags.junit.SetFlagRule
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.liveEdit.CompilationResult
import com.android.tools.idea.compose.preview.liveEdit.PreviewLiveEditManager
import com.android.tools.idea.compose.preview.renderer.renderPreviewElement
import com.android.tools.idea.compose.preview.util.SinglePreviewElementInstance
import com.android.tools.idea.concurrency.AndroidDispatchers.ioThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.moveCaret
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes


class PreviewLiveEditManagerTest {
  @get:Rule
  val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)
  @get:Rule
  val liveEditFlagRule = SetFlagRule(StudioFlags.COMPOSE_LIVE_EDIT_PREVIEW, true)
  lateinit var psiMainFile: PsiFile
  lateinit var liveEditManager: PreviewLiveEditManager

  @Before
  fun setUp() {
    val mainFile = projectRule.project.guessProjectDir()!!
      .findFileByRelativePath("app/src/main/java/google/simpleapplication/MainActivity.kt")!!
    psiMainFile = runReadAction { PsiManager.getInstance(projectRule.project).findFile(mainFile)!! }
    liveEditManager = PreviewLiveEditManager.getInstance(projectRule.project)
    invokeAndWaitIfNeeded {
      assertTrue(projectRule.build().isBuildSuccessful)
    }
    runWriteActionAndWait {
      projectRule.fixture.openFileInEditor(mainFile)
      projectRule.fixture.moveCaret("Text(\"Hello 2\")|")
      projectRule.fixture.type("\n")
    }
  }

  @After
  fun tearDown() {
    runBlocking {
      liveEditManager.stopAllDaemons().join()
    }
  }

  @Test
  fun testSingleFileCompileSuccessfully() {
    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    runWriteActionAndWait {
      projectRule.fixture.type("Text(\"Hello 3\")\n")
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    runBlocking {
      val (result, _) = liveEditManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
    }
  }

  @Test
  fun testDaemonIsRestartedAutomatically() {
    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    runWriteActionAndWait {
      projectRule.fixture.type("Text(\"Hello 3\")\n")
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    runBlocking {
      val (result, _) = liveEditManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
      liveEditManager.stopAllDaemons().join()
    }
    runBlocking {
      val (result, _) = liveEditManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
    }
  }

  @Test
  fun testLiveEditChangeRender() {
    val previewElement = SinglePreviewElementInstance.forTesting("google.simpleapplication.MainActivityKt.TwoElementsPreview")
    val initialState = renderPreviewElement(projectRule.androidFacet(":app"), previewElement).get()!!

    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    runWriteActionAndWait {
      projectRule.fixture.type("Text(\"Hello 3\")\n")
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    runBlocking {
      val (result, outputPath) = liveEditManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
      ModuleClassLoaderOverlays.getInstance(module).overlayPath = File(outputPath).toPath()
    }
    val finalState = renderPreviewElement(projectRule.androidFacet(":app"), previewElement).get()!!
    assertTrue(
      "Resulting image is expected to be at least 20% higher since a new text line was added",

      finalState.height > initialState.height * 1.20)
  }

  @Test
  fun testMultipleFilesCompileSuccessfully() {
    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    val psiSecondFile =  runReadAction {
      val vFile = projectRule.project.guessProjectDir()!!
        .findFileByRelativePath("app/src/main/java/google/simpleapplication/OtherPreviews.kt")!!
      PsiManager.getInstance(projectRule.project).findFile(vFile)!!
    }
    runBlocking {
      val (result, outputPath) = liveEditManager.compileRequest(listOf(psiMainFile, psiSecondFile), module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
      val generatedFilesSet = mutableSetOf<String>()
      withContext(ioThread) {
        @Suppress("BlockingMethodInNonBlockingContext")
        Files.walkFileTree(File(outputPath).toPath(), object : SimpleFileVisitor<Path>() {
          override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
            file?.let { generatedFilesSet.add(it?.fileName.toString()) }
            @Suppress("BlockingMethodInNonBlockingContext")
            return super.visitFile(file, attrs)
          }
        })
      }
      assertTrue(generatedFilesSet.contains("OtherPreviewsKt.class"))
    }
  }
}