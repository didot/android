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
package com.android.tools.idea.common.scene.target;

import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.MultiComponentTarget;
import com.android.tools.idea.uibuilder.scene.target.TargetSnapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * Base class for dragging targets.
 */
public abstract class DragBaseTarget extends BaseTarget implements MultiComponentTarget {

  private static final boolean DEBUG_RENDERER = false;

  @AndroidDpCoordinate protected int myOffsetX;
  @AndroidDpCoordinate protected int myOffsetY;
  @AndroidDpCoordinate protected int myFirstMouseX;
  @AndroidDpCoordinate protected int myFirstMouseY;
  protected boolean myChangedComponent;

  @NotNull private final TargetSnapper myTargetSnapper;

  private DragBaseTarget(@NotNull TargetSnapper targetSnapper) {
    super();
    myTargetSnapper = targetSnapper;
  }

  public DragBaseTarget() {
    this(new TargetSnapper());
  }

  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean canChangeSelection() {
    return true;
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    int minWidth = 16;
    int minHeight = 16;
    if (r - l < minWidth) {
      int d = (minWidth - (r - l)) / 2;
      l -= d;
      r += d;
    }
    if (b - t < minHeight) {
      int d = (minHeight - (b - t)) / 2;
      t -= d;
      b += d;
    }
    myLeft = l;
    myTop = t;
    myRight = r;
    myBottom = b;
    return false;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (DEBUG_RENDERER) {
      list.addRect(sceneContext, myLeft, myTop, myRight, myBottom, mIsOver ? JBColor.yellow : JBColor.green);
      list.addLine(sceneContext, myLeft, myTop, myRight, myBottom, JBColor.red);
      list.addLine(sceneContext, myLeft, myBottom, myRight, myTop, JBColor.red);
    }
    myTargetSnapper.renderSnappedNotches(list, sceneContext, myComponent);
  }

  protected abstract void updateAttributes(@NotNull NlAttributesHolder attributes,
                                           @AndroidDpCoordinate int x,
                                           @AndroidDpCoordinate int y);

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public int getPreferenceLevel() {
    return Target.DRAG_LEVEL;
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    if (myComponent.getParent() == null) {
      return;
    }
    myFirstMouseX = x;
    myFirstMouseY = y;
    myOffsetX = x - myComponent.getDrawX(System.currentTimeMillis());
    myOffsetY = y - myComponent.getDrawY(System.currentTimeMillis());
    myChangedComponent = false;
    getTargetNotchSnapper().reset();
    getTargetNotchSnapper().gatherNotches(myComponent);
  }

  @Override
  public void mouseDrag(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    if (myComponent.getParent() == null) {
      return;
    }
    myComponent.setDragging(true);
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    x -= myOffsetX;
    y -= myOffsetY;
    int snappedX = myTargetSnapper.trySnapHorizontal(x).orElse(x);
    int snappedY = myTargetSnapper.trySnapVertical(y).orElse(y);
    ComponentModification modification = new ComponentModification(component, "Drag");
    updateAttributes(modification, snappedX, snappedY);
    modification.apply();
    component.fireLiveChangeEvent();
    myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
    myChangedComponent = true;
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    if (!myComponent.isDragging()) {
      return;
    }
    myComponent.setDragging(false);
    if (myComponent.getParent() != null) {
      boolean commitChanges = true;
      if (Math.abs(x - myFirstMouseX) <= 1 && Math.abs(y - myFirstMouseY) <= 1) {
        commitChanges = false;
      }
      NlComponent component = myComponent.getAuthoritativeNlComponent();
      ComponentModification modification = new ComponentModification(component, "Drag");
      x -= myOffsetX;
      y -= myOffsetY;
      int snappedX = myTargetSnapper.trySnapHorizontal(x).orElse(x);
      int snappedY = myTargetSnapper.trySnapVertical(y).orElse(y);
      if (isAutoConnectionEnabled()) {
        myTargetSnapper.applyNotches(modification);
      }
      updateAttributes(modification, snappedX, snappedY);
      modification.apply();

      if (commitChanges) {
        modification.commit();
      }
    }
    if (myChangedComponent) {
      myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
    }
  }
  //endregion
  /////////////////////////////////////////////////////////////////////////////

  /**
   * @return true if the constraint should be applied, false otherwise.
   */
  protected boolean isAutoConnectionEnabled() {
    return true;
  }

  /**
   * Reset the status when the dragging is canceled.
   */
  public void cancel() {
    myComponent.setDragging(false);
    myTargetSnapper.reset();
    myChangedComponent = false;
    myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
  }

  @Override
  public Cursor getMouseCursor() {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }

  @NotNull
  public TargetSnapper getTargetNotchSnapper() {
    return myTargetSnapper;
  }

  public boolean hasChangedComponent() {
    return myChangedComponent;
  }
}
