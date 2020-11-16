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
package com.android.build.attribution.analyzers

import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.data.GarbageCollectionData
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import com.intellij.util.lang.JavaVersion

class GarbageCollectionAnalyzer(override val warningsFilter: BuildAttributionWarningsFilter): BuildAttributionReportAnalyzer {
  var garbageCollectionData: List<GarbageCollectionData> = emptyList()
  var javaVersion: Int? = null
  var isSettingSet: Boolean? = null

  override fun onBuildStart() {
    garbageCollectionData = emptyList()
  }

  override fun receiveBuildAttributionReport(androidGradlePluginAttributionData: AndroidGradlePluginAttributionData) {
    garbageCollectionData = androidGradlePluginAttributionData.garbageCollectionData.map { GarbageCollectionData(it.key, it.value) }
    javaVersion = JavaVersion.tryParse(androidGradlePluginAttributionData.javaInfo.version)?.feature
    isSettingSet = androidGradlePluginAttributionData.javaInfo.vmArguments.any { it.isGcVmArgument() }
  }

  private fun String.isGcVmArgument(): Boolean = startsWith("-XX:+Use") && endsWith("GC")
}
