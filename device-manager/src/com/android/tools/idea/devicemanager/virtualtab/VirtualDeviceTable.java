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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowButtonTableCellEditor;
import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowButtonTableCellRenderer;
import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowValue;
import com.android.tools.idea.devicemanager.ApiTableCellRenderer;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.android.tools.idea.devicemanager.DeviceTable;
import com.android.tools.idea.devicemanager.IconButtonTableCellRenderer;
import com.android.tools.idea.devicemanager.MergedTableColumn;
import com.android.tools.idea.devicemanager.PopUpMenuValue;
import com.android.tools.idea.devicemanager.Tables;
import com.android.tools.idea.devicemanager.legacy.AvdUiAction.AvdInfoProvider;
import com.android.tools.idea.devicemanager.legacy.CreateAvdAction;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTableModel.EditValue;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTableModel.LaunchInEmulatorValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.swing.DefaultRowSorter;
import javax.swing.JComponent;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO Stop implementing AvdInfoProvider
public final class VirtualDeviceTable extends DeviceTable<VirtualDevice> implements AvdInfoProvider {
  private final @NotNull VirtualDevicePanel myPanel;
  private final @NotNull VirtualDeviceAsyncSupplier myAsyncSupplier;
  private final @NotNull NewSetDevices myNewSetDevices;

  @VisibleForTesting
  static final class SetDevices implements FutureCallback<List<VirtualDevice>> {
    private final @NotNull VirtualDeviceTableModel myModel;

    @VisibleForTesting
    SetDevices(@NotNull VirtualDeviceTableModel model) {
      myModel = model;
    }

    @Override
    public void onSuccess(@Nullable List<@NotNull VirtualDevice> devices) {
      assert devices != null;
      myModel.setDevices(devices);

      DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
        .setKind(DeviceManagerEvent.EventKind.VIRTUAL_DEVICE_COUNT)
        .setVirtualDeviceCount(devices.size())
        .build();

      DeviceManagerUsageTracker.log(event);
    }

    @Override
    public void onFailure(@NotNull Throwable throwable) {
      Logger.getInstance(VirtualDeviceTable.class).warn(throwable);
    }
  }

  @VisibleForTesting
  interface NewSetDevices {
    @NotNull FutureCallback<@NotNull List<@NotNull VirtualDevice>> apply(@NotNull VirtualDeviceTableModel model);
  }

  VirtualDeviceTable(@NotNull VirtualDevicePanel panel) {
    this(panel, new VirtualDeviceAsyncSupplier(), SetDevices::new);
  }

  @VisibleForTesting
  VirtualDeviceTable(@NotNull VirtualDevicePanel panel,
                     @NotNull VirtualDeviceAsyncSupplier asyncSupplier,
                     @NotNull NewSetDevices newSetDevices) {
    super(new VirtualDeviceTableModel(), VirtualDevice.class, VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX);

    myPanel = panel;
    myAsyncSupplier = asyncSupplier;
    myNewSetDevices = newSetDevices;

    dataModel.addTableModelListener(event -> sizeWidthsToFit());

    Project project = panel.getProject();

    setDefaultEditor(LaunchInEmulatorValue.class, new LaunchInEmulatorButtonTableCellEditor(project));

    setDefaultEditor(ActivateDeviceFileExplorerWindowValue.class,
                     new ActivateDeviceFileExplorerWindowButtonTableCellEditor<>(project,
                                                                                 this,
                                                                                 EventKind.VIRTUAL_DEVICE_FILE_EXPLORER_ACTION));

    setDefaultEditor(EditValue.class, new EditButtonTableCellEditor(panel));
    setDefaultEditor(PopUpMenuValue.class, new VirtualDevicePopUpMenuButtonTableCellEditor(panel));

    setDefaultRenderer(Device.class, new VirtualDeviceTableCellRenderer());
    setDefaultRenderer(AndroidVersion.class, new ApiTableCellRenderer());
    setDefaultRenderer(Long.class, new SizeOnDiskTableCellRenderer());
    setDefaultRenderer(LaunchInEmulatorValue.class, new LaunchInEmulatorButtonTableCellRenderer());

    setDefaultRenderer(ActivateDeviceFileExplorerWindowValue.class,
                       new ActivateDeviceFileExplorerWindowButtonTableCellRenderer<>(project, this));

    setDefaultRenderer(EditValue.class, new IconButtonTableCellRenderer(AllIcons.Actions.Edit, "Edit this AVD"));
    setDefaultRenderer(PopUpMenuValue.class, new IconButtonTableCellRenderer(AllIcons.Actions.More));

    setRowSorter(newRowSorter(dataModel));
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setShowGrid(false);

    // noinspection DialogTitleCapitalization
    getEmptyText()
      .appendLine("No virtual devices added. Create a virtual device to test")
      .appendLine("applications without owning a physical device.")
      .appendLine("Create virtual device", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, new CreateAvdAction(this));

    refreshAvds();
  }

