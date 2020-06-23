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
package com.android.tools.idea.gradle.plugin

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.Java8DefaultRefactoringProcessor
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@RunsInEdt
class Java8DefaultRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @Test
  fun testIsDisabledFor420Alpha04() {
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("4.2.0-alpha04"))
    assertFalse(processor.isEnabled)
  }

  @Test
  fun testIsEnabledFor420Alpha05() {
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("4.2.0-alpha05"))
    assertTrue(processor.isEnabled)
  }

  @Test
  fun testIsDisabledFrom420Alpha05() {
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.2.0-alpha05"), GradleVersion.parse("4.2.0"))
    assertFalse(processor.isEnabled)
  }

  @Test
  fun testIsEnabledFor420Release() {
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    assertTrue(processor.isEnabled)
  }

  @Test
  fun testSimpleApplicationNoLanguageLevel() {
    writeToBuildFile(TestFileName("Java8Default/SimpleApplicationNoLanguageLevel"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleApplicationNoLanguageLevelExpected"))
  }

  @Test
  fun testSimpleApplicationNoLanguageLevelInsertOld() {
    writeToBuildFile(TestFileName("Java8Default/SimpleApplicationNoLanguageLevel"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.noLanguageLevelAction = INSERT_OLD_DEFAULT
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleApplicationNoLanguageLevelExpected"))
  }

  @Test
  fun testSimpleApplicationNoLanguageLevelAcceptNew() {
    writeToBuildFile(TestFileName("Java8Default/SimpleApplicationNoLanguageLevel"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.noLanguageLevelAction = ACCEPT_NEW_DEFAULT
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleApplicationNoLanguageLevel"))
  }

  @Test
  fun testSimpleApplicationWithKotlinNoLanguageLevel() {
    writeToBuildFile(TestFileName("Java8Default/SimpleApplicationWithKotlinNoLanguageLevel"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleApplicationWithKotlinNoLanguageLevelExpected"))
  }

  @Test
  fun testSimpleApplicationWithKotlinNoLanguageLevelInsertOld() {
    writeToBuildFile(TestFileName("Java8Default/SimpleApplicationWithKotlinNoLanguageLevel"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.noLanguageLevelAction = INSERT_OLD_DEFAULT
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleApplicationWithKotlinNoLanguageLevelExpected"))
  }

  @Test
  fun testSimpleApplicationWithKotlinNoLanguageLevelAcceptNew() {
    writeToBuildFile(TestFileName("Java8Default/SimpleApplicationWithKotlinNoLanguageLevel"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.noLanguageLevelAction = ACCEPT_NEW_DEFAULT
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleApplicationWithKotlinNoLanguageLevel"))
  }

  @Test
  fun testSimpleApplicationExplicitLanguageLevel7() {
    writeToBuildFile(TestFileName("Java8Default/SimpleApplicationExplicitLanguageLevel7"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleApplicationExplicitLanguageLevel7"))
  }

  @Test
  fun testSimpleApplicationExplicitLanguageLevel8() {
    writeToBuildFile(TestFileName("Java8Default/SimpleApplicationExplicitLanguageLevel8"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleApplicationExplicitLanguageLevel8"))
  }

  @Test
  fun testSimpleJavaLibraryNoLanguageLevel() {
    writeToBuildFile(TestFileName("Java8Default/SimpleJavaLibraryNoLanguageLevel"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleJavaLibraryNoLanguageLevelExpected"))
  }

  @Test
  fun testSimpleJavaLibraryNoLanguageLevelInsertOld() {
    writeToBuildFile(TestFileName("Java8Default/SimpleJavaLibraryNoLanguageLevel"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.noLanguageLevelAction = INSERT_OLD_DEFAULT
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleJavaLibraryNoLanguageLevelExpected"))
  }

  @Test
  fun testSimpleJavaLibraryNoLanguageLevelAcceptNew() {
    writeToBuildFile(TestFileName("Java8Default/SimpleJavaLibraryNoLanguageLevel"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.noLanguageLevelAction = ACCEPT_NEW_DEFAULT
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleJavaLibraryNoLanguageLevel"))
  }

  @Test
  fun testSimpleJavaLibraryExplicitLanguageLevel7() {
    writeToBuildFile(TestFileName("Java8Default/SimpleJavaLibraryExplicitLanguageLevel7"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleJavaLibraryExplicitLanguageLevel7"))
  }

  @Test
  fun testSimpleJavaLibraryExplicitLanguageLevel8() {
    writeToBuildFile(TestFileName("Java8Default/SimpleJavaLibraryExplicitLanguageLevel8"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleJavaLibraryExplicitLanguageLevel8"))
  }
}