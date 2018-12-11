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

import static com.google.common.truth.Truth.assertThat;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler.AgentStatusRequest;
import com.android.tools.profiler.proto.Profiler.AgentStatusResponse;
import com.android.tools.profiler.proto.Profiler.BeginSessionRequest;
import com.android.tools.profiler.proto.Profiler.BeginSessionResponse;
import com.android.tools.profiler.proto.Profiler.BytesRequest;
import com.android.tools.profiler.proto.Profiler.BytesResponse;
import com.android.tools.profiler.proto.Profiler.Command;
import com.android.tools.profiler.proto.Profiler.DeleteSessionRequest;
import com.android.tools.profiler.proto.Profiler.DeleteSessionResponse;
import com.android.tools.profiler.proto.Profiler.EndSessionRequest;
import com.android.tools.profiler.proto.Profiler.EndSessionResponse;
import com.android.tools.profiler.proto.Profiler.EventGroup;
import com.android.tools.profiler.proto.Profiler.ExecuteRequest;
import com.android.tools.profiler.proto.Profiler.ExecuteResponse;
import com.android.tools.profiler.proto.Profiler.GetDevicesRequest;
import com.android.tools.profiler.proto.Profiler.GetDevicesResponse;
import com.android.tools.profiler.proto.Profiler.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Profiler.GetEventGroupsResponse;
import com.android.tools.profiler.proto.Profiler.GetProcessesRequest;
import com.android.tools.profiler.proto.Profiler.GetProcessesResponse;
import com.android.tools.profiler.proto.Profiler.GetSessionMetaDataRequest;
import com.android.tools.profiler.proto.Profiler.GetSessionMetaDataResponse;
import com.android.tools.profiler.proto.Profiler.GetSessionsRequest;
import com.android.tools.profiler.proto.Profiler.GetSessionsResponse;
import com.android.tools.profiler.proto.Profiler.ImportSessionRequest;
import com.android.tools.profiler.proto.Profiler.ImportSessionResponse;
import com.android.tools.profiler.proto.Profiler.TimeRequest;
import com.android.tools.profiler.proto.Profiler.TimeResponse;
import com.android.tools.profiler.proto.Profiler.VersionRequest;
import com.android.tools.profiler.proto.Profiler.VersionResponse;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.commands.BeginSession;
import com.android.tools.profilers.commands.CommandHandler;
import com.android.tools.profilers.commands.EndSession;
import com.intellij.util.containers.MultiMap;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public final class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {
  public static final String VERSION = "3141592";
  public static final long FAKE_DEVICE_ID = 1234;
  public static final String FAKE_DEVICE_NAME = "FakeDevice";
  public static final String FAKE_PROCESS_NAME = "FakeProcess";
  // This value is the value used when we get a GRPC request but an int / long field has not been set.
  private static final int EMPTY_REQUEST_VALUE = 0;
  public static final Common.Device FAKE_DEVICE = Common.Device.newBuilder()
    .setDeviceId(FAKE_DEVICE_ID)
    .setSerial(FAKE_DEVICE_NAME)
    .setApiLevel(AndroidVersion.VersionCodes.O)
    .setFeatureLevel(AndroidVersion.VersionCodes.O)
    .setModel(FAKE_DEVICE_NAME)
    .setState(Common.Device.State.ONLINE)
    .build();
  //Setting PID to be 1 since there is a process with pid being 1 in test input atrace_processid_1
  public static final Common.Process FAKE_PROCESS = Common.Process.newBuilder()
    .setPid(1)
    .setDeviceId(FAKE_DEVICE_ID)
    .setState(Common.Process.State.ALIVE)
    .setName(FAKE_PROCESS_NAME)
    .build();

  private final Map<Long, Common.Device> myDevices;
  private final MultiMap<Common.Device, Common.Process> myProcesses;
  private final Map<String, ByteString> myCache;
  private final Map<Long, Common.Session> mySessions;
  private final Map<Long, Common.SessionMetaData> mySessionMetaDatas;
  private final Map<Long, List<EventGroup.Builder>> myStreamEvents;
  private final Map<Command.CommandType, CommandHandler> myCommandHandlers;
  private final FakeTimer myCommandTimer = new FakeTimer();
  private long myTimestampNs;
  private boolean myThrowErrorOnGetDevices;
  private boolean myAttachAgentCalled;
  private AgentStatusResponse myAgentStatus;

  private Common.SessionMetaData.SessionType myLastImportedSessionType;

  public FakeProfilerService() {
    this(true);
  }

  public void reset() {
    myAttachAgentCalled = false;
  }

  /**
   * Creates a fake profiler service. If connected is true there will be a device with a process already present.
   */
  public FakeProfilerService(boolean connected) {
    myDevices = new HashMap<>();
    myProcesses = MultiMap.create();
    myCache = new HashMap<>();
    mySessions = new HashMap<>();
    mySessionMetaDatas = new HashMap<>();
    myStreamEvents = new HashMap<>();
    myCommandHandlers = new HashMap<>();
    if (connected) {
      addDevice(FAKE_DEVICE);
      addProcess(FAKE_DEVICE, FAKE_PROCESS);
    }
    initializeCommandHandlers();
  }

  /**
   * This method creates any command handlers needed by test, for more information see {@link CommandHandler}.
   */
  private void initializeCommandHandlers() {
    setCommandHandler(Command.CommandType.BEGIN_SESSION, new BeginSession(myCommandTimer));
    setCommandHandler(Command.CommandType.END_SESSION, new EndSession(myCommandTimer));
  }

  /**
   * Allow test to overload specific command handles if they need to generate customized data.
   */
  public void setCommandHandler(Command.CommandType type, CommandHandler handler) {
    myCommandHandlers.put(type, handler);
  }

  /**
   * The command timer is a timer shared amongst all command handlers allowing test to control timing of events generated.
   */
  public FakeTimer getCommandTimer() {
    return myCommandTimer;
  }

  public void addProcess(Common.Device device, Common.Process process) {
    if (!myDevices.containsKey(device.getDeviceId())) {
      throw new IllegalArgumentException("Invalid device: " + device.getDeviceId());
    }
    assert device.getDeviceId() == process.getDeviceId();
    myProcesses.putValue(myDevices.get(device.getDeviceId()), process);
    // The event pipeline expects process started / ended events. As such depending on the process state when passed in we add such events.
    if (process.getState() == Common.Process.State.ALIVE) {
      addEventToEventGroup(device.getDeviceId(), process.getPid(), Common.Event.newBuilder()
        .setTimestamp(myCommandTimer.getCurrentTimeNs())
        .setKind(Common.Event.Kind.PROCESS)
        .setProcess(Common.ProcessData.newBuilder()
                      .setProcessStarted(Common.ProcessData.ProcessStarted.newBuilder()
                                           .setProcess(process)))
        .build());
    }
    if (process.getState() == Common.Process.State.DEAD) {
      addEventToEventGroup(device.getDeviceId(), process.getPid(), Common.Event.newBuilder()
        .setTimestamp(myCommandTimer.getCurrentTimeNs())
        .setKind(Common.Event.Kind.PROCESS)
        .setIsEnded(true)
        .build());
    }
  }

  public void removeProcess(Common.Device device, Common.Process process) {
    if (!myDevices.containsKey(device.getDeviceId())) {
      throw new IllegalArgumentException("Invalid device: " + device);
    }
    assert device.getDeviceId() == process.getDeviceId();
    myProcesses.remove(myDevices.get(device.getDeviceId()), process);
    // The event pipeline doesn't delete data so this fucntion is a no-op.
  }

  public void addDevice(Common.Device device) {
    myDevices.put(device.getDeviceId(), device);
    // The event pipeline expects devices are connected via streams. So when a new devices is added we create a stream connected event.
    // likewise when a device is taken offline we create a stream disconnected event.
    if (device.getState() == Common.Device.State.ONLINE) {
      addEventToEventGroup(device.getDeviceId(), device.getDeviceId(), Common.Event.newBuilder()
        .setTimestamp(myCommandTimer.getCurrentTimeNs())
        .setKind(Common.Event.Kind.STREAM)
        .setStream(Common.StreamData.newBuilder()
                     .setStreamConnected(Common.StreamData.StreamConnected.newBuilder()
                                           .setStream(Common.Stream.newBuilder()
                                                        .setType(Common.Stream.Type.DEVICE)
                                                        .setStreamId(device.getDeviceId())
                                                        .setDevice(device))))
        .build());
    }
    if (device.getState() == Common.Device.State.OFFLINE || device.getState() == Common.Device.State.DISCONNECTED) {
      addEventToEventGroup(device.getDeviceId(), device.getDeviceId(), Common.Event.newBuilder()
        .setTimestamp(myCommandTimer.getCurrentTimeNs())
        .setKind(Common.Event.Kind.STREAM)
        .setIsEnded(true)
        .build());
    }
  }

  public void updateDevice(Common.Device oldDevice, Common.Device newDevice) {
    // Move processes from old to new device
    myProcesses.putValues(newDevice, myProcesses.get(oldDevice));
    // Remove old device from processes map.
    myProcesses.remove(oldDevice);
    // Update device on devices map
    myDevices.remove(oldDevice.getDeviceId());
    // Update device simply kills the old device and swaps it with a new device. As such we kill the old device by creating a
    // stream disconnected event for the events pipeline.
    addEventToEventGroup(oldDevice.getDeviceId(), oldDevice.getDeviceId(), Common.Event.newBuilder()
      .setTimestamp(myCommandTimer.getCurrentTimeNs())
      .setKind(Common.Event.Kind.STREAM)
      .setIsEnded(true)
      .build());
    addDevice(newDevice);
  }

  /**
   * This is a helper function for test instead of creating a session via the being session command, a session is crafted and passed to
   * this function. The events pipeline crafts the proper session events to determine the life of a session. After this function is called
   * a poll will need to happen to get the latest session state.
   */
  public void addSession(Common.Session session, Common.SessionMetaData metadata) {
    mySessions.put(session.getSessionId(), session);
    mySessionMetaDatas.put(session.getSessionId(), metadata);
    addEventToEventGroup(session.getDeviceId(), session.getSessionId(), Common.Event.newBuilder()
      .setGroupId(session.getSessionId())
      .setSessionId(session.getSessionId())
      .setKind(Common.Event.Kind.SESSION)
      .setTimestamp(session.getStartTimestamp())
      .setSession(Common.SessionData.newBuilder()
                    .setSessionStarted(Common.SessionData.SessionStarted.newBuilder()
                                         .setPid(session.getPid())
                                         .setStartTimestampEpochMs(metadata.getStartTimestampEpochMs())
                                         .setJvmtiEnabled(metadata.getJvmtiEnabled())
                                         .setSessionName(metadata.getSessionName())
                                         .setType(Common.SessionData.SessionStarted.SessionType.FULL)))
      .build());
    if (session.getEndTimestamp() != Long.MAX_VALUE) {
      addEventToEventGroup(session.getDeviceId(), session.getSessionId(), Common.Event.newBuilder()
        .setGroupId(session.getSessionId())
        .setSessionId(session.getSessionId())
        .setKind(Common.Event.Kind.SESSION)
        .setIsEnded(true)
        .setTimestamp(session.getEndTimestamp())
        .build());
    }
  }

  public void addFile(String id, ByteString contents) {
    myCache.put(id, contents);
  }

  public void setTimestampNs(long timestamp) {
    myTimestampNs = timestamp;
    myCommandTimer.setCurrentTimeNs(timestamp);
  }

  @Override
  public void getVersion(VersionRequest request, StreamObserver<VersionResponse> responseObserver) {
    responseObserver.onNext(VersionResponse.newBuilder().setVersion(VERSION).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getDevices(GetDevicesRequest request, StreamObserver<GetDevicesResponse> responseObserver) {
    if (myThrowErrorOnGetDevices) {
      responseObserver.onError(new RuntimeException("Server error"));
      return;
    }
    GetDevicesResponse.Builder response = GetDevicesResponse.newBuilder();
    for (Common.Device device : myDevices.values()) {
      response.addDevice(device);
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getProcesses(GetProcessesRequest request, StreamObserver<GetProcessesResponse> responseObserver) {
    GetProcessesResponse.Builder response = GetProcessesResponse.newBuilder();
    Common.Device device = myDevices.get(request.getDeviceId());
    if (device != null) {
      for (Common.Process process : myProcesses.get(device)) {
        response.addProcess(process);
      }
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getCurrentTime(TimeRequest request, StreamObserver<TimeResponse> responseObserver) {
    TimeResponse.Builder response = TimeResponse.newBuilder();
    response.setTimestampNs(myTimestampNs);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getBytes(BytesRequest request, StreamObserver<BytesResponse> responseObserver) {
    BytesResponse.Builder builder = BytesResponse.newBuilder();
    ByteString bytes = myCache.get(request.getId());
    if (bytes != null) {
      builder.setContents(bytes);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void beginSession(BeginSessionRequest request, StreamObserver<BeginSessionResponse> responseObserver) {
    BeginSessionResponse.Builder builder = BeginSessionResponse.newBuilder();
    long sessionId = request.getDeviceId() ^ request.getPid();
    Common.Session session = Common.Session.newBuilder()
      .setSessionId(sessionId)
      .setDeviceId(request.getDeviceId())
      .setPid(request.getPid())
      .setStartTimestamp(myTimestampNs)
      .setEndTimestamp(Long.MAX_VALUE)
      .build();
    Common.SessionMetaData metadata = Common.SessionMetaData.newBuilder()
      .setSessionId(sessionId)
      .setSessionName(request.getSessionName())
      .setStartTimestampEpochMs(request.getRequestTimeEpochMs())
      .setJvmtiEnabled(request.getJvmtiConfig().getAttachAgent())
      .setLiveAllocationEnabled(request.getJvmtiConfig().getLiveAllocationEnabled())
      .setType(Common.SessionMetaData.SessionType.FULL)
      .build();
    mySessions.put(sessionId, session);
    mySessionMetaDatas.put(sessionId, metadata);
    myAttachAgentCalled = request.getJvmtiConfig().getAttachAgent();
    builder.setSession(session);
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void importSession(ImportSessionRequest request, StreamObserver<ImportSessionResponse> responseObserver) {
    mySessions.put(request.getSession().getSessionId(), request.getSession());

    Common.Session session = request.getSession();
    long sessionId = session.getSessionId();
    Common.SessionMetaData metadata = Common.SessionMetaData.newBuilder()
      .setSessionId(sessionId)
      .setSessionName(request.getSessionName())
      .setJvmtiEnabled(false)
      .setLiveAllocationEnabled(false)
      .setType(request.getSessionType())
      .build();
    mySessionMetaDatas.put(sessionId, metadata);
    myAttachAgentCalled = false;
    myLastImportedSessionType = request.getSessionType();
    responseObserver.onNext(ImportSessionResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void endSession(EndSessionRequest request, StreamObserver<EndSessionResponse> responseObserver) {
    assert (mySessions.containsKey(request.getSessionId()));
    Common.Session session = mySessions.get(request.getSessionId());
    // Set an arbitrary end time that is not Long.MAX_VALUE, which is reserved for indicating a session is ongoing.
    // If our session has not already ended we set an end timestamp.
    if (session.getEndTimestamp() == Long.MAX_VALUE) {
      session = session.toBuilder()
        .setEndTimestamp(session.getStartTimestamp() + 1)
        .build();
    }
    mySessions.put(session.getSessionId(), session);
    EndSessionResponse.Builder builder = EndSessionResponse.newBuilder().setSession(session);
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getSessions(GetSessionsRequest request, StreamObserver<GetSessionsResponse> responseObserver) {
    GetSessionsResponse response = GetSessionsResponse.newBuilder()
      .addAllSessions(mySessions.values())
      .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getSessionMetaData(GetSessionMetaDataRequest request,
                                 StreamObserver<GetSessionMetaDataResponse> responseObserver) {
    if (mySessionMetaDatas.containsKey(request.getSessionId())) {
      responseObserver.onNext(GetSessionMetaDataResponse.newBuilder().setData(mySessionMetaDatas.get(request.getSessionId())).build());
    }
    else {
      responseObserver.onNext(GetSessionMetaDataResponse.getDefaultInstance());
    }
    responseObserver.onCompleted();
  }

  @Override
  public void deleteSession(DeleteSessionRequest request, StreamObserver<DeleteSessionResponse> responseObserver) {
    mySessions.remove(request.getSessionId());
    mySessionMetaDatas.remove(request.getSessionId());
    responseObserver.onNext(DeleteSessionResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void getAgentStatus(AgentStatusRequest request, StreamObserver<AgentStatusResponse> responseObserver) {
    responseObserver.onNext(myAgentStatus != null ? myAgentStatus : AgentStatusResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  public void setAgentStatus(@NotNull AgentStatusResponse status) {
    myAgentStatus = status;
  }

  public void setThrowErrorOnGetDevices(boolean throwErrorOnGetDevices) {
    myThrowErrorOnGetDevices = throwErrorOnGetDevices;
  }

  public boolean getAgentAttachCalled() {
    return myAttachAgentCalled || ((BeginSession)myCommandHandlers.get(Command.CommandType.BEGIN_SESSION)).getAgentAttachCalled();
  }

  public Common.SessionMetaData.SessionType getLastImportedSessionType() {
    return myLastImportedSessionType;
  }

  /**
   * Helper method for finding an existing event group and updating its array of events, or creating an event group if one does not exist.
   */
  public void addEventToEventGroup(long streamId, long groupId, Common.Event event) {
    List<EventGroup.Builder> groups = getListForStream(streamId);
    Optional<EventGroup.Builder> eventGroup = groups.stream().filter(group -> group.getGroupId() == groupId).findFirst();
    if (eventGroup.isPresent()) {
      eventGroup.get().addEvents(event);
    }
    else {
      groups.add(EventGroup.newBuilder().setGroupId(groupId).addEvents(event));
    }
  }

  /**
   * Helper method for creating a list of event groups if one does not exist, otherwise returning the existing group.
   */
  private List<EventGroup.Builder> getListForStream(long streamId) {
    if (!myStreamEvents.containsKey(streamId)) {
      myStreamEvents.put(streamId, new ArrayList<>());
    }
    return myStreamEvents.get(streamId);
  }

  @Override
  public void execute(ExecuteRequest request, StreamObserver<ExecuteResponse> responseObserver) {
    assertThat(myCommandHandlers.containsKey(request.getCommand().getType()))
      .named("Missing command handler for: %s", request.getCommand().getType().toString()).isTrue();
    myCommandHandlers.get(request.getCommand().getType())
      .handleCommand(request.getCommand(), getListForStream(request.getCommand().getStreamId()));
    responseObserver.onNext(ExecuteResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void getEventGroups(GetEventGroupsRequest request, StreamObserver<GetEventGroupsResponse> responseObserver) {
    if (myThrowErrorOnGetDevices) {
      responseObserver.onError(new RuntimeException("Server error"));
      return;
    }
    // This logic mirrors that logic of perfd-host. We do proper filtering of all events here so our test, behave as close to runtime as
    // possible.
    HashMap<Long, EventGroup.Builder> eventGroups = new HashMap<>();
    for (long stream : myStreamEvents.keySet()) {
      if (request.getStreamId() != EMPTY_REQUEST_VALUE && stream != request.getStreamId()) {
        continue;
      }
      for (EventGroup.Builder eventGroup : myStreamEvents.get(stream)) {
        for (Common.Event event : eventGroup.getEventsList()) {
          if (request.getSessionId() != event.getSessionId() && request.getSessionId() != EMPTY_REQUEST_VALUE) {
            continue;
          }
          if (request.getGroupId() != EMPTY_REQUEST_VALUE && request.getGroupId() != event.getGroupId()) {
            continue;
          }
          if (request.getFromTimestamp() != EMPTY_REQUEST_VALUE && request.getFromTimestamp() > event.getTimestamp()) {
            continue;
          }
          if (request.getKind() != event.getKind()) {
            continue;
          }
          if (!eventGroups.containsKey(eventGroup.getGroupId())) {
            eventGroups.put(eventGroup.getGroupId(), EventGroup.newBuilder().setGroupId(eventGroup.getGroupId()));
          }
          eventGroups.get(eventGroup.getGroupId()).addEvents(event);
        }
      }
    }
    GetEventGroupsResponse.Builder builder = GetEventGroupsResponse.newBuilder();
    for (EventGroup.Builder eventGroup : eventGroups.values()) {
      builder.addGroups(eventGroup);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }
}
