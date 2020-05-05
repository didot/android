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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.android.tools.idea.run.LaunchCompatibilityCheckerImpl;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.serviceContainer.NonInjectable;
import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AsyncDevicesGetter {
  @NotNull
  private final Project myProject;

  @NotNull
  private final BooleanSupplier mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

  @NotNull
  private final Worker<Collection<VirtualDevice>> myVirtualDevicesWorker;

  @NotNull
  private final Worker<List<ConnectedDevice>> myConnectedDevicesWorker;

  @NotNull
  private final KeyToConnectionTimeMap myMap;

  @NotNull
  private final Function<ConnectedDevice, String> myGetName;

  @Nullable
  private LaunchCompatibilityChecker myChecker;

  @SuppressWarnings("unused")
  private AsyncDevicesGetter(@NotNull Project project) {
    this(project,
         () -> StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED.get(),
         new KeyToConnectionTimeMap(),
         new NameGetter(project));
  }

  @NonInjectable
  @VisibleForTesting
  AsyncDevicesGetter(@NotNull Project project,
                     @NotNull BooleanSupplier selectDeviceSnapshotComboBoxSnapshotsEnabled,
                     @NotNull KeyToConnectionTimeMap map,
                     @NotNull Function<ConnectedDevice, String> getName) {
    myProject = project;
    mySelectDeviceSnapshotComboBoxSnapshotsEnabled = selectDeviceSnapshotComboBoxSnapshotsEnabled;

    myVirtualDevicesWorker = new Worker<>();
    myConnectedDevicesWorker = new Worker<>();

    myMap = map;
    myGetName = getName;
  }

  @NotNull
  static AsyncDevicesGetter getInstance(@NotNull Project project) {
    return project.getService(AsyncDevicesGetter.class);
  }

  /**
   * @return an optional list of devices including the virtual devices ready to be launched, virtual devices that have been launched, and
   * the connected physical devices
   */
  @NotNull
  Optional<List<Device>> get() {
    initChecker(RunManager.getInstance(myProject).getSelectedConfiguration(), AndroidFacet::getInstance);
    File adb = AndroidSdkUtils.getAdb(myProject);

    if (adb == null) {
      Logger.getInstance(AsyncDevicesGetter.class).info("adb not found");
      return Optional.empty();
    }

    boolean snapshotsEnabled = mySelectDeviceSnapshotComboBoxSnapshotsEnabled.getAsBoolean();
    FileSystem fileSystem = FileSystems.getDefault();
    AsyncSupplier<Collection<VirtualDevice>> virtualDevicesTask = new VirtualDevicesTask(snapshotsEnabled, fileSystem, myChecker);

    AndroidDebugBridge bridge = new DdmlibAndroidDebugBridge(adb);
    AsyncSupplier<List<ConnectedDevice>> connectedDevicesTask = new ConnectedDevicesTask(bridge, snapshotsEnabled, myChecker);

    Optional<Collection<VirtualDevice>> virtualDevices = myVirtualDevicesWorker.perform(virtualDevicesTask);
    Optional<List<ConnectedDevice>> connectedDevices = myConnectedDevicesWorker.perform(connectedDevicesTask);

    if (!virtualDevices.isPresent() || !connectedDevices.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(getImpl(virtualDevices.get(), connectedDevices.get()));
  }

  /**
   * The meat of {@link #get()} separated out with injected device collections for testing
   *
   * <p>Virtual devices that have not been launched will have a single entry in virtualDevices. Virtual devices that have been launched will
   * have an entry in each list; this method will combine them into a single device and return it. Connected physical devices will have a
   * single entry in connectedDevices.
   *
   * @param virtualDevices   the virtual devices that have and have not been launched
   * @param connectedDevices the virtual devices that have been launched and the connected physical devices
   */
  @NotNull
  @VisibleForTesting
  List<Device> getImpl(@NotNull Collection<VirtualDevice> virtualDevices, @NotNull Collection<ConnectedDevice> connectedDevices) {
    Stream<Device> deviceStream = Streams.concat(
      connectedVirtualDeviceStream(connectedDevices, virtualDevices),
      physicalDeviceStream(connectedDevices),
      disconnectedVirtualDeviceStream(virtualDevices, connectedDevices));

    List<Device> devices = deviceStream.collect(Collectors.toList());

    Collection<Key> keys = devices.stream()
      .filter(Device::isConnected)
      .map(Device::getKey)
      .collect(Collectors.toList());

    myMap.retainAll(keys);
    return devices;
  }

  @NotNull
  private Stream<VirtualDevice> connectedVirtualDeviceStream(@NotNull Collection<ConnectedDevice> connectedDevices,
                                                             @NotNull Collection<VirtualDevice> virtualDevices) {
    Map<Key, VirtualDevice> keyToVirtualDeviceMap = virtualDevices.stream().collect(Collectors.toMap(Device::getKey, device -> device));

    return connectedDevices.stream()
      .filter(ConnectedDevice::isVirtualDevice)
      .map(device -> VirtualDevice.newConnectedDevice(device, myMap, keyToVirtualDeviceMap.get(device.getKey())));
  }

  @NotNull
  private Stream<PhysicalDevice> physicalDeviceStream(@NotNull Collection<ConnectedDevice> connectedDevices) {
    return connectedDevices.stream()
      .filter(ConnectedDevice::isPhysicalDevice)
      .map(device -> PhysicalDevice.newDevice(device, myGetName, myMap));
  }

  @NotNull
  private static Stream<VirtualDevice> disconnectedVirtualDeviceStream(@NotNull Collection<VirtualDevice> virtualDevices,
                                                                       @NotNull Collection<ConnectedDevice> connectedDevices) {
    Collection<Key> connectedVirtualDeviceKeys = connectedDevices.stream()
      .filter(ConnectedDevice::isVirtualDevice)
      .map(ConnectedDevice::getKey)
      .collect(Collectors.toSet());

    return virtualDevices.stream().filter(device -> !connectedVirtualDeviceKeys.contains(device.getKey()));
  }

  @VisibleForTesting
  void initChecker(@Nullable RunnerAndConfigurationSettings configurationAndSettings, @NotNull Function<Module, AndroidFacet> facetGetter) {
    if (configurationAndSettings == null) {
      myChecker = null;
      return;
    }

    Object configuration = configurationAndSettings.getConfiguration();

    if (!(configuration instanceof ModuleBasedConfiguration)) {
      myChecker = null;
      return;
    }

    Module module = ((ModuleBasedConfiguration<?, ?>)configuration).getConfigurationModule().getModule();

    if (module == null) {
      myChecker = null;
      return;
    }

    AndroidFacet facet = facetGetter.apply(module);

    if (facet == null || Disposer.isDisposed(facet)) {
      myChecker = null;
      return;
    }

    Object platform = AndroidPlatform.getInstance(facet.getModule());

    if (platform == null) {
      myChecker = null;
      return;
    }

    myChecker = LaunchCompatibilityCheckerImpl.create(facet, null, null);
  }

  @VisibleForTesting
  Object getChecker() {
    return myChecker;
  }
}
