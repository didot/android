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

import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.android.tools.idea.devicemanager.IconButtonTableCellEditor;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTableModel.LaunchInEmulatorValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.EdtExecutorService;
import icons.StudioIcons;
import java.awt.Component;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO The EDT, application pool threads, and who knows what other ones are accessing the AvdInfo. I don't like that.
final class LaunchInEmulatorButtonTableCellEditor extends IconButtonTableCellEditor {
  private final @Nullable Project myProject;
  private final @NotNull Supplier<@NotNull AvdManagerConnection> myGetDefaultAvdManagerConnection;
  private final @NotNull BiFunction<@NotNull Component, @Nullable Project, @NotNull FutureCallback<@Nullable Object>> myNewSetEnabled;

  private VirtualDevice myDevice;

  LaunchInEmulatorButtonTableCellEditor(@Nullable Project project) {
    this(project, AvdManagerConnection::getDefaultAvdManagerConnection, SetEnabled::new);
  }

  @VisibleForTesting
  LaunchInEmulatorButtonTableCellEditor(@Nullable Project project,
                                        @NotNull Supplier<@NotNull AvdManagerConnection> getDefaultAvdManagerConnection,
                                        @NotNull BiFunction<@NotNull Component, @Nullable Project, @NotNull FutureCallback<@Nullable Object>> newSetEnabled) {
    super(StudioIcons.Avd.RUN, LaunchInEmulatorValue.INSTANCE, "Launch this AVD in the emulator");

    myProject = project;
    myGetDefaultAvdManagerConnection = getDefaultAvdManagerConnection;
    myNewSetEnabled = newSetEnabled;

    myButton.addActionListener(actionEvent -> {
      if (myDevice.isOnline()) {
        stop();
      }
      else {
        launch(project);
      }
    });
  }

  private void stop() {
    DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
      .setKind(EventKind.VIRTUAL_STOP_ACTION)
      .build();

    DeviceManagerUsageTracker.log(event);
    myButton.setEnabled(false);

    ListenableFuture<Void> future = myGetDefaultAvdManagerConnection.get().stopAvdAsync(myDevice.getAvdInfo());
    Futures.addCallback(future, myNewSetEnabled.apply(myButton, myProject), EdtExecutorService.getInstance());
  }

  private void launch(@Nullable Project project) {
    DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
      .setKind(EventKind.VIRTUAL_LAUNCH_ACTION)
      .build();

    DeviceManagerUsageTracker.log(event);
    myButton.setEnabled(false);

    ListenableFuture<IDevice> future = myGetDefaultAvdManagerConnection.get().startAvd(project, myDevice.getAvdInfo());
    Futures.addCallback(future, myNewSetEnabled.apply(myButton, myProject), EdtExecutorService.getInstance());
  }

  @VisibleForTesting
  static final class SetEnabled implements FutureCallback<Object> {
    private final @NotNull Component myButton;
    private final @Nullable Project myProject;

    @VisibleForTesting
    SetEnabled(@NotNull Component button, @Nullable Project project) {
      myButton = button;
      myProject = project;
    }

    @Override
    public void onSuccess(@Nullable Object result) {
      myButton.setEnabled(true);
    }

    @Override
    public void onFailure(@NotNull Throwable throwable) {
      myButton.setEnabled(true);
      VirtualTabMessages.showErrorDialog(throwable, myProject);
    }
  }

  @Override
  public @NotNull Component getTableCellEditorComponent(@NotNull JTable table,
                                                        @NotNull Object value,
                                                        boolean selected,
                                                        int viewRowIndex,
                                                        int viewColumnIndex) {
    myDevice = ((VirtualDeviceTable)table).getDeviceAt(viewRowIndex);

    if (myDevice.isOnline()) {
      myButton.setDefaultIcon(StudioIcons.Avd.STOP);
      myButton.setEnabled(true);
      myButton.setToolTipText("Stop the emulator running this AVD");
    }
    else {
      myButton.setDefaultIcon(StudioIcons.Avd.RUN);
      myButton.setEnabled(myDevice.getAvdInfo().getStatus().equals(AvdStatus.OK));
      myButton.setToolTipText("Launch this AVD in the emulator");
    }

    super.getTableCellEditorComponent(table, value, selected, viewRowIndex, viewColumnIndex);
    return myButton;
  }
}
