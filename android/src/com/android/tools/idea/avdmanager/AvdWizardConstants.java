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

import com.android.resources.Navigation;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SystemImage;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.sdklib.repository.descriptors.PkgType;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.android.sdklib.devices.Storage.Unit;
import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * State store keys for the AVD Manager wizards
 */
public class AvdWizardConstants {
  public static final String WIZARD_ONLY = "AvdManager.WizardOnly.";

  // Avd option keys

  public static final Key<Device> DEVICE_DEFINITION_KEY = createKey(WIZARD_ONLY + "DeviceDefinition", WIZARD, Device.class);
  public static final Key<SystemImageDescription> SYSTEM_IMAGE_KEY = createKey(WIZARD_ONLY + "SystemImage", WIZARD, SystemImageDescription.class);

  public static final Key<Storage> RAM_STORAGE_KEY = createKey(AvdManager.AVD_INI_RAM_SIZE, WIZARD, Storage.class);
  public static final Key<Storage> VM_HEAP_STORAGE_KEY = createKey(AvdManager.AVD_INI_VM_HEAP_SIZE, WIZARD, Storage.class);
  public static final Key<Storage> INTERNAL_STORAGE_KEY = createKey(AvdManager.AVD_INI_DATA_PARTITION_SIZE, WIZARD, Storage.class);
  public static final Key<Storage> SD_CARD_STORAGE_KEY = createKey(AvdManager.AVD_INI_SDCARD_SIZE, WIZARD, Storage.class);
  public static final Key<String> EXISTING_SD_LOCATION = createKey(AvdManager.AVD_INI_SDCARD_PATH, WIZARD, String.class);

  // Keys used for display properties within the wizard. The values are derived from (and used to derive) the values for
  // SD_CARD_STORAGE_KEY and EXISTING_SD_LOCATION
  public static final Key<Storage> DISPLAY_SD_SIZE_KEY = createKey(WIZARD_ONLY + "displaySdCardSize", WIZARD, Storage.class);
  public static final Key<String> DISPLAY_SD_LOCATION_KEY = createKey(WIZARD_ONLY + "displaySdLocation", WIZARD, String.class);
  public static final Key<Boolean> DISPLAY_USE_EXTERNAL_SD_KEY = createKey(WIZARD_ONLY + "displayUseExistingSd", WIZARD, Boolean.class);

  public static final String AVD_INI_SCALE_FACTOR = "runtime.scalefactor";
  public static final Key<AvdScaleFactor> SCALE_SELECTION_KEY = createKey(AVD_INI_SCALE_FACTOR, WIZARD, AvdScaleFactor.class);

  public static final Key<ScreenOrientation> DEFAULT_ORIENTATION_KEY = createKey(WIZARD_ONLY + "DefaultOrientation", WIZARD, ScreenOrientation.class);

  public static final String AVD_INI_NETWORK_SPEED = "runtime.network.speed";
  public static final Key<String> NETWORK_SPEED_KEY = createKey(AVD_INI_NETWORK_SPEED, WIZARD, String.class);
  public static final String AVD_INI_NETWORK_LATENCY = "runtime.network.latency";
  public static final Key<String> NETWORK_LATENCY_KEY = createKey(AVD_INI_NETWORK_LATENCY, WIZARD, String.class);

  public static final Key<String> FRONT_CAMERA_KEY = createKey(AvdManager.AVD_INI_CAMERA_FRONT, WIZARD, String.class);
  public static final Key<String> BACK_CAMERA_KEY = createKey(AvdManager.AVD_INI_CAMERA_BACK, WIZARD, String.class);
  public static final String CHOOSE_DEVICE_DEFINITION_STEP = "Choose Device Definition Step";
  public static final String CHOOSE_SYSTEM_IMAGE_STEP = "Choose System Image Step";

  public static final Key<Boolean> USE_HOST_GPU_KEY = createKey(AvdManager.AVD_INI_GPU_EMULATION, WIZARD, Boolean.class);
  public static final Key<Boolean> USE_SNAPSHOT_KEY = createKey(AvdManager.AVD_INI_SNAPSHOT_PRESENT, WIZARD, Boolean.class);

  public static final Key<Boolean> IS_IN_EDIT_MODE_KEY = createKey(WIZARD_ONLY + "isInEditMode", WIZARD, Boolean.class);

  public static final Key<File> CUSTOM_SKIN_FILE_KEY = createKey(AvdManager.AVD_INI_SKIN_PATH, WIZARD, File.class);

  public static final Key<String> DISPLAY_NAME_KEY = createKey(AvdManagerConnection.AVD_INI_DISPLAY_NAME, WIZARD, String.class);
  public static final String AVD_INI_AVD_ID = "AvdId";
  public static final Key<String> AVD_ID_KEY = createKey(AVD_INI_AVD_ID, WIZARD, String.class);

  // Device definition keys

  public static final Key<String> DEVICE_NAME_KEY = createKey("DeviceName", STEP, String.class);

  public static final Key<Double> DIAGONAL_SCREENSIZE_KEY = createKey("DiagonalScreenSize", STEP, Double.class);
  public static final Key<Integer> RESOLUTION_WIDTH_KEY = createKey("ResolutionWidth", STEP, Integer.class);
  public static final Key<Integer> RESOLUTION_HEIGHT_KEY = createKey("ResolutionHeight", STEP, Integer.class);

