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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.ApiLevelComparator;
import com.android.tools.idea.avdmanager.AvdUiAction.AvdInfoProvider;
import com.android.tools.idea.avdmanager.CreateAvdAction;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import java.awt.Component;
import java.util.Collections;
import java.util.Comparator;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import javax.swing.DefaultRowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.jetbrains.annotations.NotNull;

/**
 * TableView that adjusts column widths automatically to not cut off table cell content
 */
public final class VirtualTableView extends TableView<AvdInfo> {
  VirtualTableView(@NotNull ListTableModel<@NotNull AvdInfo> model, @NotNull AvdInfoProvider avdInfoProvider) {
    super(model);
    getTableHeader().setResizingAllowed(false);

    //noinspection DialogTitleCapitalization
    getEmptyText()
      .appendLine("No virtual devices added. Create a virtual device to test")
      .appendLine("applications without owning a physical device.")
      .appendLine("Create virtual device", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, new CreateAvdAction(avdInfoProvider));
  }

  void setRowSorter() {
    DefaultRowSorter<TableModel, Integer> sorter = new TableRowSorter<>(dataModel);

    sorter.setComparator(0, Comparator.comparing(AvdInfo::getDisplayName));
    sorter.setComparator(1, new ApiLevelComparator().reversed());
    sorter.setSortable(3, false);
    sorter.setSortKeys(Collections.singletonList(new SortKey(0, SortOrder.ASCENDING)));

    setRowSorter(sorter);
  }

  void setWidths() {
    IntStream.range(1, columnModel.getColumnCount())
      .forEach(viewColumnIndex -> {
        int preferredWidth = getPreferredColumnWidth(viewColumnIndex);
        TableColumn column = columnModel.getColumn(viewColumnIndex);
        column.setMinWidth(preferredWidth);
        column.setMaxWidth(preferredWidth);
        column.setPreferredWidth(preferredWidth);
      });
  }

  private int getPreferredColumnWidth(int viewColumnIndex) {
    OptionalInt width = IntStream.range(-1, getRowCount())
      .map(rowIndex -> getPreferredCellWidth(rowIndex, viewColumnIndex))
      .max();

    int minWidth = JBUIScale.scale(65);

    if (!width.isPresent()) {
      return minWidth;
    }

    return Math.max(width.getAsInt(), minWidth);
  }

  private int getPreferredCellWidth(int viewRowIndex, int viewColumnIndex) {
    Component component;
    if (viewRowIndex == -1) {
      component = getTableHeader().getDefaultRenderer().getTableCellRendererComponent(
        this, getColumnName(viewColumnIndex), false, false, -1, viewColumnIndex);
    }
    else {
      component = prepareRenderer(getCellRenderer(viewRowIndex, viewColumnIndex), viewRowIndex, viewColumnIndex);
    }
    return component.getPreferredSize().width + JBUI.scale(8);
  }
}
