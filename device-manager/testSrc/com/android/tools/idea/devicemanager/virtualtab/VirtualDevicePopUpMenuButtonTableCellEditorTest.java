/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.devicemanager.DeviceTables;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.PopUpMenuButtonTableCellEditor;
import com.android.tools.idea.devicemanager.PopUpMenuValue;
import java.util.Collection;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JPopupMenu.Separator;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDevicePopUpMenuButtonTableCellEditorTest {
  private final @NotNull Emulator myEmulator = Mockito.mock(Emulator.class);

  private final PopUpMenuButtonTableCellEditor myEditor =
    new VirtualDevicePopUpMenuButtonTableCellEditor(Mockito.mock(VirtualDevicePanel.class), myEmulator);

  @Test
  public void newColdBootNowItemEmulatorDoesntSupportColdBooting() {
    // Arrange
    JTable table = DeviceTables.mock(TestVirtualDevices.pixel5Api31(Mockito.mock(AvdInfo.class)));
    myEditor.getTableCellEditorComponent(table, PopUpMenuValue.INSTANCE, false, 0, 6);

    // Act
    Collection<JComponent> items = myEditor.newItems();

    // Assert
    assertEquals(8, items.size());
  }

  @Test
  public void newColdBootNowItem() {
    // Arrange
    Mockito.when(myEmulator.supportsColdBooting()).thenReturn(true);

    JTable table = DeviceTables.mock(TestVirtualDevices.pixel5Api31(Mockito.mock(AvdInfo.class)));
    myEditor.getTableCellEditorComponent(table, PopUpMenuValue.INSTANCE, false, 0, 6);

    // Act
    List<JComponent> items = myEditor.newItems();

    // Assert
    assertEquals(9, items.size());

    AbstractButton item = (AbstractButton)items.get(2);

    assertEquals("Cold Boot Now", item.getText());
    assertEquals("Force one cold boot", item.getToolTipText());
  }

  @Test
  public void newPairDeviceItemWearOs() {
    // Arrange
    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName("Wear_OS_Small_Round_API_28"))
      .setType(DeviceType.WEAR_OS)
      .setName("Wear OS Small Round API 28")
      .setTarget("Android 9.0 Wear OS")
      .setCpuArchitecture("x86")
      .setAndroidVersion(new AndroidVersion(28))
      .setAvdInfo(Mockito.mock(AvdInfo.class))
      .build();

    myEditor.getTableCellEditorComponent(DeviceTables.mock(device), PopUpMenuValue.INSTANCE, false, 0, 6);

    // Act
    List<JComponent> items = myEditor.newItems();

    // Assert
    assertEquals(8, items.size());

    AbstractButton item = (AbstractButton)items.get(5);

    assertTrue(item.isEnabled());
    assertEquals("Pair Device", item.getText());
    assertEquals("Wear OS virtual device pairing assistant", item.getToolTipText());
  }

  @Test
  public void newPairDeviceItemApiLevelIsLessThan30() {
    // Arrange
    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName("Pixel_4_API_29"))
      .setName("Pixel 4 API 29")
      .setTarget("Android 10.0 Google Play")
      .setCpuArchitecture("x86")
      .setAvdInfo(Mockito.mock(AvdInfo.class))
      .build();

    myEditor.getTableCellEditorComponent(DeviceTables.mock(device), PopUpMenuValue.INSTANCE, false, 0, 6);

    // Act
    List<JComponent> items = myEditor.newItems();

    // Assert
    assertEquals(8, items.size());

    AbstractButton item = (AbstractButton)items.get(5);

    assertFalse(item.isEnabled());
    assertEquals("Pair Device", item.getText());
    assertEquals("Wear pairing requires API level >= 30", item.getToolTipText());
  }

  @Test
  public void newPairDeviceItemAvdDoesntHavePlayStore() {
    // Arrange
    JTable table = DeviceTables.mock(TestVirtualDevices.pixel5Api31(Mockito.mock(AvdInfo.class)));
    myEditor.getTableCellEditorComponent(table, PopUpMenuValue.INSTANCE, false, 0, 6);

    // Act
    List<JComponent> items = myEditor.newItems();

    // Assert
    assertEquals(8, items.size());

    AbstractButton item = (AbstractButton)items.get(5);

    assertFalse(item.isEnabled());
    assertEquals("Pair Device", item.getText());
    assertEquals("Wear pairing requires Google Play", item.getToolTipText());
  }

  @Test
  public void newPairDeviceItem() {
    // Arrange
    AvdInfo avd = Mockito.mock(AvdInfo.class);
    Mockito.when(avd.hasPlayStore()).thenReturn(true);

    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName("Pixel_4_API_31"))
      .setName("Pixel 4 API 31")
      .setTarget("Android 12.0 Google Play")
      .setCpuArchitecture("x86_64")
      .setAndroidVersion(new AndroidVersion(31))
      .setAvdInfo(avd)
      .build();

    myEditor.getTableCellEditorComponent(DeviceTables.mock(device), PopUpMenuValue.INSTANCE, false, 0, 6);

    // Act
    List<JComponent> items = myEditor.newItems();

    // Assert
    assertEquals(8, items.size());
    assertTrue(items.get(4) instanceof Separator);

    AbstractButton item = (AbstractButton)items.get(5);

    assertTrue(item.isEnabled());
    assertEquals("Pair Device", item.getText());
    assertEquals("Wear OS virtual device pairing assistant", item.getToolTipText());

    assertTrue(items.get(6) instanceof Separator);
  }
}
