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

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_VERTICAL_BORDERS;
import static com.android.tools.profilers.ProfilerLayout.PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER;

import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.stdui.StreamingScrollbar;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.DismissibleMessage;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.ProfilerTooltipMouseAdapter;
import com.android.tools.profilers.RecordingOption;
import com.android.tools.profilers.RecordingOptionsView;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.SupportLevel;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.event.LifecycleTooltip;
import com.android.tools.profilers.event.LifecycleTooltipView;
import com.android.tools.profilers.event.UserEventTooltip;
import com.android.tools.profilers.event.UserEventTooltipView;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.sessions.SessionsManager;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.UIUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.event.MouseListener;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.MutableComboBoxModel;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

public class CpuProfilerStageView extends StageView<CpuProfilerStage> {
  private enum PanelSizing {
    /**
     * Sizing string for the CPU graph.
     */
    MONITOR("140px", 0),

    /**
     * Sizing string for the threads / kernel view.
     */
    DETAILS("*", 1),

    /**
     * Sizing string for the threads portion of the details view.
     */
    THREADS("*", 0);

    @NotNull private final String myRowRule;
    private final int myRow;

    PanelSizing(@NotNull String rowRule, int row) {
      myRowRule = rowRule;
      myRow = row;
    }

    @NotNull
    public String getRowRule() {
      return myRowRule;
    }

    public int getRow() {
      return myRow;
    }
  }

  /**
   * Default ratio of splitter. The splitter ratio adjust the first elements size relative to the bottom elements size.
   * A ratio of 1 means only the first element is shown, while a ratio of 0 means only the bottom element is shown.
   */
  private static final float SPLITTER_DEFAULT_RATIO = 0.2f;

  private static final String SHOW_PROFILEABLE_MESSAGE = "profileable.cpu.message";

  private final CpuProfilerStage myStage;

  @NotNull private final CpuThreadsView myThreads;

  @NotNull private final RecordingOptionsView myRecordingOptionsView;

  @NotNull private final RangeTooltipComponent myTooltipComponent;

  public CpuProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull CpuProfilerStage stage) {
    super(profilersView, stage);
    myStage = stage;
    myThreads = new CpuThreadsView(myStage);
    myTooltipComponent = new RangeTooltipComponent(getStage().getTimeline(),
                                                   getTooltipPanel(),
                                                   getProfilersView().getComponent(),
                                                   this::shouldShowTooltipSeekComponent);

    getTooltipBinder().bind(CpuProfilerStageCpuUsageTooltip.class, CpuProfilerStageCpuUsageTooltipView::new);
    getTooltipBinder().bind(CpuThreadsTooltip.class, (stageView, tooltip) -> new CpuThreadsTooltipView(stageView.getComponent(), tooltip));
    getTooltipBinder().bind(LifecycleTooltip.class, (stageView, tooltip) -> new LifecycleTooltipView(stageView.getComponent(), tooltip));
    getTooltipBinder().bind(UserEventTooltip.class, (stageView, tooltip) -> new UserEventTooltipView(stageView.getComponent(), tooltip));
    getTooltipPanel().setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

    CpuUsageView usageView = new CpuUsageView(myStage);
    myTooltipComponent.registerListenersOn(usageView);
    MouseListener listener = new ProfilerTooltipMouseAdapter(myStage, () -> new CpuProfilerStageCpuUsageTooltip(myStage));
    usageView.addMouseListener(listener);

    // "Fit" for the event profiler, "*" for everything else.
    final JPanel details = new JPanel(new TabularLayout("*", "Fit-,*"));
    details.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    // Order matters as such our tooltip component should be first so it draws on top of all elements.
    details.add(myTooltipComponent, new TabularLayout.Constraint(0, 0, 3, 1));

    if (stage.getStudioProfilers().getSelectedSessionSupportLevel() == SupportLevel.DEBUGGABLE) {
      final EventMonitorView eventsView = new EventMonitorView(profilersView, stage.getEventMonitor());
      eventsView.registerTooltip(myTooltipComponent, getStage());
      details.add(eventsView.getComponent(), new TabularLayout.Constraint(0, 0));
    }

    TabularLayout mainLayout = new TabularLayout("*");
    mainLayout.setRowSizing(PanelSizing.MONITOR.getRow(), PanelSizing.MONITOR.getRowRule());
    mainLayout.setRowSizing(PanelSizing.DETAILS.getRow(), PanelSizing.DETAILS.getRowRule());
    final JPanel mainPanel = new JBPanel<>(mainLayout);
    mainPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);

    mainPanel.add(usageView, new TabularLayout.Constraint(PanelSizing.MONITOR.getRow(), 0));
    mainPanel.add(createCpuStatePanel(), new TabularLayout.Constraint(PanelSizing.DETAILS.getRow(), 0));

    // Panel that represents all of L2
    details.add(mainPanel, new TabularLayout.Constraint(1, 0));
    details.add(buildTimeAxis(myStage.getStudioProfilers()), new TabularLayout.Constraint(3, 0));
    details.add(new StreamingScrollbar(myStage.getTimeline(), details), new TabularLayout.Constraint(4, 0));

    // The first component in the splitter is the recording options, the 2nd component is the L2 components.
    myRecordingOptionsView = new RecordingOptionsView(getStage().getRecordingModel(), this::editConfigurations);

    JBSplitter splitter = new JBSplitter(false);
    splitter.setFirstComponent(myRecordingOptionsView);
    splitter.setSecondComponent(details);
    splitter.getDivider().setBorder(DEFAULT_VERTICAL_BORDERS);
    splitter.setProportion(SPLITTER_DEFAULT_RATIO);
    getComponent().add(splitter, BorderLayout.CENTER);

    CpuProfilerContextMenuInstaller.install(myStage, getIdeComponents(), usageView, getComponent());
    // Add the profilers common menu items
    getProfilersView().installCommonMenuItems(usageView);

    SessionsManager sessions = getStage().getStudioProfilers().getSessionsManager();
    sessions.addDependency(this).onChange(SessionAspect.SELECTED_SESSION, this::sessionChanged);
    sessions.addDependency(this).onChange(SessionAspect.PROFILING_SESSION, this::sessionChanged);

    if (!getStage().hasUserUsedCpuCapture()) {
      installProfilingInstructions(usageView);
    }
    sessionChanged();
  }

  @Override
  public JPanel getToolbar() {
    return getStage().getStudioProfilers().getSelectedSessionSupportLevel() == SupportLevel.PROFILEABLE
           ? DismissibleMessage.of(getStage().getStudioProfilers(),
                                   SHOW_PROFILEABLE_MESSAGE,
                                   "Some features are disabled for profileable processes.",
                                   SupportLevel.DOC_LINK)
           : new JPanel();
  }

  private void sessionChanged() {
    boolean sessionAlive = SessionsManager.isSessionAlive(getStage().getStudioProfilers().getSessionsManager().getSelectedSession());
    myRecordingOptionsView.setEnabled(sessionAlive);
  }

  private Unit editConfigurations(MutableComboBoxModel<RecordingOption> model) {
    Consumer<ProfilingConfiguration> dialogCallback = (configuration) -> {
      // Update the config list to pick up any potential changes.
      myStage.getProfilerConfigModel().updateProfilingConfigurations();
      myStage.refreshRecordingConfigurations();
    };
    Common.Device selectedDevice = myStage.getStudioProfilers().getDevice();
    int deviceFeatureLevel = selectedDevice != null ? selectedDevice.getFeatureLevel() : 0;
    getIdeComponents().openCpuProfilingConfigurationsDialog(myStage.getProfilerConfigModel(), deviceFeatureLevel, dialogCallback);
    myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackOpenProfilingConfigDialog();
    return Unit.INSTANCE;
  }

  @NotNull
  private JPanel createCpuStatePanel() {
    TabularLayout cpuStateLayout = new TabularLayout("*");
    JPanel cpuStatePanel = new JBPanel<>(cpuStateLayout);

    cpuStatePanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    cpuStateLayout.setRowSizing(PanelSizing.THREADS.getRow(), PanelSizing.THREADS.getRowRule());

    //region CpuThreadsView
    myTooltipComponent.registerListenersOn(myThreads.getComponent());
    cpuStatePanel.add(myThreads.getComponent(), new TabularLayout.Constraint(PanelSizing.THREADS.getRow(), 0));
    //endregion

    return cpuStatePanel;
  }

  private void installProfilingInstructions(@NotNull JPanel parent) {
    assert parent.getLayout().getClass() == TabularLayout.class;
    FontMetrics metrics = UIUtilities.getFontMetrics(parent, ProfilerFonts.H2_FONT);
    InstructionsPanel panel =
      new InstructionsPanel.Builder(new TextInstruction(metrics, "Click Record to start capturing CPU activity"))
        .setEaseOut(myStage.getInstructionsEaseOutModel(), parent::remove)
        .setBackgroundCornerRadius(PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER, PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER)
        .build();
    // Add the instructions panel as the first component of |parent|, so that |parent| renders the instructions on top of other components.
    parent.add(panel, new TabularLayout.Constraint(0, 0), 0);
  }

  /**
   * @return true if the blue seek component from {@link RangeTooltipComponent} should be visible.
   * See {@code RangeTooltipComponent#myShowSeekComponent}
   */
  @VisibleForTesting
  boolean shouldShowTooltipSeekComponent() {
    return myStage.getTooltip() instanceof CpuProfilerStageCpuUsageTooltip;
  }
}