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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.datastore.DataStorePollerTest;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.FakeLogService;
import com.android.tools.datastore.TestGrpcService;
import com.android.tools.datastore.service.CpuService;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.CpuProfiler.CpuDataRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuDataResponse;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerConfiguration;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartResponse;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStopRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStopResponse;
import com.android.tools.profiler.proto.CpuProfiler.CpuStartRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuStartResponse;
import com.android.tools.profiler.proto.CpuProfiler.CpuStopRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuStopResponse;
import com.android.tools.profiler.proto.CpuProfiler.GetThreadsRequest;
import com.android.tools.profiler.proto.CpuProfiler.GetThreadsResponse;
import com.android.tools.profiler.proto.CpuProfiler.GetTraceInfoRequest;
import com.android.tools.profiler.proto.CpuProfiler.GetTraceInfoResponse;
import com.android.tools.profiler.proto.CpuProfiler.GetTraceRequest;
import com.android.tools.profiler.proto.CpuProfiler.GetTraceResponse;
import com.android.tools.profiler.proto.CpuProfiler.ProfilingStateRequest;
import com.android.tools.profiler.proto.CpuProfiler.ProfilingStateResponse;
import com.android.tools.profiler.proto.CpuProfiler.SaveTraceInfoRequest;
import com.android.tools.profiler.proto.CpuProfiler.TraceInfo;
import com.android.tools.profiler.proto.CpuProfiler.TraceInitiationType;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import io.grpc.stub.StreamObserver;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;

public class CpuDataPollerTest extends DataStorePollerTest {

  private static final int THREAD_ID = 4321;
  private static final int THREAD_ID_2 = 2222;
  private static final String THREAD_NAME = "Thread1";
  private static final String THREAD_NAME_2 = "Thread2";
  private static final long TRACE_ID = 1111L;
  private static final ByteString TRACE_DATA = ByteString.copyFrom("Test Data", Charset.defaultCharset());
  private static final long BASE_TIME_NS = TimeUnit.DAYS.toNanos(1);
  private static final long ONE_SECOND_MS = TimeUnit.SECONDS.toMillis(1);
  private static final long TEN_SECONDS_MS = TimeUnit.SECONDS.toMillis(10);
  private static final GetThreadsResponse.Thread THREAD1 = GetThreadsResponse.Thread
    .newBuilder()
    .setTid(THREAD_ID)
    .setName(THREAD_NAME)
    .addActivities(
      GetThreadsResponse.ThreadActivity
        .newBuilder().setNewState(Cpu.CpuThreadData.State.WAITING).setTimestamp(delayFromBase(0)).build())
    .addActivities(
      GetThreadsResponse.ThreadActivity
        .newBuilder().setNewState(Cpu.CpuThreadData.State.RUNNING).setTimestamp(delayFromBase(4)).build())
    .addActivities(
      GetThreadsResponse.ThreadActivity
        .newBuilder().setNewState(Cpu.CpuThreadData.State.STOPPED).setTimestamp(delayFromBase(5)).build())
    .addActivities(
      GetThreadsResponse.ThreadActivity
        .newBuilder().setNewState(Cpu.CpuThreadData.State.DEAD).setTimestamp(delayFromBase(15)).build())
    .build();
  private static final GetThreadsResponse.Thread THREAD2 = GetThreadsResponse.Thread
    .newBuilder().setTid(THREAD_ID_2).setName(THREAD_NAME_2).addActivities(
      GetThreadsResponse.ThreadActivity
        .newBuilder().setNewState(Cpu.CpuThreadData.State.RUNNING).setTimestamp(delayFromBase(3)).build())
    .build();

  private static final Cpu.CpuUsageData CPU_USAGE_DATA = Cpu.CpuUsageData
    .newBuilder()
    .setAppCpuTimeInMillisec(ONE_SECOND_MS)
    .setElapsedTimeInMillisec(TEN_SECONDS_MS)
    .setSystemCpuTimeInMillisec(ONE_SECOND_MS * 2)
    .setEndTimestamp(BASE_TIME_NS)
    .build();

  private DataStoreService myDataStoreService = mock(DataStoreService.class);
  private CpuService myCpuService = new CpuService(myDataStoreService, getPollTicker()::run, new FakeLogService());

  public TestName myTestName = new TestName();
  private FakeCpuService myFakeCpuService = new FakeCpuService();
  public TestGrpcService myService =
    new TestGrpcService(CpuDataPollerTest.class, myTestName, myCpuService, myFakeCpuService, new FakeTransportService());

