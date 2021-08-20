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

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.Actions;
import java.awt.Component;
import java.util.Collections;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.UIManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class ActionsTableCellRendererTest {
  @Test
  public void getTableCellRendererComponent() {
    // Arrange
    ActionsTableCellRenderer renderer = new ActionsTableCellRenderer();

    PhysicalDevicePanel panel = Mockito.mock(PhysicalDevicePanel.class);
    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3));
    JTable table = new PhysicalDeviceTable(panel, model, PhysicalDeviceTableCellRenderer::new, ActionsTableCellRenderer::new);

    // Act
    Component component = renderer.getTableCellRendererComponent(table, Actions.INSTANCE, false, true, 0, 3);

    // Assert
    assertEquals(table.getBackground(), component.getBackground());
    assertEquals(UIManager.getBorder("Table.focusCellHighlightBorder"), ((JComponent)component).getBorder());
  }
}
