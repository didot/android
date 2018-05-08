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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SelectionModel;
import com.android.tools.adtui.ui.AdtUiCursors;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.function.Consumer;

/**
 * A component for performing/rendering selection.
 */
public final class SelectionComponent extends AnimatedComponent {

  // TODO: support using different colors for selection, border and handle
  private static final Color DEFAULT_SELECTION_COLOR = new JBColor(new Color(0x330478DA, true), new Color(0x4C2395F5, true));

  private static final Color DEFAULT_SELECTION_BORDER = new JBColor(new Color(0x4C0478DA, true), new Color(0x4C0478DA, true));

  private static final Color DRAG_BAR_COLOR = new JBColor(new Color(0x1a000000, true), new Color(0x33000000, true));

  private static final Color DEFAULT_HANDLE = new JBColor(0x696868, 0xD6D6D6);

  private static final int HANDLE_HEIGHT = 40;

  private static final int DRAG_BAR_HEIGHT = 26;

  static final int HANDLE_WIDTH = 5;

  private static final double SELECTION_MOVE_PERCENT = 0.01;

  private int myMousePressed;

  private int myMouseMovedX;

  public enum Mode {
    /** The default mode: nothing is happening */
    NONE,
    /** User is currently creating / sizing a new selection. */
    CREATE,
    /** User is over the drag bar, or moving a selection. */
    MOVE,
    /** User is adjusting the min. */
    ADJUST_MIN,
    /** User is adjusting the max. */
    ADJUST_MAX
  }

  private enum ShiftDirection {
    /** User is moving the selection to the left */
    LEFT,
    /** User is moving the selection to the right */
    RIGHT,
  }

  private Mode myMode;

  /**
   * The range being selected.
   */
  @NotNull
  private final SelectionModel myModel;

  @NotNull
  private final Range myViewRange;

