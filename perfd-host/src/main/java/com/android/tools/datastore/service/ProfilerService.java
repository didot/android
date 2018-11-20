/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.datastore.service;

import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.DeviceId;
import com.android.tools.datastore.LogService;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.DataStoreTable;
import com.android.tools.datastore.database.ProfilerTable;
import com.android.tools.datastore.database.UnifiedEventsTable;
import com.android.tools.datastore.poller.ProfilerDevicePoller;
import com.android.tools.datastore.poller.UnifiedEventsDataPoller;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.Event;
import com.android.tools.profiler.proto.Common.Stream;
import com.android.tools.profiler.proto.Profiler.AgentStatusRequest;
import com.android.tools.profiler.proto.Profiler.AgentStatusResponse;
import com.android.tools.profiler.proto.Profiler.BeginSessionRequest;
import com.android.tools.profiler.proto.Profiler.BeginSessionResponse;
import com.android.tools.profiler.proto.Profiler.BytesRequest;
import com.android.tools.profiler.proto.Profiler.BytesResponse;
import com.android.tools.profiler.proto.Profiler.ConfigureStartupAgentRequest;
import com.android.tools.profiler.proto.Profiler.ConfigureStartupAgentResponse;
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
import com.google.common.collect.Maps;
import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import java.sql.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * This class hosts an EventService that will provide callers access to all cached EventData.
 * The data is populated from polling the service passed into the connectService function.
 */
public class ProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase implements ServicePassThrough {
  private final Map<Channel, ProfilerDevicePoller> myPollers = Maps.newHashMap();
  private final Consumer<Runnable> myFetchExecutor;
  @NotNull private final LogService myLogService;
  private final ProfilerTable myTable;
  @NotNull private final DataStoreService myService;
  @NotNull private final UnifiedEventsTable myUnifiedEventsTable;
  /**
   * A mapping of stream ids to active stubs. This mapping allows commands to be routed to the proper stubs.
   */
  private final HashMap<Long, ProfilerServiceGrpc.ProfilerServiceBlockingStub> myStreamIdToStub;
  /**
   * A mapping of active channels to pollers. This mapping allows us to keep track of active pollers for a channel, and clean up pollers
   * when channels are closed.
   */
  private final Map<Channel, UnifiedEventsDataPoller> myUnifiedEventsDataPollers = Maps.newHashMap();
  /**
   * A map of active channels to unified event streams. This map helps us clean up streams when a channel is closed.
   */
  private final Map<Channel, Stream> myChannelToStream = Maps.newHashMap();

  public ProfilerService(@NotNull DataStoreService service,
                         Consumer<Runnable> fetchExecutor,
                         @NotNull LogService logService) {
    myService = service;
    myFetchExecutor = fetchExecutor;
    myLogService = logService;
    myTable = new ProfilerTable();
    myUnifiedEventsTable = new UnifiedEventsTable();
    myStreamIdToStub = new HashMap<>();
  }

  @NotNull
  private LogService.Logger getLogger() {
    return myLogService.getLogger(ProfilerService.class);
  }

  @Override
  public void getCurrentTime(TimeRequest request, StreamObserver<TimeResponse> observer) {
    // This function can get called before the datastore is connected to a device as such we need to check
    // if we have a connection before attempting to get the time.
    long streamId = request.getStreamId();
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client =
      myStreamIdToStub.containsKey(streamId) ? myStreamIdToStub.get(streamId) : myService.getProfilerClient(DeviceId.of(streamId));
    if (client != null) {
      observer.onNext(client.getCurrentTime(request));
    }
    else {
      // Need to return something in the case of no device.
      observer.onNext(TimeResponse.getDefaultInstance());
    }
    observer.onCompleted();
  }

  @Override
  public void getVersion(VersionRequest request, StreamObserver<VersionResponse> observer) {
    long streamId = request.getStreamId();
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client =
      myStreamIdToStub.containsKey(streamId) ? myStreamIdToStub.get(streamId) : myService.getProfilerClient(DeviceId.of(streamId));
    if (client != null) {
      observer.onNext(client.getVersion(request));
    }
    observer.onCompleted();
  }

  @Override
  public void getDevices(GetDevicesRequest request, StreamObserver<GetDevicesResponse> observer) {
    GetDevicesResponse response = myTable.getDevices();
    observer.onNext(response);
    observer.onCompleted();
  }

  @Override
  public void getProcesses(GetProcessesRequest request, StreamObserver<GetProcessesResponse> observer) {
    GetProcessesResponse response = myTable.getProcesses(request);
    observer.onNext(response);
    observer.onCompleted();
  }

