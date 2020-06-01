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
package com.android.tools.idea.navigator

import com.android.builder.model.SourceProvider
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.testing.*
import com.android.tools.idea.util.toIoFile
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.IdeaSourceProvider
import org.jetbrains.android.facet.SourceProviderManager
import java.io.File

/**
 * Snapshot tests for 'Source Providers'.
 *
 * The pre-recorded sync results can be found in testData/sourceProvidersSnapshots/ *.txt files.
 *
 * NOTE: It you made changes to sync or the test projects which make these tests fail in an expected way, you can re-run the tests
 *       from IDE with -DUPDATE_TEST_SNAPSHOTS to update the files.
 *
 *       Or with bazel:
bazel test //tools/adt/idea/android:intellij.android.core.tests_tests  --test_sharding_strategy=disabled  \
--test_filter="SourceProvidersSnapshotComparisonTest" --nocache_test_results --strategy=TestRunner=standalone \
--jvmopt='-DUPDATE_TEST_SNAPSHOTS' --test_output=streamed
 */
class SourceProvidersSnapshotComparisonTest : AndroidGradleTestCase(), SnapshotComparisonTest {
  override val snapshotDirectoryName = "sourceProvidersSnapshots"

  fun testSimpleApplication() {
    val text = importSyncAndDumpProject(TestProjectPaths.SIMPLE_APPLICATION)
    assertIsEqualToSnapshot(text)
  }

  fun testNestedProjects() {
    val text = importSyncAndDumpProject(TestProjectPaths.PSD_SAMPLE)
    assertIsEqualToSnapshot(text)
  }

  fun testCompositeBuild() {
    val text = importSyncAndDumpProject(TestProjectPaths.COMPOSITE_BUILD)
    assertIsEqualToSnapshot(text)
  }

  fun testDependentNativeModules() {
    val text = importSyncAndDumpProject(TestProjectPaths.DEPENDENT_NATIVE_MODULES)
    assertIsEqualToSnapshot(text)
  }

  fun testJpsWithQualifiedNames() {
    val srcPath = File(myFixture.testDataPath, toSystemDependentName(TestProjectPaths.JPS_WITH_QUALIFIED_NAMES))
    // Prepare project in a different directory (_jps) to avoid closing the currently opened project.
    val projectPath = File(toSystemDependentName(project.basePath + "_jps"))

    AndroidGradleTests.prepareProjectForImportCore(srcPath, projectPath) { projectRoot ->
      // Override settings just for tests (e.g. sdk.dir)
      AndroidGradleTests.updateLocalProperties(projectRoot, findSdkPath())
    }

    val project = PlatformTestUtil.loadAndOpenProject(projectPath.toPath())
    val text = project.dumpSourceProviders()
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)

