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

import com.android.tools.idea.avdmanager.ApiLevelComparator;
import com.android.tools.idea.devicemanager.ActionsTableCellEditorMouseMotionListener;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.Table;
import com.android.tools.idea.devicemanager.Tables;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.Actions;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.ActivateDeviceFileExplorerWindowValue;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.PopUpMenuValue;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.RemoveValue;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.table.JBTable;
import java.awt.Point;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.ActionMap;
import javax.swing.DefaultRowSorter;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.jetbrains.annotations.NotNull;

final class PhysicalDeviceTable extends JBTable implements Table {
  PhysicalDeviceTable(@NotNull PhysicalDevicePanel panel) {
    this(panel, new PhysicalDeviceTableModel());
  }

  @VisibleForTesting
  PhysicalDeviceTable(@NotNull PhysicalDevicePanel panel, @NotNull PhysicalDeviceTableModel model) {
    super(model);
    model.addTableModelListener(event -> sizeApiTypeAndActionsColumnWidthsToFit());

    if (PhysicalDeviceTableModel.SPLIT_ACTIONS_ENABLED) {
      Project project = panel.getProject();
      assert project != null;

      setDefaultEditor(ActivateDeviceFileExplorerWindowValue.class, new ActivateDeviceFileExplorerWindowButtonTableCellEditor(project));
      setDefaultEditor(RemoveValue.class, new RemoveButtonTableCellEditor(panel));
      setDefaultEditor(PopUpMenuValue.class, new PhysicalDevicePopUpMenuButtonTableCellEditor(project));

      setDefaultRenderer(ActivateDeviceFileExplorerWindowValue.class, new ActivateDeviceFileExplorerWindowButtonTableCellRenderer());
      setDefaultRenderer(RemoveValue.class, new RemoveButtonTableCellRenderer());
      setDefaultRenderer(PopUpMenuValue.class, new IconButtonTableCellRenderer(AllIcons.Actions.More));
    }
    else {
      setDefaultEditor(Actions.class, new ActionsTableCellEditor(panel));
      setDefaultRenderer(Actions.class, new ActionsTableCellRenderer());
    }

    setDefaultRenderer(Device.class, new PhysicalDeviceTableCellRenderer());
    setRowSorter(newRowSorter(model));
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setShowGrid(false);

    if (!PhysicalDeviceTableModel.SPLIT_ACTIONS_ENABLED) {
      ActionMap map = getActionMap();

      map.put("selectNextColumn", new SelectNextColumnAction());
      map.put("selectNextColumnCell", new SelectNextColumnCellAction());
      map.put("selectNextRow", new SelectNextRowAction());
      map.put("selectPreviousColumn", new SelectPreviousColumnAction());
      map.put("selectPreviousColumnCell", new SelectPreviousColumnCellAction());
      map.put("selectPreviousRow", new SelectPreviousRowAction());
    }

    getEmptyText().setText("No physical devices added. Connect a device via USB cable.");

    if (!PhysicalDeviceTableModel.SPLIT_ACTIONS_ENABLED) {
      addMouseMotionListener(new ActionsTableCellEditorMouseMotionListener(this));
    }
  }

  private void sizeApiTypeAndActionsColumnWidthsToFit() {
    getRowSorter().allRowsChanged();

    Tables.sizeWidthToFit(this, apiViewColumnIndex());
    Tables.sizeWidthToFit(this, typeViewColumnIndex());

    if (PhysicalDeviceTableModel.SPLIT_ACTIONS_ENABLED) {
      Tables.sizeWidthToFit(this, activateDeviceFileExplorerWindowViewColumnIndex(), 0);
      Tables.sizeWidthToFit(this, removeViewColumnIndex(), 0);
      Tables.sizeWidthToFit(this, popUpMenuViewColumnIndex(), 0);
    }
    else {
      Tables.sizeWidthToFit(this, actionsViewColumnIndex());
    }
  }

  private static @NotNull RowSorter<@NotNull TableModel> newRowSorter(@NotNull TableModel model) {
    DefaultRowSorter<TableModel, Integer> sorter = new TableRowSorter<>(model);

    sorter.setComparator(PhysicalDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX, Comparator.comparing(PhysicalDevice::getName));
    sorter.setComparator(PhysicalDeviceTableModel.API_MODEL_COLUMN_INDEX, new ApiLevelComparator().reversed());
    sorter.setComparator(PhysicalDeviceTableModel.TYPE_MODEL_COLUMN_INDEX, Comparator.naturalOrder().reversed());
    sorter.setSortable(PhysicalDeviceTableModel.ACTIONS_MODEL_COLUMN_INDEX, false);
    sorter.setSortKeys(Collections.singletonList(new SortKey(PhysicalDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX, SortOrder.ASCENDING)));

    return sorter;
  }

