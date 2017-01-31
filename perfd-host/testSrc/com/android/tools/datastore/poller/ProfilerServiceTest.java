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

import com.android.tools.datastore.DataStorePollerTest;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.TestGrpcService;
import com.android.tools.datastore.service.ProfilerService;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf3jarjar.ByteString;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;

public class ProfilerServiceTest extends DataStorePollerTest {

  private static final String DEVICE_SERIAL = "SomeSerialId";

  private DataStoreService myDataStore = mock(DataStoreService.class);

  private ProfilerService myProfilerService = new ProfilerService(myDataStore, getPollTicker()::run);

  private static final String BYTES_ID_1 = "0123456789";
  private static final String BYTES_ID_2 = "9876543210";
  private static final String BAD_ID = "0000000000";
  private static final ByteString BYTES_1 = ByteString.copyFromUtf8("FILE_1");
  private static final ByteString BYTES_2 = ByteString.copyFromUtf8("FILE_2");

  private static final Map<String, ByteString> PAYLOAD_CACHE = new ImmutableMap.Builder<String, ByteString>().
    put(BYTES_ID_1, BYTES_1).
    put(BYTES_ID_2, BYTES_2).
    build();

  @Rule
  public TestGrpcService<FakeProfilerService> myService = new TestGrpcService<>(myProfilerService, new FakeProfilerService());

  @Before
  public void setUp() throws Exception {
    myProfilerService.startMonitoring(myService.getChannel());
  }

  @After
  public void tearDown() throws Exception {
    myDataStore.shutdown();
  }

  @Test
  public void testGetTimes() throws Exception {
    StreamObserver<Profiler.TimesResponse> observer = mock(StreamObserver.class);
    myProfilerService.getTimes(Profiler.TimesRequest.getDefaultInstance(), observer);
    validateResponse(observer, Profiler.TimesResponse.getDefaultInstance());
  }

  @Test
  public void testGetVersion() throws Exception {
    StreamObserver<Profiler.VersionResponse> observer = mock(StreamObserver.class);
    myProfilerService.getVersion(Profiler.VersionRequest.getDefaultInstance(), observer);
    validateResponse(observer, Profiler.VersionResponse.getDefaultInstance());
  }

  @Test
  public void testGetDevices() throws Exception {
    StreamObserver<Profiler.GetDevicesResponse> observer = mock(StreamObserver.class);
    Profiler.GetDevicesResponse expected = Profiler.GetDevicesResponse.newBuilder()
      .addDevice(Profiler.Device.newBuilder()
                   .setSerial(DEVICE_SERIAL)
                   .build())
      .build();
    myProfilerService.getDevices(Profiler.GetDevicesRequest.getDefaultInstance(), observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetProcesses() throws Exception {
    StreamObserver<Profiler.GetProcessesResponse> observer = mock(StreamObserver.class);
    Profiler.GetProcessesRequest request = Profiler.GetProcessesRequest.newBuilder()
      .setDeviceSerial(DEVICE_SERIAL)
      .build();
    Profiler.GetProcessesResponse expected = Profiler.GetProcessesResponse.newBuilder()
      .addProcess(Profiler.Process.getDefaultInstance())
      .build();
    myProfilerService.getProcesses(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetFile() throws Exception {
    StreamObserver<Profiler.BytesResponse> observer1 = mock(StreamObserver.class);
    Profiler.BytesRequest request1 = Profiler.BytesRequest.newBuilder().setId(BYTES_ID_1).build();
    Profiler.BytesResponse response1 = Profiler.BytesResponse.newBuilder().setContents(BYTES_1).build();
    myProfilerService.getBytes(request1, observer1);
    validateResponse(observer1, response1);

    StreamObserver<Profiler.BytesResponse> observer2 = mock(StreamObserver.class);
    Profiler.BytesRequest request2 = Profiler.BytesRequest.newBuilder().setId(BYTES_ID_2).build();
    Profiler.BytesResponse response2 = Profiler.BytesResponse.newBuilder().setContents(BYTES_2).build();
    myProfilerService.getBytes(request2, observer2);
    validateResponse(observer2, response2);

    StreamObserver<Profiler.BytesResponse> observerNoMatch = mock(StreamObserver.class);
    Profiler.BytesRequest requestBad =
      Profiler.BytesRequest.newBuilder().setId(BAD_ID).build();
    Profiler.BytesResponse responseNoMatch = Profiler.BytesResponse.getDefaultInstance();
    myProfilerService.getBytes(requestBad, observerNoMatch);
    validateResponse(observerNoMatch, responseNoMatch);
  }

  private static class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {
    @Override
    public void getTimes(Profiler.TimesRequest request, StreamObserver<Profiler.TimesResponse> responseObserver) {
      responseObserver.onNext(Profiler.TimesResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getVersion(Profiler.VersionRequest request, StreamObserver<Profiler.VersionResponse> responseObserver) {
      responseObserver.onNext(Profiler.VersionResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getBytes(Profiler.BytesRequest request, StreamObserver<Profiler.BytesResponse> responseObserver) {
      Profiler.BytesResponse.Builder builder = Profiler.BytesResponse.newBuilder();
      ByteString bytes = PAYLOAD_CACHE.get(request.getId());
      if (bytes != null) {
        builder.setContents(bytes);
      }
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getDevices(Profiler.GetDevicesRequest request, StreamObserver<Profiler.GetDevicesResponse> responseObserver) {
      responseObserver.onNext(Profiler.GetDevicesResponse.newBuilder().addDevice(Profiler.Device.newBuilder()
                                                                                   .setSerial(DEVICE_SERIAL)
                                                                                   .build()).build());
      responseObserver.onCompleted();
    }

    @Override
    public void getProcesses(Profiler.GetProcessesRequest request, StreamObserver<Profiler.GetProcessesResponse> responseObserver) {
      responseObserver.onNext(Profiler.GetProcessesResponse.newBuilder().addProcess(Profiler.Process.getDefaultInstance()).build());
      responseObserver.onCompleted();
    }
  }
}