  private void sizeWidthsToFit() {
    getRowSorter().allRowsChanged();
    columnModel.getColumn(deviceViewColumnIndex()).setMinWidth(JBUIScale.scale(65));

    Tables.setWidths(columnModel.getColumn(apiViewColumnIndex()),
                     Tables.getPreferredColumnWidth(this, apiViewColumnIndex(), JBUIScale.scale(65)),
                     JBUIScale.scale(20));

    Tables.setWidths(columnModel.getColumn(sizeOnDiskViewColumnIndex()),
                     Tables.getPreferredColumnWidth(this, sizeOnDiskViewColumnIndex(), JBUIScale.scale(65)),
                     JBUIScale.scale(20));

    Tables.setWidths(columnModel.getColumn(launchInEmulatorViewColumnIndex()),
                     Tables.getPreferredColumnWidth(this, launchInEmulatorViewColumnIndex(), 0));

    Tables.setWidths(columnModel.getColumn(activateDeviceFileExplorerWindowViewColumnIndex()),
                     Tables.getPreferredColumnWidth(this, activateDeviceFileExplorerWindowViewColumnIndex(), 0));

    Tables.setWidths(columnModel.getColumn(editViewColumnIndex()), Tables.getPreferredColumnWidth(this, editViewColumnIndex(), 0));

    Tables.setWidths(columnModel.getColumn(popUpMenuViewColumnIndex()),
                     Tables.getPreferredColumnWidth(this, popUpMenuViewColumnIndex(), 0));
  }

  private static @NotNull RowSorter<@NotNull TableModel> newRowSorter(@NotNull TableModel model) {
    DefaultRowSorter<TableModel, Integer> sorter = new TableRowSorter<>(model);

    sorter.setComparator(VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX, Comparator.comparing(VirtualDevice::getName));
    sorter.setComparator(VirtualDeviceTableModel.API_MODEL_COLUMN_INDEX, Comparator.naturalOrder().reversed());
    sorter.setComparator(VirtualDeviceTableModel.SIZE_ON_DISK_MODEL_COLUMN_INDEX, Comparator.naturalOrder().reversed());
    sorter.setSortable(VirtualDeviceTableModel.LAUNCH_IN_EMULATOR_MODEL_COLUMN_INDEX, false);
    sorter.setSortable(VirtualDeviceTableModel.ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX, false);
    sorter.setSortable(VirtualDeviceTableModel.EDIT_MODEL_COLUMN_INDEX, false);
    sorter.setSortable(VirtualDeviceTableModel.POP_UP_MENU_MODEL_COLUMN_INDEX, false);
    VirtualTabState tabState = VirtualTabPersistentStateComponent.getInstance().getState();
    sorter.setSortKeys(Collections.singletonList(new SortKey(tabState.getSortColumn(), tabState.getSortOrder())));

    sorter.addRowSorterListener(event -> {
      List<? extends SortKey> keys = sorter.getSortKeys();
      if (!keys.isEmpty()) {
        SortKey key = keys.get(0);
        VirtualTabPersistentStateComponent.getInstance().loadState(new VirtualTabState(key.getColumn(), key.getSortOrder()));
      }
    });

    return sorter;
  }

  @Override
  public @NotNull VirtualDeviceTableModel getModel() {
    return (VirtualDeviceTableModel)dataModel;
  }

  @Override
  public @Nullable AvdInfo getAvdInfo() {
    return getSelectedDevice().map(VirtualDevice::getAvdInfo).orElse(null);
  }

  @NotNull Optional<@NotNull VirtualDevice> getSelectedDevice() {
    int viewRowIndex = getSelectedRow();

    if (viewRowIndex == -1) {
      return Optional.empty();
    }

    return Optional.of(getDeviceAt(viewRowIndex));
  }

  @Override
  protected @NotNull JTableHeader createDefaultTableHeader() {
    TableColumnModel model = new DefaultTableColumnModel();

    model.addColumn(columnModel.getColumn(deviceViewColumnIndex()));
    model.addColumn(columnModel.getColumn(apiViewColumnIndex()));
    model.addColumn(columnModel.getColumn(sizeOnDiskViewColumnIndex()));

    Collection<TableColumn> columns = Arrays.asList(columnModel.getColumn(launchInEmulatorViewColumnIndex()),
                                                    columnModel.getColumn(activateDeviceFileExplorerWindowViewColumnIndex()),
                                                    columnModel.getColumn(editViewColumnIndex()),
                                                    columnModel.getColumn(popUpMenuViewColumnIndex()));

    TableColumn column = new MergedTableColumn(columns);
    column.setHeaderValue("Actions");

    model.addColumn(column);

    JTableHeader header = super.createDefaultTableHeader();
    header.setColumnModel(model);
    header.setReorderingAllowed(false);
    header.setResizingAllowed(false);

    return header;
  }

  private int apiViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.API_MODEL_COLUMN_INDEX);
  }

  int sizeOnDiskViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.SIZE_ON_DISK_MODEL_COLUMN_INDEX);
  }

  private int launchInEmulatorViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.LAUNCH_IN_EMULATOR_MODEL_COLUMN_INDEX);
  }

  private int activateDeviceFileExplorerWindowViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX);
  }

  private int editViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.EDIT_MODEL_COLUMN_INDEX);
  }

  private int popUpMenuViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.POP_UP_MENU_MODEL_COLUMN_INDEX);
  }

  @Override
  public void refreshAvds() {
    FutureUtils.addCallback(myAsyncSupplier.get(), EdtExecutorService.getInstance(), myNewSetDevices.apply(getModel()));
  }

  @Override
  public void refreshAvdsAndSelect(@Nullable AvdInfo device) {
    refreshAvds();
  }

  @Override
  public @Nullable Project getProject() {
    return myPanel.getProject();
  }

  @Override
  public @NotNull JComponent getAvdProviderComponent() {
    return this;
  }
}
