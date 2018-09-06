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
@file:JvmName("TestRunUtil")
package com.android.tools.idea.stats

import com.android.ide.common.gradle.model.IdeAndroidArtifact
import com.android.ide.common.gradle.model.IdeBaseArtifact
import com.android.ide.common.repository.GradleCoordinate
import com.google.common.collect.Iterables
import com.google.wireless.android.sdk.stats.TestLibraries

/**
 * Constructs the [TestLibraries] protocol buffer based on dependencies in the given [IdeAndroidArtifact].
 */
fun findTestLibrariesVersions(artifact: IdeBaseArtifact): TestLibraries? {
  val deps = artifact.level2Dependencies
  val builder = TestLibraries.newBuilder()

  for (lib in (Iterables.concat(deps.androidLibraries, deps.javaLibraries))) {
    val coordinate = GradleCoordinate.parseCoordinateString(lib.artifactAddress) ?: continue
    val version = coordinate.version?.toString() ?: continue

    when (coordinate.groupId) {
      "com.android.support.test", "androidx.test" -> {
        when (coordinate.artifactId) {
          "orchestrator" -> builder.testOrchestratorVersion = version
          "rules" -> builder.testRulesVersion = version
          "runner" -> builder.testSupportLibraryVersion = version
        }
      }
      "com.android.support.test.espresso", "androidx.test.espresso" -> {
        when (coordinate.artifactId) {
          "espresso-accessibility" -> builder.espressoAccessibilityVersion = version
          "espresso-contrib" -> builder.espressoContribVersion = version
          "espresso-core" -> builder.espressoVersion = version
          "espresso-idling-resource" -> builder.espressoIdlingResourceVersion = version
          "espresso-intents" -> builder.espressoIntentsVersion = version
          "espresso-web" -> builder.espressoWebVersion = version
        }
      }
      "org.robolectric" -> {
        when (coordinate.artifactId) { "robolectric" -> builder.robolectricVersion = version }
      }
      "org.mockito" -> {
        when (coordinate.artifactId) { "mockito-core" -> builder.mockitoVersion = version }
      }
    }
  }

  return builder.build()
}