  public static final Key<Boolean> HAS_HARDWARE_BUTTONS_KEY = createKey("HasHardwareButtons", STEP, Boolean.class);
  public static final Key<Boolean> HAS_HARDWARE_KEYBOARD_KEY = createKey(HardwareProperties.HW_KEYBOARD, WIZARD, Boolean.class);
  public static final Key<Navigation> NAVIGATION_KEY = createKey("Navigation", STEP, Navigation.class);

  public static final Key<Boolean> SUPPORTS_LANDSCAPE_KEY = createKey("SupportsLandscape", STEP, Boolean.class);
  public static final Key<Boolean> SUPPORTS_PORTRAIT_KEY = createKey("SupportsPortrait", STEP, Boolean.class);

  public static final Key<Boolean> HAS_BACK_CAMERA_KEY = createKey("HasBackCamera", STEP, Boolean.class);
  public static final Key<Boolean> HAS_FRONT_CAMERA_KEY = createKey("HasFrontCamera", STEP, Boolean.class);

  public static final Key<Boolean> HAS_ACCELEROMETER_KEY = createKey("HasAccelerometer", STEP, Boolean.class);
  public static final Key<Boolean> HAS_GYROSCOPE_KEY = createKey("HasGyroscope", STEP, Boolean.class);
  public static final Key<Boolean> HAS_GPS_KEY = createKey("HasGPS", STEP, Boolean.class);
  public static final Key<Boolean> HAS_PROXIMITY_SENSOR_KEY = createKey("HasProximitySensor", STEP, Boolean.class);

  public static final Key<Screen> WIP_SCREEN_KEY = createKey("ScreenUnderConstruction", STEP, Screen.class);
  public static final Key<Hardware> WIP_HARDWARE_KEY = createKey("HardwareUnderConstruction" ,STEP, Hardware.class);
  public static final Key<Double> WIP_SCREEN_DPI_KEY = createKey("ScreenDPI", STEP, Double.class);

  public static final Key<IdDisplay> TAG_ID_KEY = createKey("TagId", STEP, IdDisplay.class);

  // Defaults
  public static final AvdScaleFactor DEFAULT_SCALE = AvdScaleFactor.AUTO;
  public static final String DEFAULT_NETWORK_SPEED = "full";
  public static final String DEFAULT_NETWORK_LATENCY = "none";
  public static final String DEFAULT_CAMERA = "none";
  public static final Storage DEFAULT_INTERNAL_STORAGE = new Storage(200, Unit.MiB);

  // Fonts
  static final Font STANDARD_FONT = new Font("Sans", Font.PLAIN, 12);
  static final Font FIGURE_FONT = new Font("Sans", Font.PLAIN, 10);
  static final Font TITLE_FONT = new Font("Sans", Font.BOLD, 16);

  // Tags
  public static final IdDisplay WEAR_TAG = new IdDisplay("android-wear", "Android Wear");
  public static final IdDisplay TV_TAG = new IdDisplay("android-tv", "Android TV");

  public static final List<IdDisplay> ALL_TAGS =
    Collections.unmodifiableList(Lists.newArrayList(SystemImage.DEFAULT_TAG, WEAR_TAG, TV_TAG));

  public static final String CREATE_SKIN_HELP_LINK = "http://developer.android.com/tools/devices/managing-avds.html#skins";

  public static final File NO_SKIN = new File("_no_skin");

  public static final class SystemImageDescription {
    private IAndroidTarget target;
    private ISystemImage systemImage;
    private com.android.sdklib.internal.repository.packages.Package remotePackage;

    public SystemImageDescription(IAndroidTarget target, ISystemImage systemImage) {
      this.target = target;
      this.systemImage = systemImage;
    }

    public SystemImageDescription(com.android.sdklib.internal.repository.packages.Package remotePackage) {
      this.remotePackage = remotePackage;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(target, systemImage, remotePackage);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof SystemImageDescription)) {
        return false;
      }
      SystemImageDescription other = (SystemImageDescription) obj;
      return Objects.equal(target, other.target) && Objects.equal(systemImage, other.systemImage) &&
             Objects.equal(remotePackage, other.remotePackage);
    }

    @Nullable
    public AndroidVersion getVersion() {
      if (target != null) {
        return target.getVersion();
      } else {
        return remotePackage.getPkgDesc().getAndroidVersion();
      }
    }

    public com.android.sdklib.internal.repository.packages.Package getRemotePackage() {
      return remotePackage;
    }

    public boolean isRemote() {
      return remotePackage != null;
    }

    @Nullable
    public String getAbiType() {
      if (systemImage != null) {
        return systemImage.getAbiType();
      } else if (remotePackage.getPkgDesc().getType() == PkgType.PKG_SYS_IMAGE
              || remotePackage.getPkgDesc().getType() == PkgType.PKG_ADDON_SYS_IMAGE) {
        return remotePackage.getPkgDesc().getPath();
      } else {
        return "";
      }
    }

    @Nullable
    public IdDisplay getTag() {
      if (systemImage != null) {
        return systemImage.getTag();
      }
      return remotePackage.getPkgDesc().getTag();
    }

    public String getName() {
      if (target != null) {
        return target.getFullName();
      }
      return remotePackage.getDescription();
    }

    public String getVendor() {
      if (target != null) {
        return target.getVendor();
      }
      return "";
    }

    public String getVersionName() {
      if (target != null) {
        return target.getVersionName();
      }
      return "";
    }

    public IAndroidTarget getTarget() {
      return target;
    }

    public File[] getSkins() {
      if (target != null) {
        return target.getSkins();
      }
      return new File[0];
    }
  }
}
