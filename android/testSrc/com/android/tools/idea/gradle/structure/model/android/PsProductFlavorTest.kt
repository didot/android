/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat

class PsProductFlavorTest : AndroidGradleTestCase() {

  fun testDescriptor() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val productFlavor = appModule.findProductFlavor("foo", "paid")
    assertThat(productFlavor, notNullValue()); productFlavor!!

    assertThat(productFlavor.descriptor.testEnumerateProperties(),
               equalTo(PsProductFlavor.ProductFlavorDescriptors.testEnumerateProperties()))
  }

  fun testProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    run {
      val productFlavor = appModule.findProductFlavor("foo", "paid")
      assertThat(productFlavor, notNullValue()); productFlavor!!

      val applicationId = PsProductFlavor.ProductFlavorDescriptors.applicationId.bind(productFlavor).getValue()
      val applicationIdSuffix = PsProductFlavor.ProductFlavorDescriptors.applicationIdSuffix.bind(productFlavor).getValue()
      val dimension = PsProductFlavor.ProductFlavorDescriptors.dimension.bind(productFlavor).getValue()
      val maxSdkVersion = PsProductFlavor.ProductFlavorDescriptors.maxSdkVersion.bind(productFlavor).getValue()
      val minSdkVersion = PsProductFlavor.ProductFlavorDescriptors.minSdkVersion.bind(productFlavor).getValue()
      val multiDexEnabled = PsProductFlavor.ProductFlavorDescriptors.multiDexEnabled.bind(productFlavor).getValue()
      val signingConfig = PsProductFlavor.ProductFlavorDescriptors.signingConfig.bind(productFlavor).getValue()
      val targetSdkVersion = PsProductFlavor.ProductFlavorDescriptors.targetSdkVersion.bind(productFlavor).getValue()
      val testApplicationId = PsProductFlavor.ProductFlavorDescriptors.testApplicationId.bind(productFlavor).getValue()
      val testInstrumentationRunner = PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunner.bind(productFlavor).getValue()
      val testFunctionalTest = PsProductFlavor.ProductFlavorDescriptors.testFunctionalTest.bind(productFlavor).getValue()
      val testHandleProfiling = PsProductFlavor.ProductFlavorDescriptors.testHandleProfiling.bind(productFlavor).getValue()
      val versionCode = PsProductFlavor.ProductFlavorDescriptors.versionCode.bind(productFlavor).getValue()
      val versionName = PsProductFlavor.ProductFlavorDescriptors.versionName.bind(productFlavor).getValue()
      val versionNameSuffix = PsProductFlavor.ProductFlavorDescriptors.versionNameSuffix.bind(productFlavor).getValue()
      val matchingFallbacks = PsProductFlavor.ProductFlavorDescriptors.matchingFallbacks.bind(productFlavor).getValue()
      val resConfigs = PsProductFlavor.ProductFlavorDescriptors.resConfigs.bind(productFlavor).getValue()
      val manifestPlaceholders = PsProductFlavor.ProductFlavorDescriptors.manifestPlaceholders.bind(productFlavor).getValue()
      val testInstrumentationRunnerArguments =
        PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunnerArguments.bind(productFlavor).getValue()
      val editableTestInstrumentationRunnerArguments =
        PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunnerArguments.bind(productFlavor).getEditableValues()
          .mapValues { it.value.getValue() }

      assertThat(dimension.resolved.asTestValue(), equalTo("foo"))
      assertThat(dimension.parsedValue.asTestValue(), equalTo("foo"))

      assertThat(applicationId.resolved.asTestValue(), equalTo("com.example.psd.sample.app.paid"))
      assertThat(applicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.paid"))

      assertThat(applicationIdSuffix.resolved.asTestValue(), nullValue())
      assertThat(applicationIdSuffix.parsedValue.asTestValue(), nullValue())

      assertThat(maxSdkVersion.resolved.asTestValue(), equalTo(25))
      assertThat(maxSdkVersion.parsedValue.asTestValue(), equalTo(25))

      assertThat(
        PsProductFlavor.ProductFlavorDescriptors.getParsed(productFlavor)?.minSdkVersion()?.valueType,
        equalTo(GradlePropertyModel.ValueType.INTEGER))
      assertThat(minSdkVersion.resolved.asTestValue(), equalTo("10"))
      assertThat(minSdkVersion.parsedValue.asTestValue(), equalTo("10"))

      assertThat(multiDexEnabled.resolved.asTestValue(), nullValue())
      assertThat(multiDexEnabled.parsedValue.asTestValue(), nullValue())

      assertThat(signingConfig.resolved.asTestValue(), nullValue())
      assertThat(signingConfig.parsedValue.asTestValue(), nullValue())

      assertThat(
        PsProductFlavor.ProductFlavorDescriptors.getParsed(productFlavor)?.targetSdkVersion()?.valueType,
        equalTo(GradlePropertyModel.ValueType.INTEGER))
      assertThat(targetSdkVersion.resolved.asTestValue(), equalTo("20"))
      assertThat(targetSdkVersion.parsedValue.asTestValue(), equalTo("20"))

      assertThat(testApplicationId.resolved.asTestValue(), equalTo("com.example.psd.sample.app.paid.test"))
      assertThat(testApplicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.paid.test"))

      assertThat(testInstrumentationRunner.resolved.asTestValue(), nullValue())
      assertThat(testInstrumentationRunner.parsedValue.asTestValue(), nullValue())

      assertThat(testFunctionalTest.resolved.asTestValue(), equalTo(true))
      assertThat(testFunctionalTest.parsedValue.asTestValue(), equalTo(true))

      assertThat(testHandleProfiling.resolved.asTestValue(), equalTo(true))
      assertThat(testHandleProfiling.parsedValue.asTestValue(), equalTo(true))

      assertThat(versionCode.resolved.asTestValue(), equalTo(2))
      assertThat(versionCode.parsedValue.asTestValue(), equalTo(2))

      assertThat(versionName.resolved.asTestValue(), equalTo("2.0"))
      assertThat(versionName.parsedValue.asTestValue(), equalTo("2.0"))

      assertThat(versionNameSuffix.resolved.asTestValue(), equalTo("vnsFoo"))
      assertThat(versionNameSuffix.parsedValue.asTestValue(), equalTo("vnsFoo"))

      assertThat(matchingFallbacks.resolved.asTestValue(), nullValue())
      assertThat(matchingFallbacks.parsedValue.asTestValue(), nullValue())

      assertThat(resConfigs.resolved.asTestValue(), equalTo(listOf()))
      assertThat(resConfigs.parsedValue.asTestValue(), nullValue())

      assertThat(manifestPlaceholders.resolved.asTestValue(), equalTo(mapOf()))
      assertThat(manifestPlaceholders.parsedValue.asTestValue(), nullValue())

      assertThat(testInstrumentationRunnerArguments.resolved.asTestValue(), equalTo(mapOf("a" to "AAA", "b" to "BBB", "c" to "CCC")))
      assertThat(testInstrumentationRunnerArguments.parsedValue.asTestValue(), equalTo(mapOf("a" to "AAA", "b" to "BBB", "c" to "CCC")))

      assertThat(editableTestInstrumentationRunnerArguments["a"]?.resolved?.asTestValue(), equalTo("AAA"))
      assertThat(editableTestInstrumentationRunnerArguments["a"]?.parsedValue?.asTestValue(), equalTo("AAA"))

      assertThat(editableTestInstrumentationRunnerArguments["b"]?.resolved?.asTestValue(), equalTo("BBB"))
      assertThat(editableTestInstrumentationRunnerArguments["b"]?.parsedValue?.asTestValue(), equalTo("BBB"))

      assertThat(editableTestInstrumentationRunnerArguments["c"]?.resolved?.asTestValue(), equalTo("CCC"))
      assertThat(editableTestInstrumentationRunnerArguments["c"]?.parsedValue?.asTestValue(), equalTo("CCC"))
    }
    run {
      val productFlavor = appModule.findProductFlavor("bar", "bar")
      assertThat(productFlavor, notNullValue()); productFlavor!!
      val applicationIdSuffix = PsProductFlavor.ProductFlavorDescriptors.applicationIdSuffix.bind(productFlavor).getValue()

      assertThat(applicationIdSuffix.resolved.asTestValue(), equalTo("barSuffix"))
      assertThat(applicationIdSuffix.parsedValue.asTestValue(), equalTo("barSuffix"))
    }
    run {
      val productFlavor = appModule.findProductFlavor("bar", "otherBar")
      assertThat(productFlavor, notNullValue()); productFlavor!!
      val matchingFallbacks = PsProductFlavor.ProductFlavorDescriptors.matchingFallbacks.bind(productFlavor).getValue()
      val resConfigs = PsProductFlavor.ProductFlavorDescriptors.resConfigs.bind(productFlavor).getValue()

      assertThat(matchingFallbacks.resolved.asTestValue(), nullValue())
      assertThat(matchingFallbacks.parsedValue.asTestValue(), equalTo(listOf("bar")))

      assertThat(resConfigs.resolved.asTestValue()?.toSet(), equalTo(setOf("en", "hdpi", "xhdpi")))
      assertThat(resConfigs.parsedValue.asTestValue()?.toSet(), equalTo(setOf("en", "hdpi", "xhdpi")))
    }
  }

  fun testDimensions() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val productFlavor = appModule.findProductFlavor("foo", "paid")
    assertThat(productFlavor, notNullValue()); productFlavor!!

    assertThat(
      PsProductFlavor.ProductFlavorDescriptors.dimension.bindContext(productFlavor).getKnownValues().get().literals,
      hasItems(ValueDescriptor("foo", "foo"), ValueDescriptor("bar", "bar")))
  }

  fun testChangingDimensions() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val productFlavor = appModule.findProductFlavor("foo", "paid")
    assertThat(productFlavor, notNullValue()); productFlavor!!

    assertThat(productFlavor.configuredDimension, equalTo("foo".asParsed()))

    var changed = false
    appModule.productFlavors.onChange(testRootDisposable) { changed = true}

    productFlavor.configuredDimension = "bar".asParsed()
    assertThat(productFlavor.configuredDimension, equalTo("bar".asParsed()))
    assertThat(changed, equalTo(true))
  }

  fun testEffectiveDimensions() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)

    run {
      val appModule = project.findModuleByName("app") as PsAndroidModule
      assertThat(appModule, notNullValue())

      val productFlavor = appModule.findProductFlavor("foo", "paid")
      assertThat(productFlavor, notNullValue()); productFlavor!!

      productFlavor.configuredDimension = ParsedValue.NotSet
      assertThat(productFlavor.effectiveDimension, nullValue())
    }
    run {
      val nested2Module = project.findModuleByName("nested2") as PsAndroidModule
      assertThat(nested2Module, notNullValue())

      val productFlavor = nested2Module.findProductFlavor("foo", "paid")
      assertThat(productFlavor, notNullValue()); productFlavor!!
      assertThat(productFlavor.configuredDimension, equalTo<ParsedValue<String>>(ParsedValue.NotSet))
      assertThat(productFlavor.effectiveDimension, equalTo("foo"))
    }
    run {
      val nested1Module = project.findModuleByName("nested1") as PsAndroidModule
      assertThat(nested1Module, notNullValue())

      val productFlavor = nested1Module.addNewProductFlavor("new_bad", "new_with_bad")
      assertThat(productFlavor, notNullValue())
      assertThat(productFlavor.configuredDimension, equalTo<ParsedValue<String>>("new_bad".asParsed()))
      assertThat(productFlavor.effectiveDimension, nullValue())
    }
  }

  fun testSetProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val productFlavor = appModule.findProductFlavor("foo", "paid")
    assertThat(productFlavor, notNullValue()); productFlavor!!

    productFlavor.applicationId = "com.example.psd.sample.app.unpaid".asParsed()
    productFlavor.applicationIdSuffix = "suffix".asParsed()
    productFlavor.configuredDimension = "bar".asParsed()
    productFlavor.maxSdkVersion = 26.asParsed()
    productFlavor.minSdkVersion = "20".asParsed()
    productFlavor.multiDexEnabled = false.asParsed()
    productFlavor.targetSdkVersion = "21".asParsed()
    productFlavor.testApplicationId = "com.example.psd.sample.app.unpaid.failed_test".asParsed()
    productFlavor.testInstrumentationRunner = "com.runner".asParsed()
    productFlavor.testFunctionalTest = false.asParsed()
    productFlavor.testHandleProfiling = false.asParsed()
    // TODO(b/79531524): find out why it fails.
    // productFlavor.versionCode = "3".asParsed()
    productFlavor.versionName = "3.0".asParsed()
    productFlavor.versionNameSuffix = "newFoo".asParsed()
    PsProductFlavor.ProductFlavorDescriptors.signingConfig.bind(productFlavor).setParsedValue(
      ParsedValue.Set.Parsed(null, DslText.Reference("signingConfigs.myConfig")))
    PsProductFlavor.ProductFlavorDescriptors.matchingFallbacks.bind(productFlavor).addItem(0).setParsedValue("free".asParsed())
    PsProductFlavor.ProductFlavorDescriptors.resConfigs.bind(productFlavor).run {
      addItem(0).setParsedValue("en".asParsed())
      addItem(1).setParsedValue("fr".asParsed())
    }
    PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunnerArguments.bind(productFlavor).changeEntryKey("b", "e")
    PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunnerArguments.bind(productFlavor)
      .getEditableValues()["a"]?.setParsedValue("AAA".asParsed())
    PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunnerArguments.bind(productFlavor)
      .getEditableValues()["e"]?.setParsedValue("EEE".asParsed())
    PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunnerArguments.bind(productFlavor).deleteEntry("c")

    fun verifyValues(productFlavor: PsProductFlavor, afterSync: Boolean = false) {
      val applicationId = PsProductFlavor.ProductFlavorDescriptors.applicationId.bind(productFlavor).getValue()
      val applicationIdSuffix = PsProductFlavor.ProductFlavorDescriptors.applicationIdSuffix.bind(productFlavor).getValue()
      val dimension = PsProductFlavor.ProductFlavorDescriptors.dimension.bind(productFlavor).getValue()
      val maxSdkVersion = PsProductFlavor.ProductFlavorDescriptors.maxSdkVersion.bind(productFlavor).getValue()
      val minSdkVersion = PsProductFlavor.ProductFlavorDescriptors.minSdkVersion.bind(productFlavor).getValue()
      val multiDexEnabled = PsProductFlavor.ProductFlavorDescriptors.multiDexEnabled.bind(productFlavor).getValue()
      val signingConfig = PsProductFlavor.ProductFlavorDescriptors.signingConfig.bind(productFlavor).getValue()
      val targetSdkVersion = PsProductFlavor.ProductFlavorDescriptors.targetSdkVersion.bind(productFlavor).getValue()
      val testApplicationId = PsProductFlavor.ProductFlavorDescriptors.testApplicationId.bind(productFlavor).getValue()
      // TODO(b/70501607): Decide on val testFunctionalTest = PsProductFlavor.ProductFlavorDescriptors.testFunctionalTest.getValue(productFlavor)
      // TODO(b/70501607): Decide on val testHandleProfiling = PsProductFlavor.ProductFlavorDescriptors.testHandleProfiling.getValue(productFlavor)
      val testInstrumentationRunner = PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunner.bind(productFlavor).getValue()
      val testFunctionalTest = PsProductFlavor.ProductFlavorDescriptors.testFunctionalTest.bind(productFlavor).getValue()
      val testHandleProfiling = PsProductFlavor.ProductFlavorDescriptors.testHandleProfiling.bind(productFlavor).getValue()
      // TODO(b/79531524): find out why it fails.
      // val versionCode = PsProductFlavor.ProductFlavorDescriptors.versionCode.bind(productFlavor).getValue()
      val versionName = PsProductFlavor.ProductFlavorDescriptors.versionName.bind(productFlavor).getValue()
      val versionNameSuffix = PsProductFlavor.ProductFlavorDescriptors.versionNameSuffix.bind(productFlavor).getValue()
      val matchingFallbacks = PsProductFlavor.ProductFlavorDescriptors.matchingFallbacks.bind(productFlavor).getValue()
      val resConfigs = PsProductFlavor.ProductFlavorDescriptors.resConfigs.bind(productFlavor).getValue()
      val testInstrumentationRunnerArguments =
        PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunnerArguments.bind(productFlavor).getValue()

      assertThat(dimension.parsedValue.asTestValue(), equalTo("bar"))
      assertThat(applicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.unpaid"))
      assertThat(applicationIdSuffix.parsedValue.asTestValue(), equalTo("suffix"))
      assertThat(maxSdkVersion.parsedValue.asTestValue(), equalTo(26))
      assertThat(minSdkVersion.parsedValue.asTestValue(), equalTo("20"))
      assertThat(multiDexEnabled.parsedValue.asTestValue(), equalTo(false))
      assertThat(signingConfig.resolved.asTestValue(), nullValue())
      assertThat(
        signingConfig.parsedValue,
        equalTo<Annotated<ParsedValue<Unit>>>(ParsedValue.Set.Parsed(null, DslText.Reference("signingConfigs.myConfig")).annotated()))
      assertThat(targetSdkVersion.parsedValue.asTestValue(), equalTo("21"))
      assertThat(testApplicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.unpaid.failed_test"))
      assertThat(matchingFallbacks.parsedValue.asTestValue(), equalTo(listOf("free")))
      assertThat(resConfigs.parsedValue.asTestValue(), equalTo(listOf("en", "fr")))
      assertThat(testInstrumentationRunner.parsedValue.asTestValue(), equalTo("com.runner"))
      // TODO(b/79531524): find out why it fails.
      // assertThat(versionCode.parsedValue.asTestValue(), equalTo("3"))
      assertThat(versionName.parsedValue.asTestValue(), equalTo("3.0"))
      assertThat(versionNameSuffix.parsedValue.asTestValue(), equalTo("newFoo"))
      assertThat(testInstrumentationRunnerArguments.parsedValue.asTestValue(), equalTo(mapOf("a" to "AAA", "e" to "EEE")))
      assertThat(testFunctionalTest.parsedValue.asTestValue(), equalTo(false))
      assertThat(testHandleProfiling.parsedValue.asTestValue(), equalTo(false))

      if (afterSync) {
        assertThat(dimension.parsedValue.asTestValue(), equalTo(dimension.resolved.asTestValue()))
        assertThat(applicationId.parsedValue.asTestValue(), equalTo(applicationId.resolved.asTestValue()))
        assertThat(applicationIdSuffix.parsedValue.asTestValue(), equalTo(applicationIdSuffix.resolved.asTestValue()))
        assertThat(maxSdkVersion.parsedValue.asTestValue(), equalTo(maxSdkVersion.resolved.asTestValue()))
        assertThat(minSdkVersion.parsedValue.asTestValue(), equalTo(minSdkVersion.resolved.asTestValue()))
        assertThat(multiDexEnabled.parsedValue.asTestValue(), equalTo(multiDexEnabled.resolved.asTestValue()))
        // TODO(b/79142681) signingConfig resolved value is always null.
        assertThat(targetSdkVersion.parsedValue.asTestValue(), equalTo(targetSdkVersion.resolved.asTestValue()))
        assertThat(testApplicationId.parsedValue.asTestValue(), equalTo(testApplicationId.resolved.asTestValue()))
        // Note: Resolved values of matchingFallbacks property are not available.
        assertThat(testInstrumentationRunner.parsedValue.asTestValue(), equalTo(testInstrumentationRunner.resolved.asTestValue()))
        // TODO(b/79531524): find out why it fails.
        // assertThat(versionCode.parsedValue.asTestValue(), equalTo(versionCode.resolved.asTestValue()))
        assertThat(versionName.parsedValue.asTestValue(), equalTo(versionName.resolved.asTestValue()))
        assertThat(versionNameSuffix.parsedValue.asTestValue(), equalTo(versionNameSuffix.resolved.asTestValue()))
        assertThat(
          testInstrumentationRunnerArguments.parsedValue.asTestValue(),
          equalTo(testInstrumentationRunnerArguments.resolved.asTestValue())
        )
        assertThat(testFunctionalTest.parsedValue.asTestValue(), equalTo(testFunctionalTest.resolved.asTestValue()))
        assertThat(testHandleProfiling.parsedValue.asTestValue(), equalTo(testHandleProfiling.resolved.asTestValue()))
        assertThat(resConfigs.parsedValue.asTestValue()?.toSet(), equalTo(resConfigs.resolved.asTestValue()?.toSet()))
      }
    }
    verifyValues(productFlavor)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findProductFlavor("bar", "paid")!!, afterSync = true)
  }
}
