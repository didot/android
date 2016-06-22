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
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.intellij.ui.components.JBLabel;
import gnu.trove.TFloatArrayList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;

import static com.android.tools.adtui.AxisComponent.AxisOrientation.LEFT;
import static com.android.tools.adtui.AxisComponent.AxisOrientation.RIGHT;

/**
 * A component that draw an axis based on data from a {@link Range} object.
 */
public final class AxisComponent extends AnimatedComponent {

  public enum AxisOrientation {
    LEFT,
    BOTTOM,
    RIGHT,
    TOP
  }

  private static final int MAJOR_MARKER_LENGTH = 12;
  private static final int MINOR_MARKER_LENGTH = 3;
  private static final int MARKER_LABEL_MARGIN = 2;
  private static final int LABEL_BOUNDS_OFFSET = 5;
  private static final int MAXIMUM_LABEL_WIDTH = 50;

  /**
   * The axis to which this axis should sync its intervals to.
   */
  private AxisComponent mParentAxis;

  /**
   * The Range object that drives this axis.
   */
  @NotNull
  private final Range mRange;

  /**
   * The Global range object.
   */
  @NotNull
  private final Range mGlobalRange;

  /**
   * Name of the axis.
   */
  @NotNull
  private final JLabel mLabel;

  /**
   * The font metrics of the tick labels.
   */
  @NotNull
  private final FontMetrics mMetrics;

  /**
   * Orientation of the axis.
   */
  @NotNull
  private final AxisOrientation mOrientation;

  /**
   * Margin before the start of the axis.
   */
  private final int mStartMargin;

  /**
   * Margin after the end of the axis.
   */
  private final int mEndMargin;

  /**
   * Display min/max values on the axis.
   */
  private boolean mShowMinMax;

  /**
   * Clamp the Range's max to the next major tick interval.
   */
  private boolean mClampToMajorTicks;

  /**
   * Axis formatter.
   */
  @NotNull
  private BaseAxisFormatter mFormatter;

  /**
   * Interpolated/Animated max value.
   */
  private double mCurrentMaxValue;

  /**
   * Interpolated/Animated min value.
   */
  private double mCurrentMinValue;

  /**
   * Length of the axis in pixels - used for internal calculation.
   */
  private int mAxisLength;

  /**
   * Calculated - Interval value per major marker.
   */
  private float mMajorInterval;

  /**
   * Calculated - Interval value per minor marker.
   */
  private float mMinorInterval;

  /**
   * Calculated - Number of pixels per major interval.
   */
  private float mMajorScale;

  /**
   * Calculated - Number of pixels per minor interval.
   */
  private float mMinorScale;

  /**
   * Calculated - Number of major ticks that will be rendered based on the target range.
   * This value is used by a child axis to sync its major tick spacing with its parent.
   */
  private float mMajorNumTicksTarget;

  /**
   * Calculated - Value of first major marker.
   */
  private double mFirstMarkerValue;

  /**
   * Cached major marker positions.
   */
  private final TFloatArrayList mMajorMarkerPositions;

  /**
   * Cached minor marker positions.
   */
  private final TFloatArrayList mMinorMarkerPositions;

  /**
   * TODO consider replacing this constructor with a builder pattern.
   * @param range       A Range object this AxisComponent listens to for the min/max values.
   * @param globalRange The global min/max range.
   * @param label       The label/name of the axis.
   * @param orientation The orientation of the axis.
   * @param startMargin Space (in pixels) before the start of the axis.
   * @param endMargin   Space (in pixels) after the end of the axis.
   * @param showMinMax  If true, min/max values are shown on the axis.
   * @param domain      Domain used for formatting the tick markers.
   */
  public AxisComponent(@NotNull Range range, @NotNull Range globalRange,
                       @NotNull String label, @NotNull AxisOrientation orientation,
                       int startMargin, int endMargin, boolean showMinMax, @NotNull BaseAxisFormatter formatter) {
    mRange = range;
    mGlobalRange = globalRange;
    mOrientation = orientation;
    mShowMinMax = showMinMax;
    mFormatter = formatter;
    mMajorMarkerPositions = new TFloatArrayList();
    mMinorMarkerPositions = new TFloatArrayList();

    // Leaves space before and after the axis, this helps to prevent the start/end labels from being clipped.
    // TODO these margins complicate the draw code, an alternative is to implement the labels as a different Component,
    // so its draw region is not clipped by the length of the axis.
    mStartMargin = startMargin;
    mEndMargin = endMargin;

    mMetrics = getFontMetrics(AdtUiUtils.DEFAULT_FONT);

    switch (mOrientation) {
      case LEFT:
      case RIGHT:
        mLabel = new RotatedLabel(label);
        mLabel.setSize(mMetrics.getHeight(), mMetrics.stringWidth(label));
        break;
      case TOP:
      case BOTTOM:
      default:
        mLabel = new JBLabel(label);
        mLabel.setSize(mMetrics.stringWidth(label), mMetrics.getHeight());
    }
    mLabel.setFont(AdtUiUtils.DEFAULT_FONT);

  }

