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
package com.android.ide.gradle.model.builder

import com.android.ide.gradle.model.LegacyV1AgpVersionModel
import com.android.ide.gradle.model.builder.LegacyApplicationIdModelBuilder.Companion.invokeMethod
import com.android.ide.gradle.model.impl.LegacyV1AgpVersionModelImpl
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder

/**
 * An injected Gradle tooling model builder to fetch the AGP version of Android Projects that will be requested using V1 models
 */
class LegacyV1AgpVersionModelBuilder : ToolingModelBuilder {

  override fun canBuild(modelName: String): Boolean {
    return modelName == LegacyV1AgpVersionModel::class.java.name
  }


  override fun buildAll(modelName: String, project: Project): LegacyV1AgpVersionModel {
    check (modelName == LegacyV1AgpVersionModel::class.java.name) { "Only valid model is ${LegacyV1AgpVersionModel::class.java.name}" }

    val extension = project.extensions.findByName("android") ?: return LegacyV1AgpVersionModelImpl("")
    return try {
      val pluginClazz = Class.forName("com.android.build.api.extension.impl.CurrentAndroidGradlePluginVersionKt", true, extension!!.javaClass.classLoader)
      val agpVersion = pluginClazz.getDeclaredMethod("getCURRENT_AGP_VERSION").invoke(null)
      val major = agpVersion.invokeMethod<Int>("getMajor")
      val minor = agpVersion.invokeMethod<Int>("getMinor")
      val micro = agpVersion.invokeMethod<Int>("getMicro")
      val preview = agpVersion.invokeMethod<Int>("getPreview")
      val previewType = agpVersion.invokeMethod<String?>("getPreviewType")
      LegacyV1AgpVersionModelImpl(getAgpVersionStringValue(major, minor, micro, preview, previewType))
    } catch (e: Exception) {
      // We know this is an AndroidProject, but we just couldn't get the agp version through LegacyV1AgpVersionModel. This means
      // the android project is using an AGP version lower than 7.0.0-alpha15.
      val versionClazz = extension.javaClass.classLoader.loadClass("com.android.Version")
      LegacyV1AgpVersionModelImpl(versionClazz.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION").get(null) as String)
    }
  }

  private fun getAgpVersionStringValue(major: Int, minor: Int, micro: Int, preview: Int, previewType: String?): String {
    return "$major.$minor.$micro" +(if (previewType != null) "-$previewType" else "") + (if (preview > 0) preview else "")
  }
  }
