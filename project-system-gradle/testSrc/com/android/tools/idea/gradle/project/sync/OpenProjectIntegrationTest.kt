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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testing.AndroidGradleTests.addJdk8ToTableButUseCurrent
import com.android.tools.idea.testing.AndroidGradleTests.restoreJdk
import com.android.tools.idea.testing.AndroidGradleTests.syncProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.PSD_SAMPLE_REPO
import com.android.tools.idea.testing.fileUnderGradleRoot
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.saveAndDump
import com.android.tools.idea.testing.verifySyncSkipped
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.PathUtil.toSystemDependentName
import org.jetbrains.android.AndroidTestBase
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
@RunsInEdt
class OpenProjectIntegrationTest : GradleIntegrationTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()!!

  @get:Rule
  val testName = TestName()

  @After
  fun tearDown() {
    restoreJdk()
  }

  @Test
  fun testReopenProject() {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    val before = openPreparedProject("project") { project -> project.saveAndDump() }
    val after = openPreparedProject("project") { project ->
      verifySyncSkipped(project, projectRule.fixture.testRootDisposable)
      project.saveAndDump()
    }
    assertThat(after).isEqualTo(before)
  }

  @Test
  fun testReimportProject() {
    val root = prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    val before = openPreparedProject("project") { project -> project.saveAndDump() }
    FileUtil.delete(File(root, ".idea"))
    val after = openPreparedProject("project") { project ->
      // Synced again.
      assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult())
        .isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS)
      project.saveAndDump()
    }
    assertThat(after).isEqualTo(before)
  }

  @Test
  fun testReopenKaptProject() {
    prepareGradleProject(TestProjectPaths.KOTLIN_KAPT, "project")
    val before = openPreparedProject("project") { project -> project.saveAndDump() }
    val after = openPreparedProject("project") { project ->
      verifySyncSkipped(project, projectRule.fixture.testRootDisposable)
      project.saveAndDump()
    }
    assertThat(after).isEqualTo(before)
  }

  @Test
  fun testReopenProjectAfterFailedSync() {
    val root = prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    val buildFile = VfsUtil.findFileByIoFile(root.resolve("app/build.gradle"), true)!!

    val (snapshots, lastSyncFinishedTimestamp) = openPreparedProject("project") { project ->
      val initial = project.saveAndDump()
      runWriteAction {
        buildFile.setBinaryContent("*bad*".toByteArray())
      }
      syncProject(project, GradleSyncInvoker.Request.testRequest())
      assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(ProjectSystemSyncManager.SyncResult.FAILURE)
      (initial to project.saveAndDump()) to GradleSyncState.getInstance(project).lastSyncFinishedTimeStamp
    }
    val (initial, before) = snapshots
    val after = openPreparedProject(
      "project",
      options = OpenPreparedProjectOptions(
        verifyOpened = { project ->
          assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(ProjectSystemSyncManager.SyncResult.FAILURE)
        }
      )
    ) { project ->
      // Make sure we tried to sync.
      assertThat(GradleSyncState.getInstance(project).lastSyncFinishedTimeStamp).isNotEqualTo(lastSyncFinishedTimestamp)
      project.saveAndDump()
    }
    assertThat(before).isEqualTo(initial)
    // TODO(b/211782178): assertThat(after).isEqualTo(before)
  }

  @Test
  fun testReopenCompositeBuildProject() {
    prepareGradleProject(TestProjectPaths.COMPOSITE_BUILD, "project")
    val before = openPreparedProject("project") { project -> project.saveAndDump() }
    val after = openPreparedProject("project") { project ->
      verifySyncSkipped(project, projectRule.fixture.testRootDisposable)
      project.saveAndDump()
    }
    assertThat(after).isEqualTo(before)
  }

  @Test
  fun testReopenPsdSampleGroovy() {
    prepareGradleProject(TestProjectPaths.PSD_SAMPLE_GROOVY, "project")
    val before = openPreparedProject("project") { project -> project.saveAndDump() }
    val after = openPreparedProject("project") { project ->
      verifySyncSkipped(project, projectRule.fixture.testRootDisposable)
      project.saveAndDump()
    }
    assertThat(after).isEqualTo(before)
  }

  @Test
  fun testOpen36Project() {
    addJdk8ToTableButUseCurrent()
    prepareGradleProject(TestProjectPaths.RUN_APP_36, "project")
    openPreparedProject("project") { project ->
      val androidTestRunConfiguration =
        RunManagerEx.getInstanceEx(project).allConfigurationsList.filterIsInstance<AndroidTestRunConfiguration>().singleOrNull()
      assertThat(androidTestRunConfiguration?.name).isEqualTo("All Tests Sub 36")

      val runConfigurations = RunManagerEx.getInstanceEx(project).allConfigurationsList.filterIsInstance<ModuleBasedConfiguration<*, *>>()
      // Existing run configuration will not be able to find the modules since we enabled qualified module names and module per source set
      // As such these existing configuration will be mapped to null and a new configuration for the app module created.
      // We don't remove this configuration to avoid losing importing config the user has set up.
      assertThat(runConfigurations.associate { it.name to it.configurationModule?.module?.name }).isEqualTo(mapOf(
        "app" to "My36.app.main",
        "app.sub36" to "My36.app.sub36.main",
        "sub36" to null,
        "All Tests Sub 36" to null
      ))
    }
  }

  @Test
  fun testOpen36ProjectWithoutModules() {
    addJdk8ToTableButUseCurrent()
    val projectRoot = prepareGradleProject(TestProjectPaths.RUN_APP_36, "project")
    runWriteAction {
      val projectRootVirtualFile = VfsUtil.findFileByIoFile(projectRoot, false)!!
      projectRootVirtualFile.findFileByRelativePath(".idea/modules.xml")!!.delete("test")
      projectRootVirtualFile.findFileByRelativePath("app/app.iml")!!.delete("test")
      projectRootVirtualFile.findFileByRelativePath("app/sub36/sub36.iml")!!.delete("test")
      projectRootVirtualFile.findFileByRelativePath("My36.iml")!!.delete("test")
    }

    openPreparedProject("project") { project ->
      val runConfigurations = RunManagerEx.getInstanceEx(project).allConfigurationsList.filterIsInstance<ModuleBasedConfiguration<*, *>>()
      // Existing run configuration will not be able to find the modules since we enabled qualified module names and module per source set
      // As such these existing configuration will be mapped to null and a new configuration for the app module created.
      // We don't remove this configuration to avoid losing importing config the user has set up.
      assertThat(runConfigurations.associate { it.name to it.configurationModule?.module?.name }).isEqualTo(mapOf(
        "app" to "My36.app.main",
        "app.sub36" to "My36.app.sub36.main",
        "sub36" to null,
        "All Tests Sub 36" to null
      ))
    }
  }

  @Test
  fun testReopenAndResync() {
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")
    val debugBefore = openPreparedProject("project") { project: Project ->
      runWriteAction {
        // Modify the project build file to ensure the project is synced when opened.
        project.gradleModule(":")!!.fileUnderGradleRoot("build.gradle")!!.also { file ->
          file.setBinaryContent((String(file.contentsToByteArray()) + " // ").toByteArray())
        }
      }
      project.saveAndDump()
    }
    val reopenedDebug = openPreparedProject("project") { project ->
      // TODO(b/146535390): Uncomment when sync required status survives restarts.
      //  assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS)
      project.saveAndDump()
    }
    assertThat(reopenedDebug).isEqualTo(debugBefore)
  }

  @Test
  fun testResyncPsdDependency() {
    prepareGradleProject(TestProjectToSnapshotPaths.PSD_DEPENDENCY, "project")
    openPreparedProject("project") { project: Project ->
      val firstSync = project.saveAndDump()
      syncProject(project, GradleSyncInvoker.Request.testRequest())
      val secondSync = project.saveAndDump()
      assertThat(firstSync).isEqualTo(secondSync)
    }
  }

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = TestProjectPaths.TEST_DATA_PATH
  override fun getAdditionalRepos() =
    listOf(File(AndroidTestBase.getTestDataPath(), toSystemDependentName(PSD_SAMPLE_REPO)))
}
