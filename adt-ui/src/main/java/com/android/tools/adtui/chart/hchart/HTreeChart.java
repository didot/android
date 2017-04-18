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

public class HTreeChart<T> extends AnimatedComponent implements MouseWheelListener, MouseListener {

  private static final String NO_HTREE = "No data available.";
  private static final String NO_RANGE = "X range width is zero: Please use a wider range.";
  private static final int ZOOM_FACTOR = 20;
  private static final String ACTION_ZOOM_IN = "zoom in";
  private static final String ACTION_ZOOM_OUT = "zoom out";
  private static final String ACTION_MOVE_LEFT = "move left";
  private static final String ACTION_MOVE_RIGHT = "move right";
  private static final int ACTION_MOVEMENT_FACTOR = 5;
  private static final int BORDER_PLUS_PADDING = 2;

  private final Orientation mOrientation;

  @Nullable
  private HRenderer<T> mHRenderer;

  @Nullable
  private HNode<T> mRoot;

  @NotNull
  private final Range mXRange;

  @NotNull
  private final Range mYRange;

  @NotNull
  private final List<Rectangle2D.Float> mRectangles;

  @NotNull
  private final List<HNode<T>> mNodes;

  @NotNull
  private final List<Rectangle2D.Float> mDrawnRectangles;

  @NotNull
  private final List<HNode<T>> mDrawnNodes;

  @NotNull
  private final HTreeChartReducer<T> mReducer;

  private boolean mRender;

