/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;

final class SelectDeploymentTargetsDialogTableModel extends AbstractTableModel {
  static final int SELECTED_MODEL_COLUMN_INDEX = 0;
  static final int TYPE_MODEL_COLUMN_INDEX = 1;
  private static final int DEVICE_MODEL_COLUMN_INDEX = 2;
  private static final int SERIAL_NUMBER_MODEL_COLUMN_INDEX = 3;
  private static final int SNAPSHOT_MODEL_COLUMN_INDEX = 4;
  private static final int ISSUE_MODEL_COLUMN_INDEX = 5;

  @NotNull
  private final List<Device> myDevices;

  @NotNull
  private final JTable myTable;

  @NotNull
  private final Multiset<String> myDeviceNameMultiset;

  SelectDeploymentTargetsDialogTableModel(@NotNull List<Device> devices, @NotNull JTable table) {
    myDevices = devices;
    myDevices.sort(new DeviceComparator());

    myTable = table;

    myDeviceNameMultiset = devices.stream()
      .map(Device::getName)
      .collect(Collectors.toCollection(() -> HashMultiset.create(myDevices.size())));
  }

  @NotNull
  Device getDeviceAt(int modelRowIndex) {
    return myDevices.get(modelRowIndex);
  }

  @Override
  public int getRowCount() {
    return myDevices.size();
  }

  @Override
  public int getColumnCount() {
    return 6;
  }

  @NotNull
  @Override
  public String getColumnName(int modelColumnIndex) {
    switch (modelColumnIndex) {
      case SELECTED_MODEL_COLUMN_INDEX:
        return "";
      case TYPE_MODEL_COLUMN_INDEX:
        return "Type";
      case DEVICE_MODEL_COLUMN_INDEX:
        return "Device";
      case SERIAL_NUMBER_MODEL_COLUMN_INDEX:
        return "Serial Number";
      case SNAPSHOT_MODEL_COLUMN_INDEX:
        return "Snapshot";
      case ISSUE_MODEL_COLUMN_INDEX:
        return "Issue";
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @NotNull
  @Override
  public Class<?> getColumnClass(int modelColumnIndex) {
    switch (modelColumnIndex) {
      case SELECTED_MODEL_COLUMN_INDEX:
        return Boolean.class;
      case TYPE_MODEL_COLUMN_INDEX:
        return Icon.class;
      case DEVICE_MODEL_COLUMN_INDEX:
      case SERIAL_NUMBER_MODEL_COLUMN_INDEX:
      case SNAPSHOT_MODEL_COLUMN_INDEX:
      case ISSUE_MODEL_COLUMN_INDEX:
        return Object.class;
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @Override
  public boolean isCellEditable(int modelRowIndex, int modelColumnIndex) {
    switch (modelColumnIndex) {
      case SELECTED_MODEL_COLUMN_INDEX:
        return true;
      case TYPE_MODEL_COLUMN_INDEX:
      case DEVICE_MODEL_COLUMN_INDEX:
      case SERIAL_NUMBER_MODEL_COLUMN_INDEX:
      case SNAPSHOT_MODEL_COLUMN_INDEX:
      case ISSUE_MODEL_COLUMN_INDEX:
        return false;
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @NotNull
  @Override
  public Object getValueAt(int modelRowIndex, int modelColumnIndex) {
    switch (modelColumnIndex) {
      case SELECTED_MODEL_COLUMN_INDEX:
        return myTable.isRowSelected(myTable.convertRowIndexToView(modelRowIndex));
      case TYPE_MODEL_COLUMN_INDEX:
        return myDevices.get(modelRowIndex).getIcon();
      case DEVICE_MODEL_COLUMN_INDEX:
        return myDevices.get(modelRowIndex).getName();
      case SERIAL_NUMBER_MODEL_COLUMN_INDEX:
        return getSerialNumber(myDevices.get(modelRowIndex));
      case SNAPSHOT_MODEL_COLUMN_INDEX:
        return Strings.nullToEmpty(Snapshot.getText(myDevices.get(modelRowIndex).getSnapshot(), FileSystems.getDefault()));
      case ISSUE_MODEL_COLUMN_INDEX:
        return Strings.nullToEmpty(myDevices.get(modelRowIndex).getValidityReason());
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @NotNull
  private Object getSerialNumber(@NotNull Device device) {
    if (!(device instanceof PhysicalDevice)) {
      return "";
    }

    if (myDeviceNameMultiset.count(device.getName()) != 1) {
      return device.getKey().getDeviceKey();
    }

    return "";
  }

  @Override
  public void setValueAt(@NotNull Object value, int modelRowIndex, int modelColumnIndex) {
    int viewRowIndex = myTable.convertRowIndexToView(modelRowIndex);

    if ((boolean)value) {
      myTable.addRowSelectionInterval(viewRowIndex, viewRowIndex);
    }
    else {
      myTable.removeRowSelectionInterval(viewRowIndex, viewRowIndex);
    }

    fireTableCellUpdated(modelRowIndex, modelColumnIndex);
  }
}
