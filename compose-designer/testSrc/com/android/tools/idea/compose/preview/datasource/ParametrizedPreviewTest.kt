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
package com.android.tools.idea.compose.preview.datasource

import com.android.ide.common.blame.Message
import com.android.testutils.TestUtils
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.renderer.renderPreviewElementForResult
import com.android.tools.idea.compose.preview.util.ParametrizedPreviewElementTemplate
import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.NoSecurityManagerRenderService
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ParametrizedPreviewTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    RenderService.shutdownRenderExecutor(5)
    RenderService.initializeRenderExecutor()
    RenderService.setForTesting(projectRule.project, NoSecurityManagerRenderService(projectRule.project))
    projectRule.fixture.testDataPath = TestUtils.getWorkspaceFile("tools/adt/idea/compose-designer/testData").path
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH)
    projectRule.requestSyncAndWait()
    val gradleInvocationResult = projectRule.invokeTasks("compileDebugSources")
    if (!gradleInvocationResult.isBuildSuccessful) {
      Assert.fail("""
        The project must compile correctly for the test to pass.

        Compiler errors:
        ${gradleInvocationResult.getCompilerMessages(Message.Kind.ERROR).joinToString("\n\n") { it.rawMessage }}


        ${gradleInvocationResult.buildError}
      """.trimIndent())
    }

    Assert.assertTrue("The project must compile correctly for the test to pass",
                      projectRule.invokeTasks("compileDebugSources").isBuildSuccessful)
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_PREVIEW_DATA_SOURCES.clearOverride()
    RenderService.setForTesting(projectRule.project, null)
  }

  /**
   * Checks that we do not find parametrized previews when the flag is disabled.
   */
  @Test
  fun testFlagGuardedParametrizedOverride() {
    StudioFlags.COMPOSE_PREVIEW_DATA_SOURCES.override(false)
    val project = projectRule.project

    val parametrizedPreviews = VfsUtil.findRelativeFile("app/src/main/java/google/simpleapplication/ParametrizedPreviews.kt",
                                                        ProjectRootManager.getInstance(project).contentRoots[0])!!

    assertEquals(0, AnnotationFilePreviewElementFinder.findPreviewMethods(project, parametrizedPreviews).count())
  }

  /**
   * Checks the rendering of the default `@Preview` in the Compose template.
   */
  @Test
  fun testParametrizedPreview() {
    StudioFlags.COMPOSE_PREVIEW_DATA_SOURCES.override(true)
    val project = projectRule.project

    val parametrizedPreviews = VfsUtil.findRelativeFile("app/src/main/java/google/simpleapplication/ParametrizedPreviews.kt",
                                                ProjectRootManager.getInstance(project).contentRoots[0])!!

    val elements = AnnotationFilePreviewElementFinder.findPreviewMethods(project, parametrizedPreviews)
      .flatMap {
        if (it is ParametrizedPreviewElementTemplate) {
          it.instances()
        }
        else {
          sequenceOf(it)
        }
      }
      .filterIsInstance<PreviewElementInstance>()

    assertEquals(3, elements.count())

    elements.forEach {
      renderPreviewElementForResult(projectRule.androidFacet(":app"), it)
    }
  }
}