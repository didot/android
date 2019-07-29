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
package com.android.tools.profilers.memory;

import static com.android.tools.profilers.memory.adapters.CaptureObject.DEFAULT_HEAP_ID;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.AllocatedClass;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationContextsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationContextsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationEvent;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSnapshotRequest;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo;
import com.android.tools.profiler.proto.MemoryProfiler.BatchAllocationSample;
import com.android.tools.profiler.proto.MemoryProfiler.BatchJNIGlobalRefEvent;
import com.android.tools.profiler.proto.MemoryProfiler.DumpDataRequest;
import com.android.tools.profiler.proto.MemoryProfiler.DumpDataResponse;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpInfo;
import com.android.tools.profiler.proto.MemoryProfiler.ImportHeapDumpRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ImportHeapDumpResponse;
import com.android.tools.profiler.proto.MemoryProfiler.ImportLegacyAllocationsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ImportLegacyAllocationsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.JNIGlobalReferenceEvent;
import com.android.tools.profiler.proto.MemoryProfiler.JNIGlobalRefsEventsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.LatestAllocationTimeRequest;
import com.android.tools.profiler.proto.MemoryProfiler.LatestAllocationTimeResponse;
import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationContextsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationEvent;
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
import com.android.tools.profiler.proto.MemoryProfiler.NativeBacktrace;
import com.android.tools.profiler.proto.MemoryProfiler.NativeCallStack;
import com.android.tools.profiler.proto.MemoryProfiler.ResolveNativeBacktraceRequest;
import com.android.tools.profiler.proto.MemoryProfiler.StackFrameInfoRequest;
import com.android.tools.profiler.proto.MemoryProfiler.StackFrameInfoResponse;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse.Status;
import com.android.tools.profiler.proto.MemoryProfiler.TriggerHeapDumpRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TriggerHeapDumpResponse;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import io.grpc.stub.StreamObserver;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FakeMemoryService extends MemoryServiceGrpc.MemoryServiceImplBase {

  private static long US_TO_NS = TimeUnit.MICROSECONDS.toNanos(1);
  // Live Allocation data constant - duration of each allocation event.
  private static long ALLOC_EVENT_DURATION_NS = 2 * US_TO_NS;
  // Live allocation data constant - number of class/method entries in our fake context data pool.
  private static int ALLOC_CONTEXT_NUM = 4;
  // Live allocation data constant - fixed object size.
  private static int ALLOC_SIZE = 1;
  // Live allocation data constant - class names that gets cycled/reused every |ALLOC_CONTEXT_NUM| us.
  private static final List<String> CONTEXT_CLASS_NAMES = Arrays.asList(
    "This.Is.Foo",
    "That.Is.Bar",
    "This.Also.Foo",
    "That.Also.Bar"
  );
  // Live allocation data constant - method names that gets cycled/reused every |ALLOC_CONTEXT_NUM| us.
  private static final List<String> CONTEXT_METHOD_NAMES = Arrays.asList(
    "FooMethodA",
    "BarMethodA",
    "FooMethodB",
    "BarMethodB"
  );
  // Difference between object tag and JNI reference value.
  private static final long JNI_REF_BASE = 0x50000000;

  private static final Long NATIVE_ADDRESSES_BASE = 0xBAADF00Dl;
  private static final List<String> NATIVE_FUNCTION_NAMES = Arrays.asList(
    "NativeNamespace::Foo::FooMethodA(string, int)",
    "NativeNamespace::Bar::BarMethodA(string, int)",
    "NativeNamespace::Foo::FooMethodB(string, int)",
    "NativeNamespace::Bar::BarMethodB(string, int)"
  );

  private static final List<String> NATIVE_SOURCE_FILE = Arrays.asList(
    "/a/path/to/sources/foo.cc",
    "/a/path/to/sources/bar.cc",
    "/a/path/to/sources/foo.h",
    "/a/path/to/sources/bar.h"
  );

  private static final List<String> NATIVE_MODULE_NAMES = Arrays.asList(
    "/data/app/com.example.sum-000==/lib/arm64/libfoo.so",
    "/data/app/com.example.sum-000==/lib/arm/libbar.so",
    "/data/app/com.example.sum-000==/lib/x86/libfoo.so",
    "/data/app/com.example.sum-000==/lib/x86_64/libbar.so"
  );

  private static final String SYSTEM_NATIVE_MODULE = "/system/lib64/libnativewindow.so";

  /**
   * By default assume we have all data available in tests - we go by [start time, end time) so we minus 1 from Long.MAX_VALUE.
   */
  private static final long DEFAULT_LATEST_ALLOCATION_TIME = Long.MAX_VALUE - 1;

  private Status myExplicitAllocationsStatus = null;
  private AllocationsInfo myExplicitAllocationsInfo = null;
  private TriggerHeapDumpResponse.Status myExplicitHeapDumpStatus = null;
  private HeapDumpInfo myExplicitHeapDumpInfo = null;
  private DumpDataResponse.Status myExplicitDumpDataStatus = null;
  private byte[] myExplicitSnapshotBuffer = null;
  private MemoryData myMemoryData = null;
  private ListHeapDumpInfosResponse.Builder myHeapDumpInfoBuilder = ListHeapDumpInfosResponse.newBuilder();
  private LegacyAllocationEventsResponse.Builder myAllocationEventsBuilder = LegacyAllocationEventsResponse.newBuilder();
  private AllocationContextsResponse.Builder myAllocationContextBuilder = AllocationContextsResponse.newBuilder();
  private int myTrackAllocationCount;
  private Common.Session mySession;
  private int mySamplingRate = 1;

  /**
   * Stores the return value for {@link #getLatestAllocationTime(LatestAllocationTimeRequest, StreamObserver)}.
   */
  private long myLatestAllocationTime = DEFAULT_LATEST_ALLOCATION_TIME;

  @Override
  public void startMonitoringApp(MemoryStartRequest request,
                                 StreamObserver<MemoryStartResponse> responseObserver) {

    mySession = request.getSession();
    responseObserver.onNext(MemoryStartResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void stopMonitoringApp(MemoryStopRequest request,
                                StreamObserver<MemoryStopResponse> responseObserver) {
    mySession = request.getSession();
    responseObserver.onNext(MemoryStopResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  public int getProcessId() {
    return mySession.getPid();
  }

  @Override
  public void trackAllocations(TrackAllocationsRequest request,
                               StreamObserver<TrackAllocationsResponse> response) {
    myTrackAllocationCount++;
    TrackAllocationsResponse.Builder builder = TrackAllocationsResponse.newBuilder();
    if (myExplicitAllocationsStatus != null) {
      builder.setStatus(myExplicitAllocationsStatus);
    }
    if (myExplicitAllocationsInfo != null) {
      builder.setInfo(myExplicitAllocationsInfo);
    }
    response.onNext(builder.build());
    response.onCompleted();
  }

  @Override
  public void importLegacyAllocations(ImportLegacyAllocationsRequest request,
                                      StreamObserver<ImportLegacyAllocationsResponse> response) {
    myExplicitAllocationsInfo = request.getInfo();
    myAllocationEventsBuilder = request.getAllocations().toBuilder();
    myAllocationContextBuilder = request.getContexts().toBuilder();
    myMemoryData = MemoryData.newBuilder().addAllocationsInfo(request.getInfo()).build();
    response.onNext(ImportLegacyAllocationsResponse.newBuilder().setStatus(ImportLegacyAllocationsResponse.Status.SUCCESS).build());
    response.onCompleted();
  }

  @Override
  public void getData(MemoryRequest request, StreamObserver<MemoryData> response) {
    response.onNext(myMemoryData != null ? myMemoryData
                                         : MemoryData.newBuilder().setEndTimestamp(request.getStartTime() + 1).build());
    response.onCompleted();
  }

  @Override
  public void getJvmtiData(MemoryRequest request, StreamObserver<MemoryData> responseObserver) {
    responseObserver
      .onNext(myMemoryData != null ? myMemoryData : MemoryData.newBuilder().setEndTimestamp(request.getStartTime() + 1).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getHeapDump(DumpDataRequest request, StreamObserver<DumpDataResponse> responseObserver) {
    DumpDataResponse.Builder response = DumpDataResponse.newBuilder();
    if (myExplicitDumpDataStatus != null) {
      response.setStatus(myExplicitDumpDataStatus);
    }
    if (myExplicitSnapshotBuffer != null) {
      response.setData(ByteString.copyFrom(myExplicitSnapshotBuffer));
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getLegacyAllocationEvents(LegacyAllocationEventsRequest request,
                                        StreamObserver<LegacyAllocationEventsResponse> responseObserver) {
    responseObserver.onNext(myAllocationEventsBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getLegacyAllocationContexts(LegacyAllocationContextsRequest request,
                                          StreamObserver<AllocationContextsResponse> responseObserver) {
    responseObserver.onNext(myAllocationContextBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void listHeapDumpInfos(ListDumpInfosRequest request,
                                StreamObserver<ListHeapDumpInfosResponse> response) {
    response.onNext(myHeapDumpInfoBuilder.build());
    response.onCompleted();
  }

  @Override
  public void importHeapDump(ImportHeapDumpRequest request, StreamObserver<ImportHeapDumpResponse> responseObserver) {
    ImportHeapDumpResponse.Builder responseBuilder = ImportHeapDumpResponse.newBuilder();
    myExplicitDumpDataStatus = DumpDataResponse.Status.SUCCESS;
    myExplicitHeapDumpInfo =
      HeapDumpInfo.newBuilder()
        .setSuccess(true)
        .build();
    myHeapDumpInfoBuilder.addInfos(myExplicitHeapDumpInfo);
    myExplicitSnapshotBuffer = request.getData().toByteArray();
    responseBuilder.setStatus(ImportHeapDumpResponse.Status.SUCCESS);
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void triggerHeapDump(TriggerHeapDumpRequest request,
                              StreamObserver<TriggerHeapDumpResponse> responseObserver) {
    TriggerHeapDumpResponse.Builder builder = TriggerHeapDumpResponse.newBuilder();
    if (myExplicitHeapDumpStatus != null) {
      builder.setStatus(myExplicitHeapDumpStatus);
    }
    if (myExplicitHeapDumpInfo != null) {
      builder.setInfo(myExplicitHeapDumpInfo);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getLatestAllocationTime(LatestAllocationTimeRequest request,
                                      StreamObserver<LatestAllocationTimeResponse> responseObserver) {
    responseObserver.onNext(LatestAllocationTimeResponse.newBuilder().setTimestamp(myLatestAllocationTime).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getAllocations(AllocationSnapshotRequest request,
                             StreamObserver<BatchAllocationSample> responseObserver) {
    boolean liveObjectsOnly = request.getLiveObjectsOnly();
    long startTime = Math.max(0, request.getStartTime());
    startTime = (long)Math.ceil(startTime / (float)US_TO_NS) * US_TO_NS;
    long endTime = request.getEndTime();
    BatchAllocationSample sample = getAllocationSample(liveObjectsOnly, startTime, endTime);
    responseObserver.onNext(sample);
    responseObserver.onCompleted();
  }

  /**
   * Generates a list of allocation events based on the request start + end time.
   * For every microsecond, an allocation event is created and uniquely tagged with the timestamp. The event generation starts
   * at t = 0, and each is expected to be deallocated |ALLOC_EVENT_DURATION_NS| later. Each allocation also references a class and
   * stack, with ids that are cycled every |ALLOC_CONTEXT_NUM| allocations.
   *
   * The following illustrates what the data looks like within {0,5}:
   * {0,2}, tag = 0, class tag = 1, stack id = 1
   * {1,3}, tag = 1, class tag = 2, stack id = 2
   * {2,4}, tag = 2, class tag = 3, stack id = 3
   * {3,5}, tag = 3, class tag = 4, stack id = 4
   * {4,6}, tag = 4, class tag = 1, stack id = 1
   * {5,7}, tag = 5, class tag = 2, stack id = 2
   */
  @NotNull
  private static BatchAllocationSample getAllocationSample(boolean liveObjectsOnly, long startTime, long endTime) {
    long timestamp = Long.MIN_VALUE;

    BatchAllocationSample.Builder sampleBuilder = BatchAllocationSample.newBuilder();
    for (long i = startTime; i < endTime; i += US_TO_NS) {
      // Skip instance creation for snapshot mode
      if (liveObjectsOnly && i < endTime - ALLOC_EVENT_DURATION_NS) {
        continue;
      }

      int tag = (int)(i / US_TO_NS);
      int contextId = tag % ALLOC_CONTEXT_NUM + 1;
      AllocationEvent event = AllocationEvent.newBuilder()
        .setAllocData(
          AllocationEvent.Allocation.newBuilder().setTag(tag).setSize(ALLOC_SIZE).setClassTag(contextId)
            .setStackId(contextId).setHeapId(DEFAULT_HEAP_ID).build())
        .setTimestamp(i).build();
      sampleBuilder.addEvents(event);
      timestamp = i;
    }

    for (long i = startTime; i < endTime; i += US_TO_NS) {
      // Skip instance creation for snapshot mode
      if (liveObjectsOnly) {
        break;
      }

      long allocTime = i - ALLOC_EVENT_DURATION_NS;
      // Do not create allocation events into the negatives.
      if (allocTime < 0) {
        continue;
      }
      int tag = (int)((i - ALLOC_EVENT_DURATION_NS) / US_TO_NS);
      int contextId = tag % ALLOC_CONTEXT_NUM + 1;
      AllocationEvent event = AllocationEvent.newBuilder()
        .setFreeData(
          AllocationEvent.Deallocation.newBuilder().setTag(tag).setSize(ALLOC_SIZE).setClassTag(contextId)
            .setStackId(contextId).setHeapId(DEFAULT_HEAP_ID).build())
        .setTimestamp(i).build();
      sampleBuilder.addEvents(event);
    }

    sampleBuilder.setTimestamp(timestamp);
    return sampleBuilder.build();
  }

  @Override
  public void getJNIGlobalRefsEvents(JNIGlobalRefsEventsRequest request,
                                     StreamObserver<BatchJNIGlobalRefEvent> responseObserver) {
    // Just add JNI references for all allocated object from the same interval.
    boolean liveObjectsOnly = request.getLiveObjectsOnly();
    BatchAllocationSample allocationSample = getAllocationSample(liveObjectsOnly, request.getStartTime(), request.getEndTime());
    BatchJNIGlobalRefEvent.Builder result = BatchJNIGlobalRefEvent.newBuilder();
    for (AllocationEvent allocEvent : allocationSample.getEventsList()) {
      if (allocEvent.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
        AllocationEvent.Allocation allocation = allocEvent.getAllocData();
        JNIGlobalReferenceEvent.Builder jniEvent = JNIGlobalReferenceEvent.newBuilder()
          .setEventType(JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF)
          .setObjectTag(allocation.getTag())
          .setRefValue(allocation.getTag() + JNI_REF_BASE)
          .setThreadId(allocation.getThreadId())
          .setTimestamp(allocEvent.getTimestamp())
          .setBacktrace(createBacktrace(allocation.getTag()));
        result.addEvents(jniEvent);
      }
      else if (allocEvent.getEventCase() == AllocationEvent.EventCase.FREE_DATA) {
        AllocationEvent.Deallocation deallocation = allocEvent.getFreeData();
        JNIGlobalReferenceEvent.Builder jniEvent = JNIGlobalReferenceEvent.newBuilder()
          .setEventType(JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF)
          .setObjectTag(deallocation.getTag())
          .setRefValue(deallocation.getTag() + JNI_REF_BASE)
          .setThreadId(deallocation.getThreadId())
          .setTimestamp(allocEvent.getTimestamp() - 1)
          .setBacktrace(createBacktrace(deallocation.getTag()));
        result.addEvents(jniEvent);
      }
    }
    result.setTimestamp(allocationSample.getTimestamp());
    responseObserver.onNext(result.build());
    responseObserver.onCompleted();
  }

  private static NativeBacktrace createBacktrace(int objTag) {
    NativeBacktrace.Builder result = NativeBacktrace.newBuilder();
    result.addAddresses(NATIVE_ADDRESSES_BASE + objTag);
    result.addAddresses(NATIVE_ADDRESSES_BASE + objTag + 1);
    return result.build();
  }

  @Override
  public void resolveNativeBacktrace(ResolveNativeBacktraceRequest request,
                                     StreamObserver<NativeCallStack> responseObserver) {
    NativeCallStack.Builder result = NativeCallStack.newBuilder();
    for (long addr : request.getBacktrace().getAddressesList()) {
      int index = (int)(addr - NATIVE_ADDRESSES_BASE) % ALLOC_CONTEXT_NUM;
      result.addFramesBuilder()
        .setModuleName(NATIVE_MODULE_NAMES.get(index))
        .setSymbolName(NATIVE_FUNCTION_NAMES.get(index))
        .setFileName(NATIVE_SOURCE_FILE.get(index))
        .setAddress(addr)
        .setLineNumber(index + 100)
        .setModuleOffset(addr);
    }

    /* Add an extra frame from a system module to check that such frames are ignored */
    result.addFramesBuilder()
      .setModuleName(SYSTEM_NATIVE_MODULE)
      .setSymbolName("system_symbol_name()")
      .setFileName("/path/android.cc")
      .setAddress(NATIVE_ADDRESSES_BASE)
      .setLineNumber(1)
      .setModuleOffset(NATIVE_ADDRESSES_BASE);

    responseObserver.onNext(result.build());
    responseObserver.onCompleted();
  }

  /**
   * This fake service call auto-generate a list of allocation contexts based on the request start + end time.
   * For simplicity, we only fake a small pool of allocation context data that starts at 0 and ends at |ALLOC_CONTEXT_NUM| us.
   * Subsequent fake allocation events are designed to reuse the same pool of data, so querying beyond |ALLOC_CONTEXT_NUM| us will
   * return empty data. Also note that class and stack ids are 1-based so a context entry at t = 0 would have an id of 1.
   *
   * The following illustrates what the data looks like within {0,5}
   * t = 0, class tag = 1 (CONTEXT_CLASS_NAMES[0]) stack id = 1 ({CONTEXT_METHOD_NAMES[0], CONTEXT_METHOD_NAMES[1]})
   * t = 1, class tag = 2 (CONTEXT_CLASS_NAMES[1]) stack id = 2 ({CONTEXT_METHOD_NAMES[1], CONTEXT_METHOD_NAMES[2]})
   * t = 2, class tag = 3 (CONTEXT_CLASS_NAMES[2]) stack id = 3 ({CONTEXT_METHOD_NAMES[2], CONTEXT_METHOD_NAMES[3]})
   * t = 3, class tag = 4 (CONTEXT_CLASS_NAMES[3]) stack id = 4 ({CONTEXT_METHOD_NAMES[3], CONTEXT_METHOD_NAMES[0]})
   */
  @Override
  public void getAllocationContexts(AllocationContextsRequest request,
                                    StreamObserver<AllocationContextsResponse> responseObserver) {
    AllocationContextsResponse.Builder contextBuilder = AllocationContextsResponse.newBuilder();
    long timestamp = Long.MIN_VALUE;
    long endTime = request.getEndTime();
    for (int i = 0; i < endTime; i += TimeUnit.MICROSECONDS.toNanos(1)) {
      int iAdjusted = (int)(i / TimeUnit.MICROSECONDS.toNanos(1));
      if (iAdjusted >= ALLOC_CONTEXT_NUM) {
        break;
      }

      // Add class.
      AllocatedClass allocClass =
        AllocatedClass.newBuilder().setClassId(iAdjusted + 1).setClassName(CONTEXT_CLASS_NAMES.get(iAdjusted)).build();
      contextBuilder.addAllocatedClasses(allocClass);

      // Add stack.
      AllocationStack.Builder stackBuilder = AllocationStack.newBuilder();
      stackBuilder.setStackId(iAdjusted + 1);
      AllocationStack.SmallFrameWrapper.Builder frameBuilder = AllocationStack.SmallFrameWrapper.newBuilder();
      for (int j = 0; j < 2; j++) {
        int id = (iAdjusted + j) % ALLOC_CONTEXT_NUM + 1; // valid method Id starts at 1
        frameBuilder.addFrames(AllocationStack.SmallFrame.newBuilder().setMethodId(id).setLineNumber(-1));
      }
      stackBuilder.setSmallStack(frameBuilder);
      contextBuilder.addAllocationStacks(stackBuilder);

      timestamp = i;
    }

    contextBuilder.setTimestamp(timestamp);
    responseObserver.onNext(contextBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getStackFrameInfo(StackFrameInfoRequest request,
                                StreamObserver<StackFrameInfoResponse> responseObserver) {
    int id = (int)request.getMethodId() - 1;  // compensate for +1 offset in method Id to get the correct index.
    StackFrameInfoResponse.Builder methodBuilder = StackFrameInfoResponse.newBuilder()
      .setClassName(CONTEXT_CLASS_NAMES.get(id))
      .setMethodName(CONTEXT_METHOD_NAMES.get(id));
    responseObserver.onNext(methodBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void setAllocationSamplingRate(MemoryProfiler.SetAllocationSamplingRateRequest request,
                                        StreamObserver<MemoryProfiler.SetAllocationSamplingRateResponse> responseObserver) {
    mySamplingRate = request.getSamplingRate().getSamplingNumInterval();
    responseObserver.onNext(MemoryProfiler.SetAllocationSamplingRateResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @NotNull
  public FakeMemoryService setExplicitAllocationsStatus(@Nullable Status status) {
    myExplicitAllocationsStatus = status;
    return this;
  }

  public FakeMemoryService setExplicitAllocationsInfo(AllocationsInfo.Status infoStatus,
                                                      long startTime, long endTime, boolean legacy) {
    myExplicitAllocationsInfo =
      AllocationsInfo.newBuilder().setStatus(infoStatus).setStartTime(startTime).setEndTime(endTime)
        .setLegacy(legacy).build();
    return this;
  }


  public FakeMemoryService setExplicitHeapDumpStatus(@Nullable TriggerHeapDumpResponse.Status status) {
    myExplicitHeapDumpStatus = status;
    return this;
  }

  public FakeMemoryService setExplicitHeapDumpInfo(long startTime, long endTime) {
    myExplicitHeapDumpInfo = HeapDumpInfo.newBuilder().setStartTime(startTime).setEndTime(endTime).build();
    return this;
  }

  public FakeMemoryService setMemoryData(@Nullable MemoryData data) {
    myMemoryData = data;
    return this;
  }

  public FakeMemoryService addExplicitHeapDumpInfo(@NotNull HeapDumpInfo info) {
    myHeapDumpInfoBuilder.addInfos(info);
    return this;
  }

  public FakeMemoryService setExplicitAllocationEvents(LegacyAllocationEventsResponse.Status status,
                                                       @NotNull List<LegacyAllocationEvent> events) {
    myAllocationEventsBuilder.setStatus(status);
    myAllocationEventsBuilder.addAllEvents(events);
    return this;
  }

  public FakeMemoryService addExplicitAllocationClass(int id, String name) {
    myAllocationContextBuilder.addAllocatedClasses(AllocatedClass.newBuilder().setClassId(id).setClassName(name).build());
    return this;
  }

  public FakeMemoryService addExplicitAllocationStack(String klass, String method, int line, int stackId) {
    myAllocationContextBuilder.addAllocationStacks(AllocationStack.newBuilder().setStackId(stackId).setFullStack(
      AllocationStack.StackFrameWrapper.newBuilder().addFrames(
        AllocationStack.StackFrame.newBuilder().setClassName(klass).setMethodName(method).setLineNumber(line).build()
      )));
    return this;
  }

  public FakeMemoryService setExplicitSnapshotBuffer(@NotNull byte[] bytes) {
    myExplicitSnapshotBuffer = bytes;
    return this;
  }

  public FakeMemoryService setExplicitDumpDataStatus(DumpDataResponse.Status status) {
    myExplicitDumpDataStatus = status;
    return this;
  }

  public int getTrackAllocationCount() {
    return myTrackAllocationCount;
  }

  public void resetTrackAllocationCount() {
    myTrackAllocationCount = 0;
  }

  public int getSamplingRate() {
    return mySamplingRate;
  }

  public void setLatestAllocationTime(long latestAllocationTime) {
    myLatestAllocationTime = latestAllocationTime;
  }

  public void resetLatestAllocationTime() {
    setLatestAllocationTime(DEFAULT_LATEST_ALLOCATION_TIME);
  }
}
