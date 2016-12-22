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

package com.android.tools.adtui;

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.Legend;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A label component that updates its value based on the reporting series passed to it.
 */
public class LegendComponent extends AnimatedComponent {

  public enum Orientation {
    HORIZONTAL,
    VERTICAL,
  }

  private static final BasicStroke LINE_STROKE = new BasicStroke(3);
  private static final BasicStroke BORDER_STROKE = new BasicStroke(1);

  /**
   * Side of the (squared) box icon in pixels.
   */
  private static final int ICON_WIDTH_PX = 10;

  /**
   * Distance, in pixels, between icons and their correspondent labels.
   */
  private static final int ICON_MARGIN_PX = 5;

  /**
   * Vertical space, in pixels, between the legend and the border of the parent component
   * or the next/previous vertical legend.
   */
  private static final int LEGEND_VERTICAL_PADDING_PX = 5;

  /**
   * Distance, in pixels, between legends.
   */
  private int LEGEND_MARGIN_PX = 10;

  /**
   * Min width of the label so that the legends don't shuffle around as the magnitude of the data changes.
   */
  private static final int LABEL_MIN_WIDTH_PX = 100;

  private LegendComponentModel myModel;

  /**
   * The visual configuration of the legends
   */
  private final Map<Legend, LegendConfig> myConfigs;

  @NotNull
  private List<JLabel> myLabelsToDraw = new ArrayList<>();

  @NotNull
  private Orientation myOrientation;

  /**
   * Legend component that renders a label, and icon for each series in a chart.
   *
   * @param orientation     Determines if we want the labels to be stacked horizontally or vertically
   * @param frequencyMillis How frequently the labels get updated
   */
  public LegendComponent(LegendComponentModel model) {
    myModel = model;
    myConfigs = new HashMap<>();
    myOrientation = Orientation.HORIZONTAL;
    myModel.addDependency(myAspectObserver)
      .onChange(LegendComponentModel.Aspect.LEGEND, this::modelChanged);
    modelChanged();
  }

  public void configure(Legend legend, LegendConfig config) {
    myConfigs.put(legend, config);
  }

  private void modelChanged() {
    int labels = myModel.getValues().size();
    for (int i = myLabelsToDraw.size(); i < labels; i++) {
      JBLabel label = new JBLabel();
      label.setFont(AdtUiUtils.DEFAULT_FONT);
      myLabelsToDraw.add(label);
    }
    if (myLabelsToDraw.size() > labels) {
      myLabelsToDraw.subList(labels, myLabelsToDraw.size()).clear();
    }

    Dimension oldSize = getPreferredSize();
    for (int i = 0; i < myModel.getValues().size(); i++) {
      JLabel label = myLabelsToDraw.get(i);
      Legend legend = myModel.getLegends().get(i);
      String text = legend.getName();
      String value = legend.getValue();
      if (value != null) {
        text += ": " + value;
      }
      label.setText(text);

      Dimension preferredSize = label.getPreferredSize();
      if (preferredSize.getWidth() < LABEL_MIN_WIDTH_PX) {
        preferredSize.width = LABEL_MIN_WIDTH_PX;
        label.setPreferredSize(preferredSize);
      }
      label.setBounds(0, 0, preferredSize.width, preferredSize.height);
    }
    if (oldSize != getPreferredSize()) {
      revalidate();
    }
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    // TODO: revisit this method and try to simplify it using JBPanels and a LayoutManager.
    Stroke defaultStroke = g2d.getStroke();
    for (int i = 0; i < myModel.getLegends().size(); ++i) {
      Legend data = myModel.getLegends().get(i);
      LegendConfig config = getConfig(data);
      JLabel label = myLabelsToDraw.get(i);
      Dimension labelPreferredSize = label.getPreferredSize();
      int xOffset = 0;

      // Draw the icon, and apply a translation offset for the label to be drawn.
      // TODO: Add config for LegendRenderData.IconType.DOTTED_LINE once we support dashed lines.
      if (config.getIcon() == LegendConfig.IconType.BOX) {
        // Adjust the box initial Y coordinate to align the box and the label vertically.
        int boxY = LEGEND_VERTICAL_PADDING_PX + (labelPreferredSize.height - ICON_WIDTH_PX) / 2;
        Color fillColor = config.getColor();
        g2d.setColor(fillColor);
        g2d.fillRect(0, boxY, ICON_WIDTH_PX, ICON_WIDTH_PX);

        int r = (int)(fillColor.getRed() * .8f);
        int g = (int)(fillColor.getGreen() * .8f);
        int b = (int)(fillColor.getBlue() * .8f);

        Color borderColor = new Color(r,g,b);
        g2d.setColor(borderColor);
        g2d.setStroke(BORDER_STROKE);
        g2d.drawRect(0, boxY, ICON_WIDTH_PX, ICON_WIDTH_PX);
        g2d.setStroke(defaultStroke);

        xOffset = ICON_WIDTH_PX + ICON_MARGIN_PX;
      }
      else if (config.getIcon() == LegendConfig.IconType.LINE) {
        g2d.setColor(config.getColor());
        g2d.setStroke(LINE_STROKE);
        int lineY = LEGEND_VERTICAL_PADDING_PX + labelPreferredSize.height / 2;
        g2d.drawLine(xOffset, lineY, ICON_WIDTH_PX, lineY);
        g2d.setStroke(defaultStroke);
        xOffset = ICON_WIDTH_PX + ICON_MARGIN_PX;
      }
      g2d.translate(xOffset, LEGEND_VERTICAL_PADDING_PX);
      label.paint(g2d);


      // Translate the draw position for the next set of labels.
      if (myOrientation == Orientation.HORIZONTAL) {
        g2d.translate(labelPreferredSize.width + LEGEND_MARGIN_PX, -LEGEND_VERTICAL_PADDING_PX);
      }
      else if (myOrientation == Orientation.VERTICAL) {
        g2d.translate(-xOffset, labelPreferredSize.height + LEGEND_VERTICAL_PADDING_PX);
      }
    }
  }

  private LegendConfig getConfig(Legend data) {
    LegendConfig config = myConfigs.get(data);
    if (config == null) {
      config = new LegendConfig(LegendConfig.IconType.BOX, Color.RED);
      myConfigs.put(data, config);
    }
    return config;
  }

  @Override
  public Dimension getPreferredSize() {
    int totalWidth = 0;
    int totalHeight = 0;
    // Using line icon (vs box icon) because it's wider. Extra space is better than lack of space.
    int iconPaddedSize = ICON_WIDTH_PX + ICON_MARGIN_PX + LEGEND_MARGIN_PX;
    // Calculate total size of all icons + labels.
    for (JLabel label : myLabelsToDraw) {
      Dimension size = label.getPreferredSize();
      if (myOrientation == Orientation.HORIZONTAL) {
        totalWidth += iconPaddedSize + size.width;
        if (totalHeight < size.height) {
          totalHeight = size.height;
        }
      }
      else if (myOrientation == Orientation.VERTICAL) {
        totalHeight += iconPaddedSize;
        if (totalWidth < size.width + iconPaddedSize) {
          totalWidth = size.width + iconPaddedSize;
        }
      }
    }
    int heightPadding = 2 * LEGEND_VERTICAL_PADDING_PX;
    // In the case of vertical legends, we have vertical padding for all the legends
    if (myOrientation == Orientation.VERTICAL) {
      heightPadding *= myModel.getLegends().size();
    }
    return new Dimension(totalWidth, totalHeight + heightPadding);
  }

  @Override
  public Dimension getMinimumSize() {
    return this.getPreferredSize();
  }
}