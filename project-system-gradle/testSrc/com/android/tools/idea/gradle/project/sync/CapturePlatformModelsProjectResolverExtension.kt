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
package com.android.tools.idea.gradle.project.sync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.registerExtension
import org.gradle.api.Project
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import java.io.Serializable

class CapturePlatformModelsProjectResolverExtension : AbstractProjectResolverExtension() {
  companion object {
    private val kotlinModels = mutableMapOf<String, KotlinGradleModel>()
    private val kaptModels = mutableMapOf<String, KaptGradleModel>()
    private val mppModels = mutableMapOf<String, KotlinMPPGradleModel>()
    private val externalProjectModels = mutableMapOf<String, ExternalProject>()
    private val testGradleModels = mutableMapOf<String, TestGradleModel>()
    private val testParameterizedGradleModels = mutableMapOf<String, TestParameterizedGradleModel>()

    fun getKotlinModel(module: Module): KotlinGradleModel? = kotlinModels[getGradleProjectPath(module)]
    fun getKaptModel(module: Module): KaptGradleModel? = kaptModels[getGradleProjectPath(module)]
    fun getMppModel(module: Module): KotlinMPPGradleModel? = mppModels[getGradleProjectPath(module)]
    fun getExternalProjectModel(module: Module): ExternalProject? = externalProjectModels[getGradleProjectPath(module)]
    fun getTestGradleModel(module: Module): TestGradleModel? = testGradleModels[getGradleProjectPath(module)]
    fun getTestParameterizedGradleModel(module: Module): TestParameterizedGradleModel? =
      testParameterizedGradleModels[getGradleProjectPath(module)]

    private fun getGradleProjectPath(module: Module): String? {
      return ExternalSystemApiUtil.getExternalProjectPath(module)
        .takeIf { ExternalSystemApiUtil.getExternalModuleType(module) == null } // Return models for holder modules only.
    }

    fun reset() {
      kotlinModels.clear()
      kaptModels.clear()
      mppModels.clear()
      externalProjectModels.clear()
      testGradleModels.clear()
      testParameterizedGradleModels.clear()
    }

    fun registerTestHelperProjectResolver(disposable: Disposable) {
      ApplicationManager.getApplication().registerExtension(
        @Suppress("UnstableApiUsage")
        EP_NAME,
        CapturePlatformModelsProjectResolverExtension(), // Note: a new instance is created by the external system.
        disposable
      )
      Disposer.register(disposable, object : Disposable {
        override fun dispose() {
          reset()
        }
      })
    }
  }

  override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    val gradleProjectPath = ideModule.data.linkedExternalProjectPath
    resolverCtx.getExtraProject(gradleModule, KotlinGradleModel::class.java)?.let { kotlinModels[gradleProjectPath] = it }
    // The KaptGradleModel is present for Java modules
    resolverCtx.getExtraProject(gradleModule, KaptGradleModel::class.java)?.let {
      kaptModels[gradleProjectPath] = it
    }
    resolverCtx.getExtraProject(gradleModule, KotlinMPPGradleModel::class.java)?.let {
      mppModels[gradleProjectPath] = it
    }
    // For Android modules it is contained within the IdeAndroidModels class
    resolverCtx.getExtraProject(gradleModule, IdeAndroidModels::class.java)?.let {
      it.kaptGradleModel?.let { kaptModel ->
        kaptModels[gradleProjectPath] = kaptModel
      }
    }
    resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java)?.let {
      externalProjectModels[gradleProjectPath] = it
    }
    resolverCtx.getExtraProject(gradleModule, TestGradleModel::class.java)?.let {
      testGradleModels[gradleProjectPath] = it
    }
    resolverCtx.getExtraProject(gradleModule, TestParameterizedGradleModel::class.java)?.let {
      testParameterizedGradleModels[gradleProjectPath] = it
    }
    super.populateModuleExtraModels(gradleModule, ideModule)
  }

  override fun getToolingExtensionsClasses(): Set<Class<*>> {
    return setOf(TestModelBuilderService::class.java)
  }

  override fun getModelProvider(): ProjectImportModelProvider {
    return TestGradleModelProvider("EHLO")
  }

  override fun getExtraProjectModelClasses(): Set<Class<*>> {
    return setOf(TestGradleModel::class.java)
  }
}

class TestGradleModelProvider(private val paramValue: String) : ProjectImportModelProvider {
  override fun populateBuildModels(
    controller: BuildController,
    buildModel: GradleBuild,
    consumer: ProjectImportModelProvider.BuildModelConsumer
  ) {
  }

  override fun populateProjectModels(
    controller: BuildController,
    projectModel: Model,
    modelConsumer: ProjectImportModelProvider.ProjectModelConsumer
  ) {
    val testGradleModel = controller.findModel(projectModel, TestGradleModel::class.java)
    testGradleModel?.also { pluginModel -> modelConsumer.consume(pluginModel, TestGradleModel::class.java) }

    val testParameterizedGradleModel =
      controller.findModel(projectModel, TestParameterizedGradleModel::class.java, ModelBuilderService.Parameter::class.java) { parameter ->
        parameter.value = paramValue
      }
    testParameterizedGradleModel?.also { pluginModel -> modelConsumer.consume(pluginModel, TestParameterizedGradleModel::class.java) }
  }
}

interface TestGradleModel {
  val message: String
}

interface TestParameterizedGradleModel {
  val message: String
}

data class TestGradleModelImpl(override val message: String) : TestGradleModel, Serializable
data class TestParameterizedGradleModelImpl(override val message: String) : TestParameterizedGradleModel, Serializable

class TestModelBuilderService : ModelBuilderService {
  override fun canBuild(modelName: String?): Boolean {
    return modelName == TestGradleModel::class.java.name
  }

  override fun buildAll(modelName: String?, project: Project?): Any {
    return TestGradleModelImpl("Hello, ${project?.buildDir}")
  }

  override fun getErrorMessageBuilder(project: Project, e: Exception): ErrorMessageBuilder {
    return ErrorMessageBuilder
      .create(project, e, "Gradle import errors")
      .withDescription(e.message.orEmpty() + "\n" + e.stackTraceToString())
  }
}

class TestParameterizedModelBuilderService : ModelBuilderService.Ex {
  override fun canBuild(modelName: String?): Boolean {
    return modelName == TestParameterizedGradleModel::class.java.name
  }

  override fun buildAll(modelName: String?, project: Project?, context: ModelBuilderContext): Any {
    return TestParameterizedGradleModelImpl("Parameter: ${context.parameter} BuildDir: ${project?.buildDir}")
  }

  override fun buildAll(modelName: String?, project: Project?): Any {
    return TestParameterizedGradleModelImpl("Hello, ${project?.buildDir}")
  }

  override fun getErrorMessageBuilder(project: Project, e: Exception): ErrorMessageBuilder {
    return ErrorMessageBuilder
      .create(project, e, "Gradle import errors")
      .withDescription(e.message.orEmpty() + "\n" + e.stackTraceToString())
  }
}

fun KotlinGradleModel.testSourceSetNames(): Collection<String> = cachedCompilerArgumentsBySourceSet.keys
fun KaptGradleModel.testSourceSetNames(): Collection<String> = sourceSets.map { it.sourceSetName }
