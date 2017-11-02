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

package com.android.tools.adtui.chart.hchart;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.DefaultHNode;
import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;

public class HTreeChart<T> extends AnimatedComponent {

  private static final String NO_HTREE = "No data available.";
  private static final String NO_RANGE = "X range width is zero: Please use a wider range.";
  private static final int ZOOM_FACTOR = 20;
  private static final String ACTION_ZOOM_IN = "zoom in";
  private static final String ACTION_ZOOM_OUT = "zoom out";
  private static final String ACTION_MOVE_LEFT = "move left";
  private static final String ACTION_MOVE_RIGHT = "move right";
  private static final int ACTION_MOVEMENT_FACTOR = 5;
  private static final int BORDER_PLUS_PADDING = 2;

  private final Orientation myOrientation;

  @Nullable
  private HRenderer<T> myRenderer;

  @Nullable
  private HNode<T> myRoot;

  @NotNull
  private final Range myXRange;

  @NotNull
  private final Range myYRange;

  @NotNull
  private final List<Rectangle2D.Float> myRectangles;

  @NotNull
  private final List<HNode<T>> myNodes;

  private boolean myRootVisible;

  @NotNull
  private final List<Rectangle2D.Float> myDrawnRectangles;

  @NotNull
  private final List<HNode<T>> myDrawnNodes;

  @NotNull
  private final HTreeChartReducer<T> myReducer;

  private boolean myRender;

  @Nullable
  private Image myCanvas;

