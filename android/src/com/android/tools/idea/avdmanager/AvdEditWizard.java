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
package com.android.tools.idea.avdmanager;

import com.android.resources.ScreenOrientation;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SystemImage;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.tools.idea.sdk.wizard.SdkComponentInstallPath;
import com.android.tools.idea.wizard.DynamicWizard;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.android.tools.idea.wizard.SingleStepPath;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.componentsList.layout.Orientation;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.*;

/**
 * Wizard for creating/editing AVDs
 */
public class AvdEditWizard extends DynamicWizard {
  @Nullable private final AvdInfo myAvdInfo;
  private final boolean myForceCreate;

  public AvdEditWizard(@Nullable Project project, @Nullable Module module, @Nullable AvdInfo avdInfo, boolean forceCreate) {
    super(project, module, "AvdEditWizard");
    myAvdInfo = avdInfo;
    myForceCreate = forceCreate;
    setTitle("Virtual Device Configuration");
  }

  @Override
  public void init() {
    if (myAvdInfo != null) {
      fillExistingInfo(myAvdInfo);
    } else {
      initDefaultInfo();
    }
    addPath(new AvdConfigurationPath(getDisposable()));
    addPath(new SdkComponentInstallPath(getDisposable()));
    addPath(new SingleStepPath(new ConfigureAvdOptionsStep(getDisposable())));

    super.init();
  }

  /**
   * Init the wizard with a set of reasonable defaults
   */
  private void initDefaultInfo() {
    ScopedStateStore state = getState();
    state.put(SCALE_SELECTION_KEY, DEFAULT_SCALE);
    state.put(NETWORK_SPEED_KEY, DEFAULT_NETWORK_SPEED);
    state.put(NETWORK_LATENCY_KEY, DEFAULT_NETWORK_LATENCY);
    state.put(FRONT_CAMERA_KEY, DEFAULT_CAMERA);
    state.put(BACK_CAMERA_KEY, DEFAULT_CAMERA);
    state.put(INTERNAL_STORAGE_KEY, DEFAULT_INTERNAL_STORAGE);
    state.put(IS_IN_EDIT_MODE_KEY, false);
    state.put(USE_HOST_GPU_KEY, true);
    state.put(SD_CARD_STORAGE_KEY, new Storage(30, Storage.Unit.MiB));
  }

  /**
   * Init the wizard by filling in the information from the given AVD
   */
  private void fillExistingInfo(@NotNull AvdInfo avdInfo) {
    ScopedStateStore state = getState();
    List<Device> devices = DeviceManagerConnection.getDevices();
    Device selectedDevice = null;
    String manufacturer = avdInfo.getDeviceManufacturer();
    String deviceId = avdInfo.getProperties().get(AvdManager.AVD_INI_DEVICE_NAME);
    for (Device device : devices) {
      if (manufacturer.equals(device.getManufacturer()) && deviceId.equals(device.getId())) {
        selectedDevice = device;
        break;
      }
    }
    state.put(DEVICE_DEFINITION_KEY, selectedDevice);
    IAndroidTarget target = avdInfo.getTarget();
    if (target != null) {
      ISystemImage selectedImage = target.getSystemImage(avdInfo.getTag(), avdInfo.getAbiType());
      SystemImageDescription systemImageDescription = new SystemImageDescription(target, selectedImage);
      state.put(SYSTEM_IMAGE_KEY, systemImageDescription);
    }

    Map<String, String> properties = avdInfo.getProperties();

    state.put(RAM_STORAGE_KEY, getStorageFromIni(properties.get(RAM_STORAGE_KEY.name)));
    state.put(VM_HEAP_STORAGE_KEY, getStorageFromIni(properties.get(VM_HEAP_STORAGE_KEY.name)));
    state.put(INTERNAL_STORAGE_KEY, getStorageFromIni(properties.get(INTERNAL_STORAGE_KEY.name)));

    String sdCardLocation = null;
    if (properties.get(EXISTING_SD_LOCATION.name) != null) {
      sdCardLocation = properties.get(EXISTING_SD_LOCATION.name);
    } else if (properties.get(SD_CARD_STORAGE_KEY.name) != null) {
      sdCardLocation = FileUtil.join(avdInfo.getDataFolderPath(), "sdcard.img");
    }
    state.put(EXISTING_SD_LOCATION, sdCardLocation);
    if (sdCardLocation != null) {
      state.put(USE_EXISTING_SD_CARD, true);
    }
    String scale = properties.get(SCALE_SELECTION_KEY.name);
    if (scale != null) {
      state.put(SCALE_SELECTION_KEY, AvdScaleFactor.findByValue(scale));
    }
    state.put(USE_HOST_GPU_KEY, fromIniString(properties.get(USE_HOST_GPU_KEY.name)));
    state.put(USE_SNAPSHOT_KEY, fromIniString(properties.get(USE_SNAPSHOT_KEY.name)));
    state.put(FRONT_CAMERA_KEY, properties.get(FRONT_CAMERA_KEY.name));
    state.put(BACK_CAMERA_KEY, properties.get(BACK_CAMERA_KEY.name));
    state.put(NETWORK_LATENCY_KEY, properties.get(NETWORK_LATENCY_KEY.name));
    state.put(NETWORK_SPEED_KEY, properties.get(NETWORK_SPEED_KEY.name));
    state.put(DISPLAY_NAME_KEY, AvdManagerConnection.getAvdDisplayName(avdInfo));

    String skinPath = properties.get(CUSTOM_SKIN_FILE_KEY.name);
    if (skinPath != null) {
      File skinFile = new File(skinPath);
      if (skinFile.isDirectory()) {
        state.put(CUSTOM_SKIN_FILE_KEY, skinFile);
      }
    }
    state.put(IS_IN_EDIT_MODE_KEY, true);
  }

