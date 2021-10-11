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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.*
import com.google.common.truth.Expect
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class AndroidManifestPackageToNamespaceRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  private lateinit var manifestFile : VirtualFile

  @Before
  fun setUpManifestFile() {
    runWriteAction {
      manifestFile = projectRule.fixture.tempDirFixture.createFile("src/main/AndroidManifest.xml")
      assertTrue(manifestFile.isWritable)
    }
  }

  @Test
  fun testNecessities() {
    val expectedNecessitiesMap = mapOf(
      ("4.0.0" to "4.1.0") to IRRELEVANT_FUTURE,
      ("4.1.0" to "4.2.0") to OPTIONAL_CODEPENDENT,
      ("7.0.0" to "7.1.0") to OPTIONAL_INDEPENDENT,
      ("4.2.0" to "8.0.0") to MANDATORY_INDEPENDENT,
      ("4.1.0" to "8.0.0") to MANDATORY_CODEPENDENT,
      ("8.0.0" to "8.1.0") to IRRELEVANT_PAST
    )
    expectedNecessitiesMap.forEach { (t, u) ->
      val processor = AndroidManifestPackageToNamespaceRefactoringProcessor(project, GradleVersion.parse(t.first), GradleVersion.parse(t.second))
      expect.that(processor.necessity()).isEqualTo(u)
    }
  }

  @Test
  fun testPackageToNamespace() {
    writeToBuildFile(TestFileName("AndroidManifestPackageToNamespace/PackageToNamespace"))
    writeToManifestFile(TestFileName("AndroidManifestPackageToNamespace/ManifestWithPackage"))
    val processor = AndroidManifestPackageToNamespaceRefactoringProcessor(project, GradleVersion.parse("4.0.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AndroidManifestPackageToNamespace/PackageToNamespaceExpected"))
    val expectedText = FileUtil.loadFile(TestFileName("AndroidManifestPackageToNamespace/ManifestWithoutPackage").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(manifestFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testPackageToConflictingNamespace() {
    writeToBuildFile(TestFileName("AndroidManifestPackageToNamespace/PackageToConflictingNamespace"))
    writeToManifestFile(TestFileName("AndroidManifestPackageToNamespace/ManifestWithPackage"))
    val processor = AndroidManifestPackageToNamespaceRefactoringProcessor(project, GradleVersion.parse("4.0.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AndroidManifestPackageToNamespace/PackageToConflictingNamespaceExpected"))
    val expectedText = FileUtil.loadFile(TestFileName("AndroidManifestPackageToNamespace/ManifestWithoutPackage").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(manifestFile)
    assertEquals(expectedText, actualText)
  }

  private fun writeToManifestFile(fileName: TestFileName) {
    val testFile = fileName.toFile(testDataPath, "")
    assertTrue(testFile.exists())
    val virtualTestFile = VfsUtil.findFileByIoFile(testFile, true)
    runWriteAction { VfsUtil.saveText(manifestFile, VfsUtilCore.loadText(virtualTestFile!!)) }
  }
}