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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.google.protobuf3jarjar.ByteString;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.cpu.CpuCaptureTest.readValidTrace;
import static org.junit.Assert.fail;

/**
 * Dummy implementation of {@link CpuServiceGrpc.CpuServiceImplBase}.
 * This class is used by the tests of the {@link com.android.tools.profilers.cpu} package.
 */
public class FakeCpuService extends CpuServiceGrpc.CpuServiceImplBase {

  /**
   * Real tid of the main thread of the trace.
   */
  public static final int TRACE_TID = 516;

  public static final int TOTAL_ELAPSED_TIME = 100;

  private static final int FAKE_TRACE_ID = 6;

  private CpuProfiler.CpuProfilingAppStartResponse.Status myStartProfilingStatus = CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS;

  private CpuProfiler.CpuProfilingAppStopResponse.Status myStopProfilingStatus = CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS;

  private int myAppId;

  /**
   * Whether there is a valid trace in the getTraceInfo response.
   */
  private boolean myValidTrace;

  @Nullable
  private ByteString myTrace;

  private CpuCapture myCapture;

  private CpuProfiler.GetTraceResponse.Status myGetTraceResponseStatus;

  private int myAppTimeMs;

  private int mySystemTimeMs;

  private boolean myEmptyUsageData;

  private long myTraceThreadActivityBuffer;

