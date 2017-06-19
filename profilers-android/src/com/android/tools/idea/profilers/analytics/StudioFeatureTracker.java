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
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.NullMonitorStage;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioMonitorStage;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.DeviceInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StudioFeatureTracker implements FeatureTracker {

  @Nullable
  private Profiler.Device myActiveDevice;

  @Nullable
  private Profiler.Process myActiveProcess;

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
  public void trackProfilingStarted() {
    track(AndroidProfilerEvent.Type.PROFILING_STARTED);
  }

  @Override
  public void trackAdvancedProfilingStarted() {
    track(AndroidProfilerEvent.Type.ADVANCED_PROFILING_STARTED);
  }

  @Override
  public void trackChangeDevice(@Nullable Profiler.Device device) {
    if (myActiveDevice != device) {
      myActiveDevice = device;
      track(AndroidProfilerEvent.Type.CHANGE_DEVICE, myActiveDevice);
    }
  }

  @Override
  public void trackChangeProcess(@Nullable Profiler.Process process) {
    if (myActiveProcess != process) {
      myActiveProcess = process;
      track(AndroidProfilerEvent.Type.CHANGE_PROCESS);
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
    track(AndroidProfilerEvent.Type.SELECT_RANGE);
  }

  @Override
  public void trackTraceArtSampled() {
    // TODO: rename event type to TRACE_ART_SAMPLED
    track(AndroidProfilerEvent.Type.TRACE_SAMPLED);
  }

  @Override
  public void trackTraceArtInstrumented() {
    // TODO: rename event type to TRACE_ART_INSTRUMENTED
    track(AndroidProfilerEvent.Type.TRACE_INSTRUMENTED);
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
  public void trackSelectNetworkRequest() {
    track(AndroidProfilerEvent.Type.SELECT_CONNECTION);
  }

  @Override
  public void trackSelectNetworkDetailsResponse() {
    track(AndroidProfilerEvent.Type.SELECT_DETAILS_RESPONSE);
  }

  @Override
  public void trackSelectNetworkDetailsHeaders() {
    track(AndroidProfilerEvent.Type.SELECT_DETAILS_HEADERS);
  }

  @Override
  public void trackSelectNetworkDetailsStack() {
    track(AndroidProfilerEvent.Type.SELECT_DETAILS_STACK);
  }

  @Override
  public void trackOpenProfilingConfigDialog() {
    track(AndroidProfilerEvent.Type.OPEN_CPU_CONFIG_DIALOG);
  }

  @Override
  public void trackCreateCustomProfilingConfig() {
    track(AndroidProfilerEvent.Type.CREATE_CPU_CONFIG);
  }

  private void track(AndroidProfilerEvent.Type eventType) {
    track(eventType, null);
  }


  private void track(AndroidProfilerEvent.Type eventType, @Nullable Profiler.Device device) {
    AndroidProfilerEvent profilerEvent = AndroidProfilerEvent.newBuilder().setStage(myCurrStage).setType(eventType).build();

    AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.ANDROID_PROFILER)
      .setAndroidProfilerEvent(profilerEvent);

    if (device != null) {
      event.setDeviceInfo(
        DeviceInfo.newBuilder()
          .setManufacturer(device.getManufacturer())
          .setModel(device.getModel())
          .setBuildVersionRelease(device.getVersion())
          .setBuildApiLevelFull(new AndroidVersion(device.getApiLevel(), device.getCodename()).getApiString())
          .setDeviceType(device.getIsEmulator() ? DeviceInfo.DeviceType.LOCAL_EMULATOR : DeviceInfo.DeviceType.LOCAL_PHYSICAL)
          .build());
    }

    UsageTracker.getInstance().log(event);
  }
}
