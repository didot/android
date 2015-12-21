/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.install;

import com.android.SdkConstants;
import com.android.repository.api.RemotePackage;
import com.android.repository.io.FileOp;
import com.android.resources.Density;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.tools.idea.avdmanager.AvdEditWizard;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.avdmanager.DeviceManagerConnection;
import com.android.tools.idea.avdmanager.SystemImageDescription;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.android.tools.idea.welcome.wizard.InstallComponentsPath;
import com.android.tools.idea.welcome.wizard.ProgressStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.sdklib.internal.avd.AvdManager.*;
import static com.android.sdklib.internal.avd.HardwareProperties.*;
import static com.android.tools.idea.avdmanager.AvdWizardConstants.*;

/**
 * Logic for setting up Android virtual device
 */
public class AndroidVirtualDevice extends InstallableComponent {
  public static final Logger LOG = Logger.getInstance(AndroidVirtualDevice.class);
  private static final String ID_DEVICE_NEXUS_5 = "Nexus 5";
  private static final IdDisplay ID_ADDON_GOOGLE_API_IMG = new IdDisplay("google_apis", "Google APIs");
  private static final IdDisplay ID_VENDOR_GOOGLE = new IdDisplay("google", "Google Inc.");
  private static final Storage DEFAULT_RAM_SIZE = new Storage(1536, Storage.Unit.MiB);
  private static final Storage DEFAULT_HEAP_SIZE = new Storage(64, Storage.Unit.MiB);

  private static final Set<String> ENABLED_HARDWARE = ImmutableSet
    .of(HW_ACCELEROMETER, HW_AUDIO_INPUT, HW_BATTERY, HW_GPS, HW_KEYBOARD, HW_ORIENTATION_SENSOR, HW_PROXIMITY_SENSOR, HW_SDCARD,
        AVD_INI_GPU_EMULATION);
  private static final Set<String> DISABLED_HARDWARE =
    ImmutableSet.of(HW_DPAD, HW_MAINKEYS, HW_TRACKBALL, AVD_INI_SNAPSHOT_PRESENT, AVD_INI_SKIN_DYNAMIC);
  private ProgressStep myProgressStep;
  @Nullable
  private final AndroidVersion myLatestVersion;

  public AndroidVirtualDevice(@NotNull ScopedStateStore store, @NotNull Multimap<String, RemotePackage> remotePackages, FileOp fop) {
    super(store, "Android Virtual Device", Storage.Unit.GiB.getNumberOfBytes(),
          "A preconfigured and optimized Android Virtual Device for app testing on the emulator. (Recommended)", fop);
    RemotePackage latestInfo = InstallComponentsPath.findLatestPlatform(remotePackages, false);
    myLatestVersion = DetailsTypes.getAndroidVersion((DetailsTypes.PlatformDetailsType)latestInfo.getTypeDetails());
  }

  @NotNull
  private static Device getDevice(@NotNull File sdkPath) throws WizardException {
    List<Device> devices = DeviceManagerConnection.getDeviceManagerConnection(sdkPath).getDevices();
    for (Device device : devices) {
      if (Objects.equal(device.getId(), ID_DEVICE_NEXUS_5)) {
        return device;
      }
    }
    throw new WizardException(String.format("No device definition with \"%s\" ID found", ID_DEVICE_NEXUS_5));
  }

  private SystemImageDescription getSystemImageDescription(AndroidSdkHandler sdkHandler) throws WizardException {
    String platformHash =
      AndroidTargetHash.getAddonHashString(ID_VENDOR_GOOGLE.getDisplay(), ID_ADDON_GOOGLE_API_IMG.getDisplay(), myLatestVersion);
    StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
    IAndroidTarget target =
      sdkHandler.getAndroidTargetManager(progress).getTargetFromHashString(platformHash, progress);
    if (target == null) {
      throw new WizardException("Missing platform target SDK component required for an AVD setup");
    }
    ISystemImage systemImage = target.getSystemImage(ID_ADDON_GOOGLE_API_IMG, SdkConstants.ABI_INTEL_ATOM);
    if (systemImage == null) {
      throw new WizardException("Missing system image required for an AVD setup");
    }
    return new SystemImageDescription(target, systemImage);
  }

  @Nullable
  @VisibleForTesting
  AvdInfo createAvd(@NotNull AvdManagerConnection connection, @NotNull AndroidSdkHandler sdkHandler) throws WizardException {
    Device d = getDevice(sdkHandler.getLocation());
    SystemImageDescription systemImageDescription = getSystemImageDescription(sdkHandler);

    String cardSize = AvdEditWizard.toIniString(DEFAULT_INTERNAL_STORAGE, false);
    File hardwareSkinPath = AvdEditWizard.resolveSkinPath(d.getDefaultHardware().getSkinFile(), systemImageDescription);
    String displayName =
      String.format("%1$s %2$s %3$s", d.getDisplayName(), systemImageDescription.getVersion(), systemImageDescription.getAbiType());
    displayName = connection.uniquifyDisplayName(displayName);
    String internalName = AvdEditWizard.cleanAvdName(connection, displayName, true);
    Abi abi = Abi.getEnum(systemImageDescription.getAbiType());
    boolean useRanchu = AvdManagerConnection.doesSystemImageSupportRanchu(systemImageDescription);
    boolean supportsSmp = abi != null && abi.supportsMultipleCpuCores() && getMaxCpuCores() > 1;
    Map<String, String> settings = getAvdSettings(internalName, d);
    settings.put(AvdManagerConnection.AVD_INI_DISPLAY_NAME, displayName);
    if (useRanchu) {
      settings.put(CPU_CORES_KEY.name, String.valueOf(supportsSmp ? getMaxCpuCores() : 1));
    }
    return connection
      .createOrUpdateAvd(null, internalName, d, systemImageDescription, ScreenOrientation.PORTRAIT, false, cardSize, hardwareSkinPath,
                         settings, false);
  }

