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

import com.android.tools.adtui.model.*;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * A component for performing/rendering selection.
 */
public final class SelectionComponent extends AnimatedComponent {

  public static final Color DEFAULT_SELECTION_COLOR = new JBColor(new Color(0x80CDE4F8, true), new Color(0x80CDE4F8, true));

  public static final Color DEFAULT_SELECTION_BORDER = new JBColor(0x91C4EF, 0x91C4EF);

  private static final Color DEFAULT_HANDLE = new JBColor(0x696868, 0x696868);

  public static final int HANDLE_HEIGHT = 40;

  public static final int HANDLE_WIDTH = 5;

  private int myMousePressed;

  private enum Mode {
    /** The default mode: nothing is happening */
    NONE,
    /** User is currently creating / sizing a new selection. */
    CREATE,
    /** User is moving a selection. */
    MOVE,
    /** User is adjusting the min. */
    ADJUST_MIN,
    /** User is adjusting the max. */
    ADJUST_MAX
  }

  private Mode myMode;

  /**
   * The range being selected.
   */
  @NotNull
  private final SelectionModel myModel;

  public SelectionComponent(@NotNull SelectionModel model) {
    myModel = model;
    myMode = Mode.NONE;
    setFocusable(true);
    initListeners();

    myModel.addDependency(myAspectObserver).onChange(SelectionModel.Aspect.SELECTION, this::modelChanged);
  }

  private void modelChanged() {
    opaqueRepaint();
  }

  private void initListeners() {
    this.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        Dimension size = getSize();
        int x = e.getX();

        double startXPos = rangeToX(myModel.getSelectionRange().getMin(), size);
        double endXPos = rangeToX(myModel.getSelectionRange().getMax(), size);
        if (startXPos - HANDLE_WIDTH < x && x < startXPos) {
          myMode = Mode.ADJUST_MIN;
        }
        else if (endXPos < x && x < endXPos + HANDLE_WIDTH) {
          myMode = Mode.ADJUST_MAX;
        }
        else if (startXPos <= x && x <= endXPos) {
          myMode = Mode.MOVE;
        }
        else {
          double value = xToRange(x);
          myModel.set(value, value);
          myMode = Mode.CREATE;
        }
        myMousePressed = e.getX();
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (myMode == Mode.CREATE) {
          myModel.fireSelectionEvent();
        }
        myMode = Mode.NONE;
        opaqueRepaint();
      }
    });
    this.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged(MouseEvent e) {
        double pressed = xToRange(myMousePressed);
        double current = xToRange(e.getX());
        double rangeDelta = current - pressed;
        double min = myModel.getSelectionRange().getMin();
        double max = myModel.getSelectionRange().getMax();
        switch (myMode) {
          case ADJUST_MIN:
            if (min + rangeDelta > max) {
              myModel.set(max, min + rangeDelta);
              myMode = Mode.ADJUST_MAX;
            }
            else {
              myModel.set(min + rangeDelta, max);
            }
            myMousePressed = e.getX();
            break;
          case ADJUST_MAX:
            if (max + rangeDelta < min) {
              myModel.set(max + rangeDelta, min);
              myMode = Mode.ADJUST_MIN;
            }
            else {
              myModel.set(min, max + rangeDelta);
            }
            myMousePressed = e.getX();
            break;
          case MOVE:
            myModel.set(min + rangeDelta, max + rangeDelta);
            myMousePressed = e.getX();
            break;
          case CREATE:
            myModel.set(pressed < current ? pressed : current, pressed < current ? current : pressed);
            break;
          case NONE:
            break;
        }
      }
    });
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          if (!myModel.getSelectionRange().isEmpty()) {
            myModel.getSelectionRange().clear();
            e.consume();
            myModel.fireSelectionEvent();
          }
        }
      }
    });
  }

  private double xToRange(int x) {
    Range range = myModel.getRange();
    return x / getSize().getWidth() * range.getLength() + range.getMin();
  }

  private float rangeToX(double value, Dimension dim) {
    Range range = myModel.getRange();
    return  (float)(dim.getWidth() * ((value - range.getMin()) / (range.getMax() - range.getMin())));
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    if (myModel.getSelectionRange().isEmpty()) {
      return;
    }
    float startXPos = rangeToX(myModel.getSelectionRange().getMin(), dim);
    float endXPos = rangeToX(myModel.getSelectionRange().getMax(), dim);

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(DEFAULT_SELECTION_COLOR);
    Rectangle2D.Float rect = new Rectangle2D.Float(startXPos, 0, endXPos - startXPos, dim.height);
    g.fill(rect);

    // Draw vertical lines, one for each endsValue.
    g.setColor(DEFAULT_SELECTION_BORDER);
    Path2D.Float path = new Path2D.Float();
    path.moveTo(startXPos, 0);
    path.lineTo(startXPos, dim.height);
    path.moveTo(endXPos, dim.height);
    path.lineTo(endXPos, 0);
    g.draw(path);

    if (myMode != Mode.CREATE) {
      drawHandle(g, startXPos, dim.height, 1.0f);
      drawHandle(g, endXPos, dim.height, -1.0f);
    }
  }

  private void drawHandle(Graphics2D g, float x, float height, float direction) {
    float up = (height - HANDLE_HEIGHT) * 0.5f;
    float down = (height + HANDLE_HEIGHT) * 0.5f;
    float width = HANDLE_WIDTH * direction;

    g.setColor(DEFAULT_HANDLE);
    Path2D.Float path = new Path2D.Float();
    path.moveTo(x, up);
    path.lineTo(x, down);
    path.quadTo(x - width, down, x - width, down - HANDLE_WIDTH);
    path.lineTo(x - width, up + HANDLE_WIDTH);
    path.quadTo(x - width, up, x, up);
    g.fill(path);
  }
}