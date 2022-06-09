/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.adb.AdbService;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeviceManagerAndroidDebugBridge {
  @UiThread
  public @NotNull ListenableFuture<@NotNull List<@NotNull IDevice>> getDevices(@Nullable Project project) {
    ListeningExecutorService service = MoreExecutors.listeningDecorator(AppExecutorUtil.getAppExecutorService());

    // noinspection UnstableApiUsage
    return FluentFuture.from(service.submit(() -> AndroidSdkUtils.findAdb(project).adbPath))
      .transformAsync(AdbService.getInstance()::getDebugBridge, service)
      .transform(DeviceManagerAndroidDebugBridge::getDevices, service);
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private static @NotNull List<@NotNull IDevice> getDevices(@NotNull AndroidDebugBridge bridge) {
    if (!bridge.isConnected()) {
      throw new IllegalArgumentException();
    }

    List<IDevice> devices = Arrays.asList(bridge.getDevices());

    if (bridge.hasInitialDeviceList()) {
      Logger.getInstance(DeviceManagerAndroidDebugBridge.class).info(devices.toString());
    }
    else {
      Logger.getInstance(DeviceManagerAndroidDebugBridge.class).warn("ADB does not have the initial device list");
    }

    return devices;
  }

  @UiThread
  public void addDeviceChangeListener(@NotNull IDeviceChangeListener listener) {
    AndroidDebugBridge.addDeviceChangeListener(listener);
  }

  @UiThread
  public void removeDeviceChangeListener(@NotNull IDeviceChangeListener listener) {
    AndroidDebugBridge.removeDeviceChangeListener(listener);
  }
}