  public void setClampToMajorTicks(boolean clamp) {
    mClampToMajorTicks = clamp;
  }

  /**
   * Updates the BaseAxisFormatter for this axis which affects how its ticks/labels are calculated and rendered.
   */
  public void setAxisFormatter(BaseAxisFormatter formatter) {
    mFormatter = formatter;
  }

  /**
   * When assigned a parent, the tick interval calculations are
   * sync'd to the parent so that their major intervals would have the same scale.
   */
  public void setParentAxis(AxisComponent parent) {
    mParentAxis = parent;
  }

  @NotNull
  public AxisOrientation getOrientation() {
    return mOrientation;
  }

  @NotNull
  public TFloatArrayList getMajorMarkerPositions() {
    return mMajorMarkerPositions;
  }

  public void setLabelVisible(boolean isVisible) {
    mLabel.setVisible(isVisible);
  }

  /**
   * Returns the position where a value would appear on this axis.
   */
  public float getPositionAtValue(double value) {
    float offset = (float)(mMinorScale * (value - mCurrentMinValue) / mMinorInterval);
    float ret = 0;
    switch (mOrientation) {
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

    return ret * mAxisLength;
  }

  /**
   * Returns the value corresponding to a pixel position on the axis.
   */
  public double getValueAtPosition(int position) {
    float offset = 0;
    switch (mOrientation) {
      case LEFT:
      case RIGHT:
        // Vertical axes are drawn from bottom to top so reverse the position.
        offset = mAxisLength - position;
        break;
      case TOP:
      case BOTTOM:
        offset = position;
        break;
    }

    float normalizedOffset = offset / mAxisLength;
    return mCurrentMinValue + mMinorInterval * normalizedOffset / mMinorScale;
  }

  /**
   * Returns the formatted value corresponding to a pixel position on the axis.
   * The formatting depends on the {@link BaseAxisFormatter} object associated
   * with this axis.
   *
   * e.g. For a value of 1500 in milliseconds, this will return "1.5s".
   */
  @NotNull
  public String getFormattedValueAtPosition(int position) {
    return mFormatter.getFormattedString(mGlobalRange.getLength(), getValueAtPosition(position));
  }

  @Override
  protected void updateData() {
    double maxTarget = mRange.getMaxTarget();
    double rangeTarget = mRange.getTargetLength();
    double clampedMaxTarget;

    // During the animate/updateData phase, the axis updates the range's max to a new target based on whether:
    // 1. mClampToMajorTicks is enabled
    //    - This would increase the max to an integral multiplier of the major interval.
    // 2. The axis has a parent axis
    //    - This would use the parent axis's major num ticks to calculate its own major interval that would fit rangeTarget while
    //      matching the tick spacing of the parent axis.
    // TODO Handle non-zero min offsets. Currently these features are used only for y axes and a non-zero use case does not exist yet.
    if (mParentAxis == null) {
      int majorInterval = mFormatter.getMajorInterval(rangeTarget);
      mMajorNumTicksTarget = mClampToMajorTicks ? (float)Math.ceil(maxTarget / majorInterval) : (float)(maxTarget / majorInterval);
      clampedMaxTarget = mMajorNumTicksTarget * majorInterval;
    } else {
      int majorInterval = mFormatter.getInterval(rangeTarget, (int)Math.floor(mParentAxis.mMajorNumTicksTarget));
      clampedMaxTarget = mParentAxis.mMajorNumTicksTarget * majorInterval;
    }

    mRange.setMaxTarget(clampedMaxTarget);
  }

  @Override
  public void postAnimate() {
    mMajorMarkerPositions.reset();
    mMinorMarkerPositions.reset();
    mCurrentMinValue = mRange.getMin();
    mCurrentMaxValue = mRange.getMax();
    double range = mRange.getLength();

    // During the postAnimate phase, use the interpoalted min/max/range values to calculate the current major and minor intervals that
    // should be used. Based on the interval values, cache the normalized marker positions which will be used during the draw call.
    mMajorInterval = mFormatter.getMajorInterval(range);
    mMinorInterval = mFormatter.getMinorInterval(mMajorInterval);
    mMajorScale = (float)(mMajorInterval / range);
    mMinorScale = (float)(mMinorInterval / range);

    // Calculate the value and offset of the first major marker
    mFirstMarkerValue = Math.floor(mCurrentMinValue / mMajorInterval) * mMajorInterval;
    // Percentage offset of first major marker.
    float firstMarkerOffset = (float)(mMinorScale * (mFirstMarkerValue - mCurrentMinValue) / mMinorInterval);

    // Calculate marker positions
    int numMarkers = (int)Math.floor((mCurrentMaxValue - mFirstMarkerValue) / mMinorInterval) + 1;
    int numMinorPerMajor = (int)(mMajorInterval / mMinorInterval);
    for (int i = 0; i < numMarkers; i++) {
      float markerOffset = firstMarkerOffset + i * mMinorScale;
      if (i % numMinorPerMajor == 0) {    // Major Tick.
        mMajorMarkerPositions.add(markerOffset);
      }
      else {
        mMinorMarkerPositions.add(markerOffset);
      }
    }
  }

  @Override
  protected void draw(Graphics2D g) {
    // Calculate drawing parameters.
    Point startPoint = new Point();
    Point endPoint = new Point();
    Point labelPoint = new Point();
    Dimension dimension = getSize();
    switch (mOrientation) {
      case LEFT:
        startPoint.x = endPoint.x = dimension.width - 1;
        startPoint.y = dimension.height - mStartMargin - 1;
        endPoint.y = mEndMargin;
        mAxisLength = startPoint.y - endPoint.y;

        //Affix label to top left.
        labelPoint.x = 0;
        labelPoint.y = endPoint.y;
        break;
      case BOTTOM:
        startPoint.x = mStartMargin;
        endPoint.x = dimension.width - mEndMargin - 1;
        startPoint.y = endPoint.y = 0;
        mAxisLength = endPoint.x - startPoint.x;

        //Affix label to bottom left
        labelPoint.x = startPoint.x;
        labelPoint.y = getHeight() - (mMetrics.getMaxAscent() + mMetrics.getMaxDescent());
        break;
      case RIGHT:
        startPoint.x = endPoint.x = 0;
        startPoint.y = dimension.height - mStartMargin - 1;
        endPoint.y = mEndMargin;
        mAxisLength = startPoint.y - endPoint.y;

        //Affix label to top right
        labelPoint.x = getWidth() - mMetrics.getMaxAdvance();
        labelPoint.y = endPoint.y;
        break;
      case TOP:
        startPoint.x = mStartMargin;
        endPoint.x = dimension.width - mEndMargin - 1;
        startPoint.y = endPoint.y = dimension.height - 1;
        mAxisLength = endPoint.x - startPoint.x;

        //Affix label to top left
        labelPoint.x = 0;
        labelPoint.y = 0;
        break;
    }

    if (mAxisLength > 0) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      // Draw axis.
      g.setColor(AdtUiUtils.DEFAULT_BORDER_COLOR);
      g.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y);

      // TODO account for pixel spacing so we can skip ticks if the length is too narrow.
      drawMarkers(g, startPoint);

      if (mLabel.isVisible()) {
        AffineTransform initialTransform = g.getTransform();
        g.translate(labelPoint.x, labelPoint.y);
        mLabel.paint(g);
        g.setTransform(initialTransform);
      }
    }
  }

  private void drawMarkers(Graphics2D g2d, Point origin) {
    g2d.setFont(AdtUiUtils.DEFAULT_FONT);

    if (mShowMinMax) {
      drawMarkerLabel(g2d, LABEL_BOUNDS_OFFSET, origin, mCurrentMinValue, true);
      drawMarkerLabel(g2d, mAxisLength - LABEL_BOUNDS_OFFSET, origin, mCurrentMaxValue, true);
    }

    // TODO fade in/out markers.
    Line2D.Float line = new Line2D.Float();

    // Draw minor ticks.
    for (int i = 0; i < mMinorMarkerPositions.size(); i++) {
      if (mMinorMarkerPositions.get(i) >= 0) {
        float scaledPosition = mMinorMarkerPositions.get(i) * mAxisLength;
        drawMarkerLine(g2d, line, scaledPosition, origin, false);
      }
    }

    // Draw major ticks.
    for (int i = 0; i < mMajorMarkerPositions.size(); i++) {
      if (mMajorMarkerPositions.get(i) >= 0) {
        float scaledPosition = mMajorMarkerPositions.get(i) * mAxisLength;
        drawMarkerLine(g2d, line, scaledPosition, origin, true);

        double markerValue = mFirstMarkerValue + i * mMajorInterval;
        drawMarkerLabel(g2d, scaledPosition, origin, markerValue, !mShowMinMax);
      }
    }
  }

  private void drawMarkerLine(Graphics2D g2d, Line2D.Float line, float markerOffset,
                              Point origin, boolean isMajor) {
    float markerStartX = 0, markerStartY = 0, markerEndX = 0, markerEndY = 0;
    int markerLength = isMajor ? MAJOR_MARKER_LENGTH : MINOR_MARKER_LENGTH;
    switch (mOrientation) {
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
    g2d.setColor(AdtUiUtils.DEFAULT_BORDER_COLOR);
    g2d.draw(line);
  }

  private void drawMarkerLabel(Graphics2D g2d, float markerOffset, Point origin,
                               double markerValue, boolean isMinMax) {
    String formattedValue = mFormatter.getFormattedString(mGlobalRange.getLength(), markerValue);
    int stringAscent = mMetrics.getAscent();
    int stringLength = mMetrics.stringWidth(formattedValue);

    float labelX, labelY;
    float reserved; // reserved space for min/max labels.
    switch (mOrientation) {
      case LEFT:
        labelX = origin.x - MAJOR_MARKER_LENGTH - MARKER_LABEL_MARGIN - stringLength;
        labelY = origin.y - markerOffset + stringAscent * 0.5f;
        reserved = stringAscent;
        break;
      case RIGHT:
        labelX = MAJOR_MARKER_LENGTH + MARKER_LABEL_MARGIN;
        labelY = origin.y - markerOffset + stringAscent * 0.5f;
        reserved = stringAscent;
        break;
      case TOP:
        labelX = origin.x + markerOffset + MARKER_LABEL_MARGIN;
        labelY = origin.y - MINOR_MARKER_LENGTH;
        reserved = stringLength;
        break;
      case BOTTOM:
        labelX = origin.x + markerOffset + MARKER_LABEL_MARGIN;
        labelY = MINOR_MARKER_LENGTH + stringAscent;
        reserved = stringLength;
        break;
      default:
        throw new AssertionError("Unexpected orientation: " + mOrientation);
    }

    if (isMinMax || (markerOffset - reserved > 0 && markerOffset + reserved < mAxisLength)) {
      g2d.setColor(AdtUiUtils.DEFAULT_FONT_COLOR);
      g2d.drawString(formattedValue, labelX, labelY);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    int width = MAJOR_MARKER_LENGTH + MARKER_LABEL_MARGIN + MAXIMUM_LABEL_WIDTH;
    int height = mStartMargin + mEndMargin;
    return (mOrientation == LEFT || mOrientation == RIGHT) ? new Dimension(width, height) : new Dimension(height, width);
  }
}
