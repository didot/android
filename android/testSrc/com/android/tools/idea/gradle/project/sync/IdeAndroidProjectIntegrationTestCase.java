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
package com.android.tools.idea.gradle.project.sync;

import static com.google.common.truth.Truth.assertThat;

import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.level2.IdeDependencies;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

public abstract class IdeAndroidProjectIntegrationTestCase extends AndroidGradleTestCase {
  protected void verifyIdeLevel2DependenciesPopulated() {
    IdeAndroidProject androidProject = getAndroidProjectInApp();
    assertNotNull(androidProject);

    // Verify IdeLevel2Dependencies are populated for each variant.
    androidProject.forEachVariant(variant -> {
      IdeDependencies level2Dependencies = variant.getMainArtifact().getLevel2Dependencies();
      assertThat(level2Dependencies).isNotNull();
      assertThat(level2Dependencies.getAndroidLibraries()).isNotEmpty();
      assertThat(level2Dependencies.getJavaLibraries()).isNotEmpty();
    });
  }

  @Nullable
  protected IdeAndroidProject getAndroidProjectInApp() {
    Module appModule = myModules.getAppModule();
    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
    return androidModel != null ? androidModel.getAndroidProject() : null;
  }

  protected void verifyAarModuleShowsAsAndroidLibrary(String expectedLibraryName) {
    IdeAndroidProject androidProject = getAndroidProjectInApp();
    assertNotNull(androidProject);

    // Aar module should show up as android library dependency, not module dependency for app module.
    androidProject.forEachVariant(variant -> {
      IdeDependencies level2Dependencies = variant.getMainArtifact().getLevel2Dependencies();
      assertThat(level2Dependencies).isNotNull();
      assertThat(level2Dependencies.getModuleDependencies()).isEmpty();
      List<String> androidLibraries = ContainerUtil.map(level2Dependencies.getAndroidLibraries(), Library::getArtifactAddress);
      assertThat(level2Dependencies.getAndroidLibraries()).isNotEmpty();
      assertThat(androidLibraries).contains(expectedLibraryName);
    });
  }
}