  public SelectionComponent(@NotNull SelectionModel model, @NotNull Range viewRange) {
    myModel = model;
    myViewRange = viewRange;
    myMode = Mode.NONE;
    setFocusable(true);
    initListeners();

    myModel.addDependency(myAspectObserver).onChange(SelectionModel.Aspect.SELECTION, this::opaqueRepaint);
    myViewRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::opaqueRepaint);
  }

  private void initListeners() {
    this.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
          requestFocusInWindow();
          myMode = getModeAtCurrentPosition(e.getX(), e.getY());
          if (myMode == Mode.CREATE) {
            // We clear the selection model explicitly, to make sure the "set" call below fires a
            // selection creation event (instead of the model thinking we're modifying an existing
            // selection)
            myModel.clear();

            double value = xToRange(e.getX());
            myModel.beginUpdate();
            myModel.set(value, value);
          }
          myMousePressed = e.getX();
          updateCursor(myMode, myMousePressed);
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
          if (myMode == Mode.CREATE) {
            myModel.endUpdate();
          }
          myMode = getModeAtCurrentPosition(e.getX(), e.getY());
          myMousePressed = -1;
          updateCursor(myMode, myMousePressed);
          opaqueRepaint();
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setCursor(Cursor.getDefaultCursor());
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
        myMouseMovedX = e.getX();
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
            myModel.set(pressed < current ? pressed : current,
                        pressed < current ? current : pressed);
            break;
          case NONE:
            break;
        }
        updateCursor(myMode, e.getX());
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        myMode = getModeAtCurrentPosition(e.getX(), e.getY());
        updateCursor(myMode, e.getX());
        myMouseMovedX = e.getX();
      }
    });
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
          case KeyEvent.VK_ESCAPE:
            if (!myModel.getSelectionRange().isEmpty()) {
              myModel.getSelectionRange().clear();
              e.consume();
            }
            break;
          case KeyEvent.VK_LEFT:
            // If we are shifting left with the alt key down, we are shrinking our selection from the right.
            // If we are shifting left with the shift key down, we are growing our selection to the left.
            shiftModel(ShiftDirection.LEFT, e.isAltDown(), e.isShiftDown());
            break;
          case KeyEvent.VK_RIGHT:
            // If we are shifting right with the shift key down, we are growing our selection to the right.
            // If we are shifting right with the alt key down, we are shrinking our selection from the left
            shiftModel(ShiftDirection.RIGHT, e.isShiftDown(), e.isAltDown());
            break;
        }
        myModel.endUpdate();
      }
    });
  }

  private void shiftModel(ShiftDirection direction, boolean zeroMin, boolean zeroMax) {
    double min = myModel.getSelectionRange().getMin();
    double max = myModel.getSelectionRange().getMax();
    double rangeDelta = myViewRange.getLength() * SELECTION_MOVE_PERCENT;
    rangeDelta = (direction == ShiftDirection.LEFT) ? rangeDelta * -1 : rangeDelta;
    double minDelta = zeroMin ? 0 : rangeDelta;
    double maxDelta = zeroMax ? 0 : rangeDelta;
    // If we don't have a selection attempt to put the selection in the center off the screen.
    if (max < min) {
      max = min = myViewRange.getLength() / 2.0 + myViewRange.getMin();
    }

    myModel.beginUpdate();
    myModel.set(min + minDelta, max + maxDelta);
    myModel.endUpdate();
  }

  private double xToRange(int x) {
    Range range = myViewRange;
    return x / getSize().getWidth() * range.getLength() + range.getMin();
  }

  private float rangeToX(double value, Dimension dim) {
    Range range = myViewRange;
    // Clamp the range to the edge of the screen. This prevents fill artifacts when zoomed in, and improves performance.
    // If we do not clamp the selection to the screen then during painting java attempts to fill a rectangle several
    // thousand pixels off screen in both directions. This results in lots of computation that isn't required as well as,
    // lots of artifacts in the selection itself.
    return  Math.min(Math.max((float)(dim.getWidth() * ((value - range.getMin()) / (range.getMax() - range.getMin()))), 0), dim.width);
  }

  private Mode getModeAtCurrentPosition(int x, int y) {
    Dimension size = getSize();
    double startXPos = rangeToX(myModel.getSelectionRange().getMin(), size);
    double endXPos = rangeToX(myModel.getSelectionRange().getMax(), size);
    if (startXPos - HANDLE_WIDTH < x && x < startXPos) {
      return Mode.ADJUST_MIN;
    }
    else if (endXPos < x && x < endXPos + HANDLE_WIDTH) {
      return Mode.ADJUST_MAX;
    }
    else if (startXPos <= x && x <= endXPos && y <= DRAG_BAR_HEIGHT) {
      return Mode.MOVE;
    }
    return Mode.CREATE;
  }

  private void updateCursor(Mode newMode, int newX) {
    switch (newMode) {
      case ADJUST_MIN:
        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
        break;
      case ADJUST_MAX:
        setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
        break;
      case MOVE:
        setCursor(myMousePressed == -1 ? AdtUiCursors.GRAB : AdtUiCursors.GRABBING);
        break;
      case CREATE:
        if (myMode == Mode.CREATE) {
          // If already in CREATE mode, update cursor in case selection changed direction, e.g.
          // dragging max handle below min handle.
          if (myMousePressed < newX) {
            setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
          }
          else {
            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
          }
        }
        else {
          setCursor(Cursor.getDefaultCursor());
        }
        break;
      case NONE:
        // NO-OP: Keep current mouse cursor.
        break;
    }
  }

  /**
   * This listens on the selection range finishes update changes. Because the selection model fires the selection aspect
   * when the mouse is not released, cannot listen on the selection model side.
   */
  public void addSelectionUpdatedListener(final Consumer<Range> listener) {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
          listener.accept(myModel.getSelectionRange());
        }
      }
    });
  }

  @NotNull
  public Mode getMode() {
    return myMode;
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

    if (myMouseMovedX > startXPos && myMouseMovedX < endXPos) {
      g.setColor(DRAG_BAR_COLOR);
      g.fill(new Rectangle2D.Float(startXPos, 0, endXPos - startXPos, DRAG_BAR_HEIGHT));
    }

    // Draw vertical lines, one for each endsValue.
    g.setColor(DEFAULT_SELECTION_BORDER);
    Path2D.Float path = new Path2D.Float();
    path.moveTo(startXPos, 0);
    path.lineTo(startXPos, dim.height);
    path.moveTo(endXPos - 1, dim.height);
    path.lineTo(endXPos - 1, 0);
    g.draw(path);

    drawHandle(g, startXPos, dim.height, 1.0f);
    drawHandle(g, endXPos, dim.height, -1.0f);
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