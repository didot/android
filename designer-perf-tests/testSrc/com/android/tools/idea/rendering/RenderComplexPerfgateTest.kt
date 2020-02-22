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
package com.android.tools.idea.rendering

import com.android.ide.common.rendering.api.Result
import com.android.testutils.TestUtils
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.res.FrameworkResourceRepositoryManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

private fun checkComplexLayoutInflateResult(result: RenderResult) {
  AndroidGradleTestCase.assertEquals(Result.Status.SUCCESS, result.renderResult.status)
}

/**
 * Asserts that the given result matches the [.SIMPLE_LAYOUT] structure
 */
private fun checkComplexLayoutRenderResult(result: RenderResult) {
  AndroidGradleTestCase.assertEquals(Result.Status.SUCCESS, result.renderResult.status)

  AndroidGradleTestCase.assertNotNull(result.renderedImage)
}

class RenderComplexPerfgateTest {
  @get:Rule
  val gradleRule = AndroidGradleProjectRule()
  private lateinit var facet: AndroidFacet
  private lateinit var layoutFile: VirtualFile
  private lateinit var layoutConfiguration: Configuration

  @Before
  fun setUp() {
    RenderTestUtil.beforeRenderTestCase()

    val baseTestPath = TestUtils.getWorkspaceFile("tools/adt/idea/designer-perf-tests/testData").path
    gradleRule.fixture.testDataPath = baseTestPath
    gradleRule.load(PERFGATE_COMPLEX_LAYOUT)
    gradleRule.requestSyncAndWait()
    facet = gradleRule.androidFacet(":app")
    val xmlPath = "$baseTestPath/projects/perfgateComplexLayout/app/src/main/res/layout/activity_main.xml"
    layoutFile = LocalFileSystem.getInstance().findFileByPath(xmlPath)!!
    layoutConfiguration = RenderTestUtil.getConfiguration(facet.module, layoutFile)
  }

  @After
  fun tearDown() {
    try {
      ApplicationManager.getApplication().invokeAndWait {
        RenderTestUtil.afterRenderTestCase()
      }
    }
    finally {
      FrameworkResourceRepositoryManager.getInstance().clearCache()
    }
  }

  @Test
  fun testComplexInflate() {
    val computable: ThrowableComputable<PerfgateRenderMetric, Exception> = ThrowableComputable {
      val task = RenderTestUtil.createRenderTask(facet, layoutFile, layoutConfiguration)
      val metric = getInflateMetric(task, ::checkComplexLayoutInflateResult)
      task.dispose().get(5, TimeUnit.SECONDS)
      metric
    }

    computeAndRecordMetric("inflate_time_complex", "inflate_memory_complex", computable)
  }

  @Test
  fun testComplexRender() {
    val computable: ThrowableComputable<PerfgateRenderMetric, Exception> = ThrowableComputable {
      val task = RenderTestUtil.createRenderTask(facet, layoutFile, layoutConfiguration)
      val metric = getRenderMetric(task, ::checkComplexLayoutInflateResult, ::checkComplexLayoutRenderResult)
      task.dispose().get(5, TimeUnit.SECONDS)
      metric
    }

    computeAndRecordMetric("render_time_complex", "render_memory_complex", computable)
  }
}