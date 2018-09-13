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

import com.intellij.util.Producer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class TooltipComponent extends AnimatedComponent {
  private static final int BORDER_SIZE = 10;
  private static final int[] DROPSHADOW_ALPHAS = new int[]{40, 30, 20, 10};

  @NotNull
  private final JComponent myTooltipContent;

  @NotNull
  private final JComponent myOwner;

  @NotNull
  private final JLayeredPane myParent;

  @NotNull
  private final Producer<Boolean> myIsOwnerDisplayable;

  @Nullable
  private final Producer<Boolean> myDefaultVisibilityOverride;

  // Minimum size of the tooltip between mouse enter/exit events.
  // This prevents the tooltip component from flapping due to the content size changing constantly.
  @NotNull
  private final Dimension myExpandedSize = new Dimension(0, 0);

  @Nullable
  private Point myLastPoint;

  @Nullable
  private Dimension myLastSize;

  private final ComponentListener myParentListener;

  private TooltipComponent(@NotNull Builder builder) {
    myTooltipContent = builder.myTooltipContent;
    myOwner = builder.myOwner;
    myParent = builder.myParent;
    myIsOwnerDisplayable = builder.myIsOwnerDisplayable;
    myDefaultVisibilityOverride = builder.myDefaultVisibilityOverride;

    myParentListener = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        resetBounds();
      }

      @Override
      public void componentMoved(ComponentEvent e) {
        resetBounds();
      }
    };

    add(myTooltipContent);
    setVisible(false);
    resetBounds();

    // Note: invokeLater here is important in order to avoid modifying the hierarchy during a
    // hierarchy event.
    // We usually handle tooltip removal on mouseExit, but we add this here for possible edge
    // cases.
    myOwner.addHierarchyListener(event -> SwingUtilities.invokeLater(() -> {
      if (!myIsOwnerDisplayable.produce()) {
        removeFromParent();
      }
    }));
  }

  private void removeFromParent() {
    setVisible(false);
    if (getParent() != null) {
      getParent().removeComponentListener(myParentListener);
      getParent().remove(this);
    }
  }

  private void resetBounds() {
    if (myParent.getWidth() != getWidth() || myParent.getHeight() != getHeight()) {
      setBounds(0, 0, myParent.getWidth(), myParent.getHeight());
    }
  }

  @NotNull
  private static Dimension max(@NotNull Dimension a, @NotNull Dimension b) {
    return new Dimension(Math.max(a.width, b.width), Math.max(a.height, b.height));
  }

  @Override
  public void doLayout() {
    Dimension size = getPreferredSize();
    myExpandedSize.setSize(size.width, 0); // Always let height vary.
    myTooltipContent.setSize(size);
    super.doLayout();
  }

  public void registerListenersOn(@NotNull Component component) {
    MouseAdapter adapter = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        handleMove(e);
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        handleMove(e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        myExpandedSize.setSize(0, 0);
        myTooltipContent.setSize(0, 0);
        myParent.addComponentListener(myParentListener);
        resetBounds();
        myParent.add(TooltipComponent.this, JLayeredPane.POPUP_LAYER);
        myLastPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), TooltipComponent.this);
        setVisible(myDefaultVisibilityOverride == null ? true : myDefaultVisibilityOverride.produce());
        revalidate();
        myLastSize = myTooltipContent.getPreferredSize(); // Stash this value only after revalidate().
        repaintIfVisible(myLastSize);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        repaintLastPoint(myLastSize);
        myExpandedSize.setSize(0, 0);
        myTooltipContent.setSize(0, 0);
        removeFromParent();
        setVisible(false);
        myLastPoint = null;
        myLastSize = null;
      }

      private void handleMove(MouseEvent e) {
        Point nextPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), TooltipComponent.this);
        if (myLastPoint != null && myLastPoint.equals(nextPoint)) {
          return; // Mouse detected a movement, but not enough to cross pixel boundaries.
        }

        repaintIfVisible(myTooltipContent.getSize()); // Repaint previous location.
        myLastPoint = nextPoint;
        if (!isVisible()) {
          // If we turn invisible, then reset the anti-flap size.
          // This won't work in all cases, since the content could set itself to invisible.
          myExpandedSize.setSize(0, 0);
        }
        if (!myTooltipContent.getPreferredSize().equals(myTooltipContent.getBounds().getSize())) {
          revalidate();
        }
        myLastSize = myTooltipContent.getPreferredSize(); // Stash this value only after revalidate().
        repaintIfVisible(myLastSize); // Repaint new location.
      }
    };
    component.addMouseMotionListener(adapter);
    component.addMouseListener(adapter);
  }

  @Override
  public Dimension getPreferredSize() {
    return max(max(myTooltipContent.getPreferredSize(), myTooltipContent.getMinimumSize()), myExpandedSize);
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    if (!isVisible()) {
      return; // We shouldn't draw the tooltip if its content is not supposed to be visible
    }
    assert myLastPoint != null; // If we're visible, myLastPoint is not null

    Dimension preferredSize = getPreferredSize();
    Point paintLocation = getPaintLocation(myLastPoint, preferredSize);
    myTooltipContent.setLocation(paintLocation.x, paintLocation.y);

    g.setColor(Color.WHITE);
    g.fillRect(paintLocation.x, paintLocation.y, preferredSize.width, preferredSize.height);
    g.setStroke(new BasicStroke(1.0f));

    RoundRectangle2D.Float rect = new RoundRectangle2D.Float();
    for (int i = 1; i <= DROPSHADOW_ALPHAS.length; i++) {
      g.setColor(new Color(0, 0, 0, DROPSHADOW_ALPHAS[i - 1]));
      rect.setRoundRect(paintLocation.x - i, paintLocation.y - i, preferredSize.width + i * 2, preferredSize.height + i * 2, i * 2, i * 2);
      g.draw(rect);
    }
  }

  @NotNull
  private Point getPaintLocation(@NotNull Point lastPoint, @NotNull Dimension preferredSize) {
    // Translate the bounds to clamp it wholly within the parent's drawable region.
    Dimension parentSize = getParent().getSize();
    int x = Math.max(Math.min(lastPoint.x + BORDER_SIZE / 2, parentSize.width - preferredSize.width - BORDER_SIZE / 2), BORDER_SIZE);
    int y = Math.max(Math.min(lastPoint.y + BORDER_SIZE / 2, parentSize.height - preferredSize.height - BORDER_SIZE / 2), BORDER_SIZE);
    return new Point(x, y);
  }

  public void repaintIfVisible(@NotNull Dimension contentDimension) {
    if (isVisible() && contentDimension.width > 0 && contentDimension.height > 0) {
      repaintLastPoint(contentDimension);
    }
  }

  private void repaintLastPoint(@Nullable Dimension repaintDimension) {
    if (myLastPoint == null) {
      return;
    }
    Dimension preferredSize = repaintDimension == null ? getPreferredSize() : repaintDimension;
    Point paintLocation = getPaintLocation(myLastPoint, preferredSize);
    opaqueRepaint(paintLocation.x - DROPSHADOW_ALPHAS.length,
                  paintLocation.y - DROPSHADOW_ALPHAS.length,
                  preferredSize.width + 2 * DROPSHADOW_ALPHAS.length + 1,   // We're off by 1 somewhere?
                  preferredSize.height + 2 * DROPSHADOW_ALPHAS.length + 1);
  }

  public static class Builder {
    @NotNull private final JComponent myTooltipContent;
    @NotNull private final JComponent myOwner;
    @NotNull private final JLayeredPane myParent;
    @NotNull private Producer<Boolean> myIsOwnerDisplayable;
    @Nullable private Producer<Boolean> myDefaultVisibilityOverride;

    /**
     * Construct a tooltip component to show for a particular {@code owner}. After
     * construction, you should also use {@link TooltipComponent#registerListenersOn(Component)} to ensure the
     * tooltip will show up on mouse movement.
     *
     * @param tooltipContent The content that will be rendered as contents of the tooltip.
     * @param owner          The suggested owner for this tooltip. The tooltip will walk up the
     *                       tree from this owner searching for a proper place to add itself.
     * @param parent         The top-most layered pane this tooltip will paint over.
     */
    public Builder(@NotNull JComponent tooltipContent, @NotNull JComponent owner, @NotNull JLayeredPane parent) {
      myTooltipContent = tooltipContent;
      myOwner = owner;
      myParent = parent;
      myIsOwnerDisplayable = myOwner::isDisplayable;
    }

    /**
     * Provided for tests, since in tests we can't create JFrames (they would throw a headless
     * exception), so calling {@code myOwner.isDisplayble()} directly would always return false.
     */
    @TestOnly
    @NotNull
    public Builder setIsOwnerDisplayable(@NotNull Producer<Boolean> isOwnerDisplayable) {
      myIsOwnerDisplayable = isOwnerDisplayable;
      return this;
    }

    /**
     * A visibility override on mouseEntered, in case an external class owns a tooltip component
     * and has more context about when it should first appear.
     */
    @NotNull
    public Builder setDefaultVisibilityOverride(@NotNull Producer<Boolean> defaultVisibilityOverride) {
      myDefaultVisibilityOverride = defaultVisibilityOverride;
      return this;
    }

    @NotNull
    public TooltipComponent build() {
      return new TooltipComponent(this);
    }
  }
}