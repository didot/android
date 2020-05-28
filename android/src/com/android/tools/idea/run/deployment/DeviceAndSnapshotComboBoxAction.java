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
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.RunManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeviceAndSnapshotComboBoxAction extends ComboBoxAction {
  /**
   * Run configurations that aren't {@link AndroidRunConfiguration} or {@link AndroidTestRunConfiguration} can use this key
   * to express their applicability for DeviceAndSnapshotComboBoxAction by setting it to true in their user data.
   */
  public static final com.intellij.openapi.util.Key<Boolean> DEPLOYS_TO_LOCAL_DEVICE =
    com.intellij.openapi.util.Key.create("DeviceAndSnapshotComboBoxAction.deploysToLocalDevice");

  @NotNull
  private final Supplier<Boolean> mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

  @NotNull
  private final Function<Project, AsyncDevicesGetter> myDevicesGetterGetter;

  @NotNull
  private final Function<Project, DevicesSelectedService> myDevicesSelectedServiceGetInstance;

  @NotNull
  private final Function<Project, ExecutionTargetService> myExecutionTargetServiceGetInstance;

  @NotNull
  private final BiFunction<Project, List<Device>, DialogWrapper> myNewModifyDeviceSetDialog;

  @NotNull
  private final Function<Project, RunManager> myGetRunManager;

  @VisibleForTesting
  static final class Builder {
    @Nullable
    private Supplier<Boolean> mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

    @Nullable
    private Function<Project, AsyncDevicesGetter> myDevicesGetterGetter;

    @Nullable
    private Function<Project, DevicesSelectedService> myDevicesSelectedServiceGetInstance;

    @Nullable
    private Function<Project, ExecutionTargetService> myExecutionTargetServiceGetInstance;

    @Nullable
    private BiFunction<Project, List<Device>, DialogWrapper> myNewModifyDeviceSetDialog;

    @Nullable
    private Function<Project, RunManager> myGetRunManager;

    Builder() {
      mySelectDeviceSnapshotComboBoxSnapshotsEnabled = () -> false;
      myDevicesGetterGetter = project -> null;
      myDevicesSelectedServiceGetInstance = project -> null;
      myExecutionTargetServiceGetInstance = project -> null;
      myNewModifyDeviceSetDialog = (project, devices) -> null;
      myGetRunManager = project -> null;
    }

    @NotNull
    Builder setSelectDeviceSnapshotComboBoxSnapshotsEnabled(@NotNull Supplier<Boolean> selectDeviceSnapshotComboBoxSnapshotsEnabled) {
      mySelectDeviceSnapshotComboBoxSnapshotsEnabled = selectDeviceSnapshotComboBoxSnapshotsEnabled;
      return this;
    }

    @NotNull
    Builder setDevicesGetterGetter(@NotNull Function<Project, AsyncDevicesGetter> devicesGetterGetter) {
      myDevicesGetterGetter = devicesGetterGetter;
      return this;
    }

    @NotNull
    Builder setDevicesSelectedServiceGetInstance(@NotNull Function<Project, DevicesSelectedService> devicesSelectedServiceGetInstance) {
      myDevicesSelectedServiceGetInstance = devicesSelectedServiceGetInstance;
      return this;
    }

    @NotNull
    Builder setExecutionTargetServiceGetInstance(@NotNull Function<Project, ExecutionTargetService> executionTargetServiceGetInstance) {
      myExecutionTargetServiceGetInstance = executionTargetServiceGetInstance;
      return this;
    }

    @NotNull
    Builder setNewModifyDeviceSetDialog(@NotNull BiFunction<Project, List<Device>, DialogWrapper> newModifyDeviceSetDialog) {
      myNewModifyDeviceSetDialog = newModifyDeviceSetDialog;
      return this;
    }

    @NotNull
    Builder setGetRunManager(@NotNull Function<Project, RunManager> getRunManager) {
      myGetRunManager = getRunManager;
      return this;
    }

    @NotNull
    DeviceAndSnapshotComboBoxAction build() {
      return new DeviceAndSnapshotComboBoxAction(this);
    }
  }

  @SuppressWarnings("unused")
  private DeviceAndSnapshotComboBoxAction() {
    this(new Builder()
           .setSelectDeviceSnapshotComboBoxSnapshotsEnabled(StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED::get)
           .setDevicesGetterGetter(AsyncDevicesGetter::getInstance)
           .setDevicesSelectedServiceGetInstance(DevicesSelectedService::getInstance)
           .setExecutionTargetServiceGetInstance(ExecutionTargetService::getInstance)
           .setNewModifyDeviceSetDialog(ModifyDeviceSetDialog::new)
           .setGetRunManager(RunManager::getInstance));
  }

  @NonInjectable
  private DeviceAndSnapshotComboBoxAction(@NotNull Builder builder) {
    assert builder.mySelectDeviceSnapshotComboBoxSnapshotsEnabled != null;
    mySelectDeviceSnapshotComboBoxSnapshotsEnabled = builder.mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

    assert builder.myDevicesGetterGetter != null;
    myDevicesGetterGetter = builder.myDevicesGetterGetter;

    assert builder.myDevicesSelectedServiceGetInstance != null;
    myDevicesSelectedServiceGetInstance = builder.myDevicesSelectedServiceGetInstance;

    assert builder.myExecutionTargetServiceGetInstance != null;
    myExecutionTargetServiceGetInstance = builder.myExecutionTargetServiceGetInstance;

    assert builder.myNewModifyDeviceSetDialog != null;
    myNewModifyDeviceSetDialog = builder.myNewModifyDeviceSetDialog;

    assert builder.myGetRunManager != null;
    myGetRunManager = builder.myGetRunManager;
  }

  @NotNull
  static DeviceAndSnapshotComboBoxAction getInstance() {
    // noinspection CastToConcreteClass
    return (DeviceAndSnapshotComboBoxAction)ActionManager.getInstance().getAction("DeviceAndSnapshotComboBox");
  }

  boolean areSnapshotsEnabled() {
    return mySelectDeviceSnapshotComboBoxSnapshotsEnabled.get();
  }

  @NotNull
  Optional<List<Device>> getDevices(@NotNull Project project) {
    Optional<List<Device>> optionalDevices = myDevicesGetterGetter.apply(project).get();

    if (optionalDevices.isPresent()) {
      List<Device> devices = optionalDevices.get();
      devices.sort(new DeviceComparator());

      return Optional.of(devices);
    }

    return optionalDevices;
  }

  @Nullable
  @VisibleForTesting
  Device getSelectedDevice(@NotNull Project project) {
    List<Device> devices = getDevices(project).orElse(Collections.emptyList());
    return myDevicesSelectedServiceGetInstance.apply(project).getDeviceSelectedWithComboBox(devices);
  }

  void setSelectedDevice(@NotNull Project project, @NotNull Device selectedDevice) {
    myDevicesSelectedServiceGetInstance.apply(project).setDeviceSelectedWithComboBox(selectedDevice);
    myExecutionTargetServiceGetInstance.apply(project).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(selectedDevice));
  }

  @NotNull
  List<Device> getSelectedDevices(@NotNull Project project) {
    DevicesSelectedService service = myDevicesSelectedServiceGetInstance.apply(project);
    List<Device> devices = getDevices(project).orElse(Collections.emptyList());

    if (service.isMultipleDevicesSelectedInComboBox()) {
      return service.getDevicesSelectedWithDialog(devices);
    }

    Device device = service.getDeviceSelectedWithComboBox(devices);

    if (device == null) {
      return Collections.emptyList();
    }

    return Collections.singletonList(device);
  }

  void setMultipleDevicesSelected(@NotNull Project project, boolean multipleDevicesSelected) {
    myDevicesSelectedServiceGetInstance.apply(project).setMultipleDevicesSelectedInComboBox(multipleDevicesSelected);

    List<Device> devices = getSelectedDevices(project);
    myExecutionTargetServiceGetInstance.apply(project).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(devices));
  }

  void modifyDeviceSet(@NotNull Project project) {
    List<Device> devices = myDevicesGetterGetter.apply(project).get().orElseThrow(AssertionError::new);

    if (!myNewModifyDeviceSetDialog.apply(project, devices).showAndGet()) {
      return;
    }

    setMultipleDevicesSelected(project, !myDevicesSelectedServiceGetInstance.apply(project).isDialogSelectionEmpty());
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return createCustomComponent(presentation, JBUI::scale);
  }

  @NotNull
  @VisibleForTesting
  JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull IntUnaryOperator scale) {
    JComponent panel = new JPanel(null);
    GroupLayout layout = new GroupLayout(panel);
    Component button = createComboBoxButton(presentation);

    Group horizontalGroup = layout.createSequentialGroup()
      .addComponent(button, 0, scale.applyAsInt(GroupLayout.DEFAULT_SIZE), scale.applyAsInt(250))
      .addGap(scale.applyAsInt(3));

    Group verticalGroup = layout.createParallelGroup()
      .addComponent(button);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    panel.setLayout(layout);
    return panel;
  }

  @NotNull
  @Override
  protected ComboBoxButton createComboBoxButton(@NotNull Presentation presentation) {
    ComboBoxButton button = new ComboBoxButton(presentation) {
      @Override
      protected JBPopup createPopup(@NotNull Runnable runnable) {
        DataContext context = getDataContext();
        return new Popup(createPopupActionGroup(this, context), context, runnable);
      }
    };

    button.setName("deviceAndSnapshotComboBoxButton");
    return button;
  }

  @Override
  protected boolean shouldShowDisabledActions() {
    return true;
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
    Project project = context.getData(CommonDataKeys.PROJECT);
    assert project != null;

    return new PopupActionGroup(getDevices(project).orElseThrow(AssertionError::new), this);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = event.getProject();

    if (project == null) {
      presentation.setVisible(false);
      return;
    }

    Optional<List<Device>> devices = getDevices(project);

    if (!devices.isPresent()) {
      presentation.setEnabled(false);
      presentation.setText("Loading Devices...");

      return;
    }

    Updater updater = new Updater.Builder()
      .setProject(project)
      .setPresentation(presentation)
      .setPlace(event.getPlace())
      .setDevicesSelectedService(myDevicesSelectedServiceGetInstance.apply(project))
      .setDevices(devices.get())
      .setConfigurationAndSettings(myGetRunManager.apply(project).getSelectedConfiguration())
      .setSnapshotsEnabled(mySelectDeviceSnapshotComboBoxSnapshotsEnabled.get())
      .build();

    updater.update();

    List<Device> selectedDevices = getSelectedDevices(project);
    myExecutionTargetServiceGetInstance.apply(project).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(selectedDevices));
  }
}
