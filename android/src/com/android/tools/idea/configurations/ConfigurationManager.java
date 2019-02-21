/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.Blocking;
import com.android.ide.common.rendering.api.Bridge;
import com.android.ide.common.resources.ResourceRepositoryUtil;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.targets.PlatformTarget;
import com.android.tools.idea.model.ActivityAttributesSnapshot;
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import javax.annotation.concurrent.GuardedBy;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.uipreview.UserDeviceManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.tools.idea.configurations.ConfigurationListener.*;

/**
 * A {@linkplain ConfigurationManager} is responsible for managing {@link Configuration}
 * objects for a given project.
 * <p>
 * Whereas a {@link Configuration} is tied to a specific render target or theme,
 * the {@linkplain ConfigurationManager} knows the set of available targets, themes,
 * locales etc. for the current project.
 * <p>
 * The {@linkplain ConfigurationManager} is also responsible for storing and retrieving
 * the saved configuration state for a given file.
 */
public class ConfigurationManager implements Disposable {
  private static final Key<ConfigurationManager> KEY = Key.create(ConfigurationManager.class.getName());

  @NotNull private final Module myModule;

  @NotNull private Collection<Device> mySdkDevices = Collections.emptyList();
  @NotNull private Map<String,Device> mySdkDevicesById = Collections.emptyMap();
  private final Map<VirtualFile, Configuration> myCache = ContainerUtil.createSoftValueMap();
  private final Object myLocalesLock = new Object();
  @GuardedBy("myLocalesLock")
  @NotNull private ImmutableList<Locale> myLocales = ImmutableList.of();
  @GuardedBy("myLocalesLock")
  private long myLocalesCacheStamp = -1;
  private Device myDefaultDevice;
  private Locale myLocale;
  private IAndroidTarget myTarget;
  private int myStateVersion;
  private ResourceResolverCache myResolverCache;

  @NotNull
  public static ConfigurationManager getOrCreateInstance(@NotNull AndroidFacet androidFacet) {
    return findConfigurationManager(androidFacet, true /* create if necessary */);
  }

