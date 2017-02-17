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

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.model.DurationData;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SelectionModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.*;
import com.android.tools.profilers.common.LoadingPanel;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.memory.adapters.*;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.profilers.ProfilerLayout.*;

public class MemoryProfilerStageView extends StageView<MemoryProfilerStage> {
  @NotNull private final MemoryCaptureView myCaptureView = new MemoryCaptureView(getStage(), getIdeComponents());
  @NotNull private final MemoryHeapView myHeapView = new MemoryHeapView(getStage());
  @NotNull private final MemoryClassView myClassView = new MemoryClassView(getStage(), getIdeComponents());
  @NotNull private final MemoryClassGrouping myClassGrouping = new MemoryClassGrouping(getStage());
  @NotNull private final MemoryInstanceView myInstanceView = new MemoryInstanceView(getStage(), getIdeComponents());
  @NotNull private final MemoryInstanceDetailsView myInstanceDetailsView = new MemoryInstanceDetailsView(getStage(), getIdeComponents());

  @Nullable private CaptureObject myCaptureObject = null;

  @NotNull private final Splitter myMainSplitter = new Splitter(false);
  @NotNull private final Splitter myChartCaptureSplitter = new Splitter(true);
  @NotNull private final JPanel myCapturePanel;
  @Nullable private LoadingPanel myCaptureLoadingPanel;
  @NotNull private final Splitter myInstanceDetailsSplitter = new Splitter(true);

  @NotNull private JButton myAllocationButton;

