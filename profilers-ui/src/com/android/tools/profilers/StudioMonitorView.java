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
package com.android.tools.profilers;

import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.chart.linechart.LineChart;

import javax.swing.*;
import java.awt.*;

public class StudioMonitorView extends ProfilerStageView {

  private static final int CHOREOGRAPHER_FPS = 60;
  private final Choreographer myChoreographer;
  private final JPanel myComponent;
  private final LineChart myLineChart;

  public StudioMonitorView(StudioMonitor monitor) {
    super(monitor);
    myComponent = new JPanel(new BorderLayout());
    myChoreographer = new Choreographer(CHOREOGRAPHER_FPS, myComponent);
    myLineChart = new LineChart();
    //TODO Have this in separate sections in the view
    for (ProfilerMonitor m : monitor.getMonitors()) {
      myLineChart.addLine(m.getRangedSeries());
    }

    myChoreographer.register(myLineChart);
    myComponent.add(myLineChart, BorderLayout.CENTER);
  }


  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
