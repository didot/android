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
package com.android.tools.idea.editors.manifest

import com.android.tools.idea.editors.manifest.ManifestPanel.ManifestTreeNode
import com.android.tools.idea.gradle.project.sync.internal.ProjectDumper
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.assertAreEqualToSnapshots
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.util.androidFacet
import com.android.utils.FileUtils.toSystemIndependentPath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.w3c.dom.Node
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

@RunWith(JUnit4::class)
class ManifestPanelContentTest : GradleIntegrationTest, SnapshotComparisonTest {

  companion object {
    private const val MANIFEST_REPORT_SNAPSHOT_SUFFIX = "_manifest_report.html"
    private const val MERGED_MANIFEST_SHAPSHOT_SUFFIX = "_merged_manifest.xml"
  }
  @get:Rule
  var testName = TestName()
  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData"
  override fun getAdditionalRepos(): Collection<File> = listOf()

  override val snapshotDirectoryWorkspaceRelativePath = Paths
    .get(getTestDataDirectoryWorkspaceRelativePath())
    .resolve(TestProjectPaths.NAVIGATION_EDITOR_INCLUDE_FROM_LIB)
    .resolve("snapshots")
    .toString()

  override fun getName(): String = testName.methodName

  @Test
  fun contentForNavigationEditor_includeFromLib() {
    val projectPath = TestProjectPaths.NAVIGATION_EDITOR_INCLUDE_FROM_LIB
    val projectRoot = prepareGradleProject(projectPath, "project")
    openPreparedProject("project") { project ->
      val appModule = project.gradleModule(":app")?.getMainModule() ?: error("Cannot find :app module")
      val appModuleFacet = appModule.androidFacet ?: error("Cannot find the facet for :app")

      val mergedManifest = MergedManifestManager.getMergedManifestSupplier(appModule).get().get(2, TimeUnit.SECONDS)

      val panel = ManifestPanel(appModuleFacet, projectRule.testRootDisposable)
      panel.showManifest(mergedManifest, appModuleFacet.sourceProviders.mainManifestFile!!, false)
      val detailsPaneContent = panel.detailsPane.text
      val manifestPaneContent = (panel.tree.model.root as ManifestTreeNode).userObject.transformToString()

      ProjectDumper().nest(projectRoot, "PROJECT_DIR") {
        assertAreEqualToSnapshots(
          normalizeContentForTest(detailsPaneContent) to MANIFEST_REPORT_SNAPSHOT_SUFFIX,
          normalizeContentForTest(manifestPaneContent) to MERGED_MANIFEST_SHAPSHOT_SUFFIX
        )
      }
    }
  }

  /* Goes through each line, removing empty lines and replacing hyperlinks with files with stable naming across different config/runs. */
  private fun ProjectDumper.normalizeContentForTest(htmlString: String) = htmlString
    .lines()
    .filter { it.trim().isNotEmpty() }
    .joinToString(separator = "\n", postfix = "\n") {
      it.replace(Regex("\"file:(.*)\"")) { matchResult ->
        val fileAndPosition = matchResult.groupValues[1]
        val (file, suffix) = splitFileAndSuffixPosition(fileAndPosition)
        // build.gradle file depends on the source location, so replacing that with something generic
        if (file.endsWith("build.gradle")) {
          "<BUILD.GRADLE>"
        } else {
          "'${toSystemIndependentPath(File(file).absolutePath).toPrintablePath()}$suffix'"
        }
      }.trimEnd()
    }.trimIndent()


  private fun splitFileAndSuffixPosition(fileAndPosition: String): Pair<String, String> {
    var suffixPosition = fileAndPosition.length
    var columns = 0
    while (suffixPosition > 0 && columns <= 2 && fileAndPosition[suffixPosition - 1].let { it.isDigit() || it == ':' }) {
      suffixPosition--
      if (fileAndPosition[suffixPosition] == ':') columns++
    }
    if (columns < 2) suffixPosition = fileAndPosition.length
    return fileAndPosition.substring(0, suffixPosition) to fileAndPosition.substring(suffixPosition, fileAndPosition.length)
  }

  private fun Node.transformToString() =
    StringWriter().let {
      TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      }.transform(DOMSource(this), StreamResult(it))
      it.buffer.toString()
    }
}