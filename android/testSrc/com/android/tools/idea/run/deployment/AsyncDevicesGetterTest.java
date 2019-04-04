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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.android.ddmlib.IDevice;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.module.Module;
import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class AsyncDevicesGetterTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private AsyncDevicesGetter myGetter;
  private VirtualDevice myVirtualDevice;

  private IDevice myConnectedDevice;
  private Collection<IDevice> myConnectedDevices;

  @Before
  public void setUp() {
    Clock clock = Mockito.mock(Clock.class);
    Mockito.when(clock.instant()).thenReturn(Instant.parse("2018-11-28T01:15:27.000Z"));

    myGetter = new AsyncDevicesGetter(myRule.getProject(), new KeyToConnectionTimeMap(clock));

    AvdInfo avdInfo = new AvdInfo(
      "Pixel_2_XL_API_27",
      new File("/usr/local/google/home/juancnuno/.android/avd/Pixel_2_XL_API_27.ini"),
      "/usr/local/google/home/juancnuno/.android/avd/Pixel_2_XL_API_27.avd",
      Mockito.mock(ISystemImage.class),
      null);

    myVirtualDevice = VirtualDevice.newDisconnectedDevice(avdInfo, null);
  }

  @Before
  public void newConnectedDevices() {
    myConnectedDevice = Mockito.mock(IDevice.class);

    myConnectedDevices = new ArrayList<>(1);
    myConnectedDevices.add(myConnectedDevice);
  }

  @Test
  public void initChecker() {
    RunConfigurationModule configurationModule = Mockito.mock(RunConfigurationModule.class);
    Mockito.when(configurationModule.getModule()).thenReturn(myRule.getModule());

    ModuleBasedConfiguration configuration = Mockito.mock(ModuleBasedConfiguration.class);
    Mockito.when(configuration.getConfigurationModule()).thenReturn(configurationModule);

    RunnerAndConfigurationSettings configurationAndSettings = Mockito.mock(RunnerAndConfigurationSettings.class);
    Mockito.when(configurationAndSettings.getConfiguration()).thenReturn(configuration);

    myGetter.initChecker(configurationAndSettings, AsyncDevicesGetterTest::newAndroidFacet);
    assertNull(myGetter.getChecker());
  }

  @NotNull
  private static AndroidFacet newAndroidFacet(@NotNull Module module) {
    return new AndroidFacet(module, "Android", Mockito.mock(AndroidFacetConfiguration.class));
  }

  @Test
  public void newVirtualDeviceIfItsConnectedAvdNamesAreEqual() {
    Mockito.when(myConnectedDevice.getAvdName()).thenReturn("Pixel_2_XL_API_27");

    Device actualDevice = myGetter.newVirtualDeviceIfItsConnected(myVirtualDevice, myConnectedDevices);

    Object expectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 XL API 27")
      .setKey("Pixel_2_XL_API_27")
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27.000Z"))
      .setAndroidDevice(actualDevice.getAndroidDevice())
      .setConnected(true)
      .build(null);

    assertEquals(expectedDevice, actualDevice);
    assertEquals(Collections.emptyList(), myConnectedDevices);
  }

  @Test
  public void newVirtualDeviceIfItsConnected() {
    Mockito.when(myConnectedDevice.getAvdName()).thenReturn("Pixel_2_XL_API_28");

    Object actualDevice = myGetter.newVirtualDeviceIfItsConnected(myVirtualDevice, myConnectedDevices);

    assertSame(myVirtualDevice, actualDevice);
    assertEquals(Collections.singletonList(myConnectedDevice), myConnectedDevices);
  }
}
