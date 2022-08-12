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

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import java.awt.Component;
import javax.swing.AbstractButton;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class WipeDataItemTest {
  private final @NotNull AvdInfo myAvd;
  private final @NotNull AvdManagerConnection myConnection;
  private final @NotNull VirtualDeviceTable myTable;
  private final @NotNull VirtualDevicePopUpMenuButtonTableCellEditor myEditor;

  public WipeDataItemTest() {
    myAvd = Mockito.mock(AvdInfo.class);
    myConnection = Mockito.mock(AvdManagerConnection.class);
    myTable = Mockito.mock(VirtualDeviceTable.class);

    VirtualDevicePanel panel = Mockito.mock(VirtualDevicePanel.class);
    Mockito.when(panel.getTable()).thenReturn(myTable);

    myEditor = Mockito.mock(VirtualDevicePopUpMenuButtonTableCellEditor.class);
    Mockito.when(myEditor.getPanel()).thenReturn(panel);
  }

  @Test
  public void wipeDataItemDeviceIsOnline() {
    // Arrange
    Mockito.when(myEditor.getDevice()).thenReturn(TestVirtualDevices.onlinePixel5Api31(myAvd));

    AbstractButton item = new WipeDataItem(myEditor,
                                           WipeDataItemTest::showCannotWipeARunningAvdDialog,
                                           (device, component) -> false,
                                           () -> myConnection);

    // Act
    item.doClick();

    // Assert
    Mockito.verify(myConnection, Mockito.never()).wipeUserData(myAvd);
    Mockito.verify(myTable, Mockito.never()).reloadDevice(TestVirtualDevices.newKey("Pixel_5_API_31"));
  }

  @Test
  public void wipeDataItemNotWipe() {
    // Arrange
    Mockito.when(myEditor.getDevice()).thenReturn(TestVirtualDevices.pixel5Api31(myAvd));

    AbstractButton item = new WipeDataItem(myEditor,
                                           WipeDataItemTest::showCannotWipeARunningAvdDialog,
                                           (device, component) -> false,
                                           () -> myConnection);

    // Act
    item.doClick();

    // Assert
    Mockito.verify(myConnection, Mockito.never()).wipeUserData(myAvd);
    Mockito.verify(myTable, Mockito.never()).reloadDevice(TestVirtualDevices.newKey("Pixel_5_API_31"));
  }

  @Test
  public void wipeDataItem() {
    // Arrange
    Mockito.when(myEditor.getDevice()).thenReturn(TestVirtualDevices.pixel5Api31(myAvd));

    AbstractButton item = new WipeDataItem(myEditor,
                                           WipeDataItemTest::showCannotWipeARunningAvdDialog,
                                           (device, component) -> true,
                                           () -> myConnection);

    // Act
    item.doClick();

    // Assert
    Mockito.verify(myConnection).wipeUserData(myAvd);
    Mockito.verify(myTable).reloadDevice(TestVirtualDevices.newKey("Pixel_5_API_31"));
  }

  private static void showCannotWipeARunningAvdDialog(@NotNull Component component) {
  }
}