  @VisibleForTesting
  public HTreeChart(@NotNull Range xRange, Orientation orientation, @NotNull HTreeChartReducer<T> reducer) {
    myRectangles = new ArrayList<>();
    myNodes = new ArrayList<>();
    myDrawnNodes = new ArrayList<>();
    myDrawnRectangles = new ArrayList<>();
    myXRange = xRange;
    myRoot = new DefaultHNode<>();
    myReducer = reducer;
    myYRange = new Range(0, 0);
    myOrientation = orientation;
    myRootVisible = true;

    setFocusable(true);
    initializeInputMap();
    initializeMouseEvents();
    setFont(AdtUiUtils.DEFAULT_FONT);
    xRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::changed);
    myYRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::changed);
    changed();
  }

  public HTreeChart(Range xRange, Orientation orientation) {
    this(xRange, orientation, new DefaultHTreeChartReducer<>());
  }

  public void setRootVisible(boolean rootVisible) {
    myRootVisible = rootVisible;
    changed();
  }

  private void changed() {
    myRender = true;
    opaqueRepaint();
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    long startTime = System.nanoTime();
    if (myRender) {
      render();
      myRender = false;
    }
    g.setFont(getFont());
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    if (myRoot == null || myRoot.getChildCount() == 0) {
      g.drawString(NO_HTREE, dim.width / 2 - mDefaultFontMetrics.stringWidth(NO_HTREE),
                   dim.height / 2);
      return;
    }

    if (myXRange.getLength() == 0) {
      g.drawString(NO_RANGE, dim.width / 2 - mDefaultFontMetrics.stringWidth(NO_RANGE),
                   dim.height / 2);
      return;
    }

    if (myCanvas == null || myCanvas.getHeight(null) != dim.getHeight()
        || myCanvas.getWidth(null) != dim.getWidth()) {
      redrawToCanvas(dim);
    }

    g.drawImage(myCanvas, 0, 0, null);

    addDebugInfo("Draw time %.2fms", (System.nanoTime() - startTime) / 1e6);
    addDebugInfo("# of nodes %d", myNodes.size());
    addDebugInfo("# of reduced nodes %d", myDrawnNodes.size());
  }

  private void redrawToCanvas(@NotNull Dimension dim) {
    final Graphics2D g;
    if (myCanvas != null && myCanvas.getWidth(null) >= dim.width && myCanvas.getHeight(null) >= dim.height) {
      g = (Graphics2D)myCanvas.getGraphics();
      g.clearRect(0, 0, dim.width, dim.height);
    } else {
      myCanvas = createImage(dim.width, dim.height);
      g = (Graphics2D)myCanvas.getGraphics();
    }
    g.setFont(getFont());
    myDrawnNodes.clear();
    myDrawnNodes.addAll(myNodes);

    myDrawnRectangles.clear();
    // Transform
    for (Rectangle2D.Float rect : myRectangles) {
      Rectangle2D.Float newRect = new Rectangle2D.Float();
      newRect.x = rect.x * (float)dim.getWidth();
      newRect.y = rect.y;
      newRect.width = Math.max(0, rect.width * (float)dim.getWidth() - BORDER_PLUS_PADDING);
      newRect.height = rect.height;

      if (myOrientation == HTreeChart.Orientation.BOTTOM_UP) {
        newRect.y = (float)(dim.getHeight() - newRect.y - newRect.getHeight());
      }

      myDrawnRectangles.add(newRect);
    }

    myReducer.reduce(myDrawnRectangles, myDrawnNodes);

    assert myDrawnRectangles.size() == myDrawnNodes.size();
    assert myRenderer != null;
    for (int i = 0; i < myDrawnNodes.size(); ++i) {
      myRenderer.render(g, myDrawnNodes.get(i), myDrawnRectangles.get(i));
    }

    g.dispose();
  }

  protected void render() {
    myNodes.clear();
    myRectangles.clear();
    myCanvas = null;
    if (myRoot == null) {
      return;
    }

    if (inRange(myRoot)) {
      myNodes.add(myRoot);
      myRectangles.add(createRectangle(myRoot));
    }

    int head = 0;
    while (head < myNodes.size()) {
      HNode<T> curNode = myNodes.get(head++);

      for (int i = 0; i < curNode.getChildCount(); ++i) {
        HNode<T> child = curNode.getChildAt(i);
        if (inRange(child)) {
          myNodes.add(child);
          myRectangles.add(createRectangle(child));
        }
      }
    }
    if (!myRootVisible && !myNodes.isEmpty()) {
      myNodes.remove(0);
      myRectangles.remove(0);
    }
  }

  private boolean inRange(@NotNull HNode<T> node) {
    return node.getStart() <= myXRange.getMax() && node.getEnd() >= myXRange.getMin();
  }

  @NotNull
  private Rectangle2D.Float createRectangle(@NotNull HNode<T> node) {
    float left = (float)Math.max(0, (node.getStart() - myXRange.getMin()) / myXRange.getLength());
    float right = (float)Math.min(1, (node.getEnd() - myXRange.getMin()) / myXRange.getLength());
    Rectangle2D.Float rect = new Rectangle2D.Float();
    rect.x = left;
    rect.y = (float)((mDefaultFontMetrics.getHeight() + BORDER_PLUS_PADDING) * node.getDepth()
                     - getYRange().getMin());
    rect.width = right - left;
    rect.height = mDefaultFontMetrics.getHeight();
    return rect;
  }

  private double positionToRange(double x) {
    return x / getWidth() * getXRange().getLength() + getXRange().getMin();
  }

  public void setHRenderer(@NotNull HRenderer<T> r) {
    this.myRenderer = r;
  }

  public void setHTree(@Nullable HNode<T> root) {
    this.myRoot = root;
    changed();
  }

  public Range getXRange() {
    return myXRange;
  }

  @Nullable
  public HNode<T> getNodeAt(Point point) {
    if (point != null) {
      for (int i = 0; i < myDrawnNodes.size(); ++i) {
        if (contains(myDrawnRectangles.get(i), point)) {
          return myDrawnNodes.get(i);
        }
      }
    }
    return null;
  }

  private static boolean contains(@NotNull Rectangle2D rectangle, @NotNull Point p) {
    return rectangle.getMinX() <= p.getX() && p.getX() <= rectangle.getMaxX() &&
           rectangle.getMinY() <= p.getY() && p.getY() <= rectangle.getMaxY();
  }

  @NotNull
  public Orientation getOrientation() {
    return myOrientation;
  }

  private void initializeInputMap() {
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), ACTION_ZOOM_IN);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), ACTION_ZOOM_OUT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), ACTION_MOVE_LEFT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), ACTION_MOVE_RIGHT);

    getActionMap().put(ACTION_ZOOM_IN, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = myXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        myXRange.set(myXRange.getMin() + delta, myXRange.getMax() - delta);
      }
    });

    getActionMap().put(ACTION_ZOOM_OUT, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = myXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        myXRange.set(myXRange.getMin() - delta, myXRange.getMax() + delta);
      }
    });

    getActionMap().put(ACTION_MOVE_LEFT, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = myXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        myXRange.set(myXRange.getMin() - delta, myXRange.getMax() - delta);
      }
    });

    getActionMap().put(ACTION_MOVE_RIGHT, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = myXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        myXRange.set(myXRange.getMin() + delta, myXRange.getMax() + delta);
      }
    });
  }

  private void initializeMouseEvents() {
    MouseAdapter adapter = new MouseAdapter() {
      private Point myLastPoint;

      @Override
      public void mouseClicked(MouseEvent e) {
        if (!hasFocus()) {
          requestFocusInWindow();
        }
      }

      @Override
      public void mousePressed(MouseEvent e) {
        myLastPoint = e.getPoint();
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        double deltaX = e.getPoint().x - myLastPoint.x;
        double deltaY = e.getPoint().y - myLastPoint.y;

        getYRange().shift(getOrientation() == Orientation.BOTTOM_UP ? deltaY : -deltaY);
        getXRange().shift(myXRange.getLength() / getWidth() * -deltaX);

        myLastPoint = e.getPoint();
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        double cursorRange = positionToRange(e.getX());
        double leftDelta = (cursorRange - getXRange().getMin()) / ZOOM_FACTOR * e
          .getWheelRotation();
        double rightDelta = (getXRange().getMax() - cursorRange) / ZOOM_FACTOR * e
          .getWheelRotation();
        getXRange().setMin(getXRange().getMin() - leftDelta);
        getXRange().setMax(getXRange().getMax() + rightDelta);
      }
    };
    addMouseWheelListener(adapter);
    addMouseListener(adapter);
    addMouseMotionListener(adapter);
  }

  public Range getYRange() {
    return myYRange;
  }

  public int getMaximumHeight() {
    if (myRoot == null) {
      return 0;
    }

    int maxDepth = -1;
    Queue<HNode<T>> queue = new LinkedList<>();
    queue.add(myRoot);

    while (!queue.isEmpty()) {
      HNode<T> n = queue.poll();
      if (n.getDepth() > maxDepth) {
        maxDepth = n.getDepth();
      }

      for (int i = 0; i < n.getChildCount(); ++i) {
        queue.add(n.getChildAt(i));
      }
    }
    maxDepth += 1;
    return (mDefaultFontMetrics.getHeight() + BORDER_PLUS_PADDING) * maxDepth;
  }

  public enum Orientation {TOP_DOWN, BOTTOM_UP}
}