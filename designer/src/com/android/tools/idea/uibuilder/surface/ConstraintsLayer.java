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
package com.android.tools.idea.uibuilder.surface;

import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.adtui.common.SwingCoordinate;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;

public class ConstraintsLayer extends Layer {
  private final ScreenView myScreenView;
  private final NlDesignSurface myDesignSurface;

  private Dimension myScreenViewSize = new Dimension();
  private Rectangle mySizeRectangle = new Rectangle();
  private final boolean showOnSelection;
  private boolean myShowOnHover = false;
  private boolean myTemporaryShow = false;

  public ConstraintsLayer(NlDesignSurface designSurface, @NotNull ScreenView screenView, boolean showOnSelection) {
    myDesignSurface = designSurface;
    myScreenView = screenView;
    this.showOnSelection = showOnSelection;
  }

  public boolean isShowOnHover() {
    return myShowOnHover;
  }

  public void setShowOnHover(boolean value) {
    myShowOnHover = value;
  }

  public ScreenView getScreenView() {
    return myScreenView;
  }

  /**
   * Base paint method. Draw the layer's background and call drawComponent() on the root component.
   *
   * @param gc The Graphics object to draw into
   */
  @Override
  public void paint(@NotNull Graphics2D gc) {
    myScreenView.getSize(myScreenViewSize);

    mySizeRectangle.setBounds(myScreenView.getX(), myScreenView.getY(), myScreenViewSize.width, myScreenViewSize.height);
    Rectangle2D.intersect(mySizeRectangle, gc.getClipBounds(), mySizeRectangle);
    if (mySizeRectangle.isEmpty()) {
      return;
    }

    NlModel myModel = myScreenView.getModel();

    if (!myTemporaryShow && !myShowOnHover && showOnSelection) {
      return;
    }

    if (myModel.getComponents().isEmpty()) {
      return;
    }
    NlComponent component = myModel.getComponents().get(0);
    component = component.getRoot();

    // Draw the background
    Graphics2D g = (Graphics2D) gc.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

    // Draw the components
    if (drawComponent(g, component, false)) {
      Dimension size = myScreenView.getSize();
      if (size.width != 0 && size.height != 0) {
        myDesignSurface.repaint(myScreenView.getX(), myScreenView.getY(), size.width, size.height);
      } else {
        myDesignSurface.repaint();
      }
    }

    g.dispose();
  }

  /**
   * Draw the given component and its children
   *
   * @param gc the graphics context
   * @param component the component we want to draw
   * @param parentHandlesPainting the parent of the component already handled the painting
   *
   * @return true if the component needs a repaint (for example when running an application)
   */
  private boolean drawComponent(@NotNull Graphics2D gc, @NotNull NlComponent component, boolean parentHandlesPainting) {
    if (NlComponentHelperKt.getViewInfo(component) != null) {

      ViewHandler handler = NlComponentHelperKt.getViewHandler(component);
      boolean handlesPainting = false;

      // Check if the view handler handles the painting
      if (handler != null && handler instanceof ViewGroupHandler) {
        ViewGroupHandler viewGroupHandler = (ViewGroupHandler)handler;
        if (viewGroupHandler.handlesPainting()) {
          viewGroupHandler.drawGroup(gc, myScreenView, component);
          handlesPainting = true;
        }
      }

      if (handler != null) {
        handler.paintConstraints(myScreenView, gc, component);
      }

    }

    boolean needsRepaint = false;
    // Draw the children of the component...
    for (NlComponent child : component.getChildren()) {
      needsRepaint |= drawComponent(gc, child, parentHandlesPainting);
    }
    return needsRepaint;
  }

  @Override
  public void hover(@SwingCoordinate int x, @SwingCoordinate int y) {
    // For constraint layer, set show on hover if they are above their screen view
    boolean show = false;
    if (getScreenView() == myDesignSurface.getHoverSceneView(x, y)) {
      show = true;
    }
    if (isShowOnHover() != show) {
      setShowOnHover(show);
      myDesignSurface.repaint();
    }
  }

  public void setTemporaryShow(boolean temporaryShow) {
    myTemporaryShow = temporaryShow;
  }
}
