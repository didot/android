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
package com.android.tools.idea.devicemanager.physicaltab;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.testing.swing.TableModelEventArgumentMatcher;
import com.google.common.collect.Lists;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PhysicalDeviceTableModelTest {
  @Test
  public void newPhysicalDeviceTableModel() {
    // Arrange
    PhysicalDevice onlinePixel5 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("0A071FDD4003ZG"))
      .setLastOnlineTime(Instant.parse("2021-03-24T22:38:05.890570Z"))
      .setName("Google Pixel 5")
      .setTarget("Android 11.0")
      .setApi("30")
      .addConnectionType(ConnectionType.USB)
      .build();

    List<PhysicalDevice> devices = Arrays.asList(TestPhysicalDevices.GOOGLE_PIXEL_3, onlinePixel5);

    // Act
    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(devices);

    // Assert
    assertEquals(Arrays.asList(onlinePixel5, TestPhysicalDevices.GOOGLE_PIXEL_3), model.getCombinedDevices());
  }

  @Test
  public void addOrSetModelRowIndexEqualsNegativeOne() {
    // Arrange
    TableModelListener listener = Mockito.mock(TableModelListener.class);

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Lists.newArrayList(TestPhysicalDevices.GOOGLE_PIXEL_3));
    model.addTableModelListener(listener);

    PhysicalDevice onlinePixel5 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("0A071FDD4003ZG"))
      .setName("Google Pixel 5")
      .setTarget("Android 11.0")
      .setApi("30")
      .addConnectionType(ConnectionType.USB)
      .build();

    // Act
    model.addOrSet(onlinePixel5);

    // Assert
    assertEquals(Arrays.asList(onlinePixel5, TestPhysicalDevices.GOOGLE_PIXEL_3), model.getCombinedDevices());
    Mockito.verify(listener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model))));
  }

  @Test
  public void addOrSetModelRowIndexDoesntEqualNegativeOne() {
    // Arrange
    TableModelListener listener = Mockito.mock(TableModelListener.class);
    List<PhysicalDevice> devices = Arrays.asList(TestPhysicalDevices.GOOGLE_PIXEL_3, TestPhysicalDevices.GOOGLE_PIXEL_5);

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(devices);
    model.addTableModelListener(listener);

    PhysicalDevice onlinePixel5 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("0A071FDD4003ZG"))
      .setName("Google Pixel 5")
      .setTarget("Android 11.0")
      .setApi("30")
      .addConnectionType(ConnectionType.USB)
      .build();

    // Act
    model.addOrSet(onlinePixel5);

    // Assert
    assertEquals(Arrays.asList(onlinePixel5, TestPhysicalDevices.GOOGLE_PIXEL_3), model.getCombinedDevices());
    Mockito.verify(listener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model))));
  }

  @Test
  public void setNameOverride() {
    // Arrange
    Key domainName = new DomainName("adb-86UX00F4R-cYuns7._adb-tls-connect._tcp");

    PhysicalDevice googlePixel3WithDomainName = new PhysicalDevice.Builder()
      .setKey(domainName)
      .setName("Google Pixel 3")
      .setTarget("Android 12 Preview")
      .setApi("S")
      .build();

    TableModelListener listener = Mockito.mock(TableModelListener.class);

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Arrays.asList(TestPhysicalDevices.GOOGLE_PIXEL_3,
                                                                                googlePixel3WithDomainName,
                                                                                TestPhysicalDevices.GOOGLE_PIXEL_5));

    model.addTableModelListener(listener);

    Key serialNumber = new SerialNumber("86UX00F4R");

    // Act
    model.setNameOverride(serialNumber, "Name Override");

    // Assert
    Object expectedDevice1 = new PhysicalDevice.Builder()
      .setKey(serialNumber)
      .setName("Google Pixel 3")
      .setNameOverride("Name Override")
      .setTarget("Android 12 Preview")
      .setApi("S")
      .build();

    Object expectedDevice2 = new PhysicalDevice.Builder()
      .setKey(domainName)
      .setName("Google Pixel 3")
      .setNameOverride("Name Override")
      .setTarget("Android 12 Preview")
      .setApi("S")
      .build();

    assertEquals(Arrays.asList(expectedDevice1, expectedDevice2, TestPhysicalDevices.GOOGLE_PIXEL_5), model.getDevices());
    assertEquals(Arrays.asList(expectedDevice1, TestPhysicalDevices.GOOGLE_PIXEL_5), model.getCombinedDevices());

    Mockito.verify(listener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model))));
  }

  @Test
  public void combineDevicesDomainNameSerialNumberEqualsSerialNumberDeviceKey() {
    // Arrange
    PhysicalDevice domainNameGooglePixel3 = new PhysicalDevice.Builder()
      .setKey(new DomainName("adb-86UX00F4R-cYuns7._adb-tls-connect._tcp"))
      .setName("Google Pixel 3")
      .setTarget("Android 12 Preview")
      .setApi("S")
      .build();

    List<PhysicalDevice> devices = Arrays.asList(TestPhysicalDevices.GOOGLE_PIXEL_3, domainNameGooglePixel3);

    // Act
    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(devices);

    // Assert
    assertEquals(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3), model.getCombinedDevices());
  }

  @Test
  public void combineDevices() {
    // Arrange
    PhysicalDevice domainNameGooglePixel3 = new PhysicalDevice.Builder()
      .setKey(new DomainName("adb-86UX00F4R-cYuns7._adb-tls-connect._tcp"))
      .setName("Google Pixel 3")
      .setTarget("Android 12 Preview")
      .setApi("S")
      .build();

    List<PhysicalDevice> devices = Arrays.asList(TestPhysicalDevices.GOOGLE_PIXEL_5, domainNameGooglePixel3);

    // Act
    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(devices);

    // Assert
    assertEquals(Arrays.asList(domainNameGooglePixel3, TestPhysicalDevices.GOOGLE_PIXEL_5), model.getCombinedDevices());
  }

  @Test
  public void getRowCount() {
    // Arrange
    TableModel model = new PhysicalDeviceTableModel(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3));

    // Act
    int count = model.getRowCount();

    // Assert
    assertEquals(1, count);
  }

  @Test
  public void getValueAtDeviceModelColumnIndex() {
    // Arrange
    TableModel model = new PhysicalDeviceTableModel(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3));

    // Act
    Object value = model.getValueAt(0, PhysicalDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX);

    // Assert
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3, value);
  }

  @Test
  public void getValueAtApiModelColumnIndex() {
    // Arrange
    TableModel model = new PhysicalDeviceTableModel(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3));

    // Act
    Object value = model.getValueAt(0, PhysicalDeviceTableModel.API_MODEL_COLUMN_INDEX);

    // Assert
    assertEquals("S", value);
  }

  @Test
  public void getValueAtTypeModelColumnIndex() {
    // Arrange
    TableModel model = new PhysicalDeviceTableModel(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3));

    // Act
    Object value = model.getValueAt(0, PhysicalDeviceTableModel.TYPE_MODEL_COLUMN_INDEX);

    // Assert
    assertEquals("", value);
  }
}
