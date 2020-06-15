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
package com.android.tools.idea.run.deployment;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.ConnectedAndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ConnectedDevicesTask implements AsyncSupplier<List<ConnectedDevice>> {
  @NotNull
  private final AndroidDebugBridge myAndroidDebugBridge;

  private final boolean mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

  @Nullable
  private final LaunchCompatibilityChecker myChecker;

  @NotNull
  private final Executor myExecutor;

  @NotNull
  private final Function<IDevice, AndroidDevice> myAndroidDeviceFactory;

  ConnectedDevicesTask(@NotNull AndroidDebugBridge androidDebugBridge,
                       boolean selectDeviceSnapshotComboBoxSnapshotsEnabled,
                       @Nullable LaunchCompatibilityChecker checker) {
    this(androidDebugBridge,
         selectDeviceSnapshotComboBoxSnapshotsEnabled,
         checker,
         AppExecutorUtil.getAppExecutorService(),
         device -> new ConnectedAndroidDevice(device, null));
  }

  @VisibleForTesting
  ConnectedDevicesTask(@NotNull AndroidDebugBridge androidDebugBridge,
                       boolean selectDeviceSnapshotComboBoxSnapshotsEnabled,
                       @Nullable LaunchCompatibilityChecker checker,
                       @NotNull Executor executor,
                       @NotNull Function<IDevice, AndroidDevice> androidDeviceFactory) {
    myAndroidDebugBridge = androidDebugBridge;
    mySelectDeviceSnapshotComboBoxSnapshotsEnabled = selectDeviceSnapshotComboBoxSnapshotsEnabled;
    myChecker = checker;
    myExecutor = executor;
    myAndroidDeviceFactory = androidDeviceFactory;
  }

  @NotNull
  @Override
  public ListenableFuture<List<ConnectedDevice>> get() {
    ListenableFuture<Collection<IDevice>> devices = myAndroidDebugBridge.getConnectedDevices();

    // noinspection UnstableApiUsage
    return Futures.transformAsync(devices, this::newConnectedDevices, myExecutor);
  }

  @NotNull
  private ListenableFuture<List<ConnectedDevice>> newConnectedDevices(@NotNull Collection<IDevice> devices) {
    Iterable<ListenableFuture<ConnectedDevice>> futures = devices.stream()
      .filter(IDevice::isOnline)
      .map(this::newConnectedDevice)
      .collect(Collectors.toList());

    // noinspection UnstableApiUsage
    return Futures.successfulAsList(futures);
  }

  @NotNull
  private ListenableFuture<ConnectedDevice> newConnectedDevice(@NotNull IDevice ddmlibDevice) {
    if (mySelectDeviceSnapshotComboBoxSnapshotsEnabled) {
      ListenableFuture<String> idFuture = myAndroidDebugBridge.getVirtualDeviceId(ddmlibDevice);

      // noinspection ConstantConditions, UnstableApiUsage
      return Futures.transform(idFuture, id -> newConnectedDevice(ddmlibDevice, id), myExecutor);
    }

    // noinspection UnstableApiUsage
    return Futures.immediateFuture(newConnectedDevice(ddmlibDevice, ""));
  }

  @NotNull
  private ConnectedDevice newConnectedDevice(@NotNull IDevice ddmlibDevice, @NotNull String id) {
    AndroidDevice androidDevice = myAndroidDeviceFactory.apply(ddmlibDevice);

    ConnectedDevice.Builder builder = new ConnectedDevice.Builder()
      .setName(ddmlibDevice.isEmulator() ? "Virtual Device" : "Physical Device")
      .setKey(newKey(ddmlibDevice, id))
      .setAndroidDevice(androidDevice);

    if (myChecker == null) {
      return builder.build();
    }

    LaunchCompatibility compatibility = myChecker.validate(androidDevice);

    return builder
      .setValid(!compatibility.isCompatible().equals(ThreeState.NO))
      .setValidityReason(compatibility.getReason())
      .build();
  }

  @NotNull
  private Key newKey(@NotNull IDevice connectedDevice, @NotNull String id) {
    if (!connectedDevice.isEmulator()) {
      return new Key(connectedDevice.getSerialNumber());
    }

    String virtualDeviceName = connectedDevice.getAvdName();

    if (virtualDeviceName == null || virtualDeviceName.equals("<build>")) {
      // Either the virtual device name is null or the developer built their own system image. Neither names will work as virtual device
      // keys. Fall back to the serial number.
      return new Key(connectedDevice.getSerialNumber());
    }

    return new Key(mySelectDeviceSnapshotComboBoxSnapshotsEnabled ? id : virtualDeviceName);
  }
}