  @VisibleForTesting
  public HTreeChart(@NotNull Range xRange, Orientation orientation, @NotNull HTreeChartReducer<T> reducer) {
    mRectangles = new ArrayList<>();
    mNodes = new ArrayList<>();
    mDrawnNodes = new ArrayList<>();
    mDrawnRectangles = new ArrayList<>();
    mXRange = xRange;
    mRoot = new HNode<>();
    mReducer = reducer;
    mYRange = new Range(0, 0);
    addMouseWheelListener(this);
    mOrientation = orientation;
    setFocusable(true);
    addMouseListener(this);
    initializeInputMap();
    setFont(AdtUiUtils.FONT_DEFAULT);

    xRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::changed);
    changed();
  }

  public HTreeChart(Range xRange, Orientation orientation) {
    this(xRange, orientation, new DefaultHTreeChartReducer<>());
  }

  public HTreeChart(Range xRange) {
    this(xRange, Orientation.TOP_DOWN);
  }

  private void changed() {
    mRender = true;
    opaqueRepaint();
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    long startTime = System.nanoTime();
    if (mRender) {
      render();
      mRender = false;
    }

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    if (mRoot == null || mRoot.getChildren().size() == 0) {
      g.drawString(NO_HTREE, dim.width / 2 - mDefaultFontMetrics.stringWidth(NO_HTREE),
                   dim.height / 2);
      return;
    }

    if (mXRange.getLength() == 0) {
      g.drawString(NO_RANGE, dim.width / 2 - mDefaultFontMetrics.stringWidth(NO_RANGE),
                   dim.height / 2);
      return;
    }

    mDrawnNodes.clear();
    mDrawnNodes.addAll(mNodes);

    mDrawnRectangles.clear();
    // Transform
    for (Rectangle2D.Float rect : mRectangles) {
      Rectangle2D.Float newRect = new Rectangle2D.Float();
      newRect.x = rect.x * (float)dim.getWidth();
      newRect.y = rect.y;
      newRect.width = rect.width * (float)dim.getWidth() - BORDER_PLUS_PADDING;
      newRect.height = rect.height;

      if (mOrientation == HTreeChart.Orientation.BOTTOM_UP) {
        newRect.y = (float)(dim.getHeight() - newRect.y - newRect.getHeight());
      }

      mDrawnRectangles.add(newRect);
    }

    mReducer.reduce(mDrawnRectangles, mDrawnNodes);

    assert mDrawnRectangles.size() == mDrawnNodes.size();
    assert mHRenderer != null;
    for (int i = 0; i < mDrawnNodes.size(); ++i) {
      mHRenderer.render(g, mDrawnNodes.get(i).getData(), mDrawnRectangles.get(i));
    }

    addDebugInfo("Draw time %.2fms", (System.nanoTime() - startTime) / 1e6);
    addDebugInfo("# of nodes %d", mNodes.size());
    addDebugInfo("# of reduced nodes %d", mDrawnNodes.size());
  }

  protected void render() {
    mNodes.clear();
    mRectangles.clear();
    if (mRoot == null) {
      return;
    }

    if (inRange(mRoot)) {
      mNodes.add(mRoot);
      mRectangles.add(createRectangle(mRoot));
    }

    int head = 0;
    while (head < mNodes.size()) {
      HNode<T> curNode = mNodes.get(head++);

      for (HNode<T> child: curNode.getChildren()) {
        if (inRange(child)) {
          mNodes.add(child);
          mRectangles.add(createRectangle(child));
        }
      }
    }
  }

  private boolean inRange(@NotNull HNode<T> node) {
    return node.getStart() <= mXRange.getMax() && node.getEnd() >= mXRange.getMin();
  }

  @NotNull
  private Rectangle2D.Float createRectangle(@NotNull HNode<T> node) {
    float left = (float)Math.max(0, (node.getStart() - mXRange.getMin()) / mXRange.getLength());
    float right = (float)Math.min(1, (node.getEnd() - mXRange.getMin()) / mXRange.getLength());
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

  public void setHRenderer(HRenderer<T> r) {
    this.mHRenderer = r;
    this.mHRenderer.setFont(getFont());
  }

  public void setHTree(@Nullable HNode<T> root) {
    this.mRoot = root;
    changed();
  }

  public Range getXRange() {
    return mXRange;
  }

  @Nullable
  public HNode<T> getHoveredNode() {
    Point p = getMousePosition();
    if (p != null) {
      for (int i = 0; i < mDrawnNodes.size(); ++i) {
        if (mDrawnRectangles.get(i).contains(p)) {
          return mDrawnNodes.get(i);
        }
      }
    }
    return null;
  }

  private void initializeInputMap() {
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), ACTION_ZOOM_IN);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), ACTION_ZOOM_OUT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), ACTION_MOVE_LEFT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), ACTION_MOVE_RIGHT);

    getActionMap().put(ACTION_ZOOM_IN, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = mXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        mXRange.set(mXRange.getMin() + delta, mXRange.getMax() - delta);
      }
    });

    getActionMap().put(ACTION_ZOOM_OUT, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = mXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        mXRange.set(mXRange.getMin() - delta, mXRange.getMax() + delta);
      }
    });

    getActionMap().put(ACTION_MOVE_LEFT, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = mXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        mXRange.set(mXRange.getMin() - delta, mXRange.getMax() - delta);
      }
    });

    getActionMap().put(ACTION_MOVE_RIGHT, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        double delta = mXRange.getLength() / ACTION_MOVEMENT_FACTOR;
        mXRange.set(mXRange.getMin() + delta, mXRange.getMax() + delta);
      }
    });
  }

  public Range getYRange() {
    return mYRange;
  }

  public int getMaximumHeight() {
    if (mRoot == null) {
      return 0;
    }

    int maxDepth = -1;
    Queue<HNode<T>> queue = new LinkedList<>();
    queue.add(mRoot);

    while (!queue.isEmpty()) {
      HNode<T> n = queue.poll();
      if (n.getDepth() > maxDepth) {
        maxDepth = n.getDepth();
      }
      queue.addAll(n.getChildren());
    }
    maxDepth += 1;
    return (mDefaultFontMetrics.getHeight() + BORDER_PLUS_PADDING) * maxDepth;
  }

  // TODO we probably want to extract/abstract this logic out later so the zooming behavior
  // is consistent across components
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

  @Override
  public void mouseClicked(MouseEvent e) {
    if (!hasFocus()) {
      requestFocusInWindow();
    }
  }

  @Override
  public void mousePressed(MouseEvent e) {
  }

  @Override
  public void mouseReleased(MouseEvent e) {
  }

  @Override
  public void mouseEntered(MouseEvent e) {
  }

  @Override
  public void mouseExited(MouseEvent e) {

  }

  public enum Orientation {TOP_DOWN, BOTTOM_UP}
}