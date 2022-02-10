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

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.ide.gradle.model.ArtifactIdentifier
import com.android.ide.gradle.model.ArtifactIdentifierImpl
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.model.IdeBaseArtifactCore
import com.android.tools.idea.gradle.model.IdeUnresolvedDependency
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeAndroidProject
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi
import com.android.tools.idea.gradle.model.ndk.v2.IdeNativeModule
import com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptSourceSetModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import java.io.File

typealias IdeVariantFetcher = (
  controller: BuildController,
  variantNameResolvers: (buildId: File, projectPath: String) -> VariantNameResolver,
  module: AndroidModule,
  configuration: ModuleConfiguration
) -> IdeVariantCoreImpl?

@UsedInBuildAction
abstract class GradleModule(val gradleProject: BasicGradleProject) {
  abstract fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer)
  abstract val variantNameResolver: VariantNameResolver
  val findModelRoot: Model get() = gradleProject
  val id = createUniqueModuleId(gradleProject)

  var projectSyncIssues: List<IdeSyncIssue>? = null; private set
  fun setSyncIssues(issues: List<IdeSyncIssue>) {
    projectSyncIssues = issues
  }

  protected inner class ModelConsumer(val buildModelConsumer: ProjectImportModelProvider.BuildModelConsumer) {
    inline fun <reified T : Any> T.deliver() {
      buildModelConsumer.consumeProjectModel(gradleProject, this, T::class.java)
    }
  }
}

/**
 * The container class for Java module, containing its Android models handled by the Android plugin.
 */
@UsedInBuildAction
class JavaModule(
  gradleProject: BasicGradleProject,
  private val kotlinGradleModel: KotlinGradleModel?,
  private val kaptGradleModel: KaptGradleModel?
) : GradleModule(gradleProject) {
  override fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer) {
    with(ModelConsumer(consumer)) {
      kotlinGradleModel?.deliver()
      kaptGradleModel?.deliver()
    }
  }

  override val variantNameResolver: VariantNameResolver = fun(_: String?, _: (dimension: String) -> String): String? = null
}

/**
 * The container class for Android module, containing its Android model, Variant models, and dependency modules.
 */
@UsedInBuildAction
@VisibleForTesting
class AndroidModule constructor(
  val agpVersion: GradleVersion?,
  val buildName: String?,
  val buildNameMap: Map<String, File>?,
  gradleProject: BasicGradleProject,
  val androidProject: IdeAndroidProjectImpl,
  /** All configured variant names if supported by the AGP version. */
  val allVariantNames: Set<String>?,
  val defaultVariantName: String?,
  val variantFetcher: IdeVariantFetcher,
  /** Old V1 model. It's only set if [nativeModule] is not set. */
  override val variantNameResolver: VariantNameResolver,
  private val nativeAndroidProject: IdeNativeAndroidProject?,
  /** New V2 model. It's only set if [nativeAndroidProject] is not set. */
  private val nativeModule: IdeNativeModule?
) : GradleModule(gradleProject) {
  val projectType: IdeAndroidProjectType get() = androidProject.projectType

  fun getVariantAbiNames(variantName: String): Collection<String>? {
    return nativeModule?.variants?.firstOrNull { it.name == variantName }?.abis?.map { it.name }
      ?: nativeAndroidProject?.variantInfos?.get(variantName)?.abiNames
  }


  enum class NativeModelVersion { None, V1, V2 }

  val nativeModelVersion: NativeModelVersion = when {
    nativeModule != null -> NativeModelVersion.V2
    nativeAndroidProject != null -> NativeModelVersion.V1
    else -> NativeModelVersion.None
  }

  var syncedVariant: IdeVariantCoreImpl? = null
  var syncedNativeVariantAbiName: String? = null
  var syncedNativeVariant: IdeNativeVariantAbi? = null
  var allVariants: List<IdeVariantCoreImpl>? = null

  var additionalClassifierArtifacts: AdditionalClassifierArtifactsModel? = null
  var kotlinGradleModel: KotlinGradleModel? = null
  var kaptGradleModel: KaptGradleModel? = null

  var unresolvedDependencies: List<IdeUnresolvedDependency> = emptyList()

  /** Returns the list of all libraries this currently selected variant depends on (and temporarily maybe some
   * libraries other variants depend on).
   **/
  fun getLibraryDependencies(): Collection<ArtifactIdentifier> {
    return collectIdentifiers(listOfNotNull(syncedVariant))
  }

  override fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer) {
    // For now, use one model cache per module. It is does deliver the smallest memory footprint, but this is what we get after
    // models are deserialized from the DataNode cache anyway. This will be replaced with a model cache per sync when shared libraries
    // are moved out of `IdeAndroidProject` and delivered to the IDE separately.
    val selectedVariantName =
      syncedVariant?.name
        ?: allVariants?.map { it.name }?.getDefaultOrFirstItem("debug")
        ?: throw AndroidSyncException("No variants found for '${gradleProject.path}'. Check build files to ensure at least one variant exists.")

    val ideAndroidModels = IdeAndroidModels(
      androidProject.patchForKapt(kaptGradleModel),
      (syncedVariant?.let { listOf(it) } ?: allVariants.orEmpty()).patchForKapt(kaptGradleModel),
      selectedVariantName,
      syncedNativeVariantAbiName,
      projectSyncIssues.orEmpty(),
      nativeModule,
      nativeAndroidProject,
      syncedNativeVariant,
      kaptGradleModel
    )
    with(ModelConsumer(consumer)) {
      ideAndroidModels.deliver()
      kotlinGradleModel?.deliver()
      additionalClassifierArtifacts?.deliver()
    }
  }
}

