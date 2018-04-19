/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType;
import com.android.tools.profilers.StudioProfilers;
import com.google.common.collect.Iterables;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is responsible for managing the profiling configuration for the CPU profiler. It is shared between
 * {@link CpuProfilerStage} and CpuProfilingConfigurationsDialog.
 */
public class CpuProfilerConfigModel {
  private static final String LAST_SELECTED_CONFIGURATION_NAME = "last.selected.configuration.name";

  /**
   * Represents the selected configuration or the configuration being used in the case of ongoing trace recording.
   * While a trace recording in progress, it is not possible to change {@link #myProfilingConfiguration}, i.e it is achieved
   * by disabling the dropdown in UI. Thus, in most cases the selected configuration is the same as
   * the configuration used for the ongoing recording.
   */
  private ProfilingConfiguration myProfilingConfiguration;

  /**
   * The list of all custom configurations. The list is filtered to configurations that are available, however it does not
   * filter on device support.
   */
  @NotNull
  private List<ProfilingConfiguration> myCustomProfilingConfigurations;

  /**
   * Same as {@link #myCustomProfilingConfigurations} except we also filter on device. This is needed, for instance, to filter custom
   * configurations on the CPU profiling configurations combobox.
   */
  @NotNull
  private List<ProfilingConfiguration> myCustomProfilingConfigurationsDeviceFiltered;

  /**
   * The list of all default configurations. This list is filtered on device compatibility as well as feature availability.
   */
  @NotNull
  private List<ProfilingConfiguration> myDefaultProfilingConfigurations;

  @NotNull
  private final StudioProfilers myProfilers;

  private AspectObserver myAspectObserver;

  public CpuProfilerConfigModel(@NotNull StudioProfilers profilers, CpuProfilerStage profilerStage) {
    myProfilers = profilers;
    myCustomProfilingConfigurations = new ArrayList<>();
    myCustomProfilingConfigurationsDeviceFiltered = new ArrayList<>();
    myDefaultProfilingConfigurations = new ArrayList<>();
    myAspectObserver = new AspectObserver();
    profilerStage.getAspect().addDependency(myAspectObserver)
      .onChange(CpuProfilerAspect.PROFILING_CONFIGURATION, this::updateProfilingConfigurations);
  }

  @NotNull
  public ProfilingConfiguration getProfilingConfiguration() {
    return myProfilingConfiguration;
  }

  public void setProfilingConfiguration(@NotNull ProfilingConfiguration configuration) {
    myProfilingConfiguration = configuration;
    myProfilers.getIdeServices().getTemporaryProfilerPreferences().setValue(LAST_SELECTED_CONFIGURATION_NAME, configuration.getName());
  }

  @NotNull
  public List<ProfilingConfiguration> getCustomProfilingConfigurations() {
    return myCustomProfilingConfigurations;
  }

  @NotNull
  public List<ProfilingConfiguration> getCustomProfilingConfigurationsDeviceFiltered() {
    return myCustomProfilingConfigurationsDeviceFiltered;
  }

  @NotNull
  public List<ProfilingConfiguration> getDefaultProfilingConfigurations() {
    return myDefaultProfilingConfigurations;
  }

  public void updateProfilingConfigurations() {
    List<ProfilingConfiguration> savedConfigs = myProfilers.getIdeServices().getUserCpuProfilerConfigs();
    myCustomProfilingConfigurations = filterConfigurations(savedConfigs, false);
    myCustomProfilingConfigurationsDeviceFiltered = filterConfigurations(savedConfigs, true);

    List<ProfilingConfiguration> defaultConfigs = myProfilers.getIdeServices().getDefaultCpuProfilerConfigs();
    myDefaultProfilingConfigurations = filterConfigurations(defaultConfigs, true);

    Common.Device selectedDevice = myProfilers.getDevice();
    // Anytime before we check the device feature level we need to validate we have a device. The device will be null in test
    // causing a null pointer exception here.
    boolean isSimpleperfEnabled = selectedDevice != null && selectedDevice.getFeatureLevel() >= AndroidVersion.VersionCodes.O &&
                                  myProfilers.getIdeServices().getFeatureConfig().isSimpleperfEnabled();
    if (myProfilingConfiguration == null) {
      // First we try to get the last selected config.
      String selectedConfigName =
        myProfilers.getIdeServices().getTemporaryProfilerPreferences().getValue(LAST_SELECTED_CONFIGURATION_NAME, "");
      ProfilingConfiguration selectedConfig =
        Stream.concat(defaultConfigs.stream(), savedConfigs.stream())
              .filter(c -> c.getName().equals(selectedConfigName))
              .findFirst()
              .orElse(null);
      if (selectedConfig != null) {
        myProfilingConfiguration = selectedConfig;
      }
      else if (myProfilers.getIdeServices().isNativeProfilingConfigurationPreferred() && isSimpleperfEnabled) {
        // If there is a preference for a native configuration, we select simpleperf.
        myProfilingConfiguration =
          Iterables.find(defaultConfigs, pref -> pref != null && pref.getProfilerType() == CpuProfilerType.SIMPLEPERF);
      }
      else {
        // Otherwise we select ART sampled.
        myProfilingConfiguration =
          Iterables.find(defaultConfigs, pref -> pref != null && pref.getProfilerType() == CpuProfilerType.ART
                                                 && pref.getMode() == CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED);
      }
    }
  }

  private List<ProfilingConfiguration> filterConfigurations(List<ProfilingConfiguration> configurations, boolean filterOnDevice) {
    // Simpleperf/Atrace profiling is not supported by devices older than O
    Common.Device selectedDevice = myProfilers.getDevice();
    Predicate<ProfilingConfiguration> filter = pref -> {
      if (selectedDevice != null && pref.getRequiredDeviceLevel() > selectedDevice.getFeatureLevel() && filterOnDevice) {
        return false;
      }
      if (pref.getProfilerType() == CpuProfilerType.SIMPLEPERF) {
        return myProfilers.getIdeServices().getFeatureConfig().isSimpleperfEnabled();
      }
      if (pref.getProfilerType() == CpuProfilerType.ATRACE) {
        return myProfilers.getIdeServices().getFeatureConfig().isAtraceEnabled();
      }
      return true;
    };
    return configurations.stream().filter(filter).collect(Collectors.toList());
  }
}