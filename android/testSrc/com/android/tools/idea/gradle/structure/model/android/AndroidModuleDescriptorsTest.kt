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

import com.android.SdkConstants
import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.OBJECT_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.helpers.matchHashStrings
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.PSD_SAMPLE_GROOVY
import com.android.tools.idea.testing.TestProjectPaths.PSD_SAMPLE_KOTLIN
import com.intellij.pom.java.LanguageLevel
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat

class AndroidModuleDescriptorsTest : AndroidGradleTestCase() {

  fun testDescriptor() {
    loadProject(PSD_SAMPLE_GROOVY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule.descriptor.testEnumerateProperties(), equalTo(AndroidModuleDescriptors.testEnumerateProperties()))
  }

  fun testProperties() {
    loadProject(PSD_SAMPLE_GROOVY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildToolsVersion = AndroidModuleDescriptors.buildToolsVersion.bind(appModule).getValue()
    val compileSdkVersion = AndroidModuleDescriptors.compileSdkVersion.bind(appModule).getValue()
    val sourceCompatibility = AndroidModuleDescriptors.sourceCompatibility.bind(appModule).getValue()
    val targetCompatibility = AndroidModuleDescriptors.targetCompatibility.bind(appModule).getValue()
    val viewBindingEnabled = AndroidModuleDescriptors.viewBindingEnabled.bind(appModule).getValue()

    assertThat(buildToolsVersion.resolved.asTestValue(), equalTo(SdkConstants.CURRENT_BUILD_TOOLS_VERSION))
    assertThat(buildToolsVersion.parsedValue.asTestValue(), equalTo(SdkConstants.CURRENT_BUILD_TOOLS_VERSION))

    assertThat(matchHashStrings(null, compileSdkVersion.resolved.asTestValue(), SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString()),
               equalTo(true))
    assertThat(compileSdkVersion.parsedValue.asTestValue(), equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString()))

    assertThat(sourceCompatibility.resolved.asTestValue(), equalTo(LanguageLevel.JDK_1_7))
    assertThat(sourceCompatibility.parsedValue.asTestValue(), nullValue())

    assertThat(targetCompatibility.resolved.asTestValue(), equalTo(LanguageLevel.JDK_1_7))
    assertThat(targetCompatibility.parsedValue.asTestValue(), nullValue())

    assertThat(viewBindingEnabled.resolved.asTestValue(), equalTo(false))
    assertThat(viewBindingEnabled.parsedValue.asTestValue(), nullValue())
  }

  fun testSetProperties() {
    // Note: this test does not attempt to sync because it won't succeed without installing older SDKs.
    loadProject(PSD_SAMPLE_GROOVY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    appModule.compileSdkVersion = "25".asParsed()
    appModule.viewBindingEnabled = true.asParsed()
    appModule.buildToolsVersion = ParsedValue.Set.Parsed(dslText = DslText.Reference("varProGuardFiles[0]"), value = null)

    fun verifyValues(appModule: PsAndroidModule) {
      val compileSdkVersion = AndroidModuleDescriptors.compileSdkVersion.bind(appModule).getValue()
      val viewBindingEnabled = AndroidModuleDescriptors.viewBindingEnabled.bind(appModule).getValue()
      assertThat(compileSdkVersion.parsedValue.asTestValue(), equalTo("25"))
      assertThat(viewBindingEnabled.parsedValue.asTestValue(), equalTo(true))
      assertThat(appModule.parsedModel?.android()?.compileSdkVersion()?.getValue(OBJECT_TYPE), equalTo<Any>(25))
      assertThat(appModule.parsedModel?.android()?.viewBinding()?.enabled()?.getValue(OBJECT_TYPE), equalTo<Any>(true))
    }

    verifyValues(appModule)
    appModule.applyChanges()
    verifyValues(appModule)
  }

  fun testSetListReferencesKotlin() {
    loadProject(PSD_SAMPLE_KOTLIN)
    val expectedKtsRawValues = listOf("localList[0]", "(rootProject.extra[\"listProp\"] as List<*>)[0] as Integer")
    doTestSetListReferences(expectedKtsRawValues)
  }

  fun testSetListReferencesGroovy() {
    loadProject(PSD_SAMPLE_GROOVY)
    val expectedGrRawValues = listOf("localList[0]", "listProp[0]")
    doTestSetListReferences(expectedGrRawValues)
  }

  private fun doTestSetListReferences(expectedValues: List<String>) {
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())


    // Set reference to a list extra property from same module.
    appModule.buildToolsVersion = ParsedValue.Set.Parsed(dslText = DslText.Reference("localList[0]"), value = null)
    // Set reference to a list extra property defined in rootProject build script.
    appModule.compileSdkVersion = ParsedValue.Set.Parsed(dslText = DslText.Reference("listProp[0]"), value = null)

    appModule.applyChanges()

    // Verify changes applied correctly.
    val buildToolsVersion = AndroidModuleDescriptors.buildToolsVersion.bind(appModule).getValue()
    val compileSdkVersion = AndroidModuleDescriptors.compileSdkVersion.bind(appModule).getValue()
    assertThat(buildToolsVersion.parsedValue.asTestValue(), equalTo("26.1.1"))
    assertThat(compileSdkVersion.parsedValue.asTestValue(), equalTo("15"))
    assertThat(appModule.parsedModel?.android()?.buildToolsVersion()?.getValue(OBJECT_TYPE), equalTo<Any>("26.1.1"))
    assertThat(appModule.parsedModel?.android()?.buildToolsVersion()?.getRawValue(STRING_TYPE), equalTo<Any>(expectedValues[0]))

    assertThat(appModule.parsedModel?.android()?.compileSdkVersion()?.getValue(OBJECT_TYPE), equalTo<Any>(15))
    assertThat(appModule.parsedModel?.android()?.compileSdkVersion()?.getRawValue(STRING_TYPE), equalTo<Any>(expectedValues[1]))
  }
}
