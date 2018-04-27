// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.energy;

import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.EnergyAxisFormatter;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.legend.Legend;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.*;
import com.android.tools.profilers.analytics.energy.EnergyEventMetadata;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class EnergyProfilerStage extends Stage implements CodeNavigator.Listener {
  private static final String HAS_USED_ENERGY_SELECTION = "energy.used.selection";
  private static final String ENERGY_EVENT_ORIGIN_INDEX = "energy.event.origin";

  @NotNull private final DetailedEnergyUsage myDetailedUsage;
  @NotNull private final AxisComponentModel myAxis;
  @NotNull private final EventMonitor myEventMonitor;
  @NotNull private final EnergyUsageLegends myLegends;
  @NotNull private final EnergyUsageLegends myUsageTooltipLegends;
  @NotNull private final EnergyEventLegends myEventTooltipLegends;
  @NotNull private final SelectionModel mySelectionModel;
  @NotNull private final EnergyEventsFetcher myFetcher;
  @NotNull private final StateChartModel<EnergyEvent> myEventModel;
  @NotNull private final EaseOutModel myInstructionsEaseOutModel;
  @NotNull private final Updatable myUpdatable;

  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners
  @SuppressWarnings("FieldCanBeLocal") private AspectObserver myAspectObserver = new AspectObserver();
  private AspectModel<EnergyProfilerAspect> myAspect = new AspectModel<>();

  @NotNull private final EnergyTraceCache myTraceCache = new EnergyTraceCache(this);

  @Nullable private EnergyDuration mySelectedDuration;

  public EnergyProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myDetailedUsage = new DetailedEnergyUsage(profilers);
    myAxis = new AxisComponentModel(myDetailedUsage.getUsageRange(), EnergyAxisFormatter.DEFAULT);
    myEventMonitor = new EventMonitor(profilers);
    myLegends = new EnergyUsageLegends(myDetailedUsage, profilers.getTimeline().getDataRange());
    myUsageTooltipLegends = new EnergyUsageLegends(myDetailedUsage, profilers.getTimeline().getTooltipRange());
    myEventTooltipLegends = new EnergyEventLegends(new DetailedEnergyEventsCount(profilers), profilers.getTimeline().getTooltipRange());
    mySelectionModel = new SelectionModel(profilers.getTimeline().getSelectionRange());
    mySelectionModel.setSelectionEnabled(profilers.isAgentAttached());
    profilers.addDependency(myAspectObserver)
             .onChange(ProfilerAspect.AGENT, () -> mySelectionModel.setSelectionEnabled(profilers.isAgentAttached()));
    mySelectionModel.addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        setProfilerMode(ProfilerMode.EXPANDED);
        profilers.getIdeServices().getFeatureTracker().trackSelectRange();
        // TODO(b/74004663): Add featureTracker#trackSelectEnergyRange() call here
        profilers.getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_ENERGY_SELECTION, true);
        myInstructionsEaseOutModel.setCurrentPercentage(1);
      }

      @Override
      public void selectionCleared() {
        setProfilerMode(ProfilerMode.NORMAL);
      }
    });
    myFetcher =
      new EnergyEventsFetcher(profilers.getClient().getEnergyClient(), profilers.getSession(), profilers.getTimeline().getSelectionRange());

    EnergyEventsDataSeries sourceSeries = new EnergyEventsDataSeries(profilers.getClient(), profilers.getSession());

    myEventModel = new StateChartModel<>();
    Range range = profilers.getTimeline().getViewRange();
    // StateChart renders series in reverse order
    myEventModel.addSeries(
      new RangedSeries<>(range, new MergedEnergyEventsDataSeries(sourceSeries, EnergyDuration.Kind.ALARM, EnergyDuration.Kind.JOB)));
    myEventModel.addSeries(new RangedSeries<>(range, new MergedEnergyEventsDataSeries(sourceSeries, EnergyDuration.Kind.WAKE_LOCK)));
    myEventModel.addSeries(new RangedSeries<>(range, new MergedEnergyEventsDataSeries(sourceSeries, EnergyDuration.Kind.LOCATION)));

    myInstructionsEaseOutModel = new EaseOutModel(profilers.getUpdater(), PROFILING_INSTRUCTIONS_EASE_OUT_NS);

    myUpdatable = elapsedNs -> getStudioProfilers().getTimeline().getTooltipRange().changed(Range.Aspect.RANGE);
  }

  @Override
  public void enter() {
    myEventMonitor.enter();

    getStudioProfilers().getUpdater().register(myAxis);
    getStudioProfilers().getUpdater().register(myDetailedUsage);
    getStudioProfilers().getUpdater().register(myLegends);
    getStudioProfilers().getUpdater().register(myUsageTooltipLegends);
    getStudioProfilers().getUpdater().register(myEventTooltipLegends);
    getStudioProfilers().getUpdater().register(myUpdatable);

    getStudioProfilers().getIdeServices().getCodeNavigator().addListener(this);
    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getClass());
  }

  @Override
  public void exit() {
    myEventMonitor.exit();

    getStudioProfilers().getUpdater().unregister(myAxis);
    getStudioProfilers().getUpdater().unregister(myDetailedUsage);
    getStudioProfilers().getUpdater().unregister(myLegends);
    getStudioProfilers().getUpdater().unregister(myUsageTooltipLegends);
    getStudioProfilers().getUpdater().unregister(myEventTooltipLegends);
    getStudioProfilers().getUpdater().unregister(myUpdatable);

    getStudioProfilers().getIdeServices().getCodeNavigator().removeListener(this);
  }

  @NotNull
  public EnergyEventsFetcher getEnergyEventsFetcher() {
    return myFetcher;
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  @NotNull
  public DetailedEnergyUsage getDetailedUsage() {
    return myDetailedUsage;
  }

  @NotNull
  StateChartModel<EnergyEvent> getEventModel() {
    return myEventModel;
  }

  @NotNull
  public AxisComponentModel getAxis() {
    return myAxis;
  }

  @NotNull
  public EventMonitor getEventMonitor() {
    return myEventMonitor;
  }

  @NotNull
  public EnergyUsageLegends getLegends() {
    return myLegends;
  }

  @NotNull
  public EnergyUsageLegends getUsageTooltipLegends() {
    return myUsageTooltipLegends;
  }

  @NotNull
  public EnergyEventLegends getEventTooltipLegends() {
    return myEventTooltipLegends;
  }

  @NotNull
  public String getName() {
    return "ENERGY";
  }

  @NotNull
  public AspectModel<EnergyProfilerAspect> getAspect() {
    return myAspect;
  }

  @Nullable
  public EnergyDuration getSelectedDuration() {
    return mySelectedDuration;
  }

  /**
   * Sets the selected duration, if the given duration is the same as existing or not valid by filter then it is ignored.
   */
  public void setSelectedDuration(@Nullable EnergyDuration duration) {
    if (Objects.equals(mySelectedDuration, duration) || !canSelectDuration(duration)) {
      return;
    }
    mySelectedDuration = duration;
    myAspect.changed(EnergyProfilerAspect.SELECTED_EVENT_DURATION);

    if (mySelectedDuration != null) {
      getStudioProfilers().getIdeServices().getFeatureTracker()
                          .trackSelectEnergyEvent(new EnergyEventMetadata(mySelectedDuration.getEventList()));
    }
  }

  @NotNull
  public EnergyTraceCache getEventsTraceCache() {
    return myTraceCache;
  }

  @NotNull
  public EaseOutModel getInstructionsEaseOutModel() {
    return myInstructionsEaseOutModel;
  }

  public boolean hasUserUsedEnergySelection() {
    return getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().getBoolean(HAS_USED_ENERGY_SELECTION, false);
  }

  @NotNull
  public ByteString requestBytes(@NotNull String id) {
    if (StringUtil.isEmpty(id)) {
      return ByteString.EMPTY;
    }

    Profiler.BytesRequest request =
      Profiler.BytesRequest.newBuilder()
                           .setId(id)
                           .setSession(getStudioProfilers().getSession())
                           .build();

    Profiler.BytesResponse response = getStudioProfilers().getClient().getProfilerClient().getBytes(request);
    return response.getContents();
  }

  /**
   * Refresh this duration, which is a no-op if it is already terminate, or it fetches latest values if the duration was still in progress.
   */
  @NotNull
  public EnergyDuration updateDuration(@NotNull EnergyDuration duration) {
    if (duration.getEventList().get(duration.getEventList().size() - 1).getIsTerminal()) {
      return duration;
    }
    EnergyProfiler.EnergyEventGroupRequest request = EnergyProfiler.EnergyEventGroupRequest.newBuilder()
      .setSession(getStudioProfilers().getSession())
      .setEventId(duration.getEventList().get(0).getEventId())
      .build();
    return new EnergyDuration(getStudioProfilers().getClient().getEnergyClient().getEventGroup(request).getEventsList());
  }

  @NotNull
  public EnergyEventOrigin getEventOrigin() {
    int savedOriginOrdinal = getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences()
                                                 .getInt(ENERGY_EVENT_ORIGIN_INDEX, EnergyEventOrigin.ALL.ordinal());
    return EnergyEventOrigin.values()[savedOriginOrdinal];
  }

 public void setEventOrigin(@NotNull EnergyEventOrigin origin) {
    if (getEventOrigin() != origin) {
      getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().setInt(ENERGY_EVENT_ORIGIN_INDEX, origin.ordinal());
      // As the selected duration is in the stage, update it before the table view update because the table view need reflect the selection.
      if (!canSelectDuration(getSelectedDuration())) {
        setSelectedDuration(null);
      }
      myAspect.changed(EnergyProfilerAspect.SELECTED_ORIGIN_FILTER);
    }
  }

  private boolean canSelectDuration(@Nullable EnergyDuration duration) {
    return duration == null || !filterByOrigin(ImmutableList.of(duration)).isEmpty();
  }

  @NotNull
  public List<EnergyDuration> filterByOrigin(@NotNull List<EnergyDuration> list) {
    String appName = getStudioProfilers().getSelectedAppName();
    return list.stream().filter(duration -> getEventOrigin().isValid(appName, myTraceCache.getTraceData(duration.getCalledByTraceId())))
               .collect(Collectors.toList());
  }

  @Override
  public void onNavigated(@NotNull CodeLocation location) {
    setProfilerMode(ProfilerMode.NORMAL);
  }

  public static class EnergyUsageLegends extends LegendComponentModel {

    @NotNull private final SeriesLegend myCpuLegend;
    @NotNull private final SeriesLegend myNetworkLegend;
    @NotNull private final SeriesLegend myLocationLegend;

    EnergyUsageLegends(DetailedEnergyUsage detailedUsage, Range range) {
      super(ProfilerMonitor.LEGEND_UPDATE_FREQUENCY_MS);
      myCpuLegend = new SeriesLegend(detailedUsage.getCpuUsageSeries(), EnergyAxisFormatter.LEGEND_FORMATTER, range, "CPU",
                                     Interpolatable.SegmentInterpolator);
      myNetworkLegend = new SeriesLegend(detailedUsage.getNetworkUsageSeries(), EnergyAxisFormatter.LEGEND_FORMATTER, range, "Network",
                                         Interpolatable.SegmentInterpolator);
      myLocationLegend = new SeriesLegend(detailedUsage.getLocationUsageSeries(), EnergyAxisFormatter.LEGEND_FORMATTER, range, "Location",
                                     Interpolatable.SegmentInterpolator);

      add(myCpuLegend);
      add(myNetworkLegend);
      add(myLocationLegend);
    }

    @NotNull
    public Legend getCpuLegend() {
      return myCpuLegend;
    }

    @NotNull
    public Legend getNetworkLegend() {
      return myNetworkLegend;
    }

    @NotNull
    public SeriesLegend getLocationLegend() {
      return myLocationLegend;
    }
  }

  public static class EnergyEventLegends extends LegendComponentModel {

    @NotNull private final SeriesLegend myLocationLegend;
    @NotNull private final SeriesLegend myWakeLockLegend;
    @NotNull private final SeriesLegend myAlarmAndJobLegend;
    @NotNull private final Range myRange;
    @NotNull private final SingleUnitAxisFormatter myFormatter =
      new SingleUnitAxisFormatter(1, 5, 5, "");

    EnergyEventLegends(@NotNull DetailedEnergyEventsCount eventCount, @NotNull Range range) {
      super(ProfilerMonitor.LEGEND_UPDATE_FREQUENCY_MS);
      myRange = range;
      myLocationLegend = createSeriesLegend(eventCount.getLocationCountSeries());
      myWakeLockLegend = createSeriesLegend(eventCount.getWakeLockCountSeries());
      myAlarmAndJobLegend = createSeriesLegend(eventCount.getAlarmAndJobCountSeries());

      add(myLocationLegend);
      add(myWakeLockLegend);
      add(myAlarmAndJobLegend);
    }

    private SeriesLegend createSeriesLegend(RangedContinuousSeries series) {
      return new SeriesLegend(series, myFormatter, myRange,
                              Interpolatable.SegmentInterpolator);
    }

    @NotNull
    public Legend getWakeLockLegend() {
      return myWakeLockLegend;
    }

    @NotNull
    public Legend getLocationLegend() {
      return myLocationLegend;
    }

    @NotNull
    public Legend getAlarmAndJobLegend() {
      return myAlarmAndJobLegend;
    }
  }
}
