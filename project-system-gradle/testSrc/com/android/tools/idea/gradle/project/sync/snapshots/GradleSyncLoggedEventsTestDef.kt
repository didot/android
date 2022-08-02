/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.LoggedUsage
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.TextFormat
import com.intellij.openapi.project.Project
import java.io.File
import java.util.Locale

data class GradleSyncLoggedEventsTestDef(
  val namePrefix: String,
  override val testProject: TestProject,
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
  val verify: (events: List<LoggedUsage>) -> Unit = {}
) : SyncedProjectTest.TestDef {

  override val name: String
    get() = "$namePrefix - ${testProject.projectName}"

  override fun toString(): String = name

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTest.TestDef {
    return copy(agpVersion = agpVersion)
  }

  override fun isCompatible(): Boolean {
    return agpVersion == AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
  }

  private val testUsageTracker = TestUsageTracker(VirtualTimeScheduler())

  override fun setup(root: File) {
    UsageTracker.setWriterForTest(testUsageTracker)
  }

  override fun runTest(root: File, project: Project) = Unit

  override fun verifyAfterClosing(root: File) {
    val usages = testUsageTracker.usages
    UsageTracker.cleanAfterTesting()
    if (System.getenv("SYNC_BASED_TESTS_DEBUG_OUTPUT")?.lowercase(Locale.getDefault()) == "y") {
      val events = usages
        .joinToString("\n") { buildString { TextFormat.printer().print(it.studioEvent, this) } }
      println(events)
    }
    verify(usages)
  }

  companion object {
    val tests = listOf(
      GradleSyncLoggedEventsTestDef(
        namePrefix = "logged_events",
        testProject = TestProject.SIMPLE_APPLICATION
      ) { events ->
        assertThat(events.dumpSyncEvents()).isEqualTo(
          """
            |GRADLE_SYNC_STARTED
            |  USER_REQUESTED_PARALLEL
            |GRADLE_SYNC_SETUP_STARTED
            |  USER_REQUESTED_PARALLEL
            |GRADLE_SYNC_ENDED
            |  USER_REQUESTED_PARALLEL
            |GRADLE_BUILD_DETAILS
          """.trimMargin()
        )
      },
      GradleSyncLoggedEventsTestDef(
        namePrefix = "logged_events",
        testProject = TestProject.SIMPLE_APPLICATION_NO_PARALLEL_SYNC
      ) { events ->
        assertThat(events.dumpSyncEvents()).isEqualTo(
          """
            |GRADLE_SYNC_STARTED
            |  USER_REQUESTED_SEQUENTIAL
            |GRADLE_SYNC_SETUP_STARTED
            |  USER_REQUESTED_SEQUENTIAL
            |GRADLE_SYNC_ENDED
            |  USER_REQUESTED_SEQUENTIAL
            |GRADLE_BUILD_DETAILS
          """.trimMargin()
        )
      },
      GradleSyncLoggedEventsTestDef(
        namePrefix = "module_counts",
        testProject = TestProject.PSD_SAMPLE_GROOVY
      ) { events ->
        assertThat(events.dumpModuleCounts()).isEqualTo(
          """
            |total_module_count: 11
            |app_module_count: 1
            |lib_module_count: 6
          """.trimMargin()
        )
      },
      GradleSyncLoggedEventsTestDef(
        namePrefix = "module_counts",
        testProject = TestProject.COMPOSITE_BUILD
      ) { events ->
        assertThat(events.dumpModuleCounts()).isEqualTo(
          """
            |total_module_count: 12
            |app_module_count: 3
            |lib_module_count: 3
          """.trimMargin()
        )
      },
    )

    private fun List<LoggedUsage>.dumpSyncEvents(): String {
      return map { it.studioEvent }
        .filter { it.hasGradleSyncStats() || it.hasGradleBuildDetails() }
        .joinToString("") {
          buildString {
            appendLine(it.kind.toString())
            if (it.gradleSyncStats.hasUserRequestedSyncType()) {
              appendLine("  ${it.gradleSyncStats.userRequestedSyncType}")
            }
          }
        }
        .trim()
    }

    private fun List<LoggedUsage>.dumpModuleCounts(): String {
      return map { it.studioEvent }
        .filter { it.hasGradleBuildDetails() }
        .flatMap { it.gradleBuildDetails.modulesList }
        .joinToString("\n") { buildString { TextFormat.printer().print(it, this) } }
        .trim()
    }
  }
}
