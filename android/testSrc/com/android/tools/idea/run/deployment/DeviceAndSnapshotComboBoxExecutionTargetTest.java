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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.intellij.execution.ExecutionTarget;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import com.intellij.execution.application.ApplicationConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceAndSnapshotComboBoxExecutionTargetTest {
  @Test
  public void deviceAndSnapshotComboBoxExecutionTarget() {
    // Arrange
    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 2 API 29")
      .setKey(new Key("Pixel_2_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    Object id1 = new DeviceAndSnapshotComboBoxExecutionTarget(Arrays.asList(device1, device2)).getId();
    Object id2 = new DeviceAndSnapshotComboBoxExecutionTarget(Arrays.asList(device2, device1)).getId();

    // Assert
    assertEquals(id1, id2);
  }

  @Test
  public void deviceAndSnapshotComboBoxExecutionTargetDevicesAndIdAreConsistent() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    DeviceAndSnapshotComboBoxExecutionTarget target = new DeviceAndSnapshotComboBoxExecutionTarget(device);

    // Assert
    assertEquals(Collections.emptyList(), target.getDeploymentDevices());
    assertEquals("device_and_snapshot_combo_box_target[]", target.getId());
  }

  @Test
  public void getDevicesDeviceIsNull() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    AndroidExecutionTarget target = new DeviceAndSnapshotComboBoxExecutionTarget(device);

    // Act
    Object actualDevices = target.getDevices();

    // Assert
    assertEquals(Collections.emptyList(), actualDevices);
  }

  @Test
  public void getDisplayNameSizeEquals1() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setConnectionTime(Instant.parse("2020-03-17T22:16:00.298Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    ExecutionTarget target = new DeviceAndSnapshotComboBoxExecutionTarget(device);

    // Act
    Object actualDisplayName = target.getDisplayName();

    // Assert
    assertEquals("Pixel 3 API 29", actualDisplayName);
  }

  @Test
  public void getDisplayName() {
    // Arrange
    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 2 API 29")
      .setKey(new Key("Pixel_2_API_29"))
      .setConnectionTime(Instant.parse("2020-03-17T22:16:00.298Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setConnectionTime(Instant.parse("2020-03-17T22:16:00.298Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    ExecutionTarget target = new DeviceAndSnapshotComboBoxExecutionTarget(Arrays.asList(device1, device2));

    // Act
    Object actualDisplayName = target.getDisplayName();

    // Assert
    assertEquals("Multiple Devices", actualDisplayName);
  }

  @Test
  public void deviceTargetNotSuggestedForNonAndroidRunConfigurations() {
    ExecutionTarget target = new DeviceAndSnapshotComboBoxExecutionTarget(Collections.emptyList());

    AndroidRunConfiguration android = Mockito.mock(AndroidRunConfiguration.class);
    AndroidTestRunConfiguration androidTest = Mockito.mock(AndroidTestRunConfiguration.class);
    ApplicationConfiguration nonAndroid = Mockito.mock(ApplicationConfiguration.class);

    assertTrue(target.canRun(android));
    assertTrue(target.canRun(androidTest));
    assertFalse(target.canRun(nonAndroid));
  }
}
