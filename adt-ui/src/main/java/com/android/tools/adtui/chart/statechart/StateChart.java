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

package com.android.tools.adtui.chart.statechart;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.MouseAdapterComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.Stopwatch;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A chart component that renders series of state change events as rectangles.
 */
public final class StateChart<T> extends MouseAdapterComponent {

  public enum RenderMode {
    BAR,  // Each state is rendered as a filled rectangle until the next state changed.
    TEXT  // Each state is marked with a vertical line and and corresponding state text/label at the beginning.
  }

  private static final int TEXT_PADDING = 3;

  private StateChartModel<T> myModel;

  /**
   * An object that maps between a type T, and a color to be used in the StateChart, all values of T should return a valid color.
   */
  @NotNull
  private final StateChartColorProvider<T> myColorProvider;

  private float myHeightGap;

  @NotNull
  private RenderMode myRenderMode;

  @NotNull
  private final StateChartConfig<T> myConfig;

  private boolean myNeedsTransform;

  @NotNull
  private final StateChartTextConverter<T> myTextConverter;

  private final List<Rectangle2D.Float> myRectangles = new ArrayList<>();
  private final List<T> myRectangleValues = new ArrayList<>();

  private float myRectHeight = 1.0f;

  /**
   * @param colors map of a state to corresponding color
   */
  @VisibleForTesting
  public StateChart(@NotNull StateChartModel<T> model, @NotNull Map<T, Color> colors) {
    this(model, new StateChartColorProvider<T>() {
      @Override
      @NotNull
      public Color getColor(boolean isMouseOver, @NotNull T value) {
        Color color = colors.get(value);
        return isMouseOver ? ColorUtil.brighter(color, 2) : color;
      }
    });
  }

  public StateChart(@NotNull StateChartModel<T> model, @NotNull StateChartColorProvider<T> colorMapping) {
    this(model, new StateChartConfig<>(new DefaultStateChartReducer<>()), colorMapping, (val) -> val.toString());
  }

  public StateChart(@NotNull StateChartModel<T> model,
                    @NotNull StateChartColorProvider<T> colorMapping,
                    StateChartTextConverter<T> textConverter) {
    this(model, new StateChartConfig<>(new DefaultStateChartReducer<>()), colorMapping, textConverter);
  }

  public StateChart(@NotNull StateChartModel<T> model,
                    @NotNull StateChartConfig<T> config,
                    @NotNull StateChartColorProvider<T> colorMapping) {
    this(model, config, colorMapping, (val) -> val.toString());
  }

  @VisibleForTesting
  public StateChart(@NotNull StateChartModel<T> model,
                    @NotNull StateChartConfig<T> config,
                    @NotNull StateChartColorProvider<T> colorMapping,
                    @NotNull StateChartTextConverter<T> textConverter) {
    myColorProvider = colorMapping;
    myRenderMode = RenderMode.BAR;
    myConfig = config;
    myNeedsTransform = true;
    myTextConverter = textConverter;
    setFont(AdtUiUtils.DEFAULT_FONT);
    setModel(model);
    setHeightGap(myConfig.getHeightGap());
  }

  public void setModel(@NotNull StateChartModel<T> model) {
    if (myModel != null) {
      myModel.removeDependencies(myAspectObserver);
    }
    myModel = model;
    myModel.addDependency(myAspectObserver).onChange(StateChartModel.Aspect.MODEL_CHANGED, this::modelChanged);
    modelChanged();
  }

  private void modelChanged() {
    myNeedsTransform = true;
    opaqueRepaint();
  }

  public void setRenderMode(@NotNull RenderMode mode) {
    myRenderMode = mode;
  }

  /**
   * Sets the gap between multiple data series.
   *
   * @param gap The gap value as a percentage {0...1} of the height given to each data series
   */
  public void setHeightGap(float gap) {
    myHeightGap = gap;
  }

  private void clearRectangles() {
    myRectangles.clear();
    myRectangleValues.clear();
  }

  /**
   * Creates a rectangle with the supplied dimensions. This function will normalize the x and width values.
   *
   * @param value     value used to associate with the created rectangle..
   * @param previousX value used to determine the x position and width of the rectangle. This value should be relative to the currentX param.
   * @param currentX  value used to determine the width of the rectangle. This value should be relative to the previousX param.
   * @param minX      minimum value of the range total range used to normalize the x position and width of the rectangle.
   * @param invRange  inverse of total range used to normalize the x position and width of the rectangle (~7% gain in performance).
   * @param rectY     rectangle height offset from max growth of rectangle. This value is expressed as a percentage from 0-1
   * @param height    height of rectangle
   */
  private void addRectangleDelta(@NotNull T value,
                                 double previousX,
                                 double currentX,
                                 double minX,
                                 double invRange,
                                 float rectY,
                                 float height) {
    // Because we start our activity line from the bottom and grow up we offset the height from the bottom of the component
    // instead of the top by subtracting our height from 1.
    Rectangle2D.Float rect = new Rectangle2D.Float(
      (float)((previousX - minX) * invRange),
      rectY,
      (float)((currentX - previousX) * invRange),
      height);
    myRectangles.add(rect);
    myRectangleValues.add(value);
  }

