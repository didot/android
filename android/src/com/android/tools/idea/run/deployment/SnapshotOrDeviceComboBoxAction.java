/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.popup.PopupFactoryImpl.ActionGroupPopup;
import icons.AndroidIcons;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

final class SnapshotOrDeviceComboBoxAction extends ComboBoxAction {
  private final Supplier<Boolean> mySelectSnapshotDeviceComboBoxVisible;
  private final AsyncDevicesGetter myDevicesGetter;

  private List<Device> myDevices;
  private Device mySelectedDevice;

  @SuppressWarnings("unused")
  private SnapshotOrDeviceComboBoxAction() {
    this(() -> StudioFlags.SELECT_SNAPSHOT_DEVICE_COMBO_BOX_VISIBLE.get(), new AsyncDevicesGetter(ApplicationManager.getApplication()));
  }

  @VisibleForTesting
  SnapshotOrDeviceComboBoxAction(@NotNull Supplier<Boolean> selectSnapshotDeviceComboBoxVisible,
                                 @NotNull AsyncDevicesGetter devicesGetter) {
    mySelectSnapshotDeviceComboBoxVisible = selectSnapshotDeviceComboBoxVisible;
    myDevicesGetter = devicesGetter;
  }

  @VisibleForTesting
  List<Device> getDevices() {
    return myDevices;
  }

  @VisibleForTesting
  Device getSelectedDevice() {
    return mySelectedDevice;
  }

  void setSelectedDevice(@NotNull Device selectedDevice) {
    mySelectedDevice = selectedDevice;
  }

  @NotNull
  @Override
  protected ComboBoxButton createComboBoxButton(@NotNull Presentation presentation) {
    return new ComboBoxButton(presentation) {
      @Override
      protected JBPopup createPopup(@NotNull Runnable runnable) {
        DataContext context = getDataContext();

        ActionGroup group = createPopupActionGroup(this, context);
        boolean show = shouldShowDisabledActions();
        int count = getMaxRows();
        Condition<AnAction> condition = getPreselectCondition();

        JBPopup popup = new ActionGroupPopup(null, group, context, false, true, show, false, runnable, count, condition, null, true);
        popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));

        return popup;
      }
    };
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup();

    Collection<AnAction> actions = newSnapshotOrDeviceActions();
    group.addAll(actions);

    if (!actions.isEmpty()) {
      group.addSeparator();
    }

    group.add(newOpenAvdManagerAction());
    AnAction action = getTroubleshootDeviceConnectionsAction();

    if (action == null) {
      return group;
    }

    group.addSeparator();
    group.add(action);

    return group;
  }

  @NotNull
  @VisibleForTesting
  Collection<AnAction> newSnapshotOrDeviceActions() {
    Collection<VirtualDevice> virtualDevices = new ArrayList<>(myDevices.size());
    Collection<Device> physicalDevices = new ArrayList<>(myDevices.size());

    myDevices.forEach(device -> {
      if (device instanceof VirtualDevice) {
        virtualDevices.add((VirtualDevice)device);
      }
      else if (device instanceof PhysicalDevice) {
        physicalDevices.add(device);
      }
      else {
        assert false;
      }
    });

    Collection<AnAction> actions = new ArrayList<>(virtualDevices.size() + 1 + physicalDevices.size());

    virtualDevices.stream()
                  .map(device -> new SnapshotActionGroup(device, this))
                  .forEach(actions::add);

    if (!virtualDevices.isEmpty() && !physicalDevices.isEmpty()) {
      actions.add(Separator.create());
    }

    physicalDevices.stream()
                   .map(device -> new SelectDeviceAction(device, this))
                   .forEach(actions::add);

    return actions;
  }

  @NotNull
  private static AnAction newOpenAvdManagerAction() {
    AnAction action = new RunAndroidAvdManagerAction();

    Presentation presentation = action.getTemplatePresentation();
    presentation.setIcon(AndroidIcons.AvdManager);
    presentation.setText("Open AVD Manager");

    return action;
  }

  @Nullable
  private static AnAction getTroubleshootDeviceConnectionsAction() {
    AnAction action = ActionManager.getInstance().getAction("DeveloperServices.ConnectionAssistant");

    if (action == null) {
      return null;
    }

    Presentation presentation = action.getTemplatePresentation();

    presentation.setIcon(null);
    presentation.setText("Troubleshoot device connections");

    return action;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getProject();

    if (project == null) {
      return;
    }

    Presentation presentation = event.getPresentation();

    if (!mySelectSnapshotDeviceComboBoxVisible.get()) {
      presentation.setVisible(false);
      return;
    }

    presentation.setVisible(true);
    myDevices = myDevicesGetter.get(project);

    if (myDevices.isEmpty()) {
      mySelectedDevice = null;

      presentation.setIcon(null);
      presentation.setText("No devices");

      return;
    }

    updateSelectedDevice();

    presentation.setIcon(mySelectedDevice.getIcon());
    presentation.setText(mySelectedDevice.getName());
  }

  private void updateSelectedDevice() {
    if (mySelectedDevice == null) {
      mySelectedDevice = myDevices.get(0);
      return;
    }

    Object selectedName = mySelectedDevice.getName();

    Optional<Device> selectedDevice = myDevices.stream()
                                               .filter(device -> device.getName().equals(selectedName))
                                               .findFirst();

    mySelectedDevice = selectedDevice.orElseGet(() -> myDevices.get(0));
  }
}
