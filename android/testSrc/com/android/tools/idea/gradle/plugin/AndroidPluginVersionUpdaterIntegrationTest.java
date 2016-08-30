/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.plugin;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater.UpdateResult;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.dsl.model.GradleBuildModel.parseBuildFile;
import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.CLASSPATH;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link AndroidPluginVersionUpdater}.
 */
public class AndroidPluginVersionUpdaterIntegrationTest extends AndroidGradleTestCase {
  private AndroidPluginVersionUpdater myVersionUpdater;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myVersionUpdater = new AndroidPluginVersionUpdater(getProject(), mock(GradleSyncState.class));
  }

  public void testUpdatePluginVersion() throws Throwable {
    loadProject("projects/sync/multiproject", false);
    setAndroidPluginVersion("1.0.0");

    UpdateResult result = myVersionUpdater.updatePluginVersion(GradleVersion.parse("20.0.0"), null);
    assertTrue(result.isPluginVersionUpdated());
    assertFalse(result.isGradleVersionUpdated());

    verifyAndroidPluginVersion("20.0.0");
  }

  public void testUpdateExperimentalPluginVersion() throws Throwable {
    loadProject("projects/experimentalPlugin", false);
    setAndroidPluginVersion("1.0.0");

    UpdateResult result = myVersionUpdater.updatePluginVersion(GradleVersion.parse("20.0.0"), null);
    assertTrue(result.isPluginVersionUpdated());
    assertFalse(result.isGradleVersionUpdated());

    verifyAndroidPluginVersion("20.0.0");
  }

  public void testUpdatePluginVersionWhenPluginHasAlreadyUpdatedVersion() throws Throwable {
    loadProject("projects/sync/multiproject", false);

    setAndroidPluginVersion("20.0.0");

    UpdateResult result = myVersionUpdater.updatePluginVersion(GradleVersion.parse("20.0.0"), null);
    assertFalse(result.isPluginVersionUpdated());
    assertFalse(result.isGradleVersionUpdated());

    verifyAndroidPluginVersion("20.0.0");
  }

  private void setAndroidPluginVersion(@NotNull String version) {
    GradleBuildModel buildModel = getTopLevelBuildModel(getProject());
    ArtifactDependencyModel androidPluginDependency = findAndroidPlugin(buildModel);
    androidPluginDependency.setVersion(version);

    runWriteCommandAction(getProject(), buildModel::applyChanges);
  }

  private void verifyAndroidPluginVersion(@NotNull String expectedVersion) {
    GradleBuildModel buildModel = getTopLevelBuildModel(getProject());
    assertEquals(expectedVersion, findAndroidPlugin(buildModel).version().value());
  }

  @NotNull
  private static GradleBuildModel getTopLevelBuildModel(@NotNull Project project) {
    VirtualFile buildFile = project.getBaseDir().findChild(FN_BUILD_GRADLE);
    assertNotNull(buildFile);
    return parseBuildFile(buildFile, project);
  }

  @NotNull
  private static ArtifactDependencyModel findAndroidPlugin(@NotNull GradleBuildModel buildModel) {
    List<ArtifactDependencyModel> dependencies = buildModel.buildscript().dependencies().artifacts(CLASSPATH);
    for (ArtifactDependencyModel dependency : dependencies) {
      if (AndroidPluginGeneration.find(dependency.name().value(), dependency.group().value()) != null) {
        return dependency;
      }
    }
    throw new AssertionFailedError("Failed to find Android plugin dependency");
  }
}