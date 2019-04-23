/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.IdeaTestCase
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.File

class BuildOutputParsersIntegrationTest: IdeaTestCase() {
  private val myTaskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, "testId")

  private lateinit var myBuildInvoker: GradleBuildInvoker
  private lateinit var scheduler: VirtualTimeScheduler
  private lateinit var myTracker: TestUsageTracker
  private lateinit var myRequest: GradleBuildInvoker.Request

  @Mock
  private lateinit var myFileDocumentManager: FileDocumentManager
  @Mock
  private lateinit var myTasksExecutor: GradleTasksExecutor
  @Mock
  private lateinit var myDebugSessionFinder: NativeDebugSessionFinder

  override fun setUp() {
    super.setUp()
    MockitoAnnotations.initMocks(this)
    scheduler = VirtualTimeScheduler()
    myTracker = TestUsageTracker(scheduler)
    UsageTracker.setWriterForTest(myTracker)

    myBuildInvoker = GradleBuildInvoker(myProject, myFileDocumentManager,
                                        GradleBuildInvokerTest.GradleTasksExecutorFactoryStub(myTasksExecutor), myDebugSessionFinder)
    myRequest = GradleBuildInvoker.Request(myProject, File(myProject.basePath), emptyList(), myTaskId)
  }

  override fun tearDown() {
    myTracker.close()
    UsageTracker.cleanAfterTesting()
    super.tearDown()
  }

  private fun checkSentMetricsData(sentMetricsData: BuildErrorMessage,
                                   errorType: BuildErrorMessage.ErrorType,
                                   fileType: BuildErrorMessage.FileType,
                                   fileIncluded: Boolean,
                                   lineIncluded: Boolean) {
    assertThat(sentMetricsData).isNotNull()
    assertThat(sentMetricsData.errorShownType).isEquivalentAccordingToCompareTo(errorType)
    assertThat(sentMetricsData.fileIncludedType).isEquivalentAccordingToCompareTo(fileType)
    assertThat(sentMetricsData.fileLocationIncluded).isEqualTo(fileIncluded)
    assertThat(sentMetricsData.lineLocationIncluded).isEqualTo(lineIncluded)
  }

  @Test
  fun testAndroidGradlePluginErrors() {
    val buildListener = myBuildInvoker.createBuildTaskListener(myRequest, "")
    val file = tempDir.createTempFile("styles.xml")
    val output = """Executing tasks: [clean, :app:assembleDebug]
                    > Task :clean UP-TO-DATE
                    > Task :app:clean
                    > Task :app:preBuild UP-TO-DATE
                    > Task :app:extractProguardFiles
                    > Task :app:preDebugBuild
                    > Task :app:checkDebugManifest
                    > Task :app:generateDebugBuildConfig
                    > Task :app:mainApkListPersistenceDebug
                    > Task :app:generateDebugResValues
                    > Task :app:createDebugCompatibleScreenManifests
                    > Task :app:mergeDebugShaders
                    > Task :app:compileDebugShaders
                    > Task :app:compileDebugAidl NO-SOURCE
                    > Task :app:compileDebugRenderscript NO-SOURCE
                    > Task :app:generateDebugResources
                    > Task :app:processDebugManifest
                    > Task :app:generateDebugAssets
                    > Task :app:mergeDebugAssets
                    > Task :app:validateSigningDebug
                    > Task :app:signingConfigWriterDebug
                    > Task :app:mergeDebugResources

                    > Task :app:processDebugResources FAILED
                    AGPBI: {"kind":"error","text":"Android resource linking failed","sources":[{"file":"${file.absolutePath}","position":{"startLine":3,"startColumn":4,"startOffset":54,"endLine":14,"endColumn":12,"endOffset":686}}],"original":"${file.absolutePath}:4:5-15:13: AAPT: error: style attribute 'attr/colorPrfimary (aka com.example.myapplication:attr/colorPrfimary)' not found.\n    ","tool":"AAPT"}
                    AGPBI: {"kind":"error","text":"Android resource linking failed","sources":[{"file":"${file.absolutePath}","position":{"startLine":3,"startColumn":4,"startOffset":54,"endLine":14,"endColumn":12,"endOffset":686}}],"original":"${file.absolutePath}:4:5-15:13: AAPT: error: style attribute 'attr/colorPgfrimaryDark (aka com.example.myapplication:attr/colorPgfrimaryDark)' not found.\n    ","tool":"AAPT"}
                    AGPBI: {"kind":"error","text":"Android resource linking failed","sources":[{"file":"${file.absolutePath}","position":{"startLine":3,"startColumn":4,"startOffset":54,"endLine":14,"endColumn":12,"endOffset":686}}],"original":"${file.absolutePath}:4:5-15:13: AAPT: error: style attribute 'attr/dfg (aka com.example.myapplication:attr/dfg)' not found.\n    ","tool":"AAPT"}
                    AGPBI: {"kind":"error","text":"Android resource linking failed","sources":[{"file":"${file.absolutePath}","position":{"startLine":3,"startColumn":4,"startOffset":54,"endLine":14,"endColumn":12,"endOffset":686}}],"original":"${file.absolutePath}:4:5-15:13: AAPT: error: style attribute 'attr/colorEdfdrror (aka com.example.myapplication:attr/colorEdfdrror)' not found.\n    ","tool":"AAPT"}

                    FAILURE: Build failed with an exception.

                    * What went wrong:
                    Execution failed for task ':app:processDebugResources'.
                    > A failure occurred while executing com.android.build.gradle.internal.tasks.Workers.ActionFacade
                       > Android resource linking failed
                         ${file.absolutePath}:4:5-15:13: AAPT: error: style attribute 'attr/colorPrfimary (aka com.example.myapplication:attr/colorPrfimary)' not found.

                         ${file.absolutePath}:4:5-15:13: AAPT: error: style attribute 'attr/colorPgfrimaryDark (aka com.example.myapplication:attr/colorPgfrimaryDark)' not found.

                         ${file.absolutePath}:4:5-15:13: AAPT: error: style attribute 'attr/dfg (aka com.example.myapplication:attr/dfg)' not found.

                         ${file.absolutePath}:4:5-15:13: AAPT: error: style attribute 'attr/colorEdfdrror (aka com.example.myapplication:attr/colorEdfdrror)' not found.


                    * Try:
                    Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output. Run with --scan to get full insights.

                    * Get more help at https://help.gradle.org

                    BUILD FAILED in 5s
                    16 actionable tasks: 15 executed, 1 up-to-date""".trimIndent()

    buildListener.onTaskOutput(myTaskId, output, true)
    buildListener.onFailure(myTaskId, RuntimeException("test"))
    buildListener.onEnd(myTaskId)

    assertThat(myTracker.usages).hasSize(1)

    val messages = myTracker.usages.first().studioEvent.buildOutputWindowStats.buildErrorMessagesList
    assertThat(messages).isNotNull()
    assertThat(messages).hasSize(4)

    messages.forEach {
      checkSentMetricsData(it, BuildErrorMessage.ErrorType.AAPT, BuildErrorMessage.FileType.PROJECT_FILE,
                           fileIncluded = true, lineIncluded = true)
    }
  }
}