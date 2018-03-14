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
package com.android.tools.idea.profilers.analytics;

import com.android.sdklib.AndroidVersion;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.NullMonitorStage;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioMonitorStage;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.ProfilingConfiguration;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.*;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StudioFeatureTracker implements FeatureTracker {

  @Nullable
  private Common.Device myActiveDevice;

  @Nullable
  private Common.Process myActiveProcess;

  private final ImmutableMap<Class<? extends Stage>, AndroidProfilerEvent.Stage> STAGE_MAP =
    ImmutableMap.<Class<? extends Stage>, AndroidProfilerEvent.Stage>builder()
      .put(NullMonitorStage.class, AndroidProfilerEvent.Stage.NULL_STAGE)
      .put(StudioMonitorStage.class, AndroidProfilerEvent.Stage.OVERVIEW_STAGE)
      .put(CpuProfilerStage.class, AndroidProfilerEvent.Stage.CPU_STAGE)
      .put(MemoryProfilerStage.class, AndroidProfilerEvent.Stage.MEMORY_STAGE)
      .put(NetworkProfilerStage.class, AndroidProfilerEvent.Stage.NETWORK_STAGE)
      .build();

  @NotNull
  private AndroidProfilerEvent.Stage myCurrStage = AndroidProfilerEvent.Stage.UNKNOWN_STAGE;

  @Override
  public void trackEnterStage(@NotNull Class<? extends Stage> stage) {
    myCurrStage = STAGE_MAP.getOrDefault(stage, AndroidProfilerEvent.Stage.UNKNOWN_STAGE);
    track(AndroidProfilerEvent.Type.STAGE_ENTERED);
  }

  @Override
  public void trackRunWithProfiling() {
    track(AndroidProfilerEvent.Type.RUN_WITH_PROFILING);
  }

  @Override
  public void trackProfilingStarted() {
    newTracker(AndroidProfilerEvent.Type.PROFILING_STARTED).setDevice(myActiveDevice).track();
  }

  @Override
  public void trackAdvancedProfilingStarted() {
    newTracker(AndroidProfilerEvent.Type.ADVANCED_PROFILING_STARTED).setDevice(myActiveDevice).track();
  }

  @Override
  public void trackChangeDevice(@Nullable Common.Device device) {
    if (myActiveDevice != device) {
      myActiveDevice = device;
      newTracker(AndroidProfilerEvent.Type.CHANGE_DEVICE).setDevice(myActiveDevice).track();
    }
  }

  @Override
  public void trackChangeProcess(@Nullable Common.Process process) {
    if (myActiveProcess != process) {
      myActiveProcess = process;
      newTracker(AndroidProfilerEvent.Type.CHANGE_PROCESS).setDevice(myActiveDevice).track();
    }
  }

  @Override
  public void trackGoBack() {
    track(AndroidProfilerEvent.Type.GO_BACK);
  }

  @Override
  public void trackSelectMonitor() {
    track(AndroidProfilerEvent.Type.SELECT_MONITOR);
  }

  @Override
  public void trackZoomIn() {
    track(AndroidProfilerEvent.Type.ZOOM_IN);
  }

  @Override
  public void trackZoomOut() {
    track(AndroidProfilerEvent.Type.ZOOM_OUT);
  }

  @Override
  public void trackResetZoom() {
    track(AndroidProfilerEvent.Type.ZOOM_RESET);
  }

  @Override
  public void trackToggleStreaming() {
    track(AndroidProfilerEvent.Type.GO_LIVE);
  }

  @Override
  public void trackNavigateToCode() {
    track(AndroidProfilerEvent.Type.NAVIGATE_TO_CODE);
  }

  @Override
  public void trackSelectRange() {
    // We set the device when tracking range selection because we need to distinguish selections made on pre-O and post-O devices.
    newTracker(AndroidProfilerEvent.Type.SELECT_RANGE).setDevice(myActiveDevice).track();
  }

  @Override
  public void trackCaptureTrace(@NotNull com.android.tools.profilers.cpu.CpuCaptureMetadata cpuCaptureMetadata) {
    newTracker(AndroidProfilerEvent.Type.CAPTURE_TRACE).setDevice(myActiveDevice).setCpuCaptureMetadata(cpuCaptureMetadata).track();
  }

  @Override
  public void trackSelectThread() {
    track(AndroidProfilerEvent.Type.SELECT_THREAD);
  }

  @Override
  public void trackSelectCaptureTopDown() {
    track(AndroidProfilerEvent.Type.SELECT_TOP_DOWN);
  }

  @Override
  public void trackSelectCaptureBottomUp() {
    track(AndroidProfilerEvent.Type.SELECT_BOTTOM_UP);
  }

  @Override
  public void trackSelectCaptureFlameChart() {
    track(AndroidProfilerEvent.Type.SELECT_FLAME_CHART);
  }

  @Override
  public void trackSelectCaptureCallChart() {
    track(AndroidProfilerEvent.Type.SELECT_CALL_CHART);
  }

  @Override
  public void trackForceGc() {
    track(AndroidProfilerEvent.Type.FORCE_GC);
  }

  @Override
  public void trackDumpHeap() {
    track(AndroidProfilerEvent.Type.SNAPSHOT_HPROF);
  }

  @Override
  public void trackRecordAllocations() {
    track(AndroidProfilerEvent.Type.CAPTURE_ALLOCATIONS);
  }

  @Override
  public void trackExportHeap() {
    track(AndroidProfilerEvent.Type.EXPORT_HPROF);
  }

  @Override
  public void trackExportAllocation() {
    track(AndroidProfilerEvent.Type.EXPORT_ALLOCATION);
  }

  @Override
  public void trackChangeClassArrangment() {
    track(AndroidProfilerEvent.Type.ARRANGE_CLASSES);
  }

  @Override
  public void trackSelectMemoryStack() {
    track(AndroidProfilerEvent.Type.SELECT_MEMORY_STACK);
  }

  @Override
  public void trackSelectMemoryReferences() {
    track(AndroidProfilerEvent.Type.SELECT_MEMORY_REFERENCES);
  }

  @Override
  public void trackSelectMemoryHeap(@NotNull String heapName) {
    AndroidProfilerEvent.MemoryHeap heapType;
    switch (heapName) {
      case CaptureObject.DEFAULT_HEAP_NAME:
        heapType = AndroidProfilerEvent.MemoryHeap.DEFAULT_HEAP;
        break;
      case CaptureObject.APP_HEAP_NAME:
        heapType = AndroidProfilerEvent.MemoryHeap.APP_HEAP;
        break;
      case CaptureObject.IMAGE_HEAP_NAME:
        heapType = AndroidProfilerEvent.MemoryHeap.IMAGE_HEAP;
        break;
      case CaptureObject.ZYGOTE_HEAP_NAME:
        heapType = AndroidProfilerEvent.MemoryHeap.ZYGOTE_HEAP;
        break;
      case CaptureObject.JNI_HEAP_NAME:
        heapType = AndroidProfilerEvent.MemoryHeap.JNI_HEAP;
        break;
      default:
        getLogger().error("Attempt to report selection of unknown heap name: " + heapName);
        return;
    }
    newTracker(AndroidProfilerEvent.Type.SELECT_MEMORY_HEAP).setMemoryHeapId(heapType).track();
  }

  @Override
  public void trackSelectNetworkRequest() {
    track(AndroidProfilerEvent.Type.SELECT_CONNECTION);
  }

  @Override
  public void trackSelectNetworkDetailsOverview() {
    track(AndroidProfilerEvent.Type.SELECT_DETAILS_OVERVIEW);
  }

  @Override
  public void trackSelectNetworkDetailsHeaders() {
    track(AndroidProfilerEvent.Type.SELECT_DETAILS_HEADERS);
  }

  @Override
  public void trackSelectNetworkDetailsResponse() {
    track(AndroidProfilerEvent.Type.SELECT_DETAILS_RESPONSE);
  }

  @Override
  public void trackSelectNetworkDetailsRequest() {
    track(AndroidProfilerEvent.Type.SELECT_DETAILS_REQUEST);
  }

  @Override
  public void trackSelectNetworkDetailsStack() {
    track(AndroidProfilerEvent.Type.SELECT_DETAILS_STACK);
  }

  @Override
  public void trackSelectNetworkDetailsError() {
    track(AndroidProfilerEvent.Type.SELECT_DETAILS_ERROR);
  }

  @Override
  public void trackSelectNetworkConnectionsView() {
    track(AndroidProfilerEvent.Type.SELECT_CONNECTIONS_CONNECTION_VIEW);
  }

  @Override
  public void trackSelectNetworkThreadsView() {
    track(AndroidProfilerEvent.Type.SELECT_CONNECTIONS_THREADS_VIEW);
  }

  @Override
  public void trackOpenProfilingConfigDialog() {
    track(AndroidProfilerEvent.Type.OPEN_CPU_CONFIG_DIALOG);
  }

  @Override
  public void trackCreateCustomProfilingConfig() {
    track(AndroidProfilerEvent.Type.CREATE_CPU_CONFIG);
  }

  @Override
  public void trackFilterMetadata(@NotNull com.android.tools.profilers.analytics.FilterMetadata filterMetadata) {
    newTracker(AndroidProfilerEvent.Type.FILTER).setFilterMetadata(filterMetadata).track();
  }

  /**
   * Convenience method for creating a new tracker with all the minimum data supplied.
   */
  @NotNull
  private Tracker newTracker(AndroidProfilerEvent.Type eventType) {
    return new Tracker(eventType, myCurrStage);
  }

  /**
   * Convenience method for the most common tracking scenario (just an event with no extra data).
   * If other data should be sent with this message, explicitly create a {@link Tracker} and use
   * {@link Tracker#track()} instead.
   */
  private void track(AndroidProfilerEvent.Type eventType) {
    newTracker(eventType).track();
  }

  private static final class Tracker {
    @NotNull private final AndroidProfilerEvent.Type myEventType;
    @NotNull private final AndroidProfilerEvent.Stage myCurrStage;
    @Nullable private Common.Device myDevice;
    @Nullable private com.android.tools.profilers.cpu.CpuCaptureMetadata myCpuCaptureMetadata;
    @Nullable private com.android.tools.profilers.analytics.FilterMetadata myFeatureMetadata;
    private AndroidProfilerEvent.MemoryHeap myMemoryHeap = AndroidProfilerEvent.MemoryHeap.UNKNOWN_HEAP;

    public Tracker(@NotNull AndroidProfilerEvent.Type eventType, @NotNull AndroidProfilerEvent.Stage stage) {
      myEventType = eventType;
      myCurrStage = stage;
    }

    @NotNull
    public Tracker setDevice(@Nullable Common.Device device) {
      myDevice = device;
      return this;
    }

    @NotNull
    public Tracker setCpuCaptureMetadata(@Nullable com.android.tools.profilers.cpu.CpuCaptureMetadata cpuCaptureMetadata) {
      myCpuCaptureMetadata = cpuCaptureMetadata;
      return this;
    }

    @NotNull
    public Tracker setFilterMetadata(@Nullable com.android.tools.profilers.analytics.FilterMetadata filterMetadata) {
      myFeatureMetadata = filterMetadata;
      return this;
    }

    @NotNull
    public Tracker setMemoryHeapId(AndroidProfilerEvent.MemoryHeap heap) {
      myMemoryHeap = heap;
      return this;
    }

    public void track() {
      AndroidProfilerEvent.Builder profilerEvent = AndroidProfilerEvent.newBuilder().setStage(myCurrStage).setType(myEventType);
      populateCpuCaptureMetadata(profilerEvent);
      populateFilterMetadata(profilerEvent);
      if (myEventType == AndroidProfilerEvent.Type.SELECT_MEMORY_HEAP) {
        profilerEvent.setMemoryHeap(myMemoryHeap);
      }
      AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.ANDROID_PROFILER)
        .setAndroidProfilerEvent(profilerEvent);

      if (myDevice != null) {
        event.setDeviceInfo(
          DeviceInfo.newBuilder()
            .setManufacturer(myDevice.getManufacturer())
            .setModel(myDevice.getModel())
            .setBuildVersionRelease(myDevice.getVersion())
            .setBuildApiLevelFull(new AndroidVersion(myDevice.getApiLevel(), myDevice.getCodename()).getApiString())
            .setDeviceType(myDevice.getIsEmulator() ? DeviceInfo.DeviceType.LOCAL_EMULATOR : DeviceInfo.DeviceType.LOCAL_PHYSICAL)
            .build());
      }

      UsageTracker.getInstance().log(event);
    }

    private void populateFilterMetadata(AndroidProfilerEvent.Builder profilerEvent) {
      if (myFeatureMetadata != null) {
        FilterMetadata.Builder filterMetadata = FilterMetadata.newBuilder();
        filterMetadata.setFeaturesUsed(myFeatureMetadata.getFeaturesUsed());
        filterMetadata.setMatchedElements(myFeatureMetadata.getMatchedElementCount());
        filterMetadata.setTotalElements(myFeatureMetadata.getTotalElementCount());
        filterMetadata.setSearchLength(myFeatureMetadata.getFilterTextLength());
        switch (myFeatureMetadata.getView()) {
          case UNKNOWN_FILTER_VIEW:
            filterMetadata.setActiveView(FilterMetadata.View.UNKNOWN_FILTER_VIEW);
            break;
          case CPU_TOP_DOWN:
            filterMetadata.setActiveView(FilterMetadata.View.CPU_TOP_DOWN);
            break;
          case CPU_BOTTOM_UP:
            filterMetadata.setActiveView(FilterMetadata.View.CPU_BOTTOM_UP);
            break;
          case CPU_FLAME_CHART:
            filterMetadata.setActiveView(FilterMetadata.View.CPU_FLAME_CHART);
            break;
          case CPU_CALL_CHART:
            filterMetadata.setActiveView(FilterMetadata.View.CPU_CALL_CHART);
            break;
          case MEMORY_CALLSTACK:
            filterMetadata.setActiveView(FilterMetadata.View.MEMORY_CALLSTACK);
            break;
          case MEMORY_PACKAGE:
            filterMetadata.setActiveView(FilterMetadata.View.MEMORY_PACKAGE);
            break;
          case MEMORY_CLASS:
            filterMetadata.setActiveView(FilterMetadata.View.MEMORY_CLASS);
            break;
          case NETWORK_CONNECTIONS:
            filterMetadata.setActiveView(FilterMetadata.View.NETWORK_CONNECTIONS);
            break;
          case NETWORK_THREADS:
            filterMetadata.setActiveView(FilterMetadata.View.NETWORK_THREADS);
            break;
        }
        profilerEvent.setFilterMetadata(filterMetadata);
      }
    }

    private void populateCpuCaptureMetadata(AndroidProfilerEvent.Builder profilerEvent) {
      if (myCpuCaptureMetadata != null) {
        CpuCaptureMetadata.Builder captureMetadata = CpuCaptureMetadata.newBuilder()
          .setCaptureDurationMs(myCpuCaptureMetadata.getCaptureDurationMs())
          .setRecordDurationMs(myCpuCaptureMetadata.getRecordDurationMs())
          .setTraceFileSizeBytes(myCpuCaptureMetadata.getTraceFileSizeBytes())
          .setParsingTimeMs(myCpuCaptureMetadata.getParsingTimeMs());

        switch (myCpuCaptureMetadata.getStatus()) {
          case SUCCESS:
            captureMetadata.setCaptureStatus(CpuCaptureMetadata.CaptureStatus.SUCCESS);
            break;
          case PARSING_FAILURE:
            captureMetadata.setCaptureStatus(CpuCaptureMetadata.CaptureStatus.PARSING_FAILURE);
            break;
          case STOP_CAPTURING_FAILURE:
            captureMetadata.setCaptureStatus(CpuCaptureMetadata.CaptureStatus.STOP_CAPTURING_FAILURE);
            break;
          case USER_ABORTED_PARSING:
            captureMetadata.setCaptureStatus(CpuCaptureMetadata.CaptureStatus.USER_ABORTED_PARSING);
            break;
        }

        ProfilingConfiguration config = myCpuCaptureMetadata.getProfilingConfiguration();
        CpuProfilingConfig.Builder cpuConfigInfo = CpuProfilingConfig.newBuilder()
          .setSampleInterval(config.getProfilingSamplingIntervalUs())
          .setSizeLimit(config.getProfilingBufferSizeInMb());

        switch (config.getProfilerType()) {
          case ART:
            cpuConfigInfo.setType(CpuProfilingConfig.Type.ART);
            break;
          case SIMPLEPERF:
            cpuConfigInfo.setType(CpuProfilingConfig.Type.SIMPLE_PERF);
            break;
          case ATRACE:
            // TODO: Setup config for ATRACE, this needs logs access.
            //cpuConfigInfo.setType(CpuProfilingConfig.Type.ATRACE);
            break;
          case UNSPECIFIED_PROFILER:
          case UNRECOGNIZED:
            break;
        }

        switch (config.getMode()) {
          case SAMPLED:
            cpuConfigInfo.setMode(CpuProfilingConfig.Mode.SAMPLED);
            break;
          case INSTRUMENTED:
            cpuConfigInfo.setMode(CpuProfilingConfig.Mode.INSTRUMENTED);
            break;
          case UNSTATED:
          case UNRECOGNIZED:
            break;
        }
        captureMetadata.setProfilingConfig(cpuConfigInfo.build());

        profilerEvent.setCpuCaptureMetadata(captureMetadata);
      }
    }
  }

  private final static Logger getLogger() {
    return Logger.getInstance(StudioFeatureTracker.class);
  }
}
