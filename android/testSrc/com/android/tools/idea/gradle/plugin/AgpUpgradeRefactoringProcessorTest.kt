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

import com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.upgrade.AgpGradleVersionRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.CompileRuntimeConfigurationRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.GMavenRepositoryRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor
import com.android.tools.idea.testing.IdeComponents
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@RunsInEdt
class AgpUpgradeRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @Before
  fun replaceSyncInvoker() {
    // At the moment, the AgpUpgrade refactoring processor itself runs sync, which means that we need to
    // replace the invoker with a fake one here (because we are not working on complete projects which
    // sync correctly).
    //
    // The location of the sync invoker might move out from here, perhaps to a subscriber to a message bus
    // listening for refactoring events, at which point this would no longer be necessary (though we might
    // need to make sure that there is no listener triggering sync while running unit tests).
    val ideComponents = IdeComponents(projectRule.fixture)
    ideComponents.replaceApplicationService(GradleSyncInvoker::class.java, GradleSyncInvoker.FakeInvoker())
  }

  private fun everythingDisabledNoEffectOn(filename: String) {
    writeToBuildFile(TestFileName(filename))
    val latestKnownVersion = ANDROID_GRADLE_PLUGIN_VERSION
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("1.0.0"), GradleVersion.parse(latestKnownVersion))
    processor.classpathRefactoringProcessor.isEnabled = false
    processor.componentRefactoringProcessors.forEach { it.isEnabled = false }
    processor.run()
    verifyFileContents(buildFile, TestFileName(filename))
  }

  @Test
  fun testEverythingDisabledNoEffectOnAgpClasspathDependency() {
    everythingDisabledNoEffectOn("AgpClasspathDependency/VersionInLiteral")
  }

  @Test
  fun testEverythingDisabledNoEffectOnJava8Default() {
    everythingDisabledNoEffectOn("Java8Default/SimpleApplicationNoLanguageLevel")
  }

  @Test
  fun testEverythingDisabledNoEffectOnCompileRuntimeConfiguration() {
    everythingDisabledNoEffectOn("CompileRuntimeConfiguration/SimpleApplication")
  }

  @Test
  fun testEverythingDisabledNoEffectOnGMavenRepository() {
    everythingDisabledNoEffectOn("GMavenRepository/AGP2Project")
  }

  @Ignore("gradle-wrapper.properties is not a build file") // TODO(b/152854665)
  fun testEverythingDisabledNoEffectOnAgpGradleVersion() {
    everythingDisabledNoEffectOn("AgpGradleVersion/OldGradleVersion")
  }

  @Test
  fun testEnabledEffectOnAgpClasspathDependency() {
    writeToBuildFile(TestFileName("AgpClasspathDependency/VersionInLiteral"))
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.classpathRefactoringProcessor.isEnabled = true
    processor.componentRefactoringProcessors.forEach { it.isEnabled = false }
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpClasspathDependency/VersionInLiteralExpected"))
  }

  @Test
  fun testEnabledEffectOnJava8Default() {
    writeToBuildFile(TestFileName("Java8Default/SimpleApplicationNoLanguageLevel"))
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.classpathRefactoringProcessor.isEnabled = false
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it is Java8DefaultRefactoringProcessor }
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleApplicationNoLanguageLevelExpected"))
  }

  @Test
  fun testEnabledEffectOnCompileRuntimeConfiguration() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleApplication"))
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("5.0.0"))
    processor.classpathRefactoringProcessor.isEnabled = false
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it is CompileRuntimeConfigurationRefactoringProcessor }
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleApplicationExpected"))
  }

  @Test
  fun testEnabledEffectOnGMavenRepository() {
    writeToBuildFile(TestFileName("GMavenRepository/AGP2Project"))
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("2.3.2"), GradleVersion.parse("4.2.0"))
    processor.classpathRefactoringProcessor.isEnabled = false
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it is GMavenRepositoryRefactoringProcessor }
    processor.componentRefactoringProcessors
      .filterIsInstance<GMavenRepositoryRefactoringProcessor>()
      .forEach { it.gradleVersion = GradleVersion.parse("6.5") }
    processor.run()
    verifyFileContents(buildFile, TestFileName("GMavenRepository/AGP2ProjectExpected"))
  }

  @Ignore("gradle-wrapper.properties is not a build file") // TODO(b/152854665)
  fun testEnabledEffectOnAgpGradleVersion() {
    writeToBuildFile(TestFileName("AgpGradleVersion/OldGradleVersion"))
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("5.0.0"))
    processor.classpathRefactoringProcessor.isEnabled = false
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it is AgpGradleVersionRefactoringProcessor }
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/OldGradleVersionExpected"))
  }
}