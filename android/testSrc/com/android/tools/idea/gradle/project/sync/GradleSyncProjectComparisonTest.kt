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
package com.android.tools.idea.gradle.project.sync

import com.android.SdkConstants.FN_SETTINGS_GRADLE
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.FileSubject
import com.android.tools.idea.testing.FileSubject.file
import com.android.tools.idea.testing.GradleSnapshotComparisonTest
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.TestProjectPaths.APP_WITH_ML_MODELS
import com.android.tools.idea.testing.TestProjectPaths.BASIC
import com.android.tools.idea.testing.TestProjectPaths.CENTRAL_BUILD_DIRECTORY
import com.android.tools.idea.testing.TestProjectPaths.HELLO_JNI
import com.android.tools.idea.testing.TestProjectPaths.KOTLIN_GRADLE_DSL
import com.android.tools.idea.testing.TestProjectPaths.NESTED_MODULE
import com.android.tools.idea.testing.TestProjectPaths.NEW_SYNC_KOTLIN_TEST
import com.android.tools.idea.testing.TestProjectPaths.NON_STANDARD_SOURCE_SETS
import com.android.tools.idea.testing.TestProjectPaths.PSD_DEPENDENCY
import com.android.tools.idea.testing.TestProjectPaths.PSD_SAMPLE_GROOVY
import com.android.tools.idea.testing.TestProjectPaths.PURE_JAVA_PROJECT
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES
import com.android.tools.idea.testing.TestProjectPaths.TWO_JARS
import com.android.tools.idea.testing.TestProjectPaths.VARIANT_SPECIFIC_DEPENDENCIES
import com.android.tools.idea.testing.assertAreEqualToSnapshots
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.fileUnderGradleRoot
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.openGradleProject
import com.android.tools.idea.testing.reopenGradleProject
import com.android.tools.idea.testing.saveAndDump
import com.google.common.truth.Truth.assertAbout
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.WriteAction.run
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.join
import com.intellij.openapi.util.io.FileUtil.writeToFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.PathUtil.toSystemDependentName
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File

/**
 * Snapshot tests for 'Gradle Sync'.
 *
 * These tests compare the results of sync by converting the resulting project to a stable text format which does not depend on local
 * environment (and ideally should not depend on the versions of irrelevant libraries) and comparing them to pre-recorded golden sync
 * results.
 *
 * The pre-recorded sync results can be found in testData/syncedProjectSnapshots/ *.txt files. Consult [snapshotSuffixes] for more
 * details on the way in which the file names are constructed.
 *
 * NOTE: It you made changes to sync or the test projects which make these tests fail in an expected way, you can re-run the tests
 *       from IDE with -DUPDATE_TEST_SNAPSHOTS to update the files. (You may need to re-run several times (currently up to 3) to
 *       update multiple snapshots used in one test.
 *
 *       Or with bazel:
bazel test //tools/adt/idea/android:intellij.android.core.tests_tests  --test_sharding_strategy=disabled  \
--test_filter="GradleSyncProjectComparisonTestCase" --nocache_test_results --strategy=TestRunner=standalone \
--jvmopt='-DUPDATE_TEST_SNAPSHOTS' --test_output=streamed --runs_per_test=3
 */
