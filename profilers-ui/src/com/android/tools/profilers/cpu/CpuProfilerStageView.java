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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.flat.FlatButton;
import com.android.tools.adtui.model.DefaultDurationData;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.*;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BOTTOM_BORDER;
import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_TOP_BORDER;
import static com.android.tools.profilers.ProfilerLayout.*;

public class CpuProfilerStageView extends StageView<CpuProfilerStage> {
  private final CpuProfilerStage myStage;

  private final JButton myCaptureButton;
  /**
   * Contains the status of the capture, e.g. "Starting record...", "Recording - XXmXXs", etc.
   */
  private final JLabel myCaptureStatus;
  private final JBList<CpuThreadsModel.RangedCpuThread> myThreads;
  /**
   * The action listener of the capture button changes depending on the state of the profiler.
   * It can be either "start capturing" or "stop capturing".
   */
  @NotNull private final Splitter mySplitter;

  @NotNull private final LoadingPanel myCaptureViewLoading;

  @Nullable private CpuCaptureView myCaptureView;

  @NotNull  private final JComboBox<ProfilingConfiguration> myProfilingConfigurationCombo;

  @Nullable
  private ProfilerTooltipView myTooltipView;

  private JPanel myTooltip;

  @NotNull
  private final ViewBinder<CpuProfilerStageView, CpuProfilerStage.Tooltip, ProfilerTooltipView> myTooltipBinder;

