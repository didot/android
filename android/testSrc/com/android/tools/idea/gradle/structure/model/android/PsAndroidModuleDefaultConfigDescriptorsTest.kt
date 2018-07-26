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

import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.meta.*
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat

class PsAndroidModuleDefaultConfigDescriptorsTest : AndroidGradleTestCase() {

  fun testProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    run {
      val appModule = project.findModuleByName("app") as PsAndroidModule
      assertThat(appModule, notNullValue())
      val defaultConfig = appModule.defaultConfig

      val applicationId = PsAndroidModuleDefaultConfigDescriptors.applicationId.bind(defaultConfig).getValue()
      val applicationIdSuffix = PsAndroidModuleDefaultConfigDescriptors.applicationIdSuffix.bind(defaultConfig).getValue()
      val maxSdkVersion = PsAndroidModuleDefaultConfigDescriptors.maxSdkVersion.bind(defaultConfig).getValue()
      val minSdkVersion = PsAndroidModuleDefaultConfigDescriptors.minSdkVersion.bind(defaultConfig).getValue()
      val multiDexEnabled = PsAndroidModuleDefaultConfigDescriptors.multiDexEnabled.bind(defaultConfig).getValue()
      val signingConfig = PsAndroidModuleDefaultConfigDescriptors.signingConfig.bind(defaultConfig).getValue()
      val targetSdkVersion = PsAndroidModuleDefaultConfigDescriptors.targetSdkVersion.bind(defaultConfig).getValue()
      val testApplicationId = PsAndroidModuleDefaultConfigDescriptors.testApplicationId.bind(defaultConfig).getValue()
      val testFunctionalTest = PsAndroidModuleDefaultConfigDescriptors.testFunctionalTest.bind(defaultConfig).getValue()
      val testHandleProfiling = PsAndroidModuleDefaultConfigDescriptors.testHandleProfiling.bind(defaultConfig).getValue()
      val testInstrumentationRunner = PsAndroidModuleDefaultConfigDescriptors.testInstrumentationRunner.bind(defaultConfig).getValue()
      val versionCode = PsAndroidModuleDefaultConfigDescriptors.versionCode.bind(defaultConfig).getValue()
      val versionName = PsAndroidModuleDefaultConfigDescriptors.versionName.bind(defaultConfig).getValue()
      val versionNameSuffix = PsAndroidModuleDefaultConfigDescriptors.versionNameSuffix.bind(defaultConfig).getValue()
      val proGuardFiles = PsAndroidModuleDefaultConfigDescriptors.proGuardFiles.bind(defaultConfig).getValue()
      val resConfigs = PsAndroidModuleDefaultConfigDescriptors.resConfigs.bind(defaultConfig).getValue()
      val manifestPlaceholders = PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.bind(defaultConfig).getValue()
      val editableManifestPlaceholders =
        PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.bind(defaultConfig).getEditableValues()
          .mapValues { it.value.getValue() }

      assertThat(applicationId.resolved.asTestValue(), equalTo("com.example.psd.sample.app.default"))
      assertThat(applicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.default"))

      assertThat(applicationIdSuffix.resolved.asTestValue(), equalTo("defaultSuffix"))
      assertThat(applicationIdSuffix.parsedValue.asTestValue(), equalTo("defaultSuffix"))

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

      // TODO(b/111630584): assertThat(testFunctionalTest.resolved.asTestValue(), equalTo(false))
      assertThat(testFunctionalTest.parsedValue.asTestValue(), equalTo(false))

      // TODO(b/111630584): assertThat(testHandleProfiling.resolved.asTestValue(), nullValue())
      assertThat(testHandleProfiling.parsedValue.asTestValue(), nullValue())

      assertThat(versionCode.resolved.asTestValue(), equalTo(1))
      assertThat(versionCode.parsedValue.asTestValue(), equalTo(1))

      assertThat(versionName.resolved.asTestValue(), equalTo("1.0"))
      assertThat(versionName.parsedValue.asTestValue(), equalTo("1.0"))

      assertThat(versionNameSuffix.resolved.asTestValue(), equalTo("vns"))
      assertThat(versionNameSuffix.parsedValue.asTestValue(), equalTo("vns"))

      assertThat(proGuardFiles.resolved.asTestValue(), equalTo(listOf()))
      assertThat(proGuardFiles.parsedValue.asTestValue(), nullValue())

      assertThat(resConfigs.resolved.asTestValue(), equalTo(listOf()))
      assertThat(resConfigs.parsedValue.asTestValue(), nullValue())

      assertThat(manifestPlaceholders.resolved.asTestValue(), equalTo(mapOf("aa" to "aaa", "bb" to "bbb", "cc" to "ccc")))
      assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(mapOf("aa" to "aaa", "bb" to "bbb", "cc" to "ccc")))

      assertThat(editableManifestPlaceholders["aa"]?.resolved?.asTestValue(), equalTo("aaa"))
      assertThat(editableManifestPlaceholders["aa"]?.parsedValue?.asTestValue(), equalTo("aaa"))

      assertThat(editableManifestPlaceholders["bb"]?.resolved?.asTestValue(), equalTo("bbb"))
      assertThat(editableManifestPlaceholders["bb"]?.parsedValue?.asTestValue(), equalTo("bbb"))

      assertThat(editableManifestPlaceholders["cc"]?.resolved?.asTestValue(), equalTo("ccc"))
      assertThat(editableManifestPlaceholders["cc"]?.parsedValue?.asTestValue(), equalTo("ccc"))
    }

