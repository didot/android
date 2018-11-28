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
package com.android.tools.idea.gradle.dsl.parser

import com.android.tools.idea.gradle.dsl.parser.android.AaptOptionsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.AaptOptionsDslElement.AAPT_OPTIONS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.AdbOptionsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.AdbOptionsDslElement.ADB_OPTIONS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement.ANDROID_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.BuildTypeDslElement
import com.android.tools.idea.gradle.dsl.parser.android.BuildTypesDslElement
import com.android.tools.idea.gradle.dsl.parser.android.BuildTypesDslElement.BUILD_TYPES_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.CompileOptionsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.DataBindingDslElement
import com.android.tools.idea.gradle.dsl.parser.android.DataBindingDslElement.DATA_BINDING_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.DexOptionsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.DexOptionsDslElement.DEX_OPTIONS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.ExternalNativeBuildDslElement
import com.android.tools.idea.gradle.dsl.parser.android.ExternalNativeBuildDslElement.EXTERNAL_NATIVE_BUILD_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.LintOptionsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.LintOptionsDslElement.LINT_OPTIONS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.PackagingOptionsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.PackagingOptionsDslElement.PACKAGING_OPTIONS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorDslElement
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement.PRODUCT_FLAVORS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigDslElement
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigsDslElement.SIGNING_CONFIGS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetDslElement
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetsDslElement.SOURCE_SETS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.SplitsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.SplitsDslElement.SPLITS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.TestOptionsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.TestOptionsDslElement.TEST_OPTIONS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.CMakeDslElement
import com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.CMakeDslElement.CMAKE_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.NdkBuildDslElement
import com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.NdkBuildDslElement.NDK_BUILD_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.ExternalNativeBuildOptionsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement.NDK_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.VectorDrawablesOptionsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.VectorDrawablesOptionsDslElement.VECTOR_DRAWABLES_OPTIONS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.CMakeOptionsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.NdkBuildOptionsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceFileDslElement
import com.android.tools.idea.gradle.dsl.parser.android.splits.AbiDslElement
import com.android.tools.idea.gradle.dsl.parser.android.splits.AbiDslElement.ABI_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.splits.DensityDslElement
import com.android.tools.idea.gradle.dsl.parser.android.splits.DensityDslElement.DENSITY_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.splits.LanguageDslElement
import com.android.tools.idea.gradle.dsl.parser.android.splits.LanguageDslElement.LANGUAGE_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.UnitTestsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.UnitTestsDslElement.UNIT_TESTS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement.BUILDSCRIPT_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement.SUBPROJECTS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationDslElement
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationsDslElement
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationsDslElement.CONFIGURATIONS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement.DEPENDENCIES_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.COMPILE_OPTIONS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.repositories.FlatDirRepositoryDslElement
import com.android.tools.idea.gradle.dsl.parser.repositories.FlatDirRepositoryDslElement.FLAT_DIR_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenCredentialsDslElement
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenCredentialsDslElement.CREDENTIALS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement.JCENTER_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement.MAVEN_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement.REPOSITORIES_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement

/**
 * Get the block element that is given be repeat
 */