  // TODO: Migrate to use the version that takes an AndroidFacet
  @NotNull
  public static ConfigurationManager getOrCreateInstance(@NotNull Module module) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet == null) {
      throw new IllegalArgumentException("Module '" + module.getName() + "' is not an Android module");
    }
    return findConfigurationManager(androidFacet, true /* create if necessary */);
  }

  // TODO: Migrate to use a version that takes an AndroidFacet
  @Nullable
  public static ConfigurationManager findExistingInstance(@NotNull Module module) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet == null) {
      throw new IllegalArgumentException("Module '" + module.getName() + "' is not an Android module");
    }
    return findConfigurationManager(androidFacet, false /* do not create if not found */);
  }

  @Contract("_, true -> !null")
  @Nullable
  private static ConfigurationManager findConfigurationManager(@NotNull AndroidFacet androidFacet, boolean createIfNecessary) {
    Module module = androidFacet.getModule();

    ConfigurationManager configurationManager = module.getUserData(KEY);
    if (configurationManager == null && createIfNecessary) {
      configurationManager = new ConfigurationManager(module);
      module.putUserData(KEY, configurationManager);
    }
    return configurationManager;
  }

  public ConfigurationManager(@NotNull Module module) {
    myModule = module;
    Disposer.register(myModule, this);
  }

  /**
   * Gets the {@link Configuration} associated with the given file
   * @return the {@link Configuration} for the given file
   */
  @Blocking
  @NotNull
  public Configuration getConfiguration(@NotNull VirtualFile file) {
    Configuration configuration = myCache.get(file);
    if (configuration == null) {
      configuration = create(file);
      myCache.put(file, configuration);
    }

    return configuration;
  }

  @VisibleForTesting
  boolean hasCachedConfiguration(@NotNull VirtualFile file) {
    return myCache.get(file) != null;
  }

  /**
   * Creates and returns a new {@link Configuration} associated with this manager.
   * This method might block while finding the correct {@link Device} for the {@link Configuration}. Finding
   * devices requires accessing (and maybe updating) the repository of existing ones.
   */
  @Blocking
  @NotNull
  private Configuration create(@NotNull VirtualFile file) {
    ConfigurationStateManager stateManager = getStateManager();
    ConfigurationFileState fileState = stateManager.getConfigurationState(file);
    assert file.getParent() != null : file;
    FolderConfiguration config = FolderConfiguration.getConfigForFolder(file.getParent().getName());
    if (config == null) {
      config = new FolderConfiguration();
    }
    Configuration configuration = Configuration.create(this, file, fileState, config);
    ConfigurationMatcher matcher = new ConfigurationMatcher(configuration, file);
    if (fileState != null) {
      matcher.adaptConfigSelection(true);
    } else {
      matcher.findAndSetCompatibleConfig(false);
    }

    return configuration;
  }

  /**
   * Similar to {@link #getConfiguration(VirtualFile)}, but creates a configuration
   * for a file known to be new, and crucially, bases the configuration on the existing configuration
   * for a known file. This is intended for when you fork a layout, and you expect the forked layout
   * to have a configuration that is (as much as possible) similar to the configuration of the
   * forked file. For example, if you create a landscape version of a layout, it will preserve the
   * screen size, locale, theme and render target of the existing layout.
   *
   * @param file the file to create a configuration for
   * @param baseFile the other file to base the configuration on
   * @return the new configuration
   */
  @NotNull
  public Configuration createSimilar(@NotNull VirtualFile file, @NotNull VirtualFile baseFile) {
    ConfigurationStateManager stateManager = getStateManager();
    ConfigurationFileState fileState = stateManager.getConfigurationState(baseFile);
    FolderConfiguration config = FolderConfiguration.getConfigForFolder(file.getParent().getName());
    if (config == null) {
      config = new FolderConfiguration();
    }
    Configuration configuration = Configuration.create(this, file, fileState, config);
    Configuration baseConfig = myCache.get(file);
    if (baseConfig != null) {
      configuration.setEffectiveDevice(baseConfig.getDevice(), baseConfig.getDeviceState());
    }
    ConfigurationMatcher matcher = new ConfigurationMatcher(configuration, file);
    matcher.adaptConfigSelection(true /*needBestMatch*/);
    myCache.put(file, configuration);

    return configuration;
  }

  /** Returns the associated persistence manager */
  public ConfigurationStateManager getStateManager() {
    return ConfigurationStateManager.get(getModule().getProject());
  }

  /** Returns the list of available devices for the current platform and any custom user devices, if any */
  @Blocking
  @NotNull
  public ImmutableList<Device> getDevices() {
    return new ImmutableList.Builder<Device>()
      .addAll(updateAndGetPlatformDevices())
      .addAll(UserDeviceManager.getInstance(getProject()).getUserDevices())
      .build();
  }

  @Blocking
  @NotNull
  public Collection<Device> updateAndGetPlatformDevices() {
    if (mySdkDevices.isEmpty()) {
      AndroidPlatform platform = AndroidPlatform.getInstance(getModule());
      if (platform != null) {
        final DeviceManager deviceManager = platform.getSdkData().getDeviceManager();
        mySdkDevices = deviceManager.getDevices(EnumSet.of(DeviceManager.DeviceFilter.DEFAULT, DeviceManager.DeviceFilter.VENDOR));
      }
    }

    return mySdkDevices;
  }

  @NotNull
  private ImmutableMap<String,Device> getDeviceMap() {
    ImmutableMap.Builder<String, Device> deviceMapBuilder = new ImmutableMap.Builder<>();
    for (Device device : getDevices()) {
      deviceMapBuilder.put(device.getId(), device);
    }
    return deviceMapBuilder.build();
  }

  @Nullable
  public Device getDeviceById(@NotNull String id) {
    return getDeviceMap().get(id);
  }

  @Nullable
  public Device createDeviceForAvd(@NotNull AvdInfo avd) {
    AndroidFacet facet = AndroidFacet.getInstance(getModule());
    assert facet != null;
    for (Device device : getDevices()) {
      if (device.getManufacturer().equals(avd.getDeviceManufacturer())
          && (device.getId().equals(avd.getDeviceName()) || device.getDisplayName().equals(avd.getDeviceName()))) {

        String avdName = avd.getName();
        Device.Builder builder = new Device.Builder(device);
        builder.setName(avdName);
        builder.setId(Configuration.AVD_ID_PREFIX + avdName);
        return builder.build();
      }
    }

    return null;
  }

  public static boolean isAvdDevice(@NotNull Device device) {
    return device.getId().startsWith(Configuration.AVD_ID_PREFIX);
  }

  /**
   * Returns all the {@link IAndroidTarget} instances applicable for the current module.
   * Note that this may include non-rendering targets, so for layout rendering contexts,
   * check individual members by calling {@link #isLayoutLibTarget(IAndroidTarget)} first.
   */
  @NotNull
  public IAndroidTarget[] getTargets() {
    AndroidPlatform platform = AndroidPlatform.getInstance(getModule());
    if (platform != null) {
      final AndroidSdkData sdkData = platform.getSdkData();

      return sdkData.getTargets();
    }

    return new IAndroidTarget[0];
  }

  public static boolean isLayoutLibTarget(@NotNull IAndroidTarget target) {
    return target.isPlatform() && target.hasRenderingLibrary();
  }

  @Nullable
  public IAndroidTarget getHighestApiTarget() {
    // Note: The target list is already sorted in ascending API order.
    IAndroidTarget[] targetList = getTargets();
    for (int i = targetList.length - 1; i >= 0; i--) {
      IAndroidTarget target = targetList[i];
      if (isLayoutLibTarget(target) && isLayoutLibSupported(target)) {
        return target;
      }
    }

    return null;
  }

  /**
   * Returns if the LayoutLib API (not to be confused with Platform API) level is supported.
   */
  private static boolean isLayoutLibSupported(IAndroidTarget target) {
    if (target instanceof PlatformTarget) {
      int layoutlibVersion = ((PlatformTarget)target).getLayoutlibApi();
      return layoutlibVersion <= Bridge.API_CURRENT;
    }
    return false;
  }

  /**
   * Returns the preferred theme
   */
  @NotNull
  public String computePreferredTheme(@NotNull Configuration configuration) {
    MergedManifestSnapshot manifest = MergedManifestManager.getSnapshot(getModule());

    // TODO: If we are rendering a layout in included context, pick the theme from the outer layout instead.

    String activity = configuration.getActivity();
    if (activity != null) {
      String activityFqcn = activity;
      if (activity.startsWith(".")) {
        String pkg = StringUtil.notNullize(manifest.getPackage());
        activityFqcn = pkg + activity;
      }

      ActivityAttributesSnapshot attributes = manifest.getActivityAttributes(activityFqcn);
      if (attributes != null) {
        String theme = attributes.getTheme();
        // Check that the theme looks like a reference.
        if (theme != null && theme.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
          return theme;
        }
      }

      // Try with the package name from the manifest.
      attributes = manifest.getActivityAttributes(activity);
      if (attributes != null) {
        String theme = attributes.getTheme();
        // Check that the theme looks like a reference.
        if (theme != null && theme.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
          return theme;
        }
      }
    }

    // Look up the default/fallback theme to use for this project (which depends on
    // the screen size when no particular theme is specified in the manifest).
    return manifest.getDefaultTheme(configuration.getTarget(), configuration.getScreenSize(), configuration.getDevice());
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public Project getProject() {
    return getModule().getProject();
  }

  @Override
  public void dispose() {
    getModule().putUserData(KEY, null);
  }

  @Nullable
  public Device getDefaultDevice() {
    if (myDefaultDevice == null) {
      // Note that this may not be the device actually used in new layouts; the ConfigMatcher
      // has a PhoneComparator which sorts devices for a best match
      List<Device> devices = getDevices();
      if (!devices.isEmpty()) {
        Device device = devices.get(0);
        for (Device d : devices) {
          String id = d.getId();
          if (id.equals("pixel")) {
            device = d;
            break;
          } else if (id.equals("Galaxy Nexus")) {
            device = d;
          }
        }

        myDefaultDevice = device;
      }
    }

    return myDefaultDevice;
  }

  /**
   * Return the default render target to use, or null if no strong preference
   */
  @Nullable
  public IAndroidTarget getDefaultTarget() {
    // Use the most recent target
    return getHighestApiTarget();
  }

  @NotNull
  public ImmutableList<Locale> getLocales() {
    // Get locales from modules, but not libraries!
    LocalResourceRepository projectResources = ResourceRepositoryManager.getProjectResources(getModule());
    assert projectResources != null;
    synchronized (myLocalesLock) {
      if (projectResources.getModificationCount() == myLocalesCacheStamp) {
        return myLocales;
      }
    }

    long modificationCount = projectResources.getModificationCount();
    ImmutableList<Locale> locales = ResourceRepositoryUtil.getLocales(projectResources)
      .stream()
      .map(Locale::create)
      .collect(ImmutableList.toImmutableList());

    synchronized (myLocalesLock) {
      myLocales = locales;
      myLocalesCacheStamp = modificationCount;

      return myLocales;
    }
  }

  @Nullable
  public IAndroidTarget getProjectTarget() {
    AndroidPlatform platform = AndroidPlatform.getInstance(getModule());
    return platform != null ? platform.getTarget() : null;
  }

  @NotNull
  public Locale getLocale() {
    if (myLocale == null) {
      String localeString = getStateManager().getProjectState().getLocale();
      if (localeString != null) {
        myLocale = ConfigurationProjectState.fromLocaleString(localeString);
      } else {
        myLocale = Locale.ANY;
      }
    }

    return myLocale;
  }

  public void setLocale(@NotNull Locale locale) {
    if (!locale.equals(myLocale)) {
      myLocale = locale;
      myStateVersion++;
      getStateManager().getProjectState().setLocale(ConfigurationProjectState.toLocaleString(locale));
      for (Configuration configuration : myCache.values()) {
        configuration.updated(CFG_LOCALE);
      }
    }
  }

  /** Returns the most recently used devices, in MRU order */
  public List<Device> getRecentDevices() {
    List<String> deviceIds = getStateManager().getProjectState().getDeviceIds();
    if (deviceIds.isEmpty()) {
      return Collections.emptyList();
    }

    List<Device> devices = Lists.newArrayListWithExpectedSize(deviceIds.size());
    ListIterator<String> iterator = deviceIds.listIterator();
    while (iterator.hasNext()) {
      String id = iterator.next();
      Device device = getDeviceById(id);
      if (device != null) {
        devices.add(device);
      } else {
        iterator.remove();
      }
    }

    return devices;
  }

  public void selectDevice(@NotNull Device device) {
    // Manually move the given device to the front of the eligibility queue
    String id = device.getId();
    List<String> deviceIds = getStateManager().getProjectState().getDeviceIds();
    deviceIds.remove(id);
    deviceIds.add(0, id);

    // Only store a limited number of recent devices
    while (deviceIds.size() > 10) {
      deviceIds.remove(deviceIds.size() - 1);
    }

    myStateVersion++;
    for (Configuration configuration : myCache.values()) {
      // TODO: Null out the themes too if using a system theme (e.g. where the theme was not chosen
      // by the activity or manifest default, but inferred based on the device and API level).
      // For example, if you switch from an Android Wear device (where the default is DeviceDefault) to
      // a Nexus 5 (where the default is currently Theme.Holo) we should recompute the theme for the
      // configuration too!
      boolean updateTheme = false;
      String theme = configuration.getTheme();
      if (theme.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
        updateTheme = true;
        configuration.startBulkEditing();
        configuration.setTheme(null);
      }

      configuration.updated(CFG_DEVICE);

      if (updateTheme) {
        configuration.finishBulkEditing();
      }
    }
  }

  @Nullable
  public IAndroidTarget getTarget() {
    if (myTarget == null) {
      ConfigurationProjectState projectState = getStateManager().getProjectState();
      if (projectState.isPickTarget()) {
        myTarget = getDefaultTarget();
      } else {
        String targetString = projectState.getTarget();
        myTarget = ConfigurationProjectState.fromTargetString(this, targetString);
        if (myTarget == null) {
          myTarget = getDefaultTarget();
        }
      }
      return myTarget;
    }

    return myTarget;
  }

  /** Returns the best render target to use for the given minimum API level */
  @Nullable
  public IAndroidTarget getTarget(int min) {
    IAndroidTarget target = getTarget();
    if (target != null && target.getVersion().getApiLevel() >= min) {
      return target;
    }

    IAndroidTarget[] targetList = getTargets();
    for (int i = targetList.length - 1; i >= 0; i--) {
      target = targetList[i];
      if (isLayoutLibTarget(target) && target.getVersion().getFeatureLevel() >= min && isLayoutLibSupported(target)) {
        return target;
      }
    }

    return null;
  }

  public void setTarget(@Nullable IAndroidTarget target) {
    if (target != myTarget) {
      if (myTarget != null) {
        // Clear out the bitmap cache of the previous platform, since it's likely we won't
        // need it again. If you have *two* projects open with different platforms, this will
        // needlessly flush the bitmap cache for the project still using it, but that just
        // means the next render will need to fetch them again; from that point on both platform
        // bitmap sets are in memory.
        AndroidTargetData targetData = AndroidTargetData.getTargetData(myTarget, getModule());
        if (targetData != null) {
          targetData.clearLayoutBitmapCache(getModule());
        }
      }

      myTarget = target;
      if (target != null) {
        getStateManager().getProjectState().setTarget(ConfigurationProjectState.toTargetString(target));
        myStateVersion++;
        for (Configuration configuration : myCache.values()) {
          configuration.updated(CFG_TARGET);
        }
      }
    }
  }

  /**
   * Synchronizes changes to the given attributes (indicated by the mask
   * referencing the {@code CFG_} configuration attribute bit flags in
   * {@link Configuration} to the layout variations of the given updated file.
   *
   * @param flags the attributes which were updated
   * @param updatedFile the file which was updated
   * @param base the base configuration to base the chooser off of
   * @param includeSelf whether the updated file itself should be updated
   * @param async whether the updates should be performed asynchronously
   */
  public void syncToVariations(
    final int flags,
    final @NotNull VirtualFile updatedFile,
    final @NotNull Configuration base,
    final boolean includeSelf,
    boolean async) {
    if (async) {
      ApplicationManager.getApplication().runReadAction(() -> doSyncToVariations(flags, updatedFile, includeSelf, base));
    } else {
      doSyncToVariations(flags, updatedFile, includeSelf, base);
    }
  }

  private void doSyncToVariations(@SuppressWarnings("UnusedParameters") int flags,
                                  VirtualFile updatedFile, boolean includeSelf,
                                  Configuration base) {
    // Synchronize the given changes to other configurations as well
    List<VirtualFile> files = ResourceHelper.getResourceVariations(updatedFile, includeSelf);
    for (VirtualFile file : files) {
      Configuration configuration = getConfiguration(file);
      Configuration.copyCompatible(base, configuration);
      configuration.save();
    }
  }

  public int getStateVersion() {
    return myStateVersion;
  }

  public ResourceResolverCache getResolverCache() {
    if (myResolverCache == null) {
      myResolverCache = new ResourceResolverCache(this);
    }

    return myResolverCache;
  }
}
