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
package com.android.tools.idea.gradle.run

import com.android.AndroidProjectTypes
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider.GradleTaskRunnerFactory
import com.android.tools.idea.gradle.util.GradleVersions
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.createAndroidProjectBuilder
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.google.common.truth.Truth
import com.intellij.execution.configurations.JavaRunConfigurationModule
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.io.File

/**
 * Tests for [GradleTaskRunnerFactory].
 */
class GradleTaskRunnerFactoryTest : PlatformTestCase() {
  private val projectDir get() = File(project.basePath!!)

  fun testCreateTaskRunnerWithAndroidRunConfigurationBaseAndGradle3Dot5() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":", "3.5.0"))
    val taskRunnerFactory = GradleTaskRunnerFactory(project, GradleVersions.getInstance())
    val configurationModule = Mockito.mock(JavaRunConfigurationModule::class.java)
    `when`(configurationModule.module).thenReturn(null)
    val configuration = Mockito.mock(AndroidRunConfigurationBase::class.java)
    `when`(configuration.configurationModule).thenReturn(configurationModule)
    val taskRunner = taskRunnerFactory.createTaskRunner(configuration)
    val buildAction = taskRunner.buildAction
    TestCase.assertNotNull(buildAction)
  }

  fun testCreateTaskRunnerWithAndroidRunConfigurationBaseAndGradleOlderThan3Dot5() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":", "3.4.1"))
    val taskRunnerFactory = GradleTaskRunnerFactory(project, GradleVersions.getInstance())
    val configuration = Mockito.mock(AndroidRunConfigurationBase::class.java)
    val taskRunner = taskRunnerFactory.createTaskRunner(configuration)
    val buildAction = taskRunner.buildAction
    TestCase.assertNull(buildAction)
  }

  fun testCreateTaskRunnerWithConfigurationNotAndroidRunConfigurationBase() {
    setupTestProjectFromAndroidModel(project, projectDir, androidModule(":", "3.5.0"))
    val taskRunnerFactory = GradleTaskRunnerFactory(project, GradleVersions.getInstance())
    val configuration = Mockito.mock(RunConfiguration::class.java)
    val taskRunner = taskRunnerFactory.createTaskRunner(configuration)
    val buildAction = taskRunner.buildAction
    TestCase.assertNull(buildAction)
  }

  fun testCreateTaskRunnerForDynamicFeatureInstrumentedTest() {
    setupTestProjectFromAndroidModel(
      project,
      projectDir,
      rootModule("3.5.0"),
      androidModule(":app", "3.5.0", dynamicFeatures = listOf(":feature1")),
      androidModule(
        ":feature1", "3.5.0",
        projectType = AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE,
        moduleDependencies = listOf(":app")
      )
    )
    val taskRunnerFactory = GradleTaskRunnerFactory(project, GradleVersions.getInstance())

    val configurationModule = Mockito.mock(JavaRunConfigurationModule::class.java)
    `when`(configurationModule.module).thenReturn(ModuleManager.getInstance(project).modules.find { it.name == "feature1" })
    val configuration = Mockito.mock(AndroidTestRunConfiguration::class.java)
    `when`(configuration.configurationModule).thenReturn(configurationModule)
    val taskRunner = taskRunnerFactory.createTaskRunner(configuration)
    val buildAction = taskRunner.myBuildAction as OutputBuildAction
    TestCase.assertNotNull(buildAction)
    UsefulTestCase.assertSize(2, buildAction.myGradlePaths)
    Truth.assertThat(buildAction.myGradlePaths).containsExactly(":app", ":feature1")
  }
}

private fun rootModule(gradleVersion: String) = JavaModuleModelBuilder(":", gradleVersion, false)

private fun androidModule(
  gradlePath: String,
  gradleVersion: String,
  projectType: Int = AndroidProjectTypes.PROJECT_TYPE_APP,
  moduleDependencies: List<String> = emptyList(),
  dynamicFeatures: List<String> = emptyList()
) = AndroidModuleModelBuilder(
  gradlePath,
  gradleVersion = gradleVersion,
  selectedBuildVariant = "debug",
  projectBuilder = createAndroidProjectBuilder(
    projectType = { projectType },
    androidModuleDependencyList = { moduleDependencies.map { AndroidModuleDependency(it, "debug") } },
    dynamicFeatures = { dynamicFeatures }
  )
)
