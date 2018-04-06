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
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.Updater;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler.*;
import com.android.tools.profilers.cpu.CpuProfiler;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.energy.EnergyProfiler;
import com.android.tools.profilers.energy.EnergyProfilerStage;
import com.android.tools.profilers.event.EventProfiler;
import com.android.tools.profilers.memory.MemoryProfiler;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.network.NetworkProfiler;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.sessions.SessionsManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.text.StringUtil;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.android.tools.profiler.proto.CpuProfiler.ProfilingStateRequest;
import static com.android.tools.profiler.proto.CpuProfiler.ProfilingStateResponse;

/**
 * The suite of profilers inside Android Studio. This object is responsible for maintaining the information
 * global across all the profilers, device management, process management, current state of the tool etc.
 */
public class StudioProfilers extends AspectModel<ProfilerAspect> implements Updatable {
  /**
   * The number of updates per second our simulated object models receive.
   */
  public static final int PROFILERS_UPDATE_RATE = 60;

  private final ProfilerClient myClient;

  private final ProfilerTimeline myTimeline;

  private final List<StudioProfiler> myProfilers;

  @NotNull
  private final IdeProfilerServices myIdeServices;

  /**
   * Processes from devices come from the latest update, and are filtered to include only ALIVE ones and {@code myProcess}.
   */
  private Map<Common.Device, List<Common.Process>> myProcesses;

  @NotNull private final SessionsManager mySessionsManager;

  @Nullable
  private Common.Process myProcess;

  private AgentStatusResponse.Status myAgentStatus;

  @Nullable
  private String myPreferredDeviceName;

  @Nullable
  private String myPreferredProcessName;

  private Common.Device myDevice;

  /**
   * The session that is currently selected.
   */
  @NotNull private Common.Session mySelectedSession;

  /**
   * The session that is currently under profiling.
   */
  @NotNull private Common.Session myProfilingSession;

  @NotNull private Common.SessionMetaData mySelectedSessionMetaData;

  @NotNull
  private Stage myStage;

  private Updater myUpdater;

  private AxisComponentModel myViewAxis;

  private long myRefreshDevices;

  private final Map<Common.SessionMetaData.SessionType, Runnable> mySessionChangeListener;

  public StudioProfilers(ProfilerClient client, @NotNull IdeProfilerServices ideServices) {
    this(client, ideServices, new FpsTimer(PROFILERS_UPDATE_RATE));
  }

