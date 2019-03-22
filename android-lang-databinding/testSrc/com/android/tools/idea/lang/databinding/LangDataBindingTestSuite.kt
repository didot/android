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
package com.android.tools.idea.lang.databinding

import com.android.testutils.JarTestSuiteRunner
import com.android.testutils.TestUtils.getWorkspaceFile
import com.android.tools.tests.IdeaTestSuiteBase
import org.junit.runner.RunWith

@RunWith(JarTestSuiteRunner::class)
@JarTestSuiteRunner.ExcludeClasses(com.android.tools.idea.lang.databinding.LangDataBindingTestSuite::class)
class LangDataBindingTestSuite : IdeaTestSuiteBase() {
  companion object {
    init {
      symlinkToIdeaHome(
        "prebuilts/studio/jdk",
        "prebuilts/studio/sdk",
        "tools/adt/idea/android/annotations",
        "tools/adt/idea/android-lang-databinding/testData",
        "tools/base/templates",
        "tools/idea/build.txt",
        "tools/idea/java") // For the mock JDK.

      IdeaTestSuiteBase.setUpOfflineRepo("tools/adt/idea/android/test_deps.zip", "prebuilts/tools/common/m2/repository")
      IdeaTestSuiteBase.setUpOfflineRepo("tools/base/build-system/studio_repo.zip", "out/studio/repo")
      IdeaTestSuiteBase.setUpOfflineRepo("tools/data-binding/data_binding_runtime.zip", "prebuilts/tools/common/m2/repository")

      // Enable Kotlin plugin (see PluginManagerCore.PROPERTY_PLUGIN_PATH).
      System.setProperty("plugin.path", getWorkspaceFile("prebuilts/tools/common/kotlin-plugin/Kotlin").absolutePath)

      // Run Kotlin in-process for easier control over its JVM args.
      System.setProperty("kotlin.compiler.execution.strategy", "in-process")
    }
  }
}