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
package com.android.tools.datastore.database;

import com.android.tools.datastore.FakeLogService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack.StackFrame;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static com.android.tools.profiler.proto.MemoryProfiler.JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF;
import static com.google.common.truth.Truth.assertThat;

public class MemoryLiveAllocationTableTest extends DatabaseTest<MemoryLiveAllocationTable> {
  private static final Common.Session VALID_SESSION = Common.Session.newBuilder().setSessionId(1L).setStreamId(1234).setPid(1).build();
  private static final Common.Session INVALID_SESSION = Common.Session.newBuilder().setSessionId(-1L).setStreamId(4321).setPid(-1).build();

  // Live allocation test data
  private static final int HEAP0 = 0;
  private static final int HEAP1 = 1;
  private static final int STACK1 = 1;
  private static final int STACK2 = 2;
  private static final long METHOD1 = 10;
  private static final long METHOD2 = 11;
  private static final long METHOD3 = 12;
  private static final int THREAD1 = 100;
  private static final int THREAD2 = 101;
  private static final int THREAD3 = 103;
  private static final int CLASS1 = 1000;
  private static final int CLASS2 = 1001;
  private static final int KLASS1_INSTANCE1_TAG = 1002;
  private static final int KLASS1_INSTANCE2_TAG = 1003;
  private static final int KLASS2_INSTANCE1_TAG = 1004;
  private static final int KLASS2_INSTANCE2_TAG = 1005;
  private static final int LINE1 = 10000;
  private static final int LINE2 = 10001;
  private static final int LINE3 = 10002;
  private final List<Long> STACK_METHODS1 = Arrays.asList(METHOD1, METHOD2);
  private final List<Long> STACK_METHODS2 = Arrays.asList(METHOD2, METHOD3);
  private final List<Integer> STACK_LINES1 = Arrays.asList(LINE1, LINE2);
  private final List<Integer> STACK_LINES2 = Arrays.asList(LINE2, LINE3);
  private static final String THREAD1_NAME = "Thread1";
  private static final String THREAD2_NAME = "Thread2";
  private static final String METHOD1_NAME = "Method1";
  private static final String METHOD2_NAME = "Method2";
  private static final String METHOD3_NAME = "Method3";
  private static final String JNI_KLASS1_NAME = "Ljava/lang/Klass1;";
  private static final String JNI_KLASS2_NAME = "[[Ljava/lang/Klass2;";
  private static final String JNI_KLASS3_NAME = "[[[Ljava/lang/Klass3;";
  private static final String JAVA_KLASS1_NAME = "java.lang.Klass1";
  private static final String JAVA_KLASS2_NAME = "java.lang.Klass2[][]";
  private static final long CLASS1_TIME = 10;
  private static final long CLASS2_TIME = 15;
  private static final long STACK1_TIME = 12;
  private static final long STACK2_TIME = 17;
  private static final long THREAD1_TIME = 13;
  private static final long THREAD2_TIME = 18;
  private static final long NATIVE_ADDRESS1 = 1300;
  private static final long NATIVE_ADDRESS2 = 2300;
  private static final long NATIVE_ADDRESS3 = 3300;
  private static final long NATIVE_ADDRESS4 = 4300;
  private static final long NATIVE_LIB_OFFSET = 100;
  private static final String NATIVE_LIB1 = "/path/to/native/lib1.so";
  private static final String NATIVE_LIB2 = "/path/to/native/lib2.so";
  private static final String NATIVE_LIB3 = "/path/to/native/lib3.so";
  private static final long JNI_REF_VALUE1 = 2001;
  private static final long JNI_REF_VALUE2 = 2002;
  private static final long JNI_REF_VALUE3 = 2003;

  @Override
  @NotNull
  protected List<Consumer<MemoryLiveAllocationTable>> getTableQueryMethodsForVerification() {
    List<Consumer<MemoryLiveAllocationTable>> methodCalls = new ArrayList<>();
    Common.Session session = Common.Session.getDefaultInstance();
    methodCalls.add((table) -> {
      BatchAllocationSample batch = BatchAllocationSample.newBuilder().addEvents(
        AllocationEvent.newBuilder().setFreeData(AllocationEvent.Deallocation.newBuilder().setAllocTime(1).build())).build();
      table.insertAllocationData(session, batch);
    });
    methodCalls.add((table) -> {
      BatchJNIGlobalRefEvent batch =
        BatchJNIGlobalRefEvent.newBuilder().addEvents(JNIGlobalReferenceEvent.newBuilder().setEventType(CREATE_GLOBAL_REF)).build();
      table.insertJniReferenceData(session, batch);
    });
    methodCalls.add((table) -> table.insertMethodInfo(session, new ArrayList<>()));
    methodCalls.add((table) -> table.insertStackInfo(session, new ArrayList<>()));
    methodCalls.add((table) -> table.insertThreadInfo(session, new ArrayList<>()));
    methodCalls.add((table) -> table.getAllocationContexts(session, 0, 0));
    methodCalls.add((table) -> table.getAllocations(session, 0, 0));
    methodCalls.add((table) -> table.getJniReferencesEventsFromRange(session, 0, 0));
    methodCalls.add((table) -> table.getJniReferencesSnapshot(session, 0));
    methodCalls.add((table) -> table.getLatestDataTimestamp(session));
    methodCalls.add((table) -> table.getSnapshot(session, 0));
    methodCalls.add((table) -> table.getStackFrameInfo(session, 0));
    methodCalls.add((table) -> {
      NativeBacktrace backtrace = NativeBacktrace.newBuilder().addAddresses(1).build();
      table.resolveNativeBacktrace(session, backtrace);
    });
    methodCalls.add((table) -> table.insertOrReplaceAllocationSamplingRateEvent(session, AllocationSamplingRateEvent.getDefaultInstance()));
    methodCalls.add((table) -> table.getAllocationSamplingRateEvents(session.getSessionId(), 0, 0));
    return methodCalls;
  }

  @Override
  @NotNull
  protected MemoryLiveAllocationTable createTable() {
    return new MemoryLiveAllocationTable(new FakeLogService());
  }

