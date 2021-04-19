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
package com.android.tools.idea.npw.module.recipes.macrobenchmarkModule

import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.SdkConstants.FN_BUILD_GRADLE_KTS
import com.android.repository.Revision
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.npw.project.GradleBuildSettings.needsExplicitBuildToolsVersion
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.npw.module.recipes.addKotlinIfNeeded
import com.android.tools.idea.npw.module.recipes.macrobenchmarkModule.src.main.androidManifestXml
import com.android.tools.idea.npw.module.recipes.macrobenchmarkModule.src.androidTest.exampleMacrobenchmarkJava
import com.android.tools.idea.npw.module.recipes.macrobenchmarkModule.src.androidTest.exampleMacrobenchmarkKt
import com.android.tools.idea.npw.module.recipes.macrobenchmarkModule.src.androidTest.testAndroidManifestXml
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidFacet

fun RecipeExecutor.generateMacrobenchmarkModule(
  moduleData: ModuleTemplateData,
  useGradleKts: Boolean,
  targetModule: Module,
) {
  val projectData = moduleData.projectTemplateData
  val testOut = moduleData.testDir
  val packageName = moduleData.packageName
  val moduleOut = moduleData.rootDir
  val buildToolsVersion = projectData.buildToolsVersion
  val (buildApi, targetApi, minApi) = moduleData.apis
  val language = projectData.language

  if (this is DefaultRecipeExecutor) {
    addSigningReference(targetModule)
  }

  val targetPackageName = AndroidModuleInfo.getInstance(targetModule)?.`package` ?: ""

  addIncludeToSettings(moduleData.name)

  val bg = buildGradle(
    explicitBuildToolsVersion = needsExplicitBuildToolsVersion(Revision.parseRevision(buildToolsVersion)),
    buildApiString = buildApi.apiString,
    buildToolsVersion = buildToolsVersion,
    minApi = minApi.apiString,
    targetApiString = targetApi.apiString,
    language = language,
    gradlePluginVersion = projectData.gradlePluginVersion,
    useGradleKts = useGradleKts,
    moduleName = moduleData.name,
    targetModule = targetModule,
  )
  val buildFile = if (useGradleKts) FN_BUILD_GRADLE_KTS else FN_BUILD_GRADLE

  save(bg, moduleOut.resolve(buildFile))
  applyPlugin("com.android.library")

  addDependency("androidx.test.ext:junit:+", "implementation")
  addDependency("androidx.test.espresso:espresso-core:3.+", "implementation")
  addDependency("androidx.test.uiautomator:uiautomator:2.+", "implementation")
  addDependency("androidx.benchmark:benchmark-macro-junit4:+", configuration = "implementation", minRev = "1.1.0-alpha02")

  save(androidManifestXml(packageName, targetPackageName), moduleOut.resolve("src/main/AndroidManifest.xml"))
  save(testAndroidManifestXml(packageName), moduleOut.resolve("src/androidTest/AndroidManifest.xml"))

  if (language == Language.Kotlin) {
    save(exampleMacrobenchmarkKt(packageName, targetPackageName), testOut.resolve("ExampleStartupBenchmark.kt"))
  }
  else {
    save(exampleMacrobenchmarkJava(packageName, targetPackageName), testOut.resolve("ExampleStartupBenchmark.java"))
  }

  addKotlinIfNeeded(projectData, noKtx = true)
}

private fun addSigningReference(targetModule: Module) {
  val projectBuildModel = ProjectBuildModel.getOrLog(targetModule.project) ?: return
  val androidBuildModel = projectBuildModel.getModuleBuildModel(targetModule)?.android() ?: return
  val debugSignConfigBuildModel = androidBuildModel.signingConfigs().first { it.name() == "debug" } ?: return
  val buildTypeReleaseBuildModel = androidBuildModel.buildTypes().first { it.name() == "release" } ?: return

  buildTypeReleaseBuildModel.signingConfig().setValue(ReferenceTo(debugSignConfigBuildModel))
  projectBuildModel.applyChanges()
}
