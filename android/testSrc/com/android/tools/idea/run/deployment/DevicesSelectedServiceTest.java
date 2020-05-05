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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.ide.util.ProjectPropertiesComponentImpl;
import com.intellij.ide.util.PropertiesComponent;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DevicesSelectedServiceTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private PropertiesComponent myProperties;
  private DevicesSelectedService myService;

  @Before
  public void newService() {
    myProperties = new ProjectPropertiesComponentImpl();
    myService = new DevicesSelectedService(myRule.getProject(), project -> myProperties, Mockito.mock(Clock.class));
  }

  @Test
  public void getDeviceSelectedWithComboBoxDevicesIsEmpty() {
    // Act
    Object device = myService.getDeviceSelectedWithComboBox(Collections.emptyList());

    // Assert
    assertNull(device);
  }

  @Test
  public void getDeviceSelectedWithComboBoxKeyAsStringIsNull() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    Object selectedDevice = myService.getDeviceSelectedWithComboBox(Collections.singletonList(device));

    // Assert
    assertEquals(device, selectedDevice);
  }

  @Test
  public void getDeviceSelectedWithComboBoxSelectedDeviceIsntPresent() {
    // Arrange
    myProperties.setValue(DevicesSelectedService.DEVICE_KEY_SELECTED_WITH_COMBO_BOX, "Pixel_2_API_29");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    Object selectedDevice = myService.getDeviceSelectedWithComboBox(Collections.singletonList(device));

    // Assert
    assertEquals(device, selectedDevice);
  }

  @Test
  public void getDeviceSelectedWithComboBoxConnectedDeviceIsntPresent() {
    // Arrange
    myProperties.setValue(DevicesSelectedService.DEVICE_KEY_SELECTED_WITH_COMBO_BOX, "Pixel_3_API_29");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    Object selectedDevice = myService.getDeviceSelectedWithComboBox(Collections.singletonList(device));

    // Assert
    assertEquals(device, selectedDevice);
  }

  @Test
  public void getDeviceSelectedWithComboBoxSelectionTimeIsBeforeConnectionTime() {
    // Arrange
    myProperties.setValue(DevicesSelectedService.DEVICE_KEY_SELECTED_WITH_COMBO_BOX, "Pixel_3_API_29");
    myProperties.setValue(DevicesSelectedService.TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX, "2018-11-28T01:15:27.000Z");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device connectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 API 29")
      .setKey(new Key("Pixel_2_API_29"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:28.000Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    Object selectedDevice = myService.getDeviceSelectedWithComboBox(Arrays.asList(device, connectedDevice));

    // Assert
    assertEquals(connectedDevice, selectedDevice);
  }

  @Test
  public void getDeviceSelectedWithComboBox() {
    // Arrange
    myProperties.setValue(DevicesSelectedService.DEVICE_KEY_SELECTED_WITH_COMBO_BOX, "Pixel_3_API_29");
    myProperties.setValue(DevicesSelectedService.TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX, "2018-11-28T01:15:27.000Z");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device connectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 API 29")
      .setKey(new Key("Pixel_2_API_29"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:26.000Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    Object selectedDevice = myService.getDeviceSelectedWithComboBox(Arrays.asList(device, connectedDevice));

    // Assert
    assertEquals(device, selectedDevice);
  }

  @Test
  public void getDeviceSelectedWithComboBoxSelectionTimeIsNull() {
    // Arrange
    myProperties.setValue(DevicesSelectedService.DEVICE_KEY_SELECTED_WITH_COMBO_BOX, "Pixel_3_API_29");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device connectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 API 29")
      .setKey(new Key("Pixel_2_API_29"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:28.000Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    Object selectedDevice = myService.getDeviceSelectedWithComboBox(Arrays.asList(device, connectedDevice));

    // Assert
    assertEquals(connectedDevice, selectedDevice);
  }

  @Test
  public void setDeviceKeysSelectedWithDialog() {
    // Act
    myService.setDeviceKeysSelectedWithDialog(Collections.emptySet());

    // Assert
    assertTrue(myService.isDialogSelectionEmpty());
  }
}
