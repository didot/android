/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ResolvedValue
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat

class PsAndroidModuleDefaultConfigDescriptorsTest : AndroidGradleTestCase() {

  private fun <T> ResolvedValue<T>.asTestValue(): T? = (this as? ResolvedValue.Set<T>)?.resolved
  private fun <T> ParsedValue<T>.asTestValue(): T? = (this as? ParsedValue.Set.Parsed<T>)?.value
  private fun <T : Any> T.asParsed(): ParsedValue<T> = ParsedValue.Set.Parsed(this, DslText.Literal)

  fun testProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())
    val defaultConfig = appModule.defaultConfig

    val applicationId = PsAndroidModuleDefaultConfigDescriptors.applicationId.bind(defaultConfig).getValue()
    val maxSdkVersion = PsAndroidModuleDefaultConfigDescriptors.maxSdkVersion.bind(defaultConfig).getValue()
    val minSdkVersion = PsAndroidModuleDefaultConfigDescriptors.minSdkVersion.bind(defaultConfig).getValue()
    val multiDexEnabled = PsAndroidModuleDefaultConfigDescriptors.multiDexEnabled.bind(defaultConfig).getValue()
    val signingConfig = PsAndroidModuleDefaultConfigDescriptors.signingConfig.bind(defaultConfig).getValue()
    val targetSdkVersion = PsAndroidModuleDefaultConfigDescriptors.targetSdkVersion.bind(defaultConfig).getValue()
    val testApplicationId = PsAndroidModuleDefaultConfigDescriptors.testApplicationId.bind(defaultConfig).getValue()
    // TODO(b/70501607): Decide on val testFunctionalTest = PsAndroidModuleDefaultConfigDescriptors.testFunctionalTest.getValue(defaultConfig)
    // TODO(b/70501607): Decide on val testHandleProfiling = PsAndroidModuleDefaultConfigDescriptors.testHandleProfiling.getValue(defaultConfig)
    val testInstrumentationRunner = PsAndroidModuleDefaultConfigDescriptors.testInstrumentationRunner.bind(defaultConfig).getValue()
    val versionCode = PsAndroidModuleDefaultConfigDescriptors.versionCode.bind(defaultConfig).getValue()
    val versionName = PsAndroidModuleDefaultConfigDescriptors.versionName.bind(defaultConfig).getValue()
    val proGuardFiles = PsAndroidModuleDefaultConfigDescriptors.proGuardFiles.bind(defaultConfig).getValue()
    val manifestPlaceholders = PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.bind(defaultConfig).getValue()
    val editableManifestPlaceholders =
      PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.bind(defaultConfig).getEditableValues()
        .mapValues { it.value.getValue() }

    assertThat(applicationId.resolved.asTestValue(), equalTo("com.example.psd.sample.app.default"))
    assertThat(applicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.default"))

    assertThat(maxSdkVersion.resolved.asTestValue(), equalTo(26))
    assertThat(maxSdkVersion.parsedValue.asTestValue(), equalTo(26))

    assertThat(minSdkVersion.resolved.asTestValue(), equalTo("9"))
    assertThat(minSdkVersion.parsedValue.asTestValue(), equalTo("9"))

    assertThat(multiDexEnabled.resolved.asTestValue(), nullValue())
    assertThat(multiDexEnabled.parsedValue.asTestValue(), nullValue())

    assertThat(signingConfig.resolved.asTestValue(), nullValue())
    assertThat(signingConfig.parsedValue.asTestValue(), nullValue())

    assertThat(targetSdkVersion.resolved.asTestValue(), equalTo("19"))
    // TODO(b/71988818) assertThat(targetSdkVersion.parsedValue.asTestValue(), equalTo("19"))

    assertThat(testApplicationId.resolved.asTestValue(), equalTo("com.example.psd.sample.app.default.test"))
    assertThat(testApplicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.default.test"))

    assertThat(testInstrumentationRunner.resolved.asTestValue(), nullValue())
    assertThat(testInstrumentationRunner.parsedValue.asTestValue(), nullValue())

    assertThat(versionCode.resolved.asTestValue(), equalTo("1"))
    assertThat(versionCode.parsedValue.asTestValue(), equalTo("1"))

    assertThat(versionName.resolved.asTestValue(), equalTo("1.0"))
    assertThat(versionName.parsedValue.asTestValue(), equalTo("1.0"))

    assertThat(proGuardFiles.resolved.asTestValue(), equalTo(listOf()))
    assertThat(proGuardFiles.parsedValue.asTestValue(), nullValue())

    assertThat(manifestPlaceholders.resolved.asTestValue(), equalTo(mapOf("aa" to "aaa", "bb" to "bbb", "cc" to "ccc")))
    assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(mapOf("aa" to "aaa", "bb" to "bbb", "cc" to "ccc")))

    assertThat(editableManifestPlaceholders["aa"]?.resolved?.asTestValue(), equalTo("aaa"))
    assertThat(editableManifestPlaceholders["aa"]?.parsedValue?.asTestValue(), equalTo("aaa"))

    assertThat(editableManifestPlaceholders["bb"]?.resolved?.asTestValue(), equalTo("bbb"))
    assertThat(editableManifestPlaceholders["bb"]?.parsedValue?.asTestValue(), equalTo("bbb"))

    assertThat(editableManifestPlaceholders["cc"]?.resolved?.asTestValue(), equalTo("ccc"))
    assertThat(editableManifestPlaceholders["cc"]?.parsedValue?.asTestValue(), equalTo("ccc"))
  }

  fun testSetProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val defaultConfig = appModule.defaultConfig
    assertThat(defaultConfig, notNullValue())

    defaultConfig.applicationId = "com.example.psd.sample.app.unpaid".asParsed()
    defaultConfig.maxSdkVersion = 26.asParsed()
    defaultConfig.minSdkVersion = "20".asParsed()
    defaultConfig.multiDexEnabled = false.asParsed()
    defaultConfig.targetSdkVersion = "21".asParsed()
    defaultConfig.testApplicationId = "com.example.psd.sample.app.unpaid.failed_test".asParsed()
    defaultConfig.testInstrumentationRunner = "com.runner".asParsed()
    // TODO(b/79531524): find out why it fails.
    // defaultConfig.versionCode = "3".asParsed()
    defaultConfig.versionName = "3.0".asParsed()
    PsAndroidModuleDefaultConfigDescriptors.signingConfig.bind(defaultConfig).setParsedValue(
      ParsedValue.Set.Parsed(Unit, DslText.Reference("signingConfigs.myConfig")))
    PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.bind(defaultConfig).run {
      getEditableValues()["aa"]?.setParsedValue("EEE".asParsed())
      changeEntryKey("aa", "ee")
      deleteEntry("cc")
      addEntry("nn").setParsedValue("NNN".asParsed())
    }

    fun verifyValues(defaultConfig: PsAndroidModuleDefaultConfig, afterSync: Boolean = false) {
      val applicationId = PsAndroidModuleDefaultConfigDescriptors.applicationId.bind(defaultConfig).getValue()
      val maxSdkVersion = PsAndroidModuleDefaultConfigDescriptors.maxSdkVersion.bind(defaultConfig).getValue()
      val minSdkVersion = PsAndroidModuleDefaultConfigDescriptors.minSdkVersion.bind(defaultConfig).getValue()
      val multiDexEnabled = PsAndroidModuleDefaultConfigDescriptors.multiDexEnabled.bind(defaultConfig).getValue()
      val signingConfig = PsAndroidModuleDefaultConfigDescriptors.signingConfig.bind(defaultConfig).getValue()
      val targetSdkVersion = PsAndroidModuleDefaultConfigDescriptors.targetSdkVersion.bind(defaultConfig).getValue()
      val testApplicationId = PsAndroidModuleDefaultConfigDescriptors.testApplicationId.bind(defaultConfig).getValue()
      // TODO(b/70501607): Decide on val testFunctionalTest = PsAndroidModuleDefaultConfigDescriptors.testFunctionalTest.getValue(defaultConfig)
      // TODO(b/70501607): Decide on val testHandleProfiling = PsAndroidModuleDefaultConfigDescriptors.testHandleProfiling.getValue(defaultConfig)
      val testInstrumentationRunner = PsAndroidModuleDefaultConfigDescriptors.testInstrumentationRunner.bind(defaultConfig).getValue()
      // TODO(b/79531524): find out why it fails.
      // val versionCode = PsAndroidModuleDefaultConfigDescriptors.versionCode.bind(defaultConfig).getValue()
      val versionName = PsAndroidModuleDefaultConfigDescriptors.versionName.bind(defaultConfig).getValue()
      val manifestPlaceholders = PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.bind(defaultConfig).getValue()

      assertThat(applicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.unpaid"))
      assertThat(maxSdkVersion.parsedValue.asTestValue(), equalTo(26))
      assertThat(minSdkVersion.parsedValue.asTestValue(), equalTo("20"))
      assertThat(multiDexEnabled.parsedValue.asTestValue(), equalTo(false))
      assertThat(signingConfig.resolved.asTestValue(), nullValue())
      assertThat(
        signingConfig.parsedValue,
        equalTo<ParsedValue<Unit>>(ParsedValue.Set.Parsed(Unit, DslText.Reference("signingConfigs.myConfig"))))
      // TODO(b/71988818)
      assertThat(targetSdkVersion.parsedValue.asTestValue(), equalTo("21"))
      assertThat(testApplicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.unpaid.failed_test"))
      assertThat(testInstrumentationRunner.parsedValue.asTestValue(), equalTo("com.runner"))
      // TODO(b/79531524): find out why it fails.
      // assertThat(versionCode.parsedValue.asTestValue(), equalTo("3"))
      assertThat(versionName.parsedValue.asTestValue(), equalTo("3.0"))
      assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(mapOf("bb" to "bbb", "ee" to "EEE", "nn" to "NNN")))

      if (afterSync) {
        assertThat(applicationId.parsedValue.asTestValue(), equalTo(applicationId.resolved.asTestValue()))
        assertThat(maxSdkVersion.parsedValue.asTestValue(), equalTo(maxSdkVersion.resolved.asTestValue()))
        assertThat(minSdkVersion.parsedValue.asTestValue(), equalTo(minSdkVersion.resolved.asTestValue()))
        assertThat(multiDexEnabled.parsedValue.asTestValue(), equalTo(multiDexEnabled.resolved.asTestValue()))
        // TODO(b/79142681) signingConfig resolved value is always null.
        // TODO(b/71988818)
        assertThat(targetSdkVersion.parsedValue.asTestValue(), equalTo(targetSdkVersion.resolved.asTestValue()))
        assertThat(testApplicationId.parsedValue.asTestValue(), equalTo(testApplicationId.resolved.asTestValue()))
        assertThat(testInstrumentationRunner.parsedValue.asTestValue(), equalTo(testInstrumentationRunner.resolved.asTestValue()))
        // TODO(b/79531524): find out why it fails.
        // assertThat(versionCode.parsedValue.asTestValue(), equalTo(versionCode.resolved.asTestValue()))
        assertThat(versionName.parsedValue.asTestValue(), equalTo(versionName.resolved.asTestValue()))
        assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(manifestPlaceholders.resolved.asTestValue()))
      }
    }
    verifyValues(defaultConfig)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProject(resolvedProject)
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.defaultConfig, afterSync = true)
  }

  fun testMapProperties_delete() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val defaultConfig = appModule.defaultConfig
    assertThat(defaultConfig, notNullValue())

    PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.bind(defaultConfig).run {
      deleteEntry("bb")
    }

    fun verifyValues(defaultConfig: PsAndroidModuleDefaultConfig, afterSync: Boolean = false) {
      val manifestPlaceholders = PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.bind(defaultConfig).getValue()

      assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(mapOf("aa" to "aaa", "cc" to "ccc")))

      if (afterSync) {
        assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(manifestPlaceholders.resolved.asTestValue()))
      }
    }

    verifyValues(defaultConfig)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProject(resolvedProject)
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.defaultConfig, afterSync = true)
  }

  fun testMapProperties_editorInserting() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val defaultConfig = appModule.defaultConfig
    assertThat(defaultConfig, notNullValue())

    PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.bind(defaultConfig).run {
      addEntry("").setParsedValue("q".asParsed())
      changeEntryKey("", "q")
    }

    fun verifyValues(defaultConfig: PsAndroidModuleDefaultConfig, afterSync: Boolean = false) {
      val manifestPlaceholders = PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.bind(defaultConfig).getValue()

      assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(mapOf("aa" to "aaa", "bb" to "bbb", "cc" to "ccc", "q" to "q")))

      if (afterSync) {
        assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(manifestPlaceholders.resolved.asTestValue()))
      }
    }

    verifyValues(defaultConfig)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProject(resolvedProject)
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.defaultConfig, afterSync = true)
  }
}
