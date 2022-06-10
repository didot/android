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
package com.android.tools.idea.editors.fast

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.run.deployment.liveedit.runWithCompileLock
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.mock.MockPsiFile
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

private val TEST_VERSION = GradleVersion.parse("0.0.1-test")

private object NopCompilerDaemonClient : CompilerDaemonClient {
  override val isRunning: Boolean = true
  override suspend fun compileRequest(files: Collection<PsiFile>,
                                      module: Module,
                                      outputDirectory: Path,
                                      indicator: ProgressIndicator): CompilationResult = CompilationResult.Success
  override fun dispose() {}
}

fun nopCompileDaemonFactory(onCalled: (String) -> Unit): (String, Project, Logger, CoroutineScope) -> CompilerDaemonClient {
  return { version, _, _, _ ->
    onCalled(version)
    NopCompilerDaemonClient
  }
}

internal class FastPreviewManagerTest {
  val projectRule = AndroidProjectRule.inMemory()
  private val project: Project
    get() = projectRule.project

  @get:Rule
  val chainRule: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(FastPreviewRule())

  private val testTracker = TestFastPreviewTrackerManager()

  @Before
  fun setUp() {
    projectRule.project.replaceService(FastPreviewTrackerManager::class.java, testTracker, projectRule.testRootDisposable)
  }

