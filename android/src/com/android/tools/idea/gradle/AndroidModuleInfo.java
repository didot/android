/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.tools.idea.rendering.ManifestInfo;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Android information about a module, such as its application package, its minSdkVersion, and so on. This
 * is derived from a combination of gradle files and manifest files.
 * <p>
 * TODO: Handle the case where gradle files are not specifying attributes, and they are specified
 * in non-default manifest files (e.g. flavor-specific manifest files)
 */
public class AndroidModuleInfo {
  private final @NotNull AndroidFacet myFacet;

  private AndroidModuleInfo(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @NotNull
  public static AndroidModuleInfo create(@NotNull AndroidFacet facet) {
    return new AndroidModuleInfo(facet);
  }

  @NotNull
  public static AndroidModuleInfo get(@NotNull AndroidFacet facet) {
    return facet.getAndroidModuleInfo();
  }

  @Nullable
  public static AndroidModuleInfo get(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? facet.getAndroidModuleInfo() : null;
  }

  @Nullable
  public String getPackage() {
    String manifestPackage = ManifestInfo.get(myFacet.getModule()).getPackage();

    IdeaAndroidProject project = myFacet.getIdeaAndroidProject();
    if (project != null) {
      return IdeaAndroidProject.computePackageName(project, manifestPackage);
    }

    // Read from the manifest: Not overridden in the configuration
    // TODO: there could be more than one manifest file; I need to look at the merged view!
    return manifestPackage;
  }

  public int getMinSdkVersion() {
    IdeaAndroidProject project = myFacet.getIdeaAndroidProject();
    if (project != null) {
      int minSdkVersion = project.getSelectedVariant().getMergedFlavor().getMinSdkVersion();
      if (minSdkVersion >= 1) {
        return minSdkVersion;
      }
      // Else: not specified in gradle files; fall back to manifest
    }

    // TODO: there could be more than one manifest file; I need to look at the merged view!
    return ManifestInfo.get(myFacet.getModule()).getMinSdkVersion();
  }

  public int getTargetSdkVersion() {
    IdeaAndroidProject project = myFacet.getIdeaAndroidProject();
    if (project != null) {
      int targetSdkVersion = project.getSelectedVariant().getMergedFlavor().getTargetSdkVersion();
      if (targetSdkVersion >= 1) {
        return targetSdkVersion;
      }
      // Else: not specified in gradle files; fall back to manifest
    }

    // TODO: there could be more than one manifest file; I need to look at the merged view!
    return ManifestInfo.get(myFacet.getModule()).getTargetSdkVersion();
  }

  public int getBuildSdkVersion() {
    // TODO: Get this from the model! For now, we take advantage of the fact that
    // the model should have synced the right type of Android SDK to the IntelliJ facet.
    AndroidPlatform platform = AndroidPlatform.getPlatform(myFacet.getModule());
    if (platform != null) {
      return platform.getApiLevel();
    }

    return -1;
  }
}
