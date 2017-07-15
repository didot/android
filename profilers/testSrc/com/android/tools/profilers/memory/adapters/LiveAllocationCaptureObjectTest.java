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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.android.tools.profilers.memory.MemoryProfilerAspect;
import com.android.tools.profilers.memory.MemoryProfilerConfiguration;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.google.common.util.concurrent.MoreExecutors;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.*;

import static com.android.tools.profilers.memory.adapters.LiveAllocationCaptureObject.DEFAULT_HEAP_NAME;
import static com.google.common.truth.Truth.assertThat;

public class LiveAllocationCaptureObjectTest {
  /**
   * String containing data used for validating against each ClassifierSet
   * Format: Name, Alloc Count, Dealloc Count, Instance Count, Children Size, Has Stack.
   */
  private static final String NODE_FORMAT = "%s,%d,%d,%d,%d,%b";

  @NotNull private final FakeMemoryService myService = new FakeMemoryService();
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("LiveAllocationCaptureObjectTest", myService);

  private final int APP_ID = 1;
  private final int CAPTURE_START_TIME = 0;
  private final ExecutorService LOAD_SERVICE = MoreExecutors.newDirectExecutorService();
  private final Executor LOAD_JOINER = MoreExecutors.directExecutor();

  private MemoryProfilerStage myStage;

  private final AspectObserver myAspectObserver = new AspectObserver();

  @Before
  public void before() {
    FakeIdeProfilerServices profilerServices = new FakeIdeProfilerServices();
    myStage = new MemoryProfilerStage(new StudioProfilers(myGrpcChannel.getClient(), profilerServices));
  }

  // Simple test to check that we get the correct data on load.
  @Test
  public void testBasicLiveAllocationLoad() throws Exception {
    // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
    boolean[] loadSuccess = new boolean[1];
    LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myGrpcChannel.getClient().getMemoryClient(),
                                                                          ProfilersTestData.SESSION_DATA,
                                                                          APP_ID,
                                                                          CAPTURE_START_TIME,
                                                                          LOAD_SERVICE,
                                                                          myStage);

    // Heap set should start out empty.
    HeapSet heapSet = capture.getHeapSet(LiveAllocationCaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
    heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE);

    // Listens to the aspect change when load is called, then check the content of the changedNode parameter

    myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, () -> loadSuccess[0] = true);

    Queue<String> expected_0_to_4 = new LinkedList<>();
    expected_0_to_4.add(String.format(NODE_FORMAT, DEFAULT_HEAP_NAME, 4, 2, 4, 2, true));
    expected_0_to_4.add(" " + String.format(NODE_FORMAT, "This", 2, 1, 2, 2, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Is", 1, 1, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Foo", 1, 1, 1, 0, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Also", 1, 0, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Foo", 1, 0, 1, 0, true));
    expected_0_to_4.add(" " + String.format(NODE_FORMAT, "That", 2, 1, 2, 2, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Is", 1, 1, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Bar", 1, 1, 1, 0, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Also", 1, 0, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Bar", 1, 0, 1, 0, true));

    Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + 4);
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
    LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myGrpcChannel.getClient().getMemoryClient(),
                                                                          ProfilersTestData.SESSION_DATA,
                                                                          APP_ID,
                                                                          CAPTURE_START_TIME,
                                                                          null,
                                                                          myStage);

    // Heap set should start out empty.
    HeapSet heapSet = capture.getHeapSet(LiveAllocationCaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
    heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE);

    Queue<String> expected_0_to_4 = new LinkedList<>();
    expected_0_to_4.add(String.format(NODE_FORMAT, DEFAULT_HEAP_NAME, 4, 2, 4, 2, true));
    expected_0_to_4.add(" " + String.format(NODE_FORMAT, "This", 2, 1, 2, 2, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Is", 1, 1, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Foo", 1, 1, 1, 0, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Also", 1, 0, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Foo", 1, 0, 1, 0, true));
    expected_0_to_4.add(" " + String.format(NODE_FORMAT, "That", 2, 1, 2, 2, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Is", 1, 1, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Bar", 1, 1, 1, 0, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Also", 1, 0, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Bar", 1, 0, 1, 0, true));

    Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + 4);
    capture.load(loadRange, LOAD_JOINER);
    waitForLoadComplete(capture);
    verifyClassifierResult(heapSet, expected_0_to_4, 0);

    Queue<String> expected_0_to_8 = new LinkedList<>();
    expected_0_to_8.add(String.format(NODE_FORMAT, DEFAULT_HEAP_NAME, 8, 6, 8, 2, true));
    expected_0_to_8.add(" " + String.format(NODE_FORMAT, "This", 4, 3, 4, 2, true));
    expected_0_to_8.add("  " + String.format(NODE_FORMAT, "Is", 2, 2, 2, 1, true));
    expected_0_to_8.add("   " + String.format(NODE_FORMAT, "Foo", 2, 2, 2, 0, true));
    expected_0_to_8.add("  " + String.format(NODE_FORMAT, "Also", 2, 1, 2, 1, true));
    expected_0_to_8.add("   " + String.format(NODE_FORMAT, "Foo", 2, 1, 2, 0, true));
    expected_0_to_8.add(" " + String.format(NODE_FORMAT, "That", 4, 3, 4, 2, true));
    expected_0_to_8.add("  " + String.format(NODE_FORMAT, "Is", 2, 2, 2, 1, true));
    expected_0_to_8.add("   " + String.format(NODE_FORMAT, "Bar", 2, 2, 2, 0, true));
    expected_0_to_8.add("  " + String.format(NODE_FORMAT, "Also", 2, 1, 2, 1, true));
    expected_0_to_8.add("   " + String.format(NODE_FORMAT, "Bar", 2, 1, 2, 0, true));

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
    // We should only get the very last selection change event. e.g. {CAPTURE_START_TIME, CAPTURE_START_TIME + 8}
    for (int k = 0; k < 4; ++k) {
      loadRange.set(CAPTURE_START_TIME, loadRange.getMax() + 1);
    }
    // unblocks our fake task, now only the last selection set should trigger the load.
    latch.countDown();
    loadSuccess[0] = false;
    waitForLoadComplete(capture);
    assertThat(loadSuccess[0]).isTrue();
    verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_8), 0);
  }

  @Test
  public void testSelectionMinChanges() throws Exception {
    // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
    boolean[] loadSuccess = new boolean[1];
    LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myGrpcChannel.getClient().getMemoryClient(),
                                                                          ProfilersTestData.SESSION_DATA,
                                                                          APP_ID,
                                                                          CAPTURE_START_TIME,
                                                                          LOAD_SERVICE,
                                                                          myStage);

    // Heap set should start out empty.
    HeapSet heapSet = capture.getHeapSet(LiveAllocationCaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
    heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE);

    myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, () -> loadSuccess[0] = true);

    Queue<String> expected_0_to_4 = new LinkedList<>();
    expected_0_to_4.add(String.format(NODE_FORMAT, DEFAULT_HEAP_NAME, 4, 2, 4, 2, true));
    expected_0_to_4.add(" " + String.format(NODE_FORMAT, "This", 2, 1, 2, 2, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Is", 1, 1, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Foo", 1, 1, 1, 0, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Also", 1, 0, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Foo", 1, 0, 1, 0, true));
    expected_0_to_4.add(" " + String.format(NODE_FORMAT, "That", 2, 1, 2, 2, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Is", 1, 1, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Bar", 1, 1, 1, 0, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Also", 1, 0, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Bar", 1, 0, 1, 0, true));
    Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + 4);
    loadSuccess[0] = false;
    capture.load(loadRange, LOAD_JOINER);
    assertThat(loadSuccess[0]).isTrue();
    verifyClassifierResult(heapSet, expected_0_to_4, 0);

    Queue<String> expected_2_to_4 = new LinkedList<>();
    expected_2_to_4.add(String.format(NODE_FORMAT, DEFAULT_HEAP_NAME, 2, 2, 4, 2, true));
    expected_2_to_4.add(" " + String.format(NODE_FORMAT, "This", 1, 1, 2, 2, true));
    expected_2_to_4.add("  " + String.format(NODE_FORMAT, "Is", 0, 1, 1, 1, true));
    expected_2_to_4.add("   " + String.format(NODE_FORMAT, "Foo", 0, 1, 1, 0, true));
    expected_2_to_4.add("  " + String.format(NODE_FORMAT, "Also", 1, 0, 1, 1, true));
    expected_2_to_4.add("   " + String.format(NODE_FORMAT, "Foo", 1, 0, 1, 0, true));
    expected_2_to_4.add(" " + String.format(NODE_FORMAT, "That", 1, 1, 2, 2, true));
    expected_2_to_4.add("  " + String.format(NODE_FORMAT, "Is", 0, 1, 1, 1, true));
    expected_2_to_4.add("   " + String.format(NODE_FORMAT, "Bar", 0, 1, 1, 0, true));
    expected_2_to_4.add("  " + String.format(NODE_FORMAT, "Also", 1, 0, 1, 1, true));
    expected_2_to_4.add("   " + String.format(NODE_FORMAT, "Bar", 1, 0, 1, 0, true));

    // Shrink selection to {2,4}
    loadSuccess[0] = false;
    loadRange.setMin(CAPTURE_START_TIME + 2);
    assertThat(loadSuccess[0]).isTrue();
    verifyClassifierResult(heapSet, new LinkedList<>(expected_2_to_4), 0);

    // Shrink selection to {4,4}
    Queue<String> expected_4_to_4 = new LinkedList<>();
    expected_4_to_4.add(String.format(NODE_FORMAT, DEFAULT_HEAP_NAME, 0, 0, 0, 0, false));
    loadSuccess[0] = false;
    loadRange.setMin(CAPTURE_START_TIME + 4);
    assertThat(loadSuccess[0]).isTrue();
    verifyClassifierResult(heapSet, expected_4_to_4, 0);

    expected_2_to_4 = new LinkedList<>();
    expected_2_to_4.add(String.format(NODE_FORMAT, DEFAULT_HEAP_NAME, 2, 2, 4, 2, true));
    expected_2_to_4.add(" " + String.format(NODE_FORMAT, "This", 1, 1, 2, 2, true));
    expected_2_to_4.add("  " + String.format(NODE_FORMAT, "Also", 1, 0, 1, 1, true));
    expected_2_to_4.add("   " + String.format(NODE_FORMAT, "Foo", 1, 0, 1, 0, true));
    expected_2_to_4.add("  " + String.format(NODE_FORMAT, "Is", 0, 1, 1, 1, true));
    expected_2_to_4.add("   " + String.format(NODE_FORMAT, "Foo", 0, 1, 1, 0, true));
    expected_2_to_4.add(" " + String.format(NODE_FORMAT, "That", 1, 1, 2, 2, true));
    expected_2_to_4.add("  " + String.format(NODE_FORMAT, "Also", 1, 0, 1, 1, true));
    expected_2_to_4.add("   " + String.format(NODE_FORMAT, "Bar", 1, 0, 1, 0, true));
    expected_2_to_4.add("  " + String.format(NODE_FORMAT, "Is", 0, 1, 1, 1, true));
    expected_2_to_4.add("   " + String.format(NODE_FORMAT, "Bar", 0, 1, 1, 0, true));

    // Restore selection back to {2,4}
    loadSuccess[0] = false;
    loadRange.setMin(CAPTURE_START_TIME + 2);
    assertThat(loadSuccess[0]).isTrue();
    verifyClassifierResult(heapSet, new LinkedList<>(expected_2_to_4), 0);
  }

  @Test
  public void testSelectionMaxChanges() throws Exception {
    // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
    boolean[] loadSuccess = new boolean[1];
    LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myGrpcChannel.getClient().getMemoryClient(),
                                                                          ProfilersTestData.SESSION_DATA,
                                                                          APP_ID,
                                                                          CAPTURE_START_TIME,
                                                                          LOAD_SERVICE,
                                                                          myStage);

    // Heap set should start out empty.
    HeapSet heapSet = capture.getHeapSet(LiveAllocationCaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
    heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE);

    myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, () -> loadSuccess[0] = true);

    Queue<String> expected_0_to_4 = new LinkedList<>();
    expected_0_to_4.add(String.format(NODE_FORMAT, DEFAULT_HEAP_NAME, 4, 2, 4, 2, true));
    expected_0_to_4.add(" " + String.format(NODE_FORMAT, "This", 2, 1, 2, 2, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Is", 1, 1, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Foo", 1, 1, 1, 0, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Also", 1, 0, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Foo", 1, 0, 1, 0, true));
    expected_0_to_4.add(" " + String.format(NODE_FORMAT, "That", 2, 1, 2, 2, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Is", 1, 1, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Bar", 1, 1, 1, 0, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Also", 1, 0, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Bar", 1, 0, 1, 0, true));
    Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + 4);
    loadSuccess[0] = false;
    capture.load(loadRange, LOAD_JOINER);
    assertThat(loadSuccess[0]).isTrue();
    verifyClassifierResult(heapSet, expected_0_to_4, 0);

    Queue<String> expected_0_to_2 = new LinkedList<>();
    expected_0_to_2.add(String.format(NODE_FORMAT, DEFAULT_HEAP_NAME, 2, 0, 2, 2, true));
    expected_0_to_2.add(" " + String.format(NODE_FORMAT, "This", 1, 0, 1, 1, true));
    expected_0_to_2.add("  " + String.format(NODE_FORMAT, "Is", 1, 0, 1, 1, true));
    expected_0_to_2.add("   " + String.format(NODE_FORMAT, "Foo", 1, 0, 1, 0, true));
    expected_0_to_2.add(" " + String.format(NODE_FORMAT, "That", 1, 0, 1, 1, true));
    expected_0_to_2.add("  " + String.format(NODE_FORMAT, "Is", 1, 0, 1, 1, true));
    expected_0_to_2.add("   " + String.format(NODE_FORMAT, "Bar", 1, 0, 1, 0, true));

    // Shrink selection to {0, 2}
    loadSuccess[0] = false;
    loadRange.setMax(CAPTURE_START_TIME + 2);
    assertThat(loadSuccess[0]).isTrue();
    verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_2), 0);

    // Shrink selection to {0,0}
    Queue<String> expected_0_to_0 = new LinkedList<>();
    expected_0_to_0.add(String.format(NODE_FORMAT, DEFAULT_HEAP_NAME, 0, 0, 0, 0, false));
    loadSuccess[0] = false;
    loadRange.setMax(CAPTURE_START_TIME);
    assertThat(loadSuccess[0]).isTrue();
    verifyClassifierResult(heapSet, expected_0_to_0, 0);

    // Restore selection back to {0, 2}
    loadSuccess[0] = false;
    loadRange.setMax(CAPTURE_START_TIME + 2);
    assertThat(loadSuccess[0]).isTrue();
    verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_2), 0);
  }

  @Test
  public void testSelectionShift() throws Exception {
    // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
    boolean[] loadSuccess = new boolean[1];
    LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myGrpcChannel.getClient().getMemoryClient(),
                                                                          ProfilersTestData.SESSION_DATA,
                                                                          APP_ID,
                                                                          CAPTURE_START_TIME,
                                                                          LOAD_SERVICE,
                                                                          myStage);

    // Heap set should start out empty.
    HeapSet heapSet = capture.getHeapSet(LiveAllocationCaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
    heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE);

    myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, () -> loadSuccess[0] = true);

    Queue<String> expected_0_to_4 = new LinkedList<>();
    expected_0_to_4.add(String.format(NODE_FORMAT, DEFAULT_HEAP_NAME, 4, 2, 4, 2, true));
    expected_0_to_4.add(" " + String.format(NODE_FORMAT, "This", 2, 1, 2, 2, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Is", 1, 1, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Foo", 1, 1, 1, 0, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Also", 1, 0, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Foo", 1, 0, 1, 0, true));
    expected_0_to_4.add(" " + String.format(NODE_FORMAT, "That", 2, 1, 2, 2, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Is", 1, 1, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Bar", 1, 1, 1, 0, true));
    expected_0_to_4.add("  " + String.format(NODE_FORMAT, "Also", 1, 0, 1, 1, true));
    expected_0_to_4.add("   " + String.format(NODE_FORMAT, "Bar", 1, 0, 1, 0, true));
    Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + 4);
    loadSuccess[0] = false;
    capture.load(loadRange, LOAD_JOINER);
    assertThat(loadSuccess[0]).isTrue();
    verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_4), 0);


    Queue<String> expected_4_to_8 = new LinkedList<>();
    expected_4_to_8.add(String.format(NODE_FORMAT, DEFAULT_HEAP_NAME, 4, 4, 6, 2, true));
    expected_4_to_8.add(" " + String.format(NODE_FORMAT, "This", 2, 2, 3, 2, true));
    expected_4_to_8.add("  " + String.format(NODE_FORMAT, "Is", 1, 1, 1, 1, true));
    expected_4_to_8.add("   " + String.format(NODE_FORMAT, "Foo", 1, 1, 1, 0, true));
    expected_4_to_8.add("  " + String.format(NODE_FORMAT, "Also", 1, 1, 2, 1, true));
    expected_4_to_8.add("   " + String.format(NODE_FORMAT, "Foo", 1, 1, 2, 0, true));
    expected_4_to_8.add(" " + String.format(NODE_FORMAT, "That", 2, 2, 3, 2, true));
    expected_4_to_8.add("  " + String.format(NODE_FORMAT, "Is", 1, 1, 1, 1, true));
    expected_4_to_8.add("   " + String.format(NODE_FORMAT, "Bar", 1, 1, 1, 0, true));
    expected_4_to_8.add("  " + String.format(NODE_FORMAT, "Also", 1, 1, 2, 1, true));
    expected_4_to_8.add("   " + String.format(NODE_FORMAT, "Bar", 1, 1, 2, 0, true));

    // Shift selection to {4,8}
    loadSuccess[0] = false;
    loadRange.set(CAPTURE_START_TIME + 4, CAPTURE_START_TIME + 8);
    assertThat(loadSuccess[0]).isTrue();
    verifyClassifierResult(heapSet, new LinkedList<>(expected_4_to_8), 0);

    // Shift selection back to {0,4}
    loadSuccess[0] = false;
    loadRange.set(CAPTURE_START_TIME, CAPTURE_START_TIME + 4);
    assertThat(loadSuccess[0]).isTrue();
    verifyClassifierResult(heapSet, new LinkedList<>(expected_0_to_4), 0);
  }

  // Class + method names in each StackFrame are lazy-loaded. Check that the method info are fetched correctly.
  @Test
  public void testLazyLoadedCallStack() throws Exception {
    LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myGrpcChannel.getClient().getMemoryClient(),
                                                                          ProfilersTestData.SESSION_DATA,
                                                                          APP_ID,
                                                                          CAPTURE_START_TIME,
                                                                          LOAD_SERVICE,
                                                                          myStage);

    // Heap set should start out empty.
    HeapSet heapSet = capture.getHeapSet(LiveAllocationCaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet.getChildrenClassifierSets().size()).isEqualTo(0);
    heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_CALLSTACK);

    Queue<String> expected_0_to_4 = new LinkedList<>();
    expected_0_to_4.add(String.format(NODE_FORMAT, DEFAULT_HEAP_NAME, 4, 2, 4, 4, true));
    expected_0_to_4.add(String.format(" " + NODE_FORMAT, "BarMethodA() (That.Is.Bar)", 1, 1, 1, 1, true));
    expected_0_to_4.add(String.format("  " + NODE_FORMAT, "FooMethodA() (This.Is.Foo)", 1, 1, 1, 1, true));
    expected_0_to_4.add(String.format("   " + NODE_FORMAT, "Foo", 1, 1, 1, 0, true));
    expected_0_to_4.add(String.format(" " + NODE_FORMAT, "FooMethodB() (This.Also.Foo)", 1, 1, 1, 1, true));
    expected_0_to_4.add(String.format("  " + NODE_FORMAT, "BarMethodA() (That.Is.Bar)", 1, 1, 1, 1, true));
    expected_0_to_4.add(String.format("   " + NODE_FORMAT, "Bar", 1, 1, 1, 0, true));
    expected_0_to_4.add(String.format(" " + NODE_FORMAT, "BarMethodB() (That.Also.Bar)", 1, 0, 1, 1, true));
    expected_0_to_4.add(String.format("  " + NODE_FORMAT, "FooMethodB() (This.Also.Foo)", 1, 0, 1, 1, true));
    expected_0_to_4.add(String.format("   " + NODE_FORMAT, "Foo", 1, 0, 1, 0, true));
    expected_0_to_4.add(String.format(" " + NODE_FORMAT, "FooMethodA() (This.Is.Foo)", 1, 0, 1, 1, true));
    expected_0_to_4.add(String.format("  " + NODE_FORMAT, "BarMethodB() (That.Also.Bar)", 1, 0, 1, 1, true));
    expected_0_to_4.add(String.format("   " + NODE_FORMAT, "Bar", 1, 0, 1, 0, true));

    Range loadRange = new Range(CAPTURE_START_TIME, CAPTURE_START_TIME + 4);
    capture.load(loadRange, LOAD_JOINER);
    verifyClassifierResult(heapSet, expected_0_to_4, 0);
  }

  /**
   * Helper method to walk through the ClassifierSet tree and validate each node against the data stored in the expected queue.
   */
  private static boolean verifyClassifierResult(@NotNull ClassifierSet node,
                                                @NotNull Queue<String> expected,
                                                int currentDepth) {
    boolean done = false;
    boolean currentNodeVisited = false;
    boolean childrenVisited = false;
    String line;

    while ((line = expected.peek()) != null && !done) {
      String trimmedLine = line.trim();
      int depth = line.indexOf(trimmedLine);

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
        String[] split = trimmedLine.split(",");

        assertThat(node.getName()).isEqualTo(split[0]);
        assertThat(node.getAllocatedCount()).isEqualTo(Integer.parseInt(split[1]));
        assertThat(node.getDeallocatedCount()).isEqualTo(Integer.parseInt(split[2]));
        assertThat(node.getInstancesCount()).isEqualTo(Integer.parseInt(split[3]));
        assertThat(node.getChildrenClassifierSets().size()).isEqualTo(Integer.parseInt(split[4]));
        assertThat(node.hasStackInfo()).isEqualTo(Boolean.parseBoolean(split[5]));
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
}