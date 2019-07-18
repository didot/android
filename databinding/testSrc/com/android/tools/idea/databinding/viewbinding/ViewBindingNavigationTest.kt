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
package com.android.tools.idea.databinding.viewbinding

import com.android.flags.junit.RestoreFlagRule
import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.isViewBindingEnabled
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.res.binding.BindingLayoutInfoFile
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.ui.UIUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class ViewBindingNavigationTest {
  private val projectRule = AndroidGradleProjectRule()

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @get:Rule
  val viewBindingFlagRule = RestoreFlagRule(StudioFlags.VIEW_BINDING_ENABLED)

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   */
  private val fixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  private val editorManager
    get() = FileEditorManager.getInstance(projectRule.project)

  @Before
  fun setUp() {
    StudioFlags.VIEW_BINDING_ENABLED.override(true)

    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.load(TestDataPaths.PROJECT_FOR_VIEWBINDING)

    val syncState = GradleSyncState.getInstance(projectRule.project)
    assertThat(syncState.isSyncNeeded().toBoolean()).isFalse()

    // Make sure that all file system events up to this point have been processed.
    VirtualFileManager.getInstance().syncRefresh()
    UIUtil.dispatchAllInvocationEvents()

    assertThat(projectRule.androidFacet.isViewBindingEnabled()).isTrue()
  }

  @Test
  fun navigateLightViewBindingClass() {
    assertThat(editorManager.selectedFiles).isEmpty()
    // ActivityMainBinding is in-memory and generated on the fly from activity_main.xml
    val binding = fixture.findClass("com.android.example.viewbinding.databinding.ActivityMainBinding") as LightBindingClass
    binding.navigate(true)
    assertThat(editorManager.selectedFiles[0].name).isEqualTo("activity_main.xml")

    // Additionally, let's verify the behavior of the LightBindingClass's navigation element, for
    // code coverage purposes.
    binding.navigationElement.let { navElement ->
      assertThat(navElement).isInstanceOf(BindingLayoutInfoFile::class.java)
      assertThat(navElement.containingFile).isSameAs(navElement)
      // This next cast has to be true or else Java code coverage will crash. More details in the
      // header docs of BindingLayoutInfoFile
      val psiClassOwner = navElement.containingFile as PsiClassOwner
      assertThat(psiClassOwner.classes).hasLength(1)
      assertThat(psiClassOwner.classes[0]).isEqualTo(binding)
      assertThat(psiClassOwner.packageName).isEqualTo("com.android.example.viewbinding.databinding")
    }
  }

  @Test
  fun navigateLightViewBindingField() {
    assertThat(editorManager.selectedFiles).isEmpty()
    // ActivityMainBinding is in-memory and generated on the fly from activity_main.xml
    val binding = fixture.findClass("com.android.example.viewbinding.databinding.ActivityMainBinding").findFieldByName("testId", false)!!
    binding.navigate(true)
    assertThat(editorManager.selectedFiles[0].name).isEqualTo("activity_main.xml")
    assertThat(binding.navigationElement).isInstanceOf(XmlTag::class.java)
    assertThat(binding.navigationElement.text).contains("id=\"@+id/testId\"")
  }
}