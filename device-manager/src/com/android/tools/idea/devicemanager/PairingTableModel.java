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
package com.android.tools.idea.devicemanager;

import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;

final class PairingTableModel extends AbstractTableModel {
  private static final int DEVICE_MODEL_COLUMN_INDEX = 0;
  private static final int STATUS_MODEL_COLUMN_INDEX = 1;

  private final @NotNull List<@NotNull Pairing> myPairings;

  PairingTableModel(@NotNull List<@NotNull Pairing> pairings) {
    myPairings = pairings;
  }

  @Override
  public int getRowCount() {
    return myPairings.size();
  }

  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  public @NotNull String getColumnName(int modelColumnIndex) {
    switch (modelColumnIndex) {
      case DEVICE_MODEL_COLUMN_INDEX:
        return "Device";
      case STATUS_MODEL_COLUMN_INDEX:
        return "Status";
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @Override
  public @NotNull Class<?> getColumnClass(int modelColumnIndex) {
    switch (modelColumnIndex) {
      case DEVICE_MODEL_COLUMN_INDEX:
        return Device.class;
      case STATUS_MODEL_COLUMN_INDEX:
        return Object.class;
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }

  @Override
  public @NotNull Object getValueAt(int modelRowIndex, int modelColumnIndex) {
    switch (modelColumnIndex) {
      case DEVICE_MODEL_COLUMN_INDEX:
        return myPairings.get(modelRowIndex).getOtherDevice();
      case STATUS_MODEL_COLUMN_INDEX:
        return myPairings.get(modelRowIndex).getStatus();
      default:
        throw new AssertionError(modelColumnIndex);
    }
  }
}
