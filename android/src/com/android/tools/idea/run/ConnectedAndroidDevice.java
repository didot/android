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

import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.ddms.DeviceNameRendererEx;
import com.android.tools.idea.ddms.DevicePropertyUtil;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ConnectedAndroidDevice implements AndroidDevice {
  private static final ExtensionPointName<DeviceNameRendererEx> EP_NAME = ExtensionPointName.create("com.android.run.deviceNameRenderer");

  @NotNull private final IDevice myDevice;
  @Nullable private final String myAvdName;
  @Nullable private final DeviceNameRendererEx myDeviceNameRenderer;

  public ConnectedAndroidDevice(@NotNull IDevice device, @Nullable List<AvdInfo> avdInfos) {
    myDevice = device;

    AvdInfo avdInfo = getAvdInfo(device, avdInfos);
    myAvdName = avdInfo == null ? null : AvdManagerConnection.getAvdDisplayName(avdInfo);
    myDeviceNameRenderer = getRendererExtension(device);
  }

  @Nullable
  private static AvdInfo getAvdInfo(@NotNull IDevice device, @Nullable List<AvdInfo> avdInfos) {
    if (avdInfos != null && device.isEmulator()) {
      for (AvdInfo info : avdInfos) {
        if (info.getName().equals(device.getAvdName())) {
          return info;
        }
      }
    }

    return null;
  }

  @Override
  public boolean isRunning() {
    return true;
  }

  @Override
  public boolean isVirtual() {
    return myDevice.isEmulator();
  }

  @NotNull
  @Override
  public AndroidVersion getVersion() {
    return myDevice.getVersion();
  }

  @NotNull
  @Override
  public String getSerial() {
    if (myDevice.isEmulator()) {
      String avdName = myDevice.getAvdName();
      if (avdName != null) {
        return avdName;
      }
    }

    return myDevice.getSerialNumber();
  }

  @Override
  public boolean supportsFeature(@NotNull IDevice.HardwareFeature feature) {
    return myDevice.supportsFeature(feature);
  }

  @NotNull
  @Override
  public String getName() {
    if (myDeviceNameRenderer != null) {
      return myDeviceNameRenderer.getName(myDevice);
    }

    return myAvdName == null ? getDeviceName() : myAvdName;
  }

  @Override
  public void renderName(@NotNull SimpleColoredComponent renderer, boolean isCompatible, @org.jetbrains.annotations.Nullable @Nullable String searchPrefix) {
    if (myDeviceNameRenderer != null) {
      myDeviceNameRenderer.render(myDevice, renderer);
      return;
    }

    renderer.setIcon(myDevice.isEmulator() ? AndroidIcons.Ddms.EmulatorDevice : AndroidIcons.Ddms.RealDevice);

    IDevice.DeviceState state = myDevice.getState();
    if (state != IDevice.DeviceState.ONLINE) {
      String name = String.format("%1$s [%2$s] ", myDevice.getSerialNumber(), myDevice.getState());
      renderer.append(name, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
      return;
    }

    SimpleTextAttributes attr = isCompatible ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
    SearchUtil.appendFragments(searchPrefix, getName(), attr.getStyle(), attr.getFgColor(), attr.getBgColor(), renderer);

    String build = DevicePropertyUtil.getBuild(myDevice);
    if (!build.isEmpty()) {
      renderer.append(" (" + build + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  private String getDeviceName() {
    StringBuilder name = new StringBuilder(20);
    name.append(DevicePropertyUtil.getManufacturer(myDevice, ""));
    if (name.length() > 0) {
      name.append(' ');
    }
    name.append(DevicePropertyUtil.getModel(myDevice, ""));
    return name.toString();
  }

  @NotNull
  @Override
  public ListenableFuture<IDevice> launch(@NotNull Project project) {
    return Futures.immediateFuture(myDevice);
  }

  @NotNull
  public IDevice getDevice() {
    return myDevice;
  }


  @Nullable
  private static DeviceNameRendererEx getRendererExtension(@NotNull IDevice device) {
    Application application = ApplicationManager.getApplication();
    if (application == null || application.isUnitTestMode()) {
      return null;
    }
    for (DeviceNameRendererEx extensionRenderer : EP_NAME.getExtensions()) {
      if (extensionRenderer.isApplicable(device)) {
        return extensionRenderer;
      }
    }
    return null;
  }
}
