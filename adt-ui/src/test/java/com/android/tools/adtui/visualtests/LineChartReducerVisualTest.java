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
package com.android.tools.adtui.visualtests;

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.*;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.util.Arrays;
import java.util.List;

// TODO: Add scrolling support to LineCharts
// TODO: As SelectionComponent used only for zooming mechanism, consider replacing it with only zooming without selection
public class LineChartReducerVisualTest extends VisualTest {
  private static final int AXIS_SIZE = 80;

  private Range myViewXRange;
  private Range mySelectionXRange;

  private LineChart myLineChart;
  private LineChartModel myLineChartModel;
  private LineChart myOptimizedLineChart;
  private AxisComponent myXAxis;

  private DefaultDataSeries<Long> myData;
  private RangedContinuousSeries mySeries;
  private RangedContinuousSeries myOptimizedSeries;
  private int myVariance = 10;
  private int mySampleSize = 10;
  private SelectionComponent mySelection;
  private LineChartModel myOptimizedLineChartModel;
  private AxisComponentModel myXAxisModel;

  @Override
  protected List<Updatable> createModelList() {
    myViewXRange = new Range(0, 0);
    mySelectionXRange = new Range();

    myLineChartModel = new LineChartModel();
    myLineChart = new LineChart(myLineChartModel, (path, config) -> path);
    myOptimizedLineChartModel = new LineChartModel();
    myOptimizedLineChart = new LineChart(myOptimizedLineChartModel);

    myXAxisModel =
      new AxisComponentModel(myViewXRange, new SingleUnitAxisFormatter(1, 5, 1, ""));
    myXAxis = new AxisComponent(myXAxisModel, AxisComponent.AxisOrientation.BOTTOM);
    SelectionModel selection = new SelectionModel(mySelectionXRange, myViewXRange);
    mySelection = new SelectionComponent(selection);

    myData = new DefaultDataSeries<>();
    mySeries = new RangedContinuousSeries("Original", myViewXRange, new Range(0, 0), myData);
    myOptimizedSeries = new RangedContinuousSeries("Reduced", myViewXRange, new Range(0, 0), myData);
    myLineChartModel.add(mySeries);
    myOptimizedLineChartModel.add(myOptimizedSeries);
    myLineChart.configure(mySeries, new LineConfig(JBColor.BLUE));
    myOptimizedLineChart.configure(myOptimizedSeries, new LineConfig(JBColor.RED));

    return Arrays.asList(myLineChartModel, myOptimizedLineChartModel, myXAxisModel);
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Arrays.asList(myLineChart, myOptimizedLineChart);
  }

  @Override
  public String getName() {
    return "LineChartReducer";
  }

  private void addData(int variance, int count) {
    for (int i = 0; i < count; ++i) {
      ImmutableList<SeriesData<Long>> data = myData.getAllData();
      long x = data.isEmpty() ? (long)(Math.random() * 4) : data.get(data.size() - 1).x + 1;
      long last = data.isEmpty() ? 0 : data.get(data.size() - 1).value;
      float delta = ((float)Math.random() - 0.5f) * variance;
      // Make sure not to add negative numbers.
      long current = Math.max(last + (long)delta, 0);
      myData.add(x, current);
      myViewXRange.setMax(x + 4);
    }
  }

  private JLayeredPane createLayeredSelection(JComponent host) {
    JBLayeredPane layeredPane = new JBLayeredPane();
    layeredPane.add(host);
    layeredPane.add(mySelection);
    layeredPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        JLayeredPane host = (JLayeredPane)e.getComponent();
        if (host != null) {
          Dimension dim = host.getSize();
          for (Component c : host.getComponents()) {
              c.setBounds(0, 0, dim.width, dim.height);
          }
        }
      }
    });
    return layeredPane;
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    JPanel chartPane = new JPanel();
    chartPane.setLayout(new BoxLayout(chartPane, BoxLayout.Y_AXIS));
    myXAxis.setMaximumSize(new Dimension(Integer.MAX_VALUE, AXIS_SIZE));
    chartPane.add(myLineChart);
    chartPane.add(createLayeredSelection(myOptimizedLineChart));
    chartPane.add(myXAxis);

    JPanel controls = VisualTest.createControlledPane(panel, chartPane);

    myLineChart.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR));
    myOptimizedLineChart.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR));

    controls.add(VisualTest.createVariableSlider("Variance", 0, 100, new VisualTests.Value() {
      @Override
      public void set(int v) {
        myVariance = v;
      }

      @Override
      public int get() {
        return myVariance;
      }
    }));

    controls.add(VisualTest.createVariableSlider("Sample Size", 10, 100000, new VisualTests.Value() {
      @Override
      public void set(int v) {
        mySampleSize = v;
      }

      @Override
      public int get() {
        return mySampleSize;
      }
    }));

    controls.add(VisualTest.createCheckbox("Filled line", itemEvent -> {
      boolean isFilled = itemEvent.getStateChange() == ItemEvent.SELECTED;
      myLineChart.getLineConfig(mySeries).setFilled(isFilled);
      myOptimizedLineChart.getLineConfig(myOptimizedSeries).setFilled(isFilled);
    }));

    controls.add(VisualTest.createCheckbox("Stepped line", itemEvent -> {
      boolean isStepped = itemEvent.getStateChange() == ItemEvent.SELECTED;
      myLineChart.getLineConfig(mySeries).setStepped(isStepped);
      myOptimizedLineChart.getLineConfig(myOptimizedSeries).setStepped(isStepped);
    }));


    controls.add(VisualTest.createButton("Add samples", e -> addData(myVariance, mySampleSize)));
    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }
}