  private void transform() {
    if (!myNeedsTransform) {
      return;
    }

    myNeedsTransform = false;

    List<RangedSeries<T>> series = myModel.getSeries();
    int seriesSize = series.size();
    if (seriesSize == 0) {
      return;
    }

    // TODO support interpolation.
    myRectHeight = 1.0f / seriesSize;
    float gap = myRectHeight * myHeightGap;
    float barHeight = (1.0f - gap) * myRectHeight;

    clearRectangles();

    for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
      RangedSeries<T> data = series.get(seriesIndex);

      final double min = data.getXRange().getMin();
      final double max = data.getXRange().getMax();
      final double invRange = 1.0 / (max - min);
      float startHeight = 1.0f - (myRectHeight * (seriesIndex + 1));

      List<SeriesData<T>> seriesDataList = data.getSeries();
      if (seriesDataList.isEmpty()) {
        continue;
      }

      // Construct rectangles.
      long previousX = seriesDataList.get(0).x;
      T previousValue = seriesDataList.get(0).value;
      for (int i = 1; i < seriesDataList.size(); i++) {
        SeriesData<T> seriesData = seriesDataList.get(i);
        long x = seriesData.x;
        T value = seriesData.value;

        if (value.equals(previousValue)) {
          // Ignore repeated values.
          continue;
        }

        assert previousValue != null;

        // Don't draw if this block doesn't intersect with [min..max]
        if (x >= min) {
          // Draw the previous block.
          addRectangleDelta(previousValue, Math.max(min, previousX), Math.min(max, x), min, invRange, startHeight + gap * 0.5f, barHeight);
        }

        // Start a new block.
        previousValue = value;
        previousX = x;

        if (previousX >= max) {
          // Drawn past max range, stop.
          break;
        }
      }
      // The last data point continues till max
      if (previousX < max && previousValue != null) {
        addRectangleDelta(previousValue, Math.max(min, previousX), max, min, invRange, startHeight + gap * 0.5f, barHeight);
      }
    }
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    Stopwatch stopwatch = new Stopwatch().start();

    transform();

    long transformTime = stopwatch.getElapsedSinceLastDeltaNs();

    g2d.setFont(getFont());
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    assert myRectangles.size() == myRectangleValues.size();
    List<Rectangle2D.Float> transformedShapes = new ArrayList<>(myRectangles.size());
    List<T> transformedValues = new ArrayList<>(myRectangleValues.size());

    float scaleX = (float)dim.width;
    float scaleY = (float)dim.height;
    for (int i = 0; i < myRectangles.size(); i++) {
      Rectangle2D.Float rectangle = myRectangles.get(i);
      // Manually scaling the rectangle results in ~6x performance improvement over calling
      // AffineTransform::createTransformedShape. The reason for this is the shape created is a Point2D.Double.
      // This shape has to support all types of points as such cannot be transformed as efficiently as a
      // rectangle. Furthermore, AffineTransform is uses doubles, which is about half as fast for LS
      // when compared to floats (doubles memory bandwidth).
      transformedShapes.add(new Rectangle2D.Float(rectangle.x * scaleX,
                                                  rectangle.y * scaleY,
                                                  rectangle.width * scaleX,
                                                  rectangle.height * scaleY));
      transformedValues.add(myRectangleValues.get(i));
    }

    long scalingTime = stopwatch.getElapsedSinceLastDeltaNs();

    myConfig.getReducer().reduce(transformedShapes, transformedValues);
    assert transformedShapes.size() == transformedValues.size();

    long reducerTime = stopwatch.getElapsedSinceLastDeltaNs();
    int hoverIndex = -1;
    //noinspection FloatingPointEquality
    if (getMouseX() != MouseAdapterComponent.INVALID_MOUSE_POSITION) {
      float mouseXFloor = (float)Math.floor(getMouseX());
      // Encode mouseXFloor into width component of the Rectangle2D.Float key to avoid recalculating on every invocation of the Comparable.
      hoverIndex = Collections.binarySearch(transformedShapes,
                                            new Rectangle2D.Float(mouseXFloor, 0, mouseXFloor + 1.0f, 0),
                                            (value, key) -> (value.x + value.width < key.x) ? -1 : (value.x > key.width ? 1 : 0));
    }

    for (int i = 0; i < transformedShapes.size(); i++) {
      T value = transformedValues.get(i);
      Rectangle2D.Float rect = transformedShapes.get(i);
      boolean isMouseOver = (i == hoverIndex);
      Color color = myColorProvider.getColor(isMouseOver, value);
      g2d.setColor(color);
      g2d.fill(rect);
      if (myRenderMode == RenderMode.TEXT) {
        String valueText = myTextConverter.convertToString(value);
        String text = AdtUiUtils.shrinkToFit(valueText, mDefaultFontMetrics, rect.width - TEXT_PADDING * 2);
        if (!text.isEmpty()) {
          g2d.setColor(myColorProvider.getFontColor(isMouseOver, value));
          float textOffset = rect.y + (rect.height - mDefaultFontMetrics.getHeight()) * 0.5f;
          textOffset += mDefaultFontMetrics.getAscent();
          g2d.drawString(text, rect.x + TEXT_PADDING, textOffset);
        }
      }
    }

    long drawTime = stopwatch.getElapsedSinceLastDeltaNs();

    addDebugInfo("XS ms: %.2fms, %.2fms", transformTime / 1000000.f, scalingTime / 1000000.f);
    addDebugInfo("RDT ms: %.2f, %.2f, %.2f", reducerTime / 1000000.f, drawTime / 1000000.f,
                 (scalingTime + reducerTime + drawTime) / 1000000.f);
    addDebugInfo("# of drawn rects: %d", transformedShapes.size());
  }
}

