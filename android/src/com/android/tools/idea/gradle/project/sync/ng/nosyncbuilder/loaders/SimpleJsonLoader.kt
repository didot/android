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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.loaders

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.AndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.Variant
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.LegacyAndroidProjectStub
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.library.LegacyGlobalLibraryMap
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.*
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject.NewAndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.gradleproject.NewGradleProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewGlobalLibraryMap
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.variant.NewVariant
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.AndroidProjectProto
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.GradleProjectProto
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.LibraryProto
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.repackage.com.google.protobuf.util.JsonFormat
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

open class SimpleJsonLoader(
  protected val path: Path,
  protected val converter: PathConverter
): Loader {
  override fun loadAndroidProject(variant: String): OldAndroidProject {
    fun loadNewAndroidProjectFromJSON(fullPath: Path, rootDir: File = File("/")): AndroidProject {
      val androidProjectJSON = String(Files.readAllBytes(fullPath))

      val proto = AndroidProjectProto.AndroidProject.newBuilder().apply {
        JsonFormat.parser().ignoringUnknownFields().merge(androidProjectJSON, this)
      }.build()

      return NewAndroidProject(proto, rootDir, converter)
    }

    fun loadNewVariantFromJSON(fullPath: Path): Variant {
      val variantJSON = String(Files.readAllBytes(fullPath))

      val proto = VariantProto.Variant.newBuilder().apply {
        JsonFormat.parser().ignoringUnknownFields().merge(variantJSON, this)
      }.build()

      return NewVariant(proto, converter)
    }

    val fullAndroidProjectPath = path.resolve(ANDROID_PROJECT_CACHE_PATH)
    val newAndroidProject = loadNewAndroidProjectFromJSON(fullAndroidProjectPath)

    val fullVariantPath = path.resolve(VARIANTS_CACHE_DIR_PATH).resolve("$variant.json")
    val newVariant = loadNewVariantFromJSON(fullVariantPath)

    return LegacyAndroidProjectStub(newAndroidProject, newVariant)
  }

  override fun loadGlobalLibraryMap(): LegacyGlobalLibraryMap {
    val fullPath = path.resolve(GLOBAL_LIBRARY_MAP_CACHE_PATH)

    val globalLibraryMapJSON = String(Files.readAllBytes(fullPath))

    val proto = LibraryProto.GlobalLibraryMap.newBuilder().apply {
      JsonFormat.parser().ignoringUnknownFields().merge(globalLibraryMapJSON, this)
    }.build()

    val newGlobalLibraryMap = NewGlobalLibraryMap(proto, converter)

    return LegacyGlobalLibraryMap(newGlobalLibraryMap)
  }

  override fun loadGradleProject(): NewGradleProject {
    val fullPath = path.resolve(GRADLE_PROJECT_CACHE_PATH)

    val gradleProjectJSON = String(Files.readAllBytes(fullPath))

    val proto = GradleProjectProto.GradleProject.newBuilder().apply {
      JsonFormat.parser().ignoringUnknownFields().merge(gradleProjectJSON, this)
    }.build()

    return NewGradleProject(proto, converter)
  }
}