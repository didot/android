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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.TestBuildAction
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.injectBuildOutputDumpingBuildViewManager
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.annotations.SystemIndependent
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File
import java.util.concurrent.TimeUnit

class AndroidGradleTestsTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  var testName = TestName()

  @Test
  fun testEmptyNotInEdt() {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    openPreparedProject("project") { }
  }

  @Test
  @RunsInEdt
  fun testEmptyInEdt() {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    openPreparedProject("project") { }
  }

  @Test
  @RunsInEdt
  fun testOutputHandling() {
    var syncMessageFound = false
    var buildMessageFound = false
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    openPreparedProject(
      "project",
      options = OpenPreparedProjectOptions(outputHandler = { if (it.contains("This is a simple application!")) syncMessageFound = true })
    ) { project ->
      injectBuildOutputDumpingBuildViewManager(project, project) { if (it.message.contains("BUILD SUCCESSFUL")) buildMessageFound = true }
      GradleBuildInvoker.getInstance(project)
        .assemble(arrayOf(project.gradleModule(":app")!!), TestCompileType.NONE)
        .get(120, TimeUnit.SECONDS)
    }
    assertThat(syncMessageFound).named("'This is a simple application!' found").isTrue()
    assertThat(buildMessageFound).named("'BUILD SUCCESSFUL' found").isTrue()
  }

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): @SystemIndependent String = "tools/adt/idea/android/testData"

  override fun getAdditionalRepos(): Collection<File> = listOf()
}