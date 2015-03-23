/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.tools.idea.editors.theme.EditedStyleItem;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.swing.util.GraphicsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * This class is used to draw drawable resources in theme editor cell.
 * Used in {@link DrawableEditor} and {@link DrawableRenderer}.
 */
public class DrawableComponent extends JButton {
  private static final Logger LOG = Logger.getInstance(DrawableComponent.class.getName());

  private static final int PADDING = 2;

  // Size of a square in checkered board background
  private static final int CELL_SIZE = 10;

  // Distance between border of a cell and image
  private static final int IMAGE_PADDING = 5;

  // Amount of space between border of box with text and text itself
  private static final int TEXT_PADDING = 1;

  // Amount of space between border of a cell and rectangle with solid background for showing text
  private static final int TEXT_MARGIN = 3;

  private final List<BufferedImage> myImages = new ArrayList<BufferedImage>();
  private String myName;
  private String myValue;
  private boolean myIsPublic;

  public static Border getBorder(final Color borderColor) {
    return BorderFactory.createMatteBorder(PADDING, PADDING, PADDING, PADDING, borderColor);
  }

  /**
   * Populate text fields shown in a cell from EditedStyleItem value
   */
  public void configure(final @NotNull EditedStyleItem item, final @Nullable RenderTask renderTask) {
    myName = item.getName();
    myValue = item.getValue();
    myIsPublic = item.isPublicAttribute();

    myImages.clear();
    if (renderTask != null) {
      myImages.addAll(renderTask.renderDrawableAllStates(item.getItemResourceValue()));
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    com.intellij.util.ui.GraphicsUtil.setupAntialiasing(g);

    final int width = getWidth();
    final int height = getHeight();
    GraphicsUtil.paintCheckeredBackground(g, new Rectangle(0, 0, width, height), CELL_SIZE);

    int offset = IMAGE_PADDING;
    for (final BufferedImage image : myImages) {
      int imageHeight = image.getHeight();
      int imageWidth = image.getWidth();

      int maxHeight = height - 2 * IMAGE_PADDING;

      if (imageHeight > maxHeight) {
        imageWidth = (int) Math.floor(imageWidth * maxHeight / ((double) imageHeight));
        imageHeight = maxHeight;
      }

      final int startY = (height - imageHeight - 2 * PADDING) / 2 + PADDING;
      final int startX = (width - offset - imageWidth);

      g.drawImage(image, startX, startY, imageWidth, imageHeight, null);
      offset += imageWidth + IMAGE_PADDING;
    }

    if (myName != null && myValue != null) {
      final FontMetrics fontMetrics = g.getFontMetrics();
      final int stringHeight = fontMetrics.getHeight();

      final int nameWidth = fontMetrics.stringWidth(myName);
      final int valueWidth = fontMetrics.stringWidth(myValue);

      g.setColor(Gray._50);
      final int rectOffset = PADDING + TEXT_MARGIN;
      g.fillRect(rectOffset, rectOffset, nameWidth + 2 * TEXT_PADDING, stringHeight);
      g.fillRect(rectOffset, height - rectOffset - stringHeight, valueWidth + 2 * TEXT_PADDING, stringHeight);

      g.setColor(JBColor.WHITE);
      g.drawString(myName, rectOffset + TEXT_PADDING, rectOffset + stringHeight - TEXT_PADDING);
      g.drawString(myValue, rectOffset + TEXT_PADDING, height - rectOffset - TEXT_PADDING);
    }

    // If attribute is private, draw a cross on it
    if (!myIsPublic) {
      g.drawLine(PADDING, PADDING, width - PADDING, height - PADDING);
      g.drawLine(width - PADDING, PADDING, PADDING, height - PADDING);
    }
  }
}
