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
package com.android.tools.idea.uibuilder.handlers.constraint.targets;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.TemporarySceneComponent;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.scout.Scout;
import com.android.tools.idea.uibuilder.scout.ScoutArrange;
import com.android.tools.idea.uibuilder.scout.ScoutWidget;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * Implements a target managing dragging on a dnd temporary widget
 */
public class ConstraintDragDndTarget extends ConstraintDragTarget {

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    if (myComponent instanceof TemporarySceneComponent) {
      getTargetNotchSnapper().gatherNotches(myComponent);
    }
    else {
      super.mouseDown(x, y);
    }
  }

  @Override
  public void mouseDrag(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @Nullable List<Target> closestTarget) {
    if (myComponent instanceof TemporarySceneComponent) {
      Scene scene = myComponent.getScene();
      int dx = getTargetNotchSnapper().trySnapX(x);
      int dy = getTargetNotchSnapper().trySnapY(y);
      myComponent.setPosition(dx, dy);
      scene.needsRebuildList();
    }
    else {
      super.mouseDrag(x, y, closestTarget);
    }
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    super.render(list, sceneContext);
  }

  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull NlComponent component) {
    myComponent.setDragging(false);
    if (myComponent.getParent() != null) {
      AttributesTransaction attributes = component.startAttributeTransaction();
      int dx = x - myOffsetX;
      int dy = y - myOffsetY;
      Point snappedCoordinates = getTargetNotchSnapper().applyNotches(myComponent, attributes, dx, dy);
      updateAttributes(attributes, snappedCoordinates.x, snappedCoordinates.y);

      boolean horizontalMatchParent = false;
      boolean verticalMatchParent = false;
      if (SdkConstants.VALUE_MATCH_PARENT.equals(component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH))) {
        horizontalMatchParent = true;
      }
      if (SdkConstants.VALUE_MATCH_PARENT.equals(component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT))) {
        verticalMatchParent = true;
      }
      if (horizontalMatchParent || verticalMatchParent) {
        float dpiFactor = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
        NlComponentHelperKt.setX(component, (int)(dx * dpiFactor));
        NlComponentHelperKt.setY(component, (int)(dy * dpiFactor));
        ScoutWidget parentScoutWidget = new ScoutWidget(myComponent.getParent().getNlComponent(), null);
        ScoutWidget[] scoutWidgets = ScoutWidget.create(Arrays.asList(component), parentScoutWidget);
        int margin = Scout.getMargin();
        if (horizontalMatchParent) {
          ScoutArrange.expandHorizontally(scoutWidgets, parentScoutWidget, margin, false);
        }
        if (verticalMatchParent) {
          ScoutArrange.expandVertically(scoutWidgets, parentScoutWidget, margin, false);
        }
      }
      attributes.apply();

      Project project = myComponent.getNlComponent().getModel().getProject();
      XmlFile file = myComponent.getNlComponent().getModel().getFile();
      WriteCommandAction action = new WriteCommandAction(project, "drag", file) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          attributes.commit();
        }
      };
      action.execute();

    }
    if (myChangedComponent) {
      myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
    }
  }
}
