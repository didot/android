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

import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.DOT_WEBP;
import static com.android.SdkConstants.FD_EMULATOR;
import static com.android.SdkConstants.FD_LIB;
import static com.android.SdkConstants.FD_TOOLS;
import static com.android.SdkConstants.FN_HARDWARE_INI;
import static com.android.SdkConstants.FN_SKIN_LAYOUT;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_AVD_ID;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_DISPLAY_NAME;
import static com.android.sdklib.repository.targets.SystemImage.AUTOMOTIVE_TAG;
import static com.android.sdklib.repository.targets.SystemImage.CHROMEOS_TAG;
import static com.android.sdklib.repository.targets.SystemImage.DEFAULT_TAG;
import static com.android.sdklib.repository.targets.SystemImage.GOOGLE_APIS_TAG;
import static com.android.sdklib.repository.targets.SystemImage.GOOGLE_APIS_X86_TAG;
import static com.android.sdklib.repository.targets.SystemImage.PLAY_STORE_TAG;
import static com.android.sdklib.repository.targets.SystemImage.TV_TAG;
import static com.android.sdklib.repository.targets.SystemImage.WEAR_TAG;

import com.android.annotations.VisibleForTesting;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.io.FileOp;
import com.android.sdklib.FileOpFileWrapper;
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.tools.idea.device.DeviceArtDescriptor;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * State store keys for the AVD Manager wizards
 */
public class AvdWizardUtils {

  public static final String WIZARD_ONLY = "AvdManager.WizardOnly.";

  // Avd option keys
  public static final String DEVICE_DEFINITION_KEY = WIZARD_ONLY + "DeviceDefinition";
  public static final String SYSTEM_IMAGE_KEY = WIZARD_ONLY + "SystemImage";

  public static final String RAM_STORAGE_KEY = AvdManager.AVD_INI_RAM_SIZE;
  public static final String VM_HEAP_STORAGE_KEY = AvdManager.AVD_INI_VM_HEAP_SIZE;
  public static final String INTERNAL_STORAGE_KEY = AvdManager.AVD_INI_DATA_PARTITION_SIZE;
  public static final String SD_CARD_STORAGE_KEY = AvdManager.AVD_INI_SDCARD_SIZE;
  public static final String EXISTING_SD_LOCATION = AvdManager.AVD_INI_SDCARD_PATH;

  // Keys used for display properties within the wizard. The values are derived from (and used to derive) the values for
  // SD_CARD_STORAGE_KEY and EXISTING_SD_LOCATION
  public static final String DISPLAY_SD_SIZE_KEY = WIZARD_ONLY + "displaySdCardSize";
  public static final String DISPLAY_SD_LOCATION_KEY = WIZARD_ONLY + "displaySdLocation";
  public static final String DISPLAY_USE_EXTERNAL_SD_KEY = WIZARD_ONLY + "displayUseExistingSd";

  public static final String DEFAULT_ORIENTATION_KEY = WIZARD_ONLY + "DefaultOrientation";

  public static final String AVD_INI_NETWORK_SPEED = "runtime.network.speed";
  public static final String NETWORK_SPEED_KEY = AVD_INI_NETWORK_SPEED;
  public static final String AVD_INI_NETWORK_LATENCY = "runtime.network.latency";
  public static final String NETWORK_LATENCY_KEY = AVD_INI_NETWORK_LATENCY;

  public static final String FRONT_CAMERA_KEY = AvdManager.AVD_INI_CAMERA_FRONT;
  public static final String BACK_CAMERA_KEY = AvdManager.AVD_INI_CAMERA_BACK;

  public static final String USE_HOST_GPU_KEY = AvdManager.AVD_INI_GPU_EMULATION;
  public static final String HOST_GPU_MODE_KEY = AvdManager.AVD_INI_GPU_MODE;

  public static final String USE_COLD_BOOT = AvdManager.AVD_INI_FORCE_COLD_BOOT_MODE;
  public static final String USE_FAST_BOOT = AvdManager.AVD_INI_FORCE_FAST_BOOT_MODE;
  public static final String USE_CHOSEN_SNAPSHOT_BOOT = AvdManager.AVD_INI_FORCE_CHOSEN_SNAPSHOT_BOOT_MODE;
  public static final String CHOSEN_SNAPSHOT_FILE = AvdManager.AVD_INI_CHOSEN_SNAPSHOT_FILE;
  public static final String COLD_BOOT_ONCE_VALUE = AvdManager.AVD_INI_COLD_BOOT_ONCE;

