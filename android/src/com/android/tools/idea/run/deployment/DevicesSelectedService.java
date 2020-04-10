/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.ExecutionTarget;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.containers.ContainerUtil;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A project scoped service that wraps the PropertiesComponent that persists the device keys selected with the combo box or the Modify
 * Device Set dialog. The actual point of this is for stubbing and verification in tests.
 */
final class DevicesSelectedService {
  @VisibleForTesting
  static final String DEVICE_KEY_SELECTED_WITH_COMBO_BOX = "DeviceAndSnapshotComboBoxAction.selectedDevice";

  @VisibleForTesting
  static final String TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX = "DeviceAndSnapshotComboBoxAction.selectionTime";

  @VisibleForTesting
  static final String MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX = "DeviceAndSnapshotComboBoxAction.multipleDevicesSelected";

  @VisibleForTesting
  static final String DEVICE_KEYS_SELECTED_WITH_DIALOG = "SelectDeploymentTargetsDialog.selectedDevices";

  @NotNull
  private final Project myProject;

  @NotNull
  private final Function<Project, PropertiesComponent> myPropertiesComponentGetInstance;

  @NotNull
  private final Clock myClock;

  @NotNull
  private final Function<Project, ExecutionTargetService> myExecutionTargetServiceGetInstance;

  @NotNull
  private final Function<Project, AsyncDevicesGetter> myAsyncDevicesGetterGetInstance;

  @VisibleForTesting
  static final class Builder {
    @Nullable
    private Project myProject;

    @Nullable
    private Function<Project, PropertiesComponent> myPropertiesComponentGetInstance;

    @Nullable
    private Clock myClock;

    @Nullable
    private Function<Project, ExecutionTargetService> myExecutionTargetServiceGetInstance;

    @Nullable
    private Function<Project, AsyncDevicesGetter> myAsyncDevicesGetterGetInstance;

    Builder() {
      myExecutionTargetServiceGetInstance = project -> null;
      myAsyncDevicesGetterGetInstance = project -> null;
    }

    @NotNull
    Builder setProject(@NotNull Project project) {
      myProject = project;
      return this;
    }

    @NotNull
    Builder setPropertiesComponentGetInstance(@NotNull Function<Project, PropertiesComponent> propertiesComponentGetInstance) {
      myPropertiesComponentGetInstance = propertiesComponentGetInstance;
      return this;
    }

    @NotNull
    Builder setClock(@NotNull Clock clock) {
      myClock = clock;
      return this;
    }

    @NotNull
    Builder setExecutionTargetServiceGetInstance(@NotNull Function<Project, ExecutionTargetService> executionTargetServiceGetInstance) {
      myExecutionTargetServiceGetInstance = executionTargetServiceGetInstance;
      return this;
    }

    @NotNull
    Builder setAsyncDevicesGetterGetInstance(@NotNull Function<Project, AsyncDevicesGetter> asyncDevicesGetterGetInstance) {
      myAsyncDevicesGetterGetInstance = asyncDevicesGetterGetInstance;
      return this;
    }

    @NotNull
    DevicesSelectedService build() {
      return new DevicesSelectedService(this);
    }
  }

  @SuppressWarnings("unused")
  private DevicesSelectedService(@NotNull Project project) {
    this(new Builder()
           .setProject(project)
           .setPropertiesComponentGetInstance(PropertiesComponent::getInstance)
           .setClock(Clock.systemDefaultZone())
           .setExecutionTargetServiceGetInstance(ExecutionTargetService::getInstance)
           .setAsyncDevicesGetterGetInstance(AsyncDevicesGetter::getInstance));
  }

  @NonInjectable
  private DevicesSelectedService(@NotNull Builder builder) {
    assert builder.myProject != null;
    myProject = builder.myProject;

    assert builder.myPropertiesComponentGetInstance != null;
    myPropertiesComponentGetInstance = builder.myPropertiesComponentGetInstance;

    assert builder.myClock != null;
    myClock = builder.myClock;

    assert builder.myExecutionTargetServiceGetInstance != null;
    myExecutionTargetServiceGetInstance = builder.myExecutionTargetServiceGetInstance;

    assert builder.myAsyncDevicesGetterGetInstance != null;
    myAsyncDevicesGetterGetInstance = builder.myAsyncDevicesGetterGetInstance;
  }

  @NotNull
  static DevicesSelectedService getInstance(@NotNull Project project) {
    return project.getService(DevicesSelectedService.class);
  }

  @NotNull
  List<Device> getSelectedDevices(@NotNull List<Device> devices) {
    if (isMultipleDevicesSelectedInComboBox()) {
      return getDevicesSelectedWithDialog();
    }

    Device device = getDeviceSelectedWithComboBox(devices);

    if (device == null) {
      return Collections.emptyList();
    }

    return Collections.singletonList(device);
  }

