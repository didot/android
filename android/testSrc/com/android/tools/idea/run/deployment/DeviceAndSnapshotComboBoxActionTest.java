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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.execution.RunManager;
import com.intellij.ide.util.ProjectPropertiesComponentImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ui.DialogWrapper;
import icons.StudioIcons;
import java.awt.Component;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class DeviceAndSnapshotComboBoxActionTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private AsyncDevicesGetter myDevicesGetter;
  private ExecutionTargetService myExecutionTargetService;
  private DevicesSelectedService myDevicesSelectedService;
  private RunManager myRunManager;

  private Presentation myPresentation;
  private AnActionEvent myEvent;

  @Before
  public void mockDevicesGetter() {
    myDevicesGetter = Mockito.mock(AsyncDevicesGetter.class);
  }

  @Before
  public void mockExecutionTargetService() {
    myExecutionTargetService = Mockito.mock(ExecutionTargetService.class);
  }

  @Before
  public void newDevicesSelectedService() {
    PropertiesComponent properties = new ProjectPropertiesComponentImpl();
    Clock clock = Clock.fixed(Instant.parse("2018-11-28T01:15:27.000Z"), ZoneId.of("America/Los_Angeles"));

    myDevicesSelectedService = new DevicesSelectedService(myRule.getProject(), project -> properties, clock);
  }

  @Before
  public void mockRunManager() {
    myRunManager = Mockito.mock(RunManager.class);
  }

  @Before
  public void mockEvent() {
    myPresentation = new Presentation();

    myEvent = Mockito.mock(AnActionEvent.class);
    Mockito.when(myEvent.getProject()).thenReturn(myRule.getProject());
    Mockito.when(myEvent.getPresentation()).thenReturn(myPresentation);
    Mockito.when(myEvent.getPlace()).thenReturn(ActionPlaces.MAIN_TOOLBAR);
  }

  @Test
  public void modifyDeviceSet() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setKey(new Key("Pixel_4_API_29"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(Collections.singletonList(device)));

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    Mockito.when(service.isMultipleDevicesSelectedInComboBox()).thenReturn(true);
    Mockito.when(service.getDevicesSelectedWithDialog(Collections.singletonList(device))).thenReturn(Collections.singletonList(device));

    DialogWrapper dialog = Mockito.mock(DialogWrapper.class);
    Mockito.when(dialog.showAndGet()).thenReturn(true);

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .setDevicesSelectedServiceGetInstance(project -> service)
      .setExecutionTargetServiceGetInstance(project -> myExecutionTargetService)
      .setNewModifyDeviceSetDialog((project, devices) -> dialog)
      .build();

    // Act
    action.modifyDeviceSet(myRule.getProject());

    // Assert
    Mockito.verify(service).setMultipleDevicesSelectedInComboBox(true);
    Mockito.verify(myExecutionTargetService).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(device));
  }

  @Test
  public void modifyDeviceSetDialogSelectionEmpty() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setKey(new Key("Pixel_4_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(Collections.singletonList(device)));

    DialogWrapper dialog = Mockito.mock(DialogWrapper.class);
    Mockito.when(dialog.showAndGet()).thenReturn(true);

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .setDevicesSelectedServiceGetInstance(project -> myDevicesSelectedService)
      .setExecutionTargetServiceGetInstance(project -> myExecutionTargetService)
      .setNewModifyDeviceSetDialog((project, devices) -> dialog)
      .build();

    // Act
    action.modifyDeviceSet(myRule.getProject());

    // Assert
    assertFalse(myDevicesSelectedService.isMultipleDevicesSelectedInComboBox());
    Mockito.verify(myExecutionTargetService).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(device));
  }

  @Test
  public void createCustomComponent() {
    // Arrange
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .build();

    myPresentation.setIcon(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE);

    // noinspection DialogTitleCapitalization
    myPresentation.setText(
      "Pixel 2 API 29 (Failed to parse properties from /usr/local/google/home/juancnuno/.android/avd/Pixel_2_API_29.avd/config.ini)",
      false);

    // Act
    Component component = action.createCustomComponent(myPresentation, i -> i);

    // Assert
    assertEquals(253, component.getPreferredSize().width);
  }

  @Test
  public void updateDevicesIsntPresent() {
    // Arrange
    AnAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .build();

    // Act
    action.update(myEvent);

    // Assert
    assertFalse(myPresentation.isEnabled());
    assertEquals("Loading Devices...", myPresentation.getText());
  }

  @Test
  public void updateDoesntClearSelectedDeviceWhenDevicesIsEmpty() {
    // Arrange
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .setDevicesSelectedServiceGetInstance(project -> myDevicesSelectedService)
      .setExecutionTargetServiceGetInstance(project -> myExecutionTargetService)
      .setGetRunManager(project -> myRunManager)
      .build();

    Device pixel2XlApiQ = new VirtualDevice.Builder()
      .setName("Pixel 2 XL API Q")
      .setKey(new Key("Pixel_2_XL_API_Q"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device pixel3XlApiQ = new VirtualDevice.Builder()
      .setName("Pixel 3 XL API Q")
      .setKey(new Key("Pixel_3_XL_API_Q"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    myDevicesSelectedService.setDeviceSelectedWithComboBox(pixel3XlApiQ);
    action.update(myEvent);

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(Arrays.asList(pixel2XlApiQ, pixel3XlApiQ)));
    action.update(myEvent);

    // Assert
    assertEquals(pixel3XlApiQ, action.getSelectedDevice(myRule.getProject()));
  }
}
