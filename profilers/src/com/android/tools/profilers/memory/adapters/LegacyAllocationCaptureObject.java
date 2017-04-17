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
package com.android.tools.profilers.memory.adapters;

import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationEvent;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profilers.RelativeTimeConverter;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.google.protobuf3jarjar.ByteString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class LegacyAllocationCaptureObject implements CaptureObject {
  static final int DEFAULT_HEAP_ID = 0;
  static final String DEFAULT_HEAP_NAME = "default";
  static final long DEFAULT_CLASSLOADER_ID = -1;

  @NotNull private final MemoryServiceBlockingStub myClient;
  @NotNull private final ClassDb myClassDb;
  @NotNull private final String myLabel;
  private final int myProcessId;
  private final Common.Session mySession;
  private long myStartTimeNs;
  private long myEndTimeNs;
  private final FeatureTracker myFeatureTracker;
  private volatile boolean myIsDoneLoading = false;
  private volatile boolean myIsLoadingError = false;
  // Allocation records do not have heap information, but we create a fake HeapSet container anyway so that we have a consistent MemoryObject model.
  private final HeapSet myFakeHeapSet;

  public LegacyAllocationCaptureObject(@NotNull MemoryServiceBlockingStub client,
                                       @Nullable Common.Session session,
                                       int processId,
                                       @NotNull MemoryProfiler.AllocationsInfo info,
                                       @NotNull RelativeTimeConverter converter,
                                       @NotNull FeatureTracker featureTracker) {
    myClient = client;
    myClassDb = new ClassDb();
    myProcessId = processId;
    mySession = session;
    myStartTimeNs = info.getStartTime();
    myEndTimeNs = info.getEndTime();
    myFakeHeapSet = new HeapSet(this, DEFAULT_HEAP_ID);
    myLabel = "Allocations" +
              (myStartTimeNs != Long.MAX_VALUE ?
               " from " + TimeAxisFormatter.DEFAULT.getFixedPointFormattedString(
                 TimeUnit.MILLISECONDS.toMicros(1), TimeUnit.NANOSECONDS.toMicros(converter.convertToRelativeTime(myStartTimeNs))) :
               "") +
              (myEndTimeNs != Long.MIN_VALUE ?
               " to " + TimeAxisFormatter.DEFAULT.getFixedPointFormattedString(
                 TimeUnit.MILLISECONDS.toMicros(1), TimeUnit.NANOSECONDS.toMicros(converter.convertToRelativeTime(myEndTimeNs))) :
               "");
    myFeatureTracker = featureTracker;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof LegacyAllocationCaptureObject)) {
      return false;
    }

    LegacyAllocationCaptureObject other = (LegacyAllocationCaptureObject)obj;
    return other.myProcessId == myProcessId && other.myStartTimeNs == myStartTimeNs && other.myEndTimeNs == myEndTimeNs;
  }

  @NotNull
  @Override
  public String getName() {
    return myLabel;
  }

  @Nullable
  @Override
  public String getExportableExtension() {
    // TODO only return this if in legacy mode, otherwise return null
    return "alloc";
  }

  @Override
  public void saveToFile(@NotNull OutputStream outputStream) throws IOException {
    MemoryProfiler.DumpDataResponse response = myClient.getLegacyAllocationDump(
      MemoryProfiler.DumpDataRequest.newBuilder().setProcessId(myProcessId).setSession(mySession).setDumpTime(myStartTimeNs).build());
    if (response.getStatus() == MemoryProfiler.DumpDataResponse.Status.SUCCESS) {
      response.getData().writeTo(outputStream);
      myFeatureTracker.trackExportAllocation();
    }
    else {
      throw new IOException("Could not retrieve allocation dump.");
    }
  }

  @NotNull
  @Override
  public Stream<InstanceObject> getInstances() {
    //noinspection ConstantConditions
    assert isDoneLoading() && !isError();
    return myFakeHeapSet.getInstancesStream();
  }

  @Override
  public long getStartTimeNs() {
    return myStartTimeNs;
  }

  @Override
  public long getEndTimeNs() {
    return myEndTimeNs;
  }

  @Override
  public boolean load() {
    MemoryProfiler.LegacyAllocationEventsResponse response;
    while (true) {
      response = myClient.getLegacyAllocationEvents(MemoryProfiler.LegacyAllocationEventsRequest.newBuilder()
                                                      .setProcessId(myProcessId)
                                                      .setSession(mySession)
                                                      .setStartTime(myStartTimeNs)
                                                      .setEndTime(myEndTimeNs).build());
      if (response.getStatus() == MemoryProfiler.LegacyAllocationEventsResponse.Status.SUCCESS) {
        break;
      }
      else if (response.getStatus() == MemoryProfiler.LegacyAllocationEventsResponse.Status.NOT_READY) {
        try {
          Thread.sleep(50L);
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          myIsLoadingError = true;
          return false;
        }
        continue;
      }
      myIsLoadingError = true;
      return false;
    }

    MemoryProfiler.LegacyAllocationContextsRequest contextRequest = MemoryProfiler.LegacyAllocationContextsRequest.newBuilder()
      .setSession(mySession)
      .addAllStackIds(response.getEventsList().stream().map(LegacyAllocationEvent::getAllocationStackId).collect(Collectors.toSet()))
      .addAllClassIds(response.getEventsList().stream().map(LegacyAllocationEvent::getAllocatedClassId).collect(Collectors.toSet()))
      .build();
    MemoryProfiler.LegacyAllocationContextsResponse contextsResponse = myClient.listLegacyAllocationContexts(contextRequest);

    Map<Integer, ClassDb.ClassEntry> classEntryMap = new HashMap<>();
    Map<ByteString, MemoryProfiler.AllocationStack> callStacks = new HashMap<>();
    contextsResponse.getAllocatedClassesList().forEach(
      className -> classEntryMap.put(className.getClassId(), myClassDb.registerClass(DEFAULT_CLASSLOADER_ID, className.getClassName())));
    contextsResponse.getAllocationStacksList().forEach(callStack -> callStacks.putIfAbsent(callStack.getStackId(), callStack));

    // TODO make sure class IDs fall into a global pool
    for (LegacyAllocationEvent event : response.getEventsList()) {
      assert classEntryMap.containsKey(event.getAllocatedClassId());
      assert callStacks.containsKey(event.getAllocationStackId());
      myFakeHeapSet.addInstanceObject(
        new LegacyAllocationsInstanceObject(event, classEntryMap.get(event.getAllocatedClassId()),
                                            callStacks.get(event.getAllocationStackId())));
    }
    myIsDoneLoading = true;

    return true;
  }

  @Override
  public boolean isDoneLoading() {
    return myIsDoneLoading || myIsLoadingError;
  }

  @Override
  public boolean isError() {
    return myIsLoadingError;
  }

  @Override
  @NotNull
  public List<ClassifierAttribute> getClassifierAttributes() {
    return Arrays.asList(ClassifierAttribute.LABEL, ClassifierAttribute.COUNT);
  }

  @NotNull
  @Override
  public List<CaptureObject.InstanceAttribute> getInstanceAttributes() {
    return Arrays.asList(CaptureObject.InstanceAttribute.LABEL, CaptureObject.InstanceAttribute.SHALLOW_SIZE);
  }

  @NotNull
  @Override
  public Collection<HeapSet> getHeapSets() {
    return Collections.singletonList(myFakeHeapSet);
  }

  @NotNull
  @Override
  public String getHeapName(int heapId) {
    assert heapId == DEFAULT_HEAP_ID;
    return DEFAULT_HEAP_NAME;
  }

  @Override
  @Nullable
  public HeapSet getHeapSet(int heapId) {
    assert heapId == DEFAULT_HEAP_ID;
    return myFakeHeapSet;
  }
}
