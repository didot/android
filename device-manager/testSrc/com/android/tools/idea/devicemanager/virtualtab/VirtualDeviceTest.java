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

import static org.junit.Assert.assertEquals;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.devicemanager.DeviceType;
import icons.StudioIcons;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceTest {
  @Test
  public void buildPhone() {
    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName("Pixel_3_API_30"))
      .setType(DeviceType.PHONE)
      .setName("Google Pixel 3")
      .setTarget("Android 11.0")
      .setCpuArchitecture("x86")
      .setAndroidVersion(new AndroidVersion(30))
      .setAvdInfo(Mockito.mock(AvdInfo.class))
      .build();

    assertEquals(new VirtualDeviceName("Pixel_3_API_30"), device.getKey());
    assertEquals("x86", device.getCpuArchitecture());
    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE, device.getIcon());
  }

  @Test
  public void buildWearOs() {
    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName("Wear_OS_Round_API_30"))
      .setType(DeviceType.WEAR_OS)
      .setName("Wear OS Round")
      .setTarget("Android 11.0")
      .setCpuArchitecture("x86")
      .setAndroidVersion(new AndroidVersion(30))
      .setAvdInfo(Mockito.mock(AvdInfo.class))
      .build();

    assertEquals(new VirtualDeviceName("Wear_OS_Round_API_30"), device.getKey());
    assertEquals("x86", device.getCpuArchitecture());
    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR, device.getIcon());
  }

  @Test
  public void buildTv() {
    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName("Android_TV_1080p_API_30"))
      .setType(DeviceType.TV)
      .setName("Android TV (1080p)")
      .setTarget("Android 11.0")
      .setCpuArchitecture("x86")
      .setAndroidVersion(new AndroidVersion(30))
      .setAvdInfo(Mockito.mock(AvdInfo.class))
      .build();

    assertEquals(new VirtualDeviceName("Android_TV_1080p_API_30"), device.getKey());
    assertEquals("x86", device.getCpuArchitecture());
    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV, device.getIcon());
  }

  @Test
  public void buildAutomotive() {
    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName("Automotive_1024p_landscape_API_30"))
      .setType(DeviceType.AUTOMOTIVE)
      .setName("Automotive (1024p landscape)")
      .setTarget("Android 11.0")
      .setCpuArchitecture("x86")
      .setAndroidVersion(new AndroidVersion(30))
      .setAvdInfo(Mockito.mock(AvdInfo.class))
      .build();

    assertEquals(new VirtualDeviceName("Automotive_1024p_landscape_API_30"), device.getKey());
    assertEquals("x86", device.getCpuArchitecture());
    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR, device.getIcon());
  }
}
