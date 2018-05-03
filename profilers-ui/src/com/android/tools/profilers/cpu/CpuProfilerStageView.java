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
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.instructions.IconInstruction;
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.NewRowInstruction;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.model.DefaultDurationData;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.ui.HideablePanel;
import com.android.tools.profiler.proto.CpuProfiler.TraceInitiationType;
import com.android.tools.profilers.*;
import com.android.tools.profilers.cpu.atrace.CpuKernelTooltip;
import com.android.tools.profilers.event.*;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.sessions.SessionsManager;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS;
import static com.android.tools.profilers.ProfilerColors.CPU_CAPTURE_BACKGROUND;
import static com.android.tools.profilers.ProfilerLayout.*;
import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;

public class CpuProfilerStageView extends StageView<CpuProfilerStage> {
  private enum PanelSpacing {
    /**
     * String to represent the portion of the stage that the cpu monitor accounts for. This value ends up being roughly 40%
     * when the threads/kernel panels are also expanded.
     */
    MONITOR_PANEL_SPACING("4*"),

    /**
     * String to represent the threads/kernel view when an element expanded. This value is used in the initial sizing as well
     * as the sizing for when the {@link HideablePanel} is expanded from a collapsed state.
     */
    HIDEABLE_PANEL_EXPANDED("6*"),

    /**
     * String to represent the threads view when an element is hidden. This value is used when the {@link HideablePanel} is
     * collapsed and we need to adjust the size of our layout accordingly.
     */
    HIDEABLE_PANEL_COLLAPSED("Fit-"),

    /**
     * String to represent the kernel view. This string means that the elements preferred size will be used when determining
     * sizing. As such the panel does not need to change sizing rules when expanding/collapsing.
     */
    HIDEABLE_PANEL_FIT("Fit");

    private final String myLayoutString;

    PanelSpacing(@NotNull String layoutType) {
      myLayoutString = layoutType;
    }

    public String toString() {
      return myLayoutString;
    }
  }

  /**
   * Row index of the monitor panel in the TabularLayout of the {@code monitorCpuThreadsLayout}.
   */
  private static final int MONITOR_PANEL_ROW = 0;

  /**
   * Row index of the kernel panel in the TabularLayout of the {@code monitorCpuThreadsLayout}.
   */
  private static final int KERNEL_PANEL_ROW = 1;

  /**
   * Row index of the threads panel in the TabularLayout of the {@code monitorCpuThreadsLayout}.
   */
  private static final int THREADS_PANEL_ROW = 2;

  /**
   * Default ratio of splitter. The splitter ratio adjust the first elements size relative to the bottom elements size.
   * A ratio of 1 means only the first element is shown, while a ratio of 0 means only the bottom element is shown.
   */
  @VisibleForTesting
  static final float SPLITTER_DEFAULT_RATIO = 0.5f;

  /**
   * When we are showing the kernel data we want to increase the size of the kernel and threads view. This in turn reduces
   * the size of the view used for the CallChart, FlameChart, ect..
   */
  @VisibleForTesting
  static final float KERNEL_VIEW_SPLITTER_RATIO = 0.75f;

  private final CpuProfilerStage myStage;

  private final JButton myCaptureButton;
  /**
   * Contains the status of the capture, e.g. "Starting record...", "Recording - XXmXXs", etc.
   */
  private final JLabel myCaptureStatus;
  private final DragAndDropList<CpuThreadsModel.RangedCpuThread> myThreads;
  private final JList<CpuKernelModel.CpuState> myCpus;
  /**
   * The action listener of the capture button changes depending on the state of the profiler.
   * It can be either "start capturing" or "stop capturing".
   */
  @NotNull private final JBSplitter mySplitter;

  @NotNull private final LoadingPanel myCaptureViewLoading;

  @Nullable private CpuCaptureView myCaptureView;

  @NotNull private final CpuProfilingConfigurationView myProfilingConfigurationView;

  /**
   * Panel to let user know to take a capture.
   */
  @NotNull private final JPanel myHelpTipPanel;

  @NotNull private final SelectionComponent mySelection;

  @NotNull private final RangeTooltipComponent myTooltipComponent;

  public CpuProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull CpuProfilerStage stage) {
    super(profilersView, stage);
    myStage = stage;
    ProfilerTimeline timeline = getTimeline();

    stage.getAspect().addDependency(this)
         .onChange(CpuProfilerAspect.CAPTURE_STATE, this::updateCaptureState)
         .onChange(CpuProfilerAspect.CAPTURE_SELECTION, this::updateCaptureSelection)
         .onChange(CpuProfilerAspect.SELECTED_THREADS, this::updateThreadSelection)
         .onChange(CpuProfilerAspect.CAPTURE_DETAILS, this::updateCaptureDetails)
         .onChange(CpuProfilerAspect.CAPTURE_ELAPSED_TIME, this::updateCaptureElapsedTime);

    getTooltipBinder().bind(CpuUsageTooltip.class, CpuUsageTooltipView::new);
    getTooltipBinder().bind(CpuKernelTooltip.class, CpuKernelTooltipView::new);
    getTooltipBinder().bind(CpuThreadsTooltip.class, CpuThreadsTooltipView::new);
    getTooltipBinder().bind(EventActivityTooltip.class, EventActivityTooltipView::new);
    getTooltipBinder().bind(EventSimpleEventTooltip.class, EventSimpleEventTooltipView::new);
    getTooltipPanel().setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
    myTooltipComponent = new RangeTooltipComponent(timeline.getTooltipRange(),
                                                   timeline.getViewRange(),
                                                   timeline.getDataRange(),
                                                   getTooltipPanel(),
                                                   ProfilerLayeredPane.class);
    mySelection = new SelectionComponent(getStage().getSelectionModel(), getTimeline().getViewRange());
    mySelection.setCursorSetter(ProfilerLayeredPane::setCursorOnProfilerLayeredPane);
    myThreads = new DragAndDropList<>(myStage.getThreadStates());
    myCpus = new JBList<>(myStage.getCpuKernelModel());

    final OverlayComponent overlay = new OverlayComponent(mySelection);

    // "Fit" for the event profiler, "*" for everything else.
    final JPanel details = new JPanel(new TabularLayout("*", "Fit-,*"));
    details.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    // Order matters as such our tooltip component should be first so it draws on top of all elements.
    details.add(myTooltipComponent, new TabularLayout.Constraint(0, 0, 3, 1));

    if (!myStage.isImportTraceMode()) {
      // We shouldn't display the events monitor while in import trace mode.
      final EventMonitorView eventsView = new EventMonitorView(profilersView, stage.getEventMonitor());
      eventsView.registerTooltip(myTooltipComponent, getStage());
      details.add(eventsView.getComponent(), new TabularLayout.Constraint(0, 0));
    }

    final JPanel monitorPanel = new JBPanel(new TabularLayout("*", "*"));
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);
    monitorPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    if (!getStage().hasUserUsedCpuCapture() && !getStage().isImportTraceMode()) {
      installProfilingInstructions(monitorPanel);
    }

    if (myStage.isImportTraceMode()) {
      final JPanel tipPanel = new JBPanel(new BorderLayout());
      configureImportTipPanel(tipPanel);

      final AxisComponent timeAxisGuide = new AxisComponent(myStage.getTimeAxisGuide(), AxisComponent.AxisOrientation.BOTTOM);
      configureImportAxisPanel(timeAxisGuide, monitorPanel);

      final JPanel overlayPanel = new JBPanel(new TabularLayout("*", "*"));
      configureImportOverlayPanel(overlayPanel, overlay);

      // Order is important
      monitorPanel.add(timeAxisGuide, new TabularLayout.Constraint(0, 0));
      monitorPanel.add(overlay, new TabularLayout.Constraint(0, 0));
      monitorPanel.add(mySelection, new TabularLayout.Constraint(0, 0));
      monitorPanel.add(tipPanel, new TabularLayout.Constraint(0, 0));
      monitorPanel.add(overlayPanel, new TabularLayout.Constraint(0, 0));
    }
    else {
      final JPanel axisPanel = new JBPanel(new BorderLayout());
      configureAxisPanel(axisPanel);

      final JPanel legendPanel = new JBPanel(new BorderLayout());
      configureLegendPanel(legendPanel);

      final JPanel overlayPanel = new JBPanel(new BorderLayout());
      configureOverlayPanel(overlayPanel, overlay);

      final JPanel lineChartPanel = new JBPanel(new BorderLayout());
      configureLineChart(lineChartPanel, overlay);

      // Panel that represents the cpu utilization.
      // Order is important
      monitorPanel.add(axisPanel, new TabularLayout.Constraint(0, 0));
      monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));
      monitorPanel.add(overlayPanel, new TabularLayout.Constraint(0, 0));
      monitorPanel.add(mySelection, new TabularLayout.Constraint(0, 0));
      monitorPanel.add(lineChartPanel, new TabularLayout.Constraint(0, 0));
    }

    JComponent timeAxis = buildTimeAxis(myStage.getStudioProfilers());
    ProfilerScrollbar scrollbar = new ProfilerScrollbar(timeline, details);

    TabularLayout monitorCpuThreadsLayout = new TabularLayout("*");
    // The cpu monitor takes up 40%.
    monitorCpuThreadsLayout.setRowSizing(MONITOR_PANEL_ROW, PanelSpacing.MONITOR_PANEL_SPACING.toString());
    // The CPU list is hidden by default so we use "Fit" making it be 0.
    monitorCpuThreadsLayout.setRowSizing(KERNEL_PANEL_ROW, PanelSpacing.HIDEABLE_PANEL_FIT.toString());
    // The threads list is expanded and takes up roughly 60%.
    monitorCpuThreadsLayout.setRowSizing(THREADS_PANEL_ROW, PanelSpacing.HIDEABLE_PANEL_EXPANDED.toString());

    final JPanel monitorCpuThreadsPanel = new JBPanel(monitorCpuThreadsLayout);
    monitorCpuThreadsPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    configureCpuPanel(monitorCpuThreadsPanel, monitorCpuThreadsLayout);
    configureThreadsPanel(monitorCpuThreadsPanel, monitorCpuThreadsLayout);
    monitorCpuThreadsPanel.add(monitorPanel, new TabularLayout.Constraint(MONITOR_PANEL_ROW, 0));

    // Panel that represents all of L2
    details.add(monitorCpuThreadsPanel, new TabularLayout.Constraint(1, 0));
    details.add(timeAxis, new TabularLayout.Constraint(3, 0));
    details.add(scrollbar, new TabularLayout.Constraint(4, 0));

    myHelpTipPanel = new JPanel(new BorderLayout());
    configureHelpTipPanel();

    // The first component in the splitter is the L2 components, the 2nd component is the L3 components.
    mySplitter = new JBSplitter(true);
    mySplitter.setFirstComponent(details);
    mySplitter.setSecondComponent(null);
    mySplitter.getDivider().setBorder(DEFAULT_HORIZONTAL_BORDERS);
    getComponent().add(mySplitter, BorderLayout.CENTER);

    myCaptureButton = new CommonButton();
    myCaptureButton.addActionListener(event -> capture());

    myCaptureStatus = new JLabel("");
    myCaptureStatus.setFont(AdtUiUtils.DEFAULT_FONT.deriveFont(12f));
    myCaptureStatus.setBorder(JBUI.Borders.emptyLeft(5));
    myCaptureStatus.setForeground(ProfilerColors.CPU_CAPTURE_STATUS);

    myCaptureViewLoading = getProfilersView().getIdeProfilerComponents().createLoadingPanel(-1);
    myCaptureViewLoading.setLoadingText("Parsing capture...");

    myProfilingConfigurationView = new CpuProfilingConfigurationView(myStage, getIdeComponents());

    updateCaptureState();
    installContextMenu();
  }

  /**
   * Makes sure the selected capture fits entirely in user's view range.
   */
  private void ensureCaptureInViewRange() {
    CpuCapture capture = myStage.getCapture();
    assert capture != null;

    // Give a padding to the capture. 5% of the view range on each side.
    ProfilerTimeline timeline = myStage.getStudioProfilers().getTimeline();
    double padding = timeline.getViewRange().getLength() * 0.05;
    // Now makes sure the capture range + padding is within view range and in the middle if possible.
    timeline.adjustRangeCloseToMiddleView(new Range(capture.getRange().getMin() - padding, capture.getRange().getMax() + padding));
  }

  /**
   * This function handles the layout and rendering of the cpu kernel panel. This panel represents
   * each core found in an atrace file and the state associated with each core.
   *
   * @param monitorCpuThreadsPanel  panel that is assumed to contain the Kernel list, as well as the Threads List.
   * @param monitorCpuThreadsLayout the layout of the panel containing the two list.
   */
  private void configureCpuPanel(JPanel monitorCpuThreadsPanel, TabularLayout monitorCpuThreadsLayout) {
    CpuKernelModel cpuModel = myStage.getCpuKernelModel();
    JScrollPane scrollingCpus = new MyScrollPane();
    scrollingCpus.setBorder(MONITOR_BORDER);
    scrollingCpus.setViewportView(myCpus);
    scrollingCpus.addMouseWheelListener(new CpuMouseWheelListener(monitorCpuThreadsPanel));
    myCpus.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    myCpus.setCellRenderer(new CpuKernelCellRenderer(getStage().getStudioProfilers().getIdeServices().getFeatureConfig(),
                                                     myStage.getStudioProfilers().getSession().getPid(),
                                                     myStage.getUpdatableManager(), myCpus, myThreads));

    // Handle selection.
    myCpus.addListSelectionListener((e) -> cpuKernelRunningStateSelected(cpuModel));
    myCpus.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        cpuKernelRunningStateSelected(cpuModel);
        getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectCpuKernelElement();
      }
    });

    // Handle Tooltip
    myTooltipComponent.registerListenersOn(myCpus);
    myCpus.addMouseListener(new ProfilerTooltipMouseAdapter(myStage, () -> new CpuKernelTooltip(myStage)));
    myCpus.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = myCpus.locationToIndex(e.getPoint());
        if (row != -1) {
          CpuKernelModel.CpuState model = myCpus.getModel().getElementAt(row);
          if (myStage.getTooltip() instanceof CpuKernelTooltip) {
            CpuKernelTooltip tooltip = (CpuKernelTooltip)myStage.getTooltip();
            tooltip.setCpuSeries(model.getSeries());
          }
        }
      }
    });

    // Create hideable panel for CPU list.
    HideablePanel hideableCpus = new HideablePanel.Builder("KERNEL", scrollingCpus)
      .setShowSeparator(false)
      // We want to keep initially expanded to false because the kernel layout is set to "Fix" by default. As such when
      // we later change the contents to have elements and expand the view we also want to trigger the StateChangedListener below
      // to properly set the layout to be expanded. If we set initially expanded to true, then the StateChangedListener will never
      // get triggered and we will not update our layout.
      .setInitiallyExpanded(false)
      .build();

    // Handle when we get CPU data we want to show the cpu list.
    cpuModel.addListDataListener(new ListDataListener() {
      @Override
      public void contentsChanged(ListDataEvent e) {
        boolean hasElements = myCpus.getModel().getSize() != 0;
        // Lets only show 4 cores max the user can scroll to view the rest.
        myCpus.setVisibleRowCount(Math.min(4, myCpus.getModel().getSize()));
        hideableCpus.setVisible(hasElements);
        hideableCpus.setExpanded(hasElements);
        hideableCpus.setTitle(String.format("KERNEL (%d)", myCpus.getModel().getSize()));
        // When the CpuKernelModel is updated we adjust the splitter. The higher the number the more space
        // the first component occupies. For when we are showing Kernel elements we want to take up more space
        // than when we are not. As such each time we modify the CpuKernelModel (when a trace is selected) we
        // adjust the proportion of the splitter accordingly.
        if (hasElements) {
          mySplitter.setProportion(KERNEL_VIEW_SPLITTER_RATIO);
        }
        else {
          mySplitter.setProportion(SPLITTER_DEFAULT_RATIO);
        }
        monitorCpuThreadsPanel.revalidate();
      }

      @Override
      public void intervalAdded(ListDataEvent e) {
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
      }
    });
    // Hide CPU panel by default
    hideableCpus.setVisible(false);

    // Clear border set by default on the hideable panel.
    hideableCpus.setBorder(JBUI.Borders.empty());
    hideableCpus.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    hideableCpus.addStateChangedListener(
      (e) -> getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackToggleCpuKernelHideablePanel());
    scrollingCpus.setBorder(JBUI.Borders.empty());
    monitorCpuThreadsPanel.add(hideableCpus, new TabularLayout.Constraint(KERNEL_PANEL_ROW, 0));
  }

  private void configureHelpTipPanel() {
    FontMetrics headerMetrics = SwingUtilities2.getFontMetrics(myHelpTipPanel, INFO_MESSAGE_HEADER_FONT);
    FontMetrics bodyMetrics = SwingUtilities2.getFontMetrics(myHelpTipPanel, INFO_MESSAGE_DESCRIPTION_FONT);
    InstructionsPanel infoMessage = new InstructionsPanel.Builder(
      new TextInstruction(headerMetrics, "Thread details unavailable"),
      new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
      new TextInstruction(bodyMetrics, "Click the record button "),
      new IconInstruction(StudioIcons.Profiler.Toolbar.RECORD, PROFILING_INSTRUCTIONS_ICON_PADDING, null),
      new TextInstruction(bodyMetrics, " to start CPU profiling"),
      new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
      new TextInstruction(bodyMetrics, "or select a capture in the timeline."))
      .setColors(JBColor.foreground(), null)
      .build();
    myHelpTipPanel.add(infoMessage, BorderLayout.CENTER);
  }

  @SuppressWarnings("UseJBColor")
  private void configureImportTipPanel(JPanel panel) {
    panel.setOpaque(false);
    panel.setBackground(new Color(0, 0, 0, 0));
    InstructionsPanel infoMessage = new InstructionsPanel.Builder(
      new TextInstruction(SwingUtilities2.getFontMetrics(panel, INFO_MESSAGE_HEADER_FONT), "Cpu usage details unavailable"))
      .setColors(JBColor.foreground(), null)
      .build();
    panel.add(infoMessage);
  }

  private void configureImportAxisPanel(AxisComponent timeAxisGuide, JPanel monitorPanel) {
    timeAxisGuide.setShowAxisLine(false);
    timeAxisGuide.setShowLabels(false);
    timeAxisGuide.setHideTickAtMin(true);
    timeAxisGuide.setMarkerColor(ProfilerColors.CPU_AXIS_GUIDE_COLOR);
    monitorPanel.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        timeAxisGuide.setMarkerLengths(monitorPanel.getHeight(), 0);
      }
    });
  }

  @SuppressWarnings("UseJBColor")
  private void configureImportOverlayPanel(JPanel overlay, OverlayComponent overlayComponent) {
    overlay.setOpaque(false);
    LineChart lineChart = new LineChart(new ArrayList<>());
    DurationDataRenderer<CpuTraceInfo> traceRenderer =
      new DurationDataRenderer.Builder<>(getStage().getTraceDurations(), ProfilerColors.CPU_CAPTURE_EVENT)
        .setDurationBg(CPU_CAPTURE_BACKGROUND)
        .setLabelProvider(this::formatCaptureLabel)
        .setLabelColors(ProfilerColors.CPU_DURATION_LABEL_BACKGROUND, Color.BLACK, Color.lightGray, Color.WHITE)
        .setClickHander(traceInfo -> getStage().setAndSelectCapture(traceInfo.getTraceId()))
        .build();
    overlayComponent.addDurationDataRenderer(traceRenderer);
    lineChart.addCustomRenderer(traceRenderer);
    overlay.add(lineChart, new TabularLayout.Constraint(0, 0));
  }

  private void configureAxisPanel(JPanel axisPanel) {
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
  }

  private void configureOverlayPanel(JPanel overlayPanel, OverlayComponent overlay) {
    MouseListener usageListener = new ProfilerTooltipMouseAdapter(myStage, () -> new CpuUsageTooltip(myStage));
    overlay.addMouseListener(usageListener);
    overlayPanel.addMouseListener(usageListener);
    overlayPanel.setOpaque(false);
    overlayPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    overlayPanel.add(overlay, BorderLayout.CENTER);

    // Double-clicking the chart should remove a capture selection if one exists.
    MouseAdapter doubleClick = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && !e.isConsumed()) {
          clearSelection();
        }
      }
    };
    overlay.addMouseListener(doubleClick);
    overlayPanel.addMouseListener(doubleClick);

    // TODO: This needs to be refactored, because probably we don't handle mouse events
    //       properly when components are layered, currently mouse events should happen on the OverlayComponent.
    myTooltipComponent.registerListenersOn(overlay);
    myTooltipComponent.registerListenersOn(overlayPanel);
  }

  private void configureThreadsPanel(JPanel threadsPanel, TabularLayout threadsMonitorPanelLayout) {
    final JScrollPane scrollingThreads = new MyScrollPane();
    scrollingThreads.addMouseWheelListener(new CpuMouseWheelListener(threadsPanel));

    // TODO(b/62447834): Make a decision on how we want to handle thread selection.
    myThreads.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myThreads.setBorder(null);
    myThreads.setCellRenderer(new ThreadCellRenderer(myThreads, myStage.getUpdatableManager()));
    myThreads.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    scrollingThreads.setBorder(null);
    CpuThreadsModel model = myStage.getThreadStates();
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

    myThreads.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myThreads.getSelectedIndex() < 0 && myThreads.getModel().getSize() > 0) {
          myThreads.setSelectedIndex(0);
        }
      }
    });

    scrollingThreads.setBorder(MONITOR_BORDER);
    scrollingThreads.setViewportView(myThreads);

    myTooltipComponent.registerListenersOn(myThreads);
    myThreads.addMouseListener(new ProfilerTooltipMouseAdapter(myStage, () -> new CpuThreadsTooltip(myStage)));
    myThreads.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = myThreads.locationToIndex(e.getPoint());
        if (row != -1) {
          CpuThreadsModel.RangedCpuThread model = myThreads.getModel().getElementAt(row);
          if (myStage.getTooltip() instanceof CpuThreadsTooltip) {
            CpuThreadsTooltip tooltip = (CpuThreadsTooltip)myStage.getTooltip();
            tooltip.setThread(model.getName(), model.getStateSeries());
          }
        }
      }
    });

    // Add AxisComponent only to scrollable section of threads list.
    final AxisComponent timeAxisGuide = new AxisComponent(myStage.getTimeAxisGuide(), AxisComponent.AxisOrientation.BOTTOM);
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

    final JPanel threads = new JPanel(new TabularLayout("*", "*"));
    threads.add(timeAxisGuide, new TabularLayout.Constraint(0, 0));
    threads.add(scrollingThreads, new TabularLayout.Constraint(0, 0));

    final HideablePanel hideablePanel = new HideablePanel.Builder("THREADS", threads)
      .setShowSeparator(false)
      .build();
    hideablePanel.addStateChangedListener((actionEvent) -> {
      getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackToggleCpuThreadsHideablePanel();
      // On expanded set row sizing to initial ratio.
      if (hideablePanel.isExpanded()) {
        threadsMonitorPanelLayout.setRowSizing(THREADS_PANEL_ROW, PanelSpacing.HIDEABLE_PANEL_EXPANDED.toString());
      }
      else {
        // On collapse have monitor panel take any left over space.
        threadsMonitorPanelLayout.setRowSizing(THREADS_PANEL_ROW, PanelSpacing.HIDEABLE_PANEL_COLLAPSED.toString());
      }
    });
    // Clear border set by default on the hideable panel.
    hideablePanel.setBorder(JBUI.Borders.customLine(ProfilerColors.CPU_AXIS_GUIDE_COLOR, 2, 0, 0, 0));
    hideablePanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    myThreads.getModel().addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {

      }

      @Override
      public void intervalRemoved(ListDataEvent e) {

      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        hideablePanel.setTitle(String.format("THREADS (%d)", myThreads.getModel().getSize()));
      }
    });
    threads.setBorder(new JBEmptyBorder(0, 0, 0, 0));
    threadsPanel.add(hideablePanel, new TabularLayout.Constraint(THREADS_PANEL_ROW, 0));
  }

  private void configureLineChart(JPanel lineChartPanel, OverlayComponent overlay) {
    lineChartPanel.setOpaque(false);

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
    lineChart.setTopPadding(Y_AXIS_TOP_MARGIN);
    lineChart.setFillEndGap(true);

    @SuppressWarnings("UseJBColor")
    DurationDataRenderer<CpuTraceInfo> traceRenderer =
      new DurationDataRenderer.Builder<>(getStage().getTraceDurations(), ProfilerColors.CPU_CAPTURE_EVENT)
        .setDurationBg(CPU_CAPTURE_BACKGROUND)
        .setLabelProvider(this::formatCaptureLabel)
        .setLabelColors(ProfilerColors.CPU_DURATION_LABEL_BACKGROUND, Color.BLACK, Color.lightGray, Color.WHITE)
        .setClickHander(traceInfo -> getStage().setAndSelectCapture(traceInfo.getTraceId()))
        .build();

    traceRenderer.addCustomLineConfig(cpuUsage.getCpuSeries(), new LineConfig(ProfilerColors.CPU_USAGE_CAPTURED)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    traceRenderer.addCustomLineConfig(cpuUsage.getOtherCpuSeries(), new LineConfig(ProfilerColors.CPU_OTHER_USAGE_CAPTURED)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    traceRenderer.addCustomLineConfig(cpuUsage.getThreadsCountSeries(), new LineConfig(ProfilerColors.THREADS_COUNT_CAPTURED)
      .setStepped(true).setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));

    overlay.addDurationDataRenderer(traceRenderer);
    lineChart.addCustomRenderer(traceRenderer);

    @SuppressWarnings("UseJBColor")
    DurationDataRenderer<DefaultDurationData> inProgressTraceRenderer =
      new DurationDataRenderer.Builder<>(getStage().getInProgressTraceDuration(), ProfilerColors.CPU_CAPTURE_EVENT)
        .setDurationBg(CPU_CAPTURE_BACKGROUND)
        .setLabelColors(ProfilerColors.CPU_DURATION_LABEL_BACKGROUND, Color.BLACK, Color.lightGray, Color.WHITE)
        .build();

    inProgressTraceRenderer.addCustomLineConfig(cpuUsage.getCpuSeries(), new LineConfig(ProfilerColors.CPU_USAGE_CAPTURED)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    inProgressTraceRenderer.addCustomLineConfig(cpuUsage.getOtherCpuSeries(), new LineConfig(ProfilerColors.CPU_OTHER_USAGE_CAPTURED)
      .setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX));
    inProgressTraceRenderer.addCustomLineConfig(cpuUsage.getThreadsCountSeries(), new LineConfig(ProfilerColors.THREADS_COUNT_CAPTURED)
      .setStepped(true).setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE));

    overlay.addDurationDataRenderer(inProgressTraceRenderer);
    lineChart.addCustomRenderer(inProgressTraceRenderer);
  }

  private void configureLegendPanel(JPanel legendPanel) {
    CpuProfilerStage.CpuStageLegends legends = getStage().getLegends();
    LegendComponent legend = new LegendComponent.Builder(legends).setRightPadding(PROFILER_LEGEND_RIGHT_PADDING).build();
    legend.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);
    legend.configure(legends.getCpuLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.CPU_USAGE_CAPTURED));
    legend.configure(legends.getOthersLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.CPU_OTHER_USAGE_CAPTURED));
    legend.configure(legends.getThreadsLegend(),
                     new LegendConfig(LegendConfig.IconType.DASHED_LINE, ProfilerColors.THREADS_COUNT_CAPTURED));

    final JLabel label = new JLabel(getStage().getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);
    label.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);

    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);
  }

  /**
   * When a running state is selected from the CPU {@link JBList} this function handles
   * finding the proper thread and selecting the thread as well as triggering the feature tracker.
   */
  private void cpuKernelRunningStateSelected(CpuKernelModel cpuModel) {
    int selectedIndex = myCpus.getSelectedIndex();
    if (selectedIndex < 0) {
      myStage.setSelectedThread(CaptureModel.NO_THREAD);
      return;
    }
    CpuKernelModel.CpuState state = cpuModel.getElementAt(selectedIndex);
    Range tooltipRange = myStage.getStudioProfilers().getTimeline().getTooltipRange();
    List<SeriesData<CpuThreadInfo>> process =
      state.getModel().getSeries().get(0).getDataSeries().getDataForXRange(tooltipRange);
    if (process.isEmpty()) {
      return;
    }

    int id = process.get(0).value.getId();
    CpuThreadsModel threadsModel = getStage().getThreadStates();
    for (int i = 0; i < myThreads.getModel().getSize(); i++) {
      CpuThreadsModel.RangedCpuThread thread = threadsModel.getElementAt(i);
      if (id == thread.getThreadId()) {
        myStage.setSelectedThread(thread.getThreadId());
        myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectThread();
        break;
      }
    }
  }

  @VisibleForTesting
  @NotNull
  CpuProfilingConfigurationView getProfilingConfigurationView() {
    return myProfilingConfigurationView;
  }

  private static Logger getLogger() {
    return Logger.getInstance(CpuProfilerStageView.class);
  }

  private void clearSelection() {
    getStage().getStudioProfilers().getTimeline().getSelectionRange().clear();
  }

  /**
   * Installs a context menu on {@link #mySelection}.
   */
  private void installContextMenu() {
    ContextMenuInstaller contextMenuInstaller = getIdeComponents().createContextMenuInstaller();
    // Add the item to trigger a recording
    installRecordMenuItem(contextMenuInstaller);

    // Add the item to export a trace file.
    if (myStage.getStudioProfilers().getIdeServices().getFeatureConfig().isExportCpuTraceEnabled()) {
      installExportTraceMenuItem(contextMenuInstaller);
    }
    installCaptureNavigationMenuItems(contextMenuInstaller);

    // Add the profilers common menu items
    getProfilersView().installCommonMenuItems(mySelection);
  }

  /**
   * Installs both {@link ContextMenuItem} corresponding to the CPU capture navigation feature on {@link #mySelection}.
   */
  private void installCaptureNavigationMenuItems(ContextMenuInstaller contextMenuInstaller) {
    int shortcutModifier = AdtUiUtils.getActionMask() | SHIFT_DOWN_MASK;

    ProfilerAction navigateNext =
      new ProfilerAction.Builder("Next capture")
        .setActionRunnable(() -> myStage.navigateNext())
        .setEnableBooleanSupplier(() -> !myStage.isImportTraceMode() && myStage.getTraceIdsIterator().hasNext())
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, shortcutModifier)).build();

    ProfilerAction navigatePrevious =
      new ProfilerAction.Builder("Previous capture")
        .setActionRunnable(() -> myStage.navigatePrevious())
        .setEnableBooleanSupplier(() -> !myStage.isImportTraceMode() && myStage.getTraceIdsIterator().hasPrevious())
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, shortcutModifier)).build();

    contextMenuInstaller.installGenericContextMenu(mySelection, navigateNext);
    contextMenuInstaller.installGenericContextMenu(mySelection, navigatePrevious);
    contextMenuInstaller.installGenericContextMenu(mySelection, ContextMenuItem.SEPARATOR);
  }

  /**
   * Installs the {@link ContextMenuItem} corresponding to the "Export Trace" feature on {@link #mySelection}.
   */
  private void installExportTraceMenuItem(ContextMenuInstaller contextMenuInstaller) {
    // Call setEnableBooleanSupplier() on ProfilerAction.Builder to make it easier to test.
    ProfilerAction exportTrace = new ProfilerAction.Builder("Export trace...").setIcon(StudioIcons.Common.EXPORT)
                                                                              .setEnableBooleanSupplier(() -> !myStage.isImportTraceMode())
                                                                              .build();
    contextMenuInstaller.installGenericContextMenu(
      mySelection, exportTrace,
      x -> exportTrace.isEnabled() && getTraceIntersectingWithMouseX(x) != null,
      x -> getIdeComponents().createExportDialog().open(
        () -> "Export trace as",
        () -> CpuProfiler.generateCaptureFileName(getTraceIntersectingWithMouseX(x).getProfilerType()),
        () -> "trace",
        file -> getStage().getStudioProfilers().getIdeServices().saveFile(
          file, (output) -> CpuProfiler.saveCaptureToFile(getTraceIntersectingWithMouseX(x).getTraceInfo(), output), null)));
    contextMenuInstaller.installGenericContextMenu(mySelection, ContextMenuItem.SEPARATOR);
  }

  /**
   * Install the {@link ContextMenuItem} corresponding to the Start/Stop recording action on {@link #mySelection}.
   */
  private void installRecordMenuItem(ContextMenuInstaller contextMenuInstaller) {
    ProfilerAction record = new ProfilerAction.Builder(() -> myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING
                                                             ? "Stop recording" : "Record CPU trace")
      .setIcon(() -> myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING
                     ? StudioIcons.Profiler.Toolbar.STOP_RECORDING : StudioIcons.Profiler.Toolbar.RECORD)
      .setEnableBooleanSupplier(() -> !myStage.isImportTraceMode() && (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING
                                                                       || myStage.getCaptureState() == CpuProfilerStage.CaptureState.IDLE))
      .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_R, AdtUiUtils.getActionMask()))
      .setActionRunnable(() -> capture())
      .build();

    contextMenuInstaller.installGenericContextMenu(mySelection, record);
    contextMenuInstaller.installGenericContextMenu(mySelection, ContextMenuItem.SEPARATOR);
  }

  /**
   * Returns the trace ID of a capture that intersects with the mouse X coordinate within {@link #mySelection}.
   */
  private CpuTraceInfo getTraceIntersectingWithMouseX(int mouseXLocation) {
    Range range = getTimeline().getViewRange();
    double pos = mouseXLocation / mySelection.getSize().getWidth() * range.getLength() + range.getMin();
    return getStage().getIntersectingTraceInfo(new Range(pos, pos));
  }

  private void installProfilingInstructions(@NotNull JPanel parent) {
    assert parent.getLayout().getClass() == TabularLayout.class;
    Icon recordIcon = UIUtil.isUnderDarcula()
                      ? IconUtil.darker(StudioIcons.Profiler.Toolbar.RECORD, 3)
                      : IconUtil.brighter(StudioIcons.Profiler.Toolbar.RECORD, 3);
    FontMetrics metrics = SwingUtilities2.getFontMetrics(parent, PROFILING_INSTRUCTIONS_FONT);
    InstructionsPanel panel = new InstructionsPanel.Builder(new TextInstruction(metrics, "Click "),
                                                            new IconInstruction(recordIcon, PROFILING_INSTRUCTIONS_ICON_PADDING, null),
                                                            new TextInstruction(metrics, " to start method profiling"))
      .setEaseOut(getStage().getInstructionsEaseOutModel(), instructionsPanel -> parent.remove(instructionsPanel))
      .setBackgroundCornerRadius(PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER, PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER)
      .build();
    parent.add(panel, new TabularLayout.Constraint(0, 0));
  }

  @Override
  public JComponent getToolbar() {
    // We shouldn't display the CPU toolbar in import trace mode, so we return an empty panel.
    if (myStage.isImportTraceMode()) {
      return new JPanel();
    }
    JPanel panel = new JPanel(new BorderLayout());
    JPanel toolbar = new JPanel(createToolbarLayout());

    toolbar.add(myProfilingConfigurationView.getComponent());
    toolbar.add(Box.createHorizontalStrut(3));
    toolbar.add(myCaptureButton);
    toolbar.add(myCaptureStatus);

    SessionsManager sessions = getStage().getStudioProfilers().getSessionsManager();
    sessions.addDependency(this).onChange(SessionAspect.SELECTED_SESSION, () -> myCaptureButton.setEnabled(shouldEnableCaptureButton()));
    myCaptureButton.setEnabled(shouldEnableCaptureButton());

    panel.add(toolbar, BorderLayout.WEST);
    return panel;
  }

  /**
   * Should enable the capture button for recording and stopping only when session is alive and no API-initiated tracing is
   * in progress.
   */
  private boolean shouldEnableCaptureButton() {
    return myStage.getStudioProfilers().getSessionsManager().isSessionAlive() && !myStage.isApiInitiatedTracingInProgress();
  }

  @Override
  public boolean navigationControllersEnabled() {
    return !myStage.isImportTraceMode();
  }

  private String formatCaptureLabel(CpuTraceInfo info) {
    Range range = getStage().getStudioProfilers().getTimeline().getDataRange();
    long min = (long)(info.getRange().getMin() - range.getMin());
    long max = (long)(info.getRange().getMax() - range.getMin());
    return String.format("%s - %s", TimeAxisFormatter.DEFAULT.getClockFormattedString(min),
                         TimeAxisFormatter.DEFAULT.getClockFormattedString(max));
  }

  private void updateCaptureState() {
    myCaptureViewLoading.stopLoading();
    switch (myStage.getCaptureState()) {
      case IDLE:
        myCaptureButton.setEnabled(true);
        myCaptureStatus.setText("");
        myCaptureButton.setToolTipText("Record a method trace");
        myCaptureButton.setIcon(StudioIcons.Profiler.Toolbar.RECORD);
        myProfilingConfigurationView.getComponent().setEnabled(true);
        // TODO: replace with loading icon
        myCaptureButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.RECORD));
        break;
      case CAPTURING:
        if (getStage().getCaptureInitiationType().equals(TraceInitiationType.INITIATED_BY_API)) {
          myCaptureButton.setEnabled(false);
        }
        else {
          myCaptureButton.setEnabled(true);
        }
        myCaptureStatus.setText("");
        myCaptureButton.setToolTipText("Stop recording");
        myCaptureButton.setIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING);
        myProfilingConfigurationView.getComponent().setEnabled(false);
        // TODO: replace with loading icon
        myCaptureButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.STOP_RECORDING));
        break;
      case PARSING:
        myCaptureViewLoading.startLoading();
        mySplitter.setSecondComponent(myCaptureViewLoading.getComponent());
        break;
      case PARSING_FAILURE:
        mySplitter.setSecondComponent(null);
        break;
      case STARTING:
        myCaptureButton.setEnabled(false);
        myCaptureStatus.setText("Starting record...");
        myCaptureButton.setToolTipText("");
        myProfilingConfigurationView.getComponent().setEnabled(false);
        break;
      case START_FAILURE:
        mySplitter.setSecondComponent(null);
        break;
      case STOPPING:
        myCaptureButton.setEnabled(false);
        myCaptureStatus.setText("Stopping record...");
        myCaptureButton.setToolTipText("");
        myProfilingConfigurationView.getComponent().setEnabled(false);
        break;
      case STOP_FAILURE:
        mySplitter.setSecondComponent(null);
        break;
    }
  }

  private void updateCaptureSelection() {
    CpuCapture capture = myStage.getCapture();
    if (capture == null) {
      // If the capture is still being parsed, the splitter second component should be myCaptureViewLoading
      if (myStage.getCaptureState() != CpuProfilerStage.CaptureState.PARSING) {
        if (myStage.isSelectionFailure()) {
          mySplitter.setSecondComponent(myHelpTipPanel);
        }
        else {
          mySplitter.setSecondComponent(null);
        }
      }
      // Clear the selection if it exists
      clearSelection();
      myCaptureView = null;
    }
    else if ((myStage.getCaptureState() == CpuProfilerStage.CaptureState.IDLE)
             || (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING)) {
      // Capture has finished parsing. Create a CpuCaptureView to display it.
      myCaptureView = new CpuCaptureView(this);
      mySplitter.setSecondComponent(myCaptureView.getComponent());
      ensureCaptureInViewRange();
    }
  }

  private void updateCaptureElapsedTime() {
    if (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING) {
      long elapsedTimeUs = myStage.getCaptureElapsedTimeUs();
      String text =
        String.format("Recording - %s", TimeAxisFormatter.DEFAULT.getClockFormattedString(elapsedTimeUs));
      myCaptureStatus.setText(text);
    }
  }

  private void capture() {
    if (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING) {
      myStage.stopCapturing();
    }
    else {
      myStage.startCapturing();
    }
  }

  private void updateThreadSelection() {
    if (myStage.getSelectedThread() == CaptureModel.NO_THREAD) {
      myThreads.clearSelection();
      return;
    }

    // Select the thread which has its tree displayed in capture panel in the threads list
    for (int i = 0; i < myThreads.getModel().getSize(); i++) {
      CpuThreadsModel.RangedCpuThread thread = myThreads.getModel().getElementAt(i);
      if (myStage.getSelectedThread() == thread.getThreadId()) {
        myThreads.setSelectedIndex(i);
        break;
      }
    }

    if (myStage.getSelectedThread() != CaptureModel.NO_THREAD && myStage.isSelectionFailure()) {
      // If the help tip info panel is already showing and the user clears thread selection, we'll leave the panel showing.
      mySplitter.setSecondComponent(myHelpTipPanel);
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

  /**
   * Class to help dispatch mouse events that would otherwise be consumed by the JScrollPane.
   * Refer to implementation in {@link javax.swing.plaf.basic.BasicScrollPaneUI.Handler#mouseWheelMoved}
   * Note: We cannot override the {@link JScrollPane#processMouseEvent} method as dispatching an event
   * to the view will result in a loop since our controls do not consume events.
   */
  private static class CpuMouseWheelListener implements MouseWheelListener {
    @NotNull
    private final JComponent myDispatchComponent;

    public CpuMouseWheelListener(@NotNull JComponent dispatchComponent) {
      myDispatchComponent = dispatchComponent;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      // If we have the modifier keys down then pass the event on to the parent control. Otherwise
      // the JScrollPane will consume the event.
      boolean isMenuKeyDown = AdtUiUtils.isActionKeyDown(e);
      // The shift key modifier is used when making the determination if we are panning vs scrolling vertically when the mouse
      // wheel is triggered.
      boolean isShiftKeyDown = e.isShiftDown();
      if (isMenuKeyDown || isShiftKeyDown) {
        myDispatchComponent.dispatchEvent(e);
      }
    }
  }
}