  @Nullable
  Device getDeviceSelectedWithComboBox(@NotNull List<Device> devices) {
    if (devices.isEmpty()) {
      return null;
    }

    String keyAsString = myPropertiesComponentGetInstance.apply(myProject).getValue(DEVICE_KEY_SELECTED_WITH_COMBO_BOX);

    if (keyAsString == null) {
      return devices.get(0);
    }

    Key key = new Key(keyAsString);

    Optional<Device> optionalSelectedDevice = devices.stream()
      .filter(device -> device.getKey().equals(key))
      .findFirst();

    if (!optionalSelectedDevice.isPresent()) {
      return devices.get(0);
    }

    Device selectedDevice = optionalSelectedDevice.get();

    Optional<Device> optionalConnectedDevice = devices.stream()
      .filter(Device::isConnected)
      .findFirst();

    if (!optionalConnectedDevice.isPresent()) {
      return selectedDevice;
    }

    Device connectedDevice = optionalConnectedDevice.get();

    Instant connectionTime = connectedDevice.getConnectionTime();
    assert connectionTime != null : "connected device \"" + connectedDevice + "\" has a null connection time";

    if (getTimeDeviceWasSelectedWithComboBox(selectedDevice).isBefore(connectionTime)) {
      return connectedDevice;
    }

    return selectedDevice;
  }

  @NotNull
  private Instant getTimeDeviceWasSelectedWithComboBox(@NotNull Device device) {
    CharSequence time = myPropertiesComponentGetInstance.apply(myProject).getValue(TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX);

    if (time == null) {
      // I don't know why this happens
      Logger.getInstance(DevicesSelectedService.class).warn("selected device \"" + device + "\" has a null selection time string");

      return Instant.MIN;
    }

    return Instant.parse(time);
  }

  void setDeviceSelectedWithComboBox(@Nullable Device deviceSelectedWithComboBox) {
    PropertiesComponent properties = myPropertiesComponentGetInstance.apply(myProject);
    properties.unsetValue(MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX);

    ExecutionTarget target;

    if (deviceSelectedWithComboBox == null) {
      properties.unsetValue(TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX);
      properties.unsetValue(DEVICE_KEY_SELECTED_WITH_COMBO_BOX);

      target = new DeviceAndSnapshotComboBoxExecutionTarget();
    }
    else {
      properties.setValue(DEVICE_KEY_SELECTED_WITH_COMBO_BOX, deviceSelectedWithComboBox.getKey().toString());
      properties.setValue(TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX, myClock.instant().toString());

      target = new DeviceAndSnapshotComboBoxExecutionTarget(deviceSelectedWithComboBox);
    }

    myExecutionTargetServiceGetInstance.apply(myProject).setActiveTarget(target);
  }

  boolean isMultipleDevicesSelectedInComboBox() {
    return myPropertiesComponentGetInstance.apply(myProject).getBoolean(MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX);
  }

  void setMultipleDevicesSelectedInComboBox(boolean multipleDevicesSelectedInComboBox) {
    PropertiesComponent properties = myPropertiesComponentGetInstance.apply(myProject);

    properties.unsetValue(TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX);
    properties.unsetValue(DEVICE_KEY_SELECTED_WITH_COMBO_BOX);

    ExecutionTarget target;

    if (!multipleDevicesSelectedInComboBox) {
      properties.unsetValue(MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX);
      target = new DeviceAndSnapshotComboBoxExecutionTarget();
    }
    else {
      properties.setValue(MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX, true);
      target = new DeviceAndSnapshotComboBoxExecutionTarget(getDevicesSelectedWithDialog());
    }

    myExecutionTargetServiceGetInstance.apply(myProject).setActiveTarget(target);
  }

  boolean isDialogSelectionEmpty() {
    return !myPropertiesComponentGetInstance.apply(myProject).isValueSet(DEVICE_KEYS_SELECTED_WITH_DIALOG);
  }

  @NotNull
  List<Device> getDevicesSelectedWithDialog() {
    Collection<Key> keys = getDeviceKeysSelectedWithDialog();
    return ContainerUtil.filter(myAsyncDevicesGetterGetInstance.apply(myProject).get(), device -> keys.contains(device.getKey()));
  }

  void setDevicesSelectedWithDialog(@NotNull List<Device> devicesSelectedWithDialog) {
    setDeviceKeysSelectedWithDialog(devicesSelectedWithDialog.stream().map(Device::getKey));
  }

  @NotNull
  Set<Key> getDeviceKeysSelectedWithDialog() {
    String[] keys = myPropertiesComponentGetInstance.apply(myProject).getValues(DEVICE_KEYS_SELECTED_WITH_DIALOG);

    if (keys == null) {
      return Collections.emptySet();
    }

    assert !Arrays.asList(keys).contains("") : Arrays.toString(keys);

    return Arrays.stream(keys)
      .map(Key::new)
      .collect(Collectors.toSet());
  }

  void setDeviceKeysSelectedWithDialog(@NotNull Set<Key> deviceKeysSelectedWithDialog) {
    setDeviceKeysSelectedWithDialog(deviceKeysSelectedWithDialog.stream());
  }

  private void setDeviceKeysSelectedWithDialog(@NotNull Stream<Key> stream) {
    String[] array = stream
      .map(Key::toString)
      .toArray(String[]::new);

    PropertiesComponent properties = myPropertiesComponentGetInstance.apply(myProject);

    if (array.length == 0) {
      properties.unsetValue(DEVICE_KEYS_SELECTED_WITH_DIALOG);
    }
    else {
      properties.setValues(DEVICE_KEYS_SELECTED_WITH_DIALOG, array);
    }
  }
}