  private static Map<String, String> getAvdSettings(@NotNull String internalName, @NotNull Device device) {
    Map<String, String> result = Maps.newHashMap();
    for (String key : ENABLED_HARDWARE) {
      result.put(key, BOOLEAN_YES);
    }
    for (String key : DISABLED_HARDWARE) {
      result.put(key, BOOLEAN_NO);
    }
    for (String key : ImmutableSet.of(AVD_INI_CAMERA_BACK, AVD_INI_CAMERA_FRONT)) {
      result.put(key, "emulated");
    }
    result.put(AVD_INI_DEVICE_NAME, device.getDisplayName());
    result.put(AVD_INI_DEVICE_MANUFACTURER, device.getManufacturer());

    result.put(AVD_INI_NETWORK_LATENCY, DEFAULT_NETWORK_LATENCY);
    result.put(AVD_INI_NETWORK_SPEED, DEFAULT_NETWORK_SPEED);
    result.put(AVD_INI_SCALE_FACTOR, DEFAULT_SCALE.getValue());
    result.put(AVD_INI_AVD_ID, internalName);
    result.put(AvdManagerConnection.AVD_INI_HW_LCD_DENSITY, String.valueOf(Density.XXHIGH.getDpiValue()));

    setStorageSizeKey(result, AVD_INI_RAM_SIZE, DEFAULT_RAM_SIZE, true);
    setStorageSizeKey(result, AVD_INI_VM_HEAP_SIZE, DEFAULT_HEAP_SIZE, true);
    setStorageSizeKey(result, AVD_INI_DATA_PARTITION_SIZE, DEFAULT_INTERNAL_STORAGE, false);

    return result;
  }

  private static void setStorageSizeKey(Map<String, String> result, String key, Storage size, boolean convertToMb) {
    result.put(key, AvdEditWizard.toIniString(size, convertToMb));
  }

  @NotNull
  @Override
  public Collection<String> getRequiredSdkPackages(Multimap<String, RemotePackage> remotePackages) {
    List<String> result = Lists.newArrayList();
    if (myLatestVersion != null) {
      result.add(DetailsTypes.getAddonPath(ID_VENDOR_GOOGLE, myLatestVersion, ID_ADDON_GOOGLE_API_IMG));
      result.add(DetailsTypes.getSysImgPath(ID_VENDOR_GOOGLE, myLatestVersion, ID_ADDON_GOOGLE_API_IMG, SdkConstants.ABI_INTEL_ATOM));
    }
    return result;
  }

  @Override
  public void init(@NotNull ProgressStep progressStep) {
    myProgressStep = progressStep;
  }

  @Override
  public void configure(@NotNull InstallContext installContext, @NotNull AndroidSdkHandler sdkHandler) {
    myProgressStep.getProgressIndicator().setIndeterminate(true);
    myProgressStep.getProgressIndicator().setText("Creating Android virtual device");
    installContext.print("Creating Android virtual device\n", ConsoleViewContentType.SYSTEM_OUTPUT);

    try {
      AvdInfo avd = createAvd(AvdManagerConnection.getAvdManagerConnection(sdkHandler), sdkHandler);
      if (avd == null) {
        throw new WizardException("Unable to create Android virtual device");
      }
      String successMessage = String.format("Android virtual device %s was successfully created\n", avd.getName());
      installContext.print(successMessage, ConsoleViewContentType.SYSTEM_OUTPUT);
    }
    catch (WizardException e) {
      LOG.error(e);
      String failureMessage = String.format("Unable to create a virtual device: %s\n", e.getMessage());
      installContext.print(failureMessage, ConsoleViewContentType.ERROR_OUTPUT);
    }
  }

  @Override
  protected boolean isSelectedByDefault(@Nullable AndroidSdkHandler sdkHandler) {
    if (sdkHandler == null) {
      return false;
    }
    SystemImageDescription desired;
    try {
      desired = getSystemImageDescription(sdkHandler);
    }
    catch (WizardException e) {
      // ignore, error will be shown during configure if they opt to try to create.
      return false;
    }

    AvdManagerConnection connection = AvdManagerConnection.getAvdManagerConnection(sdkHandler);
    List<AvdInfo> avds = connection.getAvds(false);
    for (AvdInfo avd : avds) {
      if (avd.getAbiType().equals(desired.getAbiType()) && avd.getTarget() != null
          && avd.getTarget().getVersion().equals(desired.getVersion())) {
        // We have a similar avd already installed. Deselect by default.
        return false;
      }
    }
    return true;
  }
}
