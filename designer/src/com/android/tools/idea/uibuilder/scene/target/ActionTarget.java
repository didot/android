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
package com.android.tools.idea.uibuilder.scene.target;

import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawAction;
import com.android.tools.idea.uibuilder.scene.draw.DrawTextRegion;
import com.android.tools.sherpa.drawing.decorator.TextWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Implements an Action target
 */
public class ActionTarget extends ConstraintTarget {

  final static int mySize = 12;
  final static int myGap = 4;
  final ActionTarget myPreviousActionTarget;
  Action myAction;
  private int myActionType;
  protected boolean myIsVisible = true;

  public interface Action {
    void apply(SceneComponent component);
  }

  public ActionTarget(ActionTarget previous, Action action) {
    myPreviousActionTarget = previous;
    myAction = action;
  }

  public void setActionType(int type) {
    myActionType = type;
  }

  public void setAction(Action action) {
    myAction = action;
  }

  @Override
  public boolean canChangeSelection() {
    return false;
  }

  @Override
  public int getPreferenceLevel() {
    return Target.ACTION_LEVEL;
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform, int l, int t, int r, int b) {
    float ratio = 1f / (float)sceneTransform.getScale();
    if (ratio > 2) {
      ratio = 2;
    }
    if (myPreviousActionTarget == null) {
      myLeft = l;
    }
    else {
      myLeft = myPreviousActionTarget.myRight + (myGap * ratio);
    }
    float size = (mySize * ratio);
    myTop = b + (myGap * ratio);
    myRight = myLeft + size;
    myBottom = myTop + size;
    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (!myComponent.getScene().allowsTarget(this)) {
      return;
    }
    if (myIsVisible) {
      DrawAction.add(list, sceneContext, myLeft, myTop, myRight, myBottom, myActionType, mIsOver);
    }
  }

  @Override
  public void mouseDown(int x, int y) {
    // Do nothing
  }

  @Override
  public void mouseDrag(int x, int y, @Nullable Target closestTarget) {
    // Do nothing
  }

  @Override
  public void mouseRelease(int x, int y, @Nullable Target closestTarget) {
    if (myAction != null && closestTarget == this) {
      myAction.apply(myComponent);
      myComponent.getScene().needsRebuildList();
    }
  }
}
