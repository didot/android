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

import com.android.testutils.TestUtils
import com.android.tools.idea.navigator.nodes.ndk.includes.view.IncludesViewNode
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.GroupByTypeComparator
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.ui.DeferredIcon
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RowIcon
import org.jetbrains.annotations.SystemIndependent
import sun.swing.ImageIconUIResource
import java.io.File
import javax.swing.Icon

class AndroidGradleProjectViewSnapshotComparisonTest : AndroidGradleTestCase(), GradleIntegrationTest, SnapshotComparisonTest {
  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/projectViews"
  override fun getTestDataDirectoryWorkspaceRelativePath(): @SystemIndependent String = "tools/adt/idea/android/testData/snapshots"

  data class ProjectViewSettings(
    val hideEmptyPackages: Boolean = true,
    val flattenPackages: Boolean = false
  )

  fun testSimpleApplication() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION)
    assertIsEqualToSnapshot(text)
  }

  fun testWithMlModels() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.APP_WITH_ML_MODELS)
    assertIsEqualToSnapshot(text)
  }

  fun testMultiFlavor() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.MULTI_FLAVOR)
    assertIsEqualToSnapshot(text)
  }

  fun testMultiFlavor_flattenPackages() {
    val text = importSyncAndDumpProject(
      TestProjectToSnapshotPaths.MULTI_FLAVOR,
      projectViewSettings = ProjectViewSettings(hideEmptyPackages = true, flattenPackages = true)
    )
    // TODO(b/153882815): Update the snapshot when fixed. Currently source code nodes are missing.
    assertIsEqualToSnapshot(text)
  }

  fun testNestedProjects() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY)
    assertIsEqualToSnapshot(text)
  }

  fun testNavigatorPackageViewCommonRoots_compact() {
    val text = importSyncAndDumpProject(
      TestProjectToSnapshotPaths.NAVIGATOR_PACKAGEVIEW_COMMONROOTS,
      projectViewSettings = ProjectViewSettings(hideEmptyPackages = true)
    )
    assertIsEqualToSnapshot(text)
  }

  fun testNavigatorPackageViewCommonRoots_notCompact() {
    val text = importSyncAndDumpProject(
      TestProjectToSnapshotPaths.NAVIGATOR_PACKAGEVIEW_COMMONROOTS,
      projectViewSettings = ProjectViewSettings(hideEmptyPackages = false)
    )
    assertIsEqualToSnapshot(text)
  }

  fun testNavigatorPackageViewSimple() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.NAVIGATOR_PACKAGEVIEW_SIMPLE)
    assertIsEqualToSnapshot(text)
  }

  fun testCompositeBuild() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.COMPOSITE_BUILD)
    assertIsEqualToSnapshot(text)
  }

  fun testWithBuildSrc() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.APP_WITH_BUILDSRC)
    assertIsEqualToSnapshot(text)
  }

  fun testNdkProject() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.HELLO_JNI, initialState = false, filter = this::filterOutMostIncludeFiles)
    assertIsEqualToSnapshot(text)
  }

  fun testBasicCmakeApp() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.BASIC_CMAKE_APP, initialState = false, filter = this::filterOutMostIncludeFiles)
    assertIsEqualToSnapshot(text)
  }

  fun testDependentNativeModules() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.DEPENDENT_NATIVE_MODULES, initialState = false,
                                        filter = this::filterOutMostIncludeFiles)
    assertIsEqualToSnapshot(text)
  }

  fun testJpsWithQualifiedNames() {
    val srcPath = File(myFixture.testDataPath, toSystemDependentName(TestProjectToSnapshotPaths.JPS_WITH_QUALIFIED_NAMES))
    // Prepare project in a different directory (_jps) to avoid closing the currently opened project.
    val projectPath = File(toSystemDependentName(project.basePath + "_jps"))

    AndroidGradleTests.prepareProjectForImportCore(srcPath, projectPath) { projectRoot ->
      // Override settings just for tests (e.g. sdk.dir)
      AndroidGradleTests.updateLocalProperties(projectRoot, TestUtils.getSdk())
    }

    val project = ProjectUtil.openProject(projectPath.absolutePath, null, false)!!
    val text = project.dumpAndroidProjectView()
    ProjectUtil.closeAndDispose(project)

    assertIsEqualToSnapshot(text)
  }

  fun testCompatibilityWithAndroidStudio36Project() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.COMPATIBILITY_TESTS_AS_36)
    assertIsEqualToSnapshot(text)
  }

  fun testCompatibilityWithAndroidStudio36NoImlProject() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.COMPATIBILITY_TESTS_AS_36_NO_IML)
    assertIsEqualToSnapshot(text)
  }

  fun testMissingImlIsIgnored() {
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION_CORRUPTED_MISSING_IML_40, "testMissingImlIsIgnored_Test")
    val text = openPreparedProject("testMissingImlIsIgnored_Test" ) { project: Project ->
    project.dumpAndroidProjectView()
  }

    assertIsEqualToSnapshot(text)
  }

  private fun importSyncAndDumpProject(
    projectDir: String,
    patch: ((projectRootPath: File) -> Unit)? = null,
    projectViewSettings: ProjectViewSettings = ProjectViewSettings()
  ): String =
    importSyncAndDumpProject(projectDir, patch, projectViewSettings, Unit, { _, _ -> Unit })

  private fun <T : Any> importSyncAndDumpProject(
    projectDir: String,
    patch: ((projectRootPath: File) -> Unit)? = null,
    projectViewSettings: ProjectViewSettings = ProjectViewSettings(),
    initialState: T,
    filter: (element: AbstractTreeNode<*>, state: T) -> T?
  ): String {
    val projectRootPath = prepareProjectForImport(projectDir)
    patch?.invoke(projectRootPath)
    importProject()

    return project.dumpAndroidProjectView(projectViewSettings, initialState, filter)
  }

  private fun Project.dumpAndroidProjectView(): String = dumpAndroidProjectView(initialState = Unit) { _, _ -> Unit }

  private fun <T : Any> Project.dumpAndroidProjectView(
    projectViewSettings: ProjectViewSettings = ProjectViewSettings(),
    initialState: T,
    filter: (element: AbstractTreeNode<*>, state: T) -> T?
  ): String {

    val androidSdk: File = IdeSdks.getInstance().androidSdkPath!!

    fun String.replaceVariableParts(): String {
      val userHomePath = System.getProperty("user.home")
      val androidSdkAbsolutePath = androidSdk.absolutePath
      val androidSdkUserRootedPath = androidSdk.absolutePath.replace(userHomePath, "~")
      return replace(androidSdkAbsolutePath, "<ANDROID_SDK>", ignoreCase = false)
        .replace(androidSdkUserRootedPath, "<ANDROID_SDK>", ignoreCase = false)
        .replace(userHomePath, "<HOME>", ignoreCase = false)
    }

    fun Icon.getIconText(): Icon? {
      var icon: Icon? = this
      do {
        val previous = icon
        icon = if (icon is DeferredIcon) icon.evaluate() else icon
        icon = if (icon is RowIcon && icon.allIcons.size == 1) icon.getIcon(0) else icon
        icon = if (icon is LayeredIcon && icon.allLayers.size == 1) icon.getIcon(0) else icon
      }
      while (previous != icon)
      return icon
    }

    fun PresentationData.toTestText(): String {
      val icon = getIcon(false)?.getIconText()
      val iconText =
        (icon as? IconLoader.CachedImageIcon)?.originalPath
        ?: (icon as? ImageIconUIResource)?.let { it.description ?: "ImageIconUIResource(?)" }
        ?: icon?.let { "$it (${it.javaClass.simpleName})" }
      val nodeText =
        if (coloredText.isEmpty()) presentableText
        else coloredText.joinToString(separator = "") { it.text }

      return buildString {
        append(nodeText)
        if (iconText != null) append(" (icon: $iconText)")
      }
        .replaceVariableParts()
    }

    fun createAndDumpProjectView(): String {
      val viewPane = AndroidProjectViewPane(this)
      // We need to create a component to initialize the view pane.
      viewPane.createComponent()
      val treeStructure: AbstractTreeStructure? = viewPane.treeStructure
      val rootElement = treeStructure?.rootElement ?: return ""
      // In production sorting happens when the tree builder asynchronously populates the UI. It uses the following comparator, by default,
      // which, unfortunately, is not accessible via a public API.
      val comparator = GroupByTypeComparator(null, AndroidProjectViewPane.ID)

      return buildString {

        fun dump(element: AbstractTreeNode<*>, prefix: String = "", state: T) {
          val newState = filter(element, state) ?: return

          appendln("$prefix${element.presentation.toTestText()}")
          treeStructure
            .getChildElements(element)
            .map { it as AbstractTreeNode<*> }
            .apply { forEach { it.update() } }
            .sortedWith(comparator)
            .forEach { dump(it, "    $prefix", newState) }
        }

        dump(rootElement as AbstractTreeNode<*>, state = initialState)
      }
        // Trim the trailing line end since snapshots are loaded without it.
        .trimEnd()
    }

    fun applySettings(settings: ProjectViewSettings) {
      ProjectView.getInstance(this).apply {
        setHideEmptyPackages(AndroidProjectViewPane.ID, settings.hideEmptyPackages)
        (this as ProjectViewImpl).setFlattenPackages(AndroidProjectViewPane.ID, settings.flattenPackages);
      }
    }

    fun getCurrentSettings(): ProjectViewSettings = ProjectView.getInstance(this).let { view ->
      ProjectViewSettings(
        hideEmptyPackages = view.isHideEmptyMiddlePackages(AndroidProjectViewPane.ID),
        flattenPackages = view.isFlattenPackages(AndroidProjectViewPane.ID)
      )
    }

    val oldSettings = getCurrentSettings()
    applySettings(projectViewSettings)
    return try {
      createAndDumpProjectView()
    }
    finally {
      applySettings(oldSettings)
    }
  }

  private fun filterOutMostIncludeFiles(element: AbstractTreeNode<*>, state: Boolean): Boolean? {
    return when {
      element is IncludesViewNode -> true
      state && element is PsiDirectoryNode && element.name == "android" -> state
      state && element is PsiDirectoryNode -> null
      state && element is PsiFileNode && element.name?.endsWith("native_activity.h") == true -> state
      state && element is PsiFileNode -> null
      else -> state
    }
  }
}