  @Rule
  public RuleChain myChain = RuleChain.outerRule(myTestName).around(myService);

  @Before
  public void setUp() throws Exception {
    when(myDataStoreService.getCpuClient(anyLong())).thenReturn(CpuServiceGrpc.newBlockingStub(myService.getChannel()));
    startMonitoringApp();
  }

  @After
  public void tearDown() {
    stopMonitoringApp();
  }

  private void startMonitoringApp() {
    CpuStartRequest request = CpuStartRequest.newBuilder().setSession(SESSION).build();
    myCpuService.startMonitoringApp(request, mock(StreamObserver.class));
  }

  private void stopMonitoringApp() {
    CpuStopRequest request = CpuStopRequest.newBuilder().setSession(SESSION).build();
    myCpuService.stopMonitoringApp(request, mock(StreamObserver.class));
  }

  @Test
  public void testAppStoppedRequestHandled() {
    stopMonitoringApp();
    StreamObserver<CpuProfilingAppStartResponse> observer = mock(StreamObserver.class);
    myCpuService.startProfilingApp(CpuProfilingAppStartRequest.getDefaultInstance(), observer);
    validateResponse(observer, CpuProfilingAppStartResponse.getDefaultInstance());
  }

  @Test
  public void testGetDataInRange() {
    CpuDataRequest request = CpuDataRequest
      .newBuilder().setSession(SESSION).setStartTimestamp(0).setEndTimestamp(delayFromBase(10)).build();
    CpuDataResponse expectedResponse = CpuDataResponse.newBuilder().addData(CPU_USAGE_DATA).build();
    StreamObserver<CpuDataResponse> observer = mock(StreamObserver.class);
    myCpuService.getData(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetDataCachedResponse() {
    StreamObserver<CpuDataResponse> observer = mock(StreamObserver.class);

    CpuDataRequest request = CpuDataRequest
      .newBuilder().setSession(SESSION).setStartTimestamp(BASE_TIME_NS).setEndTimestamp(delayFromBase(20)).build();
    // Workaround to modify the expected response inside doAnswer.
    CpuDataResponse[] expectedResponse = new CpuDataResponse[1];
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuDataResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuDataResponse);
      // Store the response.
      expectedResponse[0] = (CpuDataResponse)invocation.getArguments()[0];
      return null;
    }).when(observer).onNext(any(CpuDataResponse.class));
    myCpuService.getData(request, observer);