  public static final String IS_IN_EDIT_MODE_KEY = WIZARD_ONLY + "isInEditMode";

  public static final String CUSTOM_SKIN_FILE_KEY = AvdManager.AVD_INI_SKIN_PATH;
  public static final String BACKUP_SKIN_FILE_KEY = AvdManager.AVD_INI_BACKUP_SKIN_PATH;
  public static final String DEVICE_FRAME_KEY = "showDeviceFrame";

  public static final String DISPLAY_NAME_KEY = AVD_INI_DISPLAY_NAME;
  public static final String AVD_ID_KEY = AVD_INI_AVD_ID;

  public static final String CPU_CORES_KEY = AvdManager.AVD_INI_CPU_CORES;

  // Device definition keys

  public static final String HAS_HARDWARE_KEYBOARD_KEY = HardwareProperties.HW_KEYBOARD;

  // Fonts
  public static final Font STANDARD_FONT = JBFont.create(new Font("Sans", Font.PLAIN, 12));
  public static final Font FIGURE_FONT = JBFont.create(new Font("Sans", Font.PLAIN, 10));
  public static final Font TITLE_FONT = JBFont.create(new Font("Sans", Font.BOLD, 16));

  // Tags
  public static final List<IdDisplay> ALL_DEVICE_TAGS = ImmutableList.of(DEFAULT_TAG, WEAR_TAG, TV_TAG, CHROMEOS_TAG, AUTOMOTIVE_TAG);
  public static final List<IdDisplay> TAGS_WITH_GOOGLE_API = ImmutableList.of(GOOGLE_APIS_TAG, GOOGLE_APIS_X86_TAG,
                                                                              PLAY_STORE_TAG, TV_TAG, WEAR_TAG, CHROMEOS_TAG,
                                                                              AUTOMOTIVE_TAG);

  public static final String CREATE_SKIN_HELP_LINK = "http://developer.android.com/tools/devices/managing-avds.html#skins";

  public static final File NO_SKIN = new File("_no_skin");

  // The AVD wizard needs a bit of extra width as its options panel is pretty dense
  private static final Dimension AVD_WIZARD_MIN_SIZE = JBUI.size(600, 400);
  private static final Dimension AVD_WIZARD_SIZE = JBUI.size(1000, 650);

  private static final String AVD_WIZARD_HELP_URL = "https://developer.android.com/r/studio-ui/avd-manager.html";

  /** Maximum amount of RAM to *default* an AVD to, if the physical RAM on the device is higher */
  private static final int MAX_RAM_MB = 1536;

  private static final Revision MIN_SNAPSHOT_MANAGEMENT_VERSION = new Revision(27, 2, 5);
  private static final Revision MIN_WEBP_VERSION = new Revision(25, 2, 3);

  private static Map<String, HardwareProperties.HardwareProperty> ourHardwareProperties; // Hardware Properties

  private static Logger getLog() {
    return Logger.getInstance(AvdWizardUtils.class);
  }


  /**
   * Get the default amount of ram to use for the given hardware in an AVD. This is typically
   * the same RAM as is used in the hardware, but it is maxed out at {@link #MAX_RAM_MB} since more than that
   * is usually detrimental to development system performance and most likely not needed by the
   * emulated app (e.g. it's intended to let the hardware run smoothly with lots of services and
   * apps running simultaneously)
   *
   * @param hardware the hardware to look up the default amount of RAM on
   * @return the amount of RAM to default an AVD to for the given hardware
   */
  @NotNull
  public static Storage getDefaultRam(@NotNull Hardware hardware) {
    return getMaxPossibleRam(hardware.getRam());
  }

  /**
   * Get the default amount of ram to use for the given hardware in an AVD. This is typically
   * the same RAM as is used in the hardware, but it is maxed out at {@link #MAX_RAM_MB} since more than that
   * is usually detrimental to development system performance and most likely not needed by the
   * emulated app (e.g. it's intended to let the hardware run smoothly with lots of services and
   * apps running simultaneously)
   *
   * @return the amount of RAM to default an AVD to for the given hardware
   */
  @NotNull
  public static Storage getMaxPossibleRam() {
    return new Storage(MAX_RAM_MB, Storage.Unit.MiB);
  }