  @VisibleForTesting
  public StudioProfilers(ProfilerClient client, @NotNull IdeProfilerServices ideServices, @NotNull StopwatchTimer timer) {
    myClient = client;
    myIdeServices = ideServices;
    myPreferredProcessName = null;
    myStage = new NullMonitorStage(this);
    mySessionsManager = new SessionsManager(this);
    mySessionChangeListener = new HashMap<>();
    myStage.enter();

    myUpdater = new Updater(timer);
    ImmutableList.Builder<StudioProfiler> profilersBuilder = new ImmutableList.Builder<>();
    profilersBuilder.add(new EventProfiler(this));
    profilersBuilder.add(new CpuProfiler(this));
    profilersBuilder.add(new MemoryProfiler(this));
    profilersBuilder.add(new NetworkProfiler(this));
    if (myIdeServices.getFeatureConfig().isEnergyProfilerEnabled()) {
      profilersBuilder.add(new EnergyProfiler(this));
    }
    myProfilers = profilersBuilder.build();

    myTimeline = new ProfilerTimeline(myUpdater);
    myTimeline.getSelectionRange().addDependency(this).onChange(Range.Aspect.RANGE, () -> {
      if (!myTimeline.getSelectionRange().isEmpty()) {
        myTimeline.setStreaming(false);
      }
    });

    registerSessionChangeListener(Common.SessionMetaData.SessionType.FULL, () -> {
      setStage(new StudioMonitorStage(this));
      if (isSessionAlive(mySelectedSession)) {
        // The session is live - move the timeline to the current time.
        TimeResponse timeResponse = myClient.getProfilerClient()
                                            .getCurrentTime(TimeRequest.newBuilder().setDeviceId(mySelectedSession.getDeviceId()).build());

        myTimeline.reset(mySelectedSession.getStartTimestamp(), timeResponse.getTimestampNs());
        if (startupCpuProfilingStarted()) {
          setStage(new CpuProfilerStage(this));
        }
      }
      else {
        // The session is finished, reset the timeline to include the entire data range.
        myTimeline.reset(mySelectedSession.getStartTimestamp(), mySelectedSession.getEndTimestamp());
        // Disable data range update and stream/snap features.
        myTimeline.setIsPaused(true);
        myTimeline.setStreaming(false);
        myTimeline.getViewRange().set(mySessionsManager.getSessionPreferredViewRange(mySelectedSession));
      }

      // Profilers can query data depending on whether the agent is set. Even though we set the status above, delay until after the session
      // is properly assigned before firing this aspect change.
      changed(ProfilerAspect.AGENT);
    });

    myProcesses = Maps.newHashMap();
    myDevice = null;
    myProcess = null;

    // TODO: StudioProfilers initalizes with a default session, which a lot of tests now relies on to avoid a NPE.
    // We should clean all the tests up to either have StudioProfilers create a proper session first or handle the null cases better.
    mySelectedSession = myProfilingSession = Common.Session.getDefaultInstance();
    mySelectedSessionMetaData = Common.SessionMetaData.getDefaultInstance();
    mySessionsManager.addDependency(this)
                     .onChange(SessionAspect.SELECTED_SESSION, this::selectedSessionChanged)
                     .onChange(SessionAspect.PROFILING_SESSION, this::profilingSessionChanged);

    myViewAxis = new AxisComponentModel(myTimeline.getViewRange(), TimeAxisFormatter.DEFAULT);
    myViewAxis.setGlobalRange(myTimeline.getDataRange());

    myUpdater.register(myViewAxis);
    myUpdater.register(this);
  }

  public boolean isStopped() {
    return !myUpdater.isRunning();
  }

  public void stop() {
    if (isStopped()) {
      // Profiler is already stopped. Nothing to do. Ideally, this method shouldn't be called when the profiler is already stopped.
      // However, some exceptions might be thrown when listeners are notified about ProfilerAspect.STAGE aspect change and react
      // accordingly. In this case, we could end up with an inconsistent model and allowing to try to call stop and notify the listeners
      // again can only make it worse. Therefore, we return early to avoid making the model problem bigger.
      return;
    }
    // The following line can't throw an exception, will stop the updater's timer and guarantees future calls to isStopped() return true.
    myUpdater.stop();
    // The following lines trigger aspect changes and, therefore, can make many models to update. That might cause an exception to be thrown
    // and make some models inconsistent. In this case, we want future calls to this method to return early, as we can only make the
    // inconsistency worse if we call these lines again.
    setDevice(null);
    changed(ProfilerAspect.STAGE);
  }

  public Map<Common.Device, List<Common.Process>> getDeviceProcessMap() {
    return myProcesses;
  }

  public List<Common.Device> getDevices() {
    return Lists.newArrayList(myProcesses.keySet());
  }

  /**
   * Tells the profiler to select and profile the device of the same name next time it is detected.
   */
  public void setPreferredDeviceName(@Nullable String name) {
    myPreferredDeviceName = name;
    // Checks whether we can switch immediately if the device is already there.
    setDevice(findPreferredDevice());
  }

  /**
   * Tells the profiler to select and profile the process of the same name next time it is detected.
   */
  public void setPreferredProcessName(@Nullable String name) {
    myPreferredProcessName = name;
    // Checks whether we can switch immediately if the process is already there.
    setProcess(null);
  }

