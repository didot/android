/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Abi;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.Function;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An {@link AndroidDevice} represents either a connected {@link IDevice}, or the
 * {@link com.android.sdklib.internal.avd.AvdInfo} corresponding to an emulator that can be launched.
 */
public interface AndroidDevice {
  /** Returns whether the device is currently running. */
  boolean isRunning();

  /** Returns whether this is a virtual device. */
  boolean isVirtual();

  /** Returns the API level of the device. */
  @NotNull
  AndroidVersion getVersion();

  /** Returns the device display density. */
  int getDensity();

  /** Returns the list of (sorted by most preferred first) ABIs supported by this device. */
  @NotNull
  List<Abi> getAbis();

  /** Returns a unique serial number */
  @NotNull
  String getSerial();

  /** Returns whether this device supports the given hardware feature. */
  boolean supportsFeature(@NotNull IDevice.HardwareFeature feature);

  /** Returns the device name. */
  @NotNull
  String getName();

  /**
   * Renders the device name and miscellaneous info to the given component.
   *
   * @param component the component to render to
   * @param isCompatible whether the device satisfies the requirements of the current context
   * @param searchPrefix the prefix to highlight
   * @return true is the name was successfully rendered, false if some information for rendering was not readily available and
   *         the rendered name contained a "..." placeholder
   */
  boolean renderLabel(@NotNull SimpleColoredComponent component, boolean isCompatible, @Nullable String searchPrefix);

  /**
   * Tells the device to prepare all information necessary for rendering its label. This operation may be slow and should not be invoked
   * on the UI thread.
   */
  void prepareToRenderLabel();

  /** Returns the {@link IDevice} corresponding to this device, launching it if necessary. */
  @NotNull
  ListenableFuture<IDevice> launch(@NotNull Project project);

  /**
   * @param arguments additional arguments to pass to the emulator command
   */
  @NotNull
  ListenableFuture<IDevice> launch(@NotNull Project project, @NotNull List<String> arguments);

  /**
   * Returns the {@link IDevice} corresponding to this device if it is running or has been launched.
   * Throws {@link IllegalStateException} if the device is not running and hasn't been launched.
   */
  @NotNull
  ListenableFuture<IDevice> getLaunchedDevice();

  /** Check if this device can run an application with given requirements. */
  @NotNull
  LaunchCompatibility canRun(@NotNull AndroidVersion minSdkVersion,
                             @NotNull IAndroidTarget projectTarget,
                             @NotNull AndroidFacet facet,
                             Function<AndroidFacet, EnumSet<IDevice.HardwareFeature>> getRequiredHardwareFeatures,
                             @NotNull Set<String> supportedAbis);

  /** Returns whether this device is debuggable or not. */
  boolean isDebuggable();
}