  /**
   * Limits the ram to {@link #MAX_RAM_MB}
   */
  @NotNull
  private static Storage getMaxPossibleRam(Storage ram) {
    if (ram.getSizeAsUnit(Storage.Unit.MiB) >= MAX_RAM_MB) {
      return new Storage(MAX_RAM_MB, Storage.Unit.MiB);
    }
    return ram;
  }

  /**
   * Return the max number of cores that an AVD can use on this development system.
   */
  public static int getMaxCpuCores() {
    return Runtime.getRuntime().availableProcessors() / 2;
  }
  /**
   * Get the default value of hardware property from hardware-properties.ini.
   *
   * @param name the name of the requested hardware property
   * @return the default value
   */
  @Nullable
  public static String getHardwarePropertyDefaultValue(String name, @Nullable AndroidSdkHandler sdkHandler) {
    if (ourHardwareProperties == null && sdkHandler != null) {
      // get the list of possible hardware properties
      // The file is in the emulator component
      LocalPackage emulatorPackage = sdkHandler.getLocalPackage(FD_EMULATOR, new StudioLoggerProgressIndicator(AvdWizardUtils.class));
      if (emulatorPackage != null) {
        File hardwareDefs = new File(emulatorPackage.getLocation(), FD_LIB + File.separator + FN_HARDWARE_INI);
        FileOp fop = sdkHandler.getFileOp();
        ourHardwareProperties = HardwareProperties.parseHardwareDefinitions(
          new FileOpFileWrapper(hardwareDefs, fop, false), new LogWrapper(Logger.getInstance(AvdManagerConnection.class)));
      }
    }
    HardwareProperties.HardwareProperty hwProp = (ourHardwareProperties == null) ? null : ourHardwareProperties.get(name);
    return (hwProp == null) ? null : hwProp.getDefault();
  }

  /**
   * Get a version of {@code candidateBase} modified such that it is a valid filename. Invalid characters will be
   * removed, and if requested the name will be made unique.
   *
   * @param candidateBase the name on which to base the avd name.
   * @param uniquify      if true, _n will be appended to the name if necessary to make the name unique, where n is the first
   *                      number that makes the filename unique.
   * @return The modified filename.
   */
  public static String cleanAvdName(@NotNull AvdManagerConnection connection, @NotNull String candidateBase, boolean uniquify) {
    candidateBase = AvdNameVerifier.stripBadCharactersAndCollapse(candidateBase);
    if (candidateBase.isEmpty()) {
      candidateBase = "myavd";
    }
    String candidate = candidateBase;
    if (uniquify) {
      int i = 1;
      while (connection.avdExists(candidate)) {
        candidate = String.format(Locale.US, "%1$s_%2$d", candidateBase, i++);
      }
    }
    return candidate;
  }

  /**
   * Determine where the skins are and ensure they're current.
   *
   * @param deviceFile the name of the hardware device
   * @param image the system image holding the skins
   * @param fop FileOp to use
   * @return where the (possibly updated) skins are. Generally this is in the SDK.
   */
  @Nullable
  public static File pathToUpdatedSkins(@Nullable File deviceFile, @Nullable SystemImageDescription image, @NotNull FileOp fop) {
    if (deviceFile == null || deviceFile.getPath().isEmpty()) {
      return deviceFile;
    }
    if (FileUtil.filesEqual(deviceFile, NO_SKIN)) {
      return NO_SKIN;
    }
    if (deviceFile.isAbsolute()) {
      return deviceFile;
    }
    if (image != null) {
      File[] skins = image.getSkins();
      for (File skin : skins) {
        if (skin.getPath().endsWith(File.separator + deviceFile.getPath())) {
          return skin;
        }
      }
    }
    // Find the resource in the Studio distribution
    File resourcePath = null;
    File resourceParent = DeviceArtDescriptor.getBundledDescriptorsFolder();
    if (resourceParent != null) {
      // Unfortunately, some Wear devices use one name for the resource directory
      // and different name for the skin directory. Remap those.
      String deviceName = deviceFile.getPath();
      if (deviceName.equals("AndroidWearSquare")) {
        deviceName = "wear_square";
      }
      else if (deviceName.equals("AndroidWearRound")) {
        deviceName = "wear_round";
      }
      resourcePath = new File(resourceParent, deviceName);
    }
    // Find the local SDK and the directory for the local copy of the skin.
    AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    File skinDir = null;
    if (sdkData != null) {
      File sdkDir = sdkData.getLocation();
      File sdkSkinsDir = new File(sdkDir, "skins");
      skinDir = new File(sdkSkinsDir, deviceFile.getPath());
    }
    boolean webpOk = (sdkData != null) && emulatorSupportsWebp(sdkData.getSdkHandler());
    return ensureSkinsAreCurrent(resourcePath, skinDir, deviceFile, webpOk, fop);
  }

