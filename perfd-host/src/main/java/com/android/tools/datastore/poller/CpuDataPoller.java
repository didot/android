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
package com.android.tools.datastore.poller;

import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.DatastoreTable;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.google.protobuf3jarjar.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.RunnableFuture;

/**
 * This class gathers sets up a CPUProfilerService and forward all commands to the connected channel with the exception of getData.
 * The get data command will pull data locally cached from the connected service.
 */
public class CpuDataPoller extends CpuServiceGrpc.CpuServiceImplBase implements ServicePassThrough, PollRunner.PollingCallback {

  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;
  private CpuServiceGrpc.CpuServiceBlockingStub myPollingService;

  /**
   * Used to get device time.
   */
  private ProfilerServiceGrpc.ProfilerServiceBlockingStub myProfilerService;

  //TODO Pull this into a storage container that can read/write this to disk
  protected final List<CpuProfiler.CpuProfilerData> myData = new ArrayList<>();
  protected final Map<Integer, CpuProfiler.GetThreadsResponse.Thread.Builder> myThreads = new TreeMap<>();
  protected final Map<Integer, TraceData> myTraces = new HashMap<>();

  private final Object myLock = new Object();

  private int myProcessId = -1;

  private long myStartTraceTimestamp = -1;

  public CpuDataPoller() {
  }

  @Override
  public RunnableFuture<Void> getRunner() {
    return new PollRunner(this, PollRunner.POLLING_DELAY_NS);
  }

  @Override
  public ServerServiceDefinition getService() {
    return bindService();
  }

  @Override
  public void connectService(ManagedChannel channel) {
    myPollingService = CpuServiceGrpc.newBlockingStub(channel);
    myProfilerService = ProfilerServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public DatastoreTable getDatastoreTable() {
    return null;
  }

  @Override
  public void poll() throws StatusRuntimeException {
    CpuProfiler.CpuDataRequest.Builder dataRequestBuilder = CpuProfiler.CpuDataRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTimestamp(myDataRequestStartTimestampNs)
      .setEndTimestamp(Long.MAX_VALUE);
    CpuProfiler.CpuDataResponse response = myPollingService.getData(dataRequestBuilder.build());

    synchronized (myLock) {
      for (CpuProfiler.CpuProfilerData data : response.getDataList()) {
        myDataRequestStartTimestampNs = data.getBasicInfo().getEndTimestamp();
        myData.add(data);
        if (data.getDataCase() == CpuProfiler.CpuProfilerData.DataCase.THREAD_ACTIVITIES) {
          CpuProfiler.ThreadActivities activities = data.getThreadActivities();
          if (activities != null) {
            for (CpuProfiler.ThreadActivity activity : activities.getActivitiesList()) {
              int tid = activity.getTid();
              CpuProfiler.GetThreadsResponse.Thread.Builder builder = myThreads.get(tid);
              if (builder == null) {
                builder = CpuProfiler.GetThreadsResponse.Thread.newBuilder().setName(activity.getName()).setTid(tid);
                myThreads.put(tid, builder);
              }
              CpuProfiler.ThreadActivity.State state = activity.getNewState();
              CpuProfiler.GetThreadsResponse.State converted = CpuProfiler.GetThreadsResponse.State.valueOf(state.toString());
              builder.addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                                      .setTimestamp(activity.getTimestamp())
                                      .setNewState(converted));
            }
          }
        }
      }
    }
  }

  @Override
  public void getData(CpuProfiler.CpuDataRequest request, StreamObserver<CpuProfiler.CpuDataResponse> observer) {
    CpuProfiler.CpuDataResponse.Builder response = CpuProfiler.CpuDataResponse.newBuilder();
    if (myData.size() == 0) {
      observer.onNext(response.build());
      observer.onCompleted();
      return;
    }

    long startTime = request.getStartTimestamp();
    long endTime = request.getEndTimestamp();

    //TODO: Optimize so we do not need to loop all the data every request, ideally binary search to start time and loop till end.
    synchronized (myLock) {
      for (CpuProfiler.CpuProfilerData data : myData) {
        long current = data.getBasicInfo().getEndTimestamp();
        if (current > startTime && current <= endTime) {
          response.addData(data);
        }
      }
    }
    observer.onNext(response.build());
    observer.onCompleted();
  }

