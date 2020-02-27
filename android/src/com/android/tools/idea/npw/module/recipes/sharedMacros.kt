/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.module.recipes

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleInstrumentedTestJava
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleInstrumentedTestKt
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleUnitTestJava
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleUnitTestKt
import com.android.tools.idea.wizard.template.GradlePluginVersion
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.Revision
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf
import java.io.File

fun generateManifest(
  packageName: String,
  hasApplicationBlock: Boolean = false,
  theme: String = "@style/AppTheme",
  usesFeatureBlock: String = "",
  hasRoundIcon: Boolean = true
): String {
  val applicationBlock = if (hasApplicationBlock) """
    <application
    android:allowBackup="true"
    android:label="@string/app_name"
    android:icon="@mipmap/ic_launcher"
    ${renderIf(hasRoundIcon) { """android:roundIcon="@mipmap/ic_launcher_round"""" }}
    android:supportsRtl="true"
    android:theme="$theme" />
  """
  else "/"

  return """
    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${packageName}">
    $usesFeatureBlock
    $applicationBlock
    </manifest>
  """
}

fun proguardConfig(
  // Incubating, see https://google.github.io/android-gradle-dsl/current/com.android.build.gradle.internal.dsl.BuildType.html
  postprocessing: Boolean = false
) =
  if (postprocessing) {
    """
    buildTypes {
        release {
            postprocessing {
                removeUnusedCode false
                removeUnusedResources false
                obfuscate false
                optimizeCode false
                proguardFile 'proguard-rules.pro'
            }
        }
    }
    """
  }
  else {
    """
    buildTypes {
       release {
           minifyEnabled false
           proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
       }
    }
    """
  }


fun androidConfig(
  buildApiString: String,
  explicitBuildToolsVersion: Boolean,
  buildToolsVersion: Revision,
  minApi: String,
  targetApi: String,
  useAndroidX: Boolean,
  cppFlags: String,
  isLibraryProject: Boolean,
  includeCppSupport: Boolean = false,
  hasApplicationId: Boolean = false,
  applicationId: String = "",
  hasTests: Boolean = false,
  canHaveCpp: Boolean = false,
  canUseProguard: Boolean = false,
  addLintOptions: Boolean = false
): String {
  val buildToolsVersionBlock = renderIf(explicitBuildToolsVersion) { "buildToolsVersion \"$buildToolsVersion\"" }
  val applicationIdBlock = renderIf(hasApplicationId) { "applicationId \"${applicationId}\"" }
  val testsBlock = renderIf(hasTests) {
    "testInstrumentationRunner \"${getMaterialComponentName("android.support.test.runner.AndroidJUnitRunner", useAndroidX)}\""
  }
  val proguardConsumerBlock = renderIf(canUseProguard && isLibraryProject) { "consumerProguardFiles \"consumer-rules.pro\"" }
  val proguardConfigBlock = renderIf(canUseProguard) { proguardConfig() }
  val lintOptionsBlock = renderIf(addLintOptions) {
    """
      lintOptions {
          disable ('AllowBackup', 'GoogleAppIndexingWarning', 'MissingApplicationIcon')
      }
    """
  }

  val cppBlock = renderIf(canHaveCpp && includeCppSupport) {
    """
      externalNativeBuild {
        cmake {
          cppFlags "${cppFlags}"
        }
      }
  """
  }
  val cppBlock2 = renderIf(canHaveCpp && includeCppSupport) {
    """
      externalNativeBuild {
        cmake {
          path "src/main/cpp/CMakeLists.txt"
          version "3.10.2"
        }
      }
      """
  }

  // TODO(qumeric): add compileOptions
  return """
    android {
    compileSdkVersion ${buildApiString.toIntOrNull() ?: "\"$buildApiString\""}
    $buildToolsVersionBlock

    defaultConfig {
      $applicationIdBlock
      minSdkVersion ${minApi.toIntOrNull() ?: "\"$minApi\""}
      targetSdkVersion ${targetApi.toIntOrNull() ?: "\"$targetApi\""}
      versionCode 1
      versionName "1.0"

      $testsBlock
      $proguardConsumerBlock
      $cppBlock
    }

    $proguardConfigBlock
    $lintOptionsBlock
    $cppBlock2
    }
    """
}