  /**
   * Decodes the given string from the INI file and returns a {@link Storage} of
   * corresponding size.
   */
  @Nullable
  private static Storage getStorageFromIni(String iniString) {
    if (iniString == null) {
      return null;
    }
    String numString = iniString.substring(0, iniString.length() - 1);
    char unitChar = iniString.charAt(iniString.length() - 1);
    Storage.Unit selectedUnit = Storage.Unit.B;
    for (Storage.Unit u : Storage.Unit.values()) {
      if (u.toString().charAt(0) == unitChar) {
        selectedUnit = u;
        break;
      }
    }
    try {
      long numLong = Long.parseLong(numString);
      return new Storage(numLong, selectedUnit);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public void performFinishingActions() {
    ScopedStateStore state = getState();
    Device device = state.get(DEVICE_DEFINITION_KEY);
    assert device != null; // Validation should be done by individual steps
    SystemImageDescription systemImageDescription = state.get(SYSTEM_IMAGE_KEY);
    assert systemImageDescription != null;
    ScreenOrientation orientation = state.get(DEFAULT_ORIENTATION_KEY);
    assert orientation != null;

    Map<String, String> hardwareProperties = DeviceManager.getHardwareProperties(device);
    Map<String, Object> userEditedProperties = state.flatten();

    // Remove the SD card setting that we're not using
    String sdCard = null;
    Boolean useExistingSdCard = state.get(USE_EXISTING_SD_CARD);
    if (useExistingSdCard != null && useExistingSdCard) {
      userEditedProperties.remove(SD_CARD_STORAGE_KEY.name);
      sdCard = state.get(EXISTING_SD_LOCATION);
      assert sdCard != null;
    } else {
      userEditedProperties.remove(EXISTING_SD_LOCATION.name);
      Storage storage = state.get(SD_CARD_STORAGE_KEY);
      if (storage != null) {
        sdCard = toIniString(storage);
      }
    }

    // Remove any internal keys from the map
    userEditedProperties = Maps.filterEntries(userEditedProperties, new Predicate<Map.Entry<String, Object>>() {
      @Override
      public boolean apply(Map.Entry<String, Object> input) {
        return !input.getKey().startsWith(WIZARD_ONLY) && input.getValue() != null;
      }
    });
    // Call toString() on all remaining values
    hardwareProperties.putAll(Maps.transformEntries(userEditedProperties, new Maps.EntryTransformer<String, Object, String>() {
      @Override
      public String transformEntry(String key, Object value) {
        if (value instanceof Storage) {
          return toIniString((Storage)value);
        } else if (value instanceof  Boolean) {
          return toIniString((Boolean)value);
        } else if (value instanceof AvdScaleFactor) {
          return toIniString((AvdScaleFactor)value);
        } else if (value instanceof File) {
          return toIniString((File)value);
        } else if (value instanceof Double) {
          return toIniString((Double)value);
        } else {
          return value.toString();
        }
      }
    }));

    File skinFile = state.get(CUSTOM_SKIN_FILE_KEY);

    // Add any values that we can calculate
    hardwareProperties.put(AvdManager.AVD_INI_SKIN_DYNAMIC, toIniString(false));
    hardwareProperties.put(HardwareProperties.HW_KEYBOARD, toIniString(false));

    boolean isCircular = DeviceDefinitionPreview.isCircular(device);

    String avdName = calculateAvdName(myAvdInfo, device, myForceCreate);

    // If we're editing an AVD and we downgrade a system image, wipe the user data with confirmation
    if (myAvdInfo != null && !myForceCreate) {
      IAndroidTarget target = myAvdInfo.getTarget();
      if (target != null) {

        int oldApiLevel = target.getVersion().getApiLevel();
        int newApiLevel = systemImageDescription.target.getVersion().getApiLevel();
        if (oldApiLevel > newApiLevel) {
          String message = String.format(Locale.getDefault(), "You are about to downgrade %1$s from API level %2$d to API level %3$d. " +
                                                              "This requires a wipe of the userdata partition of the AVD. Do you wish to " +
                                                              "continue with the data wipe?", avdName, oldApiLevel, newApiLevel);
          int result = JOptionPane
            .showConfirmDialog(null, message, "Confirm Data Wipe", JOptionPane.YES_NO_OPTION);
          if (result == JOptionPane.YES_OPTION) {
            AvdManagerConnection.wipeUserData(myAvdInfo);
          } else {
            return; // Cancel the edit operation
          }
        }
      }
    }

    AvdManagerConnection.createOrUpdateAvd(myAvdInfo, avdName, device, systemImageDescription, orientation, isCircular, sdCard,
                                           skinFile, hardwareProperties, false);
  }

  @NotNull
  private static String toIniString(@NotNull Double value) {
    return String.format(Locale.US, "%f", value);
  }

  @NotNull
  private static String toIniString(@NotNull File value) {
    return value.getPath();
  }

  /**
   * Encode the given value as a string that can be placed in the AVD's INI file.
   */
  @NotNull
  private static String toIniString(@NotNull AvdScaleFactor value) {
    return value.getValue();
  }

  @NotNull
  private static String calculateAvdName(@Nullable AvdInfo avdInfo, @NotNull Device device, boolean forceCreate) {
    if (avdInfo != null && !forceCreate) {
      return avdInfo.getName();
    }
    String deviceName = device.getDisplayName().replace(' ', '_');
    String manufacturer = device.getManufacturer().replace(' ', '_');
    String candidateBase = String.format("AVD_for_%1$s_by_%2$s", deviceName, manufacturer);
    candidateBase = candidateBase.replaceAll("[^0-9a-zA-Z_-]+", " ").trim().replaceAll("[ _]+", "_");
    String candidate = candidateBase;
    int i = 1;
    while (AvdManagerConnection.avdExists(candidate)) {
      candidate = String.format("%1$s_%2$d", candidateBase, i);
    }
    return candidate;
  }

  /**
   * Encode the given value as a string that can be placed in the AVD's INI file.
   * Example: 10M or 1G
   */
  @NotNull
  private static String toIniString(@NotNull Storage storage) {
    Storage.Unit unit = storage.getAppropriateUnits();
    return String.format("%1$d%2$c", storage.getSizeAsUnit(unit), unit.toString().charAt(0));
  }

  /**
   * Encode the given value as a string that can be placed in the AVD's INI file.
   */
  @NotNull
  private static String toIniString(@NotNull Boolean b) {
    return b ? "yes" : "no";
  }

  private static boolean fromIniString(@Nullable String s) {
    return "yes".equals(s);
  }

  @Override
  protected String getWizardActionDescription() {
    return "Create/Edit an Android Virtual Device";
  }
}
