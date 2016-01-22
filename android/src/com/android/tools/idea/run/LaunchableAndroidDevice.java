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
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.avdmanager.AvdWizardConstants;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class LaunchableAndroidDevice implements AndroidDevice {
  private final AvdInfo myAvdInfo;

  private final Object LOCK = new Object();

  @GuardedBy("LOCK")
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
    IAndroidTarget target = myAvdInfo.getTarget();
    return target == null ? AndroidVersion.DEFAULT : target.getVersion();
  }

  @Override
  public int getDensity() {
    String s = myAvdInfo.getProperties().get(HardwareProperties.HW_LCD_DENSITY);
    if (s == null) {
      return -1;
    }

    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  @NotNull
  @Override
  public List<Abi> getAbis() {
    Abi abi = Abi.getEnum(myAvdInfo.getAbiType());
    return abi == null ? Collections.<Abi>emptyList() : Collections.singletonList(abi);
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
        return AvdWizardConstants.WEAR_TAG.equals(myAvdInfo.getTag());
      case TV:
        return AvdWizardConstants.TV_TAG.equals(myAvdInfo.getTag());
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
  public void renderName(@NotNull SimpleColoredComponent renderer, boolean isCompatible, @Nullable String searchPrefix) {
    renderer.setIcon(AndroidIcons.Ddms.EmulatorDevice);
    SimpleTextAttributes attr = isCompatible ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
    SearchUtil.appendFragments(searchPrefix, getName(), attr.getStyle(), attr.getFgColor(), attr.getBgColor(), renderer);
  }

  @Override
  @NotNull
  public ListenableFuture<IDevice> launch(@NotNull Project project) {
    synchronized (LOCK) {
      if (myLaunchedEmulator == null) {
        myLaunchedEmulator = AvdManagerConnection.getDefaultAvdManagerConnection().startAvd(project, myAvdInfo);
      }
      return myLaunchedEmulator;
    }
  }

  @NotNull
  @Override
  public ListenableFuture<IDevice> getLaunchedDevice() {
    synchronized (LOCK) {
      if (myLaunchedEmulator == null) {
        throw new IllegalStateException("Attempt to get device corresponding to an emulator that hasn't been launched yet.");
      }

      return myLaunchedEmulator;
    }
  }

  public AvdInfo getAvdInfo() {
    return myAvdInfo;
  }
}
