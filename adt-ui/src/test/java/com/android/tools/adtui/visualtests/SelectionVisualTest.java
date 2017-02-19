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

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.SelectionComponent;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SelectionVisualTest extends VisualTest {

  private SelectionComponent mySelection;

  private Range myRange;

  private Range mySelectionRange;
  private JTextField myRangeMin;
  private JTextField myRangeMax;
  private JTextField mySelectionMin;
  private JTextField mySelectionMax;
  private SelectionModel mySelectionModel;
  private JPanel myComponent;

  @Override
  protected List<Updatable> createModelList() {

    myRange = new Range(0, 1000);
    mySelectionRange = new Range(100, 900);

    DefaultDataSeries<DefaultDurationData> series1 = new DefaultDataSeries<>();
    RangedSeries<DefaultDurationData> ranged1 = new RangedSeries<>(myRange, series1);
    DurationDataModel<DefaultDurationData> constraint1 = new DurationDataModel<>(ranged1);
    series1.add(100, new DefaultDurationData(100));
    series1.add(300, new DefaultDurationData(150));
    series1.add(700, new DefaultDurationData(150));

    DefaultDataSeries<DefaultDurationData> series2 = new DefaultDataSeries<>();
    RangedSeries<DefaultDurationData> ranged2 = new RangedSeries<>(myRange, series2);
    DurationDataModel<DefaultDurationData> constraint2 = new DurationDataModel<>(ranged2);
    series2.add(120, new DefaultDurationData(20));
    series2.add(500, new DefaultDurationData(50));
    series2.add(750, new DefaultDurationData(50));

    mySelectionModel = new SelectionModel(mySelectionRange, myRange);
    mySelectionModel.addConstraint(constraint1);
    mySelectionModel.addConstraint(constraint2);
    mySelection = new SelectionComponent(mySelectionModel);

    // Add the scene components to the list
    List<Updatable> componentsList = new ArrayList<>();
    componentsList.add(frameLength -> {
      myRangeMin.setText(String.valueOf(myRange.getMin()));
      myRangeMax.setText(String.valueOf(myRange.getMax()));
      mySelectionMin.setText(String.valueOf(mySelectionRange.getMin()));
      mySelectionMax.setText(String.valueOf(mySelectionRange.getMax()));
    });

    myComponent = new JPanel(new TabularLayout("*", "*"));
    myComponent.setBackground(Color.WHITE);
    myComponent.add(mySelection, new TabularLayout.Constraint(0, 0));
    myComponent.add(new DurationMarkers(constraint2, new Color(198, 198, 198)), new TabularLayout.Constraint(0, 0));
    myComponent.add(new DurationMarkers(constraint1, new Color(232, 232, 232)), new TabularLayout.Constraint(0, 0));
    return componentsList;
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Collections.singletonList(mySelection);
  }

  @Override
  public String getName() {
    return "Selection";
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    JPanel controls = VisualTest.createControlledPane(panel, myComponent);

    myRangeMin = addEntryField("Range Min", controls);
    myRangeMax = addEntryField("Range Max", controls);
    mySelectionMin = addEntryField("Selection Min", controls);
    mySelectionMax = addEntryField("Selection Max", controls);
    controls.add(VisualTest.createCheckbox("Full constraint", e -> {
      boolean selected = e.getStateChange() == ItemEvent.SELECTED;
      mySelectionModel.setSelectFullConstraint(selected);
    }));
    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }

  private JTextField addEntryField(String name, JPanel controls) {
    JPanel panel = new JPanel(new BorderLayout());
    JTextField field = new JTextField();
    panel.add(new JLabel(name), BorderLayout.WEST);
    panel.add(field);
    controls.add(panel);
    return field;
  }

  private static class DurationMarkers extends Component {
    private final DurationDataModel<DefaultDurationData> myConstraints;
    private final Color myColor;

    public DurationMarkers(DurationDataModel<DefaultDurationData> constraints, Color color) {
      myConstraints = constraints;
      myColor = color;
    }

    @Override
    public void paint(Graphics graphics) {
      Graphics2D g = (Graphics2D)graphics;
      Range range = myConstraints.getSeries().getXRange();
      ImmutableList<SeriesData<DefaultDurationData>> series = myConstraints.getSeries().getSeries();
      g.setColor(myColor);
      for (SeriesData<DefaultDurationData> data : series) {
        double x = (data.x - range.getMin()) / range.getLength() * getSize().width;
        double w = data.value.getDuration() / range.getLength() * getSize().width;
        g.fillRect((int)x, 0, (int)w, getSize().height);
      }

    }
  }
}
