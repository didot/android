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
package com.android.tools.datastore.service;

import static com.android.tools.datastore.DataStoreDatabase.Characteristic.PERFORMANT;

import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.DataStoreService.BackingNamespace;
import com.android.tools.datastore.DeviceId;
import com.android.tools.datastore.LogService;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.MemoryLiveAllocationTable;
import com.android.tools.datastore.database.MemoryStatsTable;
import com.android.tools.datastore.poller.MemoryDataPoller;
import com.android.tools.datastore.poller.MemoryJvmtiDataPoller;
import com.android.tools.datastore.poller.NativeSymbolsPoller;
import com.android.tools.datastore.poller.PollRunner;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationContextsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationContextsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSnapshotRequest;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo;
import com.android.tools.profiler.proto.MemoryProfiler.BatchAllocationSample;
import com.android.tools.profiler.proto.MemoryProfiler.BatchJNIGlobalRefEvent;
import com.android.tools.profiler.proto.MemoryProfiler.DumpDataRequest;
import com.android.tools.profiler.proto.MemoryProfiler.DumpDataResponse;
import com.android.tools.profiler.proto.MemoryProfiler.ForceGarbageCollectionRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ForceGarbageCollectionResponse;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpInfo;
import com.android.tools.profiler.proto.MemoryProfiler.ImportHeapDumpRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ImportHeapDumpResponse;
import com.android.tools.profiler.proto.MemoryProfiler.ImportLegacyAllocationsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ImportLegacyAllocationsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.JNIGlobalRefsEventsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.LatestAllocationTimeRequest;
import com.android.tools.profiler.proto.MemoryProfiler.LatestAllocationTimeResponse;
import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationContextsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationEventsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationEventsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.ListDumpInfosRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ListHeapDumpInfosResponse;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStartRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStartResponse;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStopRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStopResponse;
import com.android.tools.profiler.proto.MemoryProfiler.NativeCallStack;
import com.android.tools.profiler.proto.MemoryProfiler.ResolveNativeBacktraceRequest;
import com.android.tools.profiler.proto.MemoryProfiler.SetAllocationSamplingRateRequest;
import com.android.tools.profiler.proto.MemoryProfiler.SetAllocationSamplingRateResponse;
import com.android.tools.profiler.proto.MemoryProfiler.StackFrameInfoRequest;
import com.android.tools.profiler.proto.MemoryProfiler.StackFrameInfoResponse;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.TriggerHeapDumpRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TriggerHeapDumpResponse;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import io.grpc.stub.StreamObserver;
import java.sql.Connection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

public class MemoryService extends MemoryServiceGrpc.MemoryServiceImplBase implements ServicePassThrough {
  private static final BackingNamespace LIVE_ALLOCATION_NAMESPACE = new BackingNamespace("LiveAllocations", PERFORMANT);

  private final Map<Long, PollRunner> myRunners = new HashMap<>();
  private final Map<Long, PollRunner> myJvmtiRunners = new HashMap<>();
  private final Map<Long, PollRunner> mySymbolizationRunners = new HashMap<>();
  private final MemoryStatsTable myStatsTable;
  private final MemoryLiveAllocationTable myAllocationsTable;
  private final Consumer<Runnable> myFetchExecutor;
  private final DataStoreService myService;
  private final LogService myLogService;

  // TODO Revisit fetch mechanism
  public MemoryService(@NotNull DataStoreService dataStoreService, Consumer<Runnable> fetchExecutor, @NotNull LogService logService) {
    myLogService = logService;
    myFetchExecutor = fetchExecutor;
    myService = dataStoreService;
    myStatsTable = new MemoryStatsTable();
    myAllocationsTable = new MemoryLiveAllocationTable(myLogService);
  }

  @Override
  public void startMonitoringApp(MemoryStartRequest request, StreamObserver<MemoryStartResponse> observer) {
    MemoryServiceGrpc.MemoryServiceBlockingStub client = myService.getMemoryClient(DeviceId.fromSession(request.getSession()));
    if (client != null) {
      observer.onNext(client.startMonitoringApp(request));
      observer.onCompleted();

      Common.Session session = request.getSession();
      DeviceId deviceId = DeviceId.fromSession(session);
      long sessionId = session.getSessionId();

      myJvmtiRunners.put(sessionId, new MemoryJvmtiDataPoller(session, myAllocationsTable, client));
      myRunners.put(sessionId, new MemoryDataPoller(session, myStatsTable, client, myFetchExecutor));
      mySymbolizationRunners.put(sessionId,
                                 new NativeSymbolsPoller(session, myAllocationsTable, myService.getNativeSymbolizer(),
                                                         myService.getTransportClient(deviceId), myLogService));

      myFetchExecutor.accept(mySymbolizationRunners.get(sessionId));
      myFetchExecutor.accept(myJvmtiRunners.get(sessionId));
      myFetchExecutor.accept(myRunners.get(sessionId));
    }
    else {
      observer.onNext(MemoryStartResponse.getDefaultInstance());
      observer.onCompleted();
    }
  }

