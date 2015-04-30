/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.BaseArtifact;
import com.android.sdklib.IAndroidTarget;
import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.util.PathsList;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

/**
 * Implementation of {@link JUnitPatcher} that removes android.jar from the class path. It's only applicable to
 * JUnit run configurations if the selected test artifact is "unit tests". In this case, the mockable android.jar is already in the
 * dependencies (taken from the model).
 */
public class AndroidJunitPatcher extends JUnitPatcher {
  @Override
  public void patchJavaParameters(@Nullable Module module, @NotNull JavaParameters javaParameters) {
    if (module == null) {
      return;
    }

    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet == null) {
      return;
    }

    IdeaAndroidProject ideaAndroidProject = androidFacet.getIdeaAndroidProject();
    if (ideaAndroidProject == null) {
      return;
    }

    // Modify the class path only if we're dealing with the unit test artifact.
    if (!ideaAndroidProject.getSelectedTestArtifactName().equals(AndroidProject.ARTIFACT_UNIT_TEST)) {
      return;
    }

    PathsList classPath = javaParameters.getClassPath();

    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    if (platform == null) {
      return;
    }

    handlePlatformJar(classPath, platform);
    handleJavaResources(ideaAndroidProject, classPath);
  }

  /**
   * Removes real android.jar from the classpath and puts the mockable one at the end.
   */
  private static void handlePlatformJar(@NotNull PathsList classPath,
                                        @NotNull AndroidPlatform platform) {
    classPath.remove(platform.getTarget().getPath(IAndroidTarget.ANDROID_JAR));

    // Move the mockable android jar to the end. This is to make sure "empty" classes from android.jar don't end up shadowing real
    // classes needed by the testing code (e.g. XML/JSON related).
    String mockableJarPath = null;
    for (String path : classPath.getPathList()) {
      if (new File(toSystemDependentName(path)).getName().startsWith("mockable-")) {
        // PathsList stores strings - use the one that's actually stored there.
        mockableJarPath = path;
        break;
      }
    }

    if (mockableJarPath != null) {
      classPath.remove(mockableJarPath);
      classPath.addTail(mockableJarPath);
    }
  }

  /**
   * Puts folders with merged java resources for the given variant on the classpath.
   */
  private static void handleJavaResources(@NotNull IdeaAndroidProject ideaAndroidProject,
                                          @NotNull PathsList classPath) {
    BaseArtifact testArtifact = ideaAndroidProject.findSelectedTestArtifactInSelectedVariant();
    if (testArtifact == null) {
      return;
    }

    try {
      classPath.add(ideaAndroidProject.getSelectedVariant().getMainArtifact().getJavaResourcesFolder());
      classPath.add(testArtifact.getJavaResourcesFolder());
    }
    catch (UnsupportedMethodException e) {
      // Java resources were not in older versions of the gradle plugin.
    }
  }
}
