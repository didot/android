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
package com.android.tools.idea.run;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.sync.setup.post.PluginVersionUpgradeStep;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.AndroidGradleTests.updateGradleVersions;
import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_PRE30;
import static com.google.common.truth.Truth.assertThat;

public class AndroidBundleRunConfigurationTest extends AndroidGradleTestCase {
  private AndroidBundleRunConfiguration myRunConfiguration;

  @Override
  public void setUp() throws Exception {
    // Flag has to be overridden as early as possible, since the run configuration type is initialized
    // during test setup (see org.jetbrains.android.AndroidPlugin).
    StudioFlags.RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED.override(true);

    super.setUp();

    ConfigurationFactory configurationFactory = AndroidBundleRunConfigurationType.getInstance().getFactory();
    myRunConfiguration = new AndroidBundleRunConfiguration(getProject(), configurationFactory);

    // We override the default extension point to prevent the "Gradle Update" UI to show during the test
    PlatformTestUtil.unregisterAllExtensions(PluginVersionUpgradeStep.EXTENSION_POINT_NAME, getTestRootDisposable());
    PlatformTestUtil.registerExtension(PluginVersionUpgradeStep.EXTENSION_POINT_NAME,
                                       new MyPluginVersionUpgradeStep(),
                                       getTestRootDisposable());
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

  public void testNoErrorIfGradlePluginVersionIsUpToDate() throws Exception {
    loadProject(DYNAMIC_APP);
    List<ValidationError> errors = myRunConfiguration.checkConfiguration(myAndroidFacet);
    assertThat(errors).isEmpty();
  }

  public void testErrorIfGradlePluginVersionIsOutdated() throws Exception {
    loadProject(SIMPLE_APPLICATION_PRE30);

    // Update plugin to 2.2.0 so that the bundle tool tasks are not available.
    // Note that downgrading to 2.2.0 always fails gradle sync.
    updateGradleVersions(getBaseDirPath(getProject()), "2.2.0");
    requestSyncAndGetExpectedFailure();

    // Verifies there is a validation error (since bundle tasks are not available)
    List<ValidationError> errors = myRunConfiguration.checkConfiguration(myAndroidFacet);
    assertThat(errors).isNotEmpty();
    ValidationError bundleTaskError = errors
      .stream()
      .filter(e -> "This configuration requires a newer version of the Android Gradle Plugin".equals(e.getMessage()))
      .findFirst()
      .orElse(null);
    assertThat(bundleTaskError).isNotNull();
  }

  private static class MyPluginVersionUpgradeStep extends PluginVersionUpgradeStep {

    @Override
    public boolean checkAndPerformUpgrade(@NotNull Project project,
                                          @NotNull AndroidPluginInfo pluginInfo) {
      // Returning {@code false} means "project is all good, no update needed or performed".
      return false;
    }
  }
}
