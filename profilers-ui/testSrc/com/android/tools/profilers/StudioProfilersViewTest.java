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

import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.cpu.CpuMonitorTooltip;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.energy.EnergyMonitorTooltip;
import com.android.tools.profilers.energy.EnergyProfilerStage;
import com.android.tools.profilers.memory.MemoryMonitorTooltip;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.network.NetworkMonitorTooltip;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.android.tools.profilers.sessions.SessionsView;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.google.common.truth.Truth;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.profilers.FakeProfilerService.FAKE_DEVICE_NAME;
import static com.android.tools.profilers.FakeProfilerService.FAKE_PROCESS_NAME;
import static com.google.common.truth.Truth.assertThat;

public class StudioProfilersViewTest {
  private static final Common.Session SESSION_O = Common.Session.newBuilder().setSessionId(2).setStartTimestamp(FakeTimer.ONE_SECOND_IN_NS)
    .setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS * 2).build();
  private static final Common.SessionMetaData SESSION_O_METADATA = Common.SessionMetaData.newBuilder().setSessionId(2).setJvmtiEnabled(true)
    .setSessionName("App Device").setType(Common.SessionMetaData.SessionType.FULL).setStartTimestampEpochMs(1).build();

  private final FakeProfilerService myService = new FakeProfilerService();
  @Rule public FakeGrpcServer myGrpcChannel = new FakeGrpcServer("StudioProfilerTestChannel", myService);
  private StudioProfilers myProfilers;
  private FakeIdeProfilerServices myProfilerServices = new FakeIdeProfilerServices();
  private FakeTimer myTimer;
  private StudioProfilersView myView;
  private FakeUi myUi;

  @Before
  public void setUp() throws Exception {
    myTimer = new FakeTimer();
    myProfilerServices.enableEnergyProfiler(true);
    myProfilers = new StudioProfilers(myGrpcChannel.getClient(), myProfilerServices, myTimer);
    myProfilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null);
    // Make sure a process is selected
    myView = new StudioProfilersView(myProfilers, new FakeIdeProfilerComponents());
    myView.bind(FakeStage.class, FakeView::new);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    JPanel component = myView.getComponent();
    component.setSize(1024, 450);
    myUi = new FakeUi(component);
  }

  @Test
  public void testSameStageTransition() {
    FakeStage stage = new FakeStage(myProfilers);
    myProfilers.setStage(stage);
    StageView view = myView.getStageView();

    myProfilers.setStage(stage);
    assertThat(myView.getStageView()).isEqualTo(view);
  }

  @Test
  public void testMonitorExpansion() {
    // Set session to enable Energy Monitor.
    myService.addSession(SESSION_O, SESSION_O_METADATA);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.getSessionsManager().setSession(SESSION_O);
    myUi = new FakeUi(myView.getComponent());

    assertThat(myProfilers.getStage()).isInstanceOf(StudioMonitorStage.class);

    List<Point> points = new TreeWalker(myView.getComponent()).descendantStream()
      .filter(d -> d instanceof LineChart)
      .map(c -> myUi.getPosition(c))
      .collect(Collectors.toList());
    // Test that we have the expected number of monitors
    assertThat(points.size()).isEqualTo(4);

    //// Test the first monitor goes to cpu profiler
    myUi.mouse.click(points.get(0).x + 1, points.get(0).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(CpuProfilerStage.class);
    myProfilers.setMonitoringStage();

    myUi.layout();
    // Test the second monitor goes to memory profiler
    myUi.mouse.click(points.get(1).x + 1, points.get(1).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(MemoryProfilerStage.class);
    myProfilers.setMonitoringStage();

    myUi.layout();
    // Test the third monitor goes to network profiler
    myUi.mouse.click(points.get(2).x + 1, points.get(2).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(NetworkProfilerStage.class);
    myProfilers.setMonitoringStage();

    myUi.layout();
    // Test the fourth monitor goes to energy profiler
    myUi.mouse.click(points.get(3).x + 1, points.get(3).y + 1);
    assertThat(myProfilers.getStage()).isInstanceOf(EnergyProfilerStage.class);
    myProfilers.setMonitoringStage();
  }

  @Test
  public void testMonitorTooltip() {
    // Set Session to enable Energy monitor tooltip.
    myService.addSession(SESSION_O, SESSION_O_METADATA);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.getSessionsManager().setSession(SESSION_O);
    myUi = new FakeUi(myView.getComponent());

    assertThat(myProfilers.getStage()).isInstanceOf(StudioMonitorStage.class);
    StudioMonitorStage stage = (StudioMonitorStage)myProfilers.getStage();

    List<Point> points = new TreeWalker(myView.getComponent()).descendantStream()
      .filter(d -> d instanceof LineChart)
      .map(c -> myUi.getPosition(c))
      .collect(Collectors.toList());
    // Test that we have the expected number of monitors
    assertThat(points.size()).isEqualTo(4);

    // cpu monitor tooltip
    myUi.mouse.moveTo(points.get(0).x + 1, points.get(0).y + 1);
    assertThat(stage.getTooltip()).isInstanceOf(CpuMonitorTooltip.class);
    ProfilerMonitor cpuMonitor = ((CpuMonitorTooltip)stage.getTooltip()).getMonitor();
    stage.getMonitors().forEach(
      monitor -> Truth.assertWithMessage("Only the CPU Monitor should be focused.")
        .that(monitor.isFocused()).isEqualTo(monitor == cpuMonitor));

    // memory monitor tooltip
    myUi.mouse.moveTo(points.get(1).x + 1, points.get(1).y + 1);
    assertThat(stage.getTooltip()).isInstanceOf(MemoryMonitorTooltip.class);
    ProfilerMonitor memoryMonitor = ((MemoryMonitorTooltip)stage.getTooltip()).getMonitor();
    stage.getMonitors().forEach(
      monitor -> Truth.assertWithMessage("Only the Memory Monitor should be focused.")
        .that(monitor.isFocused()).isEqualTo(monitor == memoryMonitor));

    // network monitor tooltip
    myUi.mouse.moveTo(points.get(2).x + 1, points.get(2).y + 1);
    assertThat(stage.getTooltip()).isInstanceOf(NetworkMonitorTooltip.class);
    ProfilerMonitor networMonitor = ((NetworkMonitorTooltip)stage.getTooltip()).getMonitor();
    stage.getMonitors().forEach(
      monitor -> Truth.assertWithMessage("Only the Network Monitor should be focused.")
        .that(monitor.isFocused()).isEqualTo(monitor == networMonitor));

    // energy monitor tooltip
    myUi.mouse.moveTo(points.get(3).x + 1, points.get(3).y + 1);
    assertThat(stage.getTooltip()).isInstanceOf(EnergyMonitorTooltip.class);
    ProfilerMonitor energyMonitor = ((EnergyMonitorTooltip)stage.getTooltip()).getMonitor();
    stage.getMonitors().forEach(
      monitor -> Truth.assertWithMessage("Only the Energy Monitor should be focused.")
        .that(monitor.isFocused()).isEqualTo(monitor == energyMonitor));

    // no tooltip
    myUi.mouse.moveTo(0, 0);
    assertThat(stage.getTooltip()).isNull();
    stage.getMonitors().forEach(monitor -> Truth.assertWithMessage("No monitor should be focused.").that(monitor.isFocused()).isFalse());
  }

  @Test
  public void testDeviceRendering() throws IOException {
    StudioProfilersView.DeviceComboBoxRenderer renderer = new StudioProfilersView.DeviceComboBoxRenderer();
    JList<Common.Device> list = new JList<>();
    // Null device
    Common.Device device = null;
    Component component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo(renderer.getEmptyText());

    // Standard case
    device = Common.Device.newBuilder()
      .setModel("Model")
      .setSerial("1234")
      .setState(Common.Device.State.ONLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234)");

    // Suffix not serial
    device = Common.Device.newBuilder()
      .setModel("Model-9999")
      .setSerial("1234")
      .setState(Common.Device.State.ONLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model-9999 (1234)");

    // Suffix serial
    device = Common.Device.newBuilder()
      .setModel("Model-1234")
      .setSerial("1234")
      .setState(Common.Device.State.ONLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234)");

    // With manufacturer
    device = Common.Device.newBuilder()
      .setManufacturer("Manufacturer")
      .setModel("Model")
      .setSerial("1234")
      .setState(Common.Device.State.ONLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Manufacturer Model (1234)");

    // Disconnected
    device = Common.Device.newBuilder()
      .setModel("Model")
      .setSerial("1234")
      .setState(Common.Device.State.DISCONNECTED)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234) [DISCONNECTED]");

    // Offline
    device = Common.Device.newBuilder()
      .setModel("Model")
      .setSerial("1234")
      .setState(Common.Device.State.OFFLINE)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234) [OFFLINE]");

    // Unspecifed
    device = Common.Device.newBuilder()
      .setModel("Model")
      .setSerial("1234")
      .setState(Common.Device.State.UNSPECIFIED)
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertThat(component.toString()).isEqualTo("Model (1234)");
  }

  @Test
  public void testProcessRendering() throws IOException {
    StudioProfilersView.ProcessComboBoxRenderer renderer = new StudioProfilersView.ProcessComboBoxRenderer();
    JList<Common.Process> list = new JList<>();
    // Null process
    Common.Process process = null;
    Component component = renderer.getListCellRendererComponent(list, process, 0, false, false);
    assertThat(component.toString()).isEqualTo(renderer.getEmptyText());

    // Process
    process = Common.Process.newBuilder()
      .setName("MyProcessName")
      .setPid(1234)
      .setState(Common.Process.State.ALIVE)
      .build();
    component = renderer.getListCellRendererComponent(list, process, 0, false, false);
    assertThat(component.toString()).isEqualTo("MyProcessName (1234)");

    // Dead process
    process = Common.Process.newBuilder()
      .setName("MyDeadProcessName")
      .setPid(4444)
      .setState(Common.Process.State.DEAD)
      .build();
    component = renderer.getListCellRendererComponent(list, process, 0, false, false);
    assertThat(component.toString()).isEqualTo("MyDeadProcessName (4444) [DEAD]");
  }

  @Test
  public void testMonitorStage() throws Exception {
    transitionStage(new StudioMonitorStage(myProfilers));
  }

  @Test
  public void testNetworkStage() throws Exception {
    transitionStage(new NetworkProfilerStage(myProfilers));
  }

  @Test
  public void testMemoryStage() throws Exception {
    transitionStage(new MemoryProfilerStage(myProfilers));
  }

  @Test
  public void testCpuStage() throws Exception {
    transitionStage(new CpuProfilerStage(myProfilers));
  }

  @Test
  public void testEnergyStage() throws Exception {
    transitionStage(new EnergyProfilerStage(myProfilers));
  }

  @Test
  public void testNoStage() throws Exception {
    StudioProfilersView view = new StudioProfilersView(myProfilers, new FakeIdeProfilerComponents());
    JPanel component = view.getComponent();
    new ReferenceWalker(myProfilers).assertNotReachable(view, component);
  }

  @Test
  public void testSessionsViewHiddenBehindFlag() {
    FakeTimer timer = new FakeTimer();
    FakeIdeProfilerServices services = new FakeIdeProfilerServices();
    services.enableSessionsView(false);
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), services, timer);
    StudioProfilersView view = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    JComponent splitter = view.getComponent();
    assertThat(splitter).isInstanceOf(ThreeComponentsSplitter.class);
    assertThat(((ThreeComponentsSplitter)splitter).getFirstComponent()).isNull();

    // Test the true case as well.
    services.enableSessionsView(true);
    profilers = new StudioProfilers(myGrpcChannel.getClient(), services, timer);
    view = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    splitter = view.getComponent();
    assertThat(splitter).isInstanceOf(ThreeComponentsSplitter.class);
    assertThat(((ThreeComponentsSplitter)splitter).getFirstComponent()).isNotNull();
  }

  @Test
  public void testRememberSessionUiStates() {
    // Check that sessions is initially expanded
    assertThat(myView.getSessionsView().getCollapsed()).isFalse();

    // Fake a collapse action and re-create the StudioProfilerView, the session UI should now remain collapsed.
    myView.getSessionsView().getCollapseButton().doClick();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), myProfilerServices, myTimer);
    StudioProfilersView profilersView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    assertThat(profilersView.getSessionsView().getCollapsed()).isTrue();

    // Fake a resize and re-create the StudioProfilerView, the session UI should maintain the previous dimension
    profilersView.getSessionsView().getExpandButton().doClick();
    ThreeComponentsSplitter splitter = (ThreeComponentsSplitter)profilersView.getComponent();
    assertThat(splitter.getFirstSize()).isEqualTo(SessionsView.getComponentMinimizeSize(true).width);
    splitter.setSize(1024, 450);
    FakeUi ui = new FakeUi(splitter);
    myUi.mouse.drag(splitter.getFirstSize(), 0, 10, 0);

    profilers = new StudioProfilers(myGrpcChannel.getClient(), myProfilerServices, myTimer);
    profilersView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    assertThat(profilersView.getSessionsView().getCollapsed()).isFalse();
    assertThat(((ThreeComponentsSplitter)profilersView.getComponent()).getFirstSize()).isEqualTo(splitter.getFirstSize());
  }

  @Test
  public void testGoLiveButtonStates() {
    // Check that go live is initially enabled and toggled
    JToggleButton liveButton = myView.getGoLiveButton();
    ArrayList<ContextMenuItem> contextMenuItems = ProfilerContextMenu.createIfAbsent(myView.getStageComponent()).getContextMenuItems();
    ContextMenuItem attachItem = null;
    ContextMenuItem detachItem = null;
    for (ContextMenuItem item : contextMenuItems) {
      if (item.getText().equals(StudioProfilersView.ATTACH_LIVE)) {
        attachItem = item;
      }
      else if (item.getText().equals(StudioProfilersView.DETACH_LIVE)) {
        detachItem = item;
      }
    }
    assertThat(attachItem).isNotNull();
    assertThat(detachItem).isNotNull();

    assertThat(myProfilers.getSessionsManager().isSessionAlive()).isTrue();
    assertThat(liveButton.isEnabled()).isTrue();
    assertThat(liveButton.isSelected()).isTrue();
    assertThat(attachItem.isEnabled()).isFalse();
    assertThat(detachItem.isEnabled()).isTrue();

    // Detaching from live should unselect the button.
    detachItem.run();
    assertThat(myProfilers.getSessionsManager().isSessionAlive()).isTrue();
    assertThat(liveButton.isEnabled()).isTrue();
    assertThat(liveButton.isSelected()).isFalse();
    assertThat(attachItem.isEnabled()).isTrue();
    assertThat(detachItem.isEnabled()).isFalse();
    assertThat(liveButton.getIcon()).isEqualTo(StudioIcons.Profiler.Toolbar.GOTO_LIVE);
    assertThat(liveButton.getToolTipText()).startsWith(StudioProfilersView.ATTACH_LIVE);

    // Attaching to live should select the button again.
    attachItem.run();
    assertThat(myProfilers.getSessionsManager().isSessionAlive()).isTrue();
    assertThat(liveButton.isEnabled()).isTrue();
    assertThat(liveButton.isSelected()).isTrue();
    assertThat(attachItem.isEnabled()).isFalse();
    assertThat(detachItem.isEnabled()).isTrue();
    assertThat(liveButton.getIcon()).isEqualTo(StudioIcons.Profiler.Toolbar.PAUSE_LIVE);
    assertThat(liveButton.getToolTipText()).startsWith(StudioProfilersView.DETACH_LIVE);

    // Stopping the session should disable and unselect the button
    myProfilers.getSessionsManager().endCurrentSession();
    Common.Session deadSession = myProfilers.getSessionsManager().getSelectedSession();
    assertThat(myProfilers.getSessionsManager().isSessionAlive()).isFalse();
    assertThat(liveButton.isEnabled()).isFalse();
    assertThat(liveButton.isSelected()).isFalse();
    assertThat(attachItem.isEnabled()).isFalse();
    assertThat(detachItem.isEnabled()).isFalse();

    Common.Device onlineDevice = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build();
    Common.Process onlineProcess = Common.Process.newBuilder().setPid(2).setState(Common.Process.State.ALIVE).build();
    myProfilers.getSessionsManager().beginSession(onlineDevice, onlineProcess);
    assertThat(myProfilers.getSessionsManager().isSessionAlive()).isTrue();
    // Live button should be selected when switching to a live session.
    assertThat(liveButton.isEnabled()).isTrue();
    assertThat(liveButton.isSelected()).isTrue();
    assertThat(attachItem.isEnabled()).isFalse();
    assertThat(detachItem.isEnabled()).isTrue();

    // Switching to a dead session should disable and unselect the button.
    myProfilers.getSessionsManager().setSession(deadSession);
    assertThat(liveButton.isEnabled()).isFalse();
    assertThat(liveButton.isSelected()).isFalse();
    assertThat(attachItem.isEnabled()).isFalse();
    assertThat(detachItem.isEnabled()).isFalse();
  }

  @Test
  public void testGoLiveButtonWhenToggleStreaming() {
    JToggleButton liveButton = myView.getGoLiveButton();
    assertThat(liveButton.isEnabled()).isTrue();
    myProfilers.getTimeline().setStreaming(false);
    assertThat(liveButton.isSelected()).isFalse();
    assertThat(liveButton.getIcon()).isEqualTo(StudioIcons.Profiler.Toolbar.GOTO_LIVE);
    assertThat(liveButton.getToolTipText()).startsWith(StudioProfilersView.ATTACH_LIVE);

    myProfilers.getTimeline().setStreaming(true);
    assertThat(liveButton.isSelected()).isTrue();
    assertThat(liveButton.getIcon()).isEqualTo(StudioIcons.Profiler.Toolbar.PAUSE_LIVE);
    assertThat(liveButton.getToolTipText()).startsWith(StudioProfilersView.DETACH_LIVE);
  }

  public void transitionStage(Stage stage) throws Exception {
    JPanel component = myView.getComponent();
    myProfilers.setStage(new FakeStage(myProfilers));
    new ReferenceWalker(myProfilers).assertNotReachable(myView, component);
    myProfilers.setStage(stage);
    // At this point it could be reachable with standard swing listeners.
    myProfilers.setStage(new FakeStage(myProfilers));
    // If we leaked a listener or a component in the tree then there will be a path
    // from the model all the way up to the main view or the main component. There could
    // be the case that some listeners that don't point to the view/component are still
    // leaked but it would be pretty rare that such a listener was needed in the first place.
    new ReferenceWalker(myProfilers).assertNotReachable(myView, component);
  }

  static class FakeView extends StageView<FakeStage> {

    public FakeView(@NotNull StudioProfilersView profilersView, @NotNull FakeStage stage) {
      super(profilersView, stage);
    }

    @Override
    public JComponent getToolbar() {
      return new JPanel();
    }
  }
}