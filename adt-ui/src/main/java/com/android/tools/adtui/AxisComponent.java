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
import com.android.tools.adtui.common.RotatedLabel;
import com.android.tools.adtui.model.AxisComponentModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.intellij.ui.components.JBLabel;
import gnu.trove.TFloatArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A component that draws an axis based on data from a {@link Range} object.
 */
public final class AxisComponent extends AnimatedComponent {

  private static final BasicStroke DEFAULT_AXIS_STROKE = new BasicStroke(1);
  private static final int MARKER_LABEL_OFFSET_PX = 3;
  private static final int MAXIMUM_LABEL_WIDTH = 50;
  private static final int DEFAULT_MAJOR_MARKER_LENGTH = 10;
  private static final int DEFAULT_MINOR_MARKER_LENGTH = 4;

  @Nullable private JLabel myLabel;

  /**
   * Interpolated/Animated min value.
   */
  private double myCurrentMinValueRelative;

  /**
   * Length of the axis in pixels - used for internal calculation.
   */
  private int myAxisLength;

  /**
   * Calculated - Interval value per minor marker.
   */
  private float myMinorInterval;

  /**
   * Calculated - Number of pixels per minor interval.
   */
  private float myMinorScale;

  /**
   * Cached major marker positions.
   */
  private final TFloatArrayList myMajorMarkerPositions;

  /**
   * Cached minor marker positions.
   */
  private final TFloatArrayList myMinorMarkerPositions;

  /**
   * Cached marker labels
   */
  @NotNull private final List<String> myMarkerLabels;

  /**
   * Cached max marker lablels
   */
  private String myMaxLabel;

  /**
   * Cached min marker lablels
   */
  private String myMinLabel;

  private AxisComponentModel myModel;

  private boolean myRender;

  private int myMajorMarkerLength = DEFAULT_MAJOR_MARKER_LENGTH;
  private int myMinorMarkerLength = DEFAULT_MINOR_MARKER_LENGTH;

  private int myStartMargin;
  private int myEndMargin;
  private boolean myShowMin;
  private boolean myShowMax;
  private boolean myShowUnitAtMax;
  private boolean myShowAxisLine = true;



  public AxisComponent(@NotNull AxisComponentModel model) {
    myModel = model;
    myMajorMarkerPositions = new TFloatArrayList();
    myMinorMarkerPositions = new TFloatArrayList();
    myMarkerLabels = new ArrayList<>();

    // Only construct and show the axis label if it is set.
    if (!myModel.getLabel().isEmpty()) {
      switch (myModel.getOrientation()) {
        case LEFT:
        case RIGHT:
          myLabel = new RotatedLabel(myModel.getLabel());
          myLabel.setSize(mDefaultFontMetrics.getHeight(), mDefaultFontMetrics.stringWidth(myModel.getLabel()));
          break;
        case TOP:
        case BOTTOM:
        default:
          myLabel = new JBLabel(myModel.getLabel());
          myLabel.setSize(mDefaultFontMetrics.stringWidth(myModel.getLabel()), mDefaultFontMetrics.getHeight());
      }
      myLabel.setFont(AdtUiUtils.DEFAULT_FONT);
    }
    myModel.addDependency()
      .onChange(AxisComponentModel.Aspect.AXIS, this::modelChanged);
  }

  private void modelChanged() {
    myRender = true;
    opaqueRepaint();
  }

  @Nullable
  public String getLabel() {
    return myLabel == null ? null : myLabel.getText();
  }

  @NotNull
  public TFloatArrayList getMajorMarkerPositions() {
    return myMajorMarkerPositions;
  }

  /**
   * Returns the position where a value would appear on this axis.
   */
  public float getPositionAtValue(double value) {
    float offset = (float)(myMinorScale * ((value - myModel.getOffset()) - myCurrentMinValueRelative) / myMinorInterval);
    float ret = 0;
    switch (myModel.getOrientation()) {
      case LEFT:
      case RIGHT:
        // Vertical axes are drawn from bottom to top so reverse the offset.
        ret = 1 - offset;
        break;
      case TOP:
      case BOTTOM:
        ret = offset;
        break;
    }

    return ret * myAxisLength;
  }

  /**
   * Returns the value corresponding to a pixel position on the axis.
   */
  public double getValueAtPosition(int position) {
    float offset = 0;
    switch (myModel.getOrientation()) {
      case LEFT:
      case RIGHT:
        // Vertical axes are drawn from bottom to top so reverse the position.
        offset = myAxisLength - position;
        break;
      case TOP:
      case BOTTOM:
        offset = position;
        break;
    }

    float normalizedOffset = offset / myAxisLength;
    return myModel.getOffset() + myCurrentMinValueRelative + myMinorInterval * normalizedOffset / myMinorScale;
  }

  public void render() {
    myMarkerLabels.clear();
    myMajorMarkerPositions.reset();
    myMinorMarkerPositions.reset();
    myCurrentMinValueRelative = myModel.getRange().getMin() - myModel.getOffset();

    double currentMaxValueRelative = myModel.getRange().getMax() - myModel.getOffset();
    double range = myModel.getRange().getLength();
    double labelRange = myModel.getGlobalRange() == null ? range : myModel.getGlobalRange().getLength();

    BaseAxisFormatter formatter = myModel.getFormatter();
    // During the postAnimate phase, use the interpolated min/max/range values to calculate the current major and minor intervals that
    // should be used. Based on the interval values, cache the normalized marker positions which will be used during the draw call.
    float majorInterval = formatter.getMajorInterval(range);
    myMinorInterval = formatter.getMinorInterval(majorInterval);
    myMinorScale = (float)(myMinorInterval / range);

    // Calculate the value and offset of the first major marker
    double firstMarkerValue = Math.floor(myCurrentMinValueRelative / majorInterval) * majorInterval;
    // Percentage offset of first major marker.
    float firstMarkerOffset = (float)(myMinorScale * (firstMarkerValue - myCurrentMinValueRelative) / myMinorInterval);

    // Calculate marker positions
    int numMarkers = (int)Math.floor((currentMaxValueRelative - firstMarkerValue) / myMinorInterval) + 1;
    int numMinorPerMajor = (int)(majorInterval / myMinorInterval);
    for (int i = 0; i < numMarkers; i++) {
      // Discard negative values (TODO configurable?)
      double markerValue = firstMarkerValue + i * myMinorInterval;
      if (markerValue < 0f) {
        continue;
      }

      // Discard out of bound values.
      float markerOffset = firstMarkerOffset + i * myMinorScale;
      if (markerOffset < 0f || markerOffset > 1f) {
        continue;
      }

      if (i % numMinorPerMajor == 0) {    // Major Tick.
        myMajorMarkerPositions.add(markerOffset);
        myMarkerLabels.add(formatter.getFormattedString(labelRange, markerValue, !myShowUnitAtMax));
      }
      else {
        myMinorMarkerPositions.add(markerOffset);
      }
    }

    if (myShowMin) {
      myMinLabel = formatter.getFormattedString(labelRange, myCurrentMinValueRelative, !myShowUnitAtMax);
    }
    if (myShowMax) {
      myMaxLabel = formatter.getFormattedString(labelRange, currentMaxValueRelative, true);
    }
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    if (myRender) {
      render();
      myRender = false;
    }
    // Calculate drawing parameters.
    Point startPoint = new Point();
    Point endPoint = new Point();
    Point labelPoint = new Point();
    switch (myModel.getOrientation()) {
      case LEFT:
        startPoint.x = endPoint.x = dim.width - 1;
        startPoint.y = dim.height - myStartMargin - 1;
        endPoint.y = myEndMargin;
        myAxisLength = startPoint.y - endPoint.y;

        //Affix label to top left.
        labelPoint.x = 0;
        labelPoint.y = endPoint.y;
        break;
      case BOTTOM:
        startPoint.x = myStartMargin;
        endPoint.x = dim.width - myEndMargin - 1;
        startPoint.y = endPoint.y = 0;
        myAxisLength = endPoint.x - startPoint.x;

        //Affix label to bottom left
        labelPoint.x = startPoint.x;
        labelPoint.y = getHeight() - (mDefaultFontMetrics.getMaxAscent() + mDefaultFontMetrics.getMaxDescent());
        break;
      case RIGHT:
        startPoint.x = endPoint.x = 0;
        startPoint.y = dim.height - myStartMargin - 1;
        endPoint.y = myEndMargin;
        myAxisLength = startPoint.y - endPoint.y;

        //Affix label to top right
        labelPoint.x = getWidth() - mDefaultFontMetrics.getMaxAdvance();
        labelPoint.y = endPoint.y;
        break;
      case TOP:
        startPoint.x = myStartMargin;
        endPoint.x = dim.width - myEndMargin - 1;
        startPoint.y = endPoint.y = dim.height - 1;
        myAxisLength = endPoint.x - startPoint.x;

        //Affix label to top left
        labelPoint.x = 0;
        labelPoint.y = 0;
        break;
    }

    if (myAxisLength > 0) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(getForeground());
      g.setStroke(DEFAULT_AXIS_STROKE);

      if (myShowAxisLine) {
        g.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y);
      }

      // TODO account for pixel spacing so we can skip ticks if the length is too narrow.
      drawMarkers(g, startPoint);

      if (myLabel != null) {
        AffineTransform initialTransform = g.getTransform();
        g.translate(labelPoint.x, labelPoint.y);
        myLabel.paint(g);
        g.setTransform(initialTransform);
      }
    }
  }

  private void drawMarkers(Graphics2D g2d, Point origin) {
    g2d.setFont(AdtUiUtils.DEFAULT_FONT);

    if (myShowMin && myMinLabel != null) {
      drawMarkerLabel(g2d, 0, origin, myMinLabel, true);
    }
    if (myShowMax && myMaxLabel != null) {
      drawMarkerLabel(g2d, myAxisLength, origin, myMaxLabel, true);
    }

    Line2D.Float line = new Line2D.Float();

    // Draw minor ticks.
    for (int i = 0; i < myMinorMarkerPositions.size(); i++) {
      float scaledPosition = myMinorMarkerPositions.get(i) * myAxisLength;
      drawMarkerLine(g2d, line, scaledPosition, origin, myMinorMarkerLength);
    }

    // Draw major ticks.
    for (int i = 0; i < myMajorMarkerPositions.size(); i++) {
      float scaledPosition = myMajorMarkerPositions.get(i) * myAxisLength;
      drawMarkerLine(g2d, line, scaledPosition, origin, myMajorMarkerLength);
      drawMarkerLabel(g2d, scaledPosition, origin, myMarkerLabels.get(i), false);
    }
  }

  private void drawMarkerLine(Graphics2D g2d, Line2D.Float line, float markerOffset,
                              Point origin, int markerLength) {
    float markerStartX = 0, markerStartY = 0, markerEndX = 0, markerEndY = 0;
    switch (myModel.getOrientation()) {
      case LEFT:
        markerStartX = origin.x - markerLength;
        markerStartY = markerEndY = origin.y - markerOffset;
        markerEndX = origin.x;
        break;
      case RIGHT:
        markerStartX = 0;
        markerStartY = markerEndY = origin.y - markerOffset;
        markerEndX = markerLength;
        break;
      case TOP:
        markerStartX = markerEndX = origin.x + markerOffset;
        markerStartY = origin.y - markerLength;
        markerEndY = origin.y;
        break;
      case BOTTOM:
        markerStartX = markerEndX = origin.x + markerOffset;
        markerStartY = 0;
        markerEndY = markerLength;
        break;
    }

    line.setLine(markerStartX, markerStartY, markerEndX, markerEndY);
    g2d.draw(line);
  }

  private void drawMarkerLabel(Graphics2D g2d, float markerOffset, Point origin, String value, boolean alwaysRender) {
    int stringAscent = mDefaultFontMetrics.getAscent();
    int stringLength = mDefaultFontMetrics.stringWidth(value);

    // Marker label placement positions are as follows:
    // 1. For horizontal axes, offset to the right relative to the marker position
    // 2. For vertical axes, centered around the marker position
    // The offset amount is specified by MARKER_LABEL_OFFSET_PX in both cases.
    float labelX, labelY;
    float reserved; // reserved space for min/max labels.
    switch (myModel.getOrientation()) {
      case LEFT:
        labelX = origin.x - (myMajorMarkerLength + stringLength + MARKER_LABEL_OFFSET_PX);
        labelY = origin.y - markerOffset + stringAscent * 0.5f;
        reserved = stringAscent;
        break;
      case RIGHT:
        labelX = myMajorMarkerLength + MARKER_LABEL_OFFSET_PX;
        labelY = origin.y - markerOffset + stringAscent * 0.5f;
        reserved = stringAscent;
        break;
      case TOP:
        labelX = origin.x + markerOffset + MARKER_LABEL_OFFSET_PX;
        labelY = origin.y - myMinorMarkerLength;
        reserved = stringLength;
        break;
      case BOTTOM:
        labelX = origin.x + markerOffset + MARKER_LABEL_OFFSET_PX;
        labelY = myMinorMarkerLength + stringAscent;
        reserved = stringLength;
        break;
      default:
        throw new AssertionError("Unexpected orientation: " + myModel.getOrientation());
    }

    if (alwaysRender || (markerOffset - reserved > 0 && markerOffset + reserved < myAxisLength)) {
      g2d.setColor(getForeground());
      g2d.drawString(value, labelX, labelY);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    int width = Math.max(myMajorMarkerLength, myMinorMarkerLength) + MARKER_LABEL_OFFSET_PX + MAXIMUM_LABEL_WIDTH;
    int height = 1;
    return (myModel.getOrientation() == AxisComponentModel.AxisOrientation.LEFT || myModel.getOrientation() == AxisComponentModel.AxisOrientation.RIGHT) ?
           new Dimension(width, height) : new Dimension(height, width);
  }

  public AxisComponentModel getModel() {
    return myModel;
  }


  public void setShowAxisLine(boolean showAxisLine) {
    myShowAxisLine = showAxisLine;
  }

  public void setShowMax(boolean showMax) {
    myShowMax = showMax;
  }

  public void setShowUnitAtMax(boolean showUnitAtMax) {
    myShowUnitAtMax = showUnitAtMax;
  }

  public void setMarkerLengths(int majorMarker, int minorMarker) {
    myMajorMarkerLength = majorMarker;
    myMinorMarkerLength = minorMarker;
  }

  public void setMargins(int startMargin, int endMargin) {
    myStartMargin = startMargin;
    myEndMargin = endMargin;
  }
}