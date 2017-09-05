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

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.android.tools.profilers.stacktrace.StackTraceModel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class MemoryProfilerStage extends Stage implements CodeNavigator.Listener {
  private static Logger getLogger() {
    return Logger.getInstance(MemoryProfilerStage.class);
  }

  private static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 5, 5);
  private static final BaseAxisFormatter OBJECT_COUNT_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 5, 5, "");
  private static final long INVALID_START_TIME = -1;

  private final DetailedMemoryUsage myDetailedMemoryUsage;
  private final AxisComponentModel myMemoryAxis;
  private final AxisComponentModel myObjectsAxis;
  private final MemoryStageLegends myLegends;
  private final MemoryStageLegends myTooltipLegends;

  private final int myProcessId;
  @Nullable
  private final Common.Session mySessionData;
  private DurationDataModel<GcDurationData> myGcStats;

  @NotNull
  private AspectModel<MemoryProfilerAspect> myAspect = new AspectModel<>();

  private final MemoryServiceBlockingStub myClient;
  private final DurationDataModel<CaptureDurationData<CaptureObject>> myHeapDumpDurations;
  private final DurationDataModel<CaptureDurationData<CaptureObject>> myAllocationDurations;
  private final CaptureObjectLoader myLoader;
  private final MemoryProfilerSelection mySelection;
  private final MemoryProfilerConfiguration myConfiguration;
  private final EventMonitor myEventMonitor;
  private final SelectionModel mySelectionModel;
  private final StackTraceModel myStackTraceModel;
  private boolean myTrackingAllocations;
  private boolean myUpdateCaptureOnSelection = true;
  private long myPendingCaptureStartTime = INVALID_START_TIME;

  public MemoryProfilerStage(@NotNull StudioProfilers profilers) {
    this(profilers, new CaptureObjectLoader());
  }

  @VisibleForTesting
  public MemoryProfilerStage(@NotNull StudioProfilers profilers, @NotNull CaptureObjectLoader loader) {
    super(profilers);
    myProcessId = profilers.getProcessId();
    mySessionData = profilers.getSession();
    myClient = profilers.getClient().getMemoryClient();
    HeapDumpSampleDataSeries heapDumpSeries =
      new HeapDumpSampleDataSeries(profilers.getClient().getMemoryClient(), mySessionData, myProcessId,
                                   profilers.getRelativeTimeConverter(), getStudioProfilers().getIdeServices().getFeatureTracker());
    AllocationInfosDataSeries allocationSeries =
      new AllocationInfosDataSeries(profilers.getClient().getMemoryClient(), mySessionData, myProcessId,
                                    profilers.getRelativeTimeConverter(), getStudioProfilers().getIdeServices().getFeatureTracker(), this);
    myLoader = loader;

    Range viewRange = profilers.getTimeline().getViewRange();

    myHeapDumpDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, heapDumpSeries));
    myAllocationDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, allocationSeries));
    mySelection = new MemoryProfilerSelection(this);
    myConfiguration = new MemoryProfilerConfiguration(this);

    myDetailedMemoryUsage = new DetailedMemoryUsage(profilers);

    myMemoryAxis = new AxisComponentModel(myDetailedMemoryUsage.getMemoryRange(), MEMORY_AXIS_FORMATTER);
    myMemoryAxis.setClampToMajorTicks(true);

    myObjectsAxis = new AxisComponentModel(myDetailedMemoryUsage.getObjectsRange(), OBJECT_COUNT_AXIS_FORMATTER);
    myObjectsAxis.setClampToMajorTicks(true);

    myLegends = new MemoryStageLegends(profilers, myDetailedMemoryUsage, profilers.getTimeline().getDataRange());
    myTooltipLegends = new MemoryStageLegends(profilers, myDetailedMemoryUsage, profilers.getTimeline().getTooltipRange());

    myGcStats = new DurationDataModel<>(new RangedSeries<>(viewRange, new GcStatsDataSeries(myClient, myProcessId, mySessionData)));
    myGcStats.setAttachedSeries(myDetailedMemoryUsage.getObjectsSeries(), Interpolatable.SegmentInterpolator);

    myEventMonitor = new EventMonitor(profilers);

    mySelectionModel = new SelectionModel(profilers.getTimeline().getSelectionRange(), profilers.getTimeline().getViewRange());
    mySelectionModel.addConstraint(myAllocationDurations);
    mySelectionModel.addConstraint(myHeapDumpDurations);
    mySelectionModel.addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        selectCaptureFromSelectionRange();
        profilers.getIdeServices().getFeatureTracker().trackSelectRange();
      }

      @Override
      public void selectionCleared() {
        selectCaptureFromSelectionRange();
      }
    });

    myStackTraceModel = new StackTraceModel(profilers.getIdeServices().getCodeNavigator());
  }

  public DetailedMemoryUsage getDetailedMemoryUsage() {
    return myDetailedMemoryUsage;
  }

  @Override
  public void enter() {
    myLoader.start();
    myEventMonitor.enter();
    getStudioProfilers().getUpdater().register(myDetailedMemoryUsage);
    getStudioProfilers().getUpdater().register(myHeapDumpDurations);
    getStudioProfilers().getUpdater().register(myAllocationDurations);
    getStudioProfilers().getUpdater().register(myMemoryAxis);
    getStudioProfilers().getUpdater().register(myObjectsAxis);
    getStudioProfilers().getUpdater().register(myLegends);
    getStudioProfilers().getUpdater().register(myTooltipLegends);
    getStudioProfilers().getUpdater().register(myGcStats);

    getStudioProfilers().getIdeServices().getCodeNavigator().addListener(this);
    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getClass());

    myTrackingAllocations = false; // TODO sync with current legacy allocation tracker status
  }

  @Override
  public void exit() {
    enableSelectLatestCapture(false, null);

    myEventMonitor.exit();
    getStudioProfilers().getUpdater().unregister(myDetailedMemoryUsage);
    getStudioProfilers().getUpdater().unregister(myHeapDumpDurations);
    getStudioProfilers().getUpdater().unregister(myAllocationDurations);
    getStudioProfilers().getUpdater().unregister(myMemoryAxis);
    getStudioProfilers().getUpdater().unregister(myObjectsAxis);
    getStudioProfilers().getUpdater().unregister(myLegends);
    getStudioProfilers().getUpdater().unregister(myTooltipLegends);
    getStudioProfilers().getUpdater().unregister(myGcStats);
    selectCaptureDuration(null, null);
    myLoader.stop();

    getStudioProfilers().getIdeServices().getCodeNavigator().removeListener(this);

    mySelectionModel.clearListeners();
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  @NotNull
  public StackTraceModel getStackTraceModel() {
    return myStackTraceModel;
  }

  private void selectCaptureFromSelectionRange() {
    if (!myUpdateCaptureOnSelection) {
      return;
    }

    myUpdateCaptureOnSelection = false;
    Range selectionRange = getStudioProfilers().getTimeline().getSelectionRange();
    Range intersection;
    CaptureDurationData<? extends CaptureObject> durationData = null;
    double overlap = 0.0f; // Weight value to determine which capture is "more" selected.

    List<SeriesData<CaptureDurationData<CaptureObject>>> series =
      new ArrayList<>(getAllocationInfosDurations().getSeries().getDataSeries().getDataForXRange(selectionRange));
    // Heap dumps break ties vs allocations.
    series.addAll(getHeapDumpSampleDurations().getSeries().getDataSeries().getDataForXRange(selectionRange));

    for (SeriesData<CaptureDurationData<CaptureObject>> data : series) {
      long duration = data.value.getDuration();
      if (duration == Long.MAX_VALUE && !data.value.getSelectableWhenMaxDuration()) {
        continue;
      }

      long dataMax = duration == Long.MAX_VALUE ? duration : data.x + duration;
      Range c = new Range(data.x, dataMax);
      intersection = c.getIntersection(selectionRange);
      if (!intersection.isEmpty() && intersection.getLength() >= overlap) {
        durationData = data.value;
        overlap = intersection.getLength();
      }
    }

    selectCaptureDuration(durationData, SwingUtilities::invokeLater);
    myUpdateCaptureOnSelection = true;
  }

  /**
   * Toggle a behavior where if there is currently no CaptureObject selected, the model will attempt to select the next CaptureObject
   * that has been created either through {@link #trackAllocations(boolean)} or {@link #requestHeapDump()}.
   *
   * @param loadJoiner if specified, the joiner executor will be passed down to {@link #loadCaptureObject(CaptureObject, Executor)} so
   *                   that the load operation of the CaptureObject will be joined and the CURRENT_LOAD_CAPTURE aspect would be
   *                   fired via the desired executor.
   */
  public void enableSelectLatestCapture(boolean enable, @Nullable Executor loadJoiner) {
    ProfilerTimeline timeline = getStudioProfilers().getTimeline();
    if (enable) {
      timeline.getDataRange().addDependency(this)
        .onChange(Range.Aspect.RANGE, () -> {
          queryAndSelectCaptureObject(loadJoiner == null ? MoreExecutors.directExecutor() : loadJoiner);
        });
    }
    else {
      // Removing the aspect observers on Ranges.
      timeline.getDataRange().removeDependencies(this);
    }
  }

  /**
   * Find a pending allocation or heap dump capture matching {@code myPendingCaptureStartTime} if no capture is currently selected.
   * Selection range will also be updated to match if the capture isn't ongoing.
   */
  private void queryAndSelectCaptureObject(@NotNull Executor loadJoiner) {
    Range dataRange = getStudioProfilers().getTimeline().getDataRange();
    // Only tries to select a capture object if one is not already selected
    if (mySelection.getCaptureObject() == null && myPendingCaptureStartTime != INVALID_START_TIME) {
      List<SeriesData<CaptureDurationData<CaptureObject>>> series =
        new ArrayList<>(getAllocationInfosDurations().getSeries().getDataSeries().getDataForXRange(dataRange));
      series.addAll(getHeapDumpSampleDurations().getSeries().getDataSeries().getDataForXRange(dataRange));

      long pendingCaptureStartTimeUs = TimeUnit.NANOSECONDS.toMicros(myPendingCaptureStartTime);
      SeriesData<CaptureDurationData<CaptureObject>> captureToSelect = null;
      for (int i = series.size() - 1; i >= 0; --i) {
        if (series.get(i).x == pendingCaptureStartTimeUs) {
          captureToSelect = series.get(i);
          break;
        }
      }

      if (captureToSelect != null &&
          (captureToSelect.value.getDuration() != Long.MAX_VALUE || captureToSelect.value.getSelectableWhenMaxDuration())) {
        selectCaptureDuration(captureToSelect.value, loadJoiner);
      }
    }
  }

  @NotNull
  public AspectModel<MemoryProfilerAspect> getAspect() {
    return myAspect;
  }

  public void requestHeapDump() {
    MemoryProfiler.TriggerHeapDumpResponse response =
      myClient
        .triggerHeapDump(MemoryProfiler.TriggerHeapDumpRequest.newBuilder().setSession(mySessionData).setProcessId(myProcessId).build());
    switch (response.getStatus()) {
      case SUCCESS:
        myPendingCaptureStartTime = response.getInfo().getStartTime();
        break;
      case IN_PROGRESS:
        getLogger().debug(String.format("A heap dump for %d is already in progress.", myProcessId));
        break;
      case UNSPECIFIED:
      case NOT_PROFILING:
      case FAILURE_UNKNOWN:
      case UNRECOGNIZED:
        break;
    }
  }

  public void forceGarbageCollection() {
    myClient.forceGarbageCollection(
      MemoryProfiler.ForceGarbageCollectionRequest.newBuilder().setProcessId(myProcessId).setSession(mySessionData).build());
  }

  public DurationDataModel<CaptureDurationData<CaptureObject>> getHeapDumpSampleDurations() {
    return myHeapDumpDurations;
  }

  /**
   * @param enabled whether to enable or disable allocation tracking.
   * @return the actual status, which may be different from the input
   */
  public void trackAllocations(boolean enabled) {
    // Allocation tracking can go through the legacy tracker which does not reach perfd, so we need to pass in the current device time.
    Profiler.TimeResponse timeResponse = getStudioProfilers().getClient().getProfilerClient()
      .getCurrentTime(Profiler.TimeRequest.newBuilder().setSession(mySessionData).build());
    long timeNs = timeResponse.getTimestampNs();

    try {
      TrackAllocationsResponse response = myClient.trackAllocations(
        MemoryProfiler.TrackAllocationsRequest.newBuilder().setRequestTime(timeNs).setSession(mySessionData).setProcessId(myProcessId)
          .setEnabled(enabled).build());
      MemoryProfiler.AllocationsInfo info = response.getInfo();
      switch (response.getStatus()) {
        case SUCCESS:
          myTrackingAllocations = enabled;
          myPendingCaptureStartTime = info.getStartTime();
          break;
        case IN_PROGRESS:
          myTrackingAllocations = true;
          break;
        case NOT_ENABLED:
          myTrackingAllocations = false;
          break;
        case UNSPECIFIED:
        case NOT_PROFILING:
        case FAILURE_UNKNOWN:
        case UNRECOGNIZED:
          break;
      }
      myAspect.changed(MemoryProfilerAspect.TRACKING_ENABLED);
    }
    catch (StatusRuntimeException e) {
      getLogger().debug(e);
    }
  }

  public boolean isTrackingAllocations() {
    return myTrackingAllocations;
  }

  public boolean useLiveAllocationTracking() {
    return getStudioProfilers().getIdeServices().getFeatureConfig().isLiveAllocationsEnabled() &&
           getStudioProfilers().getDevice().getFeatureLevel() >= AndroidVersion.VersionCodes.O;
  }

  @NotNull
  public DurationDataModel<CaptureDurationData<CaptureObject>> getAllocationInfosDurations() {
    return myAllocationDurations;
  }

  public void selectFieldObjectPath(@NotNull List<FieldObject> fieldObjectPath) {
    mySelection.selectFieldObjectPath(fieldObjectPath);
  }

  @NotNull
  public List<FieldObject> getSelectedFieldObjectPath() {
    return mySelection.getFieldObjectPath();
  }

  public void selectInstanceObject(@Nullable InstanceObject instanceObject) {
    mySelection.selectInstanceObject(instanceObject);
  }

  @Nullable
  public InstanceObject getSelectedInstanceObject() {
    return mySelection.getInstanceObject();
  }

  public void selectClassSet(@Nullable ClassSet classSet) {
    mySelection.selectClassSet(classSet);
  }

  @Nullable
  public ClassSet getSelectedClassSet() {
    return mySelection.getClassSet();
  }

  public void refreshSelectedHeap() {
    myAspect.changed(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS);
  }

  public void selectHeapSet(@Nullable HeapSet heapSet) {
    mySelection.selectHeapSet(heapSet);
  }

  @Nullable
  public HeapSet getSelectedHeapSet() {
    return mySelection.getHeapSet();
  }

  @VisibleForTesting
  void selectCaptureDuration(@Nullable CaptureDurationData<? extends CaptureObject> durationData,
                             @Nullable Executor joiner) {
    myPendingCaptureStartTime = INVALID_START_TIME;
    if (!mySelection.selectCaptureEntry(durationData == null ? null : durationData.getCaptureEntry())) {
      return;
    }

    myUpdateCaptureOnSelection = false;
    ProfilerTimeline timeline = getStudioProfilers().getTimeline();
    CaptureObject captureObject = mySelection.getCaptureObject();
    if (captureObject == null) {
      // Loading a capture can fail, in which case we reset everything.
      mySelection.selectCaptureEntry(null);
      timeline.getSelectionRange().clear();
      myAspect.changed(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE);
      setProfilerMode(ProfilerMode.NORMAL);
      return;
    }

    // Synchronize selection with the capture object. Do so only if the capture object is not ongoing.
    if (durationData.getDuration() != Long.MAX_VALUE) {
      // TODO: (revisit) we have an special case in interacting with SelectionModel where if the user tries to select a heap dump that is on
      // top of an ongoing live allocation capture (duration == Long.MAX_VALUE), the live capture would take precedence given it always
      // intersects with the previous selection. Here we clear the previous selection first to avoid said interaction.
      timeline.getSelectionRange().clear();
      long startTimeUs = TimeUnit.NANOSECONDS.toMicros(captureObject.getStartTimeNs());
      long endTimeUs = TimeUnit.NANOSECONDS.toMicros(captureObject.getEndTimeNs());
      timeline.getSelectionRange().set(startTimeUs, endTimeUs);
    }
    myUpdateCaptureOnSelection = true;

    // TODO: (revisit) - do we want to pass in data range to loadCapture as well?
    ListenableFuture<CaptureObject> future = myLoader.loadCapture(captureObject, timeline.getSelectionRange(), joiner);
    future.addListener(
      () -> {
        try {
          CaptureObject loadedCaptureObject = future.get();
          if (mySelection.finishSelectingCaptureObject(loadedCaptureObject)) {
            Collection<HeapSet> heaps = loadedCaptureObject.getHeapSets();
            if (heaps.isEmpty()) {
              return;
            }

            for (HeapSet heap : heaps) {
              if (heap.getName().equals("app")) {
                selectHeapSet(heap);
                return;
              }
            }

            for (HeapSet heap : heaps) {
              if (heap.getName().equals("default")) {
                selectHeapSet(heap);
                return;
              }
            }

            HeapSet heap = new ArrayList<>(heaps).get(0);
            selectHeapSet(heap);
          }
          else {
            // Capture loading failed.
            // TODO: loading has somehow failed - we need to inform users about the error status.
            selectCaptureDuration(null, null);
          }
        }
        catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          selectCaptureDuration(null, null);
        }
        catch (ExecutionException exception) {
          selectCaptureDuration(null, null);
          getLogger().error(exception);
        }
        catch (CancellationException ignored) {
          // No-op: a previous load-capture task is canceled due to another capture being selected and loaded.
        }
      },
      joiner == null ? MoreExecutors.directExecutor() : joiner);

    setProfilerMode(ProfilerMode.EXPANDED);
  }

  @Nullable
  public CaptureObject getSelectedCapture() {
    return mySelection.getCaptureObject();
  }

  @NotNull
  public MemoryProfilerConfiguration getConfiguration() {
    return myConfiguration;
  }

  public AxisComponentModel getMemoryAxis() {
    return myMemoryAxis;
  }

  public AxisComponentModel getObjectsAxis() {
    return myObjectsAxis;
  }

  public MemoryStageLegends getLegends() {
    return myLegends;
  }

  public MemoryStageLegends getTooltipLegends() {
    return myTooltipLegends;
  }

  public EventMonitor getEventMonitor() {
    return myEventMonitor;
  }

  public DurationDataModel<GcDurationData> getGcStats() {
    return myGcStats;
  }

  public String getName() {
    return "MEMORY";
  }

  @Override
  public void onNavigated(@NotNull CodeLocation location) {
    setProfilerMode(ProfilerMode.NORMAL);
  }

  public static class MemoryStageLegends extends LegendComponentModel {

    @NotNull private final StudioProfilers myProfilers;
    @NotNull private final SeriesLegend myJavaLegend;
    @NotNull private final SeriesLegend myNativeLegend;
    @NotNull private final SeriesLegend myGraphicsLegend;
    @NotNull private final SeriesLegend myStackLegend;
    @NotNull private final SeriesLegend myCodeLegend;
    @NotNull private final SeriesLegend myOtherLegend;
    @NotNull private final SeriesLegend myTotalLegend;
    @NotNull private final SeriesLegend myObjectsLegend;

    public MemoryStageLegends(@NotNull StudioProfilers profilers, @NotNull DetailedMemoryUsage usage, @NotNull Range range) {
      super(ProfilerMonitor.LEGEND_UPDATE_FREQUENCY_MS);
      myJavaLegend = new SeriesLegend(usage.getJavaSeries(), MEMORY_AXIS_FORMATTER, range);
      myNativeLegend = new SeriesLegend(usage.getNativeSeries(), MEMORY_AXIS_FORMATTER, range);
      myGraphicsLegend = new SeriesLegend(usage.getGraphicsSeries(), MEMORY_AXIS_FORMATTER, range);
      myStackLegend = new SeriesLegend(usage.getStackSeries(), MEMORY_AXIS_FORMATTER, range);
      myCodeLegend = new SeriesLegend(usage.getCodeSeries(), MEMORY_AXIS_FORMATTER, range);
      myOtherLegend = new SeriesLegend(usage.getOtherSeries(), MEMORY_AXIS_FORMATTER, range);
      myTotalLegend = new SeriesLegend(usage.getTotalMemorySeries(), MEMORY_AXIS_FORMATTER, range);
      myObjectsLegend = new SeriesLegend(usage.getObjectsSeries(), OBJECT_COUNT_AXIS_FORMATTER, range,
                                         Interpolatable.RoundedSegmentInterpolator);

      add(myTotalLegend);
      add(myJavaLegend);
      add(myNativeLegend);
      add(myGraphicsLegend);
      add(myStackLegend);
      add(myCodeLegend);
      add(myOtherLegend);

      myProfilers = profilers;
      myProfilers.addDependency(this).onChange(ProfilerAspect.AGENT, this::agentStatusChanged);
      agentStatusChanged();
    }

    @NotNull
    public SeriesLegend getJavaLegend() {
      return myJavaLegend;
    }

    @NotNull
    public SeriesLegend getNativeLegend() {
      return myNativeLegend;
    }

    @NotNull
    public SeriesLegend getGraphicsLegend() {
      return myGraphicsLegend;
    }

    @NotNull
    public SeriesLegend getStackLegend() {
      return myStackLegend;
    }

    @NotNull
    public SeriesLegend getCodeLegend() {
      return myCodeLegend;
    }

    @NotNull
    public SeriesLegend getOtherLegend() {
      return myOtherLegend;
    }

    @NotNull
    public SeriesLegend getTotalLegend() {
      return myTotalLegend;
    }

    @NotNull
    public SeriesLegend getObjectsLegend() {
      return myObjectsLegend;
    }

    private void agentStatusChanged() {
      if (myProfilers.isAgentAttached()) {
        add(myObjectsLegend);
      }
      else {
        remove(myObjectsLegend);
      }
    }
  }
}
