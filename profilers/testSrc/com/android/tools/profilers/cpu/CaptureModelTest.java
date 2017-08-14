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

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.android.tools.profilers.network.FakeNetworkService;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;


public class CaptureModelTest {
  private final FakeProfilerService myProfilerService = new FakeProfilerService();

  private final FakeCpuService myCpuService = new FakeCpuService();

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("CpuProfilerStageTestChannel", myCpuService, myProfilerService,
                        new FakeMemoryService(), new FakeEventService(), FakeNetworkService.newBuilder().build());

  private CpuProfilerStage myStage;

  private CaptureModel myModel;
  @Before
  public void setUp() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), timer);
    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myStage = new CpuProfilerStage(profilers);
    myStage.getStudioProfilers().setStage(myStage);
    myModel = new CaptureModel(myStage);
  }

  @Test
  public void testFilter() {
    CaptureNode root = createFilterTestTree();

    CpuThreadInfo info = new CpuThreadInfo(101, "main");
    CpuCapture capture = new CpuCapture(new Range(0, 30),
                                        new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>()
                                          .put(info, root)
                                          .build());
    myModel.setCapture(capture);
    myModel.setThread(101);
    myModel.setDetails(CaptureModel.Details.Type.CALL_CHART);
    myModel.setFilter("myPackage");
    CaptureNode node = (CaptureNode)((CaptureModel.CallChart)myModel.getDetails()).getNode();

    assertThat(node.getData().getId()).isEqualTo("mainPackage.main");
    checkChildren(node, "otherPackage.method1", "myPackage.method1");
    checkChildren(node.getFirstChild(), "myPackage.method2");

    checkChildren(node.getFirstChild().getFirstChild(), "otherPackage.method4", "myPackage.method3");
    assertThat(node.getFirstChild().getFirstChild().getFirstChild().getChildCount()).isEqualTo(0);
    assertThat(node.getFirstChild().getFirstChild().getChildAt(1).getChildCount()).isEqualTo(0);

    checkChildren(node.getChildAt(1), "otherPackage.method3", "otherPackage.method4");
  }

  @Test
  public void testGetPossibleFilters() {
    CaptureNode root = createFilterTestTree();
    CpuThreadInfo info = new CpuThreadInfo(101, "main");
    CpuCapture capture = new CpuCapture(new Range(0, 30),
                                        new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>()
                                          .put(info, root)
                                          .build());
    myModel.setCapture(capture);
    myModel.setThread(101);
    myModel.setDetails(CaptureModel.Details.Type.CALL_CHART);

    assertThat(myModel.getPossibleFilters()).containsExactly("mainPackage", "mainPackage.main",
                                                             "myPackage", "myPackage.method1",
                                                             "myPackage.method2", "myPackage.method3",
                                                             "otherPackage",
                                                             "otherPackage.method1", "otherPackage.method2",
                                                             "otherPackage.method3", "otherPackage.method4");
  }

  private static void checkChildren(CaptureNode node, String... childrenId) {
    assertThat(node.getChildCount()).isEqualTo(childrenId.length);
    for (int i = 0; i < node.getChildCount(); ++i) {
      assertThat(node.getChildAt(i).getData().getId()).isEqualTo(childrenId[i]);
    }
  }

  private CaptureNode createFilterTestTree() {
    CaptureNode root = createNode("mainPackage.main", 0, 1000);
    root.addChild(createNode("otherPackage.method1", 0, 500));
    root.addChild(createNode("myPackage.method1", 600, 700));
    root.addChild(createNode("otherPackage.method2", 800, 1000));

    root.getChildAt(1).addChild(createNode("otherPackage.method3", 600, 650));
    root.getChildAt(1).addChild(createNode("otherPackage.method4", 660, 700));

    root.getChildAt(2).addChild(createNode("otherPackage.method3", 800, 850));
    root.getChildAt(2).addChild(createNode("otherPackage.method4", 860, 900));

    CaptureNode first = root.getFirstChild();
    first.addChild(createNode("myPackage.method2", 0, 200));
    first.addChild(createNode("otherPackage.method3", 300, 500));

    first.getChildAt(0).addChild(createNode("otherPackage.method4", 0, 100));
    first.getChildAt(0).addChild(createNode("myPackage.method3", 101, 200));

    first.getChildAt(1).addChild(createNode("otherPackage.method4", 300, 400));
    first.getChildAt(1).addChild(createNode("otherPackage.method4", 401, 500));

    return root;
  }

  private CaptureNode createNode(String fullMethodName, long start, long end) {
    int index = fullMethodName.lastIndexOf(".");
    assert index != -1;
    String className = fullMethodName.substring(0, index);
    String methodName = fullMethodName.substring(index + 1);

    CaptureNode node = new CaptureNode();
    node.setMethodModel(new MethodModel(methodName, className, ""));
    node.setClockType(ClockType.GLOBAL);
    node.setStartGlobal(start);
    node.setEndGlobal(end);
    node.setStartThread(start);
    node.setEndThread(end);

    return node;
  }
}