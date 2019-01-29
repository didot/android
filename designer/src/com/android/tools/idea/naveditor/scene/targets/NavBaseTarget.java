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

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.target.BaseTarget;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import java.awt.geom.Rectangle2D;
import org.jetbrains.annotations.NotNull;

/**
 * {@linkplain NavBaseTarget} Contains helper functions common to navigation editor targets.
 */
public abstract class NavBaseTarget extends BaseTarget {
  protected NavBaseTarget(@NotNull SceneComponent component) {
    setComponent(component);
  }

  protected void layoutRectangle(@NavCoordinate int l,
                                 @NavCoordinate int t,
                                 @NavCoordinate int r,
                                 @NavCoordinate int b) {
    myLeft = l;
    myTop = t;
    myRight = r;
    myBottom = b;
  }

  protected void layoutCircle(@NavCoordinate int x,
                              @NavCoordinate int y,
                              @NavCoordinate int r) {
    layoutRectangle(x - r, y - r, x + r, y + r);
  }

  @SwingCoordinate
  protected int getSwingLeft(@NotNull SceneContext sceneContext) {
    return sceneContext.getSwingX((int)myLeft);
  }

  @SwingCoordinate
  protected int getSwingTop(@NotNull SceneContext sceneContext) {
    return sceneContext.getSwingY((int)myTop);
  }

  @SwingCoordinate
  protected int getSwingRight(@NotNull SceneContext sceneContext) {
    return sceneContext.getSwingX((int)myRight);
  }

  @SwingCoordinate
  protected int getSwingBottom(@NotNull SceneContext sceneContext) {
    return sceneContext.getSwingY((int)myBottom);
  }

  @SwingCoordinate
  protected int getSwingCenterX(@NotNull SceneContext sceneContext) {
    return sceneContext.getSwingX((int)getCenterX());
  }

  @SwingCoordinate
  protected int getSwingCenterY(@NotNull SceneContext sceneContext) {
    return sceneContext.getSwingY((int)getCenterY());
  }

  @NavCoordinate
  public Rectangle2D.Float getBounds() {
    return new Rectangle2D.Float(myLeft, myTop, myRight - myLeft, myBottom - myTop);
  }
}
