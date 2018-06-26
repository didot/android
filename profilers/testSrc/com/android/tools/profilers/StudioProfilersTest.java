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

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler.AgentStatusResponse;
import com.android.tools.profiler.proto.Profiler.VersionRequest;
import com.android.tools.profiler.proto.Profiler.VersionResponse;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuProfilerTestUtils;
import com.android.tools.profilers.energy.EnergyProfilerStage;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StudioProfilersTest {
  private final FakeProfilerService myProfilerService = new FakeProfilerService(false);
  private final FakeGrpcServer.CpuService myCpuService = new FakeGrpcServer.CpuService();
  @Rule public FakeGrpcServer myGrpcServer = new FakeGrpcServer("StudioProfilerTestChannel", myProfilerService, myCpuService);

  @Before
  public void setup() {
    myProfilerService.reset();
  }

  @Test
  public void testVersion() {
    VersionResponse response =
      myGrpcServer.getClient().getProfilerClient().getVersion(VersionRequest.getDefaultInstance());
    assertThat(response.getVersion()).isEqualTo(FakeProfilerService.VERSION);
  }

  @Test
  public void testClearedOnMonitorStage() {
    StudioProfilers profilers = getProfilersWithDeviceAndProcess();
    assertThat(profilers.getTimeline().getSelectionRange().isEmpty()).isTrue();

    profilers.setStage(new CpuProfilerStage(profilers));
    profilers.getTimeline().getSelectionRange().set(10, 10);
    profilers.setMonitoringStage();

    assertThat(profilers.getTimeline().getSelectionRange().isEmpty()).isTrue();
  }

  @Test
  public void testProfilerModeChange() throws Exception {
    StudioProfilers profilers = getProfilersWithDeviceAndProcess();
    assertThat(profilers.getMode()).isEqualTo(ProfilerMode.NORMAL);
    CpuProfilerStage stage = new CpuProfilerStage(profilers);
    profilers.setStage(stage);
    assertThat(profilers.getMode()).isEqualTo(ProfilerMode.NORMAL);
    stage.setAndSelectCapture(CpuProfilerTestUtils.getValidCapture());
    assertThat(profilers.getMode()).isEqualTo(ProfilerMode.EXPANDED);
    profilers.setMonitoringStage();
    assertThat(profilers.getMode()).isEqualTo(ProfilerMode.NORMAL);
  }

  @Test
  public void testNullClient() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(null, new FakeIdeProfilerServices(), timer);
    assertThat(profilers.getClient()).isNull();

    // Make sure update does no harm
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isNull();
    assertThat(profilers.getProcess()).isNull();
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());
  }

  @Test
  public void testSleepBeforeAppLaunched() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    //Validate we start in the null stage.
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setDevice(device);
    // Validate that just because we add a device, we still have not left the  null monitor stage.
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);

    // Pick a time to set the device to. Note that this value is arbitrary but the bug this tests
    // is exposed if this value is larger than nanoTime.
    long timeOnDevice = System.nanoTime() + 1000;
    myProfilerService.setTimestampNs(timeOnDevice);

    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    // Add a process and validate the stage goes to the monitor stage.
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(process);
    // Test that the process was attached correctly
    assertThat(profilers.getTimeline().isStreaming()).isTrue();
    // Test that the data range has not been inverted
    assertThat(profilers.getTimeline().getDataRange().isEmpty()).isFalse();
  }

  @Test
  public void testProfilerStageChange() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    //Validate we start in the null stage.
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setDevice(device);
    // Validate that just because we add a device, we still have not left the  null monitor stage.
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);

    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    // Add a process and validate the stage goes to the monitor stage.
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process);
    assertThat(profilers.getStageClass()).isSameAs(StudioMonitorStage.class);
    // Add a second device with no processes, and select that device.
    device = Common.Device.newBuilder().setSerial("FakeDevice2").build();
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setDevice(device);
    assertThat(profilers.getStageClass()).isSameAs(NullMonitorStage.class);
  }

  @Test
  public void testLateConnectionOfPreferredProcess() {
    final String PREFERRED_PROCESS = "Preferred";
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    profilers.setPreferredProcess(null, PREFERRED_PROCESS, null);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isNull();
    assertThat(profilers.getProcess()).isNull();

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setDevice(device);
    // We are waiting for the preferred process so the process should not be selected.
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getProcess()).isNull();

    Common.Process preferred = createProcess(device.getDeviceId(), 20, PREFERRED_PROCESS, Common.Process.State.ALIVE);
    myProfilerService.addProcess(device, preferred);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up

    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(preferred);
    assertThat(profilers.getProcesses()).hasSize(2);
    assertThat(profilers.getProcesses()).containsAllIn(ImmutableList.of(process, preferred));
  }

  @Test
  public void testSetPreferredProcessDoesNotProfileEarlierProcess() {
    final String PREFERRED_PROCESS = "Preferred";
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    profilers.setPreferredProcess(null, PREFERRED_PROCESS, p -> p.getStartTimestampNs() > 5);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isNull();
    assertThat(profilers.getProcess()).isNull();

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process earlierProcess = Common.Process.newBuilder()
                                           .setDeviceId(device.getDeviceId())
                                           .setPid(20)
                                           .setName(PREFERRED_PROCESS)
                                           .setState(Common.Process.State.ALIVE)
                                           .setStartTimestampNs(5)
                                           .build();
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, earlierProcess);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    // The process start time is before the time we start looking for preferred process, so profiler should not have started.
    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isNull();

    Common.Process afterProcess = Common.Process.newBuilder()
                                                .setDeviceId(device.getDeviceId())
                                                .setPid(21)
                                                .setName(PREFERRED_PROCESS)
                                                .setState(Common.Process.State.ALIVE)
                                                .setStartTimestampNs(10)
                                                .build();
    myProfilerService.addProcess(device, afterProcess);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(afterProcess);
    assertThat(profilers.getProcesses()).hasSize(2);
    assertThat(profilers.getProcesses()).containsAllIn(ImmutableList.of(earlierProcess, afterProcess));
  }

  @Test
  public void testConnectionError() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE)
      .toBuilder().setModel("FakeDevice").build();
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);

    profilers.setPreferredProcess("FakeDevice", "FakeProcess", null);
    // This should fail and not find any devices
    myProfilerService.setThrowErrorOnGetDevices(true);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getDevice()).isNull();
    assertThat(profilers.getProcess()).isNull();

    // Server "is back up", try again
    myProfilerService.setThrowErrorOnGetDevices(false);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice");
    assertThat(profilers.getProcess().getName()).isEqualTo("FakeProcess");
  }

  @Test
  public void testAlreadyConnected() {
    FakeTimer timer = new FakeTimer();
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);

    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process);
    assertThat(profilers.getStage()).isInstanceOf(StudioMonitorStage.class);
  }

  @Test
  public void testTimeResetOnConnectedDevice() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    int nowInSeconds = 42;
    myProfilerService.setTimestampNs(TimeUnit.SECONDS.toNanos(nowInSeconds));

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process);

    assertThat(profilers.getTimeline().getDataRange().getMin()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(nowInSeconds));
    assertThat(profilers.getTimeline().getDataRange().getMax()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(nowInSeconds));

    // The timeline has reset in the previous tick, so we need to advance the current time to make sure the next tick advances data range.
    timer.setCurrentTimeNs(FakeTimer.ONE_SECOND_IN_NS * 5);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS * 5);

    assertThat(profilers.getTimeline().getDataRange().getMin()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(nowInSeconds));
    assertThat(profilers.getTimeline().getDataRange().getMax()).isWithin(0.001).of(TimeUnit.SECONDS.toMicros(nowInSeconds + 5));
  }

  @Test
  public void testAgentAspectFiring() {
    FakeTimer timer = new FakeTimer();
    AgentStatusAspectObserver observer = new AgentStatusAspectObserver();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    profilers.addDependency(observer).onChange(ProfilerAspect.AGENT, observer::AgentStatusChanged);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getAgentStatus()).isEqualTo(AgentStatusResponse.getDefaultInstance());
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(0);

    // Test that status changes if no process is selected does nothing
    AgentStatusResponse attachedResponse = AgentStatusResponse.newBuilder().setStatus(AgentStatusResponse.Status.ATTACHED).build();
    myProfilerService.setAgentStatus(attachedResponse);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess()).isNull();
    assertThat(profilers.getAgentStatus()).isEqualTo(AgentStatusResponse.getDefaultInstance());
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(0);

    // Test that agent status change fires after a process is selected.
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    Common.Process process2 = createProcess(device.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process1);
    myProfilerService.addProcess(device, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process1);

    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(profilers.getAgentStatus()).isEqualTo(attachedResponse);
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(1);

    // Test that manually setting a process fires an agent status change
    profilers.setProcess(process2);
    assertThat(profilers.getProcess()).isSameAs(process2);
    assertThat(profilers.getAgentStatus()).isEqualTo(attachedResponse);
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(2);

    // Setting the same agent status should not trigger an aspect change.
    attachedResponse = AgentStatusResponse.newBuilder().setStatus(AgentStatusResponse.Status.ATTACHED).build();
    myProfilerService.setAgentStatus(attachedResponse);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getAgentStatus()).isEqualTo(attachedResponse);
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(2);

    AgentStatusResponse detachResponse = AgentStatusResponse.newBuilder().setStatus(AgentStatusResponse.Status.DETACHED).build();
    myProfilerService.setAgentStatus(detachResponse);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getAgentStatus()).isEqualTo(detachResponse);
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(3);

    // Setting the same agent status should not trigger an aspect change.
    detachResponse = AgentStatusResponse.newBuilder().setStatus(AgentStatusResponse.Status.DETACHED).build();
    myProfilerService.setAgentStatus(detachResponse);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getAgentStatus()).isEqualTo(detachResponse);
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(3);
  }

  @Test
  public void testAgentAspectNotFiredWhenSettingSameDeviceProcess() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);

    AgentStatusAspectObserver observer = new AgentStatusAspectObserver();
    profilers.addDependency(observer).onChange(ProfilerAspect.AGENT, observer::AgentStatusChanged);

    // Test that the status changed is fired when the process first gets selected.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process);

    assertThat(profilers.getDevice()).isSameAs(device);
    assertThat(profilers.getProcess()).isEqualTo(process);
    assertThat(profilers.isAgentAttached()).isFalse();
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(1);

    // Test that resetting the same device/process would not trigger the status changed event.
    profilers.setDevice(device);
    profilers.setProcess(process);
    assertThat(profilers.getDevice()).isSameAs(device);
    assertThat(profilers.getProcess()).isEqualTo(process);
    assertThat(profilers.isAgentAttached()).isFalse();
    assertThat(observer.getAgentStatusChangedCount()).isEqualTo(1);
  }

  @Test
  public void shouldSelectAlivePreferredProcessWhenRestarted() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    int nowInSeconds = 42;
    myProfilerService.setTimestampNs(TimeUnit.SECONDS.toNanos(nowInSeconds));

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Common.Device device =
      createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE).toBuilder().setModel("FakeDevice").build();
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    profilers.setPreferredProcess("FakeDevice", process.getName(), null);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);

    // Change the alive (active) process to DEAD, and create a new ALIVE process simulating a debugger restart.
    myProfilerService.removeProcess(device, process);
    process = process.toBuilder()
                     .setState(Common.Process.State.DEAD)
                     .build();
    myProfilerService.addProcess(device, process);

    // Verify the process is in the dead state.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.DEAD);

    process = process.toBuilder()
                     .setPid(21)
                     .setState(Common.Process.State.ALIVE)
                     .build();
    myProfilerService.addProcess(device, process);

    // The profiler should select the alive preferred process.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(21);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);
  }

  @Test
  public void shouldNotSelectPreferredAfterUserSelectsOtherProcess() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    int nowInSeconds = 42;
    myProfilerService.setTimestampNs(TimeUnit.SECONDS.toNanos(nowInSeconds));

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE)
      .toBuilder().setModel("FakeDevice").build();
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    profilers.setPreferredProcess("FakeDevice", process.getName(), null);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);

    Common.Process otherProcess = createProcess(device.getDeviceId(), 21, "OtherProcess", Common.Process.State.ALIVE);
    myProfilerService.addProcess(device, otherProcess);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(otherProcess);
    // The user selected the other process explicitly
    assertThat(profilers.getProcess().getPid()).isEqualTo(21);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);

    // Should select the other process again
    profilers.setProcess(null);
    assertThat(profilers.getProcess().getPid()).isEqualTo(21);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);
  }

  @Test
  public void shouldOpenCpuProfileStageIfStartupProfilingStarted() {
    FakeTimer timer = new FakeTimer();
    FakeIdeProfilerServices ideServices = new FakeIdeProfilerServices();
    ideServices.enableStartupCpuProfiling(true);

    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), ideServices, timer);
    myProfilerService.setTimestampNs(TimeUnit.SECONDS.toNanos(42));

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    myCpuService.setStartupProfiling(true);

    // To make sure that StudioProfilers#update is called, which in a consequence polls devices and processes,
    // and starts a new session with the preferred process name.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process);

    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(profilers.getProcess().getState()).isEqualTo(Common.Process.State.ALIVE);
    assertThat(profilers.getStage()).isInstanceOf(CpuProfilerStage.class);
  }

  @Test
  public void testProcessStateChangesShouldNotTriggerStageChange() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process);

    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(Common.Process.State.ALIVE).isEqualTo(profilers.getProcess().getState());

    AspectObserver observer = new AspectObserver();
    profilers.addDependency(observer).onChange(ProfilerAspect.STAGE, () -> {
      assert false;
    });
    // Change the alive (active) process to DEAD
    myProfilerService.removeProcess(device, process);
    process = process.toBuilder()
                     .setState(Common.Process.State.DEAD)
                     .build();
    myProfilerService.addProcess(device, process);

    //Verify the process is in the dead state.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getProcess().getPid()).isEqualTo(20);
    assertThat(Common.Process.State.DEAD).isEqualTo(profilers.getProcess().getState());
  }

  @Test
  public void timelineShouldBeStreamingWhenProcessIsSelected() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process);

    assertThat(profilers.getTimeline().isStreaming()).isTrue();
  }

  @Test
  public void timelineShouldStopStreamingWhenRangeIsSelected() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process);

    ProfilerTimeline timeline = profilers.getTimeline();
    assertTrue(timeline.isStreaming());
    timeline.getDataRange().set(0, FakeTimer.ONE_SECOND_IN_NS);
    timeline.getSelectionRange().set(0, 0);
    assertFalse(timeline.isStreaming());

    timeline.setStreaming(true);
    assertTrue(timeline.isStreaming());
    timeline.getSelectionRange().set(0, FakeTimer.ONE_SECOND_IN_NS);
    assertFalse(timeline.isStreaming());
  }

  @Test
  public void onlineDeviceShouldNotOverrideSelectedOfflineDevice() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process);
    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process);

    // Connect a new device, and marks the old one as disconnected
    Common.Device dead_device = device.toBuilder().setState(Common.Device.State.DISCONNECTED).build();
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    Common.Process process2 = createProcess(device2.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myProfilerService.updateDevice(device, dead_device);
    myProfilerService.addDevice(device2);
    myProfilerService.addProcess(device2, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connecting an online device should not override previously selection automatically
    assertThat(profilers.getDevice()).isEqualTo(dead_device);
    assertThat(profilers.getProcess()).isEqualTo(process);
  }

  @Test
  public void preferredDeviceShouldNotOverrideSelectedDevice() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    profilers.setPreferredProcess("Manufacturer Model", "ProcessName", null);

    // A device with a process that can be profiled
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);

    // The preferred device
    Common.Device preferredDevice = createDevice(AndroidVersion.VersionCodes.BASE, "PreferredDevice", Common.Device.State.ONLINE);
    preferredDevice = preferredDevice.toBuilder().setManufacturer("Manufacturer").setModel("Model").build();
    Common.Process preferredProcess =
      createProcess(preferredDevice.getDeviceId(), 21, "PreferredProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(preferredDevice);
    myProfilerService.addProcess(preferredDevice, preferredProcess);

    // Preferred should be selected.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(preferredDevice);

    // Selecting device manually should keep it selected
    profilers.setDevice(device);
    assertThat(profilers.getDevice()).isEqualTo(device);

    // Changing states on the preferred device should have no effects.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(device);

    Common.Device offlinePreferredDevice = preferredDevice.toBuilder().setState(Common.Device.State.DISCONNECTED).build();
    myProfilerService.updateDevice(preferredDevice, offlinePreferredDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(device);

    myProfilerService.updateDevice(offlinePreferredDevice, preferredDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(device);
  }

  @Test
  public void preferredDeviceHasPriority() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    profilers.setPreferredProcess("Manufacturer Model", "PreferredProcess", null);

    // A device with a process that can be profiled
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);

    // Create the preferred device but have it offline to start with.
    Common.Device offlinePreferredDevice = createDevice(AndroidVersion.VersionCodes.BASE, "PreferredDevice", Common.Device.State.OFFLINE);
    offlinePreferredDevice = offlinePreferredDevice.toBuilder().setManufacturer("Manufacturer").setModel("Model").build();
    Common.Process preferredProcess =
      createProcess(offlinePreferredDevice.getDeviceId(), 21, "PreferredProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(offlinePreferredDevice);
    myProfilerService.addProcess(offlinePreferredDevice, preferredProcess);
    // No device should be selected given no online preferred device is found.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isNull();
    assertThat(profilers.getProcess()).isNull();

    // Turn the preferred device online and it should be selected.
    Common.Device preferredDevice = offlinePreferredDevice.toBuilder().setState(Common.Device.State.ONLINE).build();
    myProfilerService.updateDevice(offlinePreferredDevice, preferredDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(preferredDevice);
    assertThat(profilers.getProcess()).isEqualTo(preferredProcess);

    Common.Device preferredDevice2 = createDevice(AndroidVersion.VersionCodes.BASE, "PreferredDevice2", Common.Device.State.ONLINE);
    preferredDevice2 = preferredDevice2.toBuilder().setManufacturer("Manufacturer2").setModel("Model2").build();
    Common.Process preferredProcess2 = createProcess(preferredDevice2.getDeviceId(), 22, "PreferredProcess2", Common.Process.State.ALIVE);
    myProfilerService.addDevice(preferredDevice2);
    myProfilerService.addProcess(preferredDevice2, preferredProcess2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getDevice()).isEqualTo(preferredDevice);
    assertThat(profilers.getProcess()).isEqualTo(preferredProcess);

    // Updating the preferred device should immediately switch over.
    profilers.setPreferredProcess("Manufacturer2 Model2", "PreferredProcess", null);
    assertThat(profilers.getDevice()).isEqualTo(preferredDevice2);
    assertThat(profilers.getProcess()).isNull();
  }

  @Test
  public void keepSelectedDeviceAfterDisconnectingAllDevices() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device1 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device1.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device1);
    myProfilerService.addProcess(device1, process1);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Connect a new device
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    Common.Process process2 = createProcess(device2.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device2);
    myProfilerService.addProcess(device2, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    profilers.setDevice(device2);
    assertThat(profilers.getDevice()).isEqualTo(device2);
    // Update device1 state to disconnect
    Common.Device disconnectedDevice = Common.Device.newBuilder()
                                                    .setDeviceId(device1.getDeviceId())
                                                    .setSerial(device1.getSerial())
                                                    .setState(Common.Device.State.DISCONNECTED)
                                                    .build();
    myProfilerService.updateDevice(device1, disconnectedDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Update device2 state to disconnect
    Common.Device disconnectedDevice2 = Common.Device.newBuilder()
                                                     .setDeviceId(device2.getDeviceId())
                                                     .setSerial(device2.getSerial())
                                                     .setState(Common.Device.State.DISCONNECTED)
                                                     .build();
    myProfilerService.updateDevice(device2, disconnectedDevice2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Selected device should be FakeDevice2, which was selected before disconnecting all devices
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDevice2");
    // Make sure the device is disconnected
    assertThat(profilers.getDevice().getState()).isEqualTo(Common.Device.State.DISCONNECTED);
  }

  @Test
  public void testProfileOneProcessAtATime() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device1 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device1.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    Common.Process process2 = createProcess(device1.getDeviceId(), 21, "FakeProcess2", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device1);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
    myProfilerService.addProcess(device1, process1);
    myProfilerService.addProcess(device1, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device1);
    profilers.setProcess(process1);

    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);

    // Switch to another process.
    profilers.setProcess(process2);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);
    assertThat(profilers.getProcess()).isEqualTo(process2);

    // Connect a new device with a process.
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    Common.Process process3 = createProcess(device2.getDeviceId(), 22, "FakeProcess3", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device2);
    myProfilerService.addProcess(device2, process3);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    // Switch to the new device + process
    profilers.setDevice(device2);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
    profilers.setProcess(process3);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);
    assertThat(profilers.getProcess()).isEqualTo(process3);

    // Update device2 state to disconnect
    Common.Device disconnectedDevice2 = device2.toBuilder()
                                               .setState(Common.Device.State.DISCONNECTED)
                                               .build();
    myProfilerService.updateDevice(device2, disconnectedDevice2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);

    // Switch back to the first device.
    profilers.setDevice(device1);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
    profilers.setProcess(process1);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);

    // Update device1 state to disconnect
    Common.Device disconnectedDevice = device1.toBuilder()
                                              .setState(Common.Device.State.DISCONNECTED)
                                              .build();
    myProfilerService.updateDevice(device1, disconnectedDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
  }

  @Test
  public void testAttachAgentCalledWhenFeatureEnabled() {
    FakeIdeProfilerServices fakeIdeService = new FakeIdeProfilerServices();
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), fakeIdeService, timer);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 1, "FakeProcess1", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process1);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process1);

    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(myProfilerService.getAgentAttachCalled()).isFalse();

    fakeIdeService.enableJvmtiAgent(true);
    Common.Process process2 = createProcess(device.getDeviceId(), 2, "FakeProcess2", Common.Process.State.ALIVE);
    myProfilerService.addProcess(device, process2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(process2);
    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process2);
    assertThat(myProfilerService.getAgentAttachCalled()).isTrue();
  }

  @Test
  public void testAttachAgentNotCalledPreO() {
    FakeIdeProfilerServices fakeIdeService = new FakeIdeProfilerServices();
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), fakeIdeService, timer);

    fakeIdeService.enableJvmtiAgent(true);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.N, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 1, "FakeProcess1", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process1);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process1);

    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(myProfilerService.getAgentAttachCalled()).isFalse();
  }

  /**
   * We need to account for an scenario where perfd reinstantiates and needs to pass a new client socket to the app. Hence we make the
   * same attach agent call from Studio side and let perfd handles the rest.
   */
  @Test
  public void testAttachAgentEvenIfAlreadyAttached() {
    FakeIdeProfilerServices fakeIdeService = new FakeIdeProfilerServices();
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), fakeIdeService, timer);

    AgentStatusResponse attachedResponse = AgentStatusResponse.newBuilder().setStatus(AgentStatusResponse.Status.ATTACHED).build();
    myProfilerService.setAgentStatus(attachedResponse);
    fakeIdeService.enableJvmtiAgent(true);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process1 = createProcess(device.getDeviceId(), 1, "FakeProcess1", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process1);

    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process1);

    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process1);
    assertThat(myProfilerService.getAgentAttachCalled()).isTrue();
  }

  @Test
  public void testProfilingStops()  {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE)
      .toBuilder().setModel("FakeDevice").build();
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
    myProfilerService.addProcess(device, process);

    profilers.setPreferredProcess("FakeDevice", "FakeProcess", null);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertThat(profilers.getProcess()).isEqualTo(process);
    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(1);
    assertThat(profilers.getProcess()).isEqualTo(process);
    assertThat(timer.isRunning()).isTrue();

    // Stop the profiler
    profilers.stop();

    assertThat(myGrpcServer.getProfiledProcessCount()).isEqualTo(0);
    assertThat(profilers.getProcess()).isNull();
    assertThat(profilers.getDevice()).isNull();
    assertThat(timer.isRunning()).isFalse();
  }

  @Test
  public void testNullDeviceKeepsPreviousSession() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device1 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice1", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device1.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    Common.Device device2 = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice2", Common.Device.State.ONLINE);
    myProfilerService.addDevice(device1);
    myProfilerService.addProcess(device1, process);
    myProfilerService.addDevice(device2);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device1);
    profilers.setProcess(process);

    Common.Session session = profilers.getSession();
    assertThat(profilers.getDevice()).isEqualTo(device1);
    assertThat(profilers.getProcess()).isEqualTo(process);
    assertThat(session).isNotEqualTo(Common.Session.getDefaultInstance());
    assertThat(profilers.getStageClass()).isEqualTo(StudioMonitorStage.class);

    // Setting device to null should maintain the current session.
    profilers.setDevice(null);
    assertThat(profilers.getDevice()).isEqualTo(null);
    assertThat(profilers.getProcess()).isEqualTo(null);
    assertThat(profilers.getSession().getSessionId()).isEqualTo(session.getSessionId());
    assertThat(profilers.getStageClass()).isEqualTo(StudioMonitorStage.class);

    // Setting the device without processes should go to the NullMonitorStage.
    profilers.setDevice(device2);
    assertThat(profilers.getDevice()).isEqualTo(device2);
    assertThat(profilers.getProcess()).isEqualTo(null);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());
    assertThat(profilers.getStageClass()).isEqualTo(NullMonitorStage.class);
  }

  @Test
  public void testProfilingStopsWithLiveAllocationEnabled() {
    FakeTimer timer = new FakeTimer();
    FakeIdeProfilerServices services = new FakeIdeProfilerServices();
    // Enable live allocation tracker
    services.enableLiveAllocationTracking(true);
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), services, timer);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.O, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);

    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process);

    assertThat(timer.isRunning()).isTrue();

    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isEqualTo(process);

    // Stop the profiler
    profilers.stop();

    assertThat(timer.isRunning()).isFalse();
    assertThat(profilers.getProcess()).isEqualTo(null);
    assertThat(profilers.getDevice()).isEqualTo(null);
  }

  @Test
  public void testStoppingTwice() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    // Should be modified when STAGE aspect is fired.
    boolean[] stageAspectTriggered = {false};
    profilers.addDependency(new AspectObserver())
             .onChange(ProfilerAspect.STAGE, () -> stageAspectTriggered[0] = true);

    // Check profiler is not stopped.
    assertThat(profilers.isStopped()).isFalse();
    assertThat(timer.isRunning()).isTrue();
    // Stop the profiler
    profilers.stop();
    // Profiler should have stopped and STAGE is supposed to have been fired.
    assertThat(stageAspectTriggered[0]).isTrue();

    // Check profiler is stopped.
    assertThat(profilers.isStopped()).isTrue();
    assertThat(timer.isRunning()).isFalse();
    stageAspectTriggered[0] = false;
    // Try to stop the profiler again.
    profilers.stop();
    // Profiler was already stopped and STAGE is not supposed to have been fired.
    assertThat(stageAspectTriggered[0]).isFalse();
  }

  @Test
  public void testBeginAndEndSessionOnProcessChange() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    // Adds a device without processes. Session should be null.
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    // Adds a process, which should trigger the session to start.
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process);

    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);

    // Mark the process as dead, which should ends the session.
    myProfilerService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isNotEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void testBeginAndEndSessionOnDeviceChange() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    // Adds a device with process. Session should start immediately
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setDevice(device);
    profilers.setProcess(process);
    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);

    // Killing the device should stop the session
    myProfilerService.removeProcess(device, process);
    Common.Device deadDevice = device.toBuilder().setState(Common.Device.State.DISCONNECTED).build();
    Common.Process deadProcess = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myProfilerService.addDevice(deadDevice);
    myProfilerService.addProcess(deadDevice, deadProcess);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isNotEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void testSessionDoesNotAutoStartOnSameProcess() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setDevice(device);
    profilers.setProcess(process);

    // End the session.
    myProfilerService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession()).isNotNull();
    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());

    // The same process coming alive should not start a new session automatically.
    myProfilerService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.ALIVE).build();
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());
  }

  @Test
  public void testNewSessionResetsStage() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setDevice(device);
    profilers.setProcess(process);

    assertThat(profilers.getSession()).isNotNull();
    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);
    assertThat(profilers.getStage()).isInstanceOf(StudioMonitorStage.class);

    // Goes into a different stage
    profilers.setStage(new FakeStage(profilers));
    assertThat(profilers.getStage()).isInstanceOf(FakeStage.class);

    // Ending a session should not leave the stage automatically.
    myProfilerService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.DEAD).build();
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession()).isNotNull();
    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isNotEqualTo(Long.MAX_VALUE);
    assertThat(profilers.getStage()).isInstanceOf(FakeStage.class);

    // Restarting a session on the same process should re-enter the StudioMonitorStage
    myProfilerService.removeProcess(device, process);
    process = process.toBuilder().setState(Common.Process.State.ALIVE).build();
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setProcess(process);
    assertThat(profilers.getSession()).isNotNull();
    assertThat(profilers.getSession().getDeviceId()).isEqualTo(device.getDeviceId());
    assertThat(profilers.getSession().getPid()).isEqualTo(process.getPid());
    assertThat(profilers.getSession().getEndTimestamp()).isEqualTo(Long.MAX_VALUE);
    assertThat(profilers.getStage()).isInstanceOf(StudioMonitorStage.class);
  }

  @Test
  public void testGetDirectStagesReturnsOnlyExpectedStages() {
    FakeTimer timer = new FakeTimer();
    FakeIdeProfilerServices fakeServices = new FakeIdeProfilerServices();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), fakeServices, timer);

    // When energy flag is enabled and device is pre-O, GetDirectStages does not return Energy stage.
    fakeServices.enableEnergyProfiler(true);
    Common.Device deviceNougat = createDevice(AndroidVersion.VersionCodes.N_MR1, "FakeDeviceN", Common.Device.State.ONLINE);
    myProfilerService.addDevice(deviceNougat);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(deviceNougat);
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDeviceN");

    assertThat(profilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class).inOrder();

    // When energy flag is enabled and device is O, GetDirectStages returns Energy stage.
    Common.Device deviceOreo = createDevice(AndroidVersion.VersionCodes.O, "FakeDeviceO", Common.Device.State.ONLINE);
    myProfilerService.addDevice(deviceOreo);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(deviceOreo);
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDeviceO");

    assertThat(profilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class,
      EnergyProfilerStage.class).inOrder();

    // When energy flag is disabled and device is O, GetDirectStages does not return Energy stage.
    fakeServices.enableEnergyProfiler(false);
    assertThat(profilers.getDevice().getSerial()).isEqualTo("FakeDeviceO");

    assertThat(profilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class).inOrder();
  }

  @Test
  public void testGetDirectStageReturnsEnergyOnlyForPostOSession() {
    FakeTimer timer = new FakeTimer();
    FakeIdeProfilerServices fakeServices = new FakeIdeProfilerServices();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), fakeServices, timer);

    // When energy flag is enabled and the session is pre-O, GetDirectStages does not return Energy stage.
    fakeServices.enableEnergyProfiler(true);
    Common.Session sessionPreO = Common.Session.newBuilder()
      .setSessionId(1).setStartTimestamp(FakeTimer.ONE_SECOND_IN_NS).setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS * 2).build();
    Common.SessionMetaData sessionPreOMetadata = Common.SessionMetaData.newBuilder()
      .setSessionId(1).setType(Common.SessionMetaData.SessionType.FULL).setJvmtiEnabled(false).setStartTimestampEpochMs(1).build();
    myProfilerService.addSession(sessionPreO, sessionPreOMetadata);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.getSessionsManager().setSession(sessionPreO);
    assertThat(profilers.getSessionsManager().getSelectedSessionMetaData().getJvmtiEnabled()).isFalse();

    assertThat(profilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class).inOrder();

    // When energy flag is enabled and the session is O, GetDirectStages returns Energy stage.
    Common.Session sessionO = Common.Session.newBuilder()
      .setSessionId(2).setStartTimestamp(FakeTimer.ONE_SECOND_IN_NS).setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS * 2).build();
    Common.SessionMetaData sessionOMetadata = Common.SessionMetaData.newBuilder()
      .setSessionId(2).setType(Common.SessionMetaData.SessionType.FULL).setJvmtiEnabled(true).setStartTimestampEpochMs(1).build();
    myProfilerService.addSession(sessionO, sessionOMetadata);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.getSessionsManager().setSession(sessionO);
    assertThat(profilers.getSessionsManager().getSelectedSessionMetaData().getJvmtiEnabled()).isTrue();

    assertThat(profilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class,
      EnergyProfilerStage.class).inOrder();

    // When energy flag is disabled and the session is pre-O, GetDirectStages does not return Energy stage.
    fakeServices.enableEnergyProfiler(false);
    assertThat(profilers.getSessionsManager().getSelectedSessionMetaData().getJvmtiEnabled()).isTrue();
    assertThat(profilers.getDirectStages()).containsExactly(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class).inOrder();
  }

  @Test
  public void testBuildSessionName() {
    Common.Device device1 = Common.Device.newBuilder()
                                         .setManufacturer("Manufacturer")
                                         .setModel("Model")
                                         .setSerial("Serial")
                                         .build();
    Common.Device device2 = Common.Device.newBuilder()
                                         .setModel("Model-Serial")
                                         .setSerial("Serial")
                                         .build();
    Common.Process process1 = Common.Process.newBuilder()
                                            .setPid(10)
                                            .setAbiCpuArch("x86")
                                            .setName("Process1")
                                            .build();
    Common.Process process2 = Common.Process.newBuilder()
                                            .setPid(20)
                                            .setAbiCpuArch("arm")
                                            .setName("Process2")
                                            .build();

    assertThat(StudioProfilers.buildSessionName(device1, process1)).isEqualTo("Process1 (Manufacturer Model)");
    assertThat(StudioProfilers.buildSessionName(device2, process2)).isEqualTo("Process2 (Model)");
  }

  @Test
  public void testBuildDeviceName() {
    Common.Device device = Common.Device.newBuilder()
                                        .setManufacturer("Manufacturer")
                                        .setModel("Model")
                                        .setSerial("Serial")
                                        .build();
    assertThat(StudioProfilers.buildDeviceName(device)).isEqualTo("Manufacturer Model");

    Common.Device deviceWithEmptyManufacturer = Common.Device.newBuilder()
                                                             .setModel("Model")
                                                             .setSerial("Serial")
                                                             .build();
    assertThat(StudioProfilers.buildDeviceName(deviceWithEmptyManufacturer)).isEqualTo("Model");

    Common.Device deviceWithSerialInModel = Common.Device.newBuilder()
                                                         .setManufacturer("Manufacturer")
                                                         .setModel("Model-Serial")
                                                         .setSerial("Serial")
                                                         .build();
    assertThat(StudioProfilers.buildDeviceName(deviceWithSerialInModel)).isEqualTo("Manufacturer Model");
  }

  @Test
  public void testSelectedAppNameFromSession() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess (phone)", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process);

    assertThat(profilers.getSession()).isNotEqualTo(Common.Session.getDefaultInstance());
    assertThat(profilers.getSelectedAppName()).isEqualTo("FakeProcess");
  }

  @Test
  public void testSelectedAppNameFromProcessWhenNoSession() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess (phone)", Common.Process.State.ALIVE);
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    profilers.setDevice(device);
    profilers.setProcess(process);

    profilers.getSessionsManager().setSession(Common.Session.getDefaultInstance());
    assertThat(profilers.getSelectedAppName()).isEqualTo("FakeProcess");
  }

  @Test
  public void testSelectedAppNameWhenNoProcessAndNoSession() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());
    assertThat(profilers.getProcess()).isNull();
    assertThat(profilers.getSelectedAppName()).isEmpty();
  }

  @Test
  public void testSessionViewRangeCaches() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);

    Common.Session finished_session = Common.Session.newBuilder()
                                                    .setSessionId(1).setStartTimestamp(FakeTimer.ONE_SECOND_IN_NS)
                                                    .setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS * 2).build();
    Common.SessionMetaData finished_sessin_metadata = Common.SessionMetaData.newBuilder()
                                                                            .setSessionId(1)
                                                                            .setType(Common.SessionMetaData.SessionType.FULL)
                                                                            .setStartTimestampEpochMs(1).build();
    Common.Session ongoing_session = Common.Session.newBuilder()
                                                   .setSessionId(2).setStartTimestamp(0).setEndTimestamp(Long.MAX_VALUE).build();
    Common.SessionMetaData ongoing_sessin_metadata = Common.SessionMetaData.newBuilder()
                                                                           .setSessionId(2).setType(Common.SessionMetaData.SessionType.FULL)
                                                                           .setStartTimestampEpochMs(2).build();
    myProfilerService.addSession(finished_session, finished_sessin_metadata);
    myProfilerService.addSession(ongoing_session, ongoing_sessin_metadata);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(profilers.getSession()).isEqualTo(Common.Session.getDefaultInstance());

    // Arbitrary view range min/max to be set for each session
    long viewRangeMin = TimeUnit.MILLISECONDS.toMicros(1200);
    long viewRangeMax = TimeUnit.MILLISECONDS.toMicros(1600);

    // selecting an ongoing session should use the default zoom with streaming enabled
    profilers.getSessionsManager().setSession(ongoing_session);
    assertThat(profilers.getTimeline().getViewRange().getMin()).isWithin(0).of(-ProfilerTimeline.DEFAULT_VIEW_LENGTH_US);
    assertThat(profilers.getTimeline().getViewRange().getMax()).isWithin(0).of(0);
    assertThat(profilers.getTimeline().isStreaming()).isTrue();
    assertThat(profilers.getTimeline().isPaused()).isFalse();
    profilers.getTimeline().getViewRange().set(viewRangeMin, viewRangeMax);

    // selecting a finished session without a view range cache should use the entire data range
    profilers.getSessionsManager().setSession(finished_session);
    assertThat(profilers.getTimeline().getViewRange().getMin()).isWithin(0).of(TimeUnit.SECONDS.toMicros(1));
    assertThat(profilers.getTimeline().getViewRange().getMax()).isWithin(0).of(TimeUnit.SECONDS.toMicros(2));
    assertThat(profilers.getTimeline().isStreaming()).isFalse();
    assertThat(profilers.getTimeline().isPaused()).isTrue();
    profilers.getTimeline().getViewRange().set(viewRangeMin, viewRangeMax);

    // Navigate back to the ongoing session should still use the default zoom
    profilers.getSessionsManager().setSession(ongoing_session);
    assertThat(profilers.getTimeline().getViewRange().getMin()).isWithin(0).of(-ProfilerTimeline.DEFAULT_VIEW_LENGTH_US);
    assertThat(profilers.getTimeline().getViewRange().getMax()).isWithin(0).of(0);
    assertThat(profilers.getTimeline().isStreaming()).isTrue();
    assertThat(profilers.getTimeline().isPaused()).isFalse();

    // Navigate again to the finished session should use the last view range
    profilers.getSessionsManager().setSession(finished_session);
    assertThat(profilers.getTimeline().getViewRange().getMin()).isWithin(0).of(viewRangeMin);
    assertThat(profilers.getTimeline().getViewRange().getMax()).isWithin(0).of(viewRangeMax);
    assertThat(profilers.getTimeline().isStreaming()).isFalse();
    assertThat(profilers.getTimeline().isPaused()).isTrue();

    // Arbitrarily setting the view range to something beyond the data range should force the timeline to clamp to data range.
    profilers.getTimeline().getViewRange().set(TimeUnit.SECONDS.toMicros(-10), TimeUnit.SECONDS.toMicros(10));
    profilers.getSessionsManager().setSession(Common.Session.getDefaultInstance());
    profilers.getSessionsManager().setSession(finished_session);
    assertThat(profilers.getTimeline().getViewRange().getMin()).isWithin(0).of(TimeUnit.SECONDS.toMicros(1));
    assertThat(profilers.getTimeline().getViewRange().getMax()).isWithin(0).of(TimeUnit.SECONDS.toMicros(2));
    assertThat(profilers.getTimeline().isStreaming()).isFalse();
    assertThat(profilers.getTimeline().isPaused()).isTrue();
  }

  private StudioProfilers getProfilersWithDeviceAndProcess() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcServer.getClient(), new FakeIdeProfilerServices(), timer);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    Common.Device device = createDevice(AndroidVersion.VersionCodes.BASE, "FakeDevice", Common.Device.State.ONLINE);
    myProfilerService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setDevice(device);
    assertThat(profilers.getDevice()).isEqualTo(device);
    assertThat(profilers.getProcess()).isNull();

    Common.Process process = createProcess(device.getDeviceId(), 20, "FakeProcess", Common.Process.State.ALIVE);
    myProfilerService.addProcess(device, process);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS); // One second must be enough for new devices to be picked up
    profilers.setDevice(device);
    profilers.setProcess(process);

    assertThat(profilers.getProcess()).isEqualTo(process);
    return profilers;
  }

  private static Common.Device createDevice(int featureLevel, @NotNull String serial, @NotNull Common.Device.State state) {
    return Common.Device.newBuilder()
                        .setDeviceId(serial.hashCode())
                        .setFeatureLevel(featureLevel)
                        .setSerial(serial)
                        .setState(state)
                        .build();
  }

  private Common.Process createProcess(long deviceId, int pid, @NotNull String name, Common.Process.State state) {
    return Common.Process.newBuilder()
                         .setDeviceId(deviceId)
                         .setPid(pid)
                         .setName(name)
                         .setState(state)
                         .build();
  }

  private static class AgentStatusAspectObserver extends AspectObserver {
    private int myAgentStatusChangedCount;

    void AgentStatusChanged() {
      myAgentStatusChangedCount++;
    }

    int getAgentStatusChangedCount() {
      return myAgentStatusChangedCount;
    }
  }

  private static class FakeStage extends Stage {
    private FakeStage(@NotNull StudioProfilers profilers) {
      super(profilers);
    }

    @Override
    public void enter() {
    }

    @Override
    public void exit() {
    }
  }
}
