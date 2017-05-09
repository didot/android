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

import com.android.SdkConstants;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import com.android.tools.idea.naveditor.scene.draw.DrawAction;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.ScenePicker;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.target.BaseTarget;
import com.android.tools.idea.uibuilder.scene.target.Target;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

import static com.android.tools.idea.naveditor.scene.draw.DrawAction.DrawMode.NORMAL;
import static com.android.tools.idea.naveditor.scene.draw.DrawAction.DrawMode.SELECTED;
import static com.android.tools.idea.naveditor.scene.targets.ActionTarget.ConnectionDirection.*;

/**
 * An Action in the navigation editor
 */
public class ActionTarget extends BaseTarget {
  @SwingCoordinate private Rectangle mySourceRect;
  @SwingCoordinate private Rectangle myDestRect;
  private final NlComponent myNlComponent;

  public static class CurvePoints {
    @SwingCoordinate public Point p1;
    @SwingCoordinate public Point p2;
    @SwingCoordinate public Point p3;
    @SwingCoordinate public Point p4;
    public ConnectionDirection dir;
  }

  public enum ConnectionType {NORMAL, SELF}

  public enum ConnectionDirection {
    LEFT(-1, 0), RIGHT(1, 0), TOP(0, -1), BOTTOM(0, 1);

    static {
      LEFT.myOpposite = RIGHT;
      RIGHT.myOpposite = LEFT;
      TOP.myOpposite = BOTTOM;
      BOTTOM.myOpposite = TOP;
    }

    private ConnectionDirection myOpposite;
    private final int myDeltaX;
    private final int myDeltaY;

    ConnectionDirection(int deltaX, int deltaY) {
      myDeltaX = deltaX;
      myDeltaY = deltaY;
    }

    public int getDeltaX() {
      return myDeltaX;
    }

    public int getDeltaY() {
      return myDeltaY;
    }

    public ConnectionDirection getOpposite() {
      return myOpposite;
    }
  }

  public ActionTarget(@NotNull SceneComponent component, @NotNull NlComponent actionComponent) {
    setComponent(component);
    myNlComponent = actionComponent;
  }

  @Override
  public boolean canChangeSelection() {
    return false;
  }

  @Override
  public void mouseRelease(int x, int y, @Nullable List<Target> closestTargets) {
    myNlComponent.getModel().getSelectionModel().setSelection(ImmutableList.of(myNlComponent));
  }

  @Override
  public int getPreferenceLevel() {
    return 0;
  }

  @Override
  public boolean layout(@NotNull SceneContext context, int l, int t, int r, int b) {
    // TODO
    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    Rectangle sourceRect = Coordinates.getSwingRectDip(sceneContext, getComponent().fillRect(null));
    String targetId = NlComponent.stripId(myNlComponent.getAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION));
    if (targetId == null) {
      // TODO: error handling
      return;
    }
    Rectangle destRect = null;
    //noinspection ConstantConditions
    for (SceneComponent candidate : getComponent().getParent().getChildren()) {
      if (targetId.equals(candidate.getId())) {
        destRect = Coordinates.getSwingRectDip(sceneContext, candidate.fillRect(null));
        break;
      }
    }
    mySourceRect = sourceRect;
    myDestRect = destRect;
    if (destRect != null) {
      boolean selected = getComponent().getScene().getSelection().contains(myNlComponent);
      DrawAction.buildDisplayList(list, ConnectionType.NORMAL, sourceRect, destRect, selected ? SELECTED : NORMAL);
    }
  }

  @Override
  public void addHit(@NotNull SceneContext transform, @NotNull ScenePicker picker) {
    CurvePoints points = getCurvePoints(mySourceRect, myDestRect);
    picker.addCurveTo(this, 5, points.p1.x, points.p1.y, points.p2.x, points.p2.y, points.p3.x, points.p3.y, points.p4.x, points.p4.y);
  }

  @NotNull
  public static CurvePoints getCurvePoints(@SwingCoordinate @NotNull Rectangle source,
                                            @SwingCoordinate @NotNull Rectangle dest) {
    ConnectionDirection sourceDirection = RIGHT;
    ConnectionDirection destDirection = LEFT;
    int startx = getConnectionX(sourceDirection, source);
    int starty = getConnectionY(sourceDirection, source);
    int endx = getConnectionX(destDirection, dest);
    int endy = getConnectionY(destDirection, dest);
    int dx = getDestinationDx(destDirection);
    int dy = getDestinationDy(destDirection);
    int scale_source = 100;
    int scale_dest = 100;
    CurvePoints result = new CurvePoints();
    result.dir = destDirection;
    result.p1 = new Point(startx, starty);
    result.p2 = new Point(startx + scale_source * sourceDirection.getDeltaX(), starty + scale_source * sourceDirection.getDeltaY());
    result.p3 = new Point(endx + dx + scale_dest * destDirection.getDeltaX(), endy + dy + scale_dest * destDirection.getDeltaY());
    result.p4 = new Point(endx + dx, endy + dy);
    return result;
  }

  private static int getConnectionX(@NotNull ConnectionDirection side, @NotNull Rectangle rect) {
    return rect.x + (1 + side.getDeltaX()) * rect.width / 2;
  }

  private static int getConnectionY(@NotNull ConnectionDirection side, @NotNull Rectangle rect) {
    return rect.y + (1 + side.getDeltaY()) * rect.height / 2;
  }

  public static int getDestinationDx(@NotNull ConnectionDirection side) {
    return side.getDeltaX() * DrawConnectionUtils.ARROW_SIDE;
  }

  public static int getDestinationDy(@NotNull ConnectionDirection side) {
    return side.getDeltaY() * DrawConnectionUtils.ARROW_SIDE;
  }
}

