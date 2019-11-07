// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.projectsystem

import com.android.ide.common.gradle.model.toSubmodule
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.PROJECT_MODEL_MULTIFLAVOR
import com.google.common.truth.Truth.assertThat

class GradleModelConverterMergeTest : AndroidGradleTestCase() {
  fun testResolvedConfiguration() {
    loadProject(PROJECT_MODEL_MULTIFLAVOR)
    val input = model.androidProject
    val output = input.toSubmodule()

    for (entry in output.artifacts) {
      val artifact = entry.value
      val constituentConfigs = output.configTable.configsIntersecting(entry.key)
      var manualResolvedConfig = constituentConfigs.reduce { a, b -> a.mergeWith(b) }
      assertThat(artifact.resolved.sources).isEqualTo(manualResolvedConfig.sources)
      assertThat(artifact.resolved.manifestValues.versionName).isEqualTo(manualResolvedConfig.manifestValues.versionName)
    }
  }
}