  @NotNull Optional<@NotNull PhysicalDevice> getSelectedDevice() {
    int viewRowIndex = getSelectedRow();

    if (viewRowIndex == -1) {
      return Optional.empty();
    }

    return Optional.of(getDeviceAt(viewRowIndex));
  }

  @NotNull PhysicalDevice getDeviceAt(int viewRowIndex) {
    return (PhysicalDevice)getValueAt(viewRowIndex, deviceViewColumnIndex());
  }

  @NotNull ActionsTableCellEditor getActionsCellEditor() {
    return (ActionsTableCellEditor)getCellEditor();
  }

  @VisibleForTesting
  @NotNull Object getData() {
    return IntStream.range(0, getRowCount())
      .mapToObj(this::getRowAt)
      .collect(Collectors.toList());
  }

  @VisibleForTesting
  private @NotNull Object getRowAt(int viewRowIndex) {
    return IntStream.range(0, getColumnCount())
      .mapToObj(viewColumnIndex -> getValueAt(viewRowIndex, viewColumnIndex))
      .collect(Collectors.toList());
  }

  @Override
  protected @NotNull JTableHeader createDefaultTableHeader() {
    JTableHeader header = super.createDefaultTableHeader();

    if (PhysicalDeviceTableModel.SPLIT_ACTIONS_ENABLED) {
      TableColumnModel model = new DefaultTableColumnModel();

      model.addColumn(columnModel.getColumn(deviceViewColumnIndex()));
      model.addColumn(columnModel.getColumn(apiViewColumnIndex()));
      model.addColumn(columnModel.getColumn(typeViewColumnIndex()));

      Collection<TableColumn> columns = Arrays.asList(columnModel.getColumn(activateDeviceFileExplorerWindowViewColumnIndex()),
                                                      columnModel.getColumn(removeViewColumnIndex()),
                                                      columnModel.getColumn(popUpMenuViewColumnIndex()));

      TableColumn column = new MergedTableColumn(columns);
      column.setHeaderValue("Actions");

      model.addColumn(column);
      header.setColumnModel(model);
    }

    header.setReorderingAllowed(false);
    header.setResizingAllowed(false);

    return header;
  }

  private int deviceViewColumnIndex() {
    return convertColumnIndexToView(PhysicalDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX);
  }

  private int apiViewColumnIndex() {
    return convertColumnIndexToView(PhysicalDeviceTableModel.API_MODEL_COLUMN_INDEX);
  }

  private int typeViewColumnIndex() {
    return convertColumnIndexToView(PhysicalDeviceTableModel.TYPE_MODEL_COLUMN_INDEX);
  }

  private int actionsViewColumnIndex() {
    return convertColumnIndexToView(PhysicalDeviceTableModel.ACTIONS_MODEL_COLUMN_INDEX);
  }

  private int activateDeviceFileExplorerWindowViewColumnIndex() {
    return convertColumnIndexToView(PhysicalDeviceTableModel.ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX);
  }

  private int removeViewColumnIndex() {
    return convertColumnIndexToView(PhysicalDeviceTableModel.REMOVE_MODEL_COLUMN_INDEX);
  }

  private int popUpMenuViewColumnIndex() {
    return convertColumnIndexToView(PhysicalDeviceTableModel.POP_UP_MENU_MODEL_COLUMN_INDEX);
  }

  @Override
  public @NotNull PhysicalDeviceTableModel getModel() {
    return (PhysicalDeviceTableModel)dataModel;
  }

  @Override
  public boolean isActionsColumn(int viewColumnIndex) {
    return getColumnClass(viewColumnIndex).equals(Actions.class);
  }

  @Override
  public int viewRowIndexAtPoint(@NotNull Point point) {
    return rowAtPoint(point);
  }

  @Override
  public int viewColumnIndexAtPoint(@NotNull Point point) {
    return columnAtPoint(point);
  }

  @Override
  public int getEditingViewRowIndex() {
    return editingRow;
  }
}