abstract class GradleSyncProjectComparisonTest(
  private val singleVariantSync: Boolean = false
) : GradleSyncIntegrationTestCase(), GradleSnapshotComparisonTest {
  override fun useSingleVariantSyncInfrastructure(): Boolean = singleVariantSync

  class FullVariantGradleSyncProjectComparisonTest : GradleSyncProjectComparisonTestCase() {
    // TODO(b/135453395): Re-enable after variant switching from cache is fixed.
    override fun testSwitchingVariantsWithReopenAndResync_simpleApplication() = Unit
  }

  class SingleVariantGradleSyncProjectComparisonTest :
    GradleSyncProjectComparisonTestCase(singleVariantSync = true) {
    /** TODO(b/124504437): Enable this test */
    override fun testNdkProjectSync() = Unit
  }

  abstract class GradleSyncProjectComparisonTestCase(singleVariantSync: Boolean = false
  ) : GradleSyncProjectComparisonTest(singleVariantSync) {
    fun testImportNoSync() {
      prepareProjectForImport(SIMPLE_APPLICATION)
      val request = GradleProjectImporter.Request(project)
      GradleProjectImporter.getInstance().importProjectNoSync(request)
      AndroidTestBase.refreshProjectFiles()
      val text = project.saveAndDump()
      assertIsEqualToSnapshot(text)
    }

    // https://code.google.com/p/android/issues/detail?id=233038
    open fun testLoadPlainJavaProject() {
      val text = importSyncAndDumpProject(PURE_JAVA_PROJECT)
      assertIsEqualToSnapshot(text)
    }

    // See https://code.google.com/p/android/issues/detail?id=226802
    fun testNestedModule() {
      val text = importSyncAndDumpProject(NESTED_MODULE)
      assertIsEqualToSnapshot(text)
    }

    // See https://code.google.com/p/android/issues/detail?id=224985
    open fun testNdkProjectSync() {
      val text = importSyncAndDumpProject(HELLO_JNI)
      assertIsEqualToSnapshot(text)
    }

    // See https://code.google.com/p/android/issues/detail?id=76444
    fun testWithEmptyGradleSettingsFileInSingleModuleProject() {
      val text = importSyncAndDumpProject(BASIC) { createEmptyGradleSettingsFile() }
      assertIsEqualToSnapshot(text)
    }

    fun testTransitiveDependencies() {
      // TODO(b/124505053): Remove almost identical snapshots when SDK naming is fixed.
      val text = importSyncAndDumpProject(TRANSITIVE_DEPENDENCIES)
      assertIsEqualToSnapshot(text)
    }

    fun testSimpleApplication() {
      val text = importSyncAndDumpProject(SIMPLE_APPLICATION)
      assertIsEqualToSnapshot(text)
    }

    fun testWithMlModels() {
      val text = importSyncAndDumpProject(APP_WITH_ML_MODELS)
      assertIsEqualToSnapshot(text)
    }

    fun testMultiFlavor() {
      val text = importSyncAndDumpProject(TestProjectPaths.MULTI_FLAVOR)
      assertIsEqualToSnapshot(text)
    }

    fun testExternalSourceSets() {
      val projectRootPath = prepareProjectForImport(NON_STANDARD_SOURCE_SETS)
      val request = GradleSyncInvoker.Request.testRequest(true);
      AndroidGradleTests.importProject(project, request)

      val text = project.saveAndDump(
        mapOf("EXTERNAL_SOURCE_SET" to File(projectRootPath.parentFile, "externalRoot"),
              "EXTERNAL_MANIFEST" to File(projectRootPath.parentFile, "externalManifest"))
      )
      assertIsEqualToSnapshot(text)
    }

    // See https://code.google.com/p/android/issues/detail?id=74259
    fun testWithCentralBuildDirectoryInRootModuleDeleted() {
      val text = importSyncAndDumpProject(CENTRAL_BUILD_DIRECTORY) { projectRootPath ->
        // The bug appears only when the central build folder does not exist.
        val centralBuildDirPath = File(projectRootPath, join("central", "build"))
        val centralBuildParentDirPath = centralBuildDirPath.parentFile
        FileUtil.delete(centralBuildParentDirPath)
      }
      assertIsEqualToSnapshot(text)
    }

    fun testSyncWithKotlinDsl() {
      val text = importSyncAndDumpProject(KOTLIN_GRADLE_DSL)
      assertIsEqualToSnapshot(text)
    }

    fun testSyncKotlinProject() {
      // TODO(b/125321223): Remove suffixes from the snapshot files when fixed.
      val text = importSyncAndDumpProject(NEW_SYNC_KOTLIN_TEST)
      assertIsEqualToSnapshot(text)
    }

    open fun testPsdDependency() {
      val firstSync = importSyncAndDumpProject(PSD_DEPENDENCY)
      val secondSync = syncAndDumpProject()
      // TODO(b/124677413): When fixed, [secondSync] should match the same snapshot. (Remove ".second_sync")
      assertAreEqualToSnapshots(
        firstSync to "",
        secondSync to ".second_sync"
      )
    }

    open fun testPsdDependencyDeleteModule() {
      val beforeDelete = importSyncAndDumpProject(PSD_DEPENDENCY)
      PsProjectImpl(project).let { projectModel ->
        projectModel.removeModule(":moduleB")
        projectModel.applyChanges()
      }
      val textAfterDelete = syncAndDumpProject()
      // TODO(b/124497021): Remove duplicate dependencies from the snapshot by reverting to the main snapshot when the bug is fixed.
      assertAreEqualToSnapshots(
        beforeDelete to ".before_delete",
        textAfterDelete to ".after_moduleb_deleted"
      )
    }

    // TODO(b/128873247): Update snapshot files with the bug is fixed and Java-Gradle facet is removed.
    open fun testPsdDependencyAndroidToJavaModuleAndBack() {
      val beforeAndroidToJava = importSyncAndDumpProject(PSD_DEPENDENCY)
      val oldModuleCContent = WriteAction.compute<ByteArray, Throwable> {
        val jModuleMFile = project.guessProjectDir()?.findFileByRelativePath("jModuleM/build.gradle")!!
        val moduleCFile = project.guessProjectDir()?.findFileByRelativePath("moduleC/build.gradle")!!
        val moduleCContent = moduleCFile.contentsToByteArray()
        val jModuleMContent = jModuleMFile.contentsToByteArray()
        moduleCFile.setBinaryContent(jModuleMContent)
        moduleCContent
      }
      ApplicationManager.getApplication().saveAll()
      val afterAndroidToJava = syncAndDumpProject()
      // TODO(b/124497021): Remove duplicate dependencies from the snapshot by reverting to the main snapshot when the bug is fixed.

      run<Throwable> {
        val moduleCFile = project.guessProjectDir()?.findFileByRelativePath("moduleC/build.gradle")!!
        moduleCFile.setBinaryContent(oldModuleCContent)
      }
      ApplicationManager.getApplication().saveAll()
      val textAfterSecondChange = syncAndDumpProject()
      // TODO(b/124497021): Remove duplicate dependencies from the snapshot by reverting to the main snapshot when the bug is fixed.
      assertAreEqualToSnapshots(
        beforeAndroidToJava to ".before_android_to_java",
        afterAndroidToJava to ".after_android_to_java",
        textAfterSecondChange to ".after_java_to_android"
      )
    }

    open fun testPsdSample() {
      val text = importSyncAndDumpProject(PSD_SAMPLE_GROOVY)
      assertIsEqualToSnapshot(text)
    }

    open fun testPsdSampleRenamingModule() {
      val beforeRename = importSyncAndDumpProject(PSD_SAMPLE_GROOVY)
      PsProjectImpl(project).let { projectModel ->
        projectModel.removeModule(":nested1")
        projectModel.removeModule(":nested1:deep")
        with(projectModel.parsedModel.projectSettingsModel!!) {
          addModulePath(":container1")
          addModulePath(":container1:deep")
        }
        projectModel.applyChanges()
      }
      run<Throwable> {
        project.guessProjectDir()!!.findFileByRelativePath("nested1")!!.rename("test", "container1")
      }
      ApplicationManager.getApplication().saveAll()
      val afterRename = syncAndDumpProject()
      assertAreEqualToSnapshots(
        beforeRename to "",
        afterRename to ".after_rename"
      )
    }

    open fun testPsdDependencyUpgradeLibraryModule() {
      val beforeLibUpgrade = importSyncAndDumpProject(PSD_DEPENDENCY)
      PsProjectImpl(project).let { projectModel ->
        projectModel
          .findModuleByGradlePath(":modulePlus")!!
          .dependencies
          .findLibraryDependencies("com.example.libs", "lib1")
          .forEach { it.version = "1.0".asParsed() }
        projectModel
          .findModuleByGradlePath(":mainModule")!!
          .dependencies
          .findLibraryDependencies("com.example.libs", "lib1")
          .forEach { it.version = "0.9.1".asParsed() }
        projectModel
          .findModuleByGradlePath(":mainModule")!!
          .dependencies
          .findLibraryDependencies("com.example.jlib", "lib3")
          .single().version = "0.9.1".asParsed()
        projectModel.applyChanges()
      }
      val afterLibUpgrade = syncAndDumpProject()
      // TODO(b/124677413): Remove irrelevant changes from the snapshot when the bug is fixed.
      assertAreEqualToSnapshots(
        beforeLibUpgrade to ".before_lib_upgrade",
        afterLibUpgrade to ".after_lib_upgrade"
      )
    }

    fun testTwoJarsWithTheSameName() {
      val text = importSyncAndDumpProject(TWO_JARS)
      // TODO(b/125680482): Update the snapshot when the bug is fixed.
      assertIsEqualToSnapshot(text)
    }

    fun testWithCompositeBuild() {
      val text = importSyncAndDumpProject(TestProjectPaths.COMPOSITE_BUILD)
      assertIsEqualToSnapshot(text)
    }

    fun testWithBuildSrc() {
      val text = importSyncAndDumpProject(TestProjectPaths.APP_WITH_BUILDSRC)
      assertIsEqualToSnapshot(text)
    }

    fun testSwitchingVariants_simpleApplication() {
      val debugBefore = importSyncAndDumpProject(SIMPLE_APPLICATION)
      BuildVariantUpdater.getInstance(project).updateSelectedBuildVariant(project, "app", "release", true)
      val release = project.saveAndDump()
      BuildVariantUpdater.getInstance(project).updateSelectedBuildVariant(project, "app", "debug", true)
      val debugAfter = project.saveAndDump()
      assertAreEqualToSnapshots(
        debugBefore to ".debug",
        release to ".release",
        debugAfter to ".debug"
      )
    }

    fun testReimportSimpleApplication() {
      var root: String? = null
      val before = openGradleProject(SIMPLE_APPLICATION, "project") { project ->
        val snapshot = project.saveAndDump()
        root = project.basePath
        snapshot
      }
      FileUtil.delete(File(root!!, ".idea"))
      val after = reopenGradleProject("project") { project ->
        project.saveAndDump()
      }
      assertAreEqualToSnapshots(
        before to ".same",
        after to ".same"
      )
    }

    fun testSwitchingVariantsWithReopen_simpleApplication() {
      val debugBefore = openGradleProject(SIMPLE_APPLICATION, "project") { project ->
        project.saveAndDump()
      }
      val release = reopenGradleProject("project") { project ->
        BuildVariantUpdater.getInstance(project).updateSelectedBuildVariant(project, "app", "release", true)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        project.saveAndDump()
      }
      val reopenedRelease = reopenGradleProject("project") { project ->
        project.saveAndDump()
      }
      assertAreEqualToSnapshots(
        debugBefore to ".debug",
        release to ".release",
        reopenedRelease to ".release"
      )
    }

    open fun testSwitchingVariantsWithReopenAndResync_simpleApplication() {
      val debugBefore = openGradleProject(SIMPLE_APPLICATION, "project") { project ->
        project.saveAndDump()
      }
      val release = reopenGradleProject("project") { project ->
        BuildVariantUpdater.getInstance(project).updateSelectedBuildVariant(project, "app", "release", true)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        runWriteAction {
          // Modify the project build file to ensure the project is synced when opened.
          project.gradleModule(":")!!.fileUnderGradleRoot("build.gradle")!!.also { file ->
            file.setBinaryContent((String(file.contentsToByteArray()) + " // ").toByteArray())
          }
        }
        project.saveAndDump()
      }
      val reopenedRelease = reopenGradleProject("project") { project ->
        project.saveAndDump()
      }
      assertAreEqualToSnapshots(
        debugBefore to ".debug",
        release to ".release",
        reopenedRelease to ".release"
      )
    }

    // TODO(b/135453395): This test illustrates that variant switching does not remove dependencies.
    fun testSwitchingVariants_variantSpecificDependencies() {
      val freeDebugBefore = importSyncAndDumpProject(VARIANT_SPECIFIC_DEPENDENCIES)

      BuildVariantUpdater.getInstance(project).updateSelectedBuildVariant(project, "app", "paidDebug", true)
      val paidDebug = project.saveAndDump()

      BuildVariantUpdater.getInstance(project).updateSelectedBuildVariant(project, "app", "freeDebug", true)
      val freeDebugAfter = project.saveAndDump()

      assertAreEqualToSnapshots(
        freeDebugBefore to ".freeDebugBefore",
        paidDebug to ".paidDebug",
        freeDebugAfter to ".freeDebugAfter"
      )
    }

    fun testCompatibilityWithAndroidStudio36Project() {
      val text = importSyncAndDumpProject(TestProjectPaths.COMPATIBILITY_TESTS_AS_36)
      assertIsEqualToSnapshot(text)
    }

    fun testCompatibilityWithAndroidStudio36NoImlProject() {
      val text = importSyncAndDumpProject(TestProjectPaths.COMPATIBILITY_TESTS_AS_36_NO_IML)
      assertIsEqualToSnapshot(text)
    }

    fun testApiDependency() {
      val text = importSyncAndDumpProject(TestProjectPaths.API_DEPENDENCY)
      assertIsEqualToSnapshot(text)
    }
  }

  override val snapshotDirectoryName = "syncedProjectSnapshots"
  override val snapshotSuffixes = listOfNotNull(
    // Suffixes to use to override the default expected result.
    ".single_variant".takeIf { singleVariantSync },
    ""
  )

  private lateinit var ideComponents: IdeComponents

  protected fun importSyncAndDumpProject(projectDir: String, patch: ((projectRootPath: File) -> Unit)? = null): String {
    val projectRootPath = prepareProjectForImport(projectDir)
    patch?.invoke(projectRootPath)
    // In order to display all the information we are interested in we need to force creation of missing content roots.
    AndroidGradleTests.importProject(project, GradleSyncInvoker.Request.testRequest(true))
    return project.saveAndDump()
  }

  protected fun syncAndDumpProject(): String {
    requestSyncAndWait()
    return project.saveAndDump()
  }

  override fun setUp() {
    super.setUp()
    val project = project
    ideComponents = IdeComponents(project)
    val projectSettings = GradleProjectSettings()
    projectSettings.distributionType = DEFAULT_WRAPPED
    GradleSettings.getInstance(project).linkedProjectsSettings = listOf(projectSettings)
  }

  protected fun createEmptyGradleSettingsFile() {
    val settingsFilePath = File(projectFolderPath, FN_SETTINGS_GRADLE)
    assertTrue(FileUtil.delete(settingsFilePath))
    writeToFile(settingsFilePath, " ")
    assertAbout<FileSubject, File>(file()).that(settingsFilePath).isFile()
    refreshProjectFiles()
  }

  override fun getAdditionalRepos() =
    listOf(File(AndroidTestBase.getTestDataPath(), toSystemDependentName(TestProjectPaths.PSD_SAMPLE_REPO)))

  private val tempSuffix: String = java.time.Clock.systemUTC().millis().toString()

  override fun getBaseTestPath(): @SystemDependent String {
    return File(super.getBaseTestPath(), tempSuffix).absolutePath
  }
}

