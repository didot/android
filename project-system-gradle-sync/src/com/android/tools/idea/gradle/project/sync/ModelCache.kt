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
@file:Suppress("DEPRECATION")

package com.android.tools.idea.gradle.project.sync

import com.android.build.OutputFile
import com.android.builder.model.AndroidProject
import com.android.builder.model.Library
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.Variant
import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.Versions
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.LibraryReference
import com.android.tools.idea.gradle.model.impl.BuildFolderPaths
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactOutputImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeLibraryTableImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeVariantAbiImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeModuleImpl
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableSortedSet
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.SystemIndependent
import java.io.File

interface ModelCache {
  val libraryResolver: (LibraryReference) -> IdeLibrary
  fun createLibraryTable(): IdeLibraryTableImpl

  interface V1 : ModelCache {
    fun variantFrom(
      androidProject: IdeAndroidProject,
      variant: Variant,
      modelVersion: GradleVersion?,
      androidModuleId: ModuleId
    ): IdeVariantCoreImpl

    fun androidProjectFrom(project: AndroidProject): IdeAndroidProjectImpl

    fun androidArtifactOutputFrom(output: OutputFile): IdeAndroidArtifactOutputImpl
  }

  interface V2 : ModelCache {
    /**
     * Converts V2's [BasicVariant] and [Variant] to an incomplete [IdeVariantImpl] instance which does not yet include
     * dependency information.
     */
    fun variantFrom(
      androidProject: IdeAndroidProject,
      basicVariant: BasicVariant,
      variant: com.android.builder.model.v2.ide.Variant,
      modelVersion: GradleVersion?
    ): IdeVariantCoreImpl

    /**
     * Supplements an incomplete instance of [IdeVariantImpl] with dependency information from a [VariantDependencies] model.
     */
    fun variantFrom(
      ownerBuildId: BuildId,
      ownerProjectPath: String,
      variant: IdeVariantCoreImpl,
      variantDependencies: VariantDependencies,
      androidProjectPathResolver: AndroidProjectPathResolver,
      buildNameMap: Map<String, BuildId>
    ): IdeVariantCoreImpl

    fun androidProjectFrom(
      basicProject: com.android.builder.model.v2.models.BasicAndroidProject,
      project: com.android.builder.model.v2.models.AndroidProject,
      androidVersion: Versions,
      androidDsl: AndroidDsl
    ): IdeAndroidProjectImpl
  }

  fun nativeModuleFrom(nativeModule: NativeModule): IdeNativeModuleImpl
  fun nativeVariantAbiFrom(variantAbi: NativeVariantAbi): IdeNativeVariantAbiImpl
  fun nativeAndroidProjectFrom(project: NativeAndroidProject, ndkVersion: String?): IdeNativeAndroidProjectImpl

  companion object {
    const val LOCAL_AARS = "__local_aars__"

    @JvmStatic
    fun create(useV2BuilderModels: Boolean, buildFolderPaths: BuildFolderPaths): ModelCache {
      val internedModels = InternedModels(buildFolderPaths.buildRootDirectory)
      if (useV2BuilderModels) {
        return modelCacheV2Impl(internedModels)
      }
      return modelCacheV1Impl(internedModels, buildFolderPaths)
    }

    @JvmStatic
    fun create(useV2BuilderModels: Boolean): ModelCache {
      val internedModels = InternedModels(null)
      return if (useV2BuilderModels) {
        modelCacheV2Impl(internedModels)
      }
      else {
        modelCacheV1Impl(internedModels, BuildFolderPaths())
      }
    }

    @JvmStatic
    fun create(): ModelCache.V1 {
      val internedModels = InternedModels(null)
      return modelCacheV1Impl(internedModels, BuildFolderPaths())
    }
  }
}

data class ModuleId(val gradlePath: String, val buildId: String)


@VisibleForTesting
  /** For older AGP versions pick a variant name based on a heuristic  */
fun getDefaultVariant(variantNames: Collection<String>): String? {
  // Corner case of variant filter accidentally removing all variants.
  if (variantNames.isEmpty()) {
    return null
  }

  // Favor the debug variant
  if (variantNames.contains("debug")) {
    return "debug"
  }
  // Otherwise the first alphabetically that has debug as a build type.
  val sortedNames = ImmutableSortedSet.copyOf(variantNames)
  for (variantName in sortedNames) {
    if (variantName.endsWith("Debug")) {
      return variantName
    }
  }
  // Otherwise fall back to the first alphabetically
  return sortedNames.first()
}

internal fun convertArtifactName(name: String): IdeArtifactName = when (name) {
  AndroidProject.ARTIFACT_MAIN -> IdeArtifactName.MAIN
  AndroidProject.ARTIFACT_ANDROID_TEST -> IdeArtifactName.ANDROID_TEST
  AndroidProject.ARTIFACT_UNIT_TEST -> IdeArtifactName.UNIT_TEST
  AndroidProject.ARTIFACT_TEST_FIXTURES -> IdeArtifactName.TEST_FIXTURES
  else -> error("Invalid android artifact name: $name")
}

/**
 * Converts the artifact address into a name that will be used by the IDE to represent the library.
 */
internal fun convertToLibraryName(libraryArtifactAddress: String, projectBasePath: File?): String {
  if (libraryArtifactAddress.startsWith("${ModelCache.LOCAL_AARS}:")) {
    return adjustLocalLibraryName(
      File(libraryArtifactAddress.removePrefix("${ModelCache.LOCAL_AARS}:").substringBefore(":")),
      projectBasePath
    )
  }

  return convertMavenCoordinateStringToIdeLibraryName(libraryArtifactAddress)
}

/**
 * Converts the name of a maven form dependency from the format that is returned from the Android Gradle plugin [Library]
 * to the name that will be used to setup the library in the IDE. The Android Gradle plugin uses maven co-ordinates to
 * represent the library.
 *
 * In order to share the libraries between Android and non-Android modules we want to convert the artifact
 * co-ordinate string that will match the ones that would be set up in the IDE for non-android modules.
 *
 * Current this method removes any @jar from the end of the coordinate since IDEA defaults to this and doesn't display
 * it.
 */
private fun convertMavenCoordinateStringToIdeLibraryName(mavenCoordinate: String): String {
  return mavenCoordinate.removeSuffix("@jar")
}

/**
 * Attempts to shorten the library name by making paths relative and makes paths system independent.
 * Name shortening is required because the maximum allowed file name length is 256 characters and .jar files located in deep
 * directories in CI environments may exceed this limit.
 */
private fun adjustLocalLibraryName(artifactFile: File, projectBasePath: File?): @SystemIndependent String {
  val maybeRelative = if (projectBasePath != null) artifactFile.relativeToOrSelf(projectBasePath) else artifactFile
  if (!FileUtil.filesEqual(maybeRelative, artifactFile)) {
    return FileUtil.toSystemIndependentName(File(".${File.separator}${maybeRelative}").path)
  }

  return FileUtil.toSystemIndependentName(artifactFile.path)
}
