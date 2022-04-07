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
package com.android.tools.idea.gradle.project.sync

import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.truth.Expect
import com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_1_8
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.idea.util.projectStructure.version
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

@RunsInEdt
@OldAgpTest(agpVersions = ["3.5.0"], gradleVersions = ["5.5"])
class Jvm1_8SyncTest : GradleIntegrationTest {
  @Test
  fun test18() {
    expect.that(IdeSdks.getInstance().jdk?.version).isEqualTo(JDK_1_8)
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "p", gradleVersion = "5.5", gradlePluginVersion = "3.5.0")
    openPreparedProject("p") { project ->
      val projectSdk = ProjectRootManager.getInstance(project).projectSdk
      expect.that(projectSdk?.version).isEqualTo(JDK_1_8)
    }
  }

  @Before
  fun setUp() {
    runWriteAction {
      AndroidGradleTests.overrideJdkTo8()
    }
    IdeSdks.removeJdksOn(projectRule.testRootDisposable)
  }

  @After
  fun tearDown() {
    runWriteAction {
      AndroidGradleTests.restoreJdk()
    }
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModel().onEdt()

  @get:Rule
  var testName = TestName()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = TestProjectPaths.TEST_DATA_PATH
  override fun getAdditionalRepos(): Collection<File> = listOf()
}