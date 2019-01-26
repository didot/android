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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.JComponent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class DeviceAndSnapshotComboBoxActionTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private AsyncDevicesGetter myDevicesGetter;

  private Clock myClock;
  private ConnectionTimeService myService;

  private Project myProject;
  private Presentation myPresentation;
  private AnActionEvent myEvent;

  private DataContext myContext;

  @Before
  public void mockDevicesGetter() {
    myDevicesGetter = Mockito.mock(AsyncDevicesGetter.class);
  }

  @Before
  public void newService() {
    myClock = Mockito.mock(Clock.class);
    Mockito.when(myClock.instant()).thenReturn(Instant.parse("2018-11-28T01:15:27.000Z"));

    myService = new ConnectionTimeService(myClock);
  }

  @Before
  public void mockEvent() {
    myProject = myRule.getProject();
    myPresentation = new Presentation();

    myEvent = Mockito.mock(AnActionEvent.class);

    Mockito.when(myEvent.getProject()).thenReturn(myProject);
    Mockito.when(myEvent.getPresentation()).thenReturn(myPresentation);
  }

  @Before
  public void mockContext() {
    myContext = Mockito.mock(DataContext.class);
    Mockito.when(myContext.getData(CommonDataKeys.PROJECT)).thenReturn(myRule.getProject());
  }

  @Test
  public void getSelectedDeviceDevicesIsEmpty() {
    assertNull(new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock).getSelectedDevice(myProject));
  }

  @Test
  public void getSelectedDeviceSelectedDeviceIsntPresent() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of());

    Device device = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);

    assertEquals(builder.build(null, myService), action.getSelectedDevice(myProject));
  }

  @Test
  public void getSelectedDeviceSelectedDeviceIsConnected() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setConnected(true)
      .setSnapshots(ImmutableList.of());

    Device device = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);
    action.setSelectedDevice(myProject, builder.build(null, myService));

    assertEquals(builder.build(null, myService), action.getSelectedDevice(myProject));
  }

  @Test
  public void getSelectedDeviceSelectionTimeIsBeforeConnectionTime() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of());

    Device device = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);
    action.setSelectedDevice(myProject, builder.build(null, myService));

    Mockito.when(myClock.instant()).thenReturn(Instant.parse("2018-11-28T01:15:28.000Z"));

    Device physicalDevice = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey("00fff9d2279fa601")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build(null, myService);

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Arrays.asList(builder.build(null, myService), physicalDevice));

    action.update(myEvent);

    assertEquals(physicalDevice, action.getSelectedDevice(myProject));
  }

  @Test
  public void getSelectedDeviceConnectedDeviceIsntPresent() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of());

    Device device1 = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device1));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);
    action.setSelectedDevice(myProject, builder.build(null, myService));

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel XL API 28")
      .setKey("Pixel_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of())
      .build(null, myService);

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Arrays.asList(builder.build(null, myService), device2));

    action.update(myEvent);

    assertEquals(builder.build(null, myService), action.getSelectedDevice(myProject));
  }

  @Test
  public void createPopupActionGroupActionsIsEmpty() {
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    assertEquals(actualChildren, Arrays.asList(action.getRunOnMultipleDevicesAction(), action.getOpenAvdManagerAction()));
  }

  @Test
  public void createPopupActionGroupDeviceIsVirtual() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of());

    Device device = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(action)
        .setProject(myProject)
        .setDevice(builder.build(null, myService))
        .build(),
      Separator.getInstance(),
      action.getRunOnMultipleDevicesAction(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupDeviceIsPhysical() {
    Device.Builder builder = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey("00fff9d2279fa601")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class));

    Device device = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(action)
        .setProject(myProject)
        .setDevice(builder.build(null, myService))
        .build(),
      Separator.getInstance(),
      action.getRunOnMultipleDevicesAction(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupDevicesAreVirtualAndPhysical() {
    Device.Builder virtualDeviceBuilder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of());

    Device.Builder physicalDeviceBuilder = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey("00fff9d2279fa601")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class));

    Device device1 = virtualDeviceBuilder.build(null, myService);
    Device device2 = physicalDeviceBuilder.build(null, myService);

    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Arrays.asList(device1, device2));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(action)
        .setProject(myProject)
        .setDevice(physicalDeviceBuilder.build(null, myService))
        .build(),
      Separator.getInstance(),
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(action)
        .setProject(myProject)
        .setDevice(virtualDeviceBuilder.build(null, myService))
        .build(),
      Separator.getInstance(),
      action.getRunOnMultipleDevicesAction(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupDeviceSnapshotIsDefault() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION);

    Device device = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(action)
        .setProject(myProject)
        .setDevice(builder.build(null, myService))
        .setSnapshot(VirtualDevice.DEFAULT_SNAPSHOT)
        .build(),
      Separator.getInstance(),
      action.getRunOnMultipleDevicesAction(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupSnapshotIsntDefault() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of("snap_2018-08-07_16-27-58"));

    Device device = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> false, myDevicesGetter, myClock);
    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(action)
        .setProject(myProject)
        .setDevice(builder.build(null, myService))
        .build(),
      Separator.getInstance(),
      action.getRunOnMultipleDevicesAction(),
      action.getOpenAvdManagerAction());

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void updateSelectDeviceSnapshotComboBoxVisibleIsFalse() {
    new DeviceAndSnapshotComboBoxAction(() -> false, () -> false, myDevicesGetter, myClock).update(myEvent);
    assertFalse(myPresentation.isVisible());
  }

  @Test
  public void updateDevicesIsEmpty() {
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.emptyList(), action.getDevices());
    assertNull(action.getSelectedDevice(myProject));
    assertNull(action.getSelectedSnapshot());
    assertNull(myPresentation.getIcon());
    assertEquals("No devices", myPresentation.getText());
  }

  @Test
  public void updateSelectedDeviceIsNull() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of());

    Device device = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(builder.build(null, myService)), action.getDevices());
    assertEquals(builder.build(null, myService), action.getSelectedDevice(myProject));
    assertNull(action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28, myPresentation.getText());
  }

  @Test
  public void updateDevicesContainSelectedDevice() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of());

    Device device = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.setSelectedDevice(myProject, builder.build(null, myService));
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(builder.build(null, myService)), action.getDevices());
    assertEquals(builder.build(null, myService), action.getSelectedDevice(myProject));
    assertNull(action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28, myPresentation.getText());
  }

  @Test
  public void updateDevicesDontContainSelectedDevice() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of());

    Device device1 = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device1));

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel XL API 28")
      .setKey("Pixel_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of())
      .build(null, myService);

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.setSelectedDevice(myProject, device2);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(builder.build(null, myService)), action.getDevices());
    assertEquals(builder.build(null, myService), action.getSelectedDevice(myProject));
    assertNull(action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28, myPresentation.getText());
  }

  @Test
  public void updateSelectedSnapshotIsNull() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION);

    Device device = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.setSelectedDevice(myProject, builder.build(null, myService));
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(builder.build(null, myService)), action.getDevices());
    assertEquals(builder.build(null, myService), action.getSelectedDevice(myProject));
    assertEquals(VirtualDevice.DEFAULT_SNAPSHOT, action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28 + " - defaultboot", myPresentation.getText());
  }

  @Test
  public void updateSnapshotsContainSelectedSnapshot() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION);

    Device device = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.setSelectedDevice(myProject, builder.build(null, myService));
    action.setSelectedSnapshot(VirtualDevice.DEFAULT_SNAPSHOT);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(builder.build(null, myService)), action.getDevices());
    assertEquals(builder.build(null, myService), action.getSelectedDevice(myProject));
    assertEquals(VirtualDevice.DEFAULT_SNAPSHOT, action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28 + " - defaultboot", myPresentation.getText());
  }

  @Test
  public void updateSelectedDeviceHasSnapshot() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION);

    Device device = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.setSelectedDevice(myProject, builder.build(null, myService));
    action.setSelectedSnapshot("snap_2018-08-07_16-27-58");
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(builder.build(null, myService)), action.getDevices());
    assertEquals(builder.build(null, myService), action.getSelectedDevice(myProject));
    assertEquals(VirtualDevice.DEFAULT_SNAPSHOT, action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28 + " - defaultboot", myPresentation.getText());
  }

  @Test
  public void updateSelectedDeviceDoesntHaveSnapshot() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of());

    Device device = builder.build(null, myService);
    Mockito.when(myDevicesGetter.get(myProject)).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction(() -> true, () -> true, myDevicesGetter, myClock);
    action.setSelectedDevice(myProject, builder.build(null, myService));
    action.setSelectedSnapshot(VirtualDevice.DEFAULT_SNAPSHOT);
    action.update(myEvent);

    assertTrue(myPresentation.isVisible());
    assertEquals(Collections.singletonList(builder.build(null, myService)), action.getDevices());
    assertEquals(builder.build(null, myService), action.getSelectedDevice(myProject));
    assertNull(action.getSelectedSnapshot());
    assertEquals(AndroidIcons.Ddms.EmulatorDevice, myPresentation.getIcon());
    assertEquals(Devices.PIXEL_2_XL_API_28, myPresentation.getText());
  }
}