  @Override
  public void getAgentStatus(AgentStatusRequest request, StreamObserver<AgentStatusResponse> observer) {
    observer.onNext(myTable.getAgentStatus(request));
    observer.onCompleted();
  }

  @Override
  public void configureStartupAgent(ConfigureStartupAgentRequest request, StreamObserver<ConfigureStartupAgentResponse> observer) {
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client =
      myStreamIdToStub.containsKey(request.getDeviceId()) ? myStreamIdToStub.get(request.getDeviceId()) :
      myService.getProfilerClient(DeviceId.of(request.getDeviceId()));

    if (client != null) {
      observer.onNext(client.configureStartupAgent(request));
    }
    else {
      observer.onNext(ConfigureStartupAgentResponse.getDefaultInstance());
    }

    observer.onCompleted();
  }

  @Override
  public void beginSession(BeginSessionRequest request, StreamObserver<BeginSessionResponse> responseObserver) {
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client = myService.getProfilerClient(DeviceId.of(request.getDeviceId()));
    if (client == null) {
      responseObserver.onNext(BeginSessionResponse.getDefaultInstance());
    }
    else {
      BeginSessionResponse response = client.beginSession(request);
      getLogger().info("Session (ID " + response.getSession().getSessionId() + ") begins.");
      // TODO (b/67508808) re-investigate whether we should use a poller to update the session instead.
      // The downside is we will have a delay before getSessions will see the data
      myTable.insertOrUpdateSession(response.getSession(),
                                    request.getSessionName(),
                                    request.getRequestTimeEpochMs(),
                                    request.getJvmtiConfig().getAttachAgent(),
                                    request.getJvmtiConfig().getLiveAllocationEnabled(),
                                    Common.SessionMetaData.SessionType.FULL);
      responseObserver.onNext(response);
    }
    responseObserver.onCompleted();
  }

  @Override
  public void endSession(EndSessionRequest request, StreamObserver<EndSessionResponse> responseObserver) {
    getLogger().info("Session (ID " + request.getSessionId() + ") ends.");
    DeviceId deviceId = DeviceId.of(request.getDeviceId());
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client = myService.getProfilerClient(deviceId);
    if (client == null) {
      // In case the device is no longer connected, update the session's end time with the device's last known time.
      long timeNs = myTable.getDeviceLastKnownTime(deviceId);
      myTable.updateSessionEndTime(request.getSessionId(), timeNs);
      Common.Session session = myTable.getSessionById(request.getSessionId());
      responseObserver.onNext(EndSessionResponse.newBuilder().setSession(session).build());
    }
    else {
      EndSessionResponse response = client.endSession(request);
      Common.Session session = response.getSession();
      // TODO (b/67508808) re-investigate whether we should use a poller to update the session instead.
      // The downside is we will have a delay before getSessions will see the data
      myTable.updateSessionEndTime(session.getSessionId(), session.getEndTimestamp());
      responseObserver.onNext(response);
    }
    responseObserver.onCompleted();
  }

  @Override
  public void getSessionMetaData(GetSessionMetaDataRequest request,
                                 StreamObserver<GetSessionMetaDataResponse> responseObserver) {
    responseObserver.onNext(myTable.getSessionMetaData(request.getSessionId()));
    responseObserver.onCompleted();
  }

  @Override
  public void getSessions(GetSessionsRequest request, StreamObserver<GetSessionsResponse> responseObserver) {
    responseObserver.onNext(myTable.getSessions());
    responseObserver.onCompleted();
  }

