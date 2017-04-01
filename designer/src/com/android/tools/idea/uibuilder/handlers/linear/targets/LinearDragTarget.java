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
package com.android.tools.idea.uibuilder.handlers.linear.targets;

import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.AnchorTarget;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.target.DragBaseTarget;
import com.android.tools.idea.uibuilder.scene.target.Target;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.command.WriteCommandAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Target to handle the drag of LinearLayout's children
 */
public class LinearDragTarget extends DragBaseTarget {


  private final LinearLayoutHandler myHandler;
  private LinearSeparatorTarget myClosest;

  public LinearDragTarget(LinearLayoutHandler handler) {
    myHandler = handler;
  }

  @Override
  protected void updateAttributes(@NotNull AttributesTransaction attributes, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    //Do nothing
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    super.mouseDown(x, y);
    myComponent.setModelUpdateAuthorized(false);
    SceneComponent parent = myComponent.getParent();
    assert parent != null;
    myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
    myComponent.getScene().repaint();
  }

  @Override
  public void mouseDrag(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @Nullable List<Target> closestTargets) {
    myComponent.setDragging(true);
    SceneComponent sceneParent = myComponent.getParent();
    assert sceneParent != null;
    if (myHandler.isVertical(sceneParent.getNlComponent())) {
      y -= myOffsetY;
      myComponent.setPosition(myComponent.getDrawX(), Math.max(y, 0), false);
    }
    else {
      x -= myOffsetX - sceneParent.getDrawX();
      myComponent.setPosition(Math.max(x, 0), myComponent.getDrawY(), false);
    }

    // Reset previous closest Target
    if (myClosest != null) {
      myClosest.setHighlight(false);
      myClosest = null;
    }

    Target closestTarget = null;
    for (Target target : closestTargets) {
      if (target instanceof LinearSeparatorTarget && target != this) {
        closestTarget = target;
        break;
      }
    }

    if (closestTarget != null) {
      myClosest = (LinearSeparatorTarget)closestTarget;
      myClosest.setHighlight(true);
    }

    myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
    myComponent.getScene().repaint();
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @Nullable List<Target> closestTarget) {
    super.mouseRelease(x, y, closestTarget);
    if (myClosest != null) {
      SceneComponent sceneParent = myComponent.getParent();
      if (sceneParent == null) {
        return;
      }
      NlComponent parent = sceneParent.getNlComponent();
      NlModel model = parent.getModel();

      Runnable swapComponent = () -> {
        ImmutableList<NlComponent> nlComponentImmutableList = ImmutableList.of(myComponent.getNlComponent());
        parent.getModel().addComponents(
          nlComponentImmutableList,
          parent,
          !myClosest.isAtEnd() ? myClosest.getComponent().getNlComponent() : null,
          InsertType.MOVE_WITHIN);
      };
      WriteCommandAction.runWriteCommandAction(model.getProject(), "Move component",
                                               null, swapComponent, model.getFile());
    }
    myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
    myComponent.setModelUpdateAuthorized(true);
  }
}
