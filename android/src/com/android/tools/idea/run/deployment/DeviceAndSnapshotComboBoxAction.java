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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.DefaultExecutionTarget;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.ui.popup.PopupFactoryImpl.ActionGroupPopup;
import icons.StudioIcons;
import java.awt.Dimension;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeviceAndSnapshotComboBoxAction extends ComboBoxAction {
  private static final String SELECTED_DEVICE = "DeviceAndSnapshotComboBoxAction.selectedDevice";
  private static final Key<Instant> SELECTION_TIME = new Key<>("DeviceAndSnapshotComboBoxAction.selectionTime");

  private final Supplier<Boolean> mySelectDeviceSnapshotComboBoxVisible;
  private final Supplier<Boolean> mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

  private final Function<Project, AsyncDevicesGetter> myDevicesGetterGetter;

  private final AnAction myRunOnMultipleDevicesAction;
  private final AnAction myOpenAvdManagerAction;

  private final Clock myClock;

  private List<Device> myDevices;
  private String mySelectedSnapshot;

  @SuppressWarnings("unused")
  private DeviceAndSnapshotComboBoxAction() {
    this(() -> StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_VISIBLE.get(),
         () -> StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED.get(),
         project -> ServiceManager.getService(project, AsyncDevicesGetter.class),
         Clock.systemDefaultZone());
  }

  @VisibleForTesting
  DeviceAndSnapshotComboBoxAction(@NotNull Supplier<Boolean> selectDeviceSnapshotComboBoxVisible,
                                  @NotNull Supplier<Boolean> selectDeviceSnapshotComboBoxSnapshotsEnabled,
                                  @NotNull Function<Project, AsyncDevicesGetter> devicesGetterGetter,
                                  @NotNull Clock clock) {
    mySelectDeviceSnapshotComboBoxVisible = selectDeviceSnapshotComboBoxVisible;
    mySelectDeviceSnapshotComboBoxSnapshotsEnabled = selectDeviceSnapshotComboBoxSnapshotsEnabled;

    myDevicesGetterGetter = devicesGetterGetter;

    myRunOnMultipleDevicesAction = new RunOnMultipleDevicesAction();
    myOpenAvdManagerAction = new RunAndroidAvdManagerAction();

    Presentation presentation = myOpenAvdManagerAction.getTemplatePresentation();

    presentation.setIcon(StudioIcons.Shell.Toolbar.DEVICE_MANAGER);
    presentation.setText("Open AVD Manager");

    myClock = clock;

    myDevices = Collections.emptyList();
  }

  boolean areSnapshotsEnabled() {
    return mySelectDeviceSnapshotComboBoxSnapshotsEnabled.get();
  }

  @NotNull
  @VisibleForTesting
  AnAction getRunOnMultipleDevicesAction() {
    return myRunOnMultipleDevicesAction;
  }

  @NotNull
  @VisibleForTesting
  AnAction getOpenAvdManagerAction() {
    return myOpenAvdManagerAction;
  }

  @NotNull
  @VisibleForTesting
  public List<Device> getDevices() {
    return myDevices;
  }

  @Nullable
  Device getSelectedDevice(@NotNull Project project) {
    if (myDevices.isEmpty()) {
      return null;
    }

    Object key = PropertiesComponent.getInstance(project).getValue(SELECTED_DEVICE);

    Optional<Device> optionalSelectedDevice = myDevices.stream()
      .filter(device -> device.getKey().equals(key))
      .findFirst();

    if (!optionalSelectedDevice.isPresent()) {
      return myDevices.get(0);
    }

    Device selectedDevice = optionalSelectedDevice.get();

    if (selectedDevice.isConnected()) {
      return selectedDevice;
    }

    Optional<Device> optionalConnectedDevice = myDevices.stream()
      .filter(Device::isConnected)
      .findFirst();

    if (optionalConnectedDevice.isPresent()) {
      Instant selectionTime = project.getUserData(SELECTION_TIME);
      assert selectionTime != null : "selected device \"" + selectedDevice + "\" has a null selection time";

      Device connectedDevice = optionalConnectedDevice.get();

      Instant connectionTime = connectedDevice.getConnectionTime();
      assert connectionTime != null : "connected device \"" + connectedDevice + "\" has a null connection time";

      if (selectionTime.isBefore(connectionTime)) {
        return connectedDevice;
      }
    }

    return selectedDevice;
  }

  void setSelectedDevice(@NotNull Project project, @Nullable Device selectedDevice) {
    PropertiesComponent properties = PropertiesComponent.getInstance(project);

    if (selectedDevice == null) {
      properties.unsetValue(SELECTED_DEVICE);
      project.putUserData(SELECTION_TIME, null);
    }
    else {
      properties.setValue(SELECTED_DEVICE, selectedDevice.getKey());
      project.putUserData(SELECTION_TIME, myClock.instant());
    }

    updateExecutionTargetManager(project, selectedDevice);
  }

  @Nullable
  String getSelectedSnapshot() {
    return mySelectedSnapshot;
  }

  void setSelectedSnapshot(@Nullable String selectedSnapshot) {
    mySelectedSnapshot = selectedSnapshot;
  }

  @NotNull
  @Override
  protected ComboBoxButton createComboBoxButton(@NotNull Presentation presentation) {
    ComboBoxButton button = new ComboBoxButton(presentation) {
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

    button.setName("deviceAndSnapshotComboBoxButton");
    return button;
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
    DefaultActionGroup group = new DefaultActionGroup();

    Project project = context.getData(CommonDataKeys.PROJECT);
    assert project != null;

    Collection<AnAction> actions = newSelectDeviceAndSnapshotActions(project);
    group.addAll(actions);

    if (!actions.isEmpty()) {
      group.addSeparator();
    }

    group.add(myRunOnMultipleDevicesAction);
    group.add(myOpenAvdManagerAction);

    AnAction action = getTroubleshootDeviceConnectionsAction();

    if (action == null) {
      return group;
    }

    group.addSeparator();
    group.add(action);

    return group;
  }

  @NotNull
  private Collection<AnAction> newSelectDeviceAndSnapshotActions(@NotNull Project project) {
    Map<Boolean, List<Device>> connectednessToDeviceMap = myDevices.stream().collect(Collectors.groupingBy(Device::isConnected));

    Collection<Device> connectedDevices = connectednessToDeviceMap.getOrDefault(true, Collections.emptyList());
    Collection<Device> disconnectedDevices = connectednessToDeviceMap.getOrDefault(false, Collections.emptyList());

    Collection<AnAction> actions = new ArrayList<>(connectedDevices.size() + 1 + disconnectedDevices.size());

    connectedDevices.stream()
      .map(device -> newSelectDeviceAndSnapshotAction(project, device))
      .forEach(actions::add);

    if (!connectedDevices.isEmpty() && !disconnectedDevices.isEmpty()) {
      actions.add(Separator.create());
    }

    disconnectedDevices.stream()
      .map(device -> newSelectDeviceAndSnapshotAction(project, device))
      .forEach(actions::add);

    return actions;
  }

  @NotNull
  private AnAction newSelectDeviceAndSnapshotAction(@NotNull Project project, @NotNull Device device) {
    return new SelectDeviceAndSnapshotAction.Builder()
      .setComboBoxAction(this)
      .setProject(project)
      .setDevice(device)
      .build();
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
    Presentation presentation = event.getPresentation();

    if (!mySelectDeviceSnapshotComboBoxVisible.get()) {
      presentation.setVisible(false);
      return;
    }

    Project project = event.getProject();

    if (project == null) {
      return;
    }

    presentation.setVisible(true);
    updatePresentation(presentation, RunManager.getInstance(project).getSelectedConfiguration());

    myDevices = myDevicesGetterGetter.apply(project).get();
    myDevices.sort(new DeviceComparator());

    if (myDevices.isEmpty()) {
      setSelectedDevice(project, null);
      setSelectedSnapshot(null);

      presentation.setIcon(null);
      presentation.setText("No devices");

      return;
    }

    updateSelectedSnapshot(project);

    Device device = getSelectedDevice(project);
    assert device != null;

    presentation.setIcon(device.getIcon());
    presentation.setText(mySelectedSnapshot == null ? device.getName() : device + " - " + mySelectedSnapshot);

    updateExecutionTargetManager(project, device);
  }

  @VisibleForTesting
  static void updatePresentation(@NotNull Presentation presentation, @Nullable RunnerAndConfigurationSettings settings) {
    if (settings == null) {
      presentation.setDescription("Add a run/debug configuration");
      presentation.setEnabled(false);

      return;
    }

    RunProfile configuration = settings.getConfiguration();

    if (!(configuration instanceof AndroidRunConfiguration || configuration instanceof AndroidTestRunConfiguration)) {
      presentation.setDescription("Not applicable for the \"" + configuration.getName() + "\" configuration");
      presentation.setEnabled(false);

      return;
    }

    if (configuration instanceof AndroidTestRunConfiguration &&
        isFirebaseTestLabDeviceMatrixTargetSelected((AndroidRunConfigurationBase)configuration)) {
      presentation.setDescription("Not applicable for the Firebase test lab device matrix target");
      presentation.setEnabled(false);

      return;
    }

    presentation.setDescription(null);
    presentation.setEnabled(true);
  }

  private static boolean isFirebaseTestLabDeviceMatrixTargetSelected(@NotNull AndroidRunConfigurationBase configuration) {
    Object id = TargetSelectionMode.FIREBASE_DEVICE_MATRIX.name();
    return configuration.getDeployTargetContext().getCurrentDeployTargetProvider().getId().equals(id);
  }

  private void updateSelectedSnapshot(@NotNull Project project) {
    if (!mySelectDeviceSnapshotComboBoxSnapshotsEnabled.get()) {
      return;
    }

    Device device = getSelectedDevice(project);
    assert device != null;

    Collection<String> snapshots = device.getSnapshots();

    if (mySelectedSnapshot == null) {
      Optional<String> selectedDeviceSnapshot = snapshots.stream().findFirst();
      selectedDeviceSnapshot.ifPresent(snapshot -> setSelectedSnapshot(snapshot));

      return;
    }

    if (snapshots.contains(mySelectedSnapshot)) {
      return;
    }

    Optional<String> selectedSnapshot = snapshots.stream().findFirst();
    setSelectedSnapshot(selectedSnapshot.orElse(null));
  }

  private static void updateExecutionTargetManager(@NotNull Project project, @Nullable Device device) {
    ExecutionTarget target = ExecutionTargetManager.getInstance(project).getActiveTarget();

    // Skip updating ExecutionTargetManager if the Device has not meaningfully changed.
    if (device == null && target == DefaultExecutionTarget.INSTANCE ||
        target instanceof DeviceAndSnapshotExecutionTargetProvider.Target &&
        Objects.equals(device, ((DeviceAndSnapshotExecutionTargetProvider.Target)target).getDevice())) {
      return;
    }

    // In certain test scenarios, this action may get updated in the main test thread instead of the EDT thread (is this correct?).
    // So we'll just make sure the following gets run on the EDT thread and wait for its result.
    ApplicationManager.getApplication().invokeAndWait(() -> {
      RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();

      if (settings == null) {
        return;
      }

      ExecutionTargetManager manager = ExecutionTargetManager.getInstance(project);

      for (ExecutionTarget availableTarget : manager.getTargetsFor(settings.getConfiguration())) {
        if (availableTarget instanceof DeviceAndSnapshotExecutionTargetProvider.Target) {
          manager.setActiveTarget(availableTarget);
          break;
        }
      }
    });
  }
}
