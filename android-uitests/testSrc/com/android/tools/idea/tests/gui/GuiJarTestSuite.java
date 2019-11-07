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
package com.android.tools.idea.tests.gui;

import static com.android.testutils.TestUtils.getWorkspaceRoot;

import com.android.testutils.ClassSuiteRunner;
import com.android.tools.tests.GradleDaemonsRule;
import com.android.tools.tests.IdeaTestSuiteBase;
import com.android.tools.tests.XDisplayRule;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

@RunWith(ClassSuiteRunner.class)
public class GuiJarTestSuite extends IdeaTestSuiteBase {

  @ClassRule public static GradleDaemonsRule gradle = new GradleDaemonsRule();

  @ClassRule public static XDisplayRule display = new XDisplayRule();

  static {
    optSymlinkToIdeaHome(
      "prebuilts/tools/common/offline-m2",
      "prebuilts/studio/jdk",
      "prebuilts/studio/layoutlib",
      "prebuilts/studio/sdk",
      "tools/adt/idea/adt-ui/lib/libwebp",
      "tools/adt/idea/android/annotations",
      "tools/adt/idea/android/lib",
      "tools/adt/idea/android-uitests/testData",
      "tools/adt/idea/artwork/resources/device-art-resources",
      "tools/adt/idea/resources-aar/framework_res.jar",
      "tools/base/templates",
      "tools/external/gradle",
      "tools/idea/java",
      "tools/idea/bin");

    setUpOfflineRepo("tools/base/build-system/studio_repo.zip", "out/studio/repo");
    setUpOfflineRepo("tools/adt/idea/android/test_deps.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/base/third_party/kotlin/kotlin-m2repository.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/adt/idea/android/android-gradle-1.5.0_repo.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/data-binding/data_binding_runtime.zip", "prebuilts/tools/common/m2/repository");

    List<File> additionalPlugins = getExternalPlugins();
    if (!additionalPlugins.isEmpty()) {
      String pluginPaths = additionalPlugins.stream().map(f -> f.getAbsolutePath()).collect(Collectors.joining(","));
      Logger.getInstance(GuiJarTestSuite.class).info("Setting additional plugin paths: " + pluginPaths);

      String existingPluginPaths = System.getProperty("plugin.path");
      if (!StringUtil.isEmpty(existingPluginPaths)) {
        pluginPaths = existingPluginPaths + "," + pluginPaths;
      }

      System.setProperty("plugin.path", pluginPaths);
    }

    // Make sure we run with UI
    System.setProperty("java.awt.headless", "false");
  }

  private static List<File> getExternalPlugins() {
    List<File> plugins = new ArrayList<>(1);

    // Enable Bazel plugin if it's available
    File aswb = new File(getWorkspaceRoot(), "tools/adt/idea/android-uitests/aswb");
    if (aswb.exists()) {
      plugins.add(aswb);
    }

    return plugins;
  }
}
