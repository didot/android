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

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.model.SelectionListener;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.*;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS;
import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_VERTICAL_BORDERS;
import static com.android.tools.profilers.ProfilerLayout.*;

public class EnergyProfilerStageView extends StageView<EnergyProfilerStage> {

  @NotNull private final JPanel myEventsPanel;
  @NotNull private final EnergyDetailsView myDetailsView;

  public EnergyProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull EnergyProfilerStage energyProfilerStage) {
    super(profilersView, energyProfilerStage);

    getTooltipBinder().bind(EnergyStageTooltip.class, EnergyStageTooltipView::new);
    getTooltipBinder().bind(EventActivityTooltip.class, EventActivityTooltipView::new);
    getTooltipBinder().bind(EventSimpleEventTooltip.class, EventSimpleEventTooltipView::new);

    JBSplitter verticalSplitter = new JBSplitter(true);
    verticalSplitter.getDivider().setBorder(DEFAULT_HORIZONTAL_BORDERS);
    verticalSplitter.setFirstComponent(buildMonitorUi());

    myEventsPanel = new JPanel(new TabularLayout("*,Fit-", "Fit-,*"));
    myEventsPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
    myEventsPanel.add(getSelectionTimeLabel(), new TabularLayout.Constraint(0, 1));

    JComponent eventsView = new EnergyEventsView(this).getComponent();
    myEventsPanel.add(new JBScrollPane(eventsView), new TabularLayout.Constraint(1, 0, 1, 2));
    myEventsPanel.setVisible(false);
    verticalSplitter.setSecondComponent(myEventsPanel);

    myDetailsView = new EnergyDetailsView(this);
    myDetailsView.setMinimumSize(new Dimension(JBUI.scale(450), (int)myDetailsView.getMinimumSize().getHeight()));
    myDetailsView.setVisible(false);
    JBSplitter splitter = new JBSplitter(false, 0.6f);
    splitter.setFirstComponent(verticalSplitter);
    splitter.setSecondComponent(myDetailsView);
    splitter.setHonorComponentsMinimumSize(true);
    splitter.getDivider().setBorder(DEFAULT_VERTICAL_BORDERS);

    getComponent().add(splitter, BorderLayout.CENTER);

    getStage().getAspect().addDependency(this)
      .onChange(EnergyProfilerAspect.SELECTED_EVENT_DURATION, this::updateSelectedDurationView);
  }

  @NotNull
  private JPanel buildMonitorUi() {
    StudioProfilers profilers = getStage().getStudioProfilers();
    ProfilerTimeline timeline = profilers.getTimeline();
    RangeTooltipComponent tooltip =
      new RangeTooltipComponent(timeline.getTooltipRange(),
                                timeline.getViewRange(),
                                timeline.getDataRange(),
                                getTooltipPanel(),
                                ProfilerLayeredPane.class);
    TabularLayout layout = new TabularLayout("*");
    JPanel panel = new JBPanel(layout);
    panel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    // Order matters, as such we want to put the tooltip component first so we draw the tooltip line on top of all other
    // components.
    panel.add(tooltip, new TabularLayout.Constraint(0, 0, 2, 1));

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    ProfilerScrollbar scrollbar = new ProfilerScrollbar(timeline, panel);
    panel.add(scrollbar, new TabularLayout.Constraint(4, 0));

    JComponent timeAxis = buildTimeAxis(profilers);
    panel.add(timeAxis, new TabularLayout.Constraint(3, 0));

    EventMonitorView eventsView = new EventMonitorView(getProfilersView(), getStage().getEventMonitor());
    JComponent eventsComponent = eventsView.getComponent();
    panel.add(eventsComponent, new TabularLayout.Constraint(0, 0));

    JPanel monitorPanel = new JBPanel(new TabularLayout("*", "*"));
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);
    final JLabel label = new JLabel(getStage().getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    DetailedEnergyUsage usage = getStage().getDetailedUsage();

    final LineChart lineChart = new LineChart(usage);

    LineConfig cpuConfig = new LineConfig(ProfilerColors.ENERGY_CPU)
      .setFilled(true)
      .setStacked(true)
      .setLegendIconType(LegendConfig.IconType.BOX)
      .setDataBucketInterval(EnergyMonitorView.CHART_INTERVAL_US);
    lineChart.configure(usage.getCpuUsageSeries(), cpuConfig);
    LineConfig networkConfig = new LineConfig(ProfilerColors.ENERGY_NETWORK)
      .setFilled(true)
      .setStacked(true)
      .setLegendIconType(LegendConfig.IconType.BOX)
      .setDataBucketInterval(EnergyMonitorView.CHART_INTERVAL_US);
    lineChart.configure(usage.getNetworkUsageSeries(), networkConfig);
    LineConfig locationConfig = new LineConfig(ProfilerColors.ENERGY_LOCATION)
      .setFilled(true)
      .setStacked(true)
      .setLegendIconType(LegendConfig.IconType.BOX)
      .setDataBucketInterval(EnergyMonitorView.CHART_INTERVAL_US);
    lineChart.configure(usage.getLocationUsageSeries(), locationConfig);
    lineChart.setRenderOffset(0, (int)LineConfig.DEFAULT_DASH_STROKE.getLineWidth() / 2);
    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    final AxisComponent leftAxis = new AxisComponent(getStage().getAxis(), AxisComponent.AxisOrientation.RIGHT);
    leftAxis.setShowAxisLine(false);
    leftAxis.setShowUnitAtMax(true);
    leftAxis.setHideTickAtMin(true);
    leftAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    leftAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    EnergyProfilerStage.EnergyUsageLegends legends = getStage().getLegends();
    LegendComponent legend = new LegendComponent.Builder(legends).setRightPadding(PROFILER_LEGEND_RIGHT_PADDING).build();
    legend.configure(legends.getCpuLegend(), new LegendConfig(lineChart.getLineConfig(usage.getCpuUsageSeries())));
    legend.configure(legends.getNetworkLegend(), new LegendConfig(lineChart.getLineConfig(usage.getNetworkUsageSeries())));
    legend.configure(legends.getLocationLegend(), new LegendConfig(lineChart.getLineConfig(usage.getLocationUsageSeries())));

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    SelectionComponent selection = new SelectionComponent(getStage().getSelectionModel(), getTimeline().getViewRange());
    selection.setCursorSetter(ProfilerLayeredPane::setCursorOnProfilerLayeredPane);
    getStage().getSelectionModel().addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        myEventsPanel.setVisible(true);
      }

      @Override
      public void selectionCleared() {
        myEventsPanel.setVisible(false);
      }

      @Override
      public void selectionCreationFailure() {
        myEventsPanel.setVisible(false);
      }
    });
    // Clears the selected duration when the new selection range does not overlap with it.
    selection.addSelectionUpdatedListener(selectionRange -> {
      if (getStage().getSelectedDuration() != null) {
        List<EnergyProfiler.EnergyEvent> eventList = getStage().getSelectedDuration().getEventList();
        long detailsStartNs = TimeUnit.NANOSECONDS.toMicros(eventList.get(0).getTimestamp());
        EnergyProfiler.EnergyEvent lastEvent = eventList.get(eventList.size() - 1);
        long detailsEndNs = lastEvent.getIsTerminal() ? TimeUnit.NANOSECONDS.toMicros(lastEvent.getTimestamp()) : Long.MAX_VALUE;
        if (selectionRange.getMax() < detailsStartNs || selectionRange.getMin() > detailsEndNs) {
          getStage().setSelectedDuration(null);
        }
      }
    });

    JComponent minibar = new EnergyEventMinibar(this).getComponent();

    selection.addMouseListener(new ProfilerTooltipMouseAdapter(getStage(), () -> new EnergyStageTooltip(getStage())));
    tooltip.registerListenersOn(selection);
    eventsView.registerTooltip(tooltip, getStage());

    if (!getStage().hasUserUsedEnergySelection()) {
      installProfilingInstructions(monitorPanel);
    }

    monitorPanel.add(axisPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(lineChartPanel, new TabularLayout.Constraint(0, 0));

    JPanel stagePanel = new JPanel(new TabularLayout("*", "*,50px"));
    stagePanel.add(monitorPanel, new TabularLayout.Constraint(0, 0));
    stagePanel.add(minibar, new TabularLayout.Constraint(1, 0));
    layout.setRowSizing(1, "*");
    stagePanel.setBackground(null);

    panel.add(selection, new TabularLayout.Constraint(1, 0));
    panel.add(stagePanel, new TabularLayout.Constraint(1, 0));

    return panel;
  }

  @Override
  public JComponent getToolbar() {
    return new JPanel();
  }

  private void updateSelectedDurationView() {
    myDetailsView.setDuration(getStage().getSelectedDuration());
  }

  private void installProfilingInstructions(@NotNull JPanel parent) {
    assert parent.getLayout().getClass() == TabularLayout.class;
    InstructionsPanel panel =
      new InstructionsPanel.Builder(new TextInstruction(PROFILING_INSTRUCTIONS_FONT, "Select a range to inspect energy events"))
        .setEaseOut(getStage().getInstructionsEaseOutModel(), instructionPanel -> parent.remove(instructionPanel))
        .setBackgroundCornerRadius(PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER, PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER)
        .build();
    parent.add(panel, new TabularLayout.Constraint(0, 0));
  }
}
