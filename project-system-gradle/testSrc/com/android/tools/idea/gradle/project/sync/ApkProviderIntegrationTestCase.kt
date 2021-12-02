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
package com.android.tools.idea.gradle.project.sync

import com.android.ddmlib.IDevice
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult
import com.android.tools.idea.gradle.project.sync.Target.ManuallyAssembled
import com.android.tools.idea.gradle.project.sync.Target.NamedAppTargetRunConfiguration
import com.android.tools.idea.gradle.project.sync.Target.TestTargetRunConfiguration
import com.android.tools.idea.projectsystem.gradle.getBuiltApksForSelectedVariant
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_35
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_40
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_41
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.gradleModule
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet

internal val APK_PROVIDER_TESTS: List<ProviderTestDefinition> =
  listOf(
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.COMPOSITE_BUILD,
        target = NamedAppTargetRunConfiguration(externalSystemModuleId = ":app"),
      ),
      expectApks =
      """
              ApplicationId: com.test.compositeapp
              File: project/app/build/outputs/apk/debug/app-debug.apk
              Files:
                project.app -> project/app/build/outputs/apk/debug/app-debug.apk
              RequiredInstallationOptions: []
          """.let {
        listOf(
          AGP_35 to it,
          AGP_40 to it,
          AGP_41 to it,
          AGP_CURRENT to """
                    ApplicationId: com.test.compositeapp
                    File: project/app/build/intermediates/apk/debug/app-debug.apk
                    Files:
                      project.app -> project/app/build/intermediates/apk/debug/app-debug.apk
                    RequiredInstallationOptions: []
                """,
        )
      }.toMap(),
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.COMPOSITE_BUILD,
        target = NamedAppTargetRunConfiguration(externalSystemModuleId = "TestCompositeLib1:app"),
      ),
      expectApks =
      """
              ApplicationId: com.test.composite1
              File: project/TestCompositeLib1/app/build/outputs/apk/debug/app-debug.apk
              Files:
                TestCompositeLib1.app -> project/TestCompositeLib1/app/build/outputs/apk/debug/app-debug.apk
              RequiredInstallationOptions: []
            """.let {
        listOf(
          AGP_35 to it,
          AGP_40 to it,
          AGP_41 to it,
          AGP_CURRENT to """
              ApplicationId: com.test.composite1
              File: project/TestCompositeLib1/app/build/intermediates/apk/debug/app-debug.apk
              Files:
                TestCompositeLib1.app -> project/TestCompositeLib1/app/build/intermediates/apk/debug/app-debug.apk
              RequiredInstallationOptions: []
            """,
        )
      }.toMap(),
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
        executeMakeBeforeRun = false,
      ),
      expectApks = mapOf(
        AGP_CURRENT to """
              ApkProvisionException*> Error loading build artifacts from: <ROOT>/project/app/build/intermediates/apk_ide_redirect_file/debug/redirect.txt
            """,
        AGP_41 to """
              ApkProvisionException*> Error loading build artifacts from: <ROOT>/project/app/build/outputs/apk/debug/output-metadata.json
            """,
        AGP_40 to """
              ApkProvisionException*> Couldn't get post build model. Module: Application_ID_Suffix_Test_App.app Variant: debug
            """,
        AGP_35 to """
              ApkProvisionException*> Couldn't get post build model. Module: Application_ID_Suffix_Test_App.app Variant: debug
            """
      )
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
      ),
      expectApks =
      """
            ApplicationId: one.name.defaultConfig.debug
            File: project/app/build/outputs/apk/debug/app-debug.apk
            Files:
              Application_ID_Suffix_Test_App.app -> project/app/build/outputs/apk/debug/app-debug.apk
            RequiredInstallationOptions: []
          """.let {
        listOf(
          AGP_35 to it,
          AGP_40 to it,
          AGP_41 to it,
          AGP_CURRENT to """
            ApplicationId: one.name.defaultConfig.debug
            File: project/app/build/intermediates/apk/debug/app-debug.apk
            Files:
              Application_ID_Suffix_Test_App.app -> project/app/build/intermediates/apk/debug/app-debug.apk
            RequiredInstallationOptions: []
          """,
        )
      }.toMap()
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        viaBundle = true,
        testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
      ),
      expectApks = mapOf(
        AGP_CURRENT to """
              ApplicationId: one.name.defaultConfig.debug
              File: *>java.lang.IllegalArgumentException
              Files:
                base -> project/app/build/intermediates/extracted_apks/debug/base-master.apk
                base -> project/app/build/intermediates/extracted_apks/debug/base-mdpi.apk
              RequiredInstallationOptions: []
            """,
        AGP_40 to """
              ApplicationId: one.name.defaultConfig.debug
              File: *>java.lang.IllegalArgumentException
              Files:
                base -> project/app/build/intermediates/extracted_apks/debug/out/base-master.apk
                base -> project/app/build/intermediates/extracted_apks/debug/out/base-mdpi.apk
              RequiredInstallationOptions: []
            """,
        AGP_35 to """
              ApplicationId: one.name.defaultConfig.debug
              File: *>java.lang.IllegalArgumentException
              Files:
                base -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/base-master.apk
                base -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/base-mdpi.apk
              RequiredInstallationOptions: []
            """
      )
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
        target = ManuallyAssembled(":app", forTests = false),
      ),
      expectApks = """
            ApplicationId: one.name.defaultConfig.debug
            File: project/app/build/outputs/apk/debug/app-debug.apk
            Files:
              Application_ID_Suffix_Test_App.app -> project/app/build/outputs/apk/debug/app-debug.apk
            RequiredInstallationOptions: []
          """
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.SIMPLE_APPLICATION,
        target = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
      ),
      expectApks =
      """
            ApplicationId: google.simpleapplication
            File: project/app/build/outputs/apk/debug/app-debug.apk
            Files:
              project.app -> project/app/build/outputs/apk/debug/app-debug.apk
            RequiredInstallationOptions: []

            ApplicationId: google.simpleapplication.test
            File: project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            Files:
               -> project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            RequiredInstallationOptions: []
          """.let {
        listOf(
          AGP_35 to it,
          AGP_40 to it,
          AGP_41 to it,
          AGP_CURRENT to """
            ApplicationId: google.simpleapplication
            File: project/app/build/intermediates/apk/debug/app-debug.apk
            Files:
              project.app -> project/app/build/intermediates/apk/debug/app-debug.apk
            RequiredInstallationOptions: []

            ApplicationId: google.simpleapplication.test
            File: project/app/build/intermediates/apk/androidTest/debug/app-debug-androidTest.apk
            Files:
               -> project/app/build/intermediates/apk/androidTest/debug/app-debug-androidTest.apk
            RequiredInstallationOptions: []
          """,
        )
      }.toMap()
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.SIMPLE_APPLICATION,
        target = ManuallyAssembled(":app", forTests = true),
      ),
      expectApks = """
            ApplicationId: google.simpleapplication
            File: project/app/build/outputs/apk/debug/app-debug.apk
            Files:
              project.app -> project/app/build/outputs/apk/debug/app-debug.apk
            RequiredInstallationOptions: []

            ApplicationId: google.simpleapplication.test
            File: project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            Files:
               -> project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            RequiredInstallationOptions: []
          """
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.TEST_ONLY_MODULE,
        target = TestTargetRunConfiguration("com.example.android.app.ExampleTest"),
      ),
      expectApks =
      """
            ApplicationId: com.example.android.app
            File: project/app/build/outputs/apk/debug/app-debug.apk
            Files:
               -> project/app/build/outputs/apk/debug/app-debug.apk
            RequiredInstallationOptions: []

            ApplicationId: com.example.android.app.testmodule
            File: project/test/build/outputs/apk/debug/test-debug.apk
            Files:
              project.test -> project/test/build/outputs/apk/debug/test-debug.apk
            RequiredInstallationOptions: []
          """.let {
        listOf(
          AGP_35 to it,
          AGP_40 to it,
          AGP_41 to it,
          AGP_CURRENT to
            """
            ApplicationId: com.example.android.app
            File: project/app/build/intermediates/apk/debug/app-debug.apk
            Files:
               -> project/app/build/intermediates/apk/debug/app-debug.apk
            RequiredInstallationOptions: []

            ApplicationId: com.example.android.app.testmodule
            File: project/test/build/intermediates/apk/debug/test-debug.apk
            Files:
              project.test -> project/test/build/intermediates/apk/debug/test-debug.apk
            RequiredInstallationOptions: []
          """,
        )
      }.toMap(),
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.DYNAMIC_APP,
      ),
      expectApks =
      """
            ApplicationId: google.simpleapplication
            File: *>java.lang.IllegalArgumentException
            Files:
              simpleApplication.app -> project/app/build/outputs/apk/debug/app-debug.apk
              simpleApplication.dependsOnFeature1 -> project/dependsOnFeature1/build/outputs/apk/debug/dependsOnFeature1-debug.apk
              simpleApplication.feature1 -> project/feature1/build/outputs/apk/debug/feature1-debug.apk
            RequiredInstallationOptions: []
          """.let {
        listOf(
          AGP_35 to it,
          AGP_40 to it,
          AGP_41 to it,
          AGP_CURRENT to """
            ApplicationId: google.simpleapplication
            File: *>java.lang.IllegalArgumentException
            Files:
              simpleApplication.app -> project/app/build/intermediates/apk/debug/app-debug.apk
              simpleApplication.dependsOnFeature1 -> project/dependsOnFeature1/build/intermediates/apk/debug/dependsOnFeature1-debug.apk
              simpleApplication.feature1 -> project/feature1/build/intermediates/apk/debug/feature1-debug.apk
            RequiredInstallationOptions: []
          """
        )
      }.toMap(),
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        device = 19,
        testProject = TestProjectPaths.DYNAMIC_APP,
      ),
      IGNORE = { TODO("b/189190337") },
      expectApks = mapOf(
        AGP_CURRENT to """
            ApplicationId: google.simpleapplication
            File: project/app/build/intermediates/extracted_apks/debug/standalone-mdpi.apk
            Files:
              standalone -> project/app/build/intermediates/extracted_apks/debug/standalone-mdpi.apk
            RequiredInstallationOptions: []
          """,
        AGP_35 to """
            ApplicationId: google.simpleapplication
            File: project/app/build/outputs/extracted_apks/debug/standalone-mdpi.apk
            Files:
              standalone -> project/app/build/outputs/extracted_apks/debug/standalone-mdpi.apk
            RequiredInstallationOptions: []
          """,
      )

    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.DYNAMIC_APP,
        target = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
      ),
      expectApks =
      """
                ApplicationId: google.simpleapplication
                File: *>java.lang.IllegalArgumentException
                Files:
                  simpleApplication.app -> project/app/build/outputs/apk/debug/app-debug.apk
                  simpleApplication.dependsOnFeature1 -> project/dependsOnFeature1/build/outputs/apk/debug/dependsOnFeature1-debug.apk
                  simpleApplication.feature1 -> project/feature1/build/outputs/apk/debug/feature1-debug.apk
                RequiredInstallationOptions: []

                ApplicationId: google.simpleapplication.test
                File: project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
                Files:
                   -> project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
                RequiredInstallationOptions: []
            """.let {
        listOf(
          AGP_35 to it,
          AGP_40 to it,
          AGP_41 to it,
          AGP_CURRENT to """
              ApplicationId: google.simpleapplication
              File: *>java.lang.IllegalArgumentException
              Files:
                simpleApplication.app -> project/app/build/intermediates/apk/debug/app-debug.apk
                simpleApplication.dependsOnFeature1 -> project/dependsOnFeature1/build/intermediates/apk/debug/dependsOnFeature1-debug.apk
                simpleApplication.feature1 -> project/feature1/build/intermediates/apk/debug/feature1-debug.apk
              RequiredInstallationOptions: []

              ApplicationId: google.simpleapplication.test
              File: project/app/build/intermediates/apk/androidTest/debug/app-debug-androidTest.apk
              Files:
                 -> project/app/build/intermediates/apk/androidTest/debug/app-debug-androidTest.apk
              RequiredInstallationOptions: []
            """,
        )
      }.toMap()
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        device = 19,
        testProject = TestProjectPaths.DYNAMIC_APP,
        target = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
      ),
      IGNORE = { TODO("b/189190337") },
      expectApks = mapOf(
        AGP_CURRENT to """
            ApplicationId: google.simpleapplication
            File: project/app/build/intermediates/extracted_apks/debug/standalone-mdpi.apk
            Files:
              standalone -> project/app/build/intermediates/extracted_apks/debug/standalone-mdpi.apk
            RequiredInstallationOptions: []

            ApplicationId: google.simpleapplication.test
            File: project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            Files:
               -> project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            RequiredInstallationOptions: []
          """,
        AGP_35 to """
            ApplicationId: google.simpleapplication
            File: project/app/build/outputs/extracted_apks/debug/standalone-mdpi.apk
            Files:
              standalone -> project/app/build/outputs/extracted_apks/debug/standalone-mdpi.apk
            RequiredInstallationOptions: []

            ApplicationId: google.simpleapplication.test
            File: project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            Files:
               -> project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            RequiredInstallationOptions: []
          """,
      )
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.DYNAMIC_APP,
        target = TestTargetRunConfiguration("com.example.instantapp.ExampleInstrumentedTest"),
      ),
      // Do not run with the current version of the AGP.
      IGNORE = { if (agpVersion == AGP_CURRENT) TODO("b/189202602") },
      expectApks = mapOf(
        AGP_CURRENT to """
              ApplicationId: google.simpleapplication
              File: *>java.lang.IllegalArgumentException
              Files:
                base -> project/app/build/intermediates/extracted_apks/debug/base-master.apk
                base -> project/app/build/intermediates/extracted_apks/debug/base-mdpi.apk
                dependsOnFeature1 -> project/app/build/intermediates/extracted_apks/debug/dependsOnFeature1-master.apk
                dependsOnFeature1 -> project/app/build/intermediates/extracted_apks/debug/dependsOnFeature1-mdpi.apk
                feature1 -> project/app/build/intermediates/extracted_apks/debug/feature1-master.apk
                feature1 -> project/app/build/intermediates/extracted_apks/debug/feature1-mdpi.apk
              RequiredInstallationOptions: []

              ApplicationId: com.example.feature1.test
              File: project/feature1/build/outputs/apk/androidTest/debug/feature1-debug-androidTest.apk
              Files:
                 -> project/feature1/build/outputs/apk/androidTest/debug/feature1-debug-androidTest.apk
              RequiredInstallationOptions: []
            """,
        AGP_40 to """
              ApplicationId: google.simpleapplication
              File: *>java.lang.IllegalArgumentException
              Files:
                base -> project/app/build/intermediates/extracted_apks/debug/out/base-master.apk
                base -> project/app/build/intermediates/extracted_apks/debug/out/base-mdpi.apk
                dependsOnFeature1 -> project/app/build/intermediates/extracted_apks/debug/out/dependsOnFeature1-master.apk
                dependsOnFeature1 -> project/app/build/intermediates/extracted_apks/debug/out/dependsOnFeature1-mdpi.apk
                feature1 -> project/app/build/intermediates/extracted_apks/debug/out/feature1-master.apk
                feature1 -> project/app/build/intermediates/extracted_apks/debug/out/feature1-mdpi.apk
              RequiredInstallationOptions: []

              ApplicationId: com.example.feature1.test
              File: project/feature1/build/outputs/apk/androidTest/debug/feature1-debug-androidTest.apk
              Files:
                 -> project/feature1/build/outputs/apk/androidTest/debug/feature1-debug-androidTest.apk
              RequiredInstallationOptions: []
            """,
        AGP_35 to """
              ApplicationId: google.simpleapplication
              File: *>java.lang.IllegalArgumentException
              Files:
                base -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/base-master.apk
                base -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/base-mdpi.apk
                dependsOnFeature1 -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/dependsOnFeature1-master.apk
                dependsOnFeature1 -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/dependsOnFeature1-mdpi.apk
                feature1 -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/feature1-master.apk
                feature1 -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/feature1-mdpi.apk
              RequiredInstallationOptions: []

              ApplicationId: com.example.feature1.test
              File: project/feature1/build/outputs/apk/androidTest/debug/feature1-debug-androidTest.apk
              Files:
                 -> project/feature1/build/outputs/apk/androidTest/debug/feature1-debug-androidTest.apk
              RequiredInstallationOptions: []
            """
      )
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.BUDDY_APKS,
        target = TestTargetRunConfiguration("google.testapplication.ApplicationTest"),
      ),
      expectApks =
      """
            ApplicationId: google.testapplication
            File: project/app/build/outputs/apk/debug/app-debug.apk
            Files:
              project.app -> project/app/build/outputs/apk/debug/app-debug.apk
            RequiredInstallationOptions: []

            ApplicationId: google.testapplication.test
            File: project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            Files:
               -> project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            RequiredInstallationOptions: []

            ApplicationId: com.linkedin.android.testbutler
            File: <M2>/com/linkedin/testbutler/test-butler-app/1.3.1/test-butler-app-1.3.1.apk
            Files:
               -> <M2>/com/linkedin/testbutler/test-butler-app/1.3.1/test-butler-app-1.3.1.apk
            RequiredInstallationOptions: [FORCE_QUERYABLE, GRANT_ALL_PERMISSIONS]
          """.let {
        listOf(
          AGP_35 to it,
          AGP_40 to it,
          AGP_41 to it,
          AGP_CURRENT to """
            ApplicationId: google.testapplication
            File: project/app/build/intermediates/apk/debug/app-debug.apk
            Files:
              project.app -> project/app/build/intermediates/apk/debug/app-debug.apk
            RequiredInstallationOptions: []

            ApplicationId: google.testapplication.test
            File: project/app/build/intermediates/apk/androidTest/debug/app-debug-androidTest.apk
            Files:
               -> project/app/build/intermediates/apk/androidTest/debug/app-debug-androidTest.apk
            RequiredInstallationOptions: []

            ApplicationId: com.linkedin.android.testbutler
            File: <M2>/com/linkedin/testbutler/test-butler-app/1.3.1/test-butler-app-1.3.1.apk
            Files:
               -> <M2>/com/linkedin/testbutler/test-butler-app/1.3.1/test-butler-app-1.3.1.apk
            RequiredInstallationOptions: [FORCE_QUERYABLE, GRANT_ALL_PERMISSIONS]
          """
        )
      }.toMap()
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.SIMPLE_APPLICATION,
        variant = ":app" to "release",
      ),
      expectValidate = mapOf(
        AGP_CURRENT to "The apk for your currently selected variant cannot be signed. " +
          "Please specify a signing configuration for this variant (release).",
        AGP_40 to "The apk for your currently selected variant cannot be signed. " +
          "Please specify a signing configuration for this variant (release).",
        AGP_35 to "The apk for your currently selected variant cannot be signed. " +
          "Please specify a signing configuration for this variant (release)."
      ),
      expectApks =
      """
              ApplicationId: google.simpleapplication
              File: project/app/build/outputs/apk/release/app-release-unsigned.apk
              Files:
                project.app -> project/app/build/outputs/apk/release/app-release-unsigned.apk
              RequiredInstallationOptions: []
            """.let {
        listOf(
          AGP_35 to it,
          AGP_40 to it,
          AGP_41 to it,
          AGP_CURRENT to """
              ApplicationId: google.simpleapplication
              File: project/app/build/intermediates/apk/release/app-release-unsigned.apk
              Files:
                project.app -> project/app/build/intermediates/apk/release/app-release-unsigned.apk
              RequiredInstallationOptions: []
            """,
        )
      }.toMap()
    ),
  )