private fun IdeAndroidProjectImpl.patchForKapt(kaptModel: KaptGradleModel?) = copy(isKaptEnabled = kaptModel?.isEnabled ?: false)

private fun List<IdeVariantCoreImpl>.patchForKapt(kaptModel: KaptGradleModel?): List<IdeVariantCoreImpl> {
  if (kaptModel == null) return this
  val sourceSets = kaptModel.sourceSets.associateBy { it.sourceSetName }
  return map { variant ->

    fun <T> T.maybePatch(suffix: String, code: T.(KaptSourceSetModel) -> T): T {
      val kaptSourceSet = sourceSets[variant.name + suffix] ?: return this
      return code(kaptSourceSet)
    }

    fun IdeBaseArtifactCore.generatedSourceFoldersPatchedForKapt(kaptSourceSet: KaptSourceSetModel): List<File> {
      return (generatedSourceFolders.toSet() + listOfNotNull(kaptSourceSet.generatedKotlinSourcesDirFile)).toList()
    }

    variant.copy(
      mainArtifact = variant.mainArtifact
        .maybePatch("") { copy(generatedSourceFolders = generatedSourceFoldersPatchedForKapt(it)) },
      androidTestArtifact = variant.androidTestArtifact
        ?.maybePatch("AndroidTest") { copy(generatedSourceFolders = generatedSourceFoldersPatchedForKapt(it)) },
      unitTestArtifact = variant.unitTestArtifact
        ?.maybePatch("UnitTest") { copy(generatedSourceFolders = generatedSourceFoldersPatchedForKapt(it)) },
      testFixturesArtifact = variant.testFixturesArtifact
        ?.maybePatch("TestFixtures") { copy(generatedSourceFolders = generatedSourceFoldersPatchedForKapt(it)) }
    )
  }
}

data class ModuleConfiguration(val id: String, val variant: String, val abi: String?)

@UsedInBuildAction
class NativeVariantsAndroidModule private constructor(
  gradleProject: BasicGradleProject,
  private val nativeVariants: List<IdeNativeVariantAbi>? // Null means V2.
) : GradleModule(gradleProject) {
  companion object {
    fun createV2(gradleProject: BasicGradleProject): NativeVariantsAndroidModule = NativeVariantsAndroidModule(gradleProject, null)
    fun createV1(gradleProject: BasicGradleProject, nativeVariants: List<IdeNativeVariantAbi>): NativeVariantsAndroidModule =
      NativeVariantsAndroidModule(gradleProject, nativeVariants)
  }

  override fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer) {
    with(ModelConsumer(consumer)) {
      IdeAndroidNativeVariantsModels(nativeVariants, projectSyncIssues.orEmpty()).deliver()
    }
  }

  override val variantNameResolver: VariantNameResolver = fun(_: String?, _: (String) -> String?): String? = null
}

@UsedInBuildAction
fun Collection<String>.getDefaultOrFirstItem(defaultValue: String): String? =
  if (contains(defaultValue)) defaultValue else minByOrNull { it }

@UsedInBuildAction
private fun collectIdentifiers(variants: Collection<IdeVariantCoreImpl>): List<ArtifactIdentifier> {
  return variants.asSequence()
    .flatMap {
      sequenceOf(
        it.mainArtifact.dependencyCores,
        it.androidTestArtifact?.dependencyCores,
        it.unitTestArtifact?.ideDependenciesCore,
        it.testFixturesArtifact?.dependencyCores
      )
        .filterNotNull()
    }
    .flatMap { it.androidLibraries.asSequence() + it.javaLibraries.asSequence() }
    .mapNotNull { GradleCoordinate.parseCoordinateString(it.target.artifactAddress) }
    .map { ArtifactIdentifierImpl(it.groupId, it.artifactId, it.version?.toString().orEmpty()) }
    .distinct()
    .toList()
}