    // Create a different request with the same arguments of the previous one.
    CpuDataRequest request2 = CpuDataRequest
      .newBuilder().setSession(SESSION).setStartTimestamp(BASE_TIME_NS).setEndTimestamp(delayFromBase(20)).build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuDataResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuDataResponse);
      // Make sure that getData respond with the same object, as it should have been cached.
      assertTrue(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(CpuDataResponse.class));
    myCpuService.getData(request2, observer);

    // Create yet another request with some different arguments than the previous ones.
    CpuDataRequest request3 = CpuDataRequest
      .newBuilder().setSession(SESSION).setStartTimestamp(BASE_TIME_NS).setEndTimestamp(delayFromBase(21)).build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a CpuDataResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof CpuDataResponse);
      // Make sure that getData respond with a different object, as the cached object had a different end timestamp.
      assertFalse(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(CpuDataResponse.class));
    myCpuService.getData(request3, observer);
  }

  @Test
  public void testGetDataExcludeStart() {
    CpuDataRequest request = CpuDataRequest
      .newBuilder().setSession(SESSION).setStartTimestamp(delayFromBase(11)).setEndTimestamp(Long.MAX_VALUE).build();
    CpuDataResponse expectedResponse = CpuDataResponse.newBuilder()
      .build();
    StreamObserver<CpuDataResponse> observer = mock(StreamObserver.class);
    myCpuService.getData(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void traceProfilerTypeShouldBeCorrectlySet() {
    CpuProfilerType traceType = CpuProfilerType.SIMPLEPERF;
    CpuProfilingAppStartRequest startRequest = CpuProfilingAppStartRequest
      .newBuilder().setConfiguration(CpuProfilerConfiguration.newBuilder().setProfilerType(traceType)).build();
    StreamObserver<CpuProfilingAppStartResponse> startObserver = mock(StreamObserver.class);
    myCpuService.startProfilingApp(startRequest, startObserver);
    CpuProfilingAppStopRequest stopRequest = CpuProfilingAppStopRequest.newBuilder().setProfilerType(traceType).build();
    StreamObserver<CpuProfilingAppStopResponse> stopObserver = mock(StreamObserver.class);
    myFakeCpuService.setStopProfilingAppStatus(CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.stopProfilingApp(stopRequest, stopObserver);

    GetTraceRequest request = GetTraceRequest.newBuilder().setTraceId(TRACE_ID).build();
    GetTraceResponse expectedResponse = GetTraceResponse
      .newBuilder().setStatus(GetTraceResponse.Status.SUCCESS).setData(TRACE_DATA).setProfilerType(traceType).build();
    StreamObserver<GetTraceResponse> observer = mock(StreamObserver.class);
    myCpuService.getTrace(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void traceIsOnlyPersistedInSuccessfulCaptures() {
    CpuProfilingAppStartRequest startRequest = CpuProfilingAppStartRequest.getDefaultInstance();
    StreamObserver<CpuProfilingAppStartResponse> startObserver = mock(StreamObserver.class);
    myCpuService.startProfilingApp(startRequest, startObserver);
    CpuProfilingAppStopRequest stopRequest = CpuProfilingAppStopRequest.getDefaultInstance();
    StreamObserver<CpuProfilingAppStopResponse> stopObserver = mock(StreamObserver.class);
    myFakeCpuService.setStopProfilingAppStatus(CpuProfilingAppStopResponse.Status.STOP_COMMAND_FAILED);
    myCpuService.stopProfilingApp(stopRequest, stopObserver);

    GetTraceRequest request = GetTraceRequest.newBuilder().setTraceId(TRACE_ID).build();
    // Trace should not be persisted. Therefore, the request should fail (failure status).
    GetTraceResponse expectedResponse = GetTraceResponse.newBuilder().setStatus(GetTraceResponse.Status.FAILURE).build();
    StreamObserver<GetTraceResponse> observer = mock(StreamObserver.class);
    myCpuService.getTrace(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetTraceValid() {
    CpuProfilingAppStartRequest startRequest = CpuProfilingAppStartRequest.getDefaultInstance();
    StreamObserver<CpuProfilingAppStartResponse> startObserver = mock(StreamObserver.class);
    myCpuService.startProfilingApp(startRequest, startObserver);
    CpuProfilingAppStopRequest stopRequest = CpuProfilingAppStopRequest.getDefaultInstance();
    StreamObserver<CpuProfilingAppStopResponse> stopObserver = mock(StreamObserver.class);
    myFakeCpuService.setStopProfilingAppStatus(CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.stopProfilingApp(stopRequest, stopObserver);

    GetTraceRequest request = GetTraceRequest.newBuilder().setTraceId(TRACE_ID).build();
    GetTraceResponse expectedResponse = GetTraceResponse
      .newBuilder().setStatus(GetTraceResponse.Status.SUCCESS).setData(TRACE_DATA).build();
    StreamObserver<GetTraceResponse> observer = mock(StreamObserver.class);
    myCpuService.getTrace(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testApiInitiatedTestHasFile() {
    myFakeCpuService.addTraceInfo(TraceInfo.newBuilder()
                                    .setFromTimestamp(BASE_TIME_NS)
                                    .setToTimestamp(BASE_TIME_NS)
                                    .setInitiationType(TraceInitiationType.INITIATED_BY_API)
                                    .setTraceId(TRACE_ID)
                                    .build());
    // Poll once more to make sure our service is going to know we're profiling.
    getPollTicker().run();
    GetTraceInfoRequest request = GetTraceInfoRequest
      .newBuilder().setSession(SESSION).setFromTimestamp(BASE_TIME_NS).setToTimestamp(Long.MAX_VALUE).build();
    GetTraceInfoResponse expectedResponse = GetTraceInfoResponse
      .newBuilder().addTraceInfo(
        TraceInfo.newBuilder().setInitiationType(TraceInitiationType.INITIATED_BY_API).setFromTimestamp(BASE_TIME_NS)
          .setToTimestamp(BASE_TIME_NS).setTraceId(TRACE_ID)
          .setTraceFilePath(String.format("%s/cpu_automated_trace_%d.trace", System.getProperty("java.io.tmpdir"), TRACE_ID)))
      .build();
    StreamObserver<GetTraceInfoResponse> observer = mock(StreamObserver.class);
    myCpuService.getTraceInfo(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetThreadsInRange() {
    GetThreadsRequest request = GetThreadsRequest
      .newBuilder().setSession(SESSION).setStartTimestamp(BASE_TIME_NS).setEndTimestamp(delayFromBase(20)).build();
    GetThreadsResponse expectedResponse = GetThreadsResponse
      .newBuilder()
      // Threads are returned ordered by id
      .addThreads(
        GetThreadsResponse.Thread
          .newBuilder().setTid(THREAD_ID_2).setName(THREAD_NAME_2)
          // Actual activity
          .addActivities(
            GetThreadsResponse.ThreadActivity
              .newBuilder().setNewState(Cpu.CpuThreadData.State.RUNNING).setTimestamp(delayFromBase(3)).build())
          .build())
      .addThreads(
        GetThreadsResponse.Thread
          .newBuilder().setTid(THREAD_ID).setName(THREAD_NAME)
          .addActivities(
            GetThreadsResponse.ThreadActivity
              .newBuilder().setNewState(Cpu.CpuThreadData.State.WAITING).setTimestamp(delayFromBase(0)).build())
          .addActivities(
            GetThreadsResponse.ThreadActivity
              .newBuilder().setNewState(Cpu.CpuThreadData.State.RUNNING).setTimestamp(delayFromBase(4)).build())
          .addActivities(
            GetThreadsResponse.ThreadActivity
              .newBuilder().setNewState(Cpu.CpuThreadData.State.STOPPED).setTimestamp(delayFromBase(5)).build())
          .addActivities(
            GetThreadsResponse.ThreadActivity
              .newBuilder().setNewState(Cpu.CpuThreadData.State.DEAD).setTimestamp(delayFromBase(15)).build())
          .build())
      .build();
    StreamObserver<GetThreadsResponse> observer = mock(StreamObserver.class);
    myCpuService.getThreads(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetThreadsCachedResponse() {
    StreamObserver<GetThreadsResponse> observer = mock(StreamObserver.class);

    GetThreadsRequest request = GetThreadsRequest
      .newBuilder().setSession(SESSION).setStartTimestamp(BASE_TIME_NS).setEndTimestamp(delayFromBase(20)).build();
    // Workaround to modify the expected response inside doAnswer.
    GetThreadsResponse[] expectedResponse = new GetThreadsResponse[1];
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a GetThreadsResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof GetThreadsResponse);
      // Store the response.
      expectedResponse[0] = (GetThreadsResponse)invocation.getArguments()[0];
      return null;
    }).when(observer).onNext(any(GetThreadsResponse.class));
    myCpuService.getThreads(request, observer);

    // Create a different request with the same arguments of the previous one.
    GetThreadsRequest request2 = GetThreadsRequest
      .newBuilder().setSession(SESSION).setStartTimestamp(BASE_TIME_NS).setEndTimestamp(delayFromBase(20)).build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a GetThreadsResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof GetThreadsResponse);
      // Make sure that getThreads respond with the same object, as it should have been cached.
      assertTrue(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(GetThreadsResponse.class));
    myCpuService.getThreads(request2, observer);

    // Create yet another request with some different arguments than the previous ones.
    GetThreadsRequest request3 = GetThreadsRequest
      .newBuilder().setSession(SESSION).setStartTimestamp(BASE_TIME_NS).setEndTimestamp(delayFromBase(21)).build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a GetThreadsResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof GetThreadsResponse);
      // Make sure that getThreads respond with a different object, as the cached object had a different end timestamp.
      assertFalse(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(GetThreadsResponse.class));
    myCpuService.getThreads(request3, observer);
  }

  @Test
  public void getDeadThreadBeforeRange() {
    long startTimestamp = delayFromBase(20);
    GetThreadsRequest request = GetThreadsRequest.newBuilder()
      .setSession(SESSION)
      .setStartTimestamp(startTimestamp)
      .setEndTimestamp(delayFromBase(40))
      .build();
    GetThreadsResponse expectedResponse = GetThreadsResponse
      .newBuilder()
      // THREAD_ID is not returned because it died before the requested range start
      .addThreads(
        GetThreadsResponse.Thread
          .newBuilder().setTid(THREAD_ID_2).setName(THREAD_NAME_2).addActivities(
          GetThreadsResponse.ThreadActivity
            .newBuilder()
            .setNewState(Cpu.CpuThreadData.State.RUNNING)
            // As there was no activity during the request range, the state of the thread
            // in the last initial states snapshot is set to the start timestamp of the
            // request.
            .setTimestamp(startTimestamp)
            .build())
          .build())
      .build();
    StreamObserver<GetThreadsResponse> observer = mock(StreamObserver.class);
    myCpuService.getThreads(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetTraceInfo() {
    SaveTraceInfoRequest saveRequest = SaveTraceInfoRequest
      .newBuilder()
      .setSession(SESSION)
      .setTraceInfo(TraceInfo.newBuilder().setFromTimestamp(BASE_TIME_NS).setToTimestamp(BASE_TIME_NS).setTraceId(TRACE_ID))
      .build();
    myCpuService.saveTraceInfo(saveRequest, mock(StreamObserver.class));

    GetTraceInfoRequest request = GetTraceInfoRequest
      .newBuilder().setSession(SESSION).setFromTimestamp(BASE_TIME_NS).setToTimestamp(Long.MAX_VALUE).build();
    GetTraceInfoResponse expectedResponse = GetTraceInfoResponse
      .newBuilder().addTraceInfo(
        TraceInfo.newBuilder().setFromTimestamp(BASE_TIME_NS).setToTimestamp(BASE_TIME_NS).setTraceId(TRACE_ID))
      .build();
    StreamObserver<GetTraceInfoResponse> observer = mock(StreamObserver.class);
    myCpuService.getTraceInfo(request, observer);
    validateResponse(observer, expectedResponse);
  }

  @Test
  public void testGetTraceInfoCachedResponse() {
    StreamObserver<GetTraceInfoResponse> observer = mock(StreamObserver.class);

    GetTraceInfoRequest request = GetTraceInfoRequest
      .newBuilder().setSession(SESSION).setFromTimestamp(BASE_TIME_NS).setToTimestamp(delayFromBase(20)).build();
    // Workaround to modify the expected response inside doAnswer.
    GetTraceInfoResponse[] expectedResponse = new GetTraceInfoResponse[1];
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a GetTraceInfoResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof GetTraceInfoResponse);
      // Store the response.
      expectedResponse[0] = (GetTraceInfoResponse)invocation.getArguments()[0];
      return null;
    }).when(observer).onNext(any(GetTraceInfoResponse.class));
    myCpuService.getTraceInfo(request, observer);

    // Create a different request with the same arguments of the previous one.
    GetTraceInfoRequest request2 = GetTraceInfoRequest
      .newBuilder().setSession(SESSION).setFromTimestamp(BASE_TIME_NS).setToTimestamp(delayFromBase(20)).build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a GetTraceInfoResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof GetTraceInfoResponse);
      // Make sure that getTraceInfo respond with the same object, as it should have been cached.
      assertTrue(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(GetTraceInfoResponse.class));
    myCpuService.getTraceInfo(request2, observer);

    // Create yet another request with some different arguments than the previous ones.
    GetTraceInfoRequest request3 = GetTraceInfoRequest
      .newBuilder().setSession(SESSION).setFromTimestamp(BASE_TIME_NS).setToTimestamp(delayFromBase(21)).build();
    doAnswer(invocation -> {
      // invocation.getArguments() should have just a GetTraceInfoResponse in its arguments.
      assertTrue(invocation.getArguments().length == 1);
      assertTrue(invocation.getArguments()[0] instanceof GetTraceInfoResponse);
      // Make sure that getThreads respond with a different object, as the cached object had a different end timestamp.
      assertFalse(expectedResponse[0] == invocation.getArguments()[0]);
      return null;
    }).when(observer).onNext(any(GetTraceInfoResponse.class));
    myCpuService.getTraceInfo(request3, observer);
  }

  private static long delayFromBase(int seconds) {
    return BASE_TIME_NS + TimeUnit.SECONDS.toNanos(seconds);
  }

  @Test
  public void stopMonitoringShouldSetBeingProfiledToFalse() {
    myFakeCpuService.setBeingProfiled(true);
    // Poll once more to make sure our service is going to know we're profiling.
    getPollTicker().run();

    // myLastCheckBeingProfiledTimestamp starts at 1 and is incremented each time checkAppProfilingState is called.
    // We called it once when startMonitoringApp and another time when calling getPollTicker().run().
    int expectedTimestamp = 2;
    ProfilingStateResponse expectedResponse = ProfilingStateResponse
      .newBuilder().setCheckTimestamp(expectedTimestamp).setBeingProfiled(true).build();

    ProfilingStateRequest request = ProfilingStateRequest.newBuilder().setSession(SESSION).build();
    StreamObserver<ProfilingStateResponse> observer = mock(StreamObserver.class);
    myCpuService.checkAppProfilingState(request, observer);
    validateResponse(observer, expectedResponse);

    // Stop monitoring (end session) while we're still "being profiled"
    stopMonitoringApp();

    // A response with max timestamp and being_profiled = false should be returned, as the session is ended.
    expectedResponse = ProfilingStateResponse.newBuilder().setCheckTimestamp(Long.MAX_VALUE).build();
    // Sanity check that getBeingProfiled is false. We do that because false is the default value and validateResponse fails if we
    // specify default values in the expected response.
    assertFalse(expectedResponse.getBeingProfiled());
    StreamObserver<ProfilingStateResponse> observer2 = mock(StreamObserver.class);
    myCpuService.checkAppProfilingState(request, observer2);
    validateResponse(observer2, expectedResponse);
  }

  private static class FakeTransportService extends TransportServiceGrpc.TransportServiceImplBase {

    @Override
    public void getCurrentTime(TimeRequest request, StreamObserver<TimeResponse> responseObserver) {
      responseObserver.onNext(TimeResponse.newBuilder().setTimestampNs(BASE_TIME_NS).build());
      responseObserver.onCompleted();
    }
  }

  private static class FakeCpuService extends CpuServiceGrpc.CpuServiceImplBase {

    private CpuProfilingAppStopResponse.Status myStopProfilingAppStatus;

    private boolean myIsBeingProfiled = false;

    private int myLastCheckBeingProfiledTimestamp = 1;

    private List<TraceInfo> myTraceInfoResponses = new ArrayList<>();

    public void addTraceInfo(TraceInfo info) {
      myTraceInfoResponses.add(info);
    }

    @Override
    public void checkAppProfilingState(ProfilingStateRequest request,
                                       StreamObserver<ProfilingStateResponse> responseObserver) {
      ProfilingStateResponse response = ProfilingStateResponse
        .newBuilder().setCheckTimestamp(myLastCheckBeingProfiledTimestamp++).setBeingProfiled(myIsBeingProfiled).build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void getData(CpuDataRequest request, StreamObserver<CpuDataResponse> responseObserver) {
      CpuDataResponse response = CpuDataResponse.newBuilder().addData(CPU_USAGE_DATA).build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void getThreads(GetThreadsRequest request, StreamObserver<GetThreadsResponse> responseObserver) {
      GetThreadsResponse.Builder response = GetThreadsResponse.newBuilder();
      response.addThreads(THREAD1).addThreads(THREAD2);

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getTraceInfo(GetTraceInfoRequest request, StreamObserver<GetTraceInfoResponse> responseObserver) {
      GetTraceInfoResponse.Builder response = GetTraceInfoResponse.newBuilder();
      if (!myTraceInfoResponses.isEmpty()) {
        response.addAllTraceInfo(myTraceInfoResponses);
        myTraceInfoResponses.clear();
      }
      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getTrace(GetTraceRequest request, StreamObserver<GetTraceResponse> responseObserver) {
      GetTraceResponse.Builder response = GetTraceResponse.newBuilder();

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void startMonitoringApp(CpuStartRequest request, StreamObserver<CpuStartResponse> responseObserver) {
      responseObserver.onNext(CpuStartResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void stopMonitoringApp(CpuStopRequest request, StreamObserver<CpuStopResponse> responseObserver) {
      responseObserver.onNext(CpuStopResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void startProfilingApp(CpuProfilingAppStartRequest request,
                                  StreamObserver<CpuProfilingAppStartResponse> responseObserver) {
      responseObserver.onNext(CpuProfilingAppStartResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void stopProfilingApp(CpuProfilingAppStopRequest request,
                                 StreamObserver<CpuProfilingAppStopResponse> responseObserver) {
      responseObserver.onNext(
        CpuProfilingAppStopResponse.newBuilder().setTraceId(TRACE_ID).setTrace(TRACE_DATA).setStatus(myStopProfilingAppStatus).build());
      responseObserver.onCompleted();
    }

    public void setStopProfilingAppStatus(CpuProfilingAppStopResponse.Status stopProfilingAppStatus) {
      myStopProfilingAppStatus = stopProfilingAppStatus;
    }

    public void setBeingProfiled(boolean beingProfiled) {
      myIsBeingProfiled = beingProfiled;
    }
  }
}