fun GradleDslFile.getBlockElement(nameParts: List<String>, parentElement: GradlePropertiesDslElement): GradlePropertiesDslElement? {
  return nameParts.map { namePart -> namePart.trim { it <= ' ' } }.fold(parentElement) { resultElement, nestedElementName ->
    val elementName = GradleNameElement.fake(nestedElementName)
    val element = resultElement.getElement(nestedElementName)

    if (element is GradlePropertiesDslElement) {
      return@fold element
    }

    if (element != null) return null

    // Handle special cases based on the elements name
    when (nestedElementName) {
      "rootProject" -> return@fold context.rootProjectFile ?: this
      // Ext element is supported for any Gradle domain object that implements ExtensionAware. Here we get or create
      // such an element if needed.
      EXT_BLOCK_NAME -> {
        if (resultElement !is BuildScriptDslElement) {
          val newElement = ExtDslElement(resultElement)
          resultElement.setParsedElement(newElement)
          return@fold newElement
        }
        else {
          return@fold parentElement.dslFile.getPropertyElement(EXT_BLOCK_NAME, ExtDslElement::class.java)
            ?: ExtDslElement(parentElement.dslFile).also { parentElement.dslFile.addParsedPropertyAsFirstElement(it) }
        }
      }
      APPLY_BLOCK_NAME -> {
        val newApplyElement = ApplyDslElement(resultElement)
        resultElement.setParsedElement(newApplyElement)
        return@fold newApplyElement
      }
    }

    // Handle the normal cases
    val newElement: GradlePropertiesDslElement = when (resultElement) {
      is GradleDslFile -> createNewElementForFileOrSubProject(resultElement, nestedElementName) ?: return null
      is SubProjectsDslElement -> createNewElementForFileOrSubProject(resultElement, nestedElementName) ?: return null
      is BuildScriptDslElement -> when (nestedElementName) {
        DEPENDENCIES_BLOCK_NAME -> DependenciesDslElement(resultElement)
        REPOSITORIES_BLOCK_NAME -> RepositoriesDslElement(resultElement)
        else -> return null
      }
      is RepositoriesDslElement -> when (nestedElementName) {
        MAVEN_BLOCK_NAME -> MavenRepositoryDslElement(resultElement, elementName)
        JCENTER_BLOCK_NAME -> MavenRepositoryDslElement(resultElement, elementName)
        FLAT_DIR_BLOCK_NAME -> FlatDirRepositoryDslElement(resultElement)
        else -> return null
      }
      is MavenRepositoryDslElement -> when (nestedElementName) {
        CREDENTIALS_BLOCK_NAME -> MavenCredentialsDslElement(resultElement)
        else -> return null
      }
      is AndroidDslElement -> when (nestedElementName) {
        "defaultConfig" -> ProductFlavorDslElement(resultElement, elementName)
        PRODUCT_FLAVORS_BLOCK_NAME -> ProductFlavorsDslElement(resultElement)
        BUILD_TYPES_BLOCK_NAME -> BuildTypesDslElement(resultElement)
        COMPILE_OPTIONS_BLOCK_NAME -> CompileOptionsDslElement(resultElement)
        EXTERNAL_NATIVE_BUILD_BLOCK_NAME -> ExternalNativeBuildDslElement(resultElement)
        SIGNING_CONFIGS_BLOCK_NAME -> SigningConfigsDslElement(resultElement)
        SOURCE_SETS_BLOCK_NAME -> SourceSetsDslElement(resultElement)
        AAPT_OPTIONS_BLOCK_NAME -> AaptOptionsDslElement(resultElement)
        ADB_OPTIONS_BLOCK_NAME -> AdbOptionsDslElement(resultElement)
        DATA_BINDING_BLOCK_NAME -> DataBindingDslElement(resultElement)
        DEX_OPTIONS_BLOCK_NAME -> DexOptionsDslElement(resultElement)
        LINT_OPTIONS_BLOCK_NAME -> LintOptionsDslElement(resultElement)
        PACKAGING_OPTIONS_BLOCK_NAME -> PackagingOptionsDslElement(resultElement)
        SPLITS_BLOCK_NAME -> SplitsDslElement(resultElement)
        TEST_OPTIONS_BLOCK_NAME -> TestOptionsDslElement(resultElement)
        else -> return null
      }
      is ExternalNativeBuildDslElement -> when (nestedElementName) {
        CMAKE_BLOCK_NAME -> CMakeDslElement(resultElement)
        NDK_BUILD_BLOCK_NAME -> NdkBuildDslElement(resultElement)
        else -> return null
      }
      is ProductFlavorsDslElement -> ProductFlavorDslElement(resultElement, elementName)
      is ProductFlavorDslElement -> when (nestedElementName) {
        "manifestPlaceholders" -> GradleDslExpressionMap(resultElement, elementName)
        "testInstrumentationRunnerArguments" -> GradleDslExpressionMap(resultElement, elementName)
        EXTERNAL_NATIVE_BUILD_BLOCK_NAME -> ExternalNativeBuildOptionsDslElement(resultElement)
        NDK_BLOCK_NAME -> NdkOptionsDslElement(resultElement)
        VECTOR_DRAWABLES_OPTIONS_BLOCK_NAME -> VectorDrawablesOptionsDslElement(resultElement)
        else -> return null
      }
      is BuildTypesDslElement -> BuildTypeDslElement(resultElement, elementName)
      is BuildTypeDslElement -> when (nestedElementName) {
        "manifestPlaceholders" -> GradleDslExpressionMap(resultElement, elementName)
        else -> return null
      }
      is SigningConfigsDslElement -> SigningConfigDslElement(resultElement, elementName)
      is SourceSetsDslElement -> SourceSetDslElement(resultElement, elementName)
      is SourceSetDslElement -> when (nestedElementName) {
        "manifest" -> SourceFileDslElement(resultElement, elementName)
        else -> SourceDirectoryDslElement(resultElement, elementName)
      }
      is ExternalNativeBuildOptionsDslElement -> when (nestedElementName) {
        CMAKE_BLOCK_NAME -> CMakeOptionsDslElement(resultElement)
        NDK_BUILD_BLOCK_NAME -> NdkBuildOptionsDslElement(resultElement)
        else -> return null
      }
      is SplitsDslElement -> when (nestedElementName) {
        ABI_BLOCK_NAME -> AbiDslElement(resultElement)
        DENSITY_BLOCK_NAME -> DensityDslElement(resultElement)
        LANGUAGE_BLOCK_NAME -> LanguageDslElement(resultElement)
        else -> return null
      }
      is TestOptionsDslElement -> when (nestedElementName) {
        UNIT_TESTS_BLOCK_NAME -> UnitTestsDslElement(resultElement)
        else -> return null
      }
      is ConfigurationsDslElement -> ConfigurationDslElement(resultElement, elementName)
      else -> return null
    }

    resultElement.setParsedElement(newElement)
    return@fold newElement
  }
}

private fun createNewElementForFileOrSubProject(resultElement: GradlePropertiesDslElement,
                                                nestedElementName: String): GradlePropertiesDslElement? {
  return when (nestedElementName) {
    EXT_BLOCK_NAME -> ExtDslElement(resultElement)
    ANDROID_BLOCK_NAME -> AndroidDslElement(resultElement)
    CONFIGURATIONS_BLOCK_NAME -> ConfigurationsDslElement(resultElement)
    DEPENDENCIES_BLOCK_NAME -> DependenciesDslElement(resultElement)
    SUBPROJECTS_BLOCK_NAME -> SubProjectsDslElement(resultElement)
    BUILDSCRIPT_BLOCK_NAME -> BuildScriptDslElement(resultElement)
    REPOSITORIES_BLOCK_NAME -> RepositoriesDslElement(resultElement)
    else -> {
      val projectKey = ProjectPropertiesDslElement.getStandardProjectKey(nestedElementName) ?: return null
      return ProjectPropertiesDslElement(resultElement, GradleNameElement.fake(projectKey))
    }
  }
}