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
package com.android.tools.profilers.visualtests;

import com.android.testutils.TestUtils;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.Updatable;
import com.android.tools.adtui.visualtests.VisualTest;
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.perflib.vmtrace.VmTraceParser;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuTraceArt;
import com.android.tools.profilers.cpu.MethodModel;
import com.android.tools.profilers.cpu.SampledMethodUsageHRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CpuHTreeChartReducerVisualTest extends VisualTest {
  private static final String TEST_RESOURCE_DIR = "tools/adt/idea/profilers-ui/testData/visualtests/";

  private HTreeChart<MethodModel> myChart;
  private HTreeChart<MethodModel> myNotOptimizedChart;
  private Range myRange = new Range();

  @Override
  protected List<Updatable> createModelList() {
    return Collections.emptyList();
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Arrays.asList(myChart, myNotOptimizedChart);
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    HNode<MethodModel> node = parseAndGetHNode();
    myRange.set(node.getStart(), node.getEnd());

    myChart = new HTreeChart<>(myRange, HTreeChart.Orientation.TOP_DOWN);
    myChart.setHRenderer(new SampledMethodUsageHRenderer());
    myChart.setHTree(node);

    myNotOptimizedChart = new HTreeChart<>(myRange, HTreeChart.Orientation.TOP_DOWN, (rectangles, nodes) -> {});
    myNotOptimizedChart.setHRenderer(new SampledMethodUsageHRenderer());
    myNotOptimizedChart.setHTree(node);

    panel.setLayout(new GridLayout(2, 1));
    panel.add(myChart);
    panel.add(myNotOptimizedChart);
  }

  private static HNode<MethodModel> parseAndGetHNode() {
    File file = TestUtils.getWorkspaceFile(TEST_RESOURCE_DIR + "cpu_trace.trace");
    VmTraceParser parser = new VmTraceParser(file);
    CpuTraceArt art = new CpuTraceArt();
    try {
      parser.parse();
      art.parse(parser.getTraceData());
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    for (Map.Entry<ThreadInfo, CaptureNode> entry: art.getThreadsGraph().entrySet()) {
      if (entry.getKey().getName().equals("main")) {
        return entry.getValue();
      }
    }
    return null;
  }

  @Override
  public String getName() {
    return "CpuHTreeChartReducer";
  }
}