  /**
   * Ensure that the skin images for this hardware device are current.
   *
   * If the skin files don't exist in the SDK, or if the skin files in the SDK
   * are old, then copy the skin files from the Studio resource into the SDK.
   *
   * @param resourcePath where these skin files are in the Studio distribution.
   * @param skinDestination where the skin files should be
   * @param deviceName the hardware device that the skins represent
   * @param emulatorCanDecodeWebp true if our version of the emulator supports WebP files
   * @param fop the FileOp to use
   * @return where to find the skin files for use in the emulator
   */
  @VisibleForTesting
  @Nullable
  public static File ensureSkinsAreCurrent(@Nullable File resourcePath,
                                           @Nullable File skinDestination,
                                           @Nullable File deviceName,
                                           boolean emulatorCanDecodeWebp,
                                           @NotNull FileOp fop) {
    if (resourcePath == null) {
      return (skinDestination == null) ? deviceName : skinDestination;
    }
    if (skinDestination == null) {
      return resourcePath;
    }
    // The resource and destination paths both exist.
    if (fop.exists(skinDestination)) {
      // The destination skin directory already exists. Check if its files are up to date.
      File resourceLayout = new File(resourcePath, FN_SKIN_LAYOUT);
      File destLayout = new File(skinDestination, FN_SKIN_LAYOUT);
      if (!resourceLayout.exists() ||
          (destLayout.exists() && destLayout.lastModified() >= resourceLayout.lastModified())) {
        // The 'dest/layout' file is up to date. Assume the other 'dest/' files are, also.
        return skinDestination;
      }
      // The resource and destination directories exists, but the destination has old files.
      // Remove the destination directory. We'll re-create it below.
      fop.deleteFileOrFolder(skinDestination);
    }
    // Create the destination skin directory and populate it from
    // the resource.
    try {
      fop.mkdirs(skinDestination);
      if (!emulatorCanDecodeWebp) {
        // Convert webp skin files to PNG for older versions of the emulator.
        convertWebpSkinToPng(fop, skinDestination, resourcePath);
      }
      else {
        // Normal copy
        for (File src : fop.listFiles(resourcePath)) {
          File target = new File(skinDestination, src.getName());
          if (fop.isFile(src)) {
            fop.copyFile(src, target);
          }
        }
      }
      return skinDestination;
    }
    catch (IOException e) {
      getLog().warn(String.format("Failed to copy skin directory to %1$s, using studio-relative path %2$s",
                                  skinDestination, resourcePath));
    }
    return resourcePath;
  }

  @VisibleForTesting
  static boolean emulatorSupportsWebp(@NotNull AndroidSdkHandler sdkHandler) {
    return emulatorVersionIsAtLeast(sdkHandler, MIN_WEBP_VERSION);
  }

  static boolean emulatorSupportsSnapshotManagement(@NotNull AndroidSdkHandler sdkHandler) {
    return emulatorVersionIsAtLeast(sdkHandler, MIN_SNAPSHOT_MANAGEMENT_VERSION);
  }

  private static boolean emulatorVersionIsAtLeast(@NotNull AndroidSdkHandler sdkHandler, Revision minRevision) {
    ProgressIndicator log = new StudioLoggerProgressIndicator(AvdWizardUtils.class);
    LocalPackage sdkPackage = sdkHandler.getLocalPackage(FD_EMULATOR, log);
    if (sdkPackage == null) {
      sdkPackage = sdkHandler.getLocalPackage(FD_TOOLS, log);
    }
    if (sdkPackage != null) {
      return sdkPackage.getVersion().compareTo(minRevision) >= 0;
    }
    return false;
  }