private fun resource(path: String) = File("templates/module", path)

fun RecipeExecutor.copyIcons(destination: File) {
  fun copyAdaptiveIcons() {
    copy(
      resource("mipmap-anydpi-v26/ic_launcher.xml"),
      destination.resolve("mipmap-anydpi-v26/ic_launcher.xml")
    )
    copy(
      resource("drawable/ic_launcher_background.xml"),
      destination.resolve("drawable/ic_launcher_background.xml")
    )
    copy(
      resource("drawable-v24/ic_launcher_foreground.xml"),
      destination.resolve("drawable-v24/ic_launcher_foreground.xml")
    )
    copy(
      resource("mipmap-anydpi-v26/ic_launcher_round.xml"),
      destination.resolve("mipmap-anydpi-v26/ic_launcher_round.xml")
    )
  }

  copyMipmapFolder(destination)
  copyMipmapFolder(destination)
  copyAdaptiveIcons()
}

fun RecipeExecutor.copyMipmapFolder(destination: File) {
  copy(resource("mipmap-hdpi"), destination.resolve("mipmap-hdpi"))
  copy(resource("mipmap-mdpi"), destination.resolve("mipmap-mdpi"))
  copy(resource("mipmap-xhdpi"), destination.resolve("mipmap-xhdpi"))
  copy(resource("mipmap-xxhdpi"), destination.resolve("mipmap-xxhdpi"))
  copy(resource("mipmap-xxxhdpi"), destination.resolve("mipmap-xxxhdpi"))
}

fun RecipeExecutor.copyMipmapFile(destination: File, file: String) {
  copy(resource("mipmap-hdpi/$file"), destination.resolve("mipmap-hdpi/$file"))
  copy(resource("mipmap-mdpi/$file"), destination.resolve("mipmap-mdpi/$file"))
  copy(resource("mipmap-xhdpi/$file"), destination.resolve("mipmap-xhdpi/$file"))
  copy(resource("mipmap-xxhdpi/$file"), destination.resolve("mipmap-xxhdpi/$file"))
  copy(resource("mipmap-xxxhdpi/$file"), destination.resolve("mipmap-xxxhdpi/$file"))
}

fun RecipeExecutor.addTests(
  packageName: String, useAndroidX: Boolean, isLibraryProject: Boolean, testOut: File, unitTestOut: File, language: Language
) {
  val ext = language.extension
  save(
    if (language == Language.Kotlin)
      exampleInstrumentedTestKt(packageName, useAndroidX, isLibraryProject)
    else
      exampleInstrumentedTestJava(packageName, useAndroidX, isLibraryProject),
    testOut.resolve("ExampleInstrumentedTest.$ext")
  )
  save(
    if (language == Language.Kotlin)
      exampleUnitTestKt(packageName)
    else
      exampleUnitTestJava(packageName),
    unitTestOut.resolve("ExampleUnitTest.$ext")
  )
}

fun basicStylesXml(parent: String) = """
<resources>
    <style name="AppTheme" parent="$parent" />
</resources> 
"""

fun supportsImprovedTestDeps(agpVersion: GradlePluginVersion) =
  GradleVersion.parse(agpVersion).compareIgnoringQualifiers("3.0.0") >= 0

fun RecipeExecutor.addTestDependencies(agpVersion: GradlePluginVersion) {
  addDependency("junit:junit:4.12", "testCompile")
  if (supportsImprovedTestDeps(agpVersion)) {
    addDependency("com.android.support.test:runner:+", "androidTestCompile")
    addDependency("com.android.support.test.espresso:espresso-core:+", "androidTestCompile")
  }
}

fun RecipeExecutor.createDefaultDirectories(moduleOut: File, srcOut: File) {
  createDirectory(srcOut)
  createDirectory(moduleOut.resolve("libs"))
}