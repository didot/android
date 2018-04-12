/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.bazel

import com.android.testutils.TestUtils
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.bazel.fixture.BazelConsoleToolWindowFixture
import com.android.tools.idea.tests.gui.framework.bazel.fixture.ImportBazelProjectWizardFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.GuiTestProjectSystem
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import org.fest.swing.core.Robot
import org.fest.swing.timing.Wait
import java.io.File

class BazelGuiTestProjectSystem : GuiTestProjectSystem {
  override val id = BazelGuiTestProjectSystem::class.java.name!!

  override val buildSystem: TargetBuildSystem.BuildSystem
    get() = TargetBuildSystem.BuildSystem.BAZEL

  private val logger = Logger.getInstance(id)

  override fun prepareTestForImport(targetTestDirectory: File) {
    // If the uitestignore file exists, then delete the files listed in that file.
    val ignoreFile = File(targetTestDirectory, "bazel.uitestignore")
    if (ignoreFile.exists()) {
      ignoreFile.readLines()
        .map { name -> File(targetTestDirectory, name) }
        .forEach { file -> file.delete() }
    }

    targetTestDirectory
      .walk()
      .filter { f -> f.exists() && f.name.endsWith(".bazeltestfile") }
      .forEach { f -> f.renameTo(File(f.parent, f.nameWithoutExtension)) }

    val workspaceFile = File(targetTestDirectory, "WORKSPACE")
    workspaceFile.writeText(injectRuntimeVariables(workspaceFile.readText()))

    File(targetTestDirectory, ".bazelrc").appendText("startup --host_javabase=" + getJdkPath())
  }

  private fun injectRuntimeVariables(workspaceFileContent: String) =
    workspaceFileContent
      .replace("%ANDROID_SDK_PATH%", getSdkPath())
      .replace("%LOCAL_TEST_REPOSITORY%", getPrebuiltsRepoPath())

  override fun importProject(targetTestDirectory: File, robot: Robot, buildPath: String?) {
    logger.info("Importing project.")

    val checkedBuildPath = buildPath ?: "app/BUILD"

    openBazelImportWizard(robot)
      .setWorkspacePath(targetTestDirectory.path)
      .clickNext()
      .setBazelBinaryPath(getBazelBinaryPath())
      .clickNext()
      .selectGenerateFromBuildFileOptionAndSetPath(checkedBuildPath)
      .clickNext()
      .uncommentApi27(File(targetTestDirectory, checkedBuildPath))
      .clickFinish()
      .waitForProjectValidation()
  }

  override fun requestProjectSync(ideFrameFixture: IdeFrameFixture): GuiTestProjectSystem {
    BazelConsoleToolWindowFixture(ideFrameFixture.project, ideFrameFixture.robot()).clearBazelConsole(ideFrameFixture)

    logger.info("Requesting project sync.")
    ideFrameFixture.invokeMenuPath("Bazel", "Sync", "Sync Project with BUILD Files")
    return this
  }

  override fun waitForProjectSyncToFinish(ideFrameFixture: IdeFrameFixture) {
    logger.info("Waiting for sync to start.")

    val consoleFixture = BazelConsoleToolWindowFixture(ideFrameFixture.project, ideFrameFixture.robot())
    Wait.seconds(300).expecting("Bazel sync to start").until(consoleFixture::hasSyncStarted)

    logger.info("Sync in progress; waiting for sync to finish.")
    Wait.seconds(300).expecting("Bazel sync to finish").until(consoleFixture::hasSyncFinished)

    logger.info("Sync complete; waiting for background tasks to finish.")
    GuiTests.waitForProjectIndexingToFinish(ideFrameFixture.project)
    logger.info("Background tasks have finished.")
  }

  override fun validateSetup() {
    PluginManagerCore.getPlugins().find { it.name == "Bazel" } ?: throw IllegalStateException(
        """
The bazel plugin is required to run tests with BAZEL as the build system. It doesn't seem to be present on the plugin path.
This issue can be fixed by:
 1. Generate the bazel plugin by running:
    ${'$'} bazel build //tools/adt/idea/android-uitests:aswb_plugin
 2. Add the bazel plugin to your plugin path. To do this, edit your current run configuration, and include the following in the VM options:
    -Dplugin.path=/path/to/studio-master-dev/bazel-genfiles/tools/adt/idea/android-uitests/aswb/

"""
    )
  }

  override fun getProjectRootDirectory(project: Project): VirtualFile {
    val rootDir = project.baseDir!!.parent!!

    if (rootDir.findChild("WORKSPACE") == null) {
      throw IllegalStateException(
"""
Project root directory ${rootDir.canonicalPath} does not contain a Bazel WORKSPACE file. Check BazelGuiTestProjectSystem.kt to ensure that
the location of the project data directory that is assigned during importProject() is consistent with the getProjectRootDirectory() method.
"""
      )
    }

    return rootDir
  }

  private fun openBazelImportWizard(robot: Robot): ImportBazelProjectWizardFixture {
    WelcomeFrameFixture.find(robot).importBazelProject()
    return ImportBazelProjectWizardFixture.find(robot)
  }

  private fun getBazelBinaryPath(): String {
    val platformPath = getPlatformPathName() ?: throw RuntimeException("Running test on unsupported platform for bazel")
    return File(TestUtils.getWorkspaceRoot(), "prebuilts/tools/$platformPath/bazel/bazel-real").path
  }

  private fun getPrebuiltsRepoPath(): String {
    val prebuiltsRepo = "prebuilts/tools/common/m2/repository"
    return if (TestUtils.runningFromBazel()) {
      // Based on EmbeddedDistributionPaths#findAndroidStudioLocalMavenRepoPaths:
      File(PathManager.getHomePath(), "../../$prebuiltsRepo").absolutePath
    } else {
      TestUtils.getWorkspaceFile(prebuiltsRepo).absolutePath
    }
  }

  private fun getJdkPath(): String {
    val subdir = when {
      SystemInfo.isWindows -> "win64"
      SystemInfo.isLinux -> "linux"
      SystemInfo.isMac -> "mac/Contents/Home"
      else -> throw RuntimeException("Running test on unsupported OS for bazel")
    }

    return File(TestUtils.getWorkspaceRoot(), "prebuilts/studio/jdk/$subdir").path
  }

  private fun getSdkPath(): String {
    val osName = getOSName() ?: throw RuntimeException("Running test on unsupported OS for bazel")
    return File(TestUtils.getWorkspaceRoot(), "prebuilts/studio/sdk/$osName").path
  }

  private fun getPlatformPathName(): String? {
    val platformArch = if (System.getProperty("os.arch")?.endsWith("64") ?: return null) "x86_64" else "x86"

    return when {
      SystemInfo.isWindows -> if (platformArch == "x86") "windows" else "windows-x86_64"
      SystemInfo.isLinux -> "linux-$platformArch"
      SystemInfo.isMac -> "darwin-$platformArch"
      else -> null
    }
  }

  private fun getOSName(): String? {
    return when {
      SystemInfo.isWindows -> "windows"
      SystemInfo.isLinux -> "linux"
      SystemInfo.isMac -> "darwin"
      else -> null
    }
  }
}