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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.run.util.LaunchUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

public class LaunchCompatibilityCheckerImpl implements LaunchCompatibilityChecker {
  private final AndroidVersion myMinSdkVersion;
  private final IAndroidTarget myProjectTarget;
  private final EnumSet<IDevice.HardwareFeature> myRequiredHardwareFeatures;
  private final Set<String> mySupportedAbis;

  public LaunchCompatibilityCheckerImpl(@NotNull AndroidVersion minSdkVersion,
                                        @NotNull IAndroidTarget target,
                                        @NotNull EnumSet<IDevice.HardwareFeature> requiredHardwareFeatures,
                                        @Nullable Set<String> supportedAbis) {
    myMinSdkVersion = minSdkVersion;
    myProjectTarget = target;
    myRequiredHardwareFeatures = requiredHardwareFeatures;
    mySupportedAbis = supportedAbis;
  }

  @NotNull
  @Override
  public LaunchCompatibility validate(@NotNull AndroidDevice device) {
    return device.canRun(myMinSdkVersion, myProjectTarget, myRequiredHardwareFeatures, mySupportedAbis);
  }

  public static LaunchCompatibilityChecker create(@NotNull AndroidFacet facet) {
    AndroidVersion minSdkVersion = AndroidModuleInfo.get(facet).getRuntimeMinSdkVersion();

    AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
    if (platform == null) {
      throw new IllegalStateException("Android platform not set for module: " + facet.getModule().getName());
    }

    // Currently, we only look at whether the device supports the watch feature.
    // We may not want to search the device for every possible feature, but only a small subset of important
    // features, starting with hardware type watch.
    EnumSet<IDevice.HardwareFeature> requiredHardwareFeatures;
    if (LaunchUtils.isWatchFeatureRequired(facet)) {
      requiredHardwareFeatures = EnumSet.of(IDevice.HardwareFeature.WATCH);
    }
    else {
      requiredHardwareFeatures = EnumSet.noneOf(IDevice.HardwareFeature.class);
    }

    Set<String> supportedAbis = facet.getAndroidModel() instanceof AndroidModuleModel ?
                                ((AndroidModuleModel)facet.getAndroidModel()).getSelectedVariant().getMainArtifact().getAbiFilters() :
                                null;

    return new LaunchCompatibilityCheckerImpl(minSdkVersion, platform.getTarget(), requiredHardwareFeatures, supportedAbis);
  }
}
