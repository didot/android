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

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.Profiler.*;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.intellij.util.containers.MultiMap;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {
  public static final String VERSION = "3141592";
  public static final long FAKE_DEVICE_ID = 1234;
  public static final String FAKE_DEVICE_NAME = "FakeDevice";
  public static final String FAKE_PROCESS_NAME = "FakeProcess";

  private final Map<Long, Common.Device> myDevices;
  private final MultiMap<Common.Device, Common.Process> myProcesses;
  private final Map<String, ByteString> myCache;
  private final Map<Long, Common.Session> mySessions;
  private final Map<Long, Common.SessionMetaData> mySessionMetaDatas;
  private long myTimestampNs;
  private boolean myThrowErrorOnGetDevices;
  private boolean myAttachAgentCalled;
  private AgentStatusResponse.Status myAgentStatus;

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
    if (connected) {
      Common.Device device = Common.Device.newBuilder()
        .setDeviceId(FAKE_DEVICE_ID)
        .setSerial(FAKE_DEVICE_NAME)
        .setModel(FAKE_DEVICE_NAME)
        .setState(Common.Device.State.ONLINE)
        .build();
      Common.Process process = Common.Process.newBuilder()
        //Setting PID to be 1 since there is a process with pid being 1 in test input atrace_processid_1
        .setPid(1)
        .setDeviceId(FAKE_DEVICE_ID)
        .setState(Common.Process.State.ALIVE)
        .setName(FAKE_PROCESS_NAME)
        .build();
      addDevice(device);
      addProcess(device, process);
    }
  }

  public void addProcess(Common.Device device, Common.Process process) {
    if (!myDevices.containsKey(device.getDeviceId())) {
      throw new IllegalArgumentException("Invalid device: " + device.getDeviceId());
    }
    assert device.getDeviceId() == process.getDeviceId();
    myProcesses.putValue(myDevices.get(device.getDeviceId()), process);
  }

  public void removeProcess(Common.Device device, Common.Process process) {
    if (!myDevices.containsKey(device.getDeviceId())) {
      throw new IllegalArgumentException("Invalid device: " + device);
    }
    assert device.getDeviceId() == process.getDeviceId();
    myProcesses.remove(myDevices.get(device.getDeviceId()), process);
  }

  public void addDevice(Common.Device device) {
    myDevices.put(device.getDeviceId(), device);
  }

  public void updateDevice(Common.Device oldDevice, Common.Device newDevice) {
    // Move processes from old to new device
    myProcesses.putValues(newDevice, myProcesses.get(oldDevice));
    // Remove old device from processes map.
    myProcesses.remove(oldDevice);
    // Update device on devices map
    myDevices.remove(oldDevice.getDeviceId());
    myDevices.put(newDevice.getDeviceId(), newDevice);
  }

  public void addSession(Common.Session session, Common.SessionMetaData metadata) {
    mySessions.put(session.getSessionId(), session);
    mySessionMetaDatas.put(session.getSessionId(), metadata);
  }

  public void addFile(String id, ByteString contents) {
    myCache.put(id, contents);
  }

  public void setTimestampNs(long timestamp) {
    myTimestampNs = timestamp;
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
    session = session.toBuilder()
      .setEndTimestamp(session.getStartTimestamp() + 1)
      .build();
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
    AgentStatusResponse.Builder builder = AgentStatusResponse.newBuilder();
    if (myAgentStatus != null) {
      builder.setStatus(myAgentStatus);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  public void setAgentStatus(@NotNull AgentStatusResponse.Status status) {
    myAgentStatus = status;
  }

  public void setThrowErrorOnGetDevices(boolean throwErrorOnGetDevices) {
    myThrowErrorOnGetDevices = throwErrorOnGetDevices;
  }

  public boolean getAgentAttachCalled() {
    return myAttachAgentCalled;
  }

  public Common.SessionMetaData.SessionType getLastImportedSessionType() {
    return myLastImportedSessionType;
  }
}