  @Test
  fun `pre-start daemon`() {
    val createdVersions = mutableListOf<String>()
    val latch = CountDownLatch(1)
    val manager = FastPreviewManager.getTestInstance(project, nopCompileDaemonFactory {
      createdVersions.add(it)
    }, moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    assertTrue(createdVersions.isEmpty())
    manager.preStartDaemon(projectRule.module)
    latch.await(1, TimeUnit.SECONDS)
    assertEquals("0.0.1-test", createdVersions.single())
  }

  @Test
  fun `request starts daemon`() = runBlocking {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val createdVersions = mutableListOf<String>()
    val manager = FastPreviewManager.getTestInstance(project, nopCompileDaemonFactory {
      createdVersions.add(it)
    }, moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    assertTrue(createdVersions.isEmpty())
    // Start 10 requests to ensure only one daemon is started
    coroutineScope {
      repeat(10) {
        launch {
          manager.compileRequest(file, projectRule.module)
        }
      }
    }
    assertEquals("0.0.1-test", createdVersions.single())
  }

  @Test
  fun `identical requests only trigger 1 build`() {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val scope = AndroidCoroutineScope(projectRule.testRootDisposable)
    val blockingDaemon = BlockingDaemonClient()
    val manager = FastPreviewManager.getTestInstance(project,
                                                     daemonFactory = { _, _, _, _ -> blockingDaemon },
                                                     moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }

    // Check that the same requests does not trigger more than one compilation
    val latch = CountDownLatch(10)
    repeat(10) {
      scope.launch {
        manager.compileRequest(file, projectRule.module)
        latch.countDown()
      }
    }
    blockingDaemon.complete()

    latch.await() // Wait for the 10 requests to complete
    assertEquals("Only one compilation was expected for the 10 identical requests", 1, blockingDaemon.requestReceived)
    assertEquals(
      """
        compilationSucceeded: files=1
     """.trimIndent(),
      testTracker.logOutput()
    )
  }

  @Test
  fun `disabled request cache creates new compilations for every request`() {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val scope = AndroidCoroutineScope(projectRule.testRootDisposable)
    val blockingDaemon = BlockingDaemonClient()
    val manager = FastPreviewManager.getTestInstance(project,
                                                     daemonFactory = { _, _, _, _ -> blockingDaemon },
                                                     moduleRuntimeVersionLocator = { TEST_VERSION },
                                                     maxCachedRequests = 0).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }

    // Check that the same requests does not trigger more than one compilation
    val latch = CountDownLatch(10)
    repeat(10) {
      scope.launch {
        manager.compileRequest(file, projectRule.module)
        latch.countDown()
      }
    }
    blockingDaemon.complete()

    latch.await() // Wait for the 10 requests to complete
    assertEquals("10 requests should have triggered 10 compilations", 10, blockingDaemon.requestReceived)
  }

  @Test
  fun `request caches does not trigger repeated builds`() {
    val actualFile = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    var modificationCount = 0L

    // Mock file to use so we control when it is signal as "modified".
    val mockFile = object : MockPsiFile(actualFile.virtualFile, actualFile.manager) {
      override fun getModificationStamp(): Long = modificationCount
    }
    val blockingDaemon = BlockingDaemonClient()
    val manager = FastPreviewManager.getTestInstance(project,
                                                     daemonFactory = { _, _, _, _ -> blockingDaemon },
                                                     moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    val scope = AndroidCoroutineScope(projectRule.testRootDisposable)

    val latch = CountDownLatch(10)
    scope.launch {
      repeat(10) {
        if (it % 2 == 0) { // Only change the file 5 times
          modificationCount++
        }
        manager.compileRequest(mockFile, projectRule.module)

        latch.countDown()
      }
    }
    blockingDaemon.complete()

    latch.await() // Wait for the 10 requests to complete
    assertEquals("Only 5 requests were expected to be different", 5, blockingDaemon.requestReceived)
  }

  @Test
  fun `verify compiler request`() = runBlocking {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val compilationRequests = mutableListOf<List<String>>()
    val manager = FastPreviewManager.getTestInstance(
      project,
      daemonFactory = { _, _, _, _ ->
        object : CompilerDaemonClient by NopCompilerDaemonClient {
          override suspend fun compileRequest(files: Collection<PsiFile>,
                                              module: Module,
                                              outputDirectory: Path,
                                              indicator: ProgressIndicator): CompilationResult {
            compilationRequests.add(files.map { it.virtualFile.path }.toList()
                                    + module.name
                                    + listOf(outputDirectory.toString()))
            return CompilationResult.Success
          }
        }
      },
      moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    assertTrue(compilationRequests.isEmpty())
    assertTrue(manager.compileRequest(file, projectRule.module).first == CompilationResult.Success)
    run {
      val requestParameters = compilationRequests.single().joinToString("\n")
        .replace(Regex("\n.*overlay\\d+"), "\n/tmp/overlay0") // Overlay directories are random
      assertEquals("""
      /src/test.kt
      light_idea_test_case
      /tmp/overlay0
    """.trimIndent(), requestParameters)
    }

    // Check, disabling Live Literals disables the compiler flag.
    run {
      compilationRequests.clear()
      LiveEditApplicationConfiguration.getInstance().mode = LiveEditApplicationConfiguration.LiveEditMode.DISABLED
      try {
        val file2 = projectRule.fixture.addFileToProject("testB.kt", """
          fun emptyB() {}
        """.trimIndent())
        assertTrue(manager.compileRequest(listOf(file2), projectRule.module).first == CompilationResult.Success)
        val requestParameters = compilationRequests.single().joinToString("\n")
          .replace(Regex("\n.*overlay\\d+"), "\n/tmp/overlay0") // Overlay directories are random
        assertEquals("""
        /src/testB.kt
        light_idea_test_case
        /tmp/overlay0
      """.trimIndent(), requestParameters)
      }
      finally {
        LiveEditApplicationConfiguration.getInstance().resetDefault()
      }
    }

    run {
      compilationRequests.clear()
      val file2 = projectRule.fixture.addFileToProject("testC.kt", """
      fun emptyC() {}
    """.trimIndent())
      assertTrue(manager.compileRequest(listOf(file, file2), projectRule.module).first == CompilationResult.Success)
      val requestParameters = compilationRequests.single().joinToString("\n")
        .replace(Regex("\n.*overlay\\d+"), "\n/tmp/overlay0") // Overlay directories are random
      assertEquals("""
      /src/test.kt
      /src/testC.kt
      light_idea_test_case
      /tmp/overlay0
    """.trimIndent(), requestParameters)
    }
  }

  @Test
  fun `handle daemon start exception`() = runBlocking {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val manager = FastPreviewManager.getTestInstance(
      project,
      daemonFactory = { _, _, _, _ ->
        throw IllegalStateException("Unable to start compiler")
      },
      moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    val result = manager.compileRequest(file, projectRule.module).first
    assertTrue(result.toString(), result is CompilationResult.DaemonStartFailure)
  }

  @Test
  fun `handle compile request exception`() = runBlocking {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val manager = FastPreviewManager.getTestInstance(
      project,
      daemonFactory = { _, _, _, _ ->
        object : CompilerDaemonClient by NopCompilerDaemonClient {
          override suspend fun compileRequest(files: Collection<PsiFile>,
                                              module: Module,
                                              outputDirectory: Path,
                                              indicator: ProgressIndicator): CompilationResult {
            throw IllegalStateException("Unable to process request")
          }
        }
      },
      moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    val result = manager.compileRequest(file, projectRule.module).first
    assertTrue(result.toString(), result is CompilationResult.RequestException)
  }

  @Test
  fun `handle compile failure`() = runBlocking {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val manager = FastPreviewManager.getTestInstance(
      project,
      daemonFactory = { _, _, _, _ ->
        object : CompilerDaemonClient by NopCompilerDaemonClient {
          override suspend fun compileRequest(files: Collection<PsiFile>,
                                              module: Module,
                                              outputDirectory: Path,
                                              indicator: ProgressIndicator): CompilationResult = CompilationResult.DaemonError(-1)
        }
      },
      moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    val result = manager.compileRequest(file, projectRule.module).first
    assertTrue(result.toString(), result is CompilationResult.DaemonError)
  }

  @Test
  fun `auto disable on failure`(): Unit = runBlocking {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val manager = FastPreviewManager.getTestInstance(
      project,
      daemonFactory = { _, _, _, _ ->
        object : CompilerDaemonClient by NopCompilerDaemonClient {
          override suspend fun compileRequest(files: Collection<PsiFile>,
                                              module: Module,
                                              outputDirectory: Path,
                                              indicator: ProgressIndicator): CompilationResult {
            throw IllegalStateException("Unable to process request")
          }
        }
      },
      moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    assertNull(manager.disableReason)
    assertTrue(manager.isEnabled)
    manager.compileRequest(file, projectRule.module).first.also { result ->
      assertTrue(result.toString(), result is CompilationResult.RequestException)
      assertFalse("FastPreviewManager should have been disable after a failure", manager.isEnabled)
      assertTrue("Auto disable should not be persisted", LiveEditApplicationConfiguration.getInstance().liveEditPreviewEnabled)
      assertEquals(
        "DisableReason(title=Unable to compile using Preview Live Edit, description=Unable to process request, throwable=java.lang.IllegalStateException: Unable to process request)",
        manager.disableReason.toString())
      manager.enable()
      assertNull(manager.disableReason)
    }

    manager.allowAutoDisable = false
    // Repeat the failure but set autoDisable to false
    manager.compileRequest(file, projectRule.module).first.also { result ->
      assertTrue(result.toString(), result is CompilationResult.RequestException)
      assertTrue(manager.isEnabled)
      assertTrue(LiveEditApplicationConfiguration.getInstance().liveEditPreviewEnabled)
      assertNull(manager.disableReason)
    }
    assertEquals(
      """
        autoDisabled
        compilationFailed: files=1
        userEnabled
     """.trimIndent(),
      testTracker.logOutput()
    )
  }

  @Test
  fun `do not auto disable on syntax error`(): Unit = runBlocking {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty) { // Syntax error
    """.trimIndent())
    // Utility method that allows to optionally wrap the exception before throwing it
    var wrapAndThrow: (Throwable) -> Unit = {
      throw it
    }
    val manager = FastPreviewManager.getTestInstance(
      project,
      daemonFactory = { _, _, _, _ ->
        object : CompilerDaemonClient by NopCompilerDaemonClient {
          override suspend fun compileRequest(files: Collection<PsiFile>,
                                              module: Module,
                                              outputDirectory: Path,
                                              indicator: ProgressIndicator): CompilationResult {
            val inputs = files.filterIsInstance<KtFile>()
            assertTrue(inputs.isNotEmpty())
            runReadAction {
              runWithCompileLock {
                // Simulate a syntax error compilation syntax error
                val resolution = fetchResolution(project, inputs)
                try {
                  analyze(inputs, resolution)
                } catch (t: Throwable) {
                  wrapAndThrow(t)
                }
              }
            }
            throw IllegalStateException("Not reachable")
          }
        }
      },
      moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    assertNull(manager.disableReason)
    assertTrue(manager.isEnabled)
    manager.compileRequest(file, projectRule.module).first.also { result ->
      assertTrue(result.toString(), result is CompilationResult.RequestException)
      assertTrue("FastPreviewManager should remain enabled after a syntax error", manager.isEnabled)
      assertNull(manager.disableReason)
    }

    wrapAndThrow = {
      throw ExecutionException(it)
    }
    manager.invalidateRequestsCache()
    manager.compileRequest(file, projectRule.module).first.also { result ->
      assertTrue(result.toString(), result is CompilationResult.RequestException)
      assertTrue("FastPreviewManager should remain enabled after a syntax error", manager.isEnabled)
      assertNull(manager.disableReason)
    }
    assertEquals(
      """
        compilationFailed: files=1
        compilationFailed: files=1
     """.trimIndent(),
      testTracker.logOutput()
    )
  }

  // Regression test for http://b/222838793
  @Test
  fun `verify listener parent disposable`() {
    val manager = FastPreviewManager.getTestInstance(project,
                                                     daemonFactory = { _, _, _, _ -> NopCompilerDaemonClient },
                                                     moduleRuntimeVersionLocator = { TEST_VERSION }).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
    val parentDisposable = Disposer.newDisposable()
    manager.addCompileListener(parentDisposable, object: FastPreviewManager.Companion.CompileListener {
      override fun onCompilationStarted(files: Collection<PsiFile>) {}
      override fun onCompilationComplete(result: CompilationResult, files: Collection<PsiFile>) {}
    })
    Disposer.dispose(parentDisposable)
    assertFalse(manager.isDisposed)
  }

  @Test
  fun `compiling state is true while processing a request`() {
    val file = projectRule.fixture.addFileToProject("test.kt", """
      fun empty() {}
    """.trimIndent())
    val scope = AndroidCoroutineScope(projectRule.testRootDisposable)
    val blockingDaemon = BlockingDaemonClient()
    val manager = FastPreviewManager.getTestInstance(project,
                                                     daemonFactory = { _, _, _, _ -> blockingDaemon },
                                                     moduleRuntimeVersionLocator = { TEST_VERSION },
                                                     maxCachedRequests = 0).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }

    val compilationComplete = CompletableDeferred<Unit>()
    assertFalse(manager.isCompiling)
    scope.launch {
      manager.compileRequest(file, projectRule.module)
      compilationComplete.complete(Unit)
    }
    runBlocking {
      blockingDaemon.firstRequestReceived.await()
      assertTrue(manager.isCompiling)
      blockingDaemon.complete()
      compilationComplete.await()
    }
    assertFalse(manager.isCompiling)
  }
}