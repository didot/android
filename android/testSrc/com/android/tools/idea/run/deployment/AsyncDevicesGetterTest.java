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

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.module.Module;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Function;
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

  private Function<ConnectedDevice, String> myGetName;
  private AsyncDevicesGetter myGetter;

  @Before
  public void setUp() {
    Clock clock = Mockito.mock(Clock.class);
    Mockito.when(clock.instant()).thenReturn(Instant.parse("2018-11-28T01:15:27.000Z"));

    // noinspection unchecked
    myGetName = Mockito.mock(Function.class);

    myGetter = new AsyncDevicesGetter(myRule.getProject(), () -> false, new KeyToConnectionTimeMap(clock), myGetName);
  }

  @Test
  public void getImpl() {
    // Arrange
    AndroidDevice pixel2ApiQAndroidDevice = Mockito.mock(AndroidDevice.class);

    VirtualDevice pixel2ApiQVirtualDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 API Q")
      .setKey(new Key("Pixel_2_API_Q"))
      .setAndroidDevice(pixel2ApiQAndroidDevice)
      .build();

    VirtualDevice pixel3ApiQVirtualDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API Q")
      .setKey(new Key("Pixel_3_API_Q"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    AndroidDevice googlePixel3AndroidDevice = Mockito.mock(AndroidDevice.class);

    ConnectedDevice googlePixel3ConnectedDevice = new ConnectedDevice.Builder()
      .setName("Physical Device")
      .setKey(new Key("86UX00F4R"))
      .setAndroidDevice(googlePixel3AndroidDevice)
      .build();

    Mockito.when(myGetName.apply(googlePixel3ConnectedDevice)).thenReturn("Google Pixel 3");

    AndroidDevice pixel3ApiQAndroidDevice = Mockito.mock(AndroidDevice.class);
    Mockito.when(pixel3ApiQAndroidDevice.isVirtual()).thenReturn(true);

    ConnectedDevice pixel3ApiQConnectedDevice = new ConnectedDevice.Builder()
      .setName("Virtual Device")
      .setKey(new Key("Pixel_3_API_Q"))
      .setAndroidDevice(pixel3ApiQAndroidDevice)
      .build();

    // Act
    Object actualDevices = myGetter.getImpl(
      Arrays.asList(pixel2ApiQVirtualDevice, pixel3ApiQVirtualDevice),
      Arrays.asList(googlePixel3ConnectedDevice, pixel3ApiQConnectedDevice));

    // Assert
    Object expectedPixel3ApiQDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API Q")
      .setKey(new Key("Pixel_3_API_Q"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27.000Z"))
      .setAndroidDevice(pixel3ApiQAndroidDevice)
      .build();

    Object expectedGooglePixel3Device = new PhysicalDevice.Builder()
      .setName("Google Pixel 3")
      .setKey(new Key("86UX00F4R"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27.000Z"))
      .setAndroidDevice(googlePixel3AndroidDevice)
      .build();

    Object expectedPixel2ApiQDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 API Q")
      .setKey(new Key("Pixel_2_API_Q"))
      .setAndroidDevice(pixel2ApiQAndroidDevice)
      .build();

    assertEquals(Arrays.asList(expectedPixel3ApiQDevice, expectedGooglePixel3Device, expectedPixel2ApiQDevice), actualDevices);
  }

  @Test
  public void getImplDeveloperBuiltTheirOwnSystemImage() {
    // Arrange
    AndroidDevice androidDevice = Mockito.mock(AndroidDevice.class);
    Mockito.when(androidDevice.isVirtual()).thenReturn(true);

    ConnectedDevice connectedDevice = new ConnectedDevice.Builder()
      .setName("Virtual Device")
      .setKey(new Key("emulator-5554"))
      .setAndroidDevice(androidDevice)
      .build();

    // Act
    Object actualDevices = myGetter.getImpl(Collections.emptyList(), Collections.singletonList(connectedDevice));

    // Assert
    Object expectedDevice = new VirtualDevice.Builder()
      .setName("Virtual Device")
      .setKey(new Key("emulator-5554"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27.000Z"))
      .setAndroidDevice(androidDevice)
      .build();

    assertEquals(Collections.singletonList(expectedDevice), actualDevices);
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
}
