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
package com.android.tools.idea.gradle

import com.android.builder.model.SyncIssue
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.io.File
import java.nio.file.Paths

class LibrarySamplesTest : AndroidGradleTestCase() {
  override fun setUp() {
    super.setUp()
    StudioFlags.SAMPLES_SUPPORT_ENABLED.override(true)
  }

  override fun tearDown() {
    StudioFlags.SAMPLES_SUPPORT_ENABLED.clearOverride()
    super.tearDown()
  }

  override fun getAdditionalRepos() = listOf(
    File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectPaths.REPO_FOR_SAMPLES_ARTIFACT_TEST)))

  fun testDownloadingAndAttachingSamples() {
    loadProject(TestProjectPaths.APP_WITH_LIB_WITH_SAMPLES) {
      // ignore missing manifest errors
      it.type == SyncIssue.TYPE_MISSING_ANDROID_MANIFEST
    }

    val libraryFilePaths = LibraryFilePaths.getInstance(myFixture.project)
    // Pass empty path as library path to make sure that sample sources are from maven, not from local directory.
    val samples = libraryFilePaths.findSampleSourcesJarPath("com.example.libraryWithSamples:lib1:1.0.0", File(""))
    // We download samples only for androidx libraries.
    assume().that(samples).isNull()

    val androidxSamples = libraryFilePaths.findSampleSourcesJarPath("androidx.ui.libraryWithSamples:lib1:1.0.0", File(""))
    assume().that(androidxSamples).isNotNull()
    assertThat(androidxSamples!!.name).isEqualTo("lib1-1.0.0-${AdditionalClassifierArtifactsModel.SAMPLE_SOURCE_CLASSIFIER}.jar")
  }

  fun testResolveSampleReference() {
    loadProject(TestProjectPaths.APP_WITH_LIB_WITH_SAMPLES) {
      // ignore missing manifest errors
      it.type == SyncIssue.TYPE_MISSING_ANDROID_MANIFEST
    }

    val file = VfsUtil.findFile(Paths.get(project.basePath, "/app/src/main/java/com/example/appforsamplestest/Main.kt"), false)
    assume().that(file).isNotNull()
/* b/154963473
    myFixture.configureFromExistingVirtualFile(file!!)

    myFixture.moveCaret("myFuncti|on")
    val librarySourceFunction = myFixture.elementAtCaret.navigationElement as KtNamedFunction
    assume().that(librarySourceFunction).isNotNull()

    val sampleTag = librarySourceFunction.docComment!!.getDefaultSection().findTagByName("sample")!!
    val sample = PsiTreeUtil.findChildOfType<KDocName>(sampleTag, KDocName::class.java)?.mainReference?.resolve()
    assume().that(sample).isNotNull()
    // For library structure see testData/projects/psdSampleRepo/com/example/libraryWithSamples/lib1/1.0.0
    assertThat(sample!!.getKotlinFqName()!!.asString()).isEqualTo("androidx.samples.sampleFunction")
b/154963473 */
  }
}