  @Override
  public void stopMonitoringApp(MemoryStopRequest request, StreamObserver<MemoryStopResponse> observer) {
    long sessionId = request.getSession().getSessionId();
    PollRunner runner = myRunners.remove(sessionId);
    if (runner != null) {
      runner.stop();
    }
    runner = myJvmtiRunners.remove(sessionId);
    if (runner != null) {
      runner.stop();
    }
    runner = mySymbolizationRunners.remove(sessionId);
    if (runner != null) {
      runner.stop();
    }
    // Our polling service can get shutdown if we unplug the device.
    // This should be the only function that gets called as StudioProfilers attempts
    // to stop monitoring the last app it was monitoring.
    MemoryServiceGrpc.MemoryServiceBlockingStub service =
      myService.getMemoryClient(DeviceId.fromSession(request.getSession()));
    if (service == null) {
      observer.onNext(MemoryStopResponse.getDefaultInstance());
    }
    else {
      observer.onNext(service.stopMonitoringApp(request));
    }
    observer.onCompleted();
  }

  @Override
  public void triggerHeapDump(TriggerHeapDumpRequest request, StreamObserver<TriggerHeapDumpResponse> responseObserver) {
    MemoryServiceGrpc.MemoryServiceBlockingStub client = myService.getMemoryClient(DeviceId.fromSession(request.getSession()));
    TriggerHeapDumpResponse response = TriggerHeapDumpResponse.getDefaultInstance();
    if (client != null) {
      response = client.triggerHeapDump(request);
      // Saves off the HeapDumpInfo immediately instead of waiting for the MemoryDataPoller to pull it through, which can be delayed
      // and results in a NOT_FOUND status when the profiler tries to pull the dump's data in quick successions.
      if (response.getStatus() == TriggerHeapDumpResponse.Status.SUCCESS) {
        assert response.getInfo() != null;
        myStatsTable.insertOrReplaceHeapInfo(request.getSession(), response.getInfo());
      }
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getHeapDump(DumpDataRequest request, StreamObserver<DumpDataResponse> responseObserver) {
    DumpDataResponse.Builder responseBuilder = DumpDataResponse.newBuilder();
    DumpDataResponse.Status status = myStatsTable.getHeapDumpStatus(request.getSession(), request.getDumpTime());
    switch (status) {
      case SUCCESS:
        byte[] data = myStatsTable.getHeapDumpData(request.getSession(), request.getDumpTime());
        assert data != null;
        responseBuilder.setData(ByteString.copyFrom(data));
        responseBuilder.setStatus(status);
        break;
      case NOT_READY:
      case FAILURE_UNKNOWN:
      case NOT_FOUND:
        responseBuilder.setStatus(status);
        break;
      default:
        responseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
        break;
    }

    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void listHeapDumpInfos(ListDumpInfosRequest request,
                                StreamObserver<ListHeapDumpInfosResponse> responseObserver) {
    ListHeapDumpInfosResponse.Builder responseBuilder = ListHeapDumpInfosResponse.newBuilder();
    List<HeapDumpInfo> dump = myStatsTable.getHeapDumpInfoByRequest(request.getSession(), request);
    responseBuilder.addAllInfos(dump);
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void importHeapDump(ImportHeapDumpRequest request, StreamObserver<ImportHeapDumpResponse> responseObserver) {
    ImportHeapDumpResponse.Builder responseBuilder = ImportHeapDumpResponse.newBuilder();
    myStatsTable.insertOrReplaceHeapInfo(request.getSession(), request.getInfo());
    myStatsTable
      .insertHeapDumpData(request.getSession(), request.getInfo().getStartTime(), DumpDataResponse.Status.SUCCESS, request.getData());
    responseBuilder.setStatus(ImportHeapDumpResponse.Status.SUCCESS);
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void trackAllocations(TrackAllocationsRequest request,
                               StreamObserver<TrackAllocationsResponse> responseObserver) {
    MemoryServiceGrpc.MemoryServiceBlockingStub client = myService.getMemoryClient(DeviceId.fromSession(request.getSession()));
    TrackAllocationsResponse response = TrackAllocationsResponse.getDefaultInstance();
    if (client != null) {
      response = client.trackAllocations(request);
      // Saves off the AllocationsInfo immediately instead of waiting for the MemoryDataPoller to pull it through, which can be delayed
      // and results in a NOT_FOUND status when the profiler tries to pull the info's data in quick successions.
      if (request.getEnabled() && response.getStatus() == TrackAllocationsResponse.Status.SUCCESS) {
        assert response.getInfo() != null;
        myStatsTable.insertOrReplaceAllocationsInfo(request.getSession(), response.getInfo());
      }
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void importLegacyAllocations(ImportLegacyAllocationsRequest request,
                                      StreamObserver<ImportLegacyAllocationsResponse> responseObserver) {
    assert request.getInfo().getLegacy();
    myStatsTable.insertOrReplaceAllocationsInfo(request.getSession(), request.getInfo());

    AllocationContextsResponse contexts = request.getContexts();
    LegacyAllocationEventsResponse allocations = request.getAllocations();
    if (!contexts.equals(AllocationContextsResponse.getDefaultInstance())) {
      myStatsTable
        .insertLegacyAllocationContext(request.getSession(), contexts.getAllocatedClassesList(), contexts.getAllocationStacksList());
    }
    if (!allocations.equals(LegacyAllocationEventsResponse.getDefaultInstance())) {
      myStatsTable.updateLegacyAllocationEvents(request.getSession(), request.getInfo().getStartTime(), allocations);
    }

    responseObserver.onNext(ImportLegacyAllocationsResponse.newBuilder().setStatus(ImportLegacyAllocationsResponse.Status.SUCCESS).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getLegacyAllocationContexts(LegacyAllocationContextsRequest request,
                                          StreamObserver<AllocationContextsResponse> responseObserver) {
    responseObserver.onNext(myStatsTable.getLegacyAllocationContexts(request));
    responseObserver.onCompleted();
  }

  @Override
  public void getLegacyAllocationDump(DumpDataRequest request, StreamObserver<DumpDataResponse> responseObserver) {
    DumpDataResponse.Builder responseBuilder = DumpDataResponse.newBuilder();

    AllocationsInfo response = myStatsTable.getAllocationsInfo(request.getSession(), request.getDumpTime());
    if (response == null) {
      responseBuilder.setStatus(DumpDataResponse.Status.NOT_FOUND);
    }
    else if (response.getStatus() == AllocationsInfo.Status.FAILURE_UNKNOWN) {
      responseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
    }
    else {
      if (response.getLegacy()) {
        byte[] data = myStatsTable.getLegacyAllocationDumpData(request.getSession(), request.getDumpTime());
        if (data == null) {
          responseBuilder.setStatus(DumpDataResponse.Status.NOT_READY);
        }
        else {
          responseBuilder.setStatus(DumpDataResponse.Status.SUCCESS);
          responseBuilder.setData(ByteString.copyFrom(data));
        }
      }
      else {
        // O+ allocation does not have legacy data.
        responseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
      }
    }
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getStackFrameInfo(StackFrameInfoRequest request,
                                StreamObserver<StackFrameInfoResponse> responseObserver) {
    responseObserver.onNext(myAllocationsTable.getStackFrameInfo(request.getSession(), request.getMethodId()));
    responseObserver.onCompleted();
  }

  @Override
  public void getLegacyAllocationEvents(LegacyAllocationEventsRequest request,
                                        StreamObserver<LegacyAllocationEventsResponse> responseObserver) {
    LegacyAllocationEventsResponse.Builder builder = LegacyAllocationEventsResponse.newBuilder();

    AllocationsInfo response = myStatsTable.getAllocationsInfo(request.getSession(), request.getStartTime());
    if (response == null) {
      builder.setStatus(LegacyAllocationEventsResponse.Status.NOT_FOUND);
    }
    else if (response.getStatus() == AllocationsInfo.Status.FAILURE_UNKNOWN) {
      builder.setStatus(LegacyAllocationEventsResponse.Status.FAILURE_UNKNOWN);
    }
    else {
      if (response.getLegacy()) {
        LegacyAllocationEventsResponse events =
          myStatsTable.getLegacyAllocationData(request.getSession(), request.getStartTime());
        if (events == null) {
          builder.setStatus(LegacyAllocationEventsResponse.Status.NOT_READY);
        }
        else {
          builder.mergeFrom(events);
        }
      }
      else {
        // O+ allocation does not have legacy data.
        builder.setStatus(LegacyAllocationEventsResponse.Status.FAILURE_UNKNOWN);
      }
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getData(MemoryRequest request, StreamObserver<MemoryData> responseObserver) {
    MemoryData response = myStatsTable.getData(request);
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getJvmtiData(MemoryRequest request, StreamObserver<MemoryData> responseObserver) {
    MemoryData response = MemoryData.newBuilder().addAllAllocSamplingRateEvents(
      myAllocationsTable.getAllocationSamplingRateEvents(request.getSession().getSessionId(), request.getStartTime(), request.getEndTime()))
      .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getAllocations(AllocationSnapshotRequest request, StreamObserver<BatchAllocationSample> responseObserver) {
    BatchAllocationSample response;
    if (request.getLiveObjectsOnly()) {
      response = myAllocationsTable.getSnapshot(request.getSession(), request.getEndTime());
    }
    else {
      response =
        myAllocationsTable.getAllocations(request.getSession(), request.getStartTime(), request.getEndTime());
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getJNIGlobalRefsEvents(JNIGlobalRefsEventsRequest request,
                                     StreamObserver<BatchJNIGlobalRefEvent> responseObserver) {
    BatchJNIGlobalRefEvent result;
    if (request.getLiveObjectsOnly()) {
      result = myAllocationsTable.getJniReferencesSnapshot(request.getSession(), request.getEndTime());
    }
    else {
      result = myAllocationsTable.getJniReferencesEventsFromRange(request.getSession(), request.getStartTime(), request.getEndTime());
    }

    responseObserver.onNext(result);
    responseObserver.onCompleted();
  }

  @Override
  public void resolveNativeBacktrace(ResolveNativeBacktraceRequest request,
                                     StreamObserver<NativeCallStack> responseObserver) {
    NativeCallStack callStack = myAllocationsTable.resolveNativeBacktrace(request.getSession(), request.getBacktrace());
    responseObserver.onNext(callStack);
    responseObserver.onCompleted();
  }

  @Override
  public void getLatestAllocationTime(LatestAllocationTimeRequest request,
                                      StreamObserver<LatestAllocationTimeResponse> responseObserver) {
    LatestAllocationTimeResponse response = myAllocationsTable.getLatestDataTimestamp(request.getSession());
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getAllocationContexts(AllocationContextsRequest request, StreamObserver<AllocationContextsResponse> responseObserver) {
    AllocationContextsResponse response =
      myAllocationsTable.getAllocationContexts(request.getSession(), request.getStartTime(), request.getEndTime());
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void forceGarbageCollection(ForceGarbageCollectionRequest request, StreamObserver<ForceGarbageCollectionResponse> observer) {
    MemoryServiceGrpc.MemoryServiceBlockingStub client = myService.getMemoryClient(DeviceId.fromSession(request.getSession()));
    if (client != null) {
      observer.onNext(client.forceGarbageCollection(request));
    }
    else {
      observer.onNext(ForceGarbageCollectionResponse.getDefaultInstance());
    }
    observer.onCompleted();
  }

  @Override
  public void setAllocationSamplingRate(SetAllocationSamplingRateRequest request,
                                        StreamObserver<SetAllocationSamplingRateResponse> observer) {
    MemoryServiceGrpc.MemoryServiceBlockingStub client = myService.getMemoryClient(DeviceId.fromSession(request.getSession()));
    if (client != null) {
      observer.onNext(client.setAllocationSamplingRate(request));
    }
    else {
      observer.onNext(SetAllocationSamplingRateResponse.getDefaultInstance());
    }
    observer.onCompleted();
  }

  @NotNull
  @Override
  public List<BackingNamespace> getBackingNamespaces() {
    return Arrays.asList(BackingNamespace.DEFAULT_SHARED_NAMESPACE, LIVE_ALLOCATION_NAMESPACE);
  }

  @Override
  public void setBackingStore(@NotNull BackingNamespace namespace, @NotNull Connection connection) {
    assert getBackingNamespaces().contains(namespace);
    if (namespace.equals(BackingNamespace.DEFAULT_SHARED_NAMESPACE)) {
      myStatsTable.initialize(connection);
    }
    else {
      myAllocationsTable.initialize(connection);
    }
  }
}