    assertIsEqualToSnapshot(text)
  }

  private fun importSyncAndDumpProject(
    projectDir: String,
    patch: ((projectRootPath: File) -> Unit)? = null
  ): String {
    val projectRootPath = prepareProjectForImport(projectDir)
    patch?.invoke(projectRootPath)
    importProject()

    return project.dumpSourceProviders()
  }

  @Suppress("DEPRECATION")
  private fun Project.dumpSourceProviders(): String {
    val projectRootPath = File(basePath)
    return buildString {
      var prefix = ""

      fun out(s: String) = appendln("$prefix$s")

      fun <T> nest(title: String? = null, code: () -> T): T {
        if (title != null) {
          out(title)
        }
        prefix = "    $prefix"
        val result = code()
        prefix = prefix.substring(4)
        return result
      }

      fun File.toPrintablePath(): String {
        val relativePath = absoluteFile.relativeTo(projectRootPath)
        return if (!relativePath.isAbsolute) "./${relativePath.path}" else relativePath.path
      }

      fun <T, F> T.dumpPaths(name: String, getter: (T) -> Collection<F>, mapper: (F) -> File?) {
        val entries = getter(this)
        if (entries.isEmpty()) return
        out("$name:")
        nest {
          entries
            .mapNotNull(mapper)
            .forEach {
              out(it.toPrintablePath())
            }
        }
      }

      fun SourceProvider.dumpPaths(name: String, getter: (SourceProvider) -> Collection<File>) =
        dumpPaths(name, getter) { it }

      fun IdeaSourceProvider.dumpPaths(name: String, getter: (IdeaSourceProvider) -> Collection<VirtualFile?>) =
        dumpPaths(name, getter) { it?.toIoFile() }

      fun SourceProvider.dump() {
        out(name)
        nest {
          dumpPaths("Manifest") { listOf(manifestFile) }
          dumpPaths("AidlDirectories") { it.aidlDirectories }
          dumpPaths("AssetsDirectories") { it.assetsDirectories }
          dumpPaths("CDirectories") { it.cDirectories }
          dumpPaths("CppDirectories") { it.cppDirectories }
          dumpPaths("JavaDirectories") { it.javaDirectories }
          dumpPaths("JniLibsDirectories") { it.jniLibsDirectories }
          dumpPaths("RenderscriptDirectories") { it.renderscriptDirectories }
          dumpPaths("ResDirectories") { it.resDirectories }
          dumpPaths("ResourcesDirectories") { it.resourcesDirectories }
          dumpPaths("ShadersDirectories") { it.shadersDirectories }
        }
      }

      fun IdeaSourceProvider.dump() {
        out("$name (IDEA)")
        nest {
          dumpPaths("Manifest") { listOf(manifestFile) }
          dumpPaths("AidlDirectories") { it.aidlDirectories }
          dumpPaths("AssetsDirectories") { it.assetsDirectories }
          dumpPaths("JavaDirectories") { it.javaDirectories }
          dumpPaths("JniLibsDirectories") { it.jniLibsDirectories }
          dumpPaths("RenderscriptDirectories") { it.renderscriptDirectories }
          dumpPaths("ResDirectories") { it.resDirectories }
          dumpPaths("ResourcesDirectories") { it.resourcesDirectories }
          dumpPaths("ShadersDirectories") { it.shadersDirectories }
        }
      }

      ModuleManager
        .getInstance(this@dumpSourceProviders)
        .modules
        .sortedBy { it.name }
        .forEach { module ->
          out("MODULE: ${module.name}");
          val androidFacet = AndroidFacet.getInstance(module)
          if (androidFacet != null) {
            nest {
              nest("by Facet:") {
                val sourceProviderManager = SourceProviderManager.getInstance(androidFacet)
                sourceProviderManager.mainSourceProvider.dump()
                sourceProviderManager.mainIdeaSourceProvider.dump()
              }
              val model = AndroidModel.get(module)
              if (model != null) {
                nest("by AndroidModel:") {
                  model.defaultSourceProvider.dump()
                  nest("Active:") { model.activeSourceProviders.forEach { it.dump() } }
                  nest("All:") { model.allSourceProviders.forEach { it.dump() } }
                  nest("Test:") { model.testSourceProviders.forEach { it.dump() } }
                }
              }
              nest("by IdeaSourceProviders:") {
                dumpPaths("Manifests", { IdeaSourceProvider.getManifestFiles(androidFacet) }, { it.toIoFile() })
                nest("AllIdeaSourceProviders:") { IdeaSourceProvider.getAllIdeaSourceProviders(androidFacet).forEach { it.dump() } }
                nest("AllSourceProviders:") { IdeaSourceProvider.getAllSourceProviders(androidFacet).forEach { it.dump() } }
                nest("CurrentSourceProviders:") { IdeaSourceProvider.getCurrentSourceProviders(androidFacet).forEach { it.dump() } }
                nest("CurrentTestSourceProviders:") { IdeaSourceProvider.getCurrentTestSourceProviders(androidFacet).forEach { it.dump() } }
              }
            }
          }
        }
    }
      .trimIndent()
  }
}