  @Override
  public void startProfilingApp(CpuProfiler.CpuProfilingAppStartRequest request, StreamObserver<CpuProfiler.CpuProfilingAppStartResponse> responseObserver) {
    CpuProfiler.CpuProfilingAppStartResponse.Builder response = CpuProfiler.CpuProfilingAppStartResponse.newBuilder();
    response.setStatus(myStartProfilingStatus);
    if (!myStartProfilingStatus.equals(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS)) {
      response.setErrorMessage("StartProfilingApp error");
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void stopProfilingApp(CpuProfiler.CpuProfilingAppStopRequest request, StreamObserver<CpuProfiler.CpuProfilingAppStopResponse> responseObserver) {
    CpuProfiler.CpuProfilingAppStopResponse.Builder response = CpuProfiler.CpuProfilingAppStopResponse.newBuilder();
    response.setStatus(myStopProfilingStatus);
    if (!myStopProfilingStatus.equals(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS)) {
      response.setErrorMessage("StopProfilingApp error");
    }
    if (myValidTrace) {
      response.setTrace(getTrace());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  public void setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status status) {
    myStartProfilingStatus = status;
  }

  public void setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status status) {
    myStopProfilingStatus = status;
  }

  public void setValidTrace(boolean validTrace) {
    myValidTrace = validTrace;
  }

  private ByteString getTrace() {
    try {
      if (myTrace == null) {
        myTrace = readValidTrace();
      }
    } catch (IOException e) {
      fail("Unable to get a response trace file");
    }
    return myTrace;
  }

  @Override
  public void startMonitoringApp(CpuProfiler.CpuStartRequest request, StreamObserver<CpuProfiler.CpuStartResponse> responseObserver) {
    CpuProfiler.CpuStartResponse.Builder response = CpuProfiler.CpuStartResponse.newBuilder();
    myAppId = request.getAppId();

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void stopMonitoringApp(CpuProfiler.CpuStopRequest request, StreamObserver<CpuProfiler.CpuStopResponse> responseObserver) {
    CpuProfiler.CpuStopResponse.Builder response = CpuProfiler.CpuStopResponse.newBuilder();
    myAppId = request.getAppId();

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  public int getAppId() {
    return myAppId;
  }

  @Override
  public void getTraceInfo(CpuProfiler.GetTraceInfoRequest request, StreamObserver<CpuProfiler.GetTraceInfoResponse> responseObserver) {
    CpuProfiler.GetTraceInfoResponse.Builder response = CpuProfiler.GetTraceInfoResponse.newBuilder();
    if (myValidTrace) {
      response.addTraceInfo(CpuProfiler.TraceInfo.newBuilder().setTraceId(FAKE_TRACE_ID));
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getData(CpuProfiler.CpuDataRequest request, StreamObserver<CpuProfiler.CpuDataResponse> responseObserver) {
    CpuProfiler.CpuDataResponse.Builder response = CpuProfiler.CpuDataResponse.newBuilder();
    if (myEmptyUsageData) {
      // Add another type of data to the response
      CpuProfiler.ThreadActivities.Builder activities = CpuProfiler.ThreadActivities.newBuilder();
      response.addData(CpuProfiler.CpuProfilerData.newBuilder().setThreadActivities(activities));
    } else {
      // Add first usage data
      CpuProfiler.CpuUsageData.Builder cpuUsageData = CpuProfiler.CpuUsageData.newBuilder();
      cpuUsageData.setElapsedTimeInMillisec(0);
      cpuUsageData.setSystemCpuTimeInMillisec(0);
      cpuUsageData.setAppCpuTimeInMillisec(0);
      CpuProfiler.CpuProfilerData.Builder data = CpuProfiler.CpuProfilerData.newBuilder().setCpuUsage(cpuUsageData);
      response.addData(data);

      // Add second usage data.
      cpuUsageData = CpuProfiler.CpuUsageData.newBuilder();
      cpuUsageData.setElapsedTimeInMillisec(TOTAL_ELAPSED_TIME);
      cpuUsageData.setSystemCpuTimeInMillisec(mySystemTimeMs);
      cpuUsageData.setAppCpuTimeInMillisec(myAppTimeMs);
      data = CpuProfiler.CpuProfilerData.newBuilder().setCpuUsage(cpuUsageData);
      response.addData(data);
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  public void setAppTimeMs(int appTimeMs) {
    myAppTimeMs = appTimeMs;
  }

  public void setSystemTimeMs(int systemTimeMs) {
    mySystemTimeMs = systemTimeMs;
  }

  public void setEmptyUsageData(boolean emptyUsageData) {
    myEmptyUsageData = emptyUsageData;
  }

  @Override
  public void getThreads(CpuProfiler.GetThreadsRequest request, StreamObserver<CpuProfiler.GetThreadsResponse> responseObserver) {
    CpuProfiler.GetThreadsResponse.Builder response = CpuProfiler.GetThreadsResponse.newBuilder();
    if (myValidTrace) {
      response.addAllThreads(buildTraceThreads());
    } else {
      response.addAllThreads(buildThreads(request.getStartTimestamp(), request.getEndTimestamp()));
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getTrace(CpuProfiler.GetTraceRequest request, StreamObserver<CpuProfiler.GetTraceResponse> responseObserver) {
    CpuProfiler.GetTraceResponse.Builder response = CpuProfiler.GetTraceResponse.newBuilder();
    response.setStatus(myGetTraceResponseStatus);
    if (myTrace != null) {
      response.setData(myTrace);
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  /**
   * Create two threads that overlap for certain amount of time.
   * They are referred as thread1 and thread2 in the comments present in the tests.
   *
   * Thread1 is alive from 1s to 8s, while thread2 is alive from 6s to 15s.
   */
  private static List<CpuProfiler.GetThreadsResponse.Thread> buildThreads(long start, long end) {
    List<CpuProfiler.GetThreadsResponse.Thread> threads = new ArrayList<>();

    Range requestRange = new Range(start, end);

    Range thread1Range = new Range(TimeUnit.SECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(8));
    if (!thread1Range.getIntersection(requestRange).isEmpty()) {
      List<CpuProfiler.GetThreadsResponse.ThreadActivity> activitiesThread1 = new ArrayList<>();
      activitiesThread1.add(newActivity(TimeUnit.SECONDS.toNanos(1), CpuProfiler.GetThreadsResponse.State.RUNNING));
      activitiesThread1.add(newActivity(TimeUnit.SECONDS.toNanos(8), CpuProfiler.GetThreadsResponse.State.DEAD));
      threads.add(newThread(1, "Thread 1", activitiesThread1));
    }

    Range thread2Range = new Range(TimeUnit.SECONDS.toNanos(6), TimeUnit.SECONDS.toNanos(15));
    if (!thread2Range.getIntersection(requestRange).isEmpty()) {
      List<CpuProfiler.GetThreadsResponse.ThreadActivity> activitiesThread2 = new ArrayList<>();
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(6), CpuProfiler.GetThreadsResponse.State.RUNNING));
      // Make sure we handle an unexpected state.
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(8), CpuProfiler.GetThreadsResponse.State.STOPPED));
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(10), CpuProfiler.GetThreadsResponse.State.SLEEPING));
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(12), CpuProfiler.GetThreadsResponse.State.WAITING));
      activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(15), CpuProfiler.GetThreadsResponse.State.DEAD));
      threads.add(newThread(2, "Thread 2", activitiesThread2));
    }

    return threads;
  }

  /**
   * Create one thread with two activities: RUNNING (2 seconds before capture start) and SLEEPING (2 seconds after capture end).
   */
  private List<CpuProfiler.GetThreadsResponse.Thread> buildTraceThreads() {
    Range range = myCapture.getRange();
    long rangeMid = (long)(range.getMax() + range.getMin()) / 2;

    List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities = new ArrayList<>();
    activities.add(newActivity(TimeUnit.MICROSECONDS.toNanos(
      (long)range.getMin() + myTraceThreadActivityBuffer), CpuProfiler.GetThreadsResponse.State.RUNNING));
    activities.add(newActivity(TimeUnit.MICROSECONDS.toNanos(rangeMid), CpuProfiler.GetThreadsResponse.State.SLEEPING));

    return Collections.singletonList(newThread(TRACE_TID, "Trace tid", activities));
  }

  private static CpuProfiler.GetThreadsResponse.ThreadActivity newActivity(long timestampNs, CpuProfiler.GetThreadsResponse.State state) {
    CpuProfiler.GetThreadsResponse.ThreadActivity.Builder activity = CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder();
    activity.setNewState(state);
    activity.setTimestamp(timestampNs);
    return activity.build();
  }

  private static CpuProfiler.GetThreadsResponse.Thread newThread(
    int tid, String name, List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities) {
    CpuProfiler.GetThreadsResponse.Thread.Builder thread = CpuProfiler.GetThreadsResponse.Thread.newBuilder();
    thread.setTid(tid);
    thread.setName(name);
    thread.addAllActivities(activities);
    return thread.build();
  }

  /**
   * Sets difference, in seconds, between the first thread activity and the trace capture start time.
   */
  public void setTraceThreadActivityBuffer(int seconds) {
    myTraceThreadActivityBuffer = TimeUnit.SECONDS.toMicros(seconds);
  }

  public void setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status getTraceResponseStatus) {
    myGetTraceResponseStatus = getTraceResponseStatus;
  }

  public CpuCapture parseTraceFile() throws IOException {
    if (myTrace == null) {
      myTrace = readValidTrace();
    }
    if (myCapture == null) {
      myCapture = new CpuCapture(myTrace);
    }
    return myCapture;
  }
}


