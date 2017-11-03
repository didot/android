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

import com.android.annotations.VisibleForTesting;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

public final class TooltipComponent extends AnimatedComponent {
  @NotNull
  private final Component myContent;

  @NotNull
  private final Component myOwner;

  /**
   * Provided for tests, since in tests we can't create JFrames (they would throw a headless
   * exception), so calling {@code myOwner.isDisplayble()} directly would always return false.
   */
  @NotNull
  private final Producer<Boolean> myIsOwnerDisplayable;

  @Nullable
  private Point myLastPoint;

  private final ComponentListener myParentListener;

  @Nullable
  private Class<? extends JLayeredPane> myPreferredParentClass;

  /**
   * Construct a tooltip component to show for a particular {@code owner}. After
   * construction, you should also use {@link #registerListenersOn(Component)} to ensure the
   * tooltip will show up on mouse movement.
   *
   * @param owner                The suggested owner for this tooltip. The tooltip will walk up the
   *                             tree from this owner searching for a proper place to add itself.
   * @param preferredParentClass If not {@code null}, the type of pane to use as this tooltip's
   *                             parent (useful if you have a specific parent pane in mind)
   */
  public TooltipComponent(@NotNull Component content,
                          @NotNull Component owner,
                          @Nullable Class<? extends JLayeredPane> preferredParentClass) {
    this(content, owner, preferredParentClass, owner::isDisplayable);
  }

  public TooltipComponent(@NotNull Component content, @NotNull Component owner) {
    this(content, owner, null);
  }

  @VisibleForTesting
  TooltipComponent(@NotNull Component content,
                   @NotNull Component owner,
                   @Nullable Class<? extends JLayeredPane> preferredParentClass,
                   @NotNull Producer<Boolean> isOwnerDisplayable) {
    myContent = content;
    myOwner = owner;
    myIsOwnerDisplayable = isOwnerDisplayable;
    myPreferredParentClass = preferredParentClass;
    add(content);
    removeFromParent();

    // Note: invokeLater here is important in order to avoid modifying the hierarchy during a
    // hierarchy event.
    // We usually handle tooltip removal on mouseexit, but we add this here for possible edge
    // cases.
    owner.addHierarchyListener(event -> SwingUtilities.invokeLater(() -> {
      if (!myIsOwnerDisplayable.produce()) {
        removeFromParent();
      }
    }));

    myParentListener = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        setBounds();
      }

      @Override
      public void componentMoved(ComponentEvent e) {
        setBounds();
      }
    };
  }

  private void recomputeParent() {
    if (!myIsOwnerDisplayable.produce()) {
      // No need to walk ancestors if the owner has been removed from the hierarchy
      removeFromParent();
      return;
    }

    JLayeredPane layeredPane = null;
    for (Component c : new TreeWalker(myOwner).ancestors()) {
      if (c instanceof JLayeredPane) {
        layeredPane = (JLayeredPane)c;
        if (c.getClass() == myPreferredParentClass) {
          break;
        }
      }
      else if (c instanceof RootPaneContainer) {
        layeredPane = ((RootPaneContainer)c).getLayeredPane();
      }
    }

    if (layeredPane == getParent()) {
      return;
    }

    removeFromParent();
    if (layeredPane != null) {
      setParent(layeredPane);
    }
  }

  private void removeFromParent() {
    setVisible(false);
    if (getParent() != null) {
      getParent().removeComponentListener(myParentListener);
      getParent().remove(this);
    }
  }

  private void setParent(@NotNull JLayeredPane parent) {
    setVisible(true);
    parent.add(this, JLayeredPane.POPUP_LAYER);
    parent.addComponentListener(myParentListener);
    setBounds();
  }

  private void setBounds() {
    Container parent = getParent();
    if (parent == null) {
      return;
    }
    setBounds(0, 0, parent.getWidth(), parent.getHeight());
  }

  public void registerListenersOn(Component component) {
    MouseAdapter adapter = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        handleMove(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myLastPoint = null;
        if (isVisible()) {
          removeFromParent();
        }
        opaqueRepaint();
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        handleMove(e);
      }

      private void handleMove(MouseEvent e) {
        if (!isVisible()) {
          recomputeParent();
        }
        myLastPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), TooltipComponent.this);
        opaqueRepaint();
      }
    };
    component.addMouseMotionListener(adapter);
    component.addMouseListener(adapter);
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    assert myLastPoint != null; // If we're visible, myLastPoint is not null

    Dimension size = myContent.getPreferredSize();
    Dimension minSize = myContent.getMinimumSize();
    size = new Dimension(Math.max(size.width, minSize.width), Math.max(size.height, minSize.height));

    g.setColor(Color.WHITE);
    int gap = 10;
    int x1 = Math.max(Math.min((myLastPoint.x + gap), dim.width - size.width - gap), 0);
    int y1 = Math.max(Math.min(myLastPoint.y + gap, dim.height - size.height - gap), gap);
    int width = size.width;
    int height = size.height;

    g.fillRect(x1, y1, width, height);

    g.setStroke(new BasicStroke(1.0f));

    int lines = 4;
    int[] alphas = new int[]{40, 30, 20, 10};
    RoundRectangle2D.Float rect = new RoundRectangle2D.Float();
    for (int i = 0; i < lines; i++) {
      g.setColor(new Color(0, 0, 0, alphas[i]));
      rect.setRoundRect(x1 - 1 - i, y1 - 1 - i, width + 1 + i * 2, height + 1 + i * 2, i * 2 + 2, i * 2 + 2);
      g.draw(rect);
    }
    myContent.setBounds(x1, y1, width, height);
    myContent.repaint();
  }
}