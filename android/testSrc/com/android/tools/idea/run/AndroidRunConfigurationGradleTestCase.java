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
package com.android.tools.idea.run;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.RecommendedPluginVersionUpgradeStep;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;

public abstract class AndroidRunConfigurationGradleTestCase extends AndroidGradleTestCase {
  protected AndroidRunConfiguration myRunConfiguration;

  @Override
  public void setUp() throws Exception {
    // Flag has to be overridden as early as possible, since the run configuration type is initialized
    // during test setup (see org.jetbrains.android.AndroidPlugin).
    StudioFlags.RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED.override(true);

    super.setUp();

    ConfigurationFactory configurationFactory = AndroidRunConfigurationType.getInstance().getFactory();
    myRunConfiguration = new AndroidRunConfiguration(getProject(), configurationFactory);

    // We override the default extension point to prevent the "Gradle Update" UI to show during the test
    PlatformTestUtil.maskExtensions(RecommendedPluginVersionUpgradeStep.EXTENSION_POINT_NAME, Collections
      .singletonList(new AndroidRunConfigurationGradleTestCase.MyPluginVersionUpgradeStep()), getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  private static class MyPluginVersionUpgradeStep extends RecommendedPluginVersionUpgradeStep {

    @Override
    public boolean checkUpgradable(@NotNull Project project, @NotNull AndroidPluginInfo pluginInfo) {
      // Returning {@code false} means "project is all good, no update needed".
      return false;
    }

    @Override
    public boolean performUpgradeAndSync(@NotNull Project project, @NotNull AndroidPluginInfo pluginInfo) {
      // Returning {@code false} means "project is all good, no update needed or performed".
      return false;
    }
  }
}
