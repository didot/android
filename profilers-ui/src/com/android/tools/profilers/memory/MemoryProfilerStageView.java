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

import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profilers.StageView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class MemoryProfilerStageView extends StageView {

  @NotNull
  private final Choreographer myChoreographer;

  @NotNull
  private final JComponent myComponent;

  public MemoryProfilerStageView(@NotNull MemoryProfilerStage stage) {
    super(stage);

    myComponent = new JPanel(new BorderLayout());

    myChoreographer = new Choreographer(myComponent);

    JToolBar toolBar = new JToolBar();
    JButton backButton = new JButton("Go back");
    toolBar.add(backButton);
    toolBar.setFloatable(false);
    backButton.addActionListener(action -> returnToStudioStage());
    myComponent.add(toolBar, BorderLayout.PAGE_START);

    LineChart lineChart = new LineChart();
    Range leftYRange = new Range();
    Range viewRange = stage.getStudioProfilers().getViewRange();
    // TODO set proper colors.
    lineChart.addLine(new RangedContinuousSeries("Java", viewRange, leftYRange, stage.getJavaMemory()));
    lineChart.addLine(new RangedContinuousSeries("Native", viewRange, leftYRange, stage.getNativeMemory()));
    lineChart.addLine(new RangedContinuousSeries("Graphics", viewRange, leftYRange, stage.getGraphicsMemory()));
    lineChart.addLine(new RangedContinuousSeries("Stack", viewRange, leftYRange, stage.getStackMemory()));
    lineChart.addLine(new RangedContinuousSeries("Code", viewRange, leftYRange, stage.getCodeMemory()));
    lineChart.addLine(new RangedContinuousSeries("Others", viewRange, leftYRange, stage.getOthersMemory()));
    myChoreographer.register(lineChart);

    myComponent.add(lineChart, BorderLayout.CENTER);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public JComponent getToolbar() {
    // TODO Add memory profiler toolbar elements.
    return new JPanel();
  }
}