  @Override
  public void getThreads(CpuProfiler.GetThreadsRequest request, StreamObserver<CpuProfiler.GetThreadsResponse> observer) {
    CpuProfiler.GetThreadsResponse.Builder response = CpuProfiler.GetThreadsResponse.newBuilder();

    long from = request.getStartTimestamp();
    long to = request.getEndTimestamp();

    for (CpuProfiler.GetThreadsResponse.Thread.Builder builder : myThreads.values()) {
      int count = builder.getActivitiesCount();
      if (count > 0) {
        // If they overlap
        CpuProfiler.GetThreadsResponse.ThreadActivity first = builder.getActivities(0);
        CpuProfiler.GetThreadsResponse.ThreadActivity last = builder.getActivities(count - 1);
        boolean include = first.getTimestamp() <= to && from <= last.getTimestamp();
        // If still alive.
        include = include || (last.getTimestamp() < from && last.getNewState() != CpuProfiler.GetThreadsResponse.State.DEAD);
        if (include) {
          response.addThreads(builder);
        }
      }
    }
    observer.onNext(response.build());
    observer.onCompleted();
  }

  @Override
  public void getTraceInfo(CpuProfiler.GetTraceInfoRequest request, StreamObserver<CpuProfiler.GetTraceInfoResponse> responseObserver) {
    CpuProfiler.GetTraceInfoResponse.Builder response = CpuProfiler.GetTraceInfoResponse.newBuilder();
    for (TraceData traceInfo : myTraces.values()) {
      CpuProfiler.TraceInfo trace = traceInfo.getTrace();
      // Get traces that overlap with the requested range.
      if (Math.max(trace.getFromTimestamp(), request.getFromTimestamp()) <= Math.min(trace.getFromTimestamp(), request.getToTimestamp())) {
        response.addTraceInfo(trace);
      }
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void startMonitoringApp(CpuProfiler.CpuStartRequest request, StreamObserver<CpuProfiler.CpuStartResponse> observer) {
    myProcessId = request.getAppId();
    synchronized (myLock) {
      myThreads.clear();
      myData.clear();
    }
    observer.onNext(myPollingService.startMonitoringApp(request));
    observer.onCompleted();
  }

  @Override
  public void stopMonitoringApp(CpuProfiler.CpuStopRequest request, StreamObserver<CpuProfiler.CpuStopResponse> observer) {
    myProcessId = -1;
    observer.onNext(myPollingService.stopMonitoringApp(request));
    observer.onCompleted();
  }

  @Override
  public void startProfilingApp(CpuProfiler.CpuProfilingAppStartRequest request,
                                StreamObserver<CpuProfiler.CpuProfilingAppStartResponse> observer) {
    // TODO: start time shouldn't be keep in a variable here, but passed through request/response instead.
    myStartTraceTimestamp = getCurrentDeviceTimeNs();
    observer.onNext(myPollingService.startProfilingApp(request));
    observer.onCompleted();
  }

  @Override
  public void stopProfilingApp(CpuProfiler.CpuProfilingAppStopRequest request,
                               StreamObserver<CpuProfiler.CpuProfilingAppStopResponse> observer) {
    CpuProfiler.CpuProfilingAppStopResponse response = myPollingService.stopProfilingApp(request);
    TraceData trace = new TraceData(response.getTraceId(), myStartTraceTimestamp, getCurrentDeviceTimeNs(), response.getTrace());
    myStartTraceTimestamp = -1;
    myTraces.put(response.getTraceId(), trace);
    observer.onNext(response);
    observer.onCompleted();
  }

  @Override
  public void getTrace(CpuProfiler.GetTraceRequest request, StreamObserver<CpuProfiler.GetTraceResponse> observer) {

    TraceData data = myTraces.get(request.getTraceId());
    CpuProfiler.GetTraceResponse.Builder builder = CpuProfiler.GetTraceResponse.newBuilder();
    if (data == null) {
      builder.setStatus(CpuProfiler.GetTraceResponse.Status.FAILURE);
    }
    else {
      builder.setStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS).setData(data.getData());
    }

    observer.onNext(builder.build());
    observer.onCompleted();
  }

  private long getCurrentDeviceTimeNs() {
   return myProfilerService.getTimes(Profiler.TimesRequest.getDefaultInstance()).getTimestampNs();
  }

  private static class TraceData {
    @NotNull
    private final ByteString myData;

    private CpuProfiler.TraceInfo myTrace;

    public TraceData(int traceId, long fromTimestamp, long toTimestamp, @NotNull ByteString data) {
      if (fromTimestamp < 0) {
        throw new IllegalArgumentException("Invalid trace timestamp");
      }
      myTrace = CpuProfiler.TraceInfo.newBuilder().setTraceId(traceId).setFromTimestamp(fromTimestamp).setToTimestamp(toTimestamp).build();
      myData = data;
    }

    @NotNull
    public CpuProfiler.TraceInfo getTrace() {
      return myTrace;
    }

    @NotNull
    public ByteString getData() {
      return myData;
    }
  }
}
