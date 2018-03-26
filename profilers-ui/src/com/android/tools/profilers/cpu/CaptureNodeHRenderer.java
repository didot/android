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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.chart.hchart.HRenderer;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.profilers.cpu.nodemodel.*;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.function.Predicate;

/**
 * Specifies render characteristics (i.e. text and color) of {@link com.android.tools.adtui.chart.hchart.HTreeChart} nodes that represent
 * instances of {@link CaptureNode}.
 */
public class CaptureNodeHRenderer implements HRenderer<CaptureNode> {

  private static final int LEFT_MARGIN_PX = 3;

  @NotNull
  private CaptureModel.Details.Type myType;

  @NotNull
  private TextFitsPredicate myTextFitsPredicate;

  @VisibleForTesting
  CaptureNodeHRenderer(@NotNull CaptureModel.Details.Type type, @NotNull TextFitsPredicate textFitPredicate) {
    if (type != CaptureModel.Details.Type.CALL_CHART && type != CaptureModel.Details.Type.FLAME_CHART) {
      throw new IllegalStateException("Chart type not supported and can't be rendered.");
    }
    myType = type;
    myTextFitsPredicate = textFitPredicate;
  }

  @VisibleForTesting
  public CaptureNodeHRenderer() {
    this(CaptureModel.Details.Type.CALL_CHART);
  }

  public CaptureNodeHRenderer(@NotNull CaptureModel.Details.Type type) {
    this(type, (text, metrics, width) -> metrics.stringWidth(text) <= width);
  }

  private Color getFillColor(CaptureNode node) {
    // TODO (b/74349846): Change this function to use a binder base on CaptureNode.
    CaptureNodeModel nodeModel = node.getData();
    if (nodeModel instanceof JavaMethodModel) {
      return JavaMethodHChartColors.getFillColor(nodeModel, myType, node.isUnmatched());
    }
    else if (nodeModel instanceof NativeNodeModel) {
      return NativeModelHChartColors.getFillColor(nodeModel, myType, node.isUnmatched());
    }
    // AtraceNodeModel is a SingleNameModel as such this check needs to happen before SingleNameModel check.
    else if (nodeModel instanceof AtraceNodeModel) {
      return AtraceNodeModelHChartColors.getFillColor(nodeModel, myType, node.isUnmatched());
    }
    else if (nodeModel instanceof SingleNameModel) {
      return SingleNameModelHChartColors.getFillColor(nodeModel, myType, node.isUnmatched());
    }
    throw new IllegalStateException("Node type not supported.");
  }

  private Color getIdleCpuColor(CaptureNode node) {
    // TODO (b/74349846): Change this function to use a binder base on CaptureNode.

    // The only nodes that actually show idle time are the atrace nodes. As such they are the only ones,
    // that return a custom color for the idle cpu time.
    CaptureNodeModel nodeModel = node.getData();
    if (nodeModel instanceof AtraceNodeModel) {
      return AtraceNodeModelHChartColors.getIdleCpuColor(nodeModel, myType, node.isUnmatched());
    }
    return getFillColor(node);
  }

  private Color getBorderColor(CaptureNode node) {
    // TODO (b/74349846): Change this function to use a binder base on CaptureNode.
    CaptureNodeModel nodeModel = node.getData();
    if (nodeModel instanceof JavaMethodModel) {
      return JavaMethodHChartColors.getBorderColor(nodeModel, myType, node.isUnmatched());
    }
    else if (nodeModel instanceof NativeNodeModel) {
      return NativeModelHChartColors.getBorderColor(nodeModel, myType, node.isUnmatched());
    }
    // AtraceNodeModel is a SingleNameModel and uses the same colors for the border.
    else if (nodeModel instanceof SingleNameModel) {
      return SingleNameModelHChartColors.getBorderColor(nodeModel, myType, node.isUnmatched());
    }
    throw new IllegalStateException("Node type not supported.");
  }

