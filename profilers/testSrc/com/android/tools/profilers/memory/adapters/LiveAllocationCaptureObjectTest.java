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
package com.android.tools.profilers.memory.adapters;

import static com.android.tools.profilers.memory.adapters.CaptureObject.DEFAULT_HEAP_ID;
import static com.android.tools.profilers.memory.adapters.LiveAllocationCaptureObject.DEFAULT_HEAP_NAME;
import static com.android.tools.profilers.memory.adapters.LiveAllocationCaptureObject.JNI_HEAP_ID;
import static com.android.tools.profilers.memory.adapters.LiveAllocationCaptureObject.JNI_HEAP_NAME;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.MemoryAllocSamplingData;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.android.tools.profilers.memory.MemoryProfilerAspect;
import com.android.tools.profilers.memory.MemoryProfilerConfiguration;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet;
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import com.android.tools.profilers.stacktrace.NativeFrameSymbolizer;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;


public class LiveAllocationCaptureObjectTest {

  // A fake symbolizer so that JNI reference instance objects have proper app+system callstacks.
  @NotNull private static final NativeFrameSymbolizer FAKE_SYMBOLIZER = new NativeFrameSymbolizer() {
    @NotNull
    @Override
    public Memory.NativeCallStack.NativeFrame symbolize(String abi, Memory.NativeCallStack.NativeFrame unsymbolizedFrame) {
      long address = unsymbolizedFrame.getAddress();

      // System frame.
      if (address == ProfilersTestData.SYSTEM_NATIVE_ADDRESSES_BASE) {
        return unsymbolizedFrame.toBuilder().setModuleName(ProfilersTestData.FAKE_SYSTEM_NATIVE_MODULE).build();
      }
      else {
        int index = (int)((address - ProfilersTestData.NATIVE_ADDRESSES_BASE) % ProfilersTestData.FAKE_NATIVE_FUNCTION_NAMES.size());
        return unsymbolizedFrame.toBuilder()
          .setModuleName(ProfilersTestData.FAKE_NATIVE_MODULE_NAMES.get(index))
          .setSymbolName(ProfilersTestData.FAKE_NATIVE_FUNCTION_NAMES.get(index))
          .setFileName(ProfilersTestData.FAKE_NATIVE_SOURCE_FILE.get(index))
          .build();
      }
    }
  };

  @NotNull private final FakeTimer myTimer = new FakeTimer();
  @NotNull protected final FakeTransportService myTransportService = new FakeTransportService(myTimer);
  @NotNull protected final FakeMemoryService myService = new FakeMemoryService();
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("LiveAllocationCaptureObjectTest",
                                                                   myTransportService,
                                                                   new FakeProfilerService(myTimer),
                                                                   myService);
  protected final int CAPTURE_START_TIME = 0;
  protected final ExecutorService LOAD_SERVICE = MoreExecutors.newDirectExecutorService();
  protected final Executor LOAD_JOINER = MoreExecutors.directExecutor();

  protected MemoryProfilerStage myStage;

  protected final AspectObserver myAspectObserver = new AspectObserver();

  @NotNull protected FakeIdeProfilerServices myIdeProfilerServices;

  public void before() {
    myIdeProfilerServices = new FakeIdeProfilerServices();
    myIdeProfilerServices.setNativeFrameSymbolizer(FAKE_SYMBOLIZER);
    myStage = new MemoryProfilerStage(new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myIdeProfilerServices, myTimer));

    long dataStartTime = CAPTURE_START_TIME;
    long dataEndTime = TimeUnit.SECONDS.toNanos(8);
    List<Memory.BatchAllocationContexts> contexts = ProfilersTestData.generateMemoryAllocContext(dataStartTime, dataEndTime);
    List<Memory.BatchAllocationEvents> allocEvents = ProfilersTestData.generateMemoryAllocEvents(dataStartTime, dataEndTime);
    List<Memory.BatchJNIGlobalRefEvent> jniEvents = ProfilersTestData.generateMemoryJniRefEvents(dataStartTime, dataEndTime);
    contexts.forEach(context -> myTransportService.addEventToStream(ProfilersTestData.SESSION_DATA.getStreamId(), Common.Event.newBuilder()
      .setPid(ProfilersTestData.SESSION_DATA.getPid())
      .setKind(Common.Event.Kind.MEMORY_ALLOC_CONTEXTS)
      .setTimestamp(context.getTimestamp())
      .setMemoryAllocContexts(Memory.MemoryAllocContextsData.newBuilder().setContexts(context))
      .build()));

