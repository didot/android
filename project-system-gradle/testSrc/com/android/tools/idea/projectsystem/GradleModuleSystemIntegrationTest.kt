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
package com.android.tools.idea.projectsystem

import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat


/**
 * Integration tests for [GradleDependencyManager]; contains tests that require a working gradle project.
 */
class GradleModuleSystemIntegrationTest : AndroidGradleTestCase() {

  @Throws(Exception::class)
  fun testGetDeclaredMatchingDependencies() {
    loadSimpleApplication()
    val moduleSystem = myModules.appModule.getModuleSystem()
    val dependencyManager = GradleDependencyManager.getInstance(project)
    val dummyDependency = GradleCoordinate("a", "b", "4.5.6")

    // Setup: Ensure the above dummy dependency is present in the build.gradle file.
    assertThat(dependencyManager.addDependenciesWithoutSync(myModules.appModule, listOf(dummyDependency))).isTrue()
    assertThat(dependencyManager.findMissingDependencies(myModules.appModule, listOf(dummyDependency))).isEmpty()

    assertThat(isSameArtifact(moduleSystem.getDeclaredDependency(GradleCoordinate("a", "b", "4.5.6")), dummyDependency)).isTrue()
    assertThat(isSameArtifact(moduleSystem.getDeclaredDependency(GradleCoordinate("a", "b", "4.5.+")), dummyDependency)).isTrue()
    assertThat(isSameArtifact(moduleSystem.getDeclaredDependency(GradleCoordinate("a", "b", "+")), dummyDependency)).isTrue()
  }

  @Throws(Exception::class)
  fun testGetDeclaredNonMatchingDependencies() {
    loadSimpleApplication()
    val moduleSystem = myModules.appModule.getModuleSystem()
    val dependencyManager = GradleDependencyManager.getInstance(project)
    val dummyDependency = GradleCoordinate("a", "b", "4.5.6")

    // Setup: Ensure the above dummy dependency is present in the build.gradle file.
    assertThat(dependencyManager.addDependenciesWithoutSync(myModules.appModule, listOf(dummyDependency))).isTrue()
    assertThat(dependencyManager.findMissingDependencies(myModules.appModule, listOf(dummyDependency))).isEmpty()

    assertThat(moduleSystem.getDeclaredDependency(GradleCoordinate("a", "b", "4.5.7"))).isNull()
    assertThat(moduleSystem.getDeclaredDependency(GradleCoordinate("a", "b", "4.99.+"))).isNull()
    assertThat(moduleSystem.getDeclaredDependency(GradleCoordinate("a", "BAD", "4.5.6"))).isNull()
  }

  private fun isSameArtifact(first: GradleCoordinate?, second: GradleCoordinate?) =
    GradleCoordinate.COMPARE_PLUS_LOWER.compare(first, second) == 0

}