  @Override
  public void update(long elapsedNs) {
    myRefreshDevices += elapsedNs;
    if (myRefreshDevices < TimeUnit.SECONDS.toNanos(1)) {
      return;
    }
    myRefreshDevices = 0;

    try {
      GetDevicesResponse response = myClient.getProfilerClient().getDevices(GetDevicesRequest.getDefaultInstance());
      Set<Common.Device> devices = new HashSet<>(response.getDeviceList());
      Map<Common.Device, List<Common.Process>> newProcesses = new HashMap<>();
      for (Common.Device device : devices) {
        GetProcessesRequest request = GetProcessesRequest.newBuilder().setDeviceId(device.getDeviceId()).build();
        GetProcessesResponse processes = myClient.getProfilerClient().getProcesses(request);

        int lastProcessId = myProcess == null ? 0 : myProcess.getPid();
        List<Common.Process> processList = processes.getProcessList()
                                                    .stream()
                                                    .filter(process -> process.getState() == Common.Process.State.ALIVE ||
                                                                       process.getPid() == lastProcessId)
                                                    .collect(Collectors.toList());

        newProcesses.put(device, processList);
      }

      if (!newProcesses.equals(myProcesses)) {
        myProcesses = newProcesses;
        // Find and set preferred device
        setDevice(findPreferredDevice());
        setProcess(null);

        // These need to be fired every time the process list changes so that the device/process dropdown always reflects the latest.
        changed(ProfilerAspect.DEVICES);
        changed(ProfilerAspect.PROCESSES);
      }

      mySessionsManager.update();

      // A heartbeat event may not have been sent by perfa when we first profile an app, here we keep pinging the status and
      // fire the corresponding change and tracking events.
      if (isSessionAlive(mySelectedSession)) {
        AgentStatusResponse.Status agentStatus = getAgentStatus(mySelectedSession);
        if (myAgentStatus != agentStatus) {
          myAgentStatus = agentStatus;
          changed(ProfilerAspect.AGENT);
          if (myAgentStatus == AgentStatusResponse.Status.ATTACHED) {
            getIdeServices().getFeatureTracker().trackAdvancedProfilingStarted();
          }
        }
      }
    }
    catch (StatusRuntimeException e) {
      // TODO: Clean up this exception, this has the potential to capture some subtle bugs
      // As an example the MemoryProfilerStateTest:testAgentStatusUpdatesObjectSeries depends on this exception being thrown
      // the exception gets thrown due to startMonitor being called on a service the test didn't setup, this seems like an
      // unintentional side effect of the state of the test that sets this class up properly, if we handle the exception elsewhere
      // the test will fail as a different service will run and an UNIMPLEMENTED exception will be thrown.
      System.err.println("Cannot find profiler service, retrying...");
    }
  }

  /**
   * Finds and returns the preferred device if there is an online device with a matching name. Otherwise, we attempt to maintain the
   * currently selected device. Otherwise if no preferred device is specified, return any device that has live processes in it.
   */
  @Nullable
  private Common.Device findPreferredDevice() {
    Set<Common.Device> devices = myProcesses.keySet();
    Set<Common.Device> onlineDevices =
      devices.stream().filter(device -> device.getState().equals(Common.Device.State.ONLINE)).collect(Collectors.toSet());

    // We have a preferred device, try not to select anything else.
    if (myPreferredDeviceName != null) {
      for (Common.Device device : onlineDevices) {
        if (myPreferredDeviceName.equals(buildDeviceName(device))) {
          return device;
        }
      }
    }

    // Next, prefer the device currently used, either selected by user or automatically
    if (myDevice != null) {
      for (Common.Device device : devices) {
        if (myDevice.getDeviceId() == device.getDeviceId()) {
          return device;
        }
      }
    }

    // No preferred candidate. Choose any device that has online processes
    Common.Device anyDevice = devices.isEmpty() ? null : devices.iterator().next();
    return myPreferredDeviceName == null ?
           devices.stream().filter(this::deviceHasAliveProcesses).findAny().orElse(anyDevice) : null;
  }

