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
package com.android.tools.idea.lint.model

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.lint.TestDataPaths
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.saveAndDump
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Snapshot tests for 'Lint Models'.
 *
 * These tests convert Lint models to a stable text format which does not depend on local
 * environment (and ideally should not depend on the versions of irrelevant libraries) and compare them to pre-recorded golden
 * results.
 *
 * The pre-recorded sync results can be found in [snapshotDirectoryWorkspaceRelativePath] *.txt files.
 *
 * For instructions on how to update the snapshot files see [SnapshotComparisonTest] and if running from the command-line use
 * target as "//tools/adt/idea/android-lint:intellij.android.lint.tests_tests__test_filter=LintModelSnapshotComparisonTest".
 */

@RunsInEdt
@RunWith(Parameterized::class)
class LintModelSnapshotComparisonTest : GradleIntegrationTest, SnapshotComparisonTest {

  data class TestProject(val template: String, val pathToOpen: String = "", val useOnlyV2Model: Boolean = false) {
    override fun toString(): String = "${template.removePrefix("projects/")}$pathToOpen"
  }

  @JvmField
  @Parameterized.Parameter
  var testProjectName: TestProject? = null

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testProjects(): Collection<*> = listOf(
      TestProject(TestDataPaths.SIMPLE_APPLICATION),
      TestProject(TestDataPaths.BASIC_CMAKE_APP),
      TestProject(TestDataPaths.PSD_SAMPLE_GROOVY),
      TestProject(TestDataPaths.MULTI_FLAVOR), // TODO(b/178796251): The snaphot does not include `proguardFiles`.
      TestProject(TestDataPaths.COMPOSITE_BUILD),
      TestProject(TestDataPaths.NON_STANDARD_SOURCE_SETS, "/application"),
      TestProject(TestDataPaths.LINKED, "/firstapp"),
      TestProject(TestDataPaths.KOTLIN_KAPT),
      TestProject(TestDataPaths.LINT_CUSTOM_CHECKS),
      TestProject(TestDataPaths.TEST_FIXTURES, useOnlyV2Model = true),
    )
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  var testName = TestName()

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestDataPaths.PSD_SAMPLE_REPO)))

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android-lint/testData/snapshots/lintModels"

  @Test
  fun testLintModels() {
    val projectName = testProjectName ?: error("unit test parameter not initialized")

    if (projectName.useOnlyV2Model) {
      StudioFlags.GRADLE_SYNC_USE_V2_MODEL.override(true)
    }
    try {
      val root = prepareGradleProject(projectName.template, "project")
      openPreparedProject("project${testProjectName?.pathToOpen}") { project ->
        val dump = project.saveAndDump(mapOf("ROOT" to root)) { project, projectDumper -> projectDumper.dumpLintModels(project) }
        assertIsEqualToSnapshot(dump)
      }
    } finally {
      StudioFlags.GRADLE_SYNC_USE_V2_MODEL.clearOverride()
    }
  }
}