  public MemoryProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull MemoryProfilerStage stage) {
    super(profilersView, stage);

    myChartCaptureSplitter.setFirstComponent(buildMonitorUi());
    myCapturePanel = buildCaptureUi();
    myInstanceDetailsSplitter.setOpaque(true);
    myInstanceDetailsSplitter.setFirstComponent(myInstanceView.getComponent());
    myInstanceDetailsSplitter.setSecondComponent(myInstanceDetailsView.getComponent());
    myMainSplitter.setFirstComponent(myChartCaptureSplitter);
    myMainSplitter.setSecondComponent(myInstanceDetailsSplitter);
    myMainSplitter.setProportion(0.6f);
    getComponent().add(myMainSplitter, BorderLayout.CENTER);
    captureObjectChanged();

    myAllocationButton = new JButton("Record");
    myAllocationButton.setToolTipText("Starts/stops recording of memory allocations");
    myAllocationButton
      .addActionListener(e -> getStage().trackAllocations(!getStage().isTrackingAllocations(), SwingUtilities::invokeLater));

    getStage().getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE, this::captureObjectChanged)
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::captureObjectFinishedLoading)
      .onChange(MemoryProfilerAspect.LEGACY_ALLOCATION, this::legacyAllocationChanged);

    legacyAllocationChanged();
  }

  @Override
  public JComponent getToolbar() {
    JButton backButton = new JButton();
    backButton.addActionListener(action -> getStage().getStudioProfilers().setMonitoringStage());
    backButton.setIcon(AllIcons.Actions.Back);

    JToolBar toolBar = new JToolBar();
    toolBar.setFloatable(false);
    toolBar.add(backButton);

    JButton forceGarbageCollectionButton = new JButton();
    forceGarbageCollectionButton.setIcon(ProfilerIcons.FORCE_GARBAGE_COLLECTION);
    forceGarbageCollectionButton.setToolTipText("Triggers a garbage collection event");
    forceGarbageCollectionButton.addActionListener(e -> getStage().forceGarbageCollection(SwingUtilities::invokeLater));
    toolBar.add(forceGarbageCollectionButton);

    JButton triggerHeapDumpButton = new JButton();
    triggerHeapDumpButton.setIcon(ProfilerIcons.HEAP_DUMP);
    triggerHeapDumpButton.setToolTipText("Takes an Hprof snapshot of the application memory");
    triggerHeapDumpButton.addActionListener(e -> getStage().requestHeapDump(SwingUtilities::invokeLater));
    toolBar.add(triggerHeapDumpButton);

    toolBar.add(myAllocationButton);

    return toolBar;
  }

  @VisibleForTesting
  @NotNull
  public Splitter getMainSplitter() {
    return myMainSplitter;
  }

  @VisibleForTesting
  @NotNull
  public Splitter getChartCaptureSplitter() {
    return myChartCaptureSplitter;
  }

  @VisibleForTesting
  @NotNull
  public JPanel getCapturePanel() {
    return myCapturePanel;
  }

  @VisibleForTesting
  @NotNull
  MemoryCaptureView getCaptureView() {
    return myCaptureView;
  }

  @VisibleForTesting
  @NotNull
  MemoryHeapView getHeapView() {
    return myHeapView;
  }

  @VisibleForTesting
  @NotNull
  MemoryClassGrouping getClassGrouping() {
    return myClassGrouping;
  }

  @VisibleForTesting
  @NotNull
  MemoryClassView getClassView() {
    return myClassView;
  }

  @VisibleForTesting
  @NotNull
  MemoryInstanceView getInstanceView() {
    return myInstanceView;
  }

  @VisibleForTesting
  @NotNull
  MemoryInstanceDetailsView getInstanceDetailsView() {
    return myInstanceDetailsView;
  }

  private void legacyAllocationChanged() {
    //TODO enable/disable hprof/allocation if they cannot be performed
    myAllocationButton.setText(getStage().isTrackingAllocations() ? "Stop" : "Record");
  }

  @NotNull
  private JPanel buildMonitorUi() {
    StudioProfilers profilers = getStage().getStudioProfilers();
    ProfilerTimeline timeline = profilers.getTimeline();
    Range viewRange = getTimeline().getViewRange();

    TabularLayout layout = new TabularLayout("*");
    JPanel panel = new JBPanel(layout);
    panel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    panel.setBackground(ProfilerColors.MONITOR_BACKGROUND);

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    ProfilerScrollbar sb = new ProfilerScrollbar(timeline, panel);
    panel.add(sb, new TabularLayout.Constraint(3, 0));

    AxisComponent timeAxis = buildTimeAxis(profilers);
    panel.add(timeAxis, new TabularLayout.Constraint(2, 0));

    EventMonitorView eventsView = new EventMonitorView(getProfilersView(), getStage().getEventMonitor());
    panel.add(eventsView.getComponent(), new TabularLayout.Constraint(0, 0));

    JPanel monitorPanel = new JBPanel(new TabularLayout("*", "*"));
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);
    final JLabel label = new JLabel(getStage().getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));

    DetailedMemoryUsage memoryUsage = getStage().getDetailedMemoryUsage();
    final LineChart lineChart = new LineChart(memoryUsage);
    configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_JAVA, memoryUsage.getJavaSeries());
    configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_NATIVE, memoryUsage.getNativeSeries());
    configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_GRAPHCIS, memoryUsage.getGraphicsSeries());
    configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_STACK, memoryUsage.getStackSeries());
    configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_CODE, memoryUsage.getCodeSeries());
    configureStackedFilledLine(lineChart, ProfilerColors.MEMORY_OTHERS, memoryUsage.getOtherSeries());
    lineChart.configure(memoryUsage.getTotalMemorySeries(), new LineConfig(ProfilerColors.MEMORY_TOTAL));
    lineChart.configure(memoryUsage.getObjectsSeries(), new LineConfig(ProfilerColors.MEMORY_OBJECTS)
      .setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));

    // TODO set proper colors / icons
    DurationDataRenderer<CaptureDurationData<HeapDumpCaptureObject>> heapDumpRenderer =
      new DurationDataRenderer.Builder<>(getStage().getHeapDumpSampleDurations(), Color.BLACK)
        .setLabelColors(Color.DARK_GRAY, Color.GRAY, Color.lightGray, Color.WHITE)
        .setStroke(new BasicStroke(2))
        .setIsBlocking(true)
        .setLabelProvider(
          data -> String.format("Dump (%s)", data.getDuration() == DurationData.UNSPECIFIED_DURATION ? "in progress" :
                                             TimeAxisFormatter.DEFAULT.getFormattedString(viewRange.getLength(), data.getDuration(), true)))
        .build();
    DurationDataRenderer<CaptureDurationData<AllocationsCaptureObject>> allocationRenderer =
      new DurationDataRenderer.Builder<>(getStage().getAllocationInfosDurations(), Color.LIGHT_GRAY)
        .setLabelColors(Color.DARK_GRAY, Color.GRAY, Color.lightGray, Color.WHITE)
        .setStroke(new BasicStroke(2))
        .setLabelProvider(data -> String
          .format("Allocation Record (%s)", data.getDuration() == DurationData.UNSPECIFIED_DURATION ? "in progress" :
                                            TimeAxisFormatter.DEFAULT.getFormattedString(viewRange.getLength(), data.getDuration(), true)))
        .build();
    DurationDataRenderer<GcDurationData> gcRenderer = new DurationDataRenderer.Builder<>(getStage().getGcCount(), Color.BLACK)
      .setIcon(ProfilerIcons.GARBAGE_EVENT)
      .build();

    lineChart.addCustomRenderer(heapDumpRenderer);
    lineChart.addCustomRenderer(allocationRenderer);
    lineChart.addCustomRenderer(gcRenderer);

    SelectionComponent selection = new SelectionComponent(getStage().getSelectionModel());
    final JPanel overlayPanel = new JBPanel(new BorderLayout());
    overlayPanel.setOpaque(false);
    overlayPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    final OverlayComponent overlay = new OverlayComponent(selection);
    overlay.addDurationDataRenderer(heapDumpRenderer);
    overlay.addDurationDataRenderer(allocationRenderer);
    overlay.addDurationDataRenderer(gcRenderer);
    overlayPanel.add(overlay, BorderLayout.CENTER);

    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    final AxisComponent memoryAxis = new AxisComponent(getStage().getMemoryAxis(), AxisComponent.AxisOrientation.RIGHT);
    memoryAxis.setShowAxisLine(false);
    memoryAxis.setShowMax(true);
    memoryAxis.setShowUnitAtMax(true);
    memoryAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    memoryAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(memoryAxis, BorderLayout.WEST);

    final AxisComponent rightAxis = new AxisComponent(getStage().getObjectsAxis(), AxisComponent.AxisOrientation.LEFT);
    rightAxis.setShowAxisLine(false);
    rightAxis.setShowMax(true);
    rightAxis.setShowUnitAtMax(true);
    rightAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    rightAxis.setMargins(0, Y_AXIS_TOP_MARGIN);

    axisPanel.add(rightAxis, BorderLayout.EAST);

    MemoryProfilerStage.MemoryStageLegends legends = getStage().getLegends();
    final LegendComponent legend = new LegendComponent(legends);
    legend.configure(legends.getJavaLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getJavaSeries())));
    legend.configure(legends.getNativeLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getNativeSeries())));
    legend.configure(legends.getGraphicsLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getGraphicsSeries())));
    legend.configure(legends.getStackLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getStackSeries())));
    legend.configure(legends.getCodeLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getCodeSeries())));
    legend.configure(legends.getOtherLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getOtherSeries())));
    legend.configure(legends.getTotalLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getTotalMemorySeries())));
    legend.configure(legends.getObjectsLegend(), new LegendConfig(lineChart.getLineConfig(memoryUsage.getObjectsSeries())));


    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    monitorPanel.add(overlayPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(selection, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(axisPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(lineChartPanel, new TabularLayout.Constraint(0, 0));

    layout.setRowSizing(1, "*"); // Give monitor as much space as possible
    panel.add(monitorPanel, new TabularLayout.Constraint(1, 0));

    return panel;
  }

  @NotNull
  private JPanel buildCaptureUi() {
    JPanel headingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    headingPanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    headingPanel.add(myCaptureView.getExportButton());
    headingPanel.add(myCaptureView.getComponent());

    JToolBar toolBar = new JToolBar();
    toolBar.setFloatable(false);
    toolBar.add(myHeapView.getComponent());
    toolBar.add(myClassGrouping.getComponent());
    headingPanel.add(toolBar);

    JPanel capturePanel = new JPanel(new BorderLayout());
    capturePanel.add(headingPanel, BorderLayout.PAGE_START);
    capturePanel.add(myClassView.getComponent(), BorderLayout.CENTER);
    return capturePanel;
  }

  private void captureObjectChanged() {
    // Forcefully ends the previous loading operation if it is still ongoing.
    stopLoadingUi();
    myCaptureObject = getStage().getSelectedCapture();
    if (myCaptureObject == null) {
      myChartCaptureSplitter.setSecondComponent(null);
      return;
    }

    myCaptureLoadingPanel = getProfilersView().getIdeProfilerComponents().createLoadingPanel();
    myCaptureLoadingPanel.setLoadingText("Fetching results");
    myCaptureLoadingPanel.startLoading();
    myChartCaptureSplitter.setSecondComponent(myCaptureLoadingPanel.getComponent());
  }

  private void captureObjectFinishedLoading() {
    if (myCaptureObject != getStage().getSelectedCapture()) {
      return;
    }

    stopLoadingUi();
    myChartCaptureSplitter.setSecondComponent(myCapturePanel);
  }

  private void stopLoadingUi() {
    if (myCaptureObject == null || myCaptureLoadingPanel == null) {
      return;
    }

    myCaptureLoadingPanel.stopLoading();
    myCaptureLoadingPanel = null;
    myChartCaptureSplitter.setSecondComponent(null);
  }

  private static void configureStackedFilledLine(LineChart chart, Color color, RangedContinuousSeries series) {
    chart.configure(series, new LineConfig(color).setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
  }

  /**
   * TODO currently we have slightly different icons for the MemoryInstanceView vs the MemoryInstanceDetailsView.
   * Re-investigate and see if they should share the same conditions.
   */
  @NotNull
  static Icon getInstanceObjectIcon(@NotNull InstanceObject instance) {
    if (instance instanceof FieldObject) {
      FieldObject field = (FieldObject)instance;
      if (field.getIsArray()) {
        return AllIcons.Debugger.Db_array;
      }
      else if (field.getIsPrimitive()) {
        return AllIcons.Debugger.Db_primitive;
      }
      else {
        return PlatformIcons.FIELD_ICON;
      }
    }
    else if (instance instanceof ReferenceObject) {
      ReferenceObject referrer = (ReferenceObject)instance;
      if (referrer.getIsRoot()) {
        return AllIcons.Hierarchy.Subtypes;
      }
      else if (referrer.getIsArray()) {
        return AllIcons.Debugger.Db_array;
      }
      else {
        return PlatformIcons.FIELD_ICON;
      }
    }
    else {
      return PlatformIcons.INTERFACE_ICON;
    }
  }
}
