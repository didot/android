/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model

import com.android.builder.model.NativeSettings
import com.android.builder.model.NativeToolchain
import com.android.builder.model.v2.models.ndk.NativeAbi
import com.android.builder.model.v2.models.ndk.NativeBuildSystem
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.ide.common.gradle.model.IdeNativeAndroidProject
import com.android.ide.common.gradle.model.IdeNativeModule
import com.android.ide.common.gradle.model.IdeNativeVariantAbi
import com.android.ide.common.repository.GradleVersion
import com.intellij.serialization.PropertyMapping
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.HashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

interface INdkModel {
  val features: NdkModelFeatures
  val allVariantAbis: Collection<VariantAbi>
  val syncedVariantAbis: Collection<VariantAbi>
  val symbolFolders: Map<VariantAbi, Set<File>>
  val buildFiles: Collection<File>
  val buildSystems: Collection<String>
  val defaultNdkVersion: String
}

sealed class NdkModel : INdkModel

class V1NdkModel(
  val androidProject: IdeNativeAndroidProject,
  val nativeVariantAbis: List<IdeNativeVariantAbi>
) : NdkModel() {

  @Transient
  override val features: NdkModelFeatures = NdkModelFeatures(GradleVersion.tryParse(androidProject.modelVersion))

  /** Map of synced variants. For full-variants sync, contains all variant and ABIs from [allVariantAbis]. */
  @Transient
  private val ndkVariantsByVariantAbi: MutableMap<VariantAbi, NdkVariant> = HashMap()

  @Transient
  private val toolchainsByName: MutableMap<String, NativeToolchain> = HashMap()

  @Transient
  private val settingsByName: MutableMap<String, NativeSettings> = HashMap()

  @Transient
  override val allVariantAbis: Collection<VariantAbi> = LinkedHashSet(
    androidProject.variantInfos
      .flatMap { (variant, info) ->
        info.abiNames.map { abi -> VariantAbi(variant, abi) }
      }
      .sortedBy { it.displayName }
  )

  @Transient
  override val syncedVariantAbis: Collection<VariantAbi> = ndkVariantsByVariantAbi.keys

  init {
    if (nativeVariantAbis.isEmpty()) {
      // Full-variants sync.
      populateForFullVariantsSync()
    }
    else {
      // Single-variant sync.
      populateForSingleVariantSync()
    }
  }

  // Call this method for full variants sync.
  private fun populateForFullVariantsSync() {
    for (artifact in androidProject.artifacts) {
      val variantName = if (features.isGroupNameSupported) artifact.groupName else artifact.name
      val variantAbi = VariantAbi(variantName, artifact.abi)
      var variant = ndkVariantsByVariantAbi[variantAbi]
      if (variant == null) {
        variant = NdkVariant(variantAbi.displayName, features.isExportedHeadersSupported)
        ndkVariantsByVariantAbi[variantAbi] = variant
      }
      variant.addArtifact(artifact)
    }

    // populate toolchains
    populateToolchains(androidProject.toolChains)

    // populate settings
    populateSettings(androidProject.settings)
  }

  // Call this method for single variant sync.
  private fun populateForSingleVariantSync() {
    for (variantAbi in nativeVariantAbis) {
      populateForNativeVariantAbi(variantAbi)
    }
  }

  private fun populateForNativeVariantAbi(nativeVariantAbi: IdeNativeVariantAbi) {
    val variantAbi = VariantAbi(nativeVariantAbi.variantName, nativeVariantAbi.abi)
    val variant = NdkVariant(variantAbi.displayName, features.isExportedHeadersSupported)
    for (artifact in nativeVariantAbi.artifacts) {
      variant.addArtifact(artifact)
    }
    ndkVariantsByVariantAbi[variantAbi] = variant

    // populate toolchains
    populateToolchains(nativeVariantAbi.toolChains)

    // populate settings
    populateSettings(nativeVariantAbi.settings)
  }

  private fun populateToolchains(nativeToolchains: Collection<NativeToolchain>) {
    for (toolchain in nativeToolchains) {
      toolchainsByName[toolchain.name] = toolchain
    }
  }

  private fun populateSettings(nativeSettings: Collection<NativeSettings>) {
    for (settings in nativeSettings) {
      settingsByName[settings.name] = settings
    }
  }

  val variants: Collection<NdkVariant> get() = ndkVariantsByVariantAbi.values

  override val symbolFolders: Map<VariantAbi, Set<File>> get() = ndkVariantsByVariantAbi.mapValues { (_, ndkVariant) ->
    ndkVariant.artifacts.mapNotNull { artifact ->
      artifact.outputFile?.takeIf { it.exists() }?.parentFile
    }.toSet()
  }

  @Transient
  override val buildFiles: Collection<File> = androidProject.buildFiles

  @Transient
  override val buildSystems: Collection<String> = androidProject.buildSystems

  @Transient
  override val defaultNdkVersion: String = androidProject.defaultNdkVersion

  fun getNdkVariant(variantAbi: VariantAbi?): NdkVariant? = ndkVariantsByVariantAbi[variantAbi]
  fun findToolchain(toolchainName: String): NativeToolchain? = toolchainsByName[toolchainName]
  fun findSettings(settingsName: String): NativeSettings? = settingsByName[settingsName]
}

class V2NdkModel @PropertyMapping("agpVersion", "nativeModule") constructor(
  private val agpVersion: String,
  nativeModuleArg: NativeModule) : NdkModel() {
  
  val nativeModule: NativeModule = nativeModuleArg as? IdeNativeModule ?: IdeNativeModule(nativeModuleArg)

  @Transient
  override val features: NdkModelFeatures = NdkModelFeatures(GradleVersion.tryParse(agpVersion))

  @Transient
  val abiByVariantAbi: Map<VariantAbi, NativeAbi> = nativeModule.variants.flatMap { variant ->
    variant.abis.map { abi ->
      VariantAbi(variant.name, abi.name) to abi
    }
  }.toMap()

  @Transient
  override val allVariantAbis: Collection<VariantAbi> = LinkedHashSet(abiByVariantAbi.keys.sortedBy { it.displayName })

  override val syncedVariantAbis: Collection<VariantAbi>
    get() = LinkedHashSet(
      abiByVariantAbi.entries.filter { (_, abi) -> abi.sourceFlagsFile.exists() }
        .map { (variantAbi, _) -> variantAbi }
        .sortedBy { it.displayName }
    )

  override val symbolFolders: Map<VariantAbi, Set<File>>
    get() = abiByVariantAbi.mapValues { (_, abi) ->
      abi.symbolFolderIndexFile.readIndexFile()
    }

  override val buildFiles: Collection<File> get() = abiByVariantAbi.values.flatMap { it.buildFileIndexFile.readIndexFile() }.toSet()

  @Transient
  override val buildSystems: Collection<String> = listOf(
    when (nativeModule.nativeBuildSystem) {
      NativeBuildSystem.CMAKE -> "cmake"
      NativeBuildSystem.NDK_BUILD -> "ndkBuild"
    })

  @Transient
  override val defaultNdkVersion: String = nativeModule.defaultNdkVersion
}

private fun File.readIndexFile(): Set<File> = when {
  this.isFile -> this.readLines(StandardCharsets.UTF_8).map { File(it) }.toSet()
  else -> emptySet()
}