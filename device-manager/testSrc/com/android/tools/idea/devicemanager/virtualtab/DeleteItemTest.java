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
import com.google.common.util.concurrent.Futures;
import java.awt.Component;
import javax.swing.AbstractButton;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeleteItemTest {
  private final @NotNull AvdInfo myAvd;
  private final @NotNull VirtualDeviceTableModel myModel;
  private final @NotNull VirtualDevicePopUpMenuButtonTableCellEditor myEditor;

  public DeleteItemTest() {
    myAvd = Mockito.mock(AvdInfo.class);
    myModel = Mockito.mock(VirtualDeviceTableModel.class);

    VirtualDeviceTable table = Mockito.mock(VirtualDeviceTable.class);
    Mockito.when(table.getModel()).thenReturn(myModel);

    VirtualDevicePanel panel = Mockito.mock(VirtualDevicePanel.class);
    Mockito.when(panel.getTable()).thenReturn(table);

    myEditor = Mockito.mock(VirtualDevicePopUpMenuButtonTableCellEditor.class);
    Mockito.when(myEditor.getPanel()).thenReturn(panel);
  }

  @Test
  public void deleteItemDeviceIsOnline() {
    // Arrange
    VirtualDevice virtualDevice = TestVirtualDevices.onlinePixel5Api31(myAvd);
    Mockito.when(myEditor.getDevice()).thenReturn(virtualDevice);

    AbstractButton item = new DeleteItem(myEditor, DeleteItemTest::showCannotDeleteRunningAvdDialog, (device, component) -> false);

    // Act
    item.doClick();

    // Assert
    Mockito.verify(myModel, Mockito.never()).remove(virtualDevice);
  }

  @Test
  public void deleteItemNotDelete() {
    // Arrange
    VirtualDevice virtualDevice = TestVirtualDevices.pixel5Api31(myAvd);
    Mockito.when(myEditor.getDevice()).thenReturn(virtualDevice);

    AbstractButton item = new DeleteItem(myEditor, DeleteItemTest::showCannotDeleteRunningAvdDialog, (device, component) -> false);

    // Act
    item.doClick();

    // Assert
    Mockito.verify(myModel, Mockito.never()).remove(virtualDevice);
  }

  @Test
  public void deleteItem() {
    // Arrange
    VirtualDevice virtualDevice = TestVirtualDevices.pixel5Api31(myAvd);
    Mockito.when(myEditor.getDevice()).thenReturn(virtualDevice);
    Mockito.when(myModel.remove(virtualDevice)).thenReturn(Futures.immediateFuture(true));

    AbstractButton item = new DeleteItem(myEditor, DeleteItemTest::showCannotDeleteRunningAvdDialog, (device, component) -> true);

    // Act
    item.doClick();

    // Assert
    Mockito.verify(myModel).remove(virtualDevice);
  }

  private static void showCannotDeleteRunningAvdDialog(@NotNull Component component) {
  }
}
