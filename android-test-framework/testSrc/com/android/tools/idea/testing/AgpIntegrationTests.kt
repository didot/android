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
package com.android.tools.idea.testing

import com.android.testutils.junit4.OldAgpSuite
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT

/**
 * An AGP Version definition to be used in AGP integration tests.
 */
enum class AgpVersionSoftwareEnvironmentDescriptor(
  /**
   * The version of the AG. `null` means the current `-dev` version.
   */
  val agpVersion: String?,

  /**
   * The version of Gradle to be used in integration tests for this AGP version. `null` means the latest/default version.
   */
  val gradleVersion: String?,

  /**
   * The version of the Gradle Kotlin plugin to be used in integration tests for this AGP version. `null` means the default version used by
   * Android Studio.
   */
  val kotlinVersion: String? = null
) {
  AGP_35("3.5.0", gradleVersion = "5.5", kotlinVersion = "1.4.32"),
  AGP_40("4.0.0", gradleVersion = "6.5"),

  // TODO(b/194469137): Use correct Gradle version.
  AGP_41("4.1.0", gradleVersion = "6.5"),
  AGP_42("4.2.0", gradleVersion = "6.7.1"),
  AGP_70("7.0.0", gradleVersion = "7.0.2"),
  AGP_71("7.1.0", gradleVersion = "7.2"),
  AGP_72("7.2.0", gradleVersion = "7.3.3"),
  AGP_CURRENT(null, gradleVersion = null); // Must be last to represent the newest version.

  override fun toString(): String {
    return "Agp($agpVersion, g=$gradleVersion, k=$kotlinVersion)"
  }
}

interface AgpIntegrationTestDefinition {
  val name: String
  val agpVersion: AgpVersionSoftwareEnvironmentDescriptor
  fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): AgpIntegrationTestDefinition
  fun displayName(): String = "$name${if (agpVersion != AGP_CURRENT) "-${agpVersion}" else ""}"
  fun isCompatible(): Boolean = true
}

/**
 * Applies AGP versions selected for testing in the current test target to the list of test definitions.
 */
fun List<AgpIntegrationTestDefinition>.applySelectedAgpVersions(): List<AgpIntegrationTestDefinition> =
  AgpVersionSoftwareEnvironmentDescriptor.values()
    .filter {
      val pass = (OldAgpSuite.AGP_VERSION == null || (it.agpVersion ?: "LATEST") == OldAgpSuite.AGP_VERSION) &&
                 (OldAgpSuite.GRADLE_VERSION == null || (it.gradleVersion ?: "LATEST") == OldAgpSuite.GRADLE_VERSION)
      println("${it.name}($it) : $pass")
      pass
    }
    .flatMap { version -> map { it.withAgpVersion(version) } }
    .filter { it.isCompatible() }
    .sortedWith(compareBy({ it.agpVersion.gradleVersion }, { it.agpVersion.agpVersion }))

/**
 * Prints a message describing the currently running test to the standard output.
 */
fun GradleIntegrationTest.outputCurrentlyRunningTest(testDefinition: AgpIntegrationTestDefinition) {
  println("Testing: ${this.javaClass.simpleName}.${this.getName()}[${testDefinition.displayName()}]")
}
