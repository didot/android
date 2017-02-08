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
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profilers.RelativeTimeConverter;
import com.android.tools.profilers.memory.adapters.ClassObject.ClassAttribute;
import com.google.protobuf3jarjar.ByteString;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class AllocationsCaptureObject implements CaptureObject {
  @NotNull private final MemoryServiceBlockingStub myClient;
  private final int myProcessId;
  @NotNull private final String myLabel;
  private final Common.Session mySession;
  private long myStartTimeNs;
  private long myEndTimeNs;
  private volatile List<ClassObject> myClassObjs;
  private volatile boolean myIsLoadingError;
  // Allocation records do not have heap information, but we create a fake HeapObject container anyway so that we have a consistent MemoryObject model.
  private final AllocationsHeapObject myFakeHeapObject;

  public AllocationsCaptureObject(@NotNull MemoryServiceBlockingStub client,
                                  int processId,
                                  Common.Session session,
                                  @NotNull MemoryProfiler.AllocationsInfo info,
                                  @NotNull RelativeTimeConverter converter) {
    myClient = client;
    myProcessId = processId;
    mySession = session;
    myStartTimeNs = info.getStartTime();
    myEndTimeNs = info.getEndTime();
    myFakeHeapObject = new AllocationsHeapObject();
    myLabel = "Allocations" +
              (myStartTimeNs != Long.MAX_VALUE ?
               " from " + TimeAxisFormatter.DEFAULT.getFixedPointFormattedString(
                 TimeUnit.MILLISECONDS.toMicros(1), TimeUnit.NANOSECONDS.toMicros(converter.convertToRelativeTime(myStartTimeNs))) :
               "") +
              (myEndTimeNs != Long.MIN_VALUE ?
               " to " + TimeAxisFormatter.DEFAULT.getFixedPointFormattedString(
                 TimeUnit.MILLISECONDS.toMicros(1), TimeUnit.NANOSECONDS.toMicros(converter.convertToRelativeTime(myEndTimeNs))) :
               "");
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AllocationsCaptureObject)) {
      return false;
    }

    AllocationsCaptureObject other = (AllocationsCaptureObject)obj;
    return other.myProcessId == myProcessId && other.myStartTimeNs == myStartTimeNs && other.myEndTimeNs == myEndTimeNs;
  }

  @NotNull
  @Override
  public String getLabel() {
    return myLabel;
  }

  @NotNull
  @Override
  public List<HeapObject> getHeaps() {
    //noinspection ConstantConditions
    assert isDoneLoading() && !isError();
    return Collections.singletonList(myFakeHeapObject);
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
    MemoryProfiler.AllocationEventsResponse response;
    while (true) {
      response = myClient.getAllocationEvents(MemoryProfiler.AllocationEventsRequest.newBuilder()
                                                .setProcessId(myProcessId)
                                                .setSession(mySession)
                                                .setStartTime(myStartTimeNs)
                                                .setEndTime(myEndTimeNs).build());
      if (response.getStatus() == MemoryProfiler.AllocationEventsResponse.Status.SUCCESS) {
        break;
      }
      else if (response.getStatus() == MemoryProfiler.AllocationEventsResponse.Status.NOT_READY) {
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

    MemoryProfiler.AllocationContextsRequest contextRequest = MemoryProfiler.AllocationContextsRequest.newBuilder()
      .setSession(mySession)
      .addAllStackIds(response.getEventsList().stream().map(event -> event.getAllocationStackId()).collect(Collectors.toSet()))
      .addAllClassIds(response.getEventsList().stream().map(event -> event.getAllocatedClassId()).collect(Collectors.toSet()))
      .build();
    MemoryProfiler.AllocationContextsResponse contextsResponse = myClient.listAllocationContexts(contextRequest);

    Map<Integer, AllocationsClassObject> classNodes = new HashMap<>();
    Map<ByteString, MemoryProfiler.AllocationStack> callStacks = new HashMap<>();
    contextsResponse.getAllocatedClassesList().forEach(className -> classNodes.put(className.getClassId(),
                                                                                   new AllocationsClassObject(myFakeHeapObject,
                                                                                                              className)));
    contextsResponse.getAllocationStacksList().forEach(callStack -> callStacks.putIfAbsent(callStack.getStackId(), callStack));

    // TODO make sure class IDs fall into a global pool
    for (MemoryProfiler.AllocationEvent event : response.getEventsList()) {
      assert classNodes.containsKey(event.getAllocatedClassId());
      assert callStacks.containsKey(event.getAllocationStackId());
      classNodes.get(event.getAllocatedClassId()).addInstance(
        new AllocationsInstanceObject(event, classNodes.get(event.getAllocatedClassId()), callStacks.get(event.getAllocationStackId())));
    }

    myClassObjs = new ArrayList<>(classNodes.values());
    return true;
  }

  @Override
  public boolean isDoneLoading() {
    return myClassObjs != null || myIsLoadingError;
  }

  @Override
  public boolean isError() {
    return myIsLoadingError;
  }

  final class AllocationsHeapObject implements HeapObject {
    @Override
    public String toString() {
      return getHeapName();
    }

    @NotNull
    @Override
    public String getHeapName() {
      return "default";
    }

    @NotNull
    @Override
    public List<ClassObject> getClasses() {
      return myClassObjs;
    }

    @NotNull
    @Override
    public List<ClassObject.ClassAttribute> getClassAttributes() {
      return Arrays.asList(ClassAttribute.LABEL, ClassAttribute.HEAP_COUNT);
    }
  }
}
