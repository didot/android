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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.chart.statechart.StateChartColorProvider;
import com.android.tools.adtui.common.EnumColors;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.MultiSelectionModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.adtui.trackgroup.TrackRenderer;
import com.android.tools.adtui.util.SwingUtil;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTrackRendererType;
import com.android.tools.profilers.cpu.analysis.CaptureNodeAnalysisModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import com.android.tools.profilers.cpu.capturedetails.CaptureNodeHRenderer;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Track renderer for CPU threads in CPU capture stage.
 */
public class CpuThreadTrackRenderer implements TrackRenderer<CpuThreadTrackModel, ProfilerTrackRendererType> {
  private final AspectObserver myObserver = new AspectObserver();

  @NotNull
  @Override
  public JComponent render(@NotNull TrackModel<CpuThreadTrackModel, ProfilerTrackRendererType> trackModel) {
    HTreeChart<CaptureNode> traceEventChart = createHChart(trackModel.getDataModel().getCallChartModel(),
                                                           trackModel.getDataModel().getCapture().getRange(),
                                                           trackModel.isCollapsed());
    MultiSelectionModel<CpuAnalyzable> multiSelectionModel = trackModel.getDataModel().getMultiSelectionModel();
    multiSelectionModel.addDependency(myObserver).onChange(MultiSelectionModel.Aspect.CHANGE_SELECTION, () -> {
      List<CpuAnalyzable> selection = multiSelectionModel.getSelection();
      if (!selection.isEmpty() && selection.get(0) instanceof CaptureNodeAnalysisModel) {
        // A trace event is selected, possibly in another thread track.
        // Update all tracks so that they render the deselection state (i.e. gray-out) for all of their nodes.
        traceEventChart.setSelectedNode(((CaptureNodeAnalysisModel)selection.get(0)).getNode());
      }
      else {
        // No trace event is selected. Reset all tracks' selection so they render the trace events in their default state.
        traceEventChart.setSelectedNode(null);
      }
    });

    StateChart<CpuProfilerStage.ThreadState> threadStateChart = createStateChart(trackModel.getDataModel().getThreadStateChartModel());
    JPanel panel = new JPanel();
    if (trackModel.isCollapsed() || threadStateChart == null) {
      // Don't show thread states if we don't have the chart for it or if the track is collapsed.
      panel.setLayout(new TabularLayout("*", "*"));
      panel.add(traceEventChart, new TabularLayout.Constraint(0, 0));
    }
    else {
      panel.setLayout(new TabularLayout("*", "8px,*"));
      panel.add(threadStateChart, new TabularLayout.Constraint(0, 0));
      panel.add(traceEventChart, new TabularLayout.Constraint(1, 0));
    }
    if (!trackModel.isCollapsed()) {
      panel.addMouseMotionListener(new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          if (threadStateChart != null && threadStateChart.contains(e.getPoint())) {
            trackModel.setActiveTooltipModel(trackModel.getDataModel().getThreadStateTooltip());
            threadStateChart.dispatchEvent(e);
          }
          else if (traceEventChart.contains(e.getPoint())) {
            // Translate mouse point to be relative of the tree chart component.
            Point p = e.getPoint();
            p.translate(-traceEventChart.getX(), -traceEventChart.getY());
            CaptureNode node = traceEventChart.getNodeAt(p);
            if (node == null) {
              trackModel.setActiveTooltipModel(null);
            }
            else {
              trackModel.setActiveTooltipModel(trackModel.getDataModel().getTraceEventTooltipBuilder().apply(node));
            }
            traceEventChart.dispatchEvent(SwingUtil.convertMouseEventPoint(e, p));
          }
          else {
            trackModel.setActiveTooltipModel(null);
          }
        }
      });
      panel.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          if (traceEventChart.contains(e.getPoint())) {
            // Translate mouse point to be relative of the tree chart component.
            Point p = e.getPoint();
            p.translate(-traceEventChart.getX(), -traceEventChart.getY());
            CaptureNode node = traceEventChart.getNodeAt(p);
            // Trace events only support single-selection.
            if (node != null) {
              multiSelectionModel.setSelection(
                Collections.singleton(new CaptureNodeAnalysisModel(node, trackModel.getDataModel().getCapture())));
            }
            else {
              multiSelectionModel.clearSelection();
            }
            traceEventChart.dispatchEvent(SwingUtil.convertMouseEventPoint(e, p));
          }
        }
      });
    }
    return panel;
  }

  @Nullable
  private static StateChart<CpuProfilerStage.ThreadState> createStateChart(@NotNull StateChartModel<CpuProfilerStage.ThreadState> model) {
    if (model.getSeries().isEmpty()) {
      // No thread state data, don't create chart.
      return null;
    }
    StateChart<CpuProfilerStage.ThreadState> threadStateChart = new StateChart<>(model, new CpuThreadColorProvider());
    threadStateChart.setHeightGap(0.0f);
    return threadStateChart;
  }

  private static HTreeChart<CaptureNode> createHChart(@NotNull CaptureDetails.CallChart callChartModel,
                                                      @NotNull Range captureRange,
                                                      boolean isCollapsed) {
    CaptureNode node = callChartModel.getNode();
    Range selectionRange = callChartModel.getRange();

    HTreeChart.Builder<CaptureNode> builder =
      new HTreeChart.Builder<>(node, selectionRange, new CaptureNodeHRenderer(CaptureDetails.Type.CALL_CHART))
        .setGlobalXRange(captureRange)
        .setOrientation(HTreeChart.Orientation.TOP_DOWN)
        .setRootVisible(false)
        .setNodeSelectionEnabled(true);
    if (isCollapsed) {
      builder.setCustomNodeHeightPx(1).setNodeYPaddingPx(0);
    }
    return builder.build();
  }

  private static class CpuThreadColorProvider extends StateChartColorProvider<CpuProfilerStage.ThreadState> {
    private EnumColors<CpuProfilerStage.ThreadState> myEnumColors = ProfilerColors.THREAD_STATES.build();

    @NotNull
    @Override
    public Color getColor(boolean isMouseOver, @NotNull CpuProfilerStage.ThreadState value) {
      myEnumColors.setColorIndex(isMouseOver ? 1 : 0);
      return myEnumColors.getColor(value);
    }
  }
}
