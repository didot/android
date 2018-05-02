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
import org.junit.Ignore
import java.io.File

class PsBuildTypeTest : AndroidGradleTestCase() {

  private fun <T> ResolvedValue<T>.asTestValue(): T? = (this as? ResolvedValue.Set<T>)?.resolved
  private fun <T> ParsedValue<T>.asTestValue(): T? = (this as? ParsedValue.Set.Parsed<T>)?.value
  private fun <T> ParsedValue<T>.asUnparsedValue(): String? =
    ((this as? ParsedValue.Set.Parsed<T>)?.dslText as? DslText.OtherUnparsedDslText)?.text
  private fun <T : Any> T.asParsed(): ParsedValue<T> = ParsedValue.Set.Parsed(this, DslText.Literal)

  fun testProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("release")
    assertThat(buildType, notNullValue()); buildType!!

    val applicationIdSuffix = PsBuildType.BuildTypeDescriptors.applicationIdSuffix.getValue(buildType)
    val debuggable = PsBuildType.BuildTypeDescriptors.debuggable.getValue(buildType)
    // TODO(b/70501607): Decide on val embedMicroApp = PsBuildType.BuildTypeDescriptors.embedMicroApp.getValue(buildType)
    val jniDebuggable = PsBuildType.BuildTypeDescriptors.jniDebuggable.getValue(buildType)
    val minifyEnabled = PsBuildType.BuildTypeDescriptors.minifyEnabled.getValue(buildType)
    val multiDexEnabled = PsBuildType.BuildTypeDescriptors.multiDexEnabled.getValue(buildType)
    // TODO(b/70501607): Decide on val pseudoLocalesEnabled = PsBuildType.BuildTypeDescriptors.pseudoLocalesEnabled.getValue(buildType)
    val renderscriptDebuggable = PsBuildType.BuildTypeDescriptors.renderscriptDebuggable.getValue(buildType)
    val renderscriptOptimLevel = PsBuildType.BuildTypeDescriptors.renderscriptOptimLevel.getValue(buildType)
    // TODO(b/70501607): Decide on val testCoverageEnabled = PsBuildType.BuildTypeDescriptors.testCoverageEnabled.getValue(buildType)
    val versionNameSuffix = PsBuildType.BuildTypeDescriptors.versionNameSuffix.getValue(buildType)
    val zipAlignEnabled = PsBuildType.BuildTypeDescriptors.zipAlignEnabled.getValue(buildType)
    val proGuardFiles = PsBuildType.BuildTypeDescriptors.proGuardFiles.getEditableValues(buildType).map { it.getValue(Unit) }
    val manifestPlaceholders = PsBuildType.BuildTypeDescriptors.manifestPlaceholders.getValue(buildType)

    assertThat(applicationIdSuffix.resolved.asTestValue(), equalTo("suffix"))
    assertThat(applicationIdSuffix.parsedValue.asTestValue(), equalTo("suffix"))

    assertThat(debuggable.resolved.asTestValue(), equalTo(false))
    assertThat(debuggable.parsedValue.asTestValue(), equalTo(false))

    assertThat(jniDebuggable.resolved.asTestValue(), equalTo(false))
    assertThat(jniDebuggable.parsedValue.asTestValue(), equalTo(false))

    assertThat(minifyEnabled.resolved.asTestValue(), equalTo(false))
    assertThat(minifyEnabled.parsedValue.asTestValue(), equalTo(false))

    assertThat(multiDexEnabled.resolved.asTestValue(), nullValue())
    assertThat(multiDexEnabled.parsedValue.asTestValue(), nullValue())

    assertThat(renderscriptDebuggable.resolved.asTestValue(), equalTo(false))
    assertThat(renderscriptDebuggable.parsedValue.asTestValue(), nullValue())

    assertThat(renderscriptOptimLevel.resolved.asTestValue(), equalTo(2))
    assertThat(renderscriptOptimLevel.parsedValue.asTestValue(), equalTo(2))

    assertThat(versionNameSuffix.resolved.asTestValue(), equalTo("vsuffix"))
    assertThat(versionNameSuffix.parsedValue.asTestValue(), equalTo("vsuffix"))

    assertThat(zipAlignEnabled.resolved.asTestValue(), equalTo(true))
    assertThat(zipAlignEnabled.parsedValue.asTestValue(), nullValue())

    assertThat(proGuardFiles.size, equalTo(3))
    assertThat(proGuardFiles[0].resolved.asTestValue(), nullValue())
    // TODO(b/72052622): assertThat(proGuardFiles[0].parsedValue, instanceOf(ParsedValue.Set.Parsed::class.java))
    // TODO(b/72052622): assertThat(
    //  (proGuardFiles[0].parsedValue as ParsedValue.Set.Parsed<File>).dslText?.mode,
    //  equalTo(DslMode.OTHER_UNPARSED_DSL_TEXT)
    //)
    // TODO(b/72052622): assertThat(
    //  (proGuardFiles[0].parsedValue as ParsedValue.Set.Parsed<File>).dslText?.text,
    //  equalTo("getDefaultProguardFile('proguard-android.txt')")
    //)

    // TODO(b/72814329): Resolved values are not yet supported on list properties.
    assertThat(proGuardFiles[1].resolved.asTestValue(), nullValue())
    assertThat(proGuardFiles[1].parsedValue.asTestValue(), equalTo(File("proguard-rules.txt")))

    // TODO(b/72814329): Resolved values are not yet supported on list properties.
    assertThat(proGuardFiles[2].resolved.asTestValue(), nullValue())
    assertThat(proGuardFiles[2].parsedValue.asTestValue(), equalTo(File("proguard-rules2.txt")))

    assertThat(manifestPlaceholders.resolved.asTestValue(), equalTo(mapOf()))
    assertThat(manifestPlaceholders.parsedValue.asTestValue(), nullValue())
  }

  fun testProperties_defaultResolved() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("debug")
    assertThat(buildType, notNullValue()); buildType!!
    assertFalse(buildType.isDeclared)

    val applicationIdSuffix = PsBuildType.BuildTypeDescriptors.applicationIdSuffix.getValue(buildType)
    val debuggable = PsBuildType.BuildTypeDescriptors.debuggable.getValue(buildType)
    // TODO(b/70501607): Decide on val embedMicroApp = PsBuildType.BuildTypeDescriptors.embedMicroApp.getValue(buildType)
    val jniDebuggable = PsBuildType.BuildTypeDescriptors.jniDebuggable.getValue(buildType)
    val minifyEnabled = PsBuildType.BuildTypeDescriptors.minifyEnabled.getValue(buildType)
    val multiDexEnabled = PsBuildType.BuildTypeDescriptors.multiDexEnabled.getValue(buildType)
    // TODO(b/70501607): Decide on val pseudoLocalesEnabled = PsBuildType.BuildTypeDescriptors.pseudoLocalesEnabled.getValue(buildType)
    val renderscriptDebuggable = PsBuildType.BuildTypeDescriptors.renderscriptDebuggable.getValue(buildType)
    val renderscriptOptimLevel = PsBuildType.BuildTypeDescriptors.renderscriptOptimLevel.getValue(buildType)
    // TODO(b/70501607): Decide on val testCoverageEnabled = PsBuildType.BuildTypeDescriptors.testCoverageEnabled.getValue(buildType)
    val versionNameSuffix = PsBuildType.BuildTypeDescriptors.versionNameSuffix.getValue(buildType)
    val zipAlignEnabled = PsBuildType.BuildTypeDescriptors.zipAlignEnabled.getValue(buildType)
    val manifestPlaceholders = PsBuildType.BuildTypeDescriptors.manifestPlaceholders.getValue(buildType)

    assertThat(applicationIdSuffix.resolved.asTestValue(), nullValue())
    assertThat(applicationIdSuffix.parsedValue.asTestValue(), nullValue())

    assertThat(debuggable.resolved.asTestValue(), equalTo(true))
    assertThat(debuggable.parsedValue.asTestValue(), nullValue())

    assertThat(jniDebuggable.resolved.asTestValue(), equalTo(false))
    assertThat(jniDebuggable.parsedValue.asTestValue(), nullValue())

    assertThat(minifyEnabled.resolved.asTestValue(), equalTo(false))
    assertThat(minifyEnabled.parsedValue.asTestValue(), nullValue())

    assertThat(multiDexEnabled.resolved.asTestValue(), nullValue())
    assertThat(multiDexEnabled.parsedValue.asTestValue(), nullValue())

    assertThat(renderscriptDebuggable.resolved.asTestValue(), equalTo(false))
    assertThat(renderscriptDebuggable.parsedValue.asTestValue(), nullValue())

    assertThat(renderscriptOptimLevel.resolved.asTestValue(), equalTo(3))
    assertThat(renderscriptOptimLevel.parsedValue.asTestValue(), nullValue())

    assertThat(versionNameSuffix.resolved.asTestValue(), nullValue())
    assertThat(versionNameSuffix.parsedValue.asTestValue(), nullValue())

    assertThat(zipAlignEnabled.resolved.asTestValue(), equalTo(true))
    assertThat(zipAlignEnabled.parsedValue.asTestValue(), nullValue())

    assertThat(manifestPlaceholders.resolved.asTestValue(), equalTo(mapOf()))
    assertThat(manifestPlaceholders.parsedValue.asTestValue(), nullValue())
  }

  fun testSetProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("release")
    assertThat(buildType, notNullValue()); buildType!!

    buildType.applicationIdSuffix = "new_suffix".asParsed()
    buildType.debuggable = true.asParsed()
    buildType.jniDebuggable = true.asParsed()
    buildType.minifyEnabled = true.asParsed()
    buildType.multiDexEnabled = true.asParsed()
    buildType.multiDexEnabled = false.asParsed()
    buildType.renderscriptDebuggable = true.asParsed()
    buildType.renderscriptOptimLevel = 3.asParsed()
    buildType.versionNameSuffix = "new_vsuffix".asParsed()
    buildType.zipAlignEnabled = false.asParsed()
    PsBuildType.BuildTypeDescriptors.proGuardFiles.deleteItem(buildType, 1)
    val editableProGuardFiles = PsBuildType.BuildTypeDescriptors.proGuardFiles.getEditableValues(buildType)
    editableProGuardFiles[1].setParsedValue(Unit, File("a.txt").asParsed())
    PsBuildType.BuildTypeDescriptors.proGuardFiles.addItem(buildType, 2).setParsedValue(Unit, File("z.txt").asParsed())

    PsBuildType.BuildTypeDescriptors.manifestPlaceholders.addEntry(buildType, "b").setParsedValue(Unit, "v".asParsed())
    PsBuildType.BuildTypeDescriptors.manifestPlaceholders.changeEntryKey(buildType, "b", "v")
    PsBuildType.BuildTypeDescriptors.manifestPlaceholders.deleteEntry(buildType, "v")


    fun verifyValues(buildType: PsBuildType, afterSync: Boolean = false) {
      val applicationIdSuffix = PsBuildType.BuildTypeDescriptors.applicationIdSuffix.getValue(buildType)
      val debuggable = PsBuildType.BuildTypeDescriptors.debuggable.getValue(buildType)
      // TODO(b/70501607): Decide on val embedMicroApp = PsBuildType.BuildTypeDescriptors.embedMicroApp.getValue(buildType)
      val jniDebuggable = PsBuildType.BuildTypeDescriptors.jniDebuggable.getValue(buildType)
      val minifyEnabled = PsBuildType.BuildTypeDescriptors.minifyEnabled.getValue(buildType)
      val multiDexEnabled = PsBuildType.BuildTypeDescriptors.multiDexEnabled.getValue(buildType)
      // TODO(b/70501607): Decide on val pseudoLocalesEnabled = PsBuildType.BuildTypeDescriptors.pseudoLocalesEnabled.getValue(buildType)
      val renderscriptDebuggable = PsBuildType.BuildTypeDescriptors.renderscriptDebuggable.getValue(buildType)
      val renderscriptOptimLevel = PsBuildType.BuildTypeDescriptors.renderscriptOptimLevel.getValue(buildType)
      // TODO(b/70501607): Decide on val testCoverageEnabled = PsBuildType.BuildTypeDescriptors.testCoverageEnabled.getValue(buildType)
      val versionNameSuffix = PsBuildType.BuildTypeDescriptors.versionNameSuffix.getValue(buildType)
      val zipAlignEnabled = PsBuildType.BuildTypeDescriptors.zipAlignEnabled.getValue(buildType)
      val proGuardFiles = PsBuildType.BuildTypeDescriptors.proGuardFiles.getEditableValues(buildType).map { it.getValue(Unit) }
      val manifestPlaceholders = PsBuildType.BuildTypeDescriptors.manifestPlaceholders.getValue(buildType)

      assertThat(applicationIdSuffix.parsedValue.asTestValue(), equalTo("new_suffix"))
      assertThat(debuggable.parsedValue.asTestValue(), equalTo(true))
      assertThat(jniDebuggable.parsedValue.asTestValue(), equalTo(true))
      assertThat(minifyEnabled.parsedValue.asTestValue(), equalTo(true))
      assertThat(multiDexEnabled.parsedValue.asTestValue(), equalTo(false))
      assertThat(renderscriptDebuggable.parsedValue.asTestValue(), equalTo(true))
      assertThat(renderscriptOptimLevel.parsedValue.asTestValue(), equalTo(3))
      assertThat(versionNameSuffix.parsedValue.asTestValue(), equalTo("new_vsuffix"))
      assertThat(zipAlignEnabled.parsedValue.asTestValue(), equalTo(false))

      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[0].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[0].parsedValue.asUnparsedValue(), equalTo("getDefaultProguardFile('proguard-android.txt')"))
      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[1].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[1].parsedValue.asTestValue(), equalTo(File("a.txt")))
      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[2].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[2].parsedValue.asTestValue(), equalTo(File("z.txt")))

      assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(mapOf()))

      if (afterSync) {
        assertThat(applicationIdSuffix.parsedValue.asTestValue(), equalTo(applicationIdSuffix.resolved.asTestValue()))
        assertThat(debuggable.parsedValue.asTestValue(), equalTo(debuggable.resolved.asTestValue()))
        assertThat(jniDebuggable.parsedValue.asTestValue(), equalTo(jniDebuggable.resolved.asTestValue()))
        assertThat(minifyEnabled.parsedValue.asTestValue(), equalTo(minifyEnabled.resolved.asTestValue()))
        assertThat(multiDexEnabled.parsedValue.asTestValue(), equalTo(multiDexEnabled.resolved.asTestValue()))
        assertThat(renderscriptDebuggable.parsedValue.asTestValue(), equalTo(renderscriptDebuggable.resolved.asTestValue()))
        assertThat(renderscriptOptimLevel.parsedValue.asTestValue(), equalTo(renderscriptOptimLevel.resolved.asTestValue()))
        assertThat(versionNameSuffix.parsedValue.asTestValue(), equalTo(versionNameSuffix.resolved.asTestValue()))
        assertThat(zipAlignEnabled.parsedValue.asTestValue(), equalTo(zipAlignEnabled.resolved.asTestValue()))

        // TODO(b/72814329): assertThat(proGuardFiles[1].parsedValue.asTestValue(), equalTo(proGuardFiles[1].resolved.asTestValue()))
        // TODO(b/72814329): assertThat(proGuardFiles[2].parsedValue.asTestValue(), equalTo(proGuardFiles[2].resolved.asTestValue()))

        // Note: empty manifestPlaceholders does not match null value.
        assertThat(manifestPlaceholders.resolved.asTestValue(), equalTo(mapOf()))
       }
    }

    verifyValues(buildType)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProject(resolvedProject)
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findBuildType("release")!!, afterSync = true)
  }

  fun testInsertingProguardFiles() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("release")
    assertThat(buildType, notNullValue()); buildType!!

    val editableProGuardFiles = PsBuildType.BuildTypeDescriptors.proGuardFiles.getEditableValues(buildType)
    editableProGuardFiles[1].setParsedValue(Unit, File("a.txt").asParsed())
    editableProGuardFiles[2].setParsedValue(Unit, File("b.txt").asParsed())
    PsBuildType.BuildTypeDescriptors.proGuardFiles.addItem(buildType, 0).setParsedValue(Unit, File("z.txt").asParsed())


    fun verifyValues(buildType: PsBuildType, afterSync: Boolean = false) {
      val proGuardFiles = PsBuildType.BuildTypeDescriptors.proGuardFiles.getEditableValues(buildType).map { it.getValue(Unit) }

      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[0].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[0].parsedValue.asTestValue(), equalTo(File("z.txt")))
      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[1].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[1].parsedValue.asUnparsedValue(), equalTo("getDefaultProguardFile('proguard-android.txt')"))
      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[2].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[2].parsedValue.asTestValue(), equalTo(File("a.txt")))
      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[3].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[3].parsedValue.asTestValue(), equalTo(File("b.txt")))

      if (afterSync) {
        // TODO(b/72814329): assertThat(proGuardFiles[0].parsedValue.asTestValue(), equalTo(proGuardFiles[1].resolved.asTestValue()))
        // TODO(b/72814329): assertThat(proGuardFiles[2].parsedValue.asTestValue(), equalTo(proGuardFiles[2].resolved.asTestValue()))
        // TODO(b/72814329): assertThat(proGuardFiles[3].parsedValue.asTestValue(), equalTo(proGuardFiles[1].resolved.asTestValue()))
       }
    }

    verifyValues(buildType)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProject(resolvedProject)
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findBuildType("release")!!, afterSync = true)
  }

  @Ignore("b/72853928")
  fun /*test*/SetListReferences() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("release")
    assertThat(buildType, notNullValue()); buildType!!

    PsBuildType.BuildTypeDescriptors.proGuardFiles.setParsedValue(
      buildType,
      ParsedValue.Set.Parsed(
        dslText = DslText.Reference("varProGuardFiles"),
        value = null
      )
    )

    fun verifyValues(buildType: PsBuildType, afterSync: Boolean = false) {
      val proGuardFilesValue = PsBuildType.BuildTypeDescriptors.proGuardFiles.getValue(buildType)
      val parsedProGuardFilesValue = proGuardFilesValue.parsedValue as? ParsedValue.Set.Parsed
      val proGuardFiles = PsBuildType.BuildTypeDescriptors.proGuardFiles.getEditableValues(buildType).map { it.getValue(Unit) }

      assertThat(parsedProGuardFilesValue?.dslText, equalTo<DslText?>(DslText.Reference("varProGuardFiles")))

      assertThat(proGuardFiles.size, equalTo(2))
      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[0].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[0].parsedValue.asTestValue(), equalTo(File("proguard-rules.txt")))

      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[1].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[1].parsedValue.asTestValue(), equalTo(File("proguard-rules2.txt")))

      if (afterSync) {
        // TODO(b/72814329): assertThat(proGuardFiles[0].parsedValue.asTestValue(), equalTo(proGuardFiles[0].resolved.asTestValue()))
        // TODO(b/72814329): assertThat(proGuardFiles[1].parsedValue.asTestValue(), equalTo(proGuardFiles[1].resolved.asTestValue()))
      }
    }

    verifyValues(buildType)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProject(resolvedProject)
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findBuildType("release")!!, afterSync = true)
  }
}