  public CpuProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull CpuProfilerStage stage) {
    // TODO: decide if the constructor should be split into multiple methods in order to organize the code and improve readability
    super(profilersView, stage);
    myStage = stage;

    stage.getAspect().addDependency(this)
      .onChange(CpuProfilerAspect.CAPTURE, this::updateCaptureState)
      .onChange(CpuProfilerAspect.SELECTED_THREADS, this::updateThreadSelection)
      .onChange(CpuProfilerAspect.CAPTURE_DETAILS, this::updateCaptureDetails)
      .onChange(CpuProfilerAspect.CAPTURE_ELAPSED_TIME, this::updateCaptureElapsedTime);

    myTooltipBinder = new ViewBinder<>();
    myTooltipBinder.bind(CpuProfilerStage.UsageTooltip.class, CpuUsageTooltipView::new);
    myTooltipBinder.bind(CpuProfilerStage.ThreadsTooltip.class, CpuThreadsTooltipView::new);
    myStage.getAspect().addDependency(this).onChange(CpuProfilerAspect.TOOLTIP, this::tooltipChanged);

    StudioProfilers profilers = stage.getStudioProfilers();
    ProfilerTimeline timeline = profilers.getTimeline();

    TabularLayout layout = new TabularLayout("*");
    JPanel details = new JPanel(layout);
    details.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    EventMonitorView eventsView = new EventMonitorView(profilersView, stage.getEventMonitor());
    JComponent eventsComponent = eventsView.getComponent();

    JPanel monitorPanel = new JBPanel(new TabularLayout("*", "*"));
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    final AxisComponent leftAxis = new AxisComponent(getStage().getCpuUsageAxis(), AxisComponent.AxisOrientation.RIGHT);
    leftAxis.setShowAxisLine(false);
    leftAxis.setShowMax(true);
    leftAxis.setShowUnitAtMax(true);
    leftAxis.setHideTickAtMin(true);
    leftAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    leftAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    final AxisComponent rightAxis = new AxisComponent(getStage().getThreadCountAxis(), AxisComponent.AxisOrientation.LEFT);
    rightAxis.setShowAxisLine(false);
    rightAxis.setShowMax(true);
    rightAxis.setShowUnitAtMax(true);
    rightAxis.setHideTickAtMin(true);
    rightAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    rightAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(rightAxis, BorderLayout.EAST);

    SelectionComponent selection = new SelectionComponent(getStage().getSelectionModel());
    final JPanel overlayPanel = new JBPanel(new BorderLayout());
    overlayPanel.setOpaque(false);
    overlayPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    final OverlayComponent overlay = new OverlayComponent(selection);
    overlayPanel.add(overlay, BorderLayout.CENTER);

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));

    DetailedCpuUsage cpuUsage = getStage().getCpuUsage();
    LineChart lineChart = new LineChart(cpuUsage);
    lineChart.configure(cpuUsage.getCpuSeries(), new LineConfig(ProfilerColors.CPU_USAGE)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    lineChart.configure(cpuUsage.getOtherCpuSeries(), new LineConfig(ProfilerColors.CPU_OTHER_USAGE)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    lineChart.configure(cpuUsage.getThreadsCountSeries(), new LineConfig(ProfilerColors.THREADS_COUNT)
      .setStepped(true).setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));
    lineChart.setRenderOffset(0, (int)LineConfig.DEFAULT_DASH_STROKE.getLineWidth() / 2);
    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    CpuProfilerStage.CpuStageLegends legends = getStage().getLegends();
    final LegendComponent legend = new LegendComponent(legends);
    legend.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);
    legend.configure(legends.getCpuLegend(), new LegendConfig(lineChart.getLineConfig(cpuUsage.getCpuSeries())));
    legend.configure(legends.getOthersLegend(), new LegendConfig(lineChart.getLineConfig(cpuUsage.getOtherCpuSeries())));
    legend.configure(legends.getThreadsLegend(), new LegendConfig(lineChart.getLineConfig(cpuUsage.getThreadsCountSeries())));

    final JLabel label = new JLabel(getStage().getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);
    label.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    DurationDataRenderer<CpuCapture> traceRenderer =
      new DurationDataRenderer.Builder<>(getStage().getTraceDurations(), ProfilerColors.CPU_CAPTURE_EVENT)
        .setDurationBg(ProfilerColors.DEFAULT_BACKGROUND)
        .setLabelProvider(this::formatCaptureLabel)
        .setStroke(new BasicStroke(1))
        .setLabelColors(ProfilerColors.CPU_DURATION_LABEL_BACKGROUND, Color.BLACK, Color.lightGray, Color.WHITE)
        .setClickHander(getStage()::setAndSelectCapture)
        .build();

    traceRenderer.addCustomLineConfig(cpuUsage.getCpuSeries(), new LineConfig(ProfilerColors.CPU_USAGE_CAPTURED)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    traceRenderer.addCustomLineConfig(cpuUsage.getOtherCpuSeries(), new LineConfig(ProfilerColors.CPU_OTHER_USAGE_CAPTURED)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    traceRenderer.addCustomLineConfig(cpuUsage.getThreadsCountSeries(), new LineConfig(ProfilerColors.THREADS_COUNT_CAPTURED)
      .setStepped(true).setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));

    overlay.addDurationDataRenderer(traceRenderer);
    lineChart.addCustomRenderer(traceRenderer);

    DurationDataRenderer<DefaultDurationData> inProgressTraceRenderer =
      new DurationDataRenderer.Builder<>(getStage().getInProgressTraceDuration(), ProfilerColors.CPU_CAPTURE_EVENT)
        .setLabelProvider(data -> "Recording in progress")
        .setStroke(new BasicStroke(1))
        .setLabelColors(ProfilerColors.CPU_DURATION_LABEL_BACKGROUND, Color.BLACK, Color.lightGray, Color.WHITE)
        .build();
    overlay.addDurationDataRenderer(inProgressTraceRenderer);
    lineChart.addCustomRenderer(inProgressTraceRenderer);

    monitorPanel.add(axisPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(overlayPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(selection, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(lineChartPanel, new TabularLayout.Constraint(0, 0));

    CpuThreadsModel model = myStage.getThreadStates();
    myThreads = new JBList<>(model);
    myThreads.addListSelectionListener((e) -> {
      int selectedIndex = myThreads.getSelectedIndex();
      if (selectedIndex >= 0) {
        CpuThreadsModel.RangedCpuThread thread = model.getElementAt(selectedIndex);
        if (myStage.getSelectedThread() != thread.getThreadId()) {
          myStage.setSelectedThread(thread.getThreadId());
          myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectThread();
        }
      }
      else {
        myStage.setSelectedThread(CaptureModel.NO_THREAD);
      }
    });
    JScrollPane scrollingThreads = new MyScrollPane();
    scrollingThreads.setBorder(MONITOR_BORDER);
    scrollingThreads.setViewportView(myThreads);
    myThreads.setCellRenderer(new ThreadCellRenderer(myThreads, myStage.getUpdatableManager()));
    myThreads.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    details.add(eventsComponent, new TabularLayout.Constraint(0, 0));
    details.add(createTooltip(overlayPanel, overlay, myThreads), new TabularLayout.Constraint(1, 0, 2, 1));

    // Double-clicking the chart should remove a capture selection if one exists.
    MouseAdapter doubleClick = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && !e.isConsumed()) {
          getStage().getStudioProfilers().getTimeline().getSelectionRange().clear();
        }
      }
    };
    overlay.addMouseListener(doubleClick);
    overlayPanel.addMouseListener(doubleClick);

    layout.setRowSizing(1, "4*");
    details.add(monitorPanel, new TabularLayout.Constraint(1, 0));

    layout.setRowSizing(2, "6*");
    AxisComponent timeAxisGuide = new AxisComponent(myStage.getTimeAxisGuide(), AxisComponent.AxisOrientation.BOTTOM);
    timeAxisGuide.setShowAxisLine(false);
    timeAxisGuide.setShowLabels(false);
    timeAxisGuide.setHideTickAtMin(true);
    timeAxisGuide.setMarkerColor(ProfilerColors.CPU_AXIS_GUIDE_COLOR);
    scrollingThreads.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        timeAxisGuide.setMarkerLengths(scrollingThreads.getHeight(), 0);
      }
    });
    details.add(timeAxisGuide, new TabularLayout.Constraint(2, 0));
    details.add(scrollingThreads, new TabularLayout.Constraint(2, 0));

    AxisComponent timeAxis = buildTimeAxis(profilers);
    details.add(timeAxis, new TabularLayout.Constraint(3, 0));

    ProfilerScrollbar scrollbar = new ProfilerScrollbar(timeline, details);
    details.add(scrollbar, new TabularLayout.Constraint(4, 0));

    mySplitter = new Splitter(true);
    mySplitter.setFirstComponent(details);
    mySplitter.setSecondComponent(null);
    mySplitter.setShowDividerIcon(false);
    mySplitter.getDivider().setBorder(DEFAULT_TOP_BORDER);
    getComponent().add(mySplitter, BorderLayout.CENTER);

    myCaptureButton = new FlatButton();
    myCaptureButton.addActionListener(event -> capture());

    myCaptureStatus = new JLabel("");
    myCaptureStatus.setFont(AdtUiUtils.DEFAULT_FONT.deriveFont(12f));
    myCaptureStatus.setBorder(new EmptyBorder(0, 5, 0, 0));
    myCaptureStatus.setForeground(ProfilerColors.CPU_CAPTURE_STATUS);

    myCaptureViewLoading = getProfilersView().getIdeProfilerComponents().createLoadingPanel();
    myCaptureViewLoading.setLoadingText("Parsing capture...");

    updateCaptureState();

    myProfilingConfigurationCombo = new ComboBox<>();
    JComboBoxView<ProfilingConfiguration, CpuProfilerAspect> profilingConfiguration =
      new JComboBoxView<>(myProfilingConfigurationCombo, stage.getAspect(), CpuProfilerAspect.PROFILING_CONFIGURATION,
                          stage::getProfilingConfigurations, stage::getProfilingConfiguration, stage::setProfilingConfiguration);
    profilingConfiguration.bind();
    myProfilingConfigurationCombo.addKeyListener(new KeyAdapter() {
      /**
       * Select the next item, skipping over any separators encountered
       */
      private void skipSeparators(int indexDelta) {
        int selectedIndex = myProfilingConfigurationCombo.getSelectedIndex() + indexDelta;
        if (selectedIndex < 0 || selectedIndex == myProfilingConfigurationCombo.getItemCount()) {
          return;
        }
        while (myProfilingConfigurationCombo.getItemAt(selectedIndex) == CpuProfilerStage.CONFIG_SEPARATOR_ENTRY) {
          selectedIndex += indexDelta;
        }
        myProfilingConfigurationCombo.setSelectedIndex(selectedIndex);
      }

      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
          skipSeparators(1);
          e.consume();
        }
        else if (e.getKeyCode() == KeyEvent.VK_UP) {
          skipSeparators(-1);
          e.consume();
        }
      }
    });
    myProfilingConfigurationCombo.setRenderer(new ListCellRendererWrapper<ProfilingConfiguration>() {
      @Override
      public void customize(JList list, ProfilingConfiguration value, int index, boolean selected, boolean hasFocus) {
        if (value == CpuProfilerStage.EDIT_CONFIGURATIONS_ENTRY) {
          setIcon(AllIcons.Actions.EditSource);
          setText("Edit configurations...");
        } else if (value == CpuProfilerStage.CONFIG_SEPARATOR_ENTRY) {
          setSeparator();
        }
        else {
          setText(value.getName());
        }
      }
    });
  }

  @NotNull
  private JComponent createTooltip(@NotNull JPanel overlayPanel,
                                   @NotNull JComponent overlay,
                                   @NotNull JBList<CpuThreadsModel.RangedCpuThread> threads) {
    ProfilerTimeline timeline = myStage.getStudioProfilers().getTimeline();

    MouseListener usageListener = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        myStage.setTooltip(CpuProfilerStage.Tooltip.Type.USAGE);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myStage.setTooltip(null);
      }
    };
    overlay.addMouseListener(usageListener);
    overlayPanel.addMouseListener(usageListener);

    MouseAdapter threadsListener = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        myStage.setTooltip(CpuProfilerStage.Tooltip.Type.THREADS);
        mouseMoved(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myStage.setTooltip(null);
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        int row = threads.locationToIndex(e.getPoint());
        if (row != -1) {
          CpuThreadsModel.RangedCpuThread model = threads.getModel().getElementAt(row);
          if (myStage.getTooltip() instanceof CpuProfilerStage.ThreadsTooltip) {
            CpuProfilerStage.ThreadsTooltip tooltip = (CpuProfilerStage.ThreadsTooltip)myStage.getTooltip();
            tooltip.setThread(model.getName(), model.getStateSeries());
          }
        }
      }
    };
    threads.addMouseListener(threadsListener);
    threads.addMouseMotionListener(threadsListener);

    myTooltip = new JPanel(new BorderLayout());
    RangeTooltipComponent tooltip = new RangeTooltipComponent(timeline.getTooltipRange(), timeline.getViewRange(),
                                                              timeline.getDataRange(), myTooltip);
    // TODO: This needs to be refactored, because probably we don't handle mouse events
    //       properly when components are layered, currently mouse events should happen on the OverlayComponent.
    tooltip.registerListenersOn(overlay);
    tooltip.registerListenersOn(overlayPanel);
    tooltip.registerListenersOn(threads);
    return tooltip;
  }

  private void tooltipChanged() {
    if (myTooltipView != null) {
      myTooltipView.dispose();
      myTooltipView = null;
    }
    myTooltip.removeAll();

    if (myStage.getTooltip() != null) {
      myTooltipView = myTooltipBinder.build(this, myStage.getTooltip());
      myTooltip.add(myTooltipView.createComponent(), BorderLayout.CENTER);
    }
  }

  @VisibleForTesting
  static String formatTime(long micro) {
    // TODO unify with TimeAxisFormatter
    long mil = (micro / 1000) % 1000;
    long sec = (micro / (1000 * 1000)) % 60;
    long min = (micro / (1000 * 1000 * 60)) % 60;
    long hour = micro / (1000L * 1000L * 60L * 60L);

    return String.format("%02d:%02d:%02d.%03d", hour, min, sec, mil);
  }

  @Override
  public JComponent getToolbar() {
    JPanel panel = new JPanel(new BorderLayout());
    JPanel toolbar = new JPanel(TOOLBAR_LAYOUT);

    toolbar.add(myProfilingConfigurationCombo);
    toolbar.add(myCaptureButton);
    toolbar.add(myCaptureStatus);

    StudioProfilers profilers = getStage().getStudioProfilers();
    profilers.addDependency(this).onChange(ProfilerAspect.PROCESSES, () -> myCaptureButton.setEnabled(profilers.isProcessAlive()));
    myCaptureButton.setEnabled(profilers.isProcessAlive());

    panel.add(toolbar, BorderLayout.WEST);
    return panel;
  }

  private String formatCaptureLabel(CpuCapture capture) {
    Range range = getStage().getStudioProfilers().getTimeline().getDataRange();

    long min = (long)(capture.getRange().getMin() - range.getMin());
    long max = (long)(capture.getRange().getMax() - range.getMin());
    return formatTime(min) + " - " + formatTime(max);
  }

  private void updateCaptureState() {
    myCaptureViewLoading.stopLoading();
    switch (myStage.getCaptureState()) {
      case IDLE:
        myCaptureButton.setEnabled(true);
        myCaptureStatus.setText("");
        myCaptureButton.setToolTipText("Record a method trace");
        myCaptureButton.setIcon(ProfilerIcons.RECORD);
        // TODO: replace with loading icon
        myCaptureButton.setDisabledIcon(IconLoader.getDisabledIcon(ProfilerIcons.RECORD));
        break;
      case CAPTURING:
        myCaptureButton.setEnabled(true);
        myCaptureStatus.setText("");
        myCaptureButton.setToolTipText("Stop recording");
        myCaptureButton.setIcon(ProfilerIcons.STOP_RECORDING);
        // TODO: replace with loading icon
        myCaptureButton.setDisabledIcon(IconLoader.getDisabledIcon(ProfilerIcons.STOP_RECORDING));
        break;
      case PARSING:
        myCaptureViewLoading.startLoading();
        mySplitter.setSecondComponent(myCaptureViewLoading.getComponent());
        break;
      case STARTING:
        myCaptureButton.setEnabled(false);
        myCaptureStatus.setText("Starting record...");
        myCaptureButton.setToolTipText("");
        break;
      case STOPPING:
        myCaptureButton.setEnabled(false);
        myCaptureStatus.setText("Stopping record...");
        myCaptureButton.setToolTipText("");
    }
    CpuCapture capture = myStage.getCapture();
    if (capture == null) {
      // If the capture is still being parsed, the splitter second component should be myCaptureViewLoading
      if (myStage.getCaptureState() != CpuProfilerStage.CaptureState.PARSING) {
        mySplitter.setSecondComponent(null);
      }
      myCaptureView = null;
    }
    else {
      myCaptureView = new CpuCaptureView(this);
      mySplitter.setSecondComponent(myCaptureView.getComponent());
    }
  }

  private void updateCaptureElapsedTime() {
    if (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING) {
      long elapsedTimeUs = myStage.getCaptureElapsedTimeUs();
      myCaptureStatus.setText("Recording - " + TimeAxisFormatter.DEFAULT.getFormattedString(elapsedTimeUs, elapsedTimeUs, true));
    }
  }

  private void capture() {
    if (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING) {
      myStage.stopCapturing();

      FeatureTracker featureTracker = myStage.getStudioProfilers().getIdeServices().getFeatureTracker();
      ProfilingConfiguration profilingConfiguration = myStage.getProfilingConfiguration();

      if (profilingConfiguration.getProfilerType() == CpuProfiler.CpuProfilerType.ART) {
        if (profilingConfiguration.getMode() == CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED) {
          featureTracker.trackTraceArtSampled();
        }
        else if (profilingConfiguration.getMode() == CpuProfiler.CpuProfilingAppStartRequest.Mode.INSTRUMENTED) {
          featureTracker.trackTraceArtInstrumented();
        }
      }
      else if (profilingConfiguration.getProfilerType() == CpuProfiler.CpuProfilerType.SIMPLE_PERF) {
        // TODO: track simpleperf
      }
    }
    else {
      myStage.startCapturing();
    }
  }

  private void updateThreadSelection() {
    // Select the thread which has its tree displayed in capture panel in the threads list
    for (int i = 0; i < myThreads.getModel().getSize(); i++) {
      CpuThreadsModel.RangedCpuThread thread = myThreads.getModel().getElementAt(i);
      if (myStage.getSelectedThread() == thread.getThreadId()) {
        myThreads.setSelectedIndex(i);
      }
    }
  }

  private void updateCaptureDetails() {
    if (myCaptureView != null) {
      myCaptureView.updateView();
    }
  }

  private static class MyScrollPane extends JBScrollPane {

    private MyScrollPane() {
      super();
      getVerticalScrollBar().setOpaque(false);
    }

    @Override
    protected JViewport createViewport() {
      if (SystemInfo.isMac) {
        return super.createViewport();
      }
      // Overrides it because, when not on mac, JBViewport adds the width of the scrollbar to the right inset of the border,
      // which would consequently misplace the threads state chart.
      return new JViewport();
    }
  }

  private static class ThreadCellRenderer implements ListCellRenderer<CpuThreadsModel.RangedCpuThread> {

    /**
     * Label to display the thread name on a cell.
     */
    private final JLabel myLabel;

    /**
     * Maps a thread id to a {@link StateChartData} containing the chart that should be rendered on the cell corresponding to that thread.
     */
    private final Map<Integer, StateChartData> myStateCharts;

    /**
     * Keep the index of the item currently hovered.
     */
    private int myHoveredIndex = -1;

    /**
     * {@link UpdatableManager} responsible for managing the threads state charts.
     */
    private final UpdatableManager myUpdatableManager;

    public ThreadCellRenderer(JList<CpuThreadsModel.RangedCpuThread> list, UpdatableManager updatableManager) {
      myLabel = new JLabel();
      myLabel.setFont(AdtUiUtils.DEFAULT_FONT);
      Border rightSeparator = BorderFactory.createMatteBorder(0, 0, 0, 1, ProfilerColors.THREAD_LABEL_BORDER);
      Border marginLeft = new EmptyBorder(0, 10, 0, 0);
      myLabel.setBorder(new CompoundBorder(rightSeparator, marginLeft));
      myLabel.setOpaque(true);
      myUpdatableManager = updatableManager;
      myStateCharts = new HashMap<>();
      list.addMouseMotionListener(new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          Point p = new Point(e.getX(), e.getY());
          myHoveredIndex = list.locationToIndex(p);
        }
      });
    }

    @Override
    public Component getListCellRendererComponent(JList list,
                                                  CpuThreadsModel.RangedCpuThread value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      JPanel panel = new JPanel(new TabularLayout("150px,*", "*"));
      panel.setPreferredSize(new Dimension(panel.getPreferredSize().width, 15));
      panel.setBackground(list.getBackground());

      myLabel.setText(value.getName());
      myLabel.setBackground(ProfilerColors.THREAD_LABEL_BACKGROUND);
      myLabel.setForeground(ProfilerColors.THREAD_LABEL_TEXT);

      // Instead of using just one statechart for the cell renderer and set its model here, we cache the statecharts
      // corresponding to each thread and their models. StateChart#setModel is currently expensive and will make StateChart#render
      // to be called. As this method can be called by Swing more often than our update cycle, we cache the models to avoid
      // recalculating the render states. This causes the rendering time to be substantially improved.
      int tid = value.getThreadId();
      StateChartModel<CpuProfilerStage.ThreadState> model = value.getModel();
      if (myStateCharts.containsKey(tid) && !model.equals(myStateCharts.get(tid).getModel())) {
        // The model associated to the tid has changed. That might have happened because the tid was recycled and
        // assigned to another thread. The current model needs to be unregistered.
        myUpdatableManager.unregister(myStateCharts.get(tid).getModel());
      }
      StateChart<CpuProfilerStage.ThreadState> stateChart = getOrCreateStateChart(tid, model);
      // 1 is index of the selected color, 0 is of the non-selected
      // See more: {@link ProfilerColors#THREAD_STATES}
      stateChart.getColors().setColorIndex(isSelected ? 1 : 0);
      stateChart.setOpaque(true);

      if (isSelected) {
        // Cell is selected. Update its background accordingly.
        panel.setBackground(ProfilerColors.THREAD_SELECTED_BACKGROUND);
        myLabel.setBackground(ProfilerColors.THREAD_SELECTED_BACKGROUND);
        myLabel.setForeground(ProfilerColors.SELECTED_THREAD_LABEL_TEXT);
        // As the state chart is opaque the selected background wouldn't be visible
        // if we didn't set the opaqueness to false if the cell is selected.
        stateChart.setOpaque(false);
      }
      else if (myHoveredIndex == index) {
        // Cell is hovered. Draw the hover overlay over it.
        JPanel overlay = new JPanel();
        overlay.setBackground(ProfilerColors.DEFAULT_HOVER_COLOR);
        panel.add(overlay, new TabularLayout.Constraint(0, 0, 2));
      }

      panel.add(myLabel, new TabularLayout.Constraint(0, 0));
      panel.add(stateChart, new TabularLayout.Constraint(0, 0, 2));
      return panel;
    }

    /**
     * Returns a {@link StateChart} corresponding to a given thread or create a new one if it doesn't exist.
     */
    private StateChart<CpuProfilerStage.ThreadState> getOrCreateStateChart(int tid, StateChartModel<CpuProfilerStage.ThreadState> model) {
      if (myStateCharts.containsKey(tid) && myStateCharts.get(tid).getModel().equals(model)) {
        // State chart is already saved on the map. Return it.
        return myStateCharts.get(tid).getChart();
      }
      // The state chart corresponding to the thread is not stored on the map. Create a new one.
      StateChart<CpuProfilerStage.ThreadState> stateChart = new StateChart<>(model, ProfilerColors.THREAD_STATES);
      StateChartData data = new StateChartData(stateChart, model);
      stateChart.setHeightGap(0.40f);
      myStateCharts.put(tid, data);
      myUpdatableManager.register(model);
      return stateChart;
    }

    /**
     * Contains a state chart and its corresponding model.
     */
    private static class StateChartData {
      private final StateChart<CpuProfilerStage.ThreadState> myChart;
      private final StateChartModel<CpuProfilerStage.ThreadState> myModel;

      public StateChartData(StateChart<CpuProfilerStage.ThreadState> chart, StateChartModel<CpuProfilerStage.ThreadState> model) {
        myChart = chart;
        myModel = model;
      }

      public StateChart<CpuProfilerStage.ThreadState> getChart() {
        return myChart;
      }

      public StateChartModel<CpuProfilerStage.ThreadState> getModel() {
        return myModel;
      }
    }
  }
}
