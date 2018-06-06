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
package com.android.tools.idea.naveditor.scene.targets;

import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.DragBaseTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.MultiComponentTarget;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * Implements a target allowing dragging a nav editor screen
 */
public class ScreenDragTarget extends DragBaseTarget implements MultiComponentTarget {

  private final Point[] myChildOffsets;

  public ScreenDragTarget(@NotNull SceneComponent component) {
    super();
    setComponent(component);

    myChildOffsets = new Point[component.getChildren().size()];
  }

  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  protected void updateAttributes(@NotNull NlAttributesHolder attributes, int x, int y) {
    // Nothing
  }

  @Override
  public void mouseDown(@NavCoordinate int x, @NavCoordinate int y) {
    super.mouseDown(x, y);

    for (int i = 0; i < getComponent().getChildren().size(); i++) {
      SceneComponent child = getComponent().getChild(i);
      myChildOffsets[i] = new Point(x - child.getDrawX(), y - child.getDrawY());
    }
  }

  @Override
  public void mouseDrag(@NavCoordinate int x, @NavCoordinate int y, @NotNull List<Target> closestTarget) {
    // TODO: Support growing the scrollable area when dragging a control off the screen
    SceneComponent parent = myComponent.getParent();

    if (parent == null) {
      return;
    }

    myComponent.setDragging(true);
    int dx = x - myOffsetX;
    int dy = y - myOffsetY;

    if (dx < parent.getDrawX() || dx + myComponent.getDrawWidth() > parent.getDrawX() + parent.getDrawWidth()) {
      return;
    }

    if (dy < parent.getDrawY() || dy + myComponent.getDrawHeight() > parent.getDrawY() + parent.getDrawHeight()) {
      return;
    }

    myComponent.setPosition(dx, dy, false);
    myChangedComponent = true;

    for (int i = 0; i < myChildOffsets.length; i++) {
      @NavCoordinate int newX = x - myChildOffsets[i].x;
      @NavCoordinate int newY = y - myChildOffsets[i].y;
      getComponent().getChild(i).setPosition(newX, newY);
    }
  }

  @Override
  public void mouseRelease(@NavCoordinate int x, @NavCoordinate int y, @NotNull List<Target> closestTargets) {
    if (!myComponent.isDragging()) {
      return;
    }
    myComponent.setDragging(false);
    if (myComponent.getParent() != null) {
      if (Math.abs(x - myFirstMouseX) <= 1 && Math.abs(y - myFirstMouseY) <= 1) {
        return;
      }
      ((NavSceneManager)myComponent.getScene().getSceneManager()).save(ImmutableList.of(myComponent));
    }
    if (myChangedComponent) {
      myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
    }
  }

  @Override
  public Cursor getMouseCursor() {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