  @Override
  public void deleteSession(DeleteSessionRequest request, StreamObserver<DeleteSessionResponse> responseObserver) {
    // TODO (b\67509712): properly delete all data related to the session.
    myTable.deleteSession(request.getSessionId());
    responseObserver.onNext(DeleteSessionResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void importSession(ImportSessionRequest request, StreamObserver<ImportSessionResponse> responseObserver) {
    myTable.insertOrUpdateSession(request.getSession(), request.getSessionName(), request.getStartTimestampEpochMs(), false, false,
                                  request.getSessionType());
    responseObserver.onNext(ImportSessionResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  public void startMonitoring(Channel channel) {
    assert !myPollers.containsKey(channel);
    ProfilerServiceGrpc.ProfilerServiceBlockingStub stub = ProfilerServiceGrpc.newBlockingStub(channel);
    ProfilerDevicePoller poller = new ProfilerDevicePoller(myService, myTable, stub);
    myPollers.put(channel, poller);
    DataStoreTable.addDataStoreErrorCallback(poller);
    myFetchExecutor.accept(myPollers.get(channel));
  }

  /**
   * This call to startPolling maps a stream to a channel. This information is used in the new event pipeline.
   */
  public void startPolling(Common.Stream stream, Channel channel) {
    ProfilerServiceGrpc.ProfilerServiceBlockingStub stub = ProfilerServiceGrpc.newBlockingStub(channel);
    streamConnected(stream, stub);
    UnifiedEventsDataPoller poller = new UnifiedEventsDataPoller(stream.getStreamId(), myUnifiedEventsTable, stub);
    myUnifiedEventsDataPollers.put(channel, poller);
    myStreamIdToStub.put(stream.getStreamId(), stub);
    myChannelToStream.put(channel, stream);
    myFetchExecutor.accept(poller);
  }

  public void stopMonitoring(Channel channel) {
    if (myPollers.containsKey(channel)) {
      ProfilerDevicePoller poller = myPollers.remove(channel);
      poller.stop();
      DataStoreTable.removeDataStoreErrorCallback(poller);
    }
    if (myUnifiedEventsDataPollers.containsKey(channel)) {
      UnifiedEventsDataPoller poller = myUnifiedEventsDataPollers.remove(channel);
      poller.stop();
      streamDisconnected(myChannelToStream.get(channel));
      myStreamIdToStub.remove(myChannelToStream.get(channel).getStreamId());
      myChannelToStream.remove(channel);
    }
  }

  private void streamConnected(Common.Stream stream, ProfilerServiceGrpc.ProfilerServiceBlockingStub stub) {
    myUnifiedEventsTable.insertUnifiedEvent(DataStoreService.DATASTORE_RESERVED_STREAM_ID, Event.newBuilder()
      .setKind(Event.Kind.STREAM)
      .setGroupId(stream.getStreamId())
      .setType(Event.Type.STREAM_CONNECTED)
      .setTimestamp(System.nanoTime())
      .setStream(stream)
      .build());
  }

  private void streamDisconnected(Common.Stream stream) {
    myUnifiedEventsTable.insertUnifiedEvent(DataStoreService.DATASTORE_RESERVED_STREAM_ID, Event.newBuilder()
      .setKind(Event.Kind.STREAM)
      .setType(Event.Type.STREAM_DISCONNECTED)
      .setGroupId(stream.getStreamId())
      .setStream(stream)
      .setTimestamp(System.nanoTime())
      .build());
  }


  @Override
  public void getBytes(BytesRequest request, StreamObserver<BytesResponse> responseObserver) {
    // TODO: Currently the cache is on demand, we want to look into caching all available files.
    BytesResponse response = myTable.getBytes(request);
    ProfilerServiceGrpc.ProfilerServiceBlockingStub client =
      myService.getProfilerClient(DeviceId.fromSession(request.getSession()));

    if (response == null && client != null) {
      response = myService.getProfilerClient(DeviceId.fromSession(request.getSession())).getBytes(request);
      myTable.insertOrUpdateBytes(request.getId(), request.getSession(), response);
    }
    else if (response == null) {
      response = BytesResponse.getDefaultInstance();
    }

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @NotNull
  @Override
  public List<DataStoreService.BackingNamespace> getBackingNamespaces() {
    return Collections.singletonList(DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE);
  }

  @Override
  public void setBackingStore(@NotNull DataStoreService.BackingNamespace namespace, @NotNull Connection connection) {
    assert namespace == DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE;
    myTable.initialize(connection);
    myUnifiedEventsTable.initialize(connection);
  }

  @Override
  public void execute(ExecuteRequest request, StreamObserver<ExecuteResponse> responseObserver) {
    long streamId = request.getCommand().getStreamId();
    // TODO (b/114751407): Send stream id 0 to all streams.
    // TODO (b/114751407): Handle stream not found.
    if (myStreamIdToStub.containsKey(streamId)) {
      ProfilerServiceGrpc.ProfilerServiceBlockingStub client = myStreamIdToStub.get(streamId);
      responseObserver.onNext(client.execute(request));
      responseObserver.onCompleted();
    }
    else {
      responseObserver.onNext(ExecuteResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }

  @Override
  public void getEventGroups(GetEventGroupsRequest request, StreamObserver<GetEventGroupsResponse> responseObserver) {
    GetEventGroupsResponse.Builder response = GetEventGroupsResponse.newBuilder();
    Collection<EventGroup> events = myUnifiedEventsTable.queryUnifiedEventGroups(request);
    response.addAllGroups(events);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }
}
