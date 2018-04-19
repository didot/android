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
package com.android.tools.adtui;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;

/**
 * Class to build and manipulate rectangles to be drawn in the profiler's UI. This class is not responsible
 * for drawing the rectangles, but only creating, and determining if the mouse is over a rectangle.
 * All dimensions used in this class are represented as a percentage. It is for the child class to scale the
 * rectangles to the dimensions needed for the control. The most common use case is scaling the rectangles by
 * the components width and height in the draw function.
 *
 * @param T is the value type used to be associated with stored rectangles.
 */
public abstract class MouseAdapterComponent<T> extends AnimatedComponent implements MouseListener, MouseMotionListener {

  // Initializing the normalized mouse x position to be -1. This is the clear value because we are checking
  // valid rectangles from 0 to 1, as such using a negative number as a clear value will never result in a false overlap.
  private double myNormalizedMouseX = -1;

  /**
   * Default constructor for a MouseAdapterComponent, the constructor attaches mouse listeners.
   */
  public MouseAdapterComponent() {
    attach();
  }

  public void attach() {
    detach();
    addMouseListener(this);
    addMouseMotionListener(this);
  }

  public void detach() {
    removeMouseListener(this);
    removeMouseMotionListener(this);
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    setMousePointAndForwardEvent(e.getX(), e);
  }

  @Override
  public void mousePressed(MouseEvent e) {
    setMousePointAndForwardEvent(e.getX(), e);
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    setMousePointAndForwardEvent(e.getX(), e);
  }

  @Override
  public void mouseEntered(MouseEvent e) {
    setMousePointAndForwardEvent(e.getX(), e);
  }

  @Override
  public void mouseExited(MouseEvent e) {
    // Clear mouse position
    setMousePointAndForwardEvent(-1, e);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    setMousePointAndForwardEvent(e.getX(), e);
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    setMousePointAndForwardEvent(e.getX(), e);
  }

  /**
   * Normalizes the mouse position then passes MouseEvent to listeners up the callback stack.
   *
   * @param xPosition mouse x position to be normalized for determining mouse over.
   * @param e         mouse event to be passed on to parent items.
   */
  private void setMousePointAndForwardEvent(double x, MouseEvent e) {
    // TODO (b/74547254): Revisit mouse over animation to be based on range instead of mouse coordinates.
    myNormalizedMouseX = x;
    // Parent can be null in test.
    if (getParent() != null) {
      getParent().dispatchEvent(e);
    }
  }

  /**
   * Returns whether the mouse is over a given rectangle.
   * <p>
   * Note: The input rectangle is assumed to be scaled to screen space.
   *
   * @param rectangleScreenSpace The rectangle scaled to be in screen space.
   * @return True if the mouse is within bounds of the {@code rectangleScreenSpace}. False otherwise.
   */
  public boolean isMouseOverRectangle(Rectangle2D rectangleScreenSpace) {
    return myNormalizedMouseX >= rectangleScreenSpace.getX() &&
           myNormalizedMouseX <= rectangleScreenSpace.getWidth() + rectangleScreenSpace.getX();
  }
}