  /**
   * Copies a skin folder from the internal device data folder over to the SDK skin folder, rewriting
   * the webp files to PNG, and rewriting the layout file to reference webp instead.
   *
   * @param fop          the file operation to use to conduct I/O
   * @param dest         the destination folder to write the skin files to
   * @param resourcePath the source folder to read skin files from
   * @throws IOException if there's a problem
   */
  @VisibleForTesting
  static void convertWebpSkinToPng(@NotNull FileOp fop, @NotNull File dest, @NotNull File resourcePath) throws IOException {
    File[] files = fop.listFiles(resourcePath);
    Map<String,String> renameMap = Maps.newHashMap();
    File skinFile = null;
    for (File src : files) {
      String name = src.getName();
      if (name.equals(FN_SKIN_LAYOUT)) {
        skinFile = src;
        continue;
      }

      if (name.endsWith(DOT_WEBP)) {
        // Convert WEBP to PNG until emulator supports it
        try (InputStream inputStream = new BufferedInputStream(fop.newFileInputStream(src))) {
          BufferedImage icon = ImageIO.read(inputStream);
          if (icon != null) {
            File target = new File(dest, name.substring(0, name.length() - DOT_WEBP.length()) + DOT_PNG);
            try (BufferedOutputStream outputStream = new BufferedOutputStream(fop.newFileOutputStream(target))) {
              ImageIO.write(icon, "PNG", outputStream);
              renameMap.put(name, target.getName());
              continue;
            }
          }
        }
      }

      // Normal copy: either the file is not a webp or skin file (for example, some skins such as the
      // wear ones are not in webp format), or it's a webp file where we couldn't
      // (a) decode the webp file (for example if there's a problem loading the native library doing webp
      // decoding, or (b) there was an I/O error writing the PNG file. In that case we'll leave the file in
      // webp format (current emulators support it.)
      File target = new File(dest, name);
      if (fop.isFile(src) && !fop.exists(target)) {
        fop.copyFile(src, target);
      }
    }

    if (skinFile != null) {
      // Replace skin paths
      try (InputStream inputStream = new BufferedInputStream(fop.newFileInputStream(skinFile))) {
        File target = new File(dest, skinFile.getName());
        try (BufferedOutputStream outputStream = new BufferedOutputStream(fop.newFileOutputStream(target))) {
          byte[] bytes = ByteStreams.toByteArray(inputStream);
          String layout = new String(bytes, Charsets.UTF_8);
          for (Map.Entry<String, String> entry : renameMap.entrySet()) {
            layout = layout.replace(entry.getKey(), entry.getValue());
          }
          outputStream.write(layout.getBytes(Charsets.UTF_8));
        }
      }
    }
  }

  /**
   * Creates a {@link ModelWizardDialog} containing all the steps needed to create a new AVD
   */
  public static ModelWizardDialog createAvdWizard(@Nullable Component parent,
                                                  @Nullable Project project) {
    return createAvdWizard(parent, project, new AvdOptionsModel(null));
  }

  /**
   * Creates a {@link ModelWizardDialog} containing all the steps needed to create or edit AVDs
   */
  public static ModelWizardDialog createAvdWizard(@Nullable Component parent,
                                                  @Nullable Project project,
                                                  @Nullable AvdInfo avdInfo) {
    return createAvdWizard(parent, project, new AvdOptionsModel(avdInfo));
  }

  /**
   * Creates a {@link ModelWizardDialog} containing all the steps needed to create or edit AVDs
   */
  public static ModelWizardDialog createAvdWizard(@Nullable Component parent,
                                                  @Nullable Project project,
                                                  @NotNull AvdOptionsModel model) {
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    if (!model.isInEditMode().get()) {
      wizardBuilder.addStep(new ChooseDeviceDefinitionStep(model));
      wizardBuilder.addStep(new ChooseSystemImageStep(model, project));
    }
    wizardBuilder.addStep(new ConfigureAvdOptionsStep(project, model));
    ModelWizard wizard = wizardBuilder.build();
    StudioWizardDialogBuilder builder = new StudioWizardDialogBuilder(wizard, "Virtual Device Configuration", parent);
    builder.setMinimumSize(AVD_WIZARD_MIN_SIZE);
    builder.setPreferredSize(AVD_WIZARD_SIZE);
    return builder.setHelpUrl(WizardUtils.toUrl(AVD_WIZARD_HELP_URL)).build();
  }

  /**
   * Creates a {@link ModelWizardDialog} containing all the steps needed to duplicate
   * an existing AVD
   */
  public static ModelWizardDialog createAvdWizardForDuplication(@Nullable Component parent,
                                                                @Nullable Project project,
                                                                @NotNull  AvdInfo avdInfo) {
    AvdOptionsModel avdOptions = new AvdOptionsModel(avdInfo);

    // Set this AVD as a copy
    avdOptions.setAsCopy();

    return createAvdWizard(parent, project, avdOptions);
  }

}
