/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.MultiSelectionModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.trackgroup.SelectableTrackModel;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisChartModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisTabModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable;
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import com.android.tools.profilers.cpu.capturedetails.CpuCaptureNodeTooltip;
import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Track model for CPU threads in CPU capture stage. Consists of thread states and trace events.
 */
public class CpuThreadTrackModel implements CpuAnalyzable<CpuThreadTrackModel> {
  private final StateChartModel<CpuProfilerStage.ThreadState> myThreadStateChartModel;
  private final CaptureDetails.CallChart myCallChartModel;
  private final CpuCapture myCapture;
  private final Range mySelectionRange;
  private final CpuThreadInfo myThreadInfo;
  @NotNull private final CpuThreadsTooltip myThreadStateTooltip;
  @NotNull private final Function<CaptureNode, CpuCaptureNodeTooltip> myTraceEventTooltipBuilder;
  @NotNull private final MultiSelectionModel<CpuAnalyzable> myMultiSelectionModel;

  public CpuThreadTrackModel(@NotNull StudioProfilers profilers,
                             @NotNull Range range,
                             @NotNull CpuCapture capture,
                             @NotNull CpuThreadInfo threadInfo,
                             @NotNull Timeline timeline,
                             @NotNull MultiSelectionModel<CpuAnalyzable> multiSelectionModel) {
    DataSeries<CpuProfilerStage.ThreadState> threadStateDataSeries = buildThreadStateDataSeries(profilers, capture, threadInfo.getId());

    myThreadStateChartModel = new StateChartModel<>();
    myThreadStateChartModel.addSeries(new RangedSeries<>(range, threadStateDataSeries));
    myCallChartModel = new CaptureDetails.CallChart(range, Collections.singletonList(capture.getCaptureNode(threadInfo.getId())), capture);
    myCapture = capture;
    mySelectionRange = range;
    myThreadInfo = threadInfo;

    myThreadStateTooltip = new CpuThreadsTooltip(timeline);
    myThreadStateTooltip.setThread(threadInfo.getName(), threadStateDataSeries);

    myTraceEventTooltipBuilder = captureNode -> new CpuCaptureNodeTooltip(timeline, captureNode);

    myMultiSelectionModel = multiSelectionModel;
  }

  @NotNull
  public StateChartModel<CpuProfilerStage.ThreadState> getThreadStateChartModel() {
    return myThreadStateChartModel;
  }

  @NotNull
  public CaptureDetails.CallChart getCallChartModel() {
    return myCallChartModel;
  }

  @NotNull
  public CpuCapture getCapture() {
    return myCapture;
  }

  @NotNull
  @Override
  public CpuAnalysisModel<CpuThreadTrackModel> getAnalysisModel() {
    CpuAnalysisChartModel<CpuThreadTrackModel> flameChart =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.FLAME_CHART, mySelectionRange, myCapture, CpuThreadTrackModel::getCaptureNode);
    CpuAnalysisChartModel<CpuThreadTrackModel> topDown =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.TOP_DOWN, mySelectionRange, myCapture, CpuThreadTrackModel::getCaptureNode);
    CpuAnalysisChartModel<CpuThreadTrackModel> bottomUp =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.BOTTOM_UP, mySelectionRange, myCapture, CpuThreadTrackModel::getCaptureNode);
    flameChart.getDataSeries().add(this);
    topDown.getDataSeries().add(this);
    bottomUp.getDataSeries().add(this);

    CpuAnalysisModel<CpuThreadTrackModel> model = new CpuAnalysisModel<>(myThreadInfo.getName(), "%d threads");
    model.addTabModel(flameChart);
    model.addTabModel(topDown);
    model.addTabModel(bottomUp);
    return model;
  }

  @Override
  public boolean isCompatibleWith(@NotNull SelectableTrackModel otherObj) {
    return otherObj instanceof CpuThreadTrackModel;
  }

  /**
   * @return a tooltip model for thread states.
   */
  @NotNull
  public CpuThreadsTooltip getThreadStateTooltip() {
    return myThreadStateTooltip;
  }

  /**
   * @return a function that produces a tooltip model for trace events.
   */
  @NotNull
  public Function<CaptureNode, CpuCaptureNodeTooltip> getTraceEventTooltipBuilder() {
    return myTraceEventTooltipBuilder;
  }

  @NotNull
  public MultiSelectionModel<CpuAnalyzable> getMultiSelectionModel() {
    return myMultiSelectionModel;
  }

  private Collection<CaptureNode> getCaptureNode() {
    assert myCapture.containsThread(myThreadInfo.getId());
    return Collections.singleton(myCapture.getCaptureNode(myThreadInfo.getId()));
  }

  @VisibleForTesting
  static DataSeries<CpuProfilerStage.ThreadState> buildThreadStateDataSeries(@NotNull StudioProfilers profilers,
                                                                             @NotNull CpuCapture capture,
                                                                             int threadId) {
    if (profilers.getSession().getPid() != 0) {
      // We have an ongoing session, use sampled thread state data if available.
      DataSeries<CpuProfilerStage.ThreadState> threadStateDataSeries =
        new CpuThreadStateDataSeries(profilers.getClient().getTransportClient(),
                                     profilers.getSession().getStreamId(),
                                     profilers.getSession().getPid(),
                                     threadId,
                                     capture);
      if (capture.getType() == Cpu.CpuTraceType.ATRACE) {
        // If we have an Atrace capture we need to create a MergeCaptureDataSeries.
        threadStateDataSeries = new MergeCaptureDataSeries<>(
          capture,
          threadStateDataSeries,
          new AtraceDataSeries<>((AtraceCpuCapture)capture, atraceCapture -> atraceCapture.getThreadStatesForThread(threadId)));
      }
      return threadStateDataSeries;
    }
    if (capture.getType() == Cpu.CpuTraceType.ATRACE) {
      return new AtraceDataSeries<>((AtraceCpuCapture)capture, atraceCapture -> atraceCapture.getThreadStatesForThread(threadId));
    }
    return new ImportedTraceThreadDataSeries(capture, threadId);
  }
}
