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
package com.android.tools.profilers.memory;

import static com.android.tools.profilers.StudioProfilers.DAEMON_DEVICE_DIR_PATH;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.DurationDataModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.transport.poller.TransportEventListener;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocationsInfo;
import com.android.tools.profiler.proto.MemoryProfiler.TriggerHeapDumpRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TriggerHeapDumpResponse;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.sessions.SessionAspect;
import com.google.common.annotations.VisibleForTesting;
import io.grpc.StatusRuntimeException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MainMemoryProfilerStage extends BaseStreamingMemoryProfilerStage {
  // The safe factor estimating how many times of memory is needed compared to hprof file size
  public static final int MEMORY_HPROF_SAFE_FACTOR =
    Math.max(1, Math.min(Integer.getInteger("profiler.memory.hprof.safeFactor", 10), 1000));

  /**
   * Whether the stage only contains heap dump data imported from hprof file
   */
  private final boolean myIsMemoryCaptureOnly;

  private final DurationDataModel<CaptureDurationData<CaptureObject>> myHeapDumpDurations;
  private final DurationDataModel<CaptureDurationData<CaptureObject>> myAllocationDurations;
  private final DurationDataModel<CaptureDurationData<CaptureObject>> myNativeAllocationDurations;
  private long myPendingLegacyAllocationStartTimeNs = BaseMemoryProfilerStage.INVALID_START_TIME;
  private boolean myNativeAllocationTracking = false;

  public MainMemoryProfilerStage(@NotNull StudioProfilers profilers) {
    this(profilers, new CaptureObjectLoader());
  }

  public MainMemoryProfilerStage(@NotNull StudioProfilers profilers, @NotNull CaptureObjectLoader loader) {
    super(profilers, loader);
    myIsMemoryCaptureOnly =
      profilers.getSessionsManager().getSelectedSessionMetaData().getType() == Common.SessionMetaData.SessionType.MEMORY_CAPTURE;

    // TODO(b/122964201) Pass data range as 3rd param to RangedSeries to only show data from current session
    myHeapDumpDurations = makeModel((client, session, tracker, stage) ->
                                 new HeapDumpSampleDataSeries(client, session, tracker, profilers.getIdeServices()));
    myAllocationDurations = makeModel(AllocationInfosDataSeries::new);
    myNativeAllocationDurations = makeModel(NativeAllocationSamplesSeries::new);

    myHeapDumpDurations.setRenderSeriesPredicate((data, series) ->
                                                   // Do not show the object series during a heap dump.
                                                   !series.getName().equals(getDetailedMemoryUsage().getObjectsSeries().getName())
    );

    getRangeSelectionModel().addConstraint(myAllocationDurations);
    getRangeSelectionModel().addConstraint(myNativeAllocationDurations);
    getRangeSelectionModel().addConstraint(myHeapDumpDurations);

    getStudioProfilers().getSessionsManager().addDependency(this)
      .onChange(SessionAspect.SELECTED_SESSION, this::stopRecordingOnSessionStop);
  }

  void stopRecordingOnSessionStop() {
    boolean isAlive = getStudioProfilers().getSessionsManager().isSessionAlive();
    if (!isAlive && myNativeAllocationTracking) {
      toggleNativeAllocationTracking();
    }
  }

  public boolean hasUserUsedMemoryCapture() {
    return getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().getBoolean(HAS_USED_MEMORY_CAPTURE, false);
  }

  @Override
  public void enter() {
    super.enter();
    updateAllocationTrackingStatus();
    updateNativeAllocationTrackingStatus();
  }

  @Override
  public void exit() {
    super.exit();
    enableSelectLatestCapture(false, null);
    selectCaptureDuration(null, null);
  }

  @NotNull
  @Override
  public List<DurationDataModel<CaptureDurationData<CaptureObject>>> getCaptureSeries() {
    return Arrays.asList(myAllocationDurations, myHeapDumpDurations, myNativeAllocationDurations);
  }

  @Override
  protected void selectCaptureFromSelectionRange() {
    if (!getUpdateCaptureOnSelection()) {
      return;
    }

    setUpdateCaptureOnSelection(false);
    Range selectionRange = getTimeline().getSelectionRange();
    selectCaptureDuration(getIntersectingCaptureDuration(selectionRange), SwingUtilities::invokeLater);
    setUpdateCaptureOnSelection(true);
  }

  public boolean isMemoryCaptureOnly() {
    return myIsMemoryCaptureOnly;
  }

  @Override
  protected void onCaptureToSelect(SeriesData<CaptureDurationData<CaptureObject>> captureToSelect, @NotNull Executor loadJoiner) {
    long x = captureToSelect.x;
    if (getHeapDumpSampleDurations().getSeries().getSeriesForRange(getTimeline().getDataRange()).stream().anyMatch(s -> s.x == x)) {
      getAspect().changed(MemoryProfilerAspect.HEAP_DUMP_FINISHED);
    }
    selectCaptureDuration(captureToSelect.value, loadJoiner);
  }

  /**
   * Set the start time for pending capture object imported from hprof file.
   */
  public void setPendingCaptureStartTimeGuarded(long pendingCaptureStartTime) {
    assert myIsMemoryCaptureOnly;
    super.setPendingCaptureStartTime(pendingCaptureStartTime);
  }

  private Transport.ExecuteResponse startNativeAllocationTracking() {
    IdeProfilerServices ide = getStudioProfilers().getIdeServices();
    ide.getFeatureTracker().trackRecordAllocations();
    getStudioProfilers().setMemoryLiveAllocationEnabled(false);
    Common.Process process = getStudioProfilers().getProcess();
    String traceFilePath = String.format(Locale.getDefault(), "%s/%s.trace", DAEMON_DEVICE_DIR_PATH, process.getName());
    Commands.Command dumpCommand = Commands.Command.newBuilder()
      .setStreamId(getSessionData().getStreamId())
      .setPid(getSessionData().getPid())
      .setType(Commands.Command.CommandType.START_NATIVE_HEAP_SAMPLE)
      .setStartNativeSample(Memory.StartNativeSample.newBuilder()
                              // Note: This will use the config for the one that is loaded (in the drop down) vs the one used to launch
                              // the app.
                              .setSamplingIntervalBytes(ide.getNativeMemorySamplingRateForCurrentConfig())
                              .setSharedMemoryBufferBytes(64 * 1024 * 1024)
                              .setAbiCpuArch(process.getAbiCpuArch())
                              .setTempPath(traceFilePath)
                              .setAppName(process.getName()))
      .build();
    return getStudioProfilers().getClient().getTransportClient().execute(
      Transport.ExecuteRequest.newBuilder().setCommand(dumpCommand).build());
  }

  private Transport.ExecuteResponse stopNativeAllocationTracking(long startTime) {
    getStudioProfilers().setMemoryLiveAllocationEnabled(true);
    Commands.Command dumpCommand = Commands.Command.newBuilder()
      .setStreamId(getSessionData().getStreamId())
      .setPid(getSessionData().getPid())
      .setType(Commands.Command.CommandType.STOP_NATIVE_HEAP_SAMPLE)
      .setStopNativeSample(Memory.StopNativeSample.newBuilder()
                             .setStartTime(startTime))
      .build();
    return getStudioProfilers().getClient().getTransportClient().execute(
      Transport.ExecuteRequest.newBuilder().setCommand(dumpCommand).build());
  }

  public void toggleNativeAllocationTracking() {
    assert getStudioProfilers().getProcess() != null;
    Transport.ExecuteResponse response;
    if (!myNativeAllocationTracking) {
      response = startNativeAllocationTracking();
    }
    else {
      response = stopNativeAllocationTracking(getPendingCaptureStartTime());
    }
    TransportEventListener statusListener = new TransportEventListener(Common.Event.Kind.MEMORY_NATIVE_SAMPLE_STATUS,
                                                                       getStudioProfilers().getIdeServices().getMainExecutor(),
                                                                       event -> event.getCommandId() == response.getCommandId(),
                                                                       () -> getSessionData().getStreamId(),
                                                                       () -> getSessionData().getPid(),
                                                                       event -> {
                                                                         nativeAllocationTrackingStart(
                                                                           event.getMemoryNativeTrackingStatus());
                                                                         // unregisters the listener.
                                                                         return true;
                                                                       });
    getStudioProfilers().getTransportPoller().registerListener(statusListener);
  }

  private void nativeAllocationTrackingStart(@NotNull Memory.MemoryNativeTrackingData status) {
    switch (status.getStatus()) {
      case SUCCESS:
        myNativeAllocationTracking = true;
        setPendingCaptureStartTime(status.getStartTime());
        setTrackingAllocations(true);
        myPendingLegacyAllocationStartTimeNs = status.getStartTime();
        break;
      case IN_PROGRESS:
        myNativeAllocationTracking = true;
        setTrackingAllocations(true);
        getLogger().debug(String.format(Locale.getDefault(), "A heap dump for %d is already in progress.", getSessionData().getPid()));
        break;
      case FAILURE:
        getLogger().error(status.getFailureMessage());
        // fall through
      case NOT_RECORDING:
      case UNSPECIFIED:
      case UNRECOGNIZED:
        myNativeAllocationTracking = false;
        setTrackingAllocations(false);
        break;
    }
    getAspect().changed(MemoryProfilerAspect.TRACKING_ENABLED);
  }

  public void requestHeapDump() {
    if (getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      assert getStudioProfilers().getProcess() != null;
      Commands.Command dumpCommand = Commands.Command.newBuilder()
        .setStreamId(getSessionData().getStreamId())
        .setPid(getSessionData().getPid())
        .setType(Commands.Command.CommandType.HEAP_DUMP)
        .build();
      Transport.ExecuteResponse response = getStudioProfilers().getClient().getTransportClient().execute(
        Transport.ExecuteRequest.newBuilder().setCommand(dumpCommand).build());
      TransportEventListener statusListener = new TransportEventListener(Common.Event.Kind.MEMORY_HEAP_DUMP_STATUS,
                                                                         getStudioProfilers().getIdeServices().getMainExecutor(),
                                                                         event -> event.getCommandId() == response.getCommandId(),
                                                                         () -> getSessionData().getStreamId(),
                                                                         () -> getSessionData().getPid(),
                                                                         event -> {
                                                                           handleHeapDumpStart(event.getMemoryHeapdumpStatus().getStatus());
                                                                           // unregisters the listener.
                                                                           return true;
                                                                         });
      getStudioProfilers().getTransportPoller().registerListener(statusListener);
    }
    else {
      TriggerHeapDumpResponse response = getClient().triggerHeapDump(TriggerHeapDumpRequest.newBuilder().setSession(getSessionData()).build());
      handleHeapDumpStart(response.getStatus());
    }

    getTimeline().setStreaming(true);
    getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_MEMORY_CAPTURE, true);
    getInstructionsEaseOutModel().setCurrentPercentage(1);
  }

  private void handleHeapDumpStart(@NotNull Memory.HeapDumpStatus status) {
    switch (status.getStatus()) {
      case SUCCESS:
        setPendingCaptureStartTime(status.getStartTime());
        getAspect().changed(MemoryProfilerAspect.HEAP_DUMP_STARTED);
        break;
      case IN_PROGRESS:
        getLogger().debug(String.format(Locale.getDefault(), "A heap dump for %d is already in progress.", getSessionData().getPid()));
        break;
      case UNSPECIFIED:
      case NOT_PROFILING:
      case FAILURE_UNKNOWN:
      case UNRECOGNIZED:
        break;
    }
  }

  public DurationDataModel<CaptureDurationData<CaptureObject>> getHeapDumpSampleDurations() {
    return myHeapDumpDurations;
  }

  /**
   * @param enable whether to enable or disable allocation tracking.
   * @return the actual status, which may be different from the input
   */
  public void trackAllocations(boolean enable) {
    MemoryProfiler.trackAllocations(getStudioProfilers(), getSessionData(), enable, status -> {
      switch (status.getStatus()) {
        case SUCCESS:
          setTrackingAllocations(enable);
          setPendingCaptureStartTime(status.getStartTime());
          myPendingLegacyAllocationStartTimeNs = enable ? status.getStartTime() : INVALID_START_TIME;
          break;
        case IN_PROGRESS:
          setTrackingAllocations(true);
          break;
        case NOT_ENABLED:
          setTrackingAllocations(false);
          break;
        case UNSPECIFIED:
        case NOT_PROFILING:
        case FAILURE_UNKNOWN:
        case UNRECOGNIZED:
          break;
      }
      getAspect().changed(MemoryProfilerAspect.TRACKING_ENABLED);

      if (isTrackingAllocations()) {
        getTimeline().setStreaming(true);
        getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_MEMORY_CAPTURE, true);
        getInstructionsEaseOutModel().setCurrentPercentage(1);
      }
    });
  }

  public long getAllocationTrackingElapsedTimeNs() {
    if (isTrackingAllocations()) {
      try {
        TimeResponse timeResponse = getStudioProfilers().getClient().getTransportClient()
          .getCurrentTime(TimeRequest.newBuilder().setStreamId(getSessionData().getStreamId()).build());
        return timeResponse.getTimestampNs() - myPendingLegacyAllocationStartTimeNs;
      }
      catch (StatusRuntimeException exception) {
        getLogger().warn(exception);
      }
    }
    return INVALID_START_TIME;
  }

  public boolean isNativeAllocationSamplingEnabled() {
    Common.Device device = getDeviceForSelectedSession();
    return getStudioProfilers().getIdeServices().getFeatureConfig().isNativeMemorySampleEnabled() &&
           device != null &&
           device.getFeatureLevel() >= AndroidVersion.VersionCodes.Q;
  }

  @NotNull
  public DurationDataModel<CaptureDurationData<CaptureObject>> getAllocationInfosDurations() {
    return myAllocationDurations;
  }

  @NotNull
  public DurationDataModel<CaptureDurationData<CaptureObject>> getNativeAllocationInfosDurations() {
    return myNativeAllocationDurations;
  }

  @VisibleForTesting
  public void selectCaptureDuration(@Nullable CaptureDurationData<? extends CaptureObject> durationData,
                                    @Nullable Executor joiner) {
    StudioProfilers profilers = getStudioProfilers();
    if (durationData != null &&
        durationData.isHeapDumpData() &&
        getStudioProfilers().getIdeServices().getFeatureConfig().isSeparateHeapDumpUiEnabled()) {
      profilers.setStage(new HeapDumpStage(profilers, getLoader(), durationData, joiner));
    }
    else {
      doSelectCaptureDuration(durationData, joiner);
    }
  }

  private void updateAllocationTrackingStatus() {
    List<AllocationsInfo> allocationsInfos = MemoryProfiler.getAllocationInfosForSession(getStudioProfilers().getClient(),
                                                                                         getSessionData(),
                                                                                         new Range(Long.MIN_VALUE, Long.MAX_VALUE),
                                                                                         getStudioProfilers().getIdeServices());
    AllocationsInfo lastInfo = allocationsInfos.isEmpty() ? null : allocationsInfos.get(allocationsInfos.size() - 1);
    setTrackingAllocations(lastInfo != null && (lastInfo.getLegacy() && lastInfo.getEndTime() == Long.MAX_VALUE));
    if (isTrackingAllocations()) {
      setPendingCaptureStartTime(lastInfo.getStartTime());
      myPendingLegacyAllocationStartTimeNs = lastInfo.getStartTime();
    }
    else {
      setPendingCaptureStartTime(INVALID_START_TIME);
      myPendingLegacyAllocationStartTimeNs = INVALID_START_TIME;
    }
  }

  private void updateNativeAllocationTrackingStatus() {
    List<Memory.MemoryNativeTrackingData> samples = MemoryProfiler
      .getNativeHeapStatusForSession(getStudioProfilers().getClient(), getSessionData(), new Range(Long.MIN_VALUE, Long.MAX_VALUE));
    if (samples.isEmpty()) {
      return;
    }
    Memory.MemoryNativeTrackingData last = samples.get(samples.size() - 1);
    // If there is an ongoing recording.
    if (last.getStatus() == Memory.MemoryNativeTrackingData.Status.SUCCESS) {
      nativeAllocationTrackingStart(last);
    }
  }

  public static boolean canSafelyLoadHprof(long fileSize) {
    System.gc(); // To avoid overly conservative estimation of free memory
    long leeway = 300 * 1024 * 1024; // Studio needs ~300MB to run without major freezes
    long requestableMemory = Runtime.getRuntime().maxMemory() -
                             Runtime.getRuntime().totalMemory() +
                             Runtime.getRuntime().freeMemory();
    return requestableMemory >= MEMORY_HPROF_SAFE_FACTOR * fileSize + leeway;
  }
}