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

import com.android.testutils.TestUtils;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.perflib.vmtrace.VmTraceData;
import com.android.tools.perflib.vmtrace.VmTraceParser;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CpuTraceArtTest {
  private static final String CPU_TRACES_DIR = "tools/adt/idea/profilers/testData/cputraces/";

  @Test
  public void testParse() throws IOException {
    VmTraceData data = getTraceData();
    CpuTraceArt traceArt = new CpuTraceArt();
    traceArt.parse(data);
    Map<ThreadInfo, CaptureNode> graphs = traceArt.getThreadsGraph();
    assertEquals(1, graphs.size());
    ThreadInfo info = graphs.keySet().iterator().next();
    assertEquals("AsyncTask #1", info.getName());
    assertEquals(11, info.getId());

    CaptureNode node = graphs.values().iterator().next();
    assertTrue(node.getClockType() == ClockType.GLOBAL);

    assertEquals("AsyncTask #1.", node.getData().getId());

    expectedChildrenIds(node, "android/os/Debug.startMethodTracing(Ljava/lang/String;)V",
                        "com/test/android/traceview/Basic.foo()V",
                        "android/os/Debug.stopMethodTracing()V");

    expectedChildrenIds(node.getChildren().get(0), "android/os/Debug.startMethodTracing(Ljava/lang/String;II)V");
    expectedChildrenIds(node.getChildren().get(1), "com/test/android/traceview/Basic.bar()I");
    expectedChildrenIds(node.getChildren().get(2), "dalvik/system/VMDebug.stopMethodTracing()V");
    expectedChildrenIds(node.getChildren().get(0).getChildren().get(0),
                        "dalvik/system/VMDebug.startMethodTracing(Ljava/lang/String;II)V");
  }

  @Test
  public void testThreadClock() throws IOException {
    VmTraceData data = getTraceData();
    CpuTraceArt traceArt = new CpuTraceArt();
    traceArt.parse(data);
    Map<ThreadInfo, CaptureNode> graphs = traceArt.getThreadsGraph();
    assertEquals(1, graphs.size());

    CaptureNode root = graphs.values().iterator().next();
    assertEquals(ClockType.GLOBAL, root.getClockType());

    long topLevelStart = root.getStart();

    root.setClockType(ClockType.THREAD);
    assertEquals(ClockType.THREAD, root.getClockType());

    // Thread clock is based on the topLevel call start time.
    // Therefore all nodes should have their thread start time >= top level's global start time.
    assertTrue(root.getStart() >= topLevelStart);
    for(CaptureNode child : root.getChildren()) {
      CaptureNode node = child;
      node.setClockType(ClockType.THREAD);
      assertTrue(node.getStart() >= topLevelStart);
    }
  }

  private static VmTraceData getTraceData() throws IOException {
    File traceFile = TestUtils.getWorkspaceFile(CPU_TRACES_DIR + "basic.trace");
    VmTraceParser parser = new VmTraceParser(traceFile);
    parser.parse();
    return parser.getTraceData();
  }

  private static void expectedChildrenIds(CaptureNode node, String... ids) {
    assertEquals(ids.length, node.getChildren().size());
    for (int i = 0; i < ids.length; ++i) {
      assertEquals(ids[i], node.getChildren().get(i).getData().getId());
    }
  }
}