/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerMode;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType;
import com.android.utils.HashCodes;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Preferences set when start a profiling session.
 */
public class ProfilingConfiguration {
  public static final int DEFAULT_BUFFER_SIZE_MB = 8;

  public static final int DEFAULT_SAMPLING_INTERVAL_US = 1000;

  /**
   * Name to identify the profiling preference. It should be displayed in the preferences list.
   */
  private String myName;

  /**
   * Profiler type (ART or simpleperf).
   */
  private CpuProfilerType myProfilerType;

  /**
   * Profiling mode (Sampled or Instrumented).
   */
  private CpuProfilerMode myMode;

  private int myProfilingBufferSizeInMb = DEFAULT_BUFFER_SIZE_MB;

  /**
   * Sampling interval (for sample-based profiling) in microseconds.
   */
  private int myProfilingSamplingIntervalUs = DEFAULT_SAMPLING_INTERVAL_US;

  /**
   * Whether to disable live allocation during CPU recording.
   */
  private boolean myDisableLiveAllocation = true;

  public ProfilingConfiguration() {
    // Default constructor to be used by CpuProfilingConfigService
  }

  public ProfilingConfiguration(String name,
                                CpuProfilerType profilerType,
                                CpuProfilerMode mode) {
    myName = name;
    myProfilerType = profilerType;
    myMode = mode;
  }

  public CpuProfilerMode getMode() {
    return myMode;
  }

  public void setMode(CpuProfilerMode mode) {
    myMode = mode;
  }

  public CpuProfilerType getProfilerType() {
    return myProfilerType;
  }

  public void setProfilerType(CpuProfilerType profilerType) {
    myProfilerType = profilerType;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public int getProfilingBufferSizeInMb() {
    return myProfilingBufferSizeInMb;
  }

  public void setProfilingBufferSizeInMb(int profilingBufferSizeInMb) {
    myProfilingBufferSizeInMb = profilingBufferSizeInMb;
  }

  public int getProfilingSamplingIntervalUs() {
    return myProfilingSamplingIntervalUs;
  }

  public boolean isDisableLiveAllocation() {
    return myDisableLiveAllocation;
  }

  public void setDisableLiveAllocation(boolean disableLiveAllocation) {
    myDisableLiveAllocation = disableLiveAllocation;
  }

  public int getRequiredDeviceLevel() {
    switch (myProfilerType) {
      // Atrace is supported from Android 4.1 (J) minimum, however the trace events changed in Android 7.0 (M).
      // For more info see b/79212883.
      case ATRACE:
        return AndroidVersion.VersionCodes.M;
      // Simpleperf is supported from Android 8.0 (O)
      case SIMPLEPERF:
        return AndroidVersion.VersionCodes.O;
      default:
        return 0;
    }
  }

  public boolean isDeviceLevelSupported(int deviceLevel) {
    return deviceLevel >= getRequiredDeviceLevel();
  }

  public void setProfilingSamplingIntervalUs(int profilingSamplingIntervalUs) {
    myProfilingSamplingIntervalUs = profilingSamplingIntervalUs;
  }

  /**
   * Converts from {@link com.android.tools.profiler.proto.CpuProfiler.CpuProfilerConfiguration} to {@link ProfilingConfiguration}.
   */
  @NotNull
  public static ProfilingConfiguration fromProto(@NotNull CpuProfiler.CpuProfilerConfiguration proto) {
    ProfilingConfiguration configuration = new ProfilingConfiguration(proto.getName(), proto.getProfilerType(), proto.getProfilerMode());
    configuration.setProfilingSamplingIntervalUs(proto.getSamplingIntervalUs());
    configuration.setProfilingBufferSizeInMb(proto.getBufferSizeInMb());
    configuration.setDisableLiveAllocation(proto.getDisableLiveAllocation());
    return configuration;
  }

  /**
   * Converts {@code this} to {@link com.android.tools.profiler.proto.CpuProfiler.CpuProfilerConfiguration}.
   */
  @NotNull
  public CpuProfiler.CpuProfilerConfiguration toProto() {
    return CpuProfiler.CpuProfilerConfiguration
      .newBuilder()
      .setName(getName())
      .setProfilerType(getProfilerType())
      .setProfilerMode(getMode())
      .setSamplingIntervalUs(getProfilingSamplingIntervalUs())
      .setBufferSizeInMb(getProfilingBufferSizeInMb())
      .setDisableLiveAllocation(isDisableLiveAllocation())
      .build();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ProfilingConfiguration)) {
      return false;
    }
    ProfilingConfiguration incoming = (ProfilingConfiguration)obj;
    return StringUtil.equals(getName(), incoming.getName()) &&
           getProfilerType() == incoming.getProfilerType() &&
           getMode() == incoming.getMode() &&
           getProfilingSamplingIntervalUs() == incoming.getProfilingSamplingIntervalUs() &&
           getProfilingBufferSizeInMb() == incoming.getProfilingBufferSizeInMb() &&
           isDisableLiveAllocation() == incoming.isDisableLiveAllocation();
  }

  @Override
  public int hashCode() {
    return HashCodes
      .mix(Objects.hashCode(getName()), Objects.hashCode(getProfilerType()), Objects.hashCode(getMode()), getProfilingSamplingIntervalUs(),
           getProfilingBufferSizeInMb(), Boolean.hashCode(isDisableLiveAllocation()));
  }
}