    run {
      val module = project.findModuleByGradlePath(":nested1:deep") as PsAndroidModule
      assertThat(module, notNullValue())
      val defaultConfig = module.defaultConfig

      val resConfigs = PsAndroidModuleDefaultConfigDescriptors.resConfigs.bind(defaultConfig).getValue()
      assertThat(resConfigs.resolved.asTestValue()?.toSet(), equalTo(setOf("en", "fr")))
      // TODO(b/111779356): Uncommented when fixed.
      // assertThat(resConfigs.parsedValue.asTestValue()?.toSet(), equalTo(setOf("en", "fr")))
    }
  }

  fun testSetProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val defaultConfig = appModule.defaultConfig
    assertThat(defaultConfig, notNullValue())

    defaultConfig.applicationId = "com.example.psd.sample.app.unpaid".asParsed()
    defaultConfig.applicationIdSuffix = "newSuffix".asParsed()
    defaultConfig.maxSdkVersion = 26.asParsed()
    defaultConfig.minSdkVersion = "20".asParsed()
    defaultConfig.multiDexEnabled = false.asParsed()
    defaultConfig.targetSdkVersion = "21".asParsed()
    defaultConfig.testApplicationId = "com.example.psd.sample.app.unpaid.failed_test".asParsed()
    defaultConfig.testInstrumentationRunner = "com.runner".asParsed()
    defaultConfig.testFunctionalTest = ParsedValue.NotSet
    defaultConfig.testHandleProfiling = true.asParsed()
    // TODO(b/79531524): find out why it fails.
    // defaultConfig.versionCode = "3".asParsed()
    defaultConfig.versionName = "3.0".asParsed()
    defaultConfig.versionNameSuffix = "newVns".asParsed()
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
      val applicationIdSuffix = PsAndroidModuleDefaultConfigDescriptors.applicationIdSuffix.bind(defaultConfig).getValue()
      val maxSdkVersion = PsAndroidModuleDefaultConfigDescriptors.maxSdkVersion.bind(defaultConfig).getValue()
      val minSdkVersion = PsAndroidModuleDefaultConfigDescriptors.minSdkVersion.bind(defaultConfig).getValue()
      val multiDexEnabled = PsAndroidModuleDefaultConfigDescriptors.multiDexEnabled.bind(defaultConfig).getValue()
      val signingConfig = PsAndroidModuleDefaultConfigDescriptors.signingConfig.bind(defaultConfig).getValue()
      val targetSdkVersion = PsAndroidModuleDefaultConfigDescriptors.targetSdkVersion.bind(defaultConfig).getValue()
      val testApplicationId = PsAndroidModuleDefaultConfigDescriptors.testApplicationId.bind(defaultConfig).getValue()
      val testFunctionalTest = PsAndroidModuleDefaultConfigDescriptors.testFunctionalTest.bind(defaultConfig).getValue()
      val testHandleProfiling = PsAndroidModuleDefaultConfigDescriptors.testHandleProfiling.bind(defaultConfig).getValue()
      val testInstrumentationRunner = PsAndroidModuleDefaultConfigDescriptors.testInstrumentationRunner.bind(defaultConfig).getValue()
      // TODO(b/79531524): find out why it fails.
      // val versionCode = PsAndroidModuleDefaultConfigDescriptors.versionCode.bind(defaultConfig).getValue()
      val versionName = PsAndroidModuleDefaultConfigDescriptors.versionName.bind(defaultConfig).getValue()
      val versionNameSuffix = PsAndroidModuleDefaultConfigDescriptors.versionNameSuffix.bind(defaultConfig).getValue()
      val manifestPlaceholders = PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.bind(defaultConfig).getValue()

      assertThat(applicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.unpaid"))
      assertThat(applicationIdSuffix.parsedValue.asTestValue(), equalTo("newSuffix"))
      assertThat(maxSdkVersion.parsedValue.asTestValue(), equalTo(26))
      assertThat(minSdkVersion.parsedValue.asTestValue(), equalTo("20"))
      assertThat(multiDexEnabled.parsedValue.asTestValue(), equalTo(false))
      assertThat(signingConfig.resolved.asTestValue(), nullValue())
      assertThat(
        signingConfig.parsedValue,
        equalTo<Annotated<ParsedValue<Unit>>>(ParsedValue.Set.Parsed(Unit, DslText.Reference("signingConfigs.myConfig")).annotated()))
      // TODO(b/71988818)
      assertThat(targetSdkVersion.parsedValue.asTestValue(), equalTo("21"))
      assertThat(testApplicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.unpaid.failed_test"))
      assertThat(testInstrumentationRunner.parsedValue.asTestValue(), equalTo("com.runner"))
      assertThat(testFunctionalTest.parsedValue.asTestValue(), nullValue())
      assertThat(testHandleProfiling.parsedValue.asTestValue(), equalTo(true))
      // TODO(b/79531524): find out why it fails.
      // assertThat(versionCode.parsedValue.asTestValue(), equalTo("3"))
      assertThat(versionName.parsedValue.asTestValue(), equalTo("3.0"))
      assertThat(versionNameSuffix.parsedValue.asTestValue(), equalTo("newVns"))
      assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(mapOf("bb" to "bbb", "ee" to "EEE", "nn" to "NNN")))

      if (afterSync) {
        assertThat(applicationId.parsedValue.asTestValue(), equalTo(applicationId.resolved.asTestValue()))
        assertThat(applicationIdSuffix.parsedValue.asTestValue(), equalTo(applicationIdSuffix.resolved.asTestValue()))
        assertThat(maxSdkVersion.parsedValue.asTestValue(), equalTo(maxSdkVersion.resolved.asTestValue()))
        assertThat(minSdkVersion.parsedValue.asTestValue(), equalTo(minSdkVersion.resolved.asTestValue()))
        assertThat(multiDexEnabled.parsedValue.asTestValue(), equalTo(multiDexEnabled.resolved.asTestValue()))
        // TODO(b/79142681) signingConfig resolved value is always null.
        // TODO(b/71988818)
        assertThat(targetSdkVersion.parsedValue.asTestValue(), equalTo(targetSdkVersion.resolved.asTestValue()))
        assertThat(testApplicationId.parsedValue.asTestValue(), equalTo(testApplicationId.resolved.asTestValue()))
        assertThat(testInstrumentationRunner.parsedValue.asTestValue(), equalTo(testInstrumentationRunner.resolved.asTestValue()))
        // TODO(b/111630584): assertThat(testFunctionalTest.parsedValue.asTestValue(), equalTo(testFunctionalTest.resolved.asTestValue()))
        // TODO(b/111630584): assertThat(testHandleProfiling.parsedValue.asTestValue(), equalTo(testHandleProfiling.resolved.asTestValue()))
        // TODO(b/79531524): find out why it fails.
        // assertThat(versionCode.parsedValue.asTestValue(), equalTo(versionCode.resolved.asTestValue()))
        assertThat(versionName.parsedValue.asTestValue(), equalTo(versionName.resolved.asTestValue()))
        assertThat(versionNameSuffix.parsedValue.asTestValue(), equalTo(versionNameSuffix.resolved.asTestValue()))
        assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(manifestPlaceholders.resolved.asTestValue()))
      }
    }
    verifyValues(defaultConfig)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.defaultConfig, afterSync = true)
  }

  fun testMapProperties_delete() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

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
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.defaultConfig, afterSync = true)
  }

  fun testMapProperties_editorInserting() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

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
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.defaultConfig, afterSync = true)
  }

  fun testProGuardKnownValues() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val defaultConfig = appModule.defaultConfig
    assertThat(defaultConfig, notNullValue())

    val context = PsAndroidModuleDefaultConfigDescriptors.proGuardFiles.bindContext(null, defaultConfig)
    val knownValues = context.getKnownValues().get()
    assertThat(knownValues.literals.map { it.value.getText { toString() } }.toSet(),
               equalTo(setOf("other.pro", "proguard-rules.txt", "\$getDefaultProguardFile('proguard-android.txt')")))
  }
}