private fun def(
  stackMarker: (() -> Unit) -> Unit, // Is supposed to be implemented as { it() }.
  scenario: TestScenario,
  IGNORE: TestConfiguration.() -> Unit = { },
  expectApks: String,
  expectValidate: String? = null,
) = ApkProviderTest(
  scenario = scenario,
  IGNORE = IGNORE,
  expectApks = mapOf(AGP_CURRENT to expectApks),
  expectValidate = expectValidate?.let { mapOf(AGP_CURRENT to expectValidate) } ?: emptyMap(),
  stackMarker = stackMarker
)

private fun def(
  stackMarker: (() -> Unit) -> Unit, // Is supposed to be implemented as { it() }.
  scenario: TestScenario,
  IGNORE: TestConfiguration.() -> Unit = { },
  expectApks: Map<AgpVersionSoftwareEnvironmentDescriptor, String>,
  expectValidate: Map<AgpVersionSoftwareEnvironmentDescriptor, String> = emptyMap(),
) = ApkProviderTest(
  scenario = scenario,
  IGNORE = IGNORE,
  expectApks = expectApks,
  expectValidate = expectValidate,
  stackMarker = stackMarker
)

private data class ApkProviderTest(
  override val scenario: TestScenario,
  override val IGNORE: TestConfiguration.() -> Unit,
  override val stackMarker: (() -> Unit) -> Unit, // Is supposed to be implemented as { it() }.
  val expectApks: Map<AgpVersionSoftwareEnvironmentDescriptor, String>,
  val expectValidate: Map<AgpVersionSoftwareEnvironmentDescriptor, String>,
) : ProviderTestDefinition {

  override fun verifyExpectations(
    expect: Expect,
    valueNormalizers: ValueNormalizers,
    project: Project,
    runConfiguration: AndroidRunConfigurationBase?,
    assembleResult: AssembleInvocationResult?,
    device: IDevice
  ) {
    fun AssembleInvocationResult.getApkProvider(
      gradlePath: String,
      forTests: Boolean
    ): ApkProvider {
      val module = project.gradleModule(gradlePath)!!
      val androidFacet = AndroidFacet.getInstance(module)!!
      return object : ApkProvider {
        override fun validate(): List<ValidationError> = emptyList()
        override fun getApks(ignored: IDevice): Collection<ApkInfo> =
          getBuiltApksForSelectedVariant(androidFacet, forTests).orEmpty()
      }
    }

    val apkProvider = when (scenario.target) {
      is ManuallyAssembled -> assembleResult!!.getApkProvider(scenario.target.gradlePath, scenario.target.forTests)
      else -> runConfiguration!!.apkProvider!!
    }

    with(valueNormalizers) {

      fun ApkInfo.toTestString(): String {
        val filesString = files
          .sortedBy { it.apkFile.toTestString() }
          .joinToString("\n        ") { "${it.moduleName} -> ${it.apkFile.toTestString()}" }
        return """
      ApplicationId: ${this.applicationId}
      File: ${runCatching { file.toTestString() }.let { it.getOrNull() ?: "*>${it.exceptionOrNull()}" }}
      Files:
        $filesString
      RequiredInstallationOptions: ${this.requiredInstallOptions}""".trimIndent()
      }

      fun Collection<ApkInfo>.toTestString() = joinToString("\n\n") { it.toTestString() }

      expect.that(apkProvider.validate().joinToString { it.message }).isEqualTo(expectValidate.forVersion())

      val apks = runCatching { apkProvider.getApks(device) }
      expect.that(apks.toTestString { this.toTestString() }).isEqualTo(expectApks.forVersion())
      apks.getOrNull()?.flatMap { it.files }?.forEach { expect.that(it.apkFile.exists()).named("${it.apkFile} exists").isTrue() }
    }
  }
}
