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
import com.android.tools.adtui.model.LegendRenderData;
import com.android.tools.adtui.model.ReportingSeries;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A label component that updates its value based on the reporting series passed to it.
 */
public class LegendComponent extends AnimatedComponent {

  public enum Orientation {
    HORIZONTAL,
    VERTICAL,
  }

  /**
   * Thickness, in pixels, of the line icon.
   */
  private static final int LINE_THICKNESS = 3;

  /**
   * Width of the line icon in pixels.
   */
  private static final int LINE_ICON_WIDTH_PX = 16;

  /**
   * Side of the (squared) box icon in pixels.
   */
  private static final int BOX_ICON_SIDE_PX = 10;

  /**
   * Distance, in pixels, between icons and their correspondent labels.
   */
  private static final int ICON_MARGIN_PX = 10;

  /**
   * Vertical space, in pixels, between the legend and the border of the parent component
   * or the next/previous vertical legend.
   */
  private static final int LEGEND_VERTICAL_PADDING_PX = 5;

  /**
   * Distance, in pixels, between legends.
   */
  private int LEGEND_MARGIN_PX = 20;

  private int mFrequencyMillis;

  private List<JLabel> mLabelsToDraw;

  private long mLastUpdate;

  private List<LegendRenderData> mLegendRenderData;

  private Orientation mOrientation;

  /**
   * Legend component that renders a label, and icon for each series in a chart.
   *
   * @param orientation      Determines if we want the labels to be stacked horizontally or vertically
   * @param frequencyMillis  How frequently the labels get updated
   */
  public LegendComponent(Orientation orientation, int frequencyMillis) {
    mFrequencyMillis = frequencyMillis;
    mOrientation = orientation;
    mLastUpdate = 0;
  }

  /**
   * Clears existing LegendRenderData and adds new ones.
   */
  public void setLegendData(List<LegendRenderData> data) {
    mLegendRenderData = new ArrayList<>(data);
    mLabelsToDraw = new ArrayList<>(mLegendRenderData.size());
    for (LegendRenderData initialData : mLegendRenderData) {
      JBLabel label = new JBLabel(initialData.getLabel());
      label.setFont(AdtUiUtils.DEFAULT_FONT);
      mLabelsToDraw.add(label);
    }
  }

  @Override
  protected void updateData() {
    long now = System.currentTimeMillis();
    if (now - mLastUpdate > mFrequencyMillis) {
      mLastUpdate = now;
      for (int i = 0; i < mLegendRenderData.size(); ++i) {
        LegendRenderData data = mLegendRenderData.get(i);
        ReportingSeries series = data.getSeries();
        JLabel label = mLabelsToDraw.get(i);
        Dimension preferredSize = label.getPreferredSize();
        label.setBounds(0, 0, preferredSize.width, preferredSize.height);
        if (series != null) {
          ReportingSeries.ReportingData report = series.getLatestReportingData();
          if (report != null) {
            label.setText(String.format("%s: %s", series.getLabel(), report.formattedYData));
          }
        }
      }

      // As we adjust the size of the label we need to adjust our own size
      // to tell our parent to give us enough room to draw.
      Dimension newSize = getLegendPreferredSize();
      if (newSize != getPreferredSize()) {
        setPreferredSize(newSize);
        // Set the minimum height of the component to avoid hiding all the labels
        // in case they are longer than the component's total width
        setMinimumSize(new Dimension(getMinimumSize().width, newSize.height));
        revalidate();
      }
    }
  }

  @Override
  protected void draw(Graphics2D g2d) {
    // TODO: revisit this method and try to simplify it using JBPanels and a LayoutManager.
    for (int i = 0; i < mLegendRenderData.size(); ++i) {
      LegendRenderData data = mLegendRenderData.get(i);
      JLabel label = mLabelsToDraw.get(i);
      Dimension labelPreferredSize = label.getPreferredSize();
      int xOffset = 0;

      // Draw the icon, and apply a translation offset for the label to be drawn.
      // TODO: Add config for LegendRenderData.IconType.DOTTED_LINE once we support dashed lines.
      if (data.getIcon() == LegendRenderData.IconType.BOX) {
        // Adjust the box initial Y coordinate to align the box and the label vertically.
        int boxY = LEGEND_VERTICAL_PADDING_PX + (labelPreferredSize.height - BOX_ICON_SIDE_PX) / 2;
        g2d.setColor(data.getColor());
        g2d.fillRect(0, boxY, BOX_ICON_SIDE_PX, BOX_ICON_SIDE_PX);
        xOffset = BOX_ICON_SIDE_PX + ICON_MARGIN_PX;
      }
      else if (data.getIcon() == LegendRenderData.IconType.LINE) {
        g2d.setColor(data.getColor());
        Stroke defaultStroke = g2d.getStroke();
        g2d.setStroke(new BasicStroke(LINE_THICKNESS));
        int lineY = LEGEND_VERTICAL_PADDING_PX + labelPreferredSize.height / 2;
        g2d.drawLine(xOffset, lineY, LINE_ICON_WIDTH_PX, lineY);
        g2d.setStroke(defaultStroke);
        xOffset = LINE_ICON_WIDTH_PX + ICON_MARGIN_PX;
      }
      g2d.translate(xOffset, LEGEND_VERTICAL_PADDING_PX);
      label.paint(g2d);

      // Translate the draw position for the next set of labels.
      if (mOrientation == Orientation.HORIZONTAL) {
        g2d.translate(labelPreferredSize.width + LEGEND_MARGIN_PX, -LEGEND_VERTICAL_PADDING_PX);
      }
      else if (mOrientation == Orientation.VERTICAL) {
        g2d.translate(-xOffset, labelPreferredSize.height + LEGEND_VERTICAL_PADDING_PX);
      }
    }
  }

  private Dimension getLegendPreferredSize() {
    int totalWidth = 0;
    int totalHeight = 0;
    // Using line icon (vs box icon) because it's wider. Extra space is better than lack of space.
    int iconPaddedSize = LINE_ICON_WIDTH_PX + ICON_MARGIN_PX + LEGEND_MARGIN_PX;
    // Calculate total size of all icons + labels.
    for (JLabel label : mLabelsToDraw) {
      Dimension size = label.getPreferredSize();
      if (mOrientation == Orientation.HORIZONTAL) {
        totalWidth += iconPaddedSize + size.width;
        if (totalHeight < size.height) {
          totalHeight = size.height;
        }
      }
      else if (mOrientation == Orientation.VERTICAL) {
        totalHeight += iconPaddedSize;
        if (totalWidth < size.width + iconPaddedSize) {
          totalWidth = size.width + iconPaddedSize;
        }
      }
    }
    int heightPadding = 2 * LEGEND_VERTICAL_PADDING_PX;
    // In the case of vertical legends, we have vertical padding for all the legends
    if (mOrientation == Orientation.VERTICAL) {
      heightPadding *= mLabelsToDraw.size();
    }
    return new Dimension(totalWidth, totalHeight + heightPadding);
  }
}