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

package com.android.model.builder

import com.android.model.sources.builder.SourcesAndJavadocModelBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.util.VersionNumber
import javax.inject.Inject

class AndroidStudioToolingPlugin @Inject
internal constructor(private val registry: ToolingModelBuilderRegistry) : Plugin<Project> {

  override fun apply(project: Project) {
    registry.register(GradlePluginModelBuilder())
    // SourcesAndJavadocModelBuilder extends ParameterizedToolingModelBuilder, which is available since Gradle 4.4.
    if (isGradleAtLeast(project.gradle.gradleVersion, "4.4")) {
      registry.register(SourcesAndJavadocModelBuilder())
    }
  }
}

internal fun isGradleAtLeast(gradleVersion: String, expectedVersion: String): Boolean {
  val currentVersion = VersionNumber.parse(gradleVersion)
  val givenVersion = VersionNumber.parse(expectedVersion)
  return currentVersion >= givenVersion
}
