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
import com.intellij.testFramework.RunsInEdt
import org.junit.Test

@RunsInEdt
class GradlePluginsRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @Test
  fun testKotlinPluginVersionInLiteral() {
    writeToBuildFile(TestFileName("GradlePlugins/KotlinPluginVersionInLiteral"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/KotlinPluginVersionInLiteralExpected"))
  }

  @Test
  fun testKotlinPluginVersionInDsl() {
    writeToBuildFile(TestFileName("GradlePlugins/KotlinPluginVersionInDsl"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/KotlinPluginVersionInDslExpected"))
  }

  @Test
  fun testKotlinPluginVersionInSettings() {
    writeToSettingsFile(TestFileName("GradlePlugins/KotlinPluginVersionInSettings"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(settingsFile, TestFileName("GradlePlugins/KotlinPluginVersionInSettingsExpected"))
  }

  @Test
  fun testKotlinPluginNewEnoughVersionInLiteral() {
    writeToBuildFile(TestFileName("GradlePlugins/KotlinPluginNewEnoughVersionInLiteral"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("GradlePlugins/KotlinPluginNewEnoughVersionInLiteral"))
  }

  @Test
  fun testKotlinPluginNewEnoughVersionInDsl() {
    writeToBuildFile(TestFileName("GradlePlugins/KotlinPluginNewEnoughVersionInDsl"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("GradlePlugins/KotlinPluginNewEnoughVersionInDsl"))
  }

  @Test
  fun testKotlinPluginNewEnoughVersionInSettings() {
    writeToSettingsFile(TestFileName("GradlePlugins/KotlinPluginNewEnoughVersionInSettings"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(settingsFile, TestFileName("GradlePlugins/KotlinPluginNewEnoughVersionInSettings"))
  }

  @Test
  fun testKotlinPluginVersionInInterpolatedVariable() {
    writeToBuildFile(TestFileName("GradlePlugins/KotlinPluginVersionInInterpolatedVariable"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/KotlinPluginVersionInInterpolatedVariableExpected"))
  }

  @Test
  fun testKotlinPluginNewEnoughVersionInInterpolatedVariable() {
    writeToBuildFile(TestFileName("GradlePlugins/KotlinPluginNewEnoughVersionInInterpolatedVariable"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("GradlePlugins/KotlinPluginNewEnoughVersionInInterpolatedVariable"))
  }

  @Test
  fun testKotlinPluginVersionPlus() {
    writeToBuildFile(TestFileName("GradlePlugins/KotlinPluginVersionPlus"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/KotlinPluginVersionPlus"))
  }

  @Test
  fun testKotlinPluginUnknownVersion() {
    writeToBuildFile(TestFileName("GradlePlugins/KotlinPluginUnknownVersion"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("GradlePlugins/KotlinPluginUnknownVersion"))
  }

  @Test
  fun testKotlinPluginVersionInLiteral70() {
    writeToBuildFile(TestFileName("GradlePlugins/KotlinPluginVersionInLiteral"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/KotlinPluginVersionInLiteral70Expected"))
  }

  @Test
  fun testKotlinPluginVersionInDsl70() {
    writeToBuildFile(TestFileName("GradlePlugins/KotlinPluginVersionInDsl"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/KotlinPluginVersionInDsl70Expected"))
  }

  @Test
  fun testSafeArgsVersionInLiteral() {
    writeToBuildFile(TestFileName("GradlePlugins/SafeArgsVersionInLiteral"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/SafeArgsVersionInLiteral"))
  }

  @Test
  fun testSafeArgsVersionInInterpolatedVariable() {
    writeToBuildFile(TestFileName("GradlePlugins/SafeArgsVersionInInterpolatedVariable"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/SafeArgsVersionInInterpolatedVariable"))
  }

  @Test
  fun testSafeArgsVersionInDsl() {
    writeToBuildFile(TestFileName("GradlePlugins/SafeArgsVersionInDsl"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/SafeArgsVersionInDsl"))
  }

  @Test
  fun testAndroidJUnit5VersionTo400() {
    writeToBuildFile(TestFileName("GradlePlugins/AndroidJUnit5Version"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.6.0"), GradleVersion.parse("4.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/AndroidJUnit5VersionTo400Expected"))
  }

  @Test
  fun testAndroidJUnit5VersionTo410() {
    writeToBuildFile(TestFileName("GradlePlugins/AndroidJUnit5Version"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.6.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/AndroidJUnit5VersionTo410Expected"))
  }

  @Test
  fun testAndroidJUnit5VersionInDslTo410() {
    writeToBuildFile(TestFileName("GradlePlugins/AndroidJUnit5VersionInDsl"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.6.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/AndroidJUnit5VersionInDslTo410Expected"))
  }

  @Test
  fun testFirebaseCrashlyticsVersionTo420() {
    writeToBuildFile(TestFileName("GradlePlugins/FirebaseCrashlyticsVersion"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("4.2.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/FirebaseCrashlyticsVersionTo420Expected"))
  }

  @Test
  fun testFirebaseCrashlyticsVersionTo700() {
    writeToBuildFile(TestFileName("GradlePlugins/FirebaseCrashlyticsVersion"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/FirebaseCrashlyticsVersionTo700Expected"))
  }

  @Test
  fun testFirebaseCrashlyticsVersionInDslTo700() {
    writeToBuildFile(TestFileName("GradlePlugins/FirebaseCrashlyticsVersionInDsl"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/FirebaseCrashlyticsVersionInDslTo700Expected"))
  }

  @Test
  fun testFirebaseAppdistributionVersionTo400() {
    writeToBuildFile(TestFileName("GradlePlugins/FirebaseAppdistributionVersion"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/FirebaseAppdistributionVersionTo400Expected"))
  }

  @Test
  fun testFirebaseAppdistributionVersionTo700() {
    writeToBuildFile(TestFileName("GradlePlugins/FirebaseAppdistributionVersion"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/FirebaseAppdistributionVersionTo700Expected"))
  }

  @Test
  fun testFirebaseAppdistributionVersionInDslTo400() {
    writeToBuildFile(TestFileName("GradlePlugins/FirebaseAppdistributionVersionInDsl"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/FirebaseAppdistributionVersionInDslTo400Expected"))
  }

  @Test
  fun testFirebaseAppdistributionVersionInDslTo700() {
    writeToBuildFile(TestFileName("GradlePlugins/FirebaseAppdistributionVersionInDsl"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/FirebaseAppdistributionVersionInDslTo700Expected"))
  }

  @Test
  fun testGoogleOssLicensesVersionTo700() {
    writeToBuildFile(TestFileName("GradlePlugins/GoogleOssLicensesVersion"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/GoogleOssLicensesVersionTo700Expected"))
  }

  @Test
  fun testGoogleOssLicensesVersionInDslTo700() {
    writeToBuildFile(TestFileName("GradlePlugins/GoogleOssLicensesVersionInDsl"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradlePlugins/GoogleOssLicensesVersionInDslTo700Expected"))
  }


}