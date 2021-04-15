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

package com.android.tools.idea.gradle.model

import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.projectsystem.gradle.convertLibraryToExternalLibrary
import com.android.tools.idea.gradle.project.sync.ModelCache
import com.android.ide.common.util.PathString
import com.android.ide.common.util.toPathString
import com.android.projectmodel.DynamicResourceValue
import com.android.projectmodel.RecursiveResourceFolder
import com.android.resources.ResourceType
import com.android.tools.idea.gradle.project.model.classFieldsToDynamicResourceValues
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Tests for [GradleModelConverterUtil].
 */
class GradleModelConverterUtilTest {

    val modelCache = ModelCache.createForTesting()

    @get:Rule
    val expect = Expect.createAndEnableStackTrace();

    @Test
    fun testClassFieldsToDynamicResourceValues() {
        val input = mapOf(
            "foo" to modelCache.classFieldFrom(ClassFieldStub(ResourceType.STRING.getName(), "foo", "baz")),
            "foo2" to modelCache.classFieldFrom(ClassFieldStub(ResourceType.INTEGER.getName(), "foo2", "123")))
        val output = classFieldsToDynamicResourceValues(input)

        val expectedOutput = mapOf(
            "foo" to DynamicResourceValue(ResourceType.STRING, "baz"),
            "foo2" to DynamicResourceValue(ResourceType.INTEGER, "123")
        )

        assertThat(output).isEqualTo(expectedOutput)
    }

  @Test
  fun testConvertAndroidLibrary() {
    val original = IdeAndroidLibraryImpl(
      artifactAddress = "artifact:address:1.0",
      folder = File("libraryFolder"),
      manifest = "manifest.xml",
      jarFile = "file.jar",
      compileJarFile = "api.jar",
      resFolder = "res",
      resStaticLibrary = File("libraryFolder/res.apk"),
      assetsFolder = "assets",
      localJars = listOf(),
      jniFolder = "jni",
      aidlFolder = "aidl",
      renderscriptFolder = "renderscriptFolder",
      proguardRules = "proguardRules",
      lintJar = "lint.jar",
      externalAnnotations = "externalAnnotations",
      publicResources = "publicResources",
      artifact = File("artifactFile"),
      symbolFile = "symbolFile",
      isProvided = false
    )
    val result = convertLibraryToExternalLibrary(original)

    with(original) {
      expect.that(result.address).isEqualTo(artifactAddress)
      expect.that(result.location).isEqualTo(artifact.toPathString())
      expect.that(result.manifestFile).isEqualTo(PathString(manifest))
      expect.that(result.resFolder).isEqualTo(RecursiveResourceFolder(PathString(resFolder)))
      expect.that(result.symbolFile).isEqualTo(PathString(symbolFile))
      expect.that(result.resApkFile).isEqualTo(resStaticLibrary?.let(::PathString))
    }
  }
}
