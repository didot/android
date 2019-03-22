/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.databinding;

import static com.android.testutils.TestUtils.getWorkspaceFile;

import com.android.testutils.JarTestSuiteRunner;
import com.android.tools.tests.GradleDaemonsRule;
import com.android.tools.tests.IdeaTestSuiteBase;
import com.intellij.idea.IdeaTestApplication;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses(AndroidDataBindingTestSuite.class)  // a suite mustn't contain itself
public final class AndroidDataBindingTestSuite extends IdeaTestSuiteBase {
  @ClassRule public static GradleDaemonsRule gradle = new GradleDaemonsRule();

  static {
    symlinkToIdeaHome(
      "prebuilts/studio/jdk",
      "prebuilts/studio/sdk",
      "tools/adt/idea/android/annotations",
      "tools/adt/idea/databinding/testData",
      "tools/base/templates",
      "tools/idea/build.txt",
      "tools/idea/java"); // For the mock JDK.

    setUpOfflineRepo("tools/adt/idea/android/test_deps.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/base/build-system/studio_repo.zip", "out/studio/repo");
    setUpOfflineRepo("tools/data-binding/data_binding_runtime.zip", "prebuilts/tools/common/m2/repository");

    // Enable Kotlin plugin (see PluginManagerCore.PROPERTY_PLUGIN_PATH).
    System.setProperty("plugin.path", getWorkspaceFile("prebuilts/tools/common/kotlin-plugin/Kotlin").getAbsolutePath());

    // Run Kotlin in-process for easier control over its JVM args.
    System.setProperty("kotlin.compiler.execution.strategy", "in-process");

    // As a side-effect, the following line initializes an initial application. This is important
    // as this test suite has at least one test that creates and then disposes a temporary mock
    // application. However, the ApplicationManager API doesn't fallback to an older application if
    // one was never set, which leaves other tests that call ApplicationManager.getApplication()
    // unexpectedly accessing a disposed application - leading to exceptions if the tests happen to
    // be called in a bad order.
    IdeaTestApplication.getInstance();
  }
}
