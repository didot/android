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

package com.android.tools.idea.monitor.ui.visual;

import com.android.tools.adtui.*;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.adtui.visualtests.VisualTest;
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.TimeAxisSegment;
import com.android.tools.idea.monitor.ui.cpu.view.CpuUsageSegment;
import com.android.tools.idea.monitor.ui.memory.view.MemorySegment;
import com.android.tools.idea.monitor.ui.network.view.NetworkSegment;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.EventDispatcher;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProfilerOverviewVisualTest extends VisualTest {

  // Segment dimensions.
  private static final int MONITOR_MAX_HEIGHT = Short.MAX_VALUE;
  private static final int MONITOR_PREFERRED_HEIGHT = 200;
  private static final int TIME_AXIS_HEIGHT = 20;

  // TODO: Replace with actual icons.
  private static final Icon[] ICONS = {
    AndroidIcons.ToolWindows.Warning,
    AndroidIcons.ToolWindows.Warning,
    AndroidIcons.ToolWindows.Warning
  };

  private SeriesDataStore mDataStore;

  @NotNull
  private Range mTimeCurrentRange;

  @NotNull
  private Range mTimeGlobalRange;

  @NotNull
  private Range mTimeSelectionRange;

  @NotNull
  private AxisComponent mTimeAxis;

  @NotNull
  private SelectionComponent mSelection;

  @NotNull
  private RangeScrollbar mScrollbar;

  private JPanel mSegmentsContainer;

  private AccordionLayout mLayout;

  private EventDispatcher<ProfilerEventListener> mEventDispatcher;

  private Component mResetProfilersButton;

  @Override
  protected void initialize() {
    mEventDispatcher = EventDispatcher.create(ProfilerEventListener.class);
    mDataStore = new VisualTestSeriesDataStore();
    super.initialize();
  }

  @Override
  protected void reset() {
    if (mDataStore != null) {
      mDataStore.reset();
    }

    super.reset();
  }

  @Override
  protected List<Animatable> createComponentsList() {
    long startTimeUs = mDataStore.getLatestTimeUs();
    mTimeCurrentRange = new Range();
    mTimeGlobalRange = new Range(startTimeUs - RangeScrollbar.DEFAULT_VIEW_LENGTH_US, startTimeUs);
    mTimeSelectionRange = new Range();

    mScrollbar = new RangeScrollbar(mTimeGlobalRange, mTimeCurrentRange);

    // add horizontal time axis
    AxisComponent.Builder builder = new AxisComponent.Builder(mTimeCurrentRange, TimeAxisFormatter.DEFAULT, AxisComponent.AxisOrientation.BOTTOM)
      .setGlobalRange(mTimeGlobalRange).setOffset(startTimeUs);
    mTimeAxis = builder.build();

    mSegmentsContainer = new JBPanel();
    mLayout = new AccordionLayout(mSegmentsContainer, AccordionLayout.Orientation.VERTICAL);
    // TODO animation is disabled at the moment as it causes visual artifacts on the SelectionComponent due to overlapping painting regions.
    mLayout.setLerpFraction(1f);
    mSegmentsContainer.setLayout(mLayout);

    mSelection = new SelectionComponent(mTimeSelectionRange,
                                        mTimeCurrentRange);

    List<Animatable> componentsList = new ArrayList<>();
    componentsList.add(mLayout);
    // Get latest data time from the data store.
    componentsList.add(frameLength -> mTimeGlobalRange.setMax(mDataStore.getLatestTimeUs()));
    componentsList.add(mSelection);            // Update selection range immediate.
    componentsList.add(mScrollbar);            // Update current range immediate.
    componentsList.add(mTimeAxis);             // Read ranges.
    return componentsList;
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Arrays.asList(mSelection, mTimeAxis);
  }

  @Override
  public String getName() {
    return "Overview";
  }

  @Override
  public void populateUi(JPanel panel) {
    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    JBPanel gridBagPanel = new JBPanel();
    gridBagPanel.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR, 1));
    gridBagPanel.setLayout(gbl);

    // TODO create some controls.
    final JPanel controls = VisualTest.createControlledPane(panel, gridBagPanel);

    // Add Mock Toolbar
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.gridwidth = 4;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gridBagPanel.add(createToolbarPanel(), gbc);

    // Add Selection Overlay
    // TODO define sizes for x columns 0 and 1
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridy = 1;
    gbc.gridx = 2;
    gbc.gridwidth = 1;
    gbc.weightx = 1;
    gbc.weighty = 1;
    gridBagPanel.add(mSelection, gbc);

    // Add Accordion Control
    gbc.gridx = 0;
    gbc.gridwidth = 4;
    gbc.gridy = 1;
    gbc.weighty = 0;
    gbc.weightx = 0;
    gridBagPanel.add(mSegmentsContainer, gbc);

    // Add Scrollbar
    gbc.gridy = 2;
    gridBagPanel.add(mScrollbar, gbc);

    // TODO: fix me, currently it's throwing IllegalArgumentException, the reason is:
    // EventSegment is using {@code DeviceProfilerService} which doesn't exist in visual test concept.
    // Mock event segment
    //BaseSegment eventSegment = new EventSegment(mTimeCurrentRange, mDataStore, ICONS, mEventDispatcher);
    //setupAndRegisterSegment(eventSegment, EVENT_MIN_HEIGHT, MONITOR_PREFERRED_HEIGHT, MONITOR_MAX_HEIGHT);

    // Mock monitor segments
    BaseSegment networkSegment = new NetworkSegment(mTimeCurrentRange, mDataStore, mEventDispatcher);
    setupAndRegisterSegment(networkSegment, 0, MONITOR_PREFERRED_HEIGHT, MONITOR_MAX_HEIGHT);

    BaseSegment memorySegment = new MemorySegment(mTimeCurrentRange, mDataStore, mEventDispatcher);
    setupAndRegisterSegment(memorySegment, 0, MONITOR_PREFERRED_HEIGHT, MONITOR_MAX_HEIGHT);

    BaseSegment cpuSegment = new CpuUsageSegment(mTimeCurrentRange, mDataStore, mEventDispatcher);
    setupAndRegisterSegment(cpuSegment, 0, MONITOR_PREFERRED_HEIGHT, MONITOR_MAX_HEIGHT);

    // Timeline segment
    BaseSegment timeSegment = new TimeAxisSegment(mTimeCurrentRange, mTimeAxis, mEventDispatcher);
    setupAndRegisterSegment(timeSegment, TIME_AXIS_HEIGHT, TIME_AXIS_HEIGHT, TIME_AXIS_HEIGHT);

    // Add left spacer
    Dimension leftSpacer = new Dimension(BaseSegment.getSpacerWidth() + networkSegment.getLabelColumnWidth(), 0);
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.gridwidth = 2;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gridBagPanel.add(new Box.Filler(leftSpacer, leftSpacer, leftSpacer), gbc);

    // Add right spacer
    Dimension rightSpacerWidth = new Dimension(BaseSegment.getSpacerWidth(), 0);
    Box.Filler rightSpacer = new Box.Filler(rightSpacerWidth, rightSpacerWidth, rightSpacerWidth);
    gbc.gridy = 0;
    gbc.gridx = 3;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gridBagPanel.add(rightSpacer, gbc);
    rightSpacer.setVisible(false);  // hide right space in L1 by default.

    // Resize the SelectionComponent based on whether we are in L1 or L2 view.
    // TODO construct/destroyed Level3 segment/elements as we expand/collapse segments
    mEventDispatcher.addListener(new ProfilerEventListener() {
      @Override
      public void profilerExpanded(@NotNull BaseProfilerUiManager.ProfilerType profilerType) {
        switch (profilerType) {
          case NETWORK:
            maximizeSegment(networkSegment);
            break;
          case CPU:
            maximizeSegment(cpuSegment);
            break;
          case MEMORY:
            maximizeSegment(memorySegment);
            break;
        }

        rightSpacer.setVisible(true);
        mResetProfilersButton.setEnabled(true);
      }

      @Override
      public void profilersReset() {
        // Sets all the components back to their preferred states.
        mLayout.resetComponents();
        rightSpacer.setVisible(false);
        mResetProfilersButton.setEnabled(false);
        backProfilersToL1();
      }

      private void maximizeSegment(BaseSegment segment) {
        mLayout.setState(segment, AccordionLayout.AccordionState.MAXIMIZE);
        segment.toggleView(true);
      }

      private void backProfilersToL1() {
        networkSegment.toggleView(false);
        cpuSegment.toggleView(false);
        memorySegment.toggleView(false);
      }
    });
  }

  private JComponent createToolbarPanel() {
    JBPanel panel = new JBPanel();
    BoxLayout layout = new BoxLayout(panel, BoxLayout.X_AXIS);
    panel.setLayout(layout);
    panel.setBorder(BorderFactory.createLineBorder(Color.GRAY));

    JComboBox<String> deviceCb = new JComboBox<>(new String[]{"Device1", "Device2"});
    deviceCb.addActionListener(e -> getChoreographer().reset());
    JComboBox<String> processCb = new JComboBox<>(new String[]{"Process1", "Process2"});
    processCb.addActionListener(e -> getChoreographer().reset());

    mResetProfilersButton = VisualTest.createButton("Back to L1", e -> mEventDispatcher.getMulticaster().profilersReset());
    mResetProfilersButton.setEnabled(false);

    panel.add(mResetProfilersButton);
    panel.add(deviceCb);
    panel.add(processCb);
    return panel;
  }

  private void setupAndRegisterSegment(@NotNull BaseSegment segment, int minHeight, int preferredHeight, int maxHeight) {
    segment.setMinimumSize(new Dimension(0, minHeight));
    segment.setPreferredSize(new Dimension(0, preferredHeight));
    segment.setMaximumSize(new Dimension(0, maxHeight));
    segment.setBorder(BorderFactory.createLineBorder(Color.GRAY));

    List<Animatable> segmentAnimatables = new ArrayList<>();
    segment.createComponentsList(segmentAnimatables);
    addToChoreographer(segmentAnimatables);

    segment.initializeComponents();
    mSegmentsContainer.add(segment);
  }
}