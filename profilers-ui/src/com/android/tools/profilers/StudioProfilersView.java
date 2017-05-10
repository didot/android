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
package com.android.tools.profilers;

import com.android.tools.adtui.flat.FlatButton;
import com.android.tools.adtui.flat.FlatComboBox;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.flat.FlatToggleButton;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuProfilerStageView;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.memory.MemoryProfilerStageView;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.android.tools.profilers.network.NetworkProfilerStageView;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.function.BiFunction;

public class StudioProfilersView extends AspectObserver {
  private final StudioProfilers myProfiler;
  private final ViewBinder<StudioProfilersView, Stage, StageView> myBinder;
  private StageView myStageView;
  private BorderLayout myLayout;
  private JPanel myComponent;
  private JPanel myStageToolbar;
  private JPanel myMonitoringToolbar;
  private JPanel myCommonToolbar;
  private AbstractButton myGoLive;

  @NotNull
  private final IdeProfilerComponents myIdeProfilerComponents;

  public StudioProfilersView(@NotNull StudioProfilers profiler, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myProfiler = profiler;
    myIdeProfilerComponents = ideProfilerComponents;
    myStageView = null;
    initializeUi();

    myBinder = new ViewBinder<>();
    myBinder.bind(StudioMonitorStage.class, StudioMonitorStageView::new);
    myBinder.bind(CpuProfilerStage.class, CpuProfilerStageView::new);
    myBinder.bind(MemoryProfilerStage.class, MemoryProfilerStageView::new);
    myBinder.bind(NetworkProfilerStage.class, NetworkProfilerStageView::new);
    myBinder.bind(NullMonitorStage.class, NullMonitorStageView::new);

    myProfiler.addDependency(this).onChange(ProfilerAspect.STAGE, this::updateStageView);
    updateStageView();
  }

  @VisibleForTesting
  public <S extends Stage, T extends StageView> void bind(@NotNull Class<S> clazz,
                                                          @NotNull BiFunction<StudioProfilersView, S, T> constructor) {
    myBinder.bind(clazz, constructor);
  }

  @VisibleForTesting
  public StageView getStageView() {
    return myStageView;
  }

  private void initializeUi() {
    myLayout = new BorderLayout();
    myComponent = new JPanel(myLayout);

    JComboBox<Profiler.Device> deviceCombo = new ComboBox<>();
    JComboBoxView devices = new JComboBoxView<>(deviceCombo, myProfiler, ProfilerAspect.DEVICES,
                                                myProfiler::getDevices,
                                                myProfiler::getDevice,
                                                device -> {
                                                  myProfiler.setDevice(device);
                                                  myProfiler.getIdeServices().getFeatureTracker().trackChangeDevice();
                                                });
    devices.bind();
    deviceCombo.setRenderer(new DeviceComboBoxRenderer());
    deviceCombo.setFont(deviceCombo.getFont().deriveFont(12.0f));

    JComboBox<Profiler.Process> processCombo = new ComboBox<>();
    JComboBoxView processes = new JComboBoxView<>(processCombo, myProfiler, ProfilerAspect.PROCESSES,
                                                  myProfiler::getProcesses,
                                                  myProfiler::getProcess,
                                                  process -> {
                                                    myProfiler.setProcess(process);
                                                    myProfiler.getIdeServices().getFeatureTracker().trackChangeProcess();
                                                  });
    processes.bind();
    processCombo.setRenderer(new ProcessComboBoxRenderer());
    processCombo.setFont(processCombo.getFont().deriveFont(12.0f));

    JPanel toolbar = new JPanel(new BorderLayout());
    JPanel leftToolbar = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));

    toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ProfilerColors.MONITOR_BORDER));
    toolbar.setPreferredSize(new Dimension(15, 30));

    myMonitoringToolbar = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 2));
    myMonitoringToolbar.add(deviceCombo);
    myMonitoringToolbar.add(processCombo);

    myCommonToolbar = new JPanel(ProfilerLayout.TOOLBAR_LAYOUT);
    JButton button = new FlatButton(ProfilerIcons.BACK_ARROW);
    button.addActionListener(action -> {
      myProfiler.setMonitoringStage();
      myProfiler.getIdeServices().getFeatureTracker().trackGoBack();
    });
    myCommonToolbar.add(button);
    myCommonToolbar.add(new FlatSeparator());

    JComboBox<Class<? extends Stage>> stageCombo = new FlatComboBox<>();
    JComboBoxView stages = new JComboBoxView<>(stageCombo, myProfiler, ProfilerAspect.STAGE,
                                               myProfiler::getDirectStages,
                                               myProfiler::getStageClass,
                                               stage -> {
                                                 // Track first, so current stage is sent with the event
                                                 myProfiler.getIdeServices().getFeatureTracker().trackSelectMonitor();
                                                 myProfiler.setNewStage(stage);
                                               });
    stageCombo.setRenderer(new StageComboBoxRenderer());
    stages.bind();
    myCommonToolbar.add(stageCombo);
    myCommonToolbar.add(new FlatSeparator());

    leftToolbar.add(myMonitoringToolbar);
    leftToolbar.add(myCommonToolbar);
    toolbar.add(leftToolbar, BorderLayout.WEST);

    // Changing from default to add additional padding.
    JPanel rightToolbar = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 2));
    toolbar.add(rightToolbar, BorderLayout.EAST);

    ProfilerTimeline timeline = myProfiler.getTimeline();
    FlatButton zoomOut = new FlatButton(ProfilerIcons.ZOOM_OUT);
    zoomOut.setDisabledIcon(IconLoader.getDisabledIcon(ProfilerIcons.ZOOM_OUT));
    zoomOut.addActionListener(event -> {
      timeline.zoomOut();
      myProfiler.getIdeServices().getFeatureTracker().trackZoomOut();
    });
    zoomOut.setToolTipText("Zoom out");
    rightToolbar.add(zoomOut);

    FlatButton zoomIn = new FlatButton(ProfilerIcons.ZOOM_IN);
    zoomIn.setDisabledIcon(IconLoader.getDisabledIcon(ProfilerIcons.ZOOM_IN));
    zoomIn.addActionListener(event -> {
      timeline.zoomIn();
      myProfiler.getIdeServices().getFeatureTracker().trackZoomIn();
    });
    zoomIn.setToolTipText("Zoom in");
    rightToolbar.add(zoomIn);

    FlatButton resetZoom = new FlatButton(ProfilerIcons.RESET_ZOOM);
    resetZoom.setDisabledIcon(IconLoader.getDisabledIcon(ProfilerIcons.RESET_ZOOM));
    resetZoom.addActionListener(event -> {
      timeline.resetZoom();
      myProfiler.getIdeServices().getFeatureTracker().trackResetZoom();
    });
    resetZoom.setToolTipText("Reset zoom");
    rightToolbar.add(resetZoom);
    rightToolbar.add(new FlatSeparator());

    myGoLive = new FlatToggleButton("Live", ProfilerIcons.GOTO_LIVE);
    myGoLive.setDisabledIcon(IconLoader.getDisabledIcon(ProfilerIcons.GOTO_LIVE));
    myGoLive.setToolTipText("See realtime profiler data");
    myGoLive.addActionListener(event -> {
      timeline.toggleStreaming();
      myProfiler.getIdeServices().getFeatureTracker().trackToggleStreaming();
    });
    timeline.addDependency(this).onChange(ProfilerTimeline.Aspect.STREAMING, this::updateStreaming);

    myGoLive.setHorizontalTextPosition(SwingConstants.LEFT);
    rightToolbar.add(myGoLive);

    Runnable toggleToolButtons = () -> {
      zoomOut.setEnabled(myProfiler.isProcessAlive());
      zoomIn.setEnabled(myProfiler.isProcessAlive());
      resetZoom.setEnabled(myProfiler.isProcessAlive());
      myGoLive.setEnabled(myProfiler.isProcessAlive());
    };
    myProfiler.addDependency(this).onChange(ProfilerAspect.PROCESSES, toggleToolButtons);
    toggleToolButtons.run();

    myStageToolbar = new JPanel(new BorderLayout());
    toolbar.add(myStageToolbar, BorderLayout.CENTER);

    myComponent.add(toolbar, BorderLayout.NORTH);

    updateStreaming();
  }

  private void updateStreaming() {
    myGoLive.setSelected(myProfiler.getTimeline().isStreaming());
  }

  private void updateStageView() {
    Stage stage = myProfiler.getStage();
    if (myStageView != null && myStageView.getStage() == stage) {
      return;
    }

    myStageView = myBinder.build(this, stage);
    Component prev = myLayout.getLayoutComponent(BorderLayout.CENTER);
    if (prev != null) {
      myComponent.remove(prev);
    }
    myComponent.add(myStageView.getComponent(), BorderLayout.CENTER);
    myComponent.revalidate();

    myStageToolbar.removeAll();
    myStageToolbar.add(myStageView.getToolbar(), BorderLayout.CENTER);
    myStageToolbar.revalidate();

    boolean topLevel = myStageView == null || myStageView.needsProcessSelection();
    myMonitoringToolbar.setVisible(topLevel);
    myCommonToolbar.setVisible(!topLevel);
  }

  public JPanel getComponent() {
    return myComponent;
  }

  @VisibleForTesting
  public static class DeviceComboBoxRenderer extends ColoredListCellRenderer<Profiler.Device> {

    @NotNull
    private final String myEmptyText = "No connected devices";

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Profiler.Device value, int index,
                                         boolean selected, boolean hasFocus) {
      if (value != null) {
        renderDeviceName(value);
      }
      else {
        append(getEmptyText(), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }

    public void renderDeviceName(@NotNull Profiler.Device d) {
      // TODO: Share code between here and DeviceRenderer#renderDeviceName
      String manufacturer = d.getManufacturer();
      String model = d.getModel();
      String serial = d.getSerial();
      String suffix = String.format("-%s", serial);
      if (model.endsWith(suffix)) {
        model = model.substring(0, model.length() - suffix.length());
      }
      if (!StringUtil.isEmpty(manufacturer)) {
        append(String.format("%s ", manufacturer), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
      append(model, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      append(String.format(" (%1$s)", serial), SimpleTextAttributes.GRAY_ATTRIBUTES);

      Profiler.Device.State state = d.getState();
      if (state != Profiler.Device.State.ONLINE && state != Profiler.Device.State.UNSPECIFIED) {
        append(String.format(" [%s]", state), SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
      }
    }

    @NotNull
    @VisibleForTesting
    public String getEmptyText() {
      return myEmptyText;
    }
  }

  @VisibleForTesting
  public static class ProcessComboBoxRenderer extends ColoredListCellRenderer<Profiler.Process> {

    @NotNull
    private final String myEmptyText = "No debuggable processes";

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Profiler.Process value, int index,
                                         boolean selected, boolean hasFocus) {
      if (value != null) {
        renderProcessName(value);
      }
      else {
        append(getEmptyText(), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }

    private void renderProcessName(@NotNull Profiler.Process process) {
      // TODO: Share code between here and ClientCellRenderer#renderClient
      String name = process.getName();
      // Highlight the last part of the process name.
      int index = name.lastIndexOf('.');
      append(name.substring(0, index + 1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append(name.substring(index + 1), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

      append(String.format(" (%1$d)", process.getPid()), SimpleTextAttributes.GRAY_ATTRIBUTES);

      if (process.getState() != Profiler.Process.State.ALIVE) {
        append(" [DEAD]", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
      }
    }

    @NotNull
    @VisibleForTesting
    public String getEmptyText() {
      return myEmptyText;
    }
  }

  @VisibleForTesting
  public static class StageComboBoxRenderer extends ColoredListCellRenderer<Class> {

    private static ImmutableMap<Class<? extends Stage>, String> CLASS_TO_NAME = ImmutableMap.of(
      CpuProfilerStage.class, "CPU",
      MemoryProfilerStage.class, "MEMORY",
      NetworkProfilerStage.class, "NETWORK");

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Class value, int index, boolean selected, boolean hasFocus) {
      String name = CLASS_TO_NAME.get(value);
      append(name == null ? "[UNKNOWN]" : name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }


  @NotNull
  public IdeProfilerComponents getIdeProfilerComponents() {
    return myIdeProfilerComponents;
  }
}
