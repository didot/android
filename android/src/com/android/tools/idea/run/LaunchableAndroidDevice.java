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

import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ThreeState;
import icons.StudioIcons;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LaunchableAndroidDevice implements AndroidDevice {
  private static final Map<Abi, List<Abi>> ABI_MAPPINGS = ImmutableMap.of(
    Abi.X86_64, ImmutableList.of(Abi.X86_64, Abi.X86),
    Abi.ARM64_V8A, ImmutableList.of(Abi.ARM64_V8A, Abi.ARMEABI_V7A, Abi.ARMEABI));
  @NotNull private final AvdInfo myAvdInfo;

  private final Object myLock = new Object();

  @GuardedBy("myLock")
  private ListenableFuture<IDevice> myLaunchedEmulator;

  public LaunchableAndroidDevice(@NotNull AvdInfo avdInfo) {
    myAvdInfo = avdInfo;
  }

  @Override
  public boolean isRunning() {
    return false;
  }

  @Override
  public boolean isVirtual() {
    return true;
  }

  @NotNull
  @Override
  public AndroidVersion getVersion() {
    return myAvdInfo.getAndroidVersion();
  }

  @Override
  public int getDensity() {
    String s = myAvdInfo.getProperties().get(HardwareProperties.HW_LCD_DENSITY);
    if (s == null) {
      return -1;
    }

    try {
      return Integer.parseInt(s);
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  @NotNull
  @Override
  public List<Abi> getAbis() {
    Abi abi = Abi.getEnum(myAvdInfo.getAbiType());
    if (abi == null) {
      return Collections.emptyList();
    }
    List<Abi> abis = ABI_MAPPINGS.get(abi);
    if (abis != null) {
      return abis;
    }
    return Collections.singletonList(abi);
  }

  @NotNull
  @Override
  public String getSerial() {
    return myAvdInfo.getName();
  }

  @Override
  public boolean supportsFeature(@NotNull IDevice.HardwareFeature feature) {
    switch (feature) {
      case WATCH:
        return SystemImage.WEAR_TAG.equals(myAvdInfo.getTag());
      case TV:
        return SystemImage.TV_TAG.equals(myAvdInfo.getTag());
      case AUTOMOTIVE:
        return SystemImage.AUTOMOTIVE_TAG.equals(myAvdInfo.getTag());
      default:
        return true;
    }
  }

  @NotNull
  @Override
  public String getName() {
    return AvdManagerConnection.getAvdDisplayName(myAvdInfo);
  }

  @Override
  public boolean renderLabel(@NotNull SimpleColoredComponent component, boolean isCompatible, @Nullable String searchPrefix) {
    component.setIcon(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE);
    SimpleTextAttributes attr = isCompatible ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
    SearchUtil.appendFragments(searchPrefix, getName(), attr.getStyle(), attr.getFgColor(), attr.getBgColor(), component);
    return true;
  }

  @Override
  public void prepareToRenderLabel() {
  }

  @Override
  @NotNull
  public ListenableFuture<IDevice> launch(@NotNull Project project) {
    return launch(project, null);
  }

  @Override
  @NotNull
  public ListenableFuture<IDevice> launch(@NotNull Project project, @Nullable String snapshot) {
    synchronized (myLock) {
      if (myLaunchedEmulator == null) {
        myLaunchedEmulator = AvdManagerConnection.getDefaultAvdManagerConnection().startAvd(project, myAvdInfo, snapshot);
      }
      return myLaunchedEmulator;
    }
  }

  @NotNull
  @Override
  public ListenableFuture<IDevice> getLaunchedDevice() {
    synchronized (myLock) {
      if (myLaunchedEmulator == null) {
        throw new IllegalStateException("Attempt to get device corresponding to an emulator that hasn't been launched yet.");
      }

      return myLaunchedEmulator;
    }
  }

  @Override
  @NotNull
  public LaunchCompatibility canRun(@NotNull AndroidVersion minSdkVersion,
                                    @NotNull IAndroidTarget projectTarget,
                                    @NotNull EnumSet<IDevice.HardwareFeature> requiredFeatures,
                                    @Nullable Set<String> supportedAbis) {
    LaunchCompatibility compatibility = LaunchCompatibility.YES;

    if (myAvdInfo.getStatus() != AvdInfo.AvdStatus.OK) {
      if (AvdManagerConnection.isSystemImageDownloadProblem(myAvdInfo.getStatus())) {
        // The original error message includes the name of the AVD which is already shown in the UI.
        // Make the error message simpler here:
        compatibility = new LaunchCompatibility(ThreeState.UNSURE, "Missing system image");
      }
      else {
        compatibility = new LaunchCompatibility(ThreeState.NO, myAvdInfo.getErrorMessage());
      }
    }
    return compatibility.combine(LaunchCompatibility.canRunOnDevice(minSdkVersion, projectTarget, requiredFeatures, supportedAbis, this));
  }

  @NotNull
  public AvdInfo getAvdInfo() {
    return myAvdInfo;
  }

  @Override
  public boolean isDebuggable() {
    return !myAvdInfo.hasPlayStore();
  }
}