  @Test
  public void testIgnoreDuplicatedAllocationData() {
    AllocationEvent alloc1 = AllocationEvent
      .newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(CLASS1))
      .setTimestamp(0)
      .build();
    AllocationEvent dupAlloc1 = AllocationEvent
      .newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(CLASS2))
      .setTimestamp(6)
      .build();

    BatchAllocationSample insertSample = BatchAllocationSample.newBuilder().addEvents(alloc1).addEvents(dupAlloc1).build();
    getTable().insertAllocationData(VALID_SESSION, insertSample);

    // A query that asks for live objects should return alloc1 and considered alloc2 duplicated.
    BatchAllocationSample querySample = getTable().getAllocations(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(querySample.getEventsCount()).isEqualTo(1);
    assertThat(querySample.getEvents(0)).isEqualTo(alloc1);
  }

  NativeBacktrace createBacktrace(long... addresses) {
    NativeBacktrace.Builder result = NativeBacktrace.newBuilder();
    for (long address : addresses) {
      result.addAddresses(address);
    }
    return result.build();
  }


  private static MemoryMap createMemoryMap() {
    MemoryMap.Builder memMap = MemoryMap.newBuilder();
    memMap.addRegions(MemoryMap.MemoryRegion
                        .newBuilder()
                        .setStartAddress((NATIVE_ADDRESS1 - NATIVE_LIB_OFFSET))
                        .setEndAddress(NATIVE_ADDRESS1 + NATIVE_LIB_OFFSET)
                        .setName(NATIVE_LIB1));

    memMap.addRegions(MemoryMap.MemoryRegion
                        .newBuilder()
                        .setStartAddress((NATIVE_ADDRESS2 - NATIVE_LIB_OFFSET))
                        .setEndAddress(NATIVE_ADDRESS2 + NATIVE_LIB_OFFSET)
                        .setName(NATIVE_LIB2));

    memMap.addRegions(MemoryMap.MemoryRegion
                        .newBuilder()
                        .setStartAddress((NATIVE_ADDRESS3 - NATIVE_LIB_OFFSET))
                        .setEndAddress(NATIVE_ADDRESS3 + NATIVE_LIB_OFFSET)
                        .setName(NATIVE_LIB3));

    // NATIVE_ADDRESS4 intentionally left unmapped
    return memMap.build();
  }

  @Test
  public void testRepeatedInsertAndQueryOfJniRefs() {
    long timestamp = 0;
    final int ITERATION_COUNT = 10;
    final int BATCH_SIZE = 1000;
    for (int iteration = 0; iteration < ITERATION_COUNT; iteration++) {
      BatchJNIGlobalRefEvent.Builder insertBatchBuilder = BatchJNIGlobalRefEvent.newBuilder();
      insertBatchBuilder.setMemoryMap(createMemoryMap());
      for (int counter = 1; counter <= BATCH_SIZE; counter++) {
        int seed = iteration * BATCH_SIZE * 2 + counter;
        JNIGlobalReferenceEvent alloc = JNIGlobalReferenceEvent
          .newBuilder()
          .setEventType(CREATE_GLOBAL_REF)
          .setObjectTag(KLASS1_INSTANCE1_TAG + seed)
          .setRefValue(JNI_REF_VALUE1 + seed)
          .setThreadId(THREAD1)
          .setBacktrace(createBacktrace(NATIVE_ADDRESS1, NATIVE_ADDRESS2))
          .setTimestamp(seed).build();
        insertBatchBuilder.addEvents(alloc);
      }

      for (int counter = 1; counter <= BATCH_SIZE; counter++) {
        int seed = iteration * BATCH_SIZE * 2 + counter;
        JNIGlobalReferenceEvent dealloc = JNIGlobalReferenceEvent
          .newBuilder()
          .setEventType(JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF)
          .setObjectTag(KLASS1_INSTANCE1_TAG + seed)
          .setRefValue(JNI_REF_VALUE1 + seed)
          .setThreadId(THREAD3)
          .setBacktrace(createBacktrace(NATIVE_ADDRESS2, NATIVE_ADDRESS1))
          .setTimestamp(seed + 1)
          .build();
        insertBatchBuilder.addEvents(dealloc);
      }

      BatchJNIGlobalRefEvent insertBatch = insertBatchBuilder.build();
      getTable().insertJniReferenceData(VALID_SESSION, insertBatch);

      BatchJNIGlobalRefEvent queryBatch =
        getTable().getJniReferencesEventsFromRange(VALID_SESSION, timestamp, Long.MAX_VALUE);
      timestamp = queryBatch.getTimestamp() + 1;
      assertThat(queryBatch.getEventsCount()).isEqualTo(insertBatch.getEventsCount());
      for (int i = 0; i < queryBatch.getEventsCount(); i++) {
        assertThat(queryBatch.getEvents(i)).isEqualTo(insertBatch.getEvents(i));
      }
    }
  }

  @Test
  public void testInsertAndQueryJniRefEvents() {
    // create jni ref for instance 1 at t=1
    JNIGlobalReferenceEvent alloc1 = JNIGlobalReferenceEvent
      .newBuilder()
      .setEventType(CREATE_GLOBAL_REF)
      .setObjectTag(KLASS1_INSTANCE1_TAG)
      .setRefValue(JNI_REF_VALUE1)
      .setThreadId(THREAD1)
      .setBacktrace(createBacktrace(NATIVE_ADDRESS1, NATIVE_ADDRESS2))
      .setTimestamp(1).build();

    // create jni ref for instance 2 at t=5
    JNIGlobalReferenceEvent alloc2 = JNIGlobalReferenceEvent
      .newBuilder()
      .setEventType(CREATE_GLOBAL_REF)
      .setObjectTag(KLASS1_INSTANCE2_TAG)
      .setRefValue(JNI_REF_VALUE2)
      .setThreadId(THREAD2)
      .setBacktrace(createBacktrace(NATIVE_ADDRESS3, NATIVE_ADDRESS4))
      .setTimestamp(5).build();

    // free jni ref for instance 1 at t=10
    JNIGlobalReferenceEvent dealloc1 = JNIGlobalReferenceEvent
      .newBuilder()
      .setEventType(JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF)
      .setObjectTag(KLASS1_INSTANCE1_TAG)
      .setRefValue(JNI_REF_VALUE1)
      .setThreadId(THREAD3)
      .setBacktrace(createBacktrace(NATIVE_ADDRESS2, NATIVE_ADDRESS1))
      .setTimestamp(10).build();

    BatchJNIGlobalRefEvent.Builder insertBatch = BatchJNIGlobalRefEvent.newBuilder();
    insertBatch.setMemoryMap(createMemoryMap());
    insertBatch.addEvents(alloc1).addEvents(alloc2).addEvents(dealloc1);
    getTable().insertJniReferenceData(VALID_SESSION, insertBatch.build());

    // Query all events
    BatchJNIGlobalRefEvent queryBatch = getTable().getJniReferencesEventsFromRange(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(queryBatch.getEventsCount()).isEqualTo(3);
    assertThat(queryBatch.getEvents(0)).isEqualTo(alloc1);
    assertThat(queryBatch.getEvents(1)).isEqualTo(alloc2);
    assertThat(queryBatch.getEvents(2)).isEqualTo(dealloc1);
    assertThat(queryBatch.getTimestamp()).isEqualTo(dealloc1.getTimestamp());

    // Query events within [5,7]
    queryBatch = getTable().getJniReferencesEventsFromRange(VALID_SESSION, 5, 7);
    assertThat(queryBatch.getEventsCount()).isEqualTo(1);
    assertThat(queryBatch.getEvents(0)).isEqualTo(alloc2);
    assertThat(queryBatch.getTimestamp()).isEqualTo(alloc2.getTimestamp());

    // Query events within [0,10]
    queryBatch = getTable().getJniReferencesEventsFromRange(VALID_SESSION, 0, 10);
    assertThat(queryBatch.getEventsCount()).isEqualTo(2);
    assertThat(queryBatch.getEvents(0)).isEqualTo(alloc1);
    assertThat(queryBatch.getEvents(1)).isEqualTo(alloc2);
    assertThat(queryBatch.getTimestamp()).isEqualTo(alloc2.getTimestamp());

    // Query events within [7,100]
    queryBatch = getTable().getJniReferencesEventsFromRange(VALID_SESSION, 7, 100);
    assertThat(queryBatch.getEventsCount()).isEqualTo(1);
    assertThat(queryBatch.getEvents(0)).isEqualTo(dealloc1);
    assertThat(queryBatch.getTimestamp()).isEqualTo(dealloc1.getTimestamp());

    // Query references alive at t=2. only alloc1
    queryBatch = getTable().getJniReferencesSnapshot(VALID_SESSION, 2);
    assertThat(queryBatch.getEventsCount()).isEqualTo(1);
    assertThat(queryBatch.getEvents(0)).isEqualTo(alloc1);
    assertThat(queryBatch.getTimestamp()).isEqualTo(alloc1.getTimestamp());

    // Query references alive at t=7. all of them
    queryBatch = getTable().getJniReferencesSnapshot(VALID_SESSION, 7);
    assertThat(queryBatch.getEventsCount()).isEqualTo(2);
    assertThat(queryBatch.getEvents(0)).isEqualTo(alloc1);
    assertThat(queryBatch.getEvents(1)).isEqualTo(alloc2);
    assertThat(queryBatch.getTimestamp()).isEqualTo(alloc2.getTimestamp());

    // Query references alive at t=100. only alloc2
    queryBatch = getTable().getJniReferencesSnapshot(VALID_SESSION, 100);
    assertThat(queryBatch.getEventsCount()).isEqualTo(1);
    assertThat(queryBatch.getEvents(0)).isEqualTo(alloc2);
    assertThat(queryBatch.getTimestamp()).isEqualTo(alloc2.getTimestamp());
  }

  @Test
  public void testJniRefEventsWithEmptyBacktrace() {
    JNIGlobalReferenceEvent alloc1 = JNIGlobalReferenceEvent
      .newBuilder()
      .setEventType(CREATE_GLOBAL_REF)
      .setObjectTag(KLASS1_INSTANCE1_TAG)
      .setRefValue(JNI_REF_VALUE1)
      .setThreadId(THREAD1)
      .setTimestamp(1).build();

    JNIGlobalReferenceEvent alloc2 = JNIGlobalReferenceEvent
      .newBuilder()
      .setEventType(CREATE_GLOBAL_REF)
      .setObjectTag(KLASS1_INSTANCE2_TAG)
      .setRefValue(JNI_REF_VALUE2)
      .setThreadId(THREAD2)
      .setBacktrace(createBacktrace(NATIVE_ADDRESS2, NATIVE_ADDRESS1))
      .setTimestamp(5).build();

    JNIGlobalReferenceEvent alloc3 = JNIGlobalReferenceEvent
      .newBuilder()
      .setEventType(CREATE_GLOBAL_REF)
      .setObjectTag(KLASS1_INSTANCE1_TAG)
      .setRefValue(JNI_REF_VALUE3)
      .setThreadId(THREAD3)
      .setTimestamp(10).build();

    BatchJNIGlobalRefEvent.Builder insertBatch = BatchJNIGlobalRefEvent.newBuilder();
    insertBatch.setMemoryMap(createMemoryMap());
    insertBatch.addEvents(alloc1).addEvents(alloc2).addEvents(alloc3);
    getTable().insertJniReferenceData(VALID_SESSION, insertBatch.build());

    // Query all events
    BatchJNIGlobalRefEvent queryBatch = getTable().getJniReferencesEventsFromRange(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(queryBatch.getEventsCount()).isEqualTo(3);
    assertThat(queryBatch.getEvents(0)).isEqualTo(alloc1);
    assertThat(queryBatch.getEvents(1)).isEqualTo(alloc2);
    assertThat(queryBatch.getEvents(2)).isEqualTo(alloc3);
  }


  @Test
  public void testInsertAndQueryAllocationData() {
    // A klass1 instance allocation event (t = 0)
    AllocationEvent alloc1 = AllocationEvent
      .newBuilder().setAllocData(
        AllocationEvent.Allocation
          .newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(CLASS1).setThreadId(THREAD1).setStackId(STACK1).setHeapId(HEAP0))
      .setTimestamp(0).build();
    // A klass1 instance deallocation event (t = 7)
    AllocationEvent dealloc1 = AllocationEvent
      .newBuilder().setFreeData(
        AllocationEvent.Deallocation
          .newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(CLASS1).setThreadId(THREAD1).setStackId(STACK1).setHeapId(HEAP0))
      .setTimestamp(7).build();
    // A klass2 instance allocation event (t = 6)
    AllocationEvent alloc2 = AllocationEvent
      .newBuilder().setAllocData(
        AllocationEvent.Allocation
          .newBuilder().setTag(KLASS2_INSTANCE1_TAG).setClassTag(CLASS2).setHeapId(HEAP1)).setTimestamp(6).build();

    BatchAllocationSample insertSample = BatchAllocationSample
      .newBuilder().addEvents(alloc1).addEvents(dealloc1).addEvents(alloc2).build();
    getTable().insertAllocationData(VALID_SESSION, insertSample);

    // A query that asks for live objects.
    BatchAllocationSample querySample = getTable().getAllocations(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(querySample.getEventsCount()).isEqualTo(3);
    assertThat(querySample.getEvents(0)).isEqualTo(alloc1);
    assertThat(querySample.getEvents(1)).isEqualTo(alloc2);
    assertThat(querySample.getEvents(2)).isEqualTo(dealloc1);
    assertThat(querySample.getTimestamp()).isEqualTo(dealloc1.getTimestamp());

    // A query that asks for live objects between t=0 and t=7
    querySample = getTable().getAllocations(VALID_SESSION, 0, 7);
    // .... should returns class data + both class instances
    assertThat(querySample.getEventsCount()).isEqualTo(2);
    assertThat(querySample.getEvents(0)).isEqualTo(alloc1);
    assertThat(querySample.getEvents(1)).isEqualTo(alloc2);
    assertThat(querySample.getTimestamp()).isEqualTo(alloc2.getTimestamp());

    // A query that asks for live objects between t=7 and t=MAX_VALUE
    querySample = getTable().getAllocations(VALID_SESSION, 7, Long.MAX_VALUE);
    // .... should return only the free event
    assertThat(querySample.getEventsCount()).isEqualTo(1);
    assertThat(querySample.getEvents(0)).isEqualTo(dealloc1);
    assertThat(querySample.getTimestamp()).isEqualTo(dealloc1.getTimestamp());

    // A query that asks for a snapshot at t == 6
    querySample = getTable().getSnapshot(VALID_SESSION, 6);
    // .... should return the first instance
    assertThat(querySample.getEventsCount()).isEqualTo(1);
    assertThat(querySample.getEvents(0)).isEqualTo(alloc1);
    assertThat(querySample.getTimestamp()).isEqualTo(alloc1.getTimestamp());

    // A query that asks for a snapshot at t == 7
    querySample = getTable().getSnapshot(VALID_SESSION, 7);
    // .... should return only the second instance
    assertThat(querySample.getEventsCount()).isEqualTo(1);
    assertThat(querySample.getEvents(0)).isEqualTo(alloc2);
    assertThat(querySample.getTimestamp()).isEqualTo(alloc2.getTimestamp());
  }

  @Test
  public void testLatestDataTimestamp() {
    assertThat(getTable().getLatestDataTimestamp(VALID_SESSION).getTimestamp()).isEqualTo(0);

    // A klass1 instance allocation event (t = 1)
    AllocationEvent alloc1 = AllocationEvent
      .newBuilder().setAllocData(
        AllocationEvent.Allocation
          .newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(CLASS1).setThreadId(THREAD1).setStackId(STACK1).setHeapId(HEAP0))
      .setTimestamp(1).build();
    getTable().insertAllocationData(VALID_SESSION, BatchAllocationSample.newBuilder().addEvents(alloc1).build());
    assertThat(getTable().getLatestDataTimestamp(VALID_SESSION).getTimestamp()).isEqualTo(1);

    // A klass1 instance deallocation event (t = 7)
    AllocationEvent dealloc1 = AllocationEvent
      .newBuilder().setFreeData(
        AllocationEvent.Deallocation
          .newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(CLASS1).setThreadId(THREAD1).setStackId(STACK1).setHeapId(HEAP0))
      .setTimestamp(7).build();
    getTable().insertAllocationData(VALID_SESSION, BatchAllocationSample.newBuilder().addEvents(dealloc1).build());
    assertThat(getTable().getLatestDataTimestamp(VALID_SESSION).getTimestamp()).isEqualTo(7);

    // A klass2 instance allocation event (t = 6)
    AllocationEvent alloc2 = AllocationEvent
      .newBuilder().setAllocData(
        AllocationEvent.Allocation
          .newBuilder().setTag(KLASS2_INSTANCE1_TAG).setClassTag(CLASS2).setHeapId(HEAP1))
      .setTimestamp(6).build();
    getTable().insertAllocationData(VALID_SESSION, BatchAllocationSample.newBuilder().addEvents(alloc2).build());
    assertThat(getTable().getLatestDataTimestamp(VALID_SESSION).getTimestamp()).isEqualTo(7);
  }

  @Test
  public void testIgnoreDuplicatedMethodInfo() {
    List<StackFrame> methodsToInsert = new ArrayList<>();
    StackFrame method1 = StackFrame.newBuilder().setMethodId(METHOD1).setMethodName(METHOD1_NAME).setClassName(JNI_KLASS1_NAME).build();
    StackFrame dupMethod1 = StackFrame.newBuilder().setMethodId(METHOD1).setMethodName(METHOD2_NAME).setClassName(JNI_KLASS2_NAME).build();
    methodsToInsert.add(method1);
    methodsToInsert.add(dupMethod1);
    getTable().insertMethodInfo(VALID_SESSION, methodsToInsert);

    // Valid cases
    StackFrameInfoResponse convertedMethod1 =
      StackFrameInfoResponse.newBuilder().setMethodName(METHOD1_NAME).setClassName(JAVA_KLASS1_NAME).build();
    assertThat(getTable().getStackFrameInfo(VALID_SESSION, METHOD1)).isEqualTo(convertedMethod1);
  }

  @Test
  public void testInsertAndQueryMethodInfo() {
    List<StackFrame> methodsToInsert = new ArrayList<>();
    StackFrame method1 = StackFrame.newBuilder().setMethodId(METHOD1).setMethodName(METHOD1_NAME).setClassName(JNI_KLASS1_NAME).build();
    StackFrame method2 = StackFrame.newBuilder().setMethodId(METHOD2).setMethodName(METHOD2_NAME).setClassName(JNI_KLASS2_NAME).build();
    methodsToInsert.add(method1);
    methodsToInsert.add(method2);

    getTable().insertMethodInfo(VALID_SESSION, methodsToInsert);

    // Valid cases
    StackFrameInfoResponse convertedMethod1 =
      StackFrameInfoResponse.newBuilder().setMethodName(METHOD1_NAME).setClassName(JAVA_KLASS1_NAME).build();
    StackFrameInfoResponse convertedMethod2 =
      StackFrameInfoResponse.newBuilder().setMethodName(METHOD2_NAME).setClassName(JAVA_KLASS2_NAME).build();
    assertThat(getTable().getStackFrameInfo(VALID_SESSION, METHOD1)).isEqualTo(convertedMethod1);
    assertThat(getTable().getStackFrameInfo(VALID_SESSION, METHOD2)).isEqualTo(convertedMethod2);

    // Non-existent methods / invalid pid
    assertThat(getTable().getStackFrameInfo(INVALID_SESSION, METHOD1))
      .isEqualTo(StackFrameInfoResponse.getDefaultInstance());
    assertThat(getTable().getStackFrameInfo(INVALID_SESSION, METHOD2))
      .isEqualTo(StackFrameInfoResponse.getDefaultInstance());
    assertThat(getTable().getStackFrameInfo(VALID_SESSION, METHOD3))
      .isEqualTo(StackFrameInfoResponse.getDefaultInstance());
  }

  @Test
  public void testPruningJniRefs() {
    getTable().setAllocationCountLimit(2);

    JNIGlobalReferenceEvent alloc1 = JNIGlobalReferenceEvent
      .newBuilder()
      .setEventType(CREATE_GLOBAL_REF)
      .setObjectTag(KLASS1_INSTANCE1_TAG)
      .setRefValue(JNI_REF_VALUE1)
      .setThreadId(THREAD1)
      .setBacktrace(createBacktrace(NATIVE_ADDRESS1, NATIVE_ADDRESS2))
      .setTimestamp(1).build();

    JNIGlobalReferenceEvent alloc2 = JNIGlobalReferenceEvent
      .newBuilder()
      .setEventType(CREATE_GLOBAL_REF)
      .setObjectTag(KLASS1_INSTANCE2_TAG)
      .setRefValue(JNI_REF_VALUE2)
      .setThreadId(THREAD1)
      .setBacktrace(createBacktrace(NATIVE_ADDRESS1, NATIVE_ADDRESS2))
      .setTimestamp(3).build();

    JNIGlobalReferenceEvent alloc3 = JNIGlobalReferenceEvent
      .newBuilder()
      .setEventType(CREATE_GLOBAL_REF)
      .setObjectTag(KLASS2_INSTANCE1_TAG)
      .setRefValue(JNI_REF_VALUE3)
      .setThreadId(THREAD1)
      .setBacktrace(createBacktrace(NATIVE_ADDRESS1, NATIVE_ADDRESS2))
      .setTimestamp(4).build();

    BatchJNIGlobalRefEvent insertBatch = BatchJNIGlobalRefEvent.newBuilder()
                                                               .addEvents(alloc1).addEvents(alloc2).addEvents(alloc3).build();
    getTable().insertJniReferenceData(VALID_SESSION, insertBatch);

    BatchJNIGlobalRefEvent queryBatch = getTable().getJniReferencesSnapshot(VALID_SESSION, 10);
    assertThat(queryBatch.getEventsCount()).isEqualTo(3);

    JNIGlobalReferenceEvent dealloc2 = JNIGlobalReferenceEvent
      .newBuilder()
      .setEventType(JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF)
      .setObjectTag(KLASS2_INSTANCE1_TAG)
      .setRefValue(JNI_REF_VALUE3)
      .setThreadId(THREAD1)
      .setBacktrace(createBacktrace(NATIVE_ADDRESS2, NATIVE_ADDRESS1))
      .setTimestamp(100).build();

    JNIGlobalReferenceEvent dealloc3 = JNIGlobalReferenceEvent
      .newBuilder()
      .setEventType(JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF)
      .setObjectTag(KLASS2_INSTANCE1_TAG)
      .setRefValue(JNI_REF_VALUE3)
      .setThreadId(THREAD1)
      .setBacktrace(createBacktrace(NATIVE_ADDRESS2, NATIVE_ADDRESS1))
      .setTimestamp(80).build();

    insertBatch = BatchJNIGlobalRefEvent.newBuilder()
                                        .addEvents(dealloc2).addEvents(dealloc3).build();
    getTable().insertJniReferenceData(VALID_SESSION, insertBatch);

    // At this time record about JNI_REF_VALUE3 should be pruned.
    queryBatch = getTable().getJniReferencesSnapshot(VALID_SESSION, 10);
    assertThat(queryBatch.getEventsCount()).isEqualTo(2);
    assertThat(queryBatch.getEvents(0)).isEqualTo(alloc1);
    assertThat(queryBatch.getEvents(1)).isEqualTo(alloc2);
  }

  @Test
  public void testPruningAllocationData() {
    getTable().setAllocationCountLimit(2);

    AllocationContextsResponse contextSample = getTable().getAllocationContexts(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(contextSample.getAllocatedClassesCount()).isEqualTo(0);

    AllocatedClass expectedKlass1 = AllocatedClass.newBuilder().setClassName(JAVA_KLASS1_NAME).setClassId(CLASS1).build();
    AllocatedClass expectedKlass2 = AllocatedClass.newBuilder().setClassName(JAVA_KLASS2_NAME).setClassId(CLASS2).build();

    // A class that is loaded since the beginning (t = 0)
    AllocationEvent klass1 = AllocationEvent.newBuilder().setClassData(expectedKlass1).setTimestamp(0).build();
    BatchAllocationSample insertSample = BatchAllocationSample.newBuilder().addEvents(klass1).build();
    getTable().insertAllocationData(VALID_SESSION, insertSample);
    contextSample = getTable().getAllocationContexts(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(contextSample.getAllocatedClassesCount()).isEqualTo(1);
    assertThat(contextSample.getAllocatedClasses(0)).isEqualTo(expectedKlass1);

    // A klass1 instance allocation event (t = 0)
    AllocationEvent alloc1 = AllocationEvent
      .newBuilder().setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG).setClassTag(CLASS1))
      .setTimestamp(0).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(alloc1).build();
    getTable().insertAllocationData(VALID_SESSION, insertSample);
    contextSample = getTable().getAllocationContexts(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(contextSample.getAllocatedClassesCount()).isEqualTo(1);
    assertThat(contextSample.getAllocatedClasses(0)).isEqualTo(expectedKlass1);

    BatchAllocationSample querySample = getTable().getAllocations(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(querySample.getEventsCount()).isEqualTo(1);
    assertThat(querySample.getEvents(0)).isEqualTo(alloc1);

    // A class that is loaded at t=5 (tag = 2)
    AllocationEvent klass2 = AllocationEvent.newBuilder().setClassData(expectedKlass2).setTimestamp(1).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(klass2).build();
    getTable().insertAllocationData(VALID_SESSION, insertSample);
    contextSample = getTable().getAllocationContexts(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(contextSample.getAllocatedClassesCount()).isEqualTo(2);
    assertThat(contextSample.getAllocatedClasses(0)).isEqualTo(expectedKlass1);
    assertThat(contextSample.getAllocatedClasses(1)).isEqualTo(expectedKlass2);
    querySample = getTable().getAllocations(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(querySample.getEventsCount()).isEqualTo(1);
    assertThat(querySample.getEvents(0)).isEqualTo(alloc1);

    // A klass2 instance allocation event (t = 2, tag = 101)
    AllocationEvent alloc2 = AllocationEvent
      .newBuilder().setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS2_INSTANCE1_TAG).setClassTag(CLASS2))
      .setTimestamp(2).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(alloc2).build();
    getTable().insertAllocationData(VALID_SESSION, insertSample);
    contextSample = getTable().getAllocationContexts(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(contextSample.getAllocatedClassesCount()).isEqualTo(2);
    assertThat(contextSample.getAllocatedClasses(0)).isEqualTo(expectedKlass1);
    assertThat(contextSample.getAllocatedClasses(1)).isEqualTo(expectedKlass2);
    querySample = getTable().getAllocations(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(querySample.getEventsCount()).isEqualTo(2);
    assertThat(querySample.getEvents(0)).isEqualTo(alloc1);
    assertThat(querySample.getEvents(1)).isEqualTo(alloc2);

    // A klass1 instance allocation event (t = 3, tag = 102)
    AllocationEvent alloc3 = AllocationEvent
      .newBuilder().setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS1_INSTANCE2_TAG).setClassTag(CLASS1)).setTimestamp(3)
      .build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(alloc3).build();
    getTable().insertAllocationData(VALID_SESSION, insertSample);
    contextSample = getTable().getAllocationContexts(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(contextSample.getAllocatedClassesCount()).isEqualTo(2);
    assertThat(contextSample.getAllocatedClasses(0)).isEqualTo(expectedKlass1);
    assertThat(contextSample.getAllocatedClasses(1)).isEqualTo(expectedKlass2);
    querySample = getTable().getAllocations(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(querySample.getEventsCount()).isEqualTo(3);
    assertThat(querySample.getEvents(0)).isEqualTo(alloc1);
    assertThat(querySample.getEvents(1)).isEqualTo(alloc2);
    assertThat(querySample.getEvents(2)).isEqualTo(alloc3);

    // A alloc1 instance deallocation event (t = 5, tag = 100)
    AllocationEvent dealloc1 = AllocationEvent
      .newBuilder().setFreeData(AllocationEvent.Deallocation.newBuilder().setTag(KLASS1_INSTANCE1_TAG)).setTimestamp(5).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(dealloc1).build();
    getTable().insertAllocationData(VALID_SESSION, insertSample);
    contextSample = getTable().getAllocationContexts(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(contextSample.getAllocatedClassesCount()).isEqualTo(2);
    assertThat(contextSample.getAllocatedClasses(0)).isEqualTo(expectedKlass1);
    assertThat(contextSample.getAllocatedClasses(1)).isEqualTo(expectedKlass2);
    querySample = getTable().getAllocations(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(querySample.getEventsCount()).isEqualTo(2);
    assertThat(querySample.getEvents(0)).isEqualTo(alloc2);
    assertThat(querySample.getEvents(1)).isEqualTo(alloc3);

    // A alloc2 instance deallocation event (t = 6, tag = 101)
    AllocationEvent dealloc2 = AllocationEvent
      .newBuilder().setFreeData(AllocationEvent.Deallocation.newBuilder().setTag(KLASS2_INSTANCE1_TAG).setClassTag(CLASS2))
      .setTimestamp(6).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(dealloc2).build();
    getTable().insertAllocationData(VALID_SESSION, insertSample);
    contextSample = getTable().getAllocationContexts(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(contextSample.getAllocatedClassesCount()).isEqualTo(2);
    assertThat(contextSample.getAllocatedClasses(0)).isEqualTo(expectedKlass1);
    assertThat(contextSample.getAllocatedClasses(1)).isEqualTo(expectedKlass2);
    querySample = getTable().getAllocations(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(querySample.getEventsCount()).isEqualTo(3);
    assertThat(querySample.getEvents(0)).isEqualTo(alloc2);
    assertThat(querySample.getEvents(1)).isEqualTo(alloc3);
    assertThat(querySample.getEvents(2)).isEqualTo(dealloc2);

    // A klass2 instance allocation event (t = 2, tag = 103)
    AllocationEvent alloc4 = AllocationEvent
      .newBuilder().setAllocData(AllocationEvent.Allocation.newBuilder().setTag(KLASS2_INSTANCE2_TAG).setClassTag(CLASS2))
      .setTimestamp(7).build();
    insertSample = BatchAllocationSample.newBuilder().addEvents(alloc4).build();
    getTable().insertAllocationData(VALID_SESSION, insertSample);
    contextSample = getTable().getAllocationContexts(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(contextSample.getAllocatedClassesCount()).isEqualTo(2);
    assertThat(contextSample.getAllocatedClasses(0)).isEqualTo(expectedKlass1);
    assertThat(contextSample.getAllocatedClasses(1)).isEqualTo(expectedKlass2);
    querySample = getTable().getAllocations(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(querySample.getEventsCount()).isEqualTo(2);
    assertThat(querySample.getEvents(0)).isEqualTo(alloc3);
    assertThat(querySample.getEvents(1)).isEqualTo(alloc4);
  }

  @Test
  public void testIgnoreDuplicatedAllocationContext() {
    List<StackFrame> methodsToInsert = new ArrayList<>();
    StackFrame method1 = StackFrame.newBuilder().setMethodId(METHOD1).setMethodName(METHOD1_NAME).setClassName(JNI_KLASS1_NAME).build();
    StackFrame method2 = StackFrame.newBuilder().setMethodId(METHOD2).setMethodName(METHOD2_NAME).setClassName(JNI_KLASS2_NAME).build();
    methodsToInsert.add(method1);
    methodsToInsert.add(method2);

    List<EncodedAllocationStack> stacksToInsert = new ArrayList<>();
    EncodedAllocationStack stack1 =
      EncodedAllocationStack.newBuilder().setStackId(STACK1).addAllMethodIds(STACK_METHODS1).addAllLineNumbers(STACK_LINES1)
                            .setTimestamp(STACK1_TIME).build();
    EncodedAllocationStack dupStack1 =
      EncodedAllocationStack.newBuilder().setStackId(STACK1).addAllMethodIds(STACK_METHODS2).addAllLineNumbers(STACK_LINES2)
                            .setTimestamp(STACK2_TIME).build();
    stacksToInsert.add(stack1);
    stacksToInsert.add(dupStack1);

    List<ThreadInfo> threadsToInsert = new ArrayList<>();
    ThreadInfo thread1 = ThreadInfo.newBuilder().setThreadId(THREAD1).setThreadName(THREAD1_NAME).setTimestamp(THREAD1_TIME).build();
    ThreadInfo dupThread1 = ThreadInfo.newBuilder().setThreadId(THREAD1).setThreadName(THREAD2_NAME).setTimestamp(THREAD2_TIME).build();
    threadsToInsert.add(thread1);
    threadsToInsert.add(dupThread1);

    BatchAllocationSample.Builder classesBuilder = BatchAllocationSample.newBuilder();
    AllocatedClass class1 = AllocatedClass.newBuilder().setClassId(CLASS1).setClassName(JNI_KLASS1_NAME).build();
    AllocatedClass dupClass1 = AllocatedClass.newBuilder().setClassId(CLASS1).setClassName(JNI_KLASS2_NAME).build();
    classesBuilder.addEvents(AllocationEvent.newBuilder().setTimestamp(CLASS1_TIME).setClassData(class1));
    classesBuilder.addEvents(AllocationEvent.newBuilder().setTimestamp(CLASS2_TIME).setClassData(dupClass1));

    // Insert handcrafted data.
    getTable().insertMethodInfo(VALID_SESSION, methodsToInsert);
    getTable().insertStackInfo(VALID_SESSION, stacksToInsert);
    getTable().insertThreadInfo(VALID_SESSION, threadsToInsert);
    getTable().insertAllocationData(VALID_SESSION, classesBuilder.build());

    AllocatedClass expectedKlass1 = class1.toBuilder().setClassName(JAVA_KLASS1_NAME).build();
    AllocationStack expectedStack1 = AllocationStack
      .newBuilder()
      .setStackId(STACK1)
      .setSmallStack(
        AllocationStack.SmallFrameWrapper
          .newBuilder().addFrames(AllocationStack.SmallFrame.newBuilder().setMethodId(METHOD1).setLineNumber(LINE1))
          .addFrames(AllocationStack.SmallFrame.newBuilder().setMethodId(METHOD2).setLineNumber(LINE2)))
      .build();
    ThreadInfo expectedThread = ThreadInfo.newBuilder().setThreadId(THREAD1).setThreadName(THREAD1_NAME).build();

    AllocationContextsResponse contexts = getTable().getAllocationContexts(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(contexts.getAllocatedClassesCount()).isEqualTo(1);
    assertThat(contexts.getAllocatedClasses(0)).isEqualTo(expectedKlass1);
    assertThat(contexts.getAllocationStacksCount()).isEqualTo(1);
    assertThat(contexts.getAllocationStacks(0)).isEqualTo(expectedStack1);
    assertThat(contexts.getAllocationThreadsCount()).isEqualTo(1);
    assertThat(contexts.getAllocationThreads(0)).isEqualTo(expectedThread);
  }

  @Test
  public void testAllocationContextQueriesAfterInsertion() {
    List<StackFrame> methodsToInsert = new ArrayList<>();
    StackFrame method1 = StackFrame.newBuilder().setMethodId(METHOD1).setMethodName(METHOD1_NAME).setClassName(JNI_KLASS1_NAME).build();
    StackFrame method2 = StackFrame.newBuilder().setMethodId(METHOD2).setMethodName(METHOD2_NAME).setClassName(JNI_KLASS2_NAME).build();
    StackFrame method3 = StackFrame.newBuilder().setMethodId(METHOD3).setMethodName(METHOD3_NAME).setClassName(JNI_KLASS3_NAME).build();
    methodsToInsert.add(method1);
    methodsToInsert.add(method2);
    methodsToInsert.add(method3);

    List<EncodedAllocationStack> stacksToInsert = new ArrayList<>();
    EncodedAllocationStack stack1 =
      EncodedAllocationStack.newBuilder().setStackId(STACK1).addAllMethodIds(STACK_METHODS1).addAllLineNumbers(STACK_LINES1)
                            .setTimestamp(STACK1_TIME).build();
    EncodedAllocationStack stack2 =
      EncodedAllocationStack.newBuilder().setStackId(STACK2).addAllMethodIds(STACK_METHODS2).addAllLineNumbers(STACK_LINES2)
                            .setTimestamp(STACK2_TIME).build();
    stacksToInsert.add(stack1);
    stacksToInsert.add(stack2);

    List<ThreadInfo> threadsToInsert = new ArrayList<>();
    ThreadInfo thread1 = ThreadInfo.newBuilder().setThreadId(THREAD1).setThreadName(THREAD1_NAME).setTimestamp(THREAD1_TIME).build();
    ThreadInfo thread2 = ThreadInfo.newBuilder().setThreadId(THREAD2).setThreadName(THREAD2_NAME).setTimestamp(THREAD2_TIME).build();
    threadsToInsert.add(thread1);
    threadsToInsert.add(thread2);

    BatchAllocationSample.Builder classesBuilder = BatchAllocationSample.newBuilder();
    AllocatedClass class1 = AllocatedClass.newBuilder().setClassId(CLASS1).setClassName(JNI_KLASS1_NAME).build();
    AllocatedClass class2 = AllocatedClass.newBuilder().setClassId(CLASS2).setClassName(JNI_KLASS2_NAME).build();
    classesBuilder.addEvents(AllocationEvent.newBuilder().setTimestamp(CLASS1_TIME).setClassData(class1));
    classesBuilder.addEvents(AllocationEvent.newBuilder().setTimestamp(CLASS2_TIME).setClassData(class2));

    // Insert handcrafted data.
    getTable().insertMethodInfo(VALID_SESSION, methodsToInsert);
    getTable().insertStackInfo(VALID_SESSION, stacksToInsert);
    getTable().insertThreadInfo(VALID_SESSION, threadsToInsert);
    getTable().insertAllocationData(VALID_SESSION, classesBuilder.build());

    AllocatedClass expectedKlass1 = class1.toBuilder().setClassName(JAVA_KLASS1_NAME).build();
    AllocatedClass expectedKlass2 = class2.toBuilder().setClassName(JAVA_KLASS2_NAME).build();

    AllocationStack expectedStack1 = AllocationStack
      .newBuilder()
      .setStackId(STACK1)
      .setSmallStack(
        AllocationStack.SmallFrameWrapper
          .newBuilder().addFrames(AllocationStack.SmallFrame.newBuilder().setMethodId(METHOD1).setLineNumber(LINE1))
          .addFrames(AllocationStack.SmallFrame.newBuilder().setMethodId(METHOD2).setLineNumber(LINE2)))
      .build();
    AllocationStack expectedStack2 = AllocationStack
      .newBuilder()
      .setStackId(STACK2)
      .setSmallStack(
        AllocationStack.SmallFrameWrapper
          .newBuilder().addFrames(AllocationStack.SmallFrame.newBuilder().setMethodId(METHOD2).setLineNumber(LINE2))
          .addFrames(AllocationStack.SmallFrame.newBuilder().setMethodId(METHOD3).setLineNumber(LINE3)))
      .build();
    ThreadInfo expectedThread1 = ThreadInfo.newBuilder().setThreadId(THREAD1).setThreadName(THREAD1_NAME).build();
    ThreadInfo expectedThread2 = ThreadInfo.newBuilder().setThreadId(THREAD2).setThreadName(THREAD2_NAME).build();

    AllocationContextsResponse contexts = getTable().getAllocationContexts(VALID_SESSION, 0, Long.MAX_VALUE);
    assertThat(contexts.getAllocatedClassesCount()).isEqualTo(2);
    assertThat(contexts.getAllocatedClasses(0)).isEqualTo(expectedKlass1);
    assertThat(contexts.getAllocatedClasses(1)).isEqualTo(expectedKlass2);
    assertThat(contexts.getAllocationStacksCount()).isEqualTo(2);
    assertThat(contexts.getAllocationStacks(0)).isEqualTo(expectedStack1);
    assertThat(contexts.getAllocationStacks(1)).isEqualTo(expectedStack2);
    assertThat(contexts.getAllocationThreadsCount()).isEqualTo(2);
    assertThat(contexts.getAllocationThreads(0)).isEqualTo(expectedThread1);
    assertThat(contexts.getAllocationThreads(1)).isEqualTo(expectedThread2);

    // Timestamp should be set to the latest AllocatedClass's. This is because Stacks/Methods/Threads are inserted first, and their
    // timestamps can be ahead of classes that are going to be inserted after. If we use those timestamps as the start point of subsequent
    // context queries, we might miss some classes.
    assertThat(contexts.getTimestamp()).isEqualTo(CLASS2_TIME);
  }

  @Test
  public void testJNIPrimitiveTypesConversion() {
    BatchAllocationSample.Builder classesBuilder = BatchAllocationSample.newBuilder();
    AllocatedClass boolClass = AllocatedClass.newBuilder().setClassId(1).setClassName("Z").build();
    AllocatedClass byteClass = AllocatedClass.newBuilder().setClassId(2).setClassName("B").build();
    AllocatedClass charClass = AllocatedClass.newBuilder().setClassId(3).setClassName("C").build();
    AllocatedClass shortClass = AllocatedClass.newBuilder().setClassId(4).setClassName("S").build();
    AllocatedClass intClass = AllocatedClass.newBuilder().setClassId(5).setClassName("I").build();
    AllocatedClass longClass = AllocatedClass.newBuilder().setClassId(6).setClassName("J").build();
    AllocatedClass floatClass = AllocatedClass.newBuilder().setClassId(7).setClassName("F").build();
    AllocatedClass doubleClass = AllocatedClass.newBuilder().setClassId(8).setClassName("D").build();

    classesBuilder.addEvents(AllocationEvent.newBuilder().setTimestamp(1).setClassData(boolClass));
    classesBuilder.addEvents(AllocationEvent.newBuilder().setTimestamp(2).setClassData(byteClass));
    classesBuilder.addEvents(AllocationEvent.newBuilder().setTimestamp(3).setClassData(charClass));
    classesBuilder.addEvents(AllocationEvent.newBuilder().setTimestamp(4).setClassData(shortClass));
    classesBuilder.addEvents(AllocationEvent.newBuilder().setTimestamp(5).setClassData(intClass));
    classesBuilder.addEvents(AllocationEvent.newBuilder().setTimestamp(6).setClassData(longClass));
    classesBuilder.addEvents(AllocationEvent.newBuilder().setTimestamp(7).setClassData(floatClass));
    classesBuilder.addEvents(AllocationEvent.newBuilder().setTimestamp(8).setClassData(doubleClass));

    getTable().insertAllocationData(VALID_SESSION, classesBuilder.build());

    AllocationContextsResponse contexts = getTable().getAllocationContexts(VALID_SESSION, 1, 2);
    assertThat(contexts.getAllocatedClassesCount()).isEqualTo(1);
    assertThat(contexts.getAllocatedClasses(0)).isEqualTo(boolClass.toBuilder().setClassName("boolean").build());

    contexts = getTable().getAllocationContexts(VALID_SESSION, 2, 3);
    assertThat(contexts.getAllocatedClassesCount()).isEqualTo(1);
    assertThat(contexts.getAllocatedClasses(0)).isEqualTo(byteClass.toBuilder().setClassName("byte").build());

    contexts = getTable().getAllocationContexts(VALID_SESSION, 3, 4);
    assertThat(contexts.getAllocatedClassesCount()).isEqualTo(1);
    assertThat(contexts.getAllocatedClasses(0)).isEqualTo(charClass.toBuilder().setClassName("char").build());

    contexts = getTable().getAllocationContexts(VALID_SESSION, 4, 5);
    assertThat(contexts.getAllocatedClassesCount()).isEqualTo(1);
    assertThat(contexts.getAllocatedClasses(0)).isEqualTo(shortClass.toBuilder().setClassName("short").build());

    contexts = getTable().getAllocationContexts(VALID_SESSION, 5, 6);
    assertThat(contexts.getAllocatedClassesCount()).isEqualTo(1);
    assertThat(contexts.getAllocatedClasses(0)).isEqualTo(intClass.toBuilder().setClassName("int").build());

    contexts = getTable().getAllocationContexts(VALID_SESSION, 6, 7);
    assertThat(contexts.getAllocatedClassesCount()).isEqualTo(1);
    assertThat(contexts.getAllocatedClasses(0)).isEqualTo(longClass.toBuilder().setClassName("long").build());

    contexts = getTable().getAllocationContexts(VALID_SESSION, 7, 8);
    assertThat(contexts.getAllocatedClassesCount()).isEqualTo(1);
    assertThat(contexts.getAllocatedClasses(0)).isEqualTo(floatClass.toBuilder().setClassName("float").build());

    contexts = getTable().getAllocationContexts(VALID_SESSION, 8, 9);
    assertThat(contexts.getAllocatedClassesCount()).isEqualTo(1);
    assertThat(contexts.getAllocatedClasses(0)).isEqualTo(doubleClass.toBuilder().setClassName("double").build());
  }

  @Test
  public void testInsertAndGetAllocationSamplingRateEvents() {
    AllocationSamplingRateEvent oldSamplingRate = AllocationSamplingRateEvent.newBuilder().setTimestamp(2).build();
    AllocationSamplingRateEvent newSamplingRate = AllocationSamplingRateEvent.newBuilder().setTimestamp(3).build();

    getTable().insertOrReplaceAllocationSamplingRateEvent(VALID_SESSION, oldSamplingRate);
    getTable().insertOrReplaceAllocationSamplingRateEvent(VALID_SESSION, newSamplingRate);

    List<AllocationSamplingRateEvent> result = getTable().getAllocationSamplingRateEvents(VALID_SESSION.getSessionId(), 0, 1);
    assertThat(result.isEmpty()).isTrue();

    result = getTable().getAllocationSamplingRateEvents(VALID_SESSION.getSessionId(), 1, 2);
    assertThat(result).containsExactly(oldSamplingRate);

    result = getTable().getAllocationSamplingRateEvents(VALID_SESSION.getSessionId(), 1, 3);
    assertThat(result).containsExactly(oldSamplingRate, newSamplingRate).inOrder();

    result = getTable().getAllocationSamplingRateEvents(INVALID_SESSION.getSessionId(), 0, Long.MAX_VALUE);
    assertThat(result.isEmpty()).isTrue();
  }
}