  /**
   * @return the same color but with 20% opacity that is used to represent an unmatching node.
   */
  @NotNull
  static Color toUnmatchColor(@NotNull Color color) {
    // TODO: maybe cache for performance?
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(255 * 0.2));
  }

  @Override
  public void render(@NotNull Graphics2D g, @NotNull CaptureNode node, @NotNull Rectangle2D drawingArea, boolean isFocused) {
    // Draw rectangle background
    CaptureNode captureNode = node;
    CaptureNodeModel nodeModel = node.getData();
    Color nodeColor = getFillColor(captureNode);
    Color idleColor = getIdleCpuColor(captureNode);
    if (isFocused) {
      // All colors we use in call and flame charts are pretty bright, so darkening them works as an effective highlight
      nodeColor = ColorUtil.darker(nodeColor, 2);
      idleColor = ColorUtil.darker(idleColor, 2);
    }
    g.setPaint(nodeColor);
    g.fill(drawingArea);

    double clockLength = node.getEnd() - node.getStart();
    double threadLength = node.getEndThread() - node.getStartThread();
    // If we have a difference in our clock length and thread time that means we have idle time.
    // For now we only do this for node that have an atrace capture node model.
    if (nodeModel instanceof AtraceNodeModel && threadLength > 0 && clockLength - threadLength > 0) {
      // Idle time width is 1 minus the  ratio of clock time, and thread time.
      double ratio = 1 - (threadLength / clockLength);
      double cpuIdleTimeRatio = ratio *  drawingArea.getWidth();
      g.setPaint(idleColor);
      // The Idle time is drawn at the end of our total time, as such we start at our X + our idle time.
      // The width is then defined as the total width minus our idle time ratio.
      g.fill(new Rectangle2D.Double(drawingArea.getX() + cpuIdleTimeRatio,
                                    drawingArea.getY(),
                                    drawingArea.getWidth() - cpuIdleTimeRatio,
                                    drawingArea.getHeight()));
    }

    // Draw rectangle outline.
    g.setPaint(getBorderColor(captureNode));
    g.draw(drawingArea);

    // Draw text
    Font font = g.getFont();
    FontMetrics fontMetrics = g.getFontMetrics(font);
    if (captureNode.getFilterType() == CaptureNode.FilterType.MATCH) {
      g.setPaint(Color.BLACK);
    }
    else if (captureNode.getFilterType() == CaptureNode.FilterType.UNMATCH) {
      g.setPaint(toUnmatchColor(Color.BLACK));
    }
    else {
      g.setPaint(Color.BLACK);
      g.setFont(font.deriveFont(Font.BOLD));
    }
    Float availableWidth = (float)drawingArea.getWidth() - LEFT_MARGIN_PX;
    String text = generateFittingText(node.getData(), s -> myTextFitsPredicate.test(s, fontMetrics, availableWidth));
    float textPositionX = LEFT_MARGIN_PX + (float)drawingArea.getX();
    float textPositionY = (float)(drawingArea.getY() + fontMetrics.getAscent());
    g.drawString(text, textPositionX, textPositionY);

    g.setFont(font);
  }

  /**
   * Find the best text for the given rectangle constraints.
   */
  private static String generateFittingText(CaptureNodeModel model, Predicate<String> textFitsPredicate) {
    String classOrNamespace = "";
    String separator = "";
    if (model instanceof CppFunctionModel) {
      classOrNamespace = ((CppFunctionModel)model).getClassOrNamespace();
      separator = "::";
    }
    else if (model instanceof JavaMethodModel) {
      classOrNamespace = ((JavaMethodModel)model).getClassName();
      separator = ".";
    }

    if (!separator.isEmpty() && !classOrNamespace.isEmpty()) {
      // String#split receives a regex. As "." is a special regex character, we need to handle the case.
      String[] classElements = classOrNamespace.split(separator.equals(".") ? "\\." : separator);

      // Try abbreviating a few first packages, e.g for "java.lang.String.toString" try in this order ->
      // "java.lang.String.toString", "j.lang.String.toString", "j.l.String.toString", "j.l.S.toString"
      for (int abbreviationCount = 0; abbreviationCount <= classElements.length; ++abbreviationCount) {
        // Try to reduce by using abbreviations for first |abbreviationCount| classElements.
        StringBuilder textBuilder = new StringBuilder();
        for (int i = 0; i < classElements.length; ++i) {
          textBuilder.append(i < abbreviationCount ? classElements[i].charAt(0) : classElements[i]).append(separator);
        }
        textBuilder.append(model.getName());
        String text = textBuilder.toString();
        if (textFitsPredicate.test(text)) {
          return text;
        }
      }
    }

    // Try: toString or t...
    return AdtUiUtils.shrinkToFit(model.getName(), textFitsPredicate);
  }

  public interface TextFitsPredicate {
    boolean test(String text, FontMetrics metrics, float width);
  }
}