    allocEvents
      .forEach(events -> myTransportService.addEventToStream(ProfilersTestData.SESSION_DATA.getStreamId(), Common.Event.newBuilder()
        .setPid(ProfilersTestData.SESSION_DATA.getPid())
        .setKind(Common.Event.Kind.MEMORY_ALLOC_EVENTS)
        .setTimestamp(events.getTimestamp())
        .setMemoryAllocEvents(Memory.MemoryAllocEventsData.newBuilder().setEvents(events))
        .build()));

    jniEvents.forEach(jniRefs -> myTransportService.addEventToStream(ProfilersTestData.SESSION_DATA.getStreamId(), Common.Event.newBuilder()
      .setPid(ProfilersTestData.SESSION_DATA.getPid())
      .setKind(Common.Event.Kind.MEMORY_JNI_REF_EVENTS)
      .setTimestamp(jniRefs.getTimestamp())
      .setMemoryJniRefEvents(Memory.MemoryJniRefData.newBuilder().setEvents(jniRefs))
      .build()));
  }

  @RunWith(value = Parameterized.class)
  public static class AllHeapsTests extends LiveAllocationCaptureObjectTest {

    @Parameter(0)
    public int myHeapId;

    @Parameter(1)
    public String myHeapName;

    @Parameter(2)
    public Boolean myJniRefTracking;

    @Parameter(3)
    public Boolean myNewPipeline;

    private ProfilerClient myProfilerClient;

    @Before
    @Override
    public void before() {
      super.before();
      myIdeProfilerServices.enableJniReferenceTracking(myJniRefTracking);
      myIdeProfilerServices.enableEventsPipeline(myNewPipeline);
      myProfilerClient = new ProfilerClient(myGrpcChannel.getChannel());
    }

    @Parameters(name = "{index}: HeapId:{0}, HeapName:{1}, JNI tracking: {2}, New Pipeline: {3}")
    public static Object[] getHeapParameters() {
      return new Object[]{
        new Object[]{DEFAULT_HEAP_ID, DEFAULT_HEAP_NAME, false, false},
        new Object[]{DEFAULT_HEAP_ID, DEFAULT_HEAP_NAME, true, false},
        new Object[]{DEFAULT_HEAP_ID, DEFAULT_HEAP_NAME, false, true},
        new Object[]{DEFAULT_HEAP_ID, DEFAULT_HEAP_NAME, true, true},
        new Object[]{JNI_HEAP_ID, JNI_HEAP_NAME, true, false},
        new Object[]{JNI_HEAP_ID, JNI_HEAP_NAME, true, true},
      };
    }


    // Simple test to check that we get the correct delta + total data.
    @Test
    public void testBasicDataLoad() throws Exception {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      boolean[] loadSuccess = new boolean[1];
      LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myProfilerClient,
                                                                            ProfilersTestData.SESSION_DATA,
                                                                            CAPTURE_START_TIME,
                                                                            LOAD_SERVICE,
                                                                            myStage);

      // Heap set should start out empty.
      HeapSet heapSet = capture.getHeapSet(myHeapId);
      assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
      heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE);

      // Listens to the aspect change when load is called, then check the content of the changedNode parameter
      myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, () -> loadSuccess[0] = true);

      Queue<ClassifierSetTestData> expected_0_to_4 = new LinkedList<>();
      expected_0_to_4.add(new ClassifierSetTestData(0, myHeapName, 4, 2, 2, 4, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "That", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true));

      Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4));
      loadSuccess[0] = false;
      capture.load(loadRange, LOAD_JOINER);
      assertThat(loadSuccess[0]).isTrue();
      verifyClassifierResult(heapSet, expected_0_to_4, 0);
    }

    // This test checks that optimization by canceling outstanding queries works properly.
    @Test
    public void testUnstartedSelectionEventsCancelled() throws Exception {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      boolean[] loadSuccess = new boolean[1];
      LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myProfilerClient,
                                                                            ProfilersTestData.SESSION_DATA,
                                                                            CAPTURE_START_TIME,
                                                                            null,
                                                                            myStage);

      // Heap set should start out empty.
      HeapSet heapSet = capture.getHeapSet(myHeapId);
      assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
      heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE);

      Queue<ClassifierSetTestData> expected_0_to_4 = new LinkedList<>();
      expected_0_to_4.add(new ClassifierSetTestData(0, myHeapName, 4, 2, 2, 4, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "That", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true));

      Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4));
      capture.load(loadRange, LOAD_JOINER);
      waitForLoadComplete(capture);
      verifyClassifierResult(heapSet, expected_0_to_4, 0);

      Queue<ClassifierSetTestData> expected_0_to_8 = new LinkedList<>();
      expected_0_to_8.add(new ClassifierSetTestData(0, myHeapName, 8, 6, 2, 8, 2, true));
      expected_0_to_8.add(new ClassifierSetTestData(1, "This", 4, 3, 1, 4, 2, true));
      expected_0_to_8.add(new ClassifierSetTestData(2, "Is", 2, 2, 0, 2, 1, true));
      expected_0_to_8.add(new ClassifierSetTestData(3, "Foo", 2, 2, 0, 2, 0, true));
      expected_0_to_8.add(new ClassifierSetTestData(2, "Also", 2, 1, 1, 2, 1, true));
      expected_0_to_8.add(new ClassifierSetTestData(3, "Foo", 2, 1, 1, 2, 0, true));
      expected_0_to_8.add(new ClassifierSetTestData(1, "That", 4, 3, 1, 4, 2, true));
      expected_0_to_8.add(new ClassifierSetTestData(2, "Is", 2, 2, 0, 2, 1, true));
      expected_0_to_8.add(new ClassifierSetTestData(3, "Bar", 2, 2, 0, 2, 0, true));
      expected_0_to_8.add(new ClassifierSetTestData(2, "Also", 2, 1, 1, 2, 1, true));
      expected_0_to_8.add(new ClassifierSetTestData(3, "Bar", 2, 1, 1, 2, 0, true));

      // Listens to the aspect change when load is called, then check the content of the changedNode parameter
      int[] myHeapChangedCount = new int[1];
      myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, () -> {
        // We should not receive more than one heapChanged event.
        assertThat(myHeapChangedCount[0]++).isEqualTo(0);
        loadSuccess[0] = true;
      });

      // Adds a task that starts and blocks. This forces the subsequent selection change events to wait.
      CountDownLatch latch = new CountDownLatch(1);
      capture.myExecutorService.submit((Callable<CaptureObject>)() -> {
        try {
          latch.await();
        }
        catch (Exception ignored) {
        }
        return capture;
      });

      // Fake 4 selection range changes that would be cancelled.
      // We should only get the very last selection change event. e.g. {CAPTURE_START_TIME, CAPTURE_START_TIME + 8 secs}
      for (int k = 0; k < 4; ++k) {
        loadRange.set(CAPTURE_START_TIME, loadRange.getMax() + TimeUnit.SECONDS.toMicros(1));
      }
      loadSuccess[0] = false;

      // unblocks our fake task, now only the last selection set should trigger the load.
      latch.countDown();
      waitForLoadComplete(capture);
      assertThat(loadSuccess[0]).isTrue();
      verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_8), 0);
    }

    @Test
    public void testSelectionWithFilter() throws Exception {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      boolean[] loadSuccess = new boolean[1];
      LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myProfilerClient,
                                                                            ProfilersTestData.SESSION_DATA,
                                                                            CAPTURE_START_TIME,
                                                                            LOAD_SERVICE,
                                                                            myStage);

      // Heap set should start out empty.
      HeapSet heapSet = capture.getHeapSet(myHeapId);
      assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
      heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE);
      myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, () -> loadSuccess[0] = true);


      Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4));
      loadSuccess[0] = false;
      capture.load(loadRange, LOAD_JOINER);
      // Filter with "Foo"
      Queue<ClassifierSetTestData> expected_0_to_4 = new LinkedList<>();
      expected_0_to_4.add(new ClassifierSetTestData(0, myHeapName, 2, 1, 1, 4, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true));
      heapSet.selectFilter(new Filter("Foo", true, false));
      assertThat(loadSuccess[0]).isTrue();
      assertThat(heapSet.getFilterMatchCount()).isEqualTo(2);
      verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_4), 0);

      //Filter with "Bar"
      heapSet.selectFilter(new Filter("bar"));
      expected_0_to_4 = new LinkedList<>();
      expected_0_to_4.add(new ClassifierSetTestData(0, myHeapName, 2, 1, 1, 4, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "That", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true));
      assertThat(heapSet.getFilterMatchCount()).isEqualTo(2);
      verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_4), 0);

      // filter with package name and regex
      heapSet.selectFilter(new Filter("T[a-z]is", false, true));
      expected_0_to_4 = new LinkedList<>();
      expected_0_to_4.add(new ClassifierSetTestData(0, myHeapName, 2, 1, 1, 4, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true));
      assertThat(heapSet.getFilterMatchCount()).isEqualTo(3);
      verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_4), 0);

      // Reset filter
      heapSet.selectFilter(Filter.EMPTY_FILTER);
      expected_0_to_4 = new LinkedList<>();
      expected_0_to_4.add(new ClassifierSetTestData(0, myHeapName, 4, 2, 2, 4, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "That", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true));
      verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_4), 0);
    }

    @Test
    public void testSelectionMinChanges() throws Exception {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      boolean[] loadSuccess = new boolean[1];
      LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myProfilerClient,
                                                                            ProfilersTestData.SESSION_DATA,
                                                                            CAPTURE_START_TIME,
                                                                            LOAD_SERVICE,
                                                                            myStage);

      // Heap set should start out empty.
      HeapSet heapSet = capture.getHeapSet(myHeapId);
      assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
      heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE);

      myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, () -> loadSuccess[0] = true);

      Queue<ClassifierSetTestData> expected_0_to_4 = new LinkedList<>();
      expected_0_to_4.add(new ClassifierSetTestData(0, myHeapName, 4, 2, 2, 4, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "That", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true));
      Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4));
      loadSuccess[0] = false;
      capture.load(loadRange, LOAD_JOINER);
      assertThat(loadSuccess[0]).isTrue();
      verifyClassifierResult(heapSet, expected_0_to_4, 0);

      Queue<ClassifierSetTestData> expected_2_to_4 = new LinkedList<>();
      expected_2_to_4.add(new ClassifierSetTestData(0, myHeapName, 2, 2, 2, 4, 2, true));
      expected_2_to_4.add(new ClassifierSetTestData(1, "This", 1, 1, 1, 2, 2, true));
      expected_2_to_4.add(new ClassifierSetTestData(2, "Is", 0, 1, 0, 1, 1, true));
      expected_2_to_4.add(new ClassifierSetTestData(3, "Foo", 0, 1, 0, 1, 0, true));
      expected_2_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_2_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true));
      expected_2_to_4.add(new ClassifierSetTestData(1, "That", 1, 1, 1, 2, 2, true));
      expected_2_to_4.add(new ClassifierSetTestData(2, "Is", 0, 1, 0, 1, 1, true));
      expected_2_to_4.add(new ClassifierSetTestData(3, "Bar", 0, 1, 0, 1, 0, true));
      expected_2_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_2_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true));

      // Shrink selection to {2,4}
      loadSuccess[0] = false;
      loadRange.setMin(CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(2));
      assertThat(loadSuccess[0]).isTrue();
      verifyClassifierResult(heapSet, new LinkedList<>(expected_2_to_4), 0);

      // Shrink selection to {4,4}
      Queue<ClassifierSetTestData> expected_4_to_4 = new LinkedList<>();
      expected_4_to_4.add(new ClassifierSetTestData(0, myHeapName, 0, 0, 2, 2, 2, true));
      expected_4_to_4.add(new ClassifierSetTestData(1, "This", 0, 0, 1, 1, 1, true));
      expected_4_to_4.add(new ClassifierSetTestData(2, "Also", 0, 0, 1, 1, 1, true));
      expected_4_to_4.add(new ClassifierSetTestData(3, "Foo", 0, 0, 1, 1, 0, true));
      expected_4_to_4.add(new ClassifierSetTestData(1, "That", 0, 0, 1, 1, 1, true));
      expected_4_to_4.add(new ClassifierSetTestData(2, "Also", 0, 0, 1, 1, 1, true));
      expected_4_to_4.add(new ClassifierSetTestData(3, "Bar", 0, 0, 1, 1, 0, true));
      loadSuccess[0] = false;
      loadRange.setMin(CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4));
      assertThat(loadSuccess[0]).isTrue();
      verifyClassifierResult(heapSet, expected_4_to_4, 0);

      // Restore selection back to {2,4}
      loadSuccess[0] = false;
      loadRange.setMin(CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(2));
      assertThat(loadSuccess[0]).isTrue();
      verifyClassifierResult(heapSet, new LinkedList<>(expected_2_to_4), 0);
    }

    @Test
    public void testSelectionMaxChanges() throws Exception {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      boolean[] loadSuccess = new boolean[1];
      LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myProfilerClient,
                                                                            ProfilersTestData.SESSION_DATA,
                                                                            CAPTURE_START_TIME,
                                                                            LOAD_SERVICE,
                                                                            myStage);

      // Heap set should start out empty.
      HeapSet heapSet = capture.getHeapSet(myHeapId);
      assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
      heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE);

      myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, () -> loadSuccess[0] = true);

      Queue<ClassifierSetTestData> expected_0_to_4 = new LinkedList<>();
      expected_0_to_4.add(new ClassifierSetTestData(0, myHeapName, 4, 2, 2, 4, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "That", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true));
      Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4));
      loadSuccess[0] = false;
      capture.load(loadRange, LOAD_JOINER);
      assertThat(loadSuccess[0]).isTrue();
      verifyClassifierResult(heapSet, expected_0_to_4, 0);

      Queue<ClassifierSetTestData> expected_0_to_2 = new LinkedList<>();
      expected_0_to_2.add(new ClassifierSetTestData(0, myHeapName, 2, 0, 2, 2, 2, true));
      expected_0_to_2.add(new ClassifierSetTestData(1, "This", 1, 0, 1, 1, 1, true));
      expected_0_to_2.add(new ClassifierSetTestData(2, "Is", 1, 0, 1, 1, 1, true));
      expected_0_to_2.add(new ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true));
      expected_0_to_2.add(new ClassifierSetTestData(1, "That", 1, 0, 1, 1, 1, true));
      expected_0_to_2.add(new ClassifierSetTestData(2, "Is", 1, 0, 1, 1, 1, true));
      expected_0_to_2.add(new ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true));

      // Shrink selection to {0, 2}
      loadSuccess[0] = false;
      loadRange.setMax(CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(2));
      assertThat(loadSuccess[0]).isTrue();
      verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_2), 0);

      // Shrink selection to {0,0}
      Queue<ClassifierSetTestData> expected_0_to_0 = new LinkedList<>();
      expected_0_to_0.add(new ClassifierSetTestData(0, myHeapName, 0, 0, 0, 0, 0, false));
      loadSuccess[0] = false;
      loadRange.setMax(CAPTURE_START_TIME);
      assertThat(loadSuccess[0]).isTrue();
      verifyClassifierResult(heapSet, expected_0_to_0, 0);

      // Restore selection back to {0, 2}
      loadSuccess[0] = false;
      loadRange.setMax(CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(2));
      assertThat(loadSuccess[0]).isTrue();
      verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_2), 0);
    }

    @Test
    public void testSelectionShift() throws Exception {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      boolean[] loadSuccess = new boolean[1];
      LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myProfilerClient,
                                                                            ProfilersTestData.SESSION_DATA,
                                                                            CAPTURE_START_TIME,
                                                                            LOAD_SERVICE,
                                                                            myStage);

      // Heap set should start out empty.
      HeapSet heapSet = capture.getHeapSet(myHeapId);
      assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
      heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE);

      myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, () -> loadSuccess[0] = true);

      Queue<ClassifierSetTestData> expected_0_to_4 = new LinkedList<>();
      expected_0_to_4.add(new ClassifierSetTestData(0, myHeapName, 4, 2, 2, 4, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "That", 2, 1, 1, 2, 2, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true));
      Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4));
      loadSuccess[0] = false;
      capture.load(loadRange, LOAD_JOINER);
      assertThat(loadSuccess[0]).isTrue();
      verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_4), 0);

      Queue<ClassifierSetTestData> expected_4_to_8 = new LinkedList<>();
      expected_4_to_8.add(new ClassifierSetTestData(0, myHeapName, 4, 4, 2, 6, 2, true));
      expected_4_to_8.add(new ClassifierSetTestData(1, "This", 2, 2, 1, 3, 2, true));
      expected_4_to_8.add(new ClassifierSetTestData(2, "Also", 1, 1, 1, 2, 1, true));
      expected_4_to_8.add(new ClassifierSetTestData(3, "Foo", 1, 1, 1, 2, 0, true));
      expected_4_to_8.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_4_to_8.add(new ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true));
      expected_4_to_8.add(new ClassifierSetTestData(1, "That", 2, 2, 1, 3, 2, true));
      expected_4_to_8.add(new ClassifierSetTestData(2, "Also", 1, 1, 1, 2, 1, true));
      expected_4_to_8.add(new ClassifierSetTestData(3, "Bar", 1, 1, 1, 2, 0, true));
      expected_4_to_8.add(new ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true));
      expected_4_to_8.add(new ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true));

      // Shift selection to {4,8}
      loadSuccess[0] = false;
      loadRange.set(CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4), CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(8));
      assertThat(loadSuccess[0]).isTrue();
      verifyClassifierResult(heapSet, new LinkedList<>(expected_4_to_8), 0);

      // Shift selection back to {0,4}
      loadSuccess[0] = false;
      loadRange.set(CAPTURE_START_TIME, CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4));
      assertThat(loadSuccess[0]).isTrue();
      verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_4), 0);
    }

    @Test
    public void testInfoMessageBasedOnSelection() {
      MemoryAllocSamplingData fullData = MemoryAllocSamplingData.newBuilder()
        .setSamplingNumInterval(MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue()).build();
      MemoryAllocSamplingData sampledData = MemoryAllocSamplingData.newBuilder()
        .setSamplingNumInterval(MemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED.getValue()).build();
      MemoryAllocSamplingData noneData = MemoryAllocSamplingData.newBuilder()
        .setSamplingNumInterval(MemoryProfilerStage.LiveAllocationSamplingMode.NONE.getValue()).build();
      if (myNewPipeline) {
        myTransportService.addEventToStream(
          ProfilersTestData.SESSION_DATA.getStreamId(), Common.Event.newBuilder()
            .setPid(ProfilersTestData.SESSION_DATA.getPid())
            .setKind(Common.Event.Kind.MEMORY_ALLOC_SAMPLING)
            .setTimestamp(TimeUnit.SECONDS.toNanos(CAPTURE_START_TIME))
            .setMemoryAllocSampling(fullData)
            .build());
        myTransportService.addEventToStream(
          ProfilersTestData.SESSION_DATA.getStreamId(), Common.Event.newBuilder()
            .setPid(ProfilersTestData.SESSION_DATA.getPid())
            .setKind(Common.Event.Kind.MEMORY_ALLOC_SAMPLING)
            .setTimestamp(TimeUnit.SECONDS.toNanos(CAPTURE_START_TIME + 1))
            .setMemoryAllocSampling(sampledData)
            .build());
        myTransportService.addEventToStream(
          ProfilersTestData.SESSION_DATA.getStreamId(), Common.Event.newBuilder()
            .setPid(ProfilersTestData.SESSION_DATA.getPid())
            .setKind(Common.Event.Kind.MEMORY_ALLOC_SAMPLING)
            .setTimestamp(TimeUnit.SECONDS.toNanos(CAPTURE_START_TIME + 2))
            .setMemoryAllocSampling(noneData)
            .build());
        myTransportService.addEventToStream(
          ProfilersTestData.SESSION_DATA.getStreamId(), Common.Event.newBuilder()
            .setPid(ProfilersTestData.SESSION_DATA.getPid())
            .setKind(Common.Event.Kind.MEMORY_ALLOC_SAMPLING)
            .setTimestamp(TimeUnit.SECONDS.toNanos(CAPTURE_START_TIME + 3))
            .setMemoryAllocSampling(fullData)
            .build());
      }
      else {
        MemoryProfiler.MemoryData memoryData = MemoryProfiler.MemoryData.newBuilder().setEndTimestamp(1)
          .addAllocSamplingRateEvents(MemoryProfiler.AllocationSamplingRateEvent.newBuilder()
                                        .setTimestamp(TimeUnit.SECONDS.toNanos(CAPTURE_START_TIME))
                                        .setSamplingRate(fullData))
          .addAllocSamplingRateEvents(MemoryProfiler.AllocationSamplingRateEvent.newBuilder()
                                        .setTimestamp(TimeUnit.SECONDS.toNanos(CAPTURE_START_TIME + 1))
                                        .setSamplingRate(sampledData))
          .addAllocSamplingRateEvents(MemoryProfiler.AllocationSamplingRateEvent.newBuilder()
                                        .setTimestamp(TimeUnit.SECONDS.toNanos(CAPTURE_START_TIME + 2))
                                        .setSamplingRate(noneData))
          .addAllocSamplingRateEvents(MemoryProfiler.AllocationSamplingRateEvent.newBuilder()
                                        .setTimestamp(TimeUnit.SECONDS.toNanos(CAPTURE_START_TIME + 3))
                                        .setSamplingRate(fullData))
          .build();
        myService.setMemoryData(memoryData);
      }

      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      boolean[] loadSuccess = new boolean[1];
      LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myProfilerClient,
                                                                            ProfilersTestData.SESSION_DATA,
                                                                            CAPTURE_START_TIME,
                                                                            LOAD_SERVICE,
                                                                            myStage);
      myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, () -> loadSuccess[0] = true);

      Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME);
      loadSuccess[0] = false;
      capture.load(loadRange, LOAD_JOINER);
      assertThat(loadSuccess[0]).isTrue();
      assertThat(capture.getInfoMessage()).isNull();

      loadSuccess[0] = false;
      loadRange.set(CAPTURE_START_TIME, CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(3));
      assertThat(loadSuccess[0]).isTrue();
      assertThat(capture.getInfoMessage()).isEqualTo(LiveAllocationCaptureObject.SAMPLING_INFO_MESSAGE);

      loadSuccess[0] = false;
      loadRange.set(CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(1), CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(2));
      assertThat(loadSuccess[0]).isTrue();
      assertThat(capture.getInfoMessage()).isEqualTo(LiveAllocationCaptureObject.SAMPLING_INFO_MESSAGE);

      loadSuccess[0] = false;
      loadRange.set(CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(2), CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(3));
      assertThat(loadSuccess[0]).isTrue();
      assertThat(capture.getInfoMessage()).isEqualTo(LiveAllocationCaptureObject.SAMPLING_INFO_MESSAGE);

      loadSuccess[0] = false;
      loadRange.set(CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(3), CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4));
      assertThat(loadSuccess[0]).isTrue();
      assertThat(capture.getInfoMessage()).isNull();
    }
  }


  public static class DefaultHeapTest extends LiveAllocationCaptureObjectTest {

    private ProfilerClient myProfilerClient;

    @Before
    @Override
    public void before() {
      super.before();
      myProfilerClient = new ProfilerClient(myGrpcChannel.getChannel());
    }


    // Class + method names in each StackFrame are lazy-loaded. Check that the method info are fetched correctly.
    @Test
    public void testLazyLoadedCallStack() throws Exception {
      LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myProfilerClient,
                                                                            ProfilersTestData.SESSION_DATA,
                                                                            CAPTURE_START_TIME,
                                                                            LOAD_SERVICE,
                                                                            myStage);

      // Heap set should start out empty.
      HeapSet heapSet = capture.getHeapSet(DEFAULT_HEAP_ID);
      assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
      heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_CALLSTACK);

      Queue<ClassifierSetTestData> expected_0_to_4 = new LinkedList<>();
      expected_0_to_4.add(new ClassifierSetTestData(0, DEFAULT_HEAP_NAME, 4, 2, 2, 4, 4, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "BarMethodA() (That.Is.Bar)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "FooMethodA() (This.Is.Foo)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "FooMethodB() (This.Also.Foo)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "BarMethodA() (That.Is.Bar)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "BarMethodB() (That.Also.Bar)", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "FooMethodB() (This.Also.Foo)", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "FooMethodA() (This.Is.Foo)", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "BarMethodB() (That.Also.Bar)", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true));

      Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4));
      capture.load(loadRange, LOAD_JOINER);
      verifyClassifierResult(heapSet, expected_0_to_4, 0);
    }


    @Test
    public void testSelectionWithJaveMethodFilter() throws Exception {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      boolean[] loadSuccess = new boolean[1];
      LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myProfilerClient,
                                                                            ProfilersTestData.SESSION_DATA,
                                                                            CAPTURE_START_TIME,
                                                                            LOAD_SERVICE,
                                                                            myStage);

      // Heap set should start out empty.
      HeapSet heapSet = capture.getHeapSet(DEFAULT_HEAP_ID);
      assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
      heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE);
      myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, () -> loadSuccess[0] = true);

      Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4));
      loadSuccess[0] = false;
      capture.load(loadRange, LOAD_JOINER);

      // Filter with Java method name
      heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_CALLSTACK);
      heapSet.selectFilter(new Filter("MethodA"));
      Queue<ClassifierSetTestData> expected_0_to_4 = new LinkedList<>();
      expected_0_to_4.add(new ClassifierSetTestData(0, DEFAULT_HEAP_NAME, 3, 2, 1, 4, 3, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "BarMethodA() (That.Is.Bar)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "FooMethodA() (This.Is.Foo)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "FooMethodB() (This.Also.Foo)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "BarMethodA() (That.Is.Bar)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "FooMethodA() (This.Is.Foo)", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "BarMethodB() (That.Also.Bar)", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true));
      verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_4), 0);
    }
  }

  public static class JniHeapTest extends LiveAllocationCaptureObjectTest {

    private ProfilerClient myProfilerClient;

    @Before
    @Override
    public void before() {
      super.before();
      myIdeProfilerServices.enableJniReferenceTracking(true);
      myProfilerClient = new ProfilerClient(myGrpcChannel.getChannel());
    }

    @Test
    public void testLazyLoadedCallStack() throws Exception {
      LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myProfilerClient,
                                                                            ProfilersTestData.SESSION_DATA,
                                                                            CAPTURE_START_TIME,
                                                                            LOAD_SERVICE,
                                                                            myStage);

      // Heap set should start out empty.
      HeapSet heapSet = capture.getHeapSet(JNI_HEAP_ID);
      assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
      heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_CALLSTACK);

      Queue<ClassifierSetTestData> expected_0_to_4 = new LinkedList<>();
      expected_0_to_4.add(new ClassifierSetTestData(0, JNI_HEAP_NAME, 4, 2, 2, 4, 4, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "BarMethodA() (NativeNamespace::Bar)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "FooMethodA() (NativeNamespace::Foo)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "FooMethodB() (NativeNamespace::Foo)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "BarMethodA() (NativeNamespace::Bar)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "BarMethodB() (NativeNamespace::Bar)", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "FooMethodB() (NativeNamespace::Foo)", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "FooMethodA() (NativeNamespace::Foo)", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "BarMethodB() (NativeNamespace::Bar)", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true));

      Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4));
      capture.load(loadRange, LOAD_JOINER);
      verifyClassifierResult(heapSet, expected_0_to_4, 0);
    }


    @Test
    public void testSelectionWithJaveMethodFilter() throws Exception {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      boolean[] loadSuccess = new boolean[1];
      LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myProfilerClient,
                                                                            ProfilersTestData.SESSION_DATA,
                                                                            CAPTURE_START_TIME,
                                                                            LOAD_SERVICE,
                                                                            myStage);

      // Heap set should start out empty.
      HeapSet heapSet = capture.getHeapSet(JNI_HEAP_ID);
      assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
      heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE);
      myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, () -> loadSuccess[0] = true);

      Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4));
      loadSuccess[0] = false;
      capture.load(loadRange, LOAD_JOINER);

      // Filter with Java method name
      heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_CALLSTACK);
      heapSet.selectFilter(new Filter("MethodA"));
      Queue<ClassifierSetTestData> expected_0_to_4 = new LinkedList<>();
      expected_0_to_4.add(new ClassifierSetTestData(0, JNI_HEAP_NAME, 3, 2, 1, 4, 3, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "BarMethodA() (NativeNamespace::Bar)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "FooMethodA() (NativeNamespace::Foo)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "FooMethodB() (NativeNamespace::Foo)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "BarMethodA() (NativeNamespace::Bar)", 1, 1, 0, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true));
      expected_0_to_4.add(new ClassifierSetTestData(1, "FooMethodA() (NativeNamespace::Foo)", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(2, "BarMethodB() (NativeNamespace::Bar)", 1, 0, 1, 1, 1, true));
      expected_0_to_4.add(new ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true));
      verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_4), 0);
    }
  }


  private static boolean verifyClassifierResult(@NotNull ClassifierSet node,
                                                @NotNull Queue<ClassifierSetTestData> expected,
                                                int currentDepth) {
    boolean done = false;
    boolean currentNodeVisited = false;
    boolean childrenVisited = false;
    ClassifierSetTestData testData;

    while ((testData = expected.peek()) != null && !done) {
      int depth = testData.depth;

      if (depth < currentDepth) {
        // We are done with the current sub-tree.
        done = true;
      }
      else if (depth > currentDepth) {
        // We need to go deeper...
        assertThat(node.getChildrenClassifierSets().size()).isGreaterThan(0);
        assertThat(childrenVisited).isFalse();
        for (ClassifierSet child : node.getChildrenClassifierSets()) {
          boolean childResult =
            verifyClassifierResult(child, expected, currentDepth + 1);
          assertThat(childResult).isTrue();
        }
        childrenVisited = true;
      }
      else {
        if (currentNodeVisited) {
          done = true;
          continue;
        }

        // We are at current node, consumes the current line.
        expected.poll();
        assertThat(node.getName()).isEqualTo(testData.name);
        assertThat(node.getDeltaAllocationCount()).isEqualTo(testData.allocations);
        assertThat(node.getDeltaDeallocationCount()).isEqualTo(testData.deallocations);
        assertThat(node.getTotalObjectCount()).isEqualTo(testData.total);
        assertThat(node.getInstancesCount()).isEqualTo(testData.instanceCount);
        assertThat(node.getChildrenClassifierSets().size()).isEqualTo(testData.childrenSize);
        assertThat(node.hasStackInfo()).isEqualTo(testData.hasStack);
        currentNodeVisited = true;
      }
    }

    assertThat(currentNodeVisited).isTrue();
    return currentNodeVisited;
  }

  // Wait for the executor service to complete the task created in load(...)
  // NOTE - this works because myExecutorService is a single-threaded executor.
  private static void waitForLoadComplete(@NotNull LiveAllocationCaptureObject capture) throws InterruptedException, ExecutionException {
    CountDownLatch latch = new CountDownLatch(1);
    capture.myExecutorService.invokeAny(Collections.singleton((Callable<CaptureObject>)() -> {
      try {
        latch.countDown();
      }
      catch (Exception ignored) {
      }
      return capture;
    }));
    latch.await();
  }

  // Auxiliary class to verify ClassifierSet's internal data.
  private static class ClassifierSetTestData {
    int depth;
    String name;
    int allocations;
    int deallocations;
    int total;
    int instanceCount;
    int childrenSize;
    boolean hasStack;

    ClassifierSetTestData(int depth,
                          String name,
                          int allocations,
                          int deallocations,
                          int total,
                          int instanceCount,
                          int childrenSize,
                          boolean hasStack) {
      this.depth = depth;
      this.name = name;
      this.allocations = allocations;
      this.deallocations = deallocations;
      this.total = total;
      this.instanceCount = instanceCount;
      this.childrenSize = childrenSize;
      this.hasStack = hasStack;
    }
  }
}