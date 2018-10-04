/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.builder.model.AndroidProject.PROJECT_TYPE_APP
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModelCollection
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsModuleType
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.gradle.structure.model.meta.maybeValue
import com.android.tools.idea.gradle.structure.model.moduleTypeFromAndroidModuleType
import com.android.tools.idea.gradle.structure.model.parsedModelModuleType
import com.android.tools.idea.gradle.structure.model.repositories.search.AndroidSdkRepositories
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository
import com.android.tools.idea.gradle.util.GradleUtil.getAndroidModuleIcon
import com.android.utils.combineAsCamelCase
import java.io.File
import javax.swing.Icon

class PsAndroidModule(
  parent: PsProject,
  override val gradlePath: String
) : PsModule(parent) {
  override val descriptor by AndroidModuleDescriptors
  var resolvedModel: AndroidModuleModel? = null; private set
  override var projectType: PsModuleType = PsModuleType.UNKNOWN; private set
  var isLibrary: Boolean = false; private set
  override var rootDir: File? = null; private set
  override var icon: Icon? = null; private set

  private var buildTypeCollection: PsBuildTypeCollection? = null
  private var flavorDimensionCollection: PsFlavorDimensionCollection? = null
  private var productFlavorCollection: PsProductFlavorCollection? = null
  private var variantCollection: PsVariantCollection? = null
  private var dependencyCollection: PsAndroidModuleDependencyCollection? = null
  private var signingConfigCollection: PsSigningConfigCollection? = null

  var buildToolsVersion by AndroidModuleDescriptors.buildToolsVersion
  var compileSdkVersion by AndroidModuleDescriptors.compileSdkVersion
  var sourceCompatibility by AndroidModuleDescriptors.sourceCompatibility
  var targetCompatibility by AndroidModuleDescriptors.targetCompatibility

  fun init(
    name: String,
    parentModule: PsModule?,
    resolvedModel: AndroidModuleModel?,
    parsedModel: GradleBuildModel?
  ) {
    super.init(name, parentModule, parsedModel)
    this.resolvedModel = resolvedModel

    projectType =
      moduleTypeFromAndroidModuleType(resolvedModel?.androidProject?.projectType).takeUnless { it == PsModuleType.UNKNOWN }
      ?: parsedModel?.parsedModelModuleType() ?: PsModuleType.UNKNOWN
    isLibrary = projectType.androidModuleType != PROJECT_TYPE_APP
    rootDir = resolvedModel?.rootDirPath ?: parsedModel?.virtualFile?.path?.let { File(it).parentFile }
    icon = projectType.androidModuleType?.let { getAndroidModuleIcon(it) }

    buildTypeCollection?.refresh()
    flavorDimensionCollection?.refresh()
    productFlavorCollection?.refresh()
    variantCollection?.refresh()
    dependencyCollection = null
    signingConfigCollection?.refresh()
  }

  val buildTypes: PsModelCollection<PsBuildType> get() = getOrCreateBuildTypeCollection()
  val productFlavors: PsModelCollection<PsProductFlavor> get() = getOrCreateProductFlavorCollection()
  val variants: PsModelCollection<PsVariant> get() = getOrCreateVariantCollection()
  override val dependencies: PsAndroidModuleDependencyCollection get() = getOrCreateDependencyCollection()
  val signingConfigs: PsModelCollection<PsSigningConfig> get() = getOrCreateSigningConfigCollection()
  val defaultConfig = PsAndroidModuleDefaultConfig(this)
  val flavorDimensions: PsModelCollection<PsFlavorDimension> get() = getOrCreateFlavorDimensionCollection()

  fun findBuildType(buildType: String): PsBuildType? = getOrCreateBuildTypeCollection().findElement(buildType)

  fun findProductFlavor(dimension: String, name: String): PsProductFlavor? =
    getOrCreateProductFlavorCollection().findElement(PsProductFlavorKey(dimension, name))

  fun findVariant(key: PsVariantKey): PsVariant? = getOrCreateVariantCollection().findElement(key)

  fun findSigningConfig(signingConfig: String): PsSigningConfig? = getOrCreateSigningConfigCollection().findElement(signingConfig)

  fun findFlavorDimension(flavorDimensionName: String): PsFlavorDimension? =
    getOrCreateFlavorDimensionCollection().findElement(flavorDimensionName)

  override fun canDependOn(module: PsModule): Boolean =
    // 'module' is either a Java library or an AAR module.
    (module as? PsAndroidModule)?.isLibrary == true || (module is PsJavaModule)

  override fun populateRepositories(repositories: MutableList<ArtifactRepository>) {
    super.populateRepositories(repositories)
    repositories.addAll(listOfNotNull(AndroidSdkRepositories.getAndroidRepository(), AndroidSdkRepositories.getGoogleRepository()))
  }

  // TODO(solodkyy): Return a collection of PsBuildConfiguration instead of strings.
  override fun getConfigurations(onlyImportantFor: ImportantFor?): List<String> {

    fun applicableArtifacts() = listOf("", "test", "androidTest")

    fun flavorsByDimension(dimension: String) =
      productFlavors.filter { it.dimension.maybeValue == dimension }.map { it.name }

    fun buildFlavorCombinations() = when {
      flavorDimensions.size > 1 -> flavorDimensions
        .fold(listOf(listOf(""))) { acc, dimension ->
          flavorsByDimension(dimension.name).flatMap { flavor ->
            acc.map { prefix -> prefix + flavor }
          }
        }
        .map { it.filter { it != "" }.combineAsCamelCase() }
      else -> listOf()  // There are no additional flavor combinations if there is only one flavor dimension.
    }

    fun applicableProductFlavors() =
      listOf("") +
      (if (onlyImportantFor == null || onlyImportantFor == ImportantFor.LIBRARY) productFlavors.map { it.name } else listOf()) +
      (if (onlyImportantFor == null) buildFlavorCombinations() else listOf())

    fun applicableBuildTypes(artifact: String) =
    // TODO(solodkyy): Include product flavor combinations
      when (artifact) {
        "androidTest" -> listOf("")  // androidTest is built only for the configured buildType.
        else -> listOf("") +
                (if (onlyImportantFor == null || onlyImportantFor == ImportantFor.LIBRARY) buildTypes.map { it.name } else listOf())
      }

    // TODO(solodkyy): When explicitly requested return other advanced scopes (compileOnly, api).
    fun applicableScopes() = listOfNotNull(
      "implementation",
      "api".takeIf { onlyImportantFor == null || onlyImportantFor == ImportantFor.MODULE },
      "compileOnly".takeIf { onlyImportantFor == null },
      "annotationProcessor".takeIf { onlyImportantFor == null })

    val result = mutableListOf<String>()
    applicableArtifacts().forEach { artifact ->
      applicableProductFlavors().forEach { productFlavor ->
        applicableBuildTypes(artifact).forEach { buildType ->
          applicableScopes().forEach { scope ->
            result.add(listOf(artifact, productFlavor, buildType, scope).filter { it != "" }.combineAsCamelCase())
          }
        }
      }
    }
    return result.toList()
  }

  fun addNewBuildType(name: String): PsBuildType = getOrCreateBuildTypeCollection().addNew(name)

  fun validateBuildTypeName(name: String): String? = when {
    name.isEmpty() -> "Build type name cannot be empty."
    getOrCreateBuildTypeCollection().any { it.name == name } -> "Duplicate build type name: '$name'"
    else -> null
  }

  fun removeBuildType(buildType: PsBuildType) = getOrCreateBuildTypeCollection().remove(buildType.name)

  fun addNewFlavorDimension(name: String) = getOrCreateFlavorDimensionCollection().addNew(name)

  fun validateFlavorDimensionName(name: String): String? = when {
    name.isEmpty() -> "Flavor dimension name cannot be empty."
    getOrCreateFlavorDimensionCollection().any { it.name == name } -> "Duplicate flavor dimension name: '$name'"
    else -> null
  }

  fun removeFlavorDimension(flavorDimension: PsFlavorDimension) = getOrCreateFlavorDimensionCollection().remove(flavorDimension.name)

  fun addNewProductFlavor(dimension: String, name: String): PsProductFlavor =
    getOrCreateProductFlavorCollection().addNew(PsProductFlavorKey(dimension, name))

  fun validateProductFlavorName(name: String): String? = when {
    name.isEmpty() -> "Product flavor name cannot be empty."
    getOrCreateProductFlavorCollection().any { it.name == name } -> "Duplicate product flavor name: '$name'"
    else -> null
  }

  fun removeProductFlavor(productFlavor: PsProductFlavor) =
    getOrCreateProductFlavorCollection()
      .remove(PsProductFlavorKey(productFlavor.dimension.maybeValue.orEmpty(), productFlavor.name))

  fun addNewSigningConfig(name: String): PsSigningConfig = getOrCreateSigningConfigCollection().addNew(name)

  fun validateSigningConfigName(name: String): String? = when {
    name.isEmpty() -> "Signing config name cannot be empty."
    getOrCreateSigningConfigCollection().any { it.name == name } -> "Duplicate signing config name: '$name'"
    else -> null
  }

  fun removeSigningConfig(signingConfig: PsSigningConfig) = getOrCreateSigningConfigCollection().remove(signingConfig.name)


  private fun getOrCreateBuildTypeCollection(): PsBuildTypeCollection =
    buildTypeCollection ?: PsBuildTypeCollection(this).also { buildTypeCollection = it }

  private fun getOrCreateFlavorDimensionCollection(): PsFlavorDimensionCollection =
    flavorDimensionCollection ?: PsFlavorDimensionCollection(this).also { flavorDimensionCollection = it }

  private fun getOrCreateProductFlavorCollection(): PsProductFlavorCollection =
    productFlavorCollection ?: PsProductFlavorCollection(this).also { productFlavorCollection = it }

  private fun getOrCreateVariantCollection(): PsVariantCollection =
    variantCollection ?: PsVariantCollection(this).also { variantCollection = it }

  private fun getOrCreateDependencyCollection(): PsAndroidModuleDependencyCollection =
    dependencyCollection ?: PsAndroidModuleDependencyCollection(this).also { dependencyCollection = it }

  private fun getOrCreateSigningConfigCollection(): PsSigningConfigCollection =
    signingConfigCollection ?: PsSigningConfigCollection(this).also { signingConfigCollection = it }

  override fun findLibraryDependencies(group: String?, name: String): List<PsDeclaredLibraryDependency> =
    dependencies.findLibraryDependencies(group, name)

  override fun resetDependencies() {
    resetDeclaredDependencies()
    resetResolvedDependencies()
  }

  internal fun resetResolvedDependencies() {
    variants.forEach { variant -> variant.forEachArtifact { artifact -> artifact.resetDependencies() } }
  }

  private fun resetDeclaredDependencies() {
    dependencyCollection = null
  }
}