  private boolean deviceHasAliveProcesses(@NotNull Common.Device device) {
    if (!device.getState().equals(Common.Device.State.ONLINE)) {
      return false;
    }

    List<Common.Process> deviceProcesses = myProcesses.get(device);
    if (deviceProcesses == null) {
      return false;
    }
    for (Common.Process process : deviceProcesses) {
      if (process.getState().equals(Common.Process.State.ALIVE)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Chooses the given device.
   */
  public void setDevice(@Nullable Common.Device device) {
    if (device != null) {
      // Device can be not null in the following scenarios:
      // 1. User explicitly sets a device from the dropdown.
      // 2. The update loop has found the preferred device, in which case it will stay selected until the user selects something else.
      // 3. There was no preferred device and the update loop found a device with live processes.
      // All of these cases mean that we can unset the preferred device.
      myPreferredDeviceName = null;
    }

    if (!Objects.equals(device, myDevice)) {
      // The device has changed and we need to reset the process.
      // First, end the current session on the previous device.
      mySessionsManager.endCurrentSession();
      myDevice = device;
      changed(ProfilerAspect.DEVICES);

      // Then set a new process.
      setProcess(null);
    }
  }

  public void setMonitoringStage() {
    setStage(new StudioMonitorStage(this));
  }


  /**
   * Return the meta data of current selected session
   */
  @NotNull
  public Common.SessionMetaData getSelectedSessionMetaData() {
    return mySelectedSessionMetaData;
  }

  /**
   * Chooses a process, and starts profiling it if not already (and stops profiling the previous one).
   *
   * @param process the process that will be selected. If it is null, a process will be determined
   *                automatically by heuristics.
   */
  public void setProcess(@Nullable Common.Process process) {
    List<Common.Process> processes = myProcesses.get(myDevice);
    if (process == null || processes == null || !processes.contains(process)) {
      process = getPreferredProcess(processes);
    }
    else {
      // The user wants to select a different process explicitly.
      // If the user intentionally selects something else, the profiler should not switch
      // back to the preferred process in any cases.
      myPreferredProcessName = null;
    }

    if (!Objects.equals(process, myProcess)) {
      // First make sure to end the previous session.
      mySessionsManager.endCurrentSession();

      myProcess = process;
      changed(ProfilerAspect.PROCESSES);
      mySessionsManager.beginSession(myDevice, myProcess);
    }
  }

  /**
   * Register the listener to set proper stage when a new session is selected.
   *
   * @param sessionType type of the new session.
   * @param listener    listener to register.
   */
  public void registerSessionChangeListener(Common.SessionMetaData.SessionType sessionType, Runnable listener) {
    mySessionChangeListener.put(sessionType, listener);
  }

  private void selectedSessionChanged() {
    Common.Session newSession = mySessionsManager.getSelectedSession();

    // The current selected session has not changed but it has gone from live to finished, simply pause the timeline.
    if (mySelectedSession.getSessionId() == newSession.getSessionId() &&
        isSessionAlive(mySelectedSession) && !isSessionAlive(newSession)) {
      mySelectedSession = newSession;
      myTimeline.setIsPaused(true);
      return;
    }

    mySelectedSession = newSession;
    mySelectedSessionMetaData = myClient.getProfilerClient()
                                        .getSessionMetaData(
                                          GetSessionMetaDataRequest.newBuilder().setSessionId(mySelectedSession.getSessionId()).build())
                                        .getData();
    myAgentStatus = getAgentStatus(mySelectedSession);
    if (Common.Session.getDefaultInstance().equals(newSession)) {
      // No selected session - go to the null stage.
      myTimeline.setIsPaused(true);
      setStage(new NullMonitorStage(this));
      return;
    }

    // Set the stage base on session type
    Common.SessionMetaData.SessionType sessionType = mySelectedSessionMetaData.getType();
    assert mySessionChangeListener.containsKey(sessionType);
    mySessionChangeListener.get(sessionType).run();
  }

  private void profilingSessionChanged() {
    Common.Session newSession = mySessionsManager.getProfilingSession();
    // Stops the previous profiling session if it is active
    if (!Common.Session.getDefaultInstance().equals(myProfilingSession)) {
      assert isSessionAlive(myProfilingSession);
      myProfilers.forEach(profiler -> profiler.stopProfiling(myProfilingSession));
    }

    myProfilingSession = newSession;

    if (!Common.Session.getDefaultInstance().equals(myProfilingSession)) {
      assert isSessionAlive(myProfilingSession);
      myProfilers.forEach(profiler -> profiler.startProfiling(myProfilingSession));
      myIdeServices.getFeatureTracker().trackProfilingStarted();
      if (getAgentStatus(myProfilingSession) == AgentStatusResponse.Status.ATTACHED) {
        getIdeServices().getFeatureTracker().trackAdvancedProfilingStarted();
      }
    }
  }

  /**
   * Checks whether startup CPU Profiling started for the selected session by making RPC call to perfd.
   */
  private boolean startupCpuProfilingStarted() {
    if (!getIdeServices().getFeatureConfig().isStartupCpuProfilingEnabled()) {
      return false;
    }

    ProfilingStateResponse response = getClient().getCpuClient()
                                                 .checkAppProfilingState(
                                                   ProfilingStateRequest.newBuilder().setSession(mySelectedSession).build());

    return response.getBeingProfiled() && response.getIsStartupProfiling();
  }

  @NotNull
  public String getSessionDisplayName() {
    if (Common.Session.getDefaultInstance().equals(mySelectedSession)) {
      return "";
    }

    return mySelectedSessionMetaData.getSessionName();
  }

  /**
   * Chooses a process among all potential candidates starting from the project's app process, and then the one previously used. If no
   * candidate is available and no preferred process has been configured, select the first available process.
   */
  @Nullable
  private Common.Process getPreferredProcess(List<Common.Process> processes) {
    if (processes == null || processes.isEmpty()) {
      return null;
    }
    // Prefer the project's app if available.
    if (myPreferredProcessName != null) {
      for (Common.Process process : processes) {
        if (process.getName().equals(myPreferredProcessName) && process.getState() == Common.Process.State.ALIVE) {
          return process;
        }
      }
    }

    // Next, prefer the one previously used, either selected by user or automatically (even if the process has switched states)
    if (myProcess != null) {
      for (Common.Process process : processes) {
        if (isSameProcess(myProcess, process)) {
          return process;
        }
      }
    }

    // No preferred candidate. Choose a new process if we are not already waiting for the preferred process.
    return myPreferredProcessName == null ? processes.get(0) : null;
  }

  @NotNull
  private AgentStatusResponse.Status getAgentStatus(@NotNull Common.Session session) {
    if (Common.Session.getDefaultInstance().equals(session)) {
      return AgentStatusResponse.getDefaultInstance().getStatus();
    }

    AgentStatusRequest statusRequest =
      AgentStatusRequest.newBuilder().setPid(session.getPid()).setDeviceId(session.getDeviceId()).build();
    return myClient.getProfilerClient().getAgentStatus(statusRequest).getStatus();
  }

  /**
   * @return true if the processes are the same, false otherwise. The reason Objects.equals is not used here is because the states could
   * have changed between process1 and process2, but they should be considered the same as long as we have matching pids and names, so we
   * don't reset the stage.
   */
  private static boolean isSameProcess(@Nullable Common.Process process1, @Nullable Common.Process process2) {
    return process1 != null &&
           process2 != null &&
           process1.getPid() == process2.getPid() && process1.getName().equals(process2.getName());
  }

  public List<Common.Process> getProcesses() {
    List<Common.Process> processes = myProcesses.get(myDevice);
    return processes == null ? ImmutableList.of() : processes;
  }

  @NotNull
  public Stage getStage() {
    return myStage;
  }

  public ProfilerClient getClient() {
    return myClient;
  }

  @NotNull
  public SessionsManager getSessionsManager() {
    return mySessionsManager;
  }

  /**
   * @return the active session, otherwise {@link Common.Session#getDefaultInstance()} if no session is currently being profiled.
   */
  @NotNull
  public Common.Session getSession() {
    return mySelectedSession;
  }

  public void setStage(@NotNull Stage stage) {
    myStage.exit();
    getTimeline().getSelectionRange().clear();
    myStage = stage;
    myStage.enter();
    this.changed(ProfilerAspect.STAGE);
  }

  @NotNull
  public ProfilerTimeline getTimeline() {
    return myTimeline;
  }

  @Nullable
  public Common.Device getDevice() {
    return myDevice;
  }

  @Nullable
  public Common.Process getProcess() {
    return myProcess;
  }

  private boolean isSessionAlive(@NotNull Common.Session session) {
    return session.getEndTimestamp() == Long.MAX_VALUE;
  }

  public boolean isAgentAttached() {
    return myAgentStatus == AgentStatusResponse.Status.ATTACHED;
  }

  public List<StudioProfiler> getProfilers() {
    return myProfilers;
  }

  public ProfilerMode getMode() {
    return myStage.getProfilerMode();
  }

  public void modeChanged() {
    changed(ProfilerAspect.MODE);
  }

  @NotNull
  public IdeProfilerServices getIdeServices() {
    return myIdeServices;
  }

  public Updater getUpdater() {
    return myUpdater;
  }

  public AxisComponentModel getViewAxis() {
    return myViewAxis;
  }

  /**
   * Return the list of stages that target a specific profiler, which a user might want to jump
   * between. This should exclude things like the top-level profiler stage, null stage, etc.
   */
  public List<Class<? extends Stage>> getDirectStages() {
    ImmutableList.Builder<Class<? extends Stage>> listBuilder = ImmutableList.builder();
    listBuilder.add(CpuProfilerStage.class);
    listBuilder.add(MemoryProfilerStage.class);
    listBuilder.add(NetworkProfilerStage.class);
    // Show the energy stage in the list only when the session has JVMTI enabled or the device is above O.
    boolean hasSession = mySelectedSession.getSessionId() != 0;
    boolean isEnergyStageEnabled = hasSession ? mySelectedSessionMetaData.getJvmtiEnabled()
                                              : myDevice != null && myDevice.getFeatureLevel() >= AndroidVersion.VersionCodes.O;
    if (getIdeServices().getFeatureConfig().isEnergyProfilerEnabled() && isEnergyStageEnabled) {
      listBuilder.add(EnergyProfilerStage.class);
    }
    return listBuilder.build();
  }

  @NotNull
  public Class<? extends Stage> getStageClass() {
    return myStage.getClass();
  }

  // TODO: Unify with how monitors expand.
  public void setNewStage(Class<? extends Stage> clazz) {
    try {
      Constructor<? extends Stage> constructor = clazz.getConstructor(StudioProfilers.class);
      Stage stage = constructor.newInstance(this);
      setStage(stage);
    }
    catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
      // will not happen
    }
  }

  @NotNull
  public static String buildSessionName(@NotNull Common.Device device, @NotNull Common.Process process) {
    return String.format("%s (%s)", process.getName(), buildDeviceName(device));
  }

  /**
   * Mirrors AndroidProfilerToolWindow#getDeviceDisplayName but works with a {@link Common.Device}.
   */
  @NotNull
  public static String buildDeviceName(@NotNull Common.Device device) {
    StringBuilder deviceNameBuilder = new StringBuilder();
    String manufacturer = device.getManufacturer();
    String model = device.getModel();
    String serial = device.getSerial();
    String suffix = String.format("-%s", serial);
    if (model.endsWith(suffix)) {
      model = model.substring(0, model.length() - suffix.length());
    }
    if (!StringUtil.isEmpty(manufacturer)) {
      deviceNameBuilder.append(manufacturer);
      deviceNameBuilder.append(" ");
    }
    deviceNameBuilder.append(model);

    return deviceNameBuilder.toString();
  }
}
