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
package com.android.tools.idea.uibuilder.scene;

import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.uibuilder.model.NlLayoutType;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneView;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.sherpa.drawing.AndroidColorSet;
import com.android.tools.sherpa.drawing.BlueprintColorSet;
import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.idea.naveditor.scene.NavColorSet;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.WeakHashMap;

/**
 * This represents the Transform between dp to screen space.
 * There are two ways to create it get() or get(ScreenView)
 */
public class SceneContext {
  ColorSet myColorSet;
  Long myTime;
  @SwingCoordinate int myMouseX = -1, myMouseY = -1;
  private boolean myShowOnlySelection = false;

  private SceneContext() {
    myTime = System.currentTimeMillis();
  }

  public void setShowOnlySelection(boolean value) {
    myShowOnlySelection = value;
  }

  public long getTime() {
    return myTime;
  }

  public void setTime(long time) {
    myTime = time;
  }

  public void setMouseLocation(@SwingCoordinate int x, @SwingCoordinate int y) {
    myMouseX = x;
    myMouseY = y;
  }

  /**
   * Get the X location of the mouse
   *
   * @return
   */
  @SwingCoordinate
  public int getMouseX() {
    return myMouseX;
  }

  /**
   * Get the Y location of the mouse
   *
   * @return
   */
  @SwingCoordinate
  public int getMouseY() {
    return myMouseY;
  }

  /**
   * Used to request Repaint
   */
  public void repaint() {

  }

  @SwingCoordinate
  public int getSwingX(@AndroidDpCoordinate float x) {
    return (int) x;
  }

  @SwingCoordinate
  public int getSwingY(@AndroidDpCoordinate float y) {
    return (int) y;
  }

  @SwingCoordinate
  public int getSwingDimension(@AndroidDpCoordinate float dim) {
    return (int) dim;
  }

  public ColorSet getColorSet() {
    return myColorSet;
  }

  public double getScale() { return 1; }

  private static SceneContext lazySingleton;

  /**
   * Provide an Identity transform used in testing
   *
   * @return
   */
  public static SceneContext get() {
    if (lazySingleton == null) {
      lazySingleton = new SceneContext();
      lazySingleton.myColorSet = new BlueprintColorSet();
    }
    return lazySingleton;
  }

  /**
   * Get a SceneContext for a SceneView. They are cached and reused using a weakhashmap.
   *
   * @param sceneView
   * @return
   */
  public static SceneContext get(SceneView sceneView) {
    if (cache.containsKey(sceneView)) {
      SoftReference<SceneViewTransform> viewTransformRef =  cache.get(sceneView);
      SceneViewTransform viewTransform = viewTransformRef != null ? viewTransformRef.get() : null;

      if (viewTransform != null) {
        return viewTransform;
      }
    }
    SceneViewTransform sceneViewTransform = new SceneViewTransform(sceneView);
    ColorSet colorSet;
    if (sceneView.getModel().getType() == NlLayoutType.NAV) {
      colorSet = new NavColorSet();
    }
    else if (sceneView instanceof ScreenView && ((ScreenView)sceneView).getScreenViewType() == ScreenView.ScreenViewType.BLUEPRINT) {
      colorSet = new BlueprintColorSet();
    }
    else {
      colorSet = new AndroidColorSet();
    }
    sceneViewTransform.myColorSet = colorSet;

    cache.put(sceneView, new SoftReference<>(sceneViewTransform));
    return sceneViewTransform;
  }

  private static WeakHashMap<SceneView, SoftReference<SceneViewTransform>> cache = new WeakHashMap<>();

  @Nullable
  public DesignSurface getSurface() {
    return null;
  }

  /**
   * Set the tool tip text to be displayed if no one moves.
   * @param toolTip
   */
  public void setToolTip(@Nullable String toolTip) {
  }

  public float pxToDp(int px) {
    return px * Coordinates.DEFAULT_DENSITY;
  }

  public boolean showOnlySelection() {
    return myShowOnlySelection;
  }

  /**
   * The  SceneContext based on a ScreenView
   */
  private static class SceneViewTransform extends SceneContext {
    SceneView mySceneView;

    public SceneViewTransform(SceneView sceneView) {
      mySceneView = sceneView;
    }

    @Override
    public void setToolTip(@Nullable String toolTip) {
      mySceneView.setToolTip(toolTip);
    }

    @NotNull
    @Override
    public DesignSurface getSurface() {
      return mySceneView.getSurface();
    }

    @Override
    public double getScale() { return mySceneView.getScale(); }

    @Override
    @SwingCoordinate
    public int getSwingX(@AndroidDpCoordinate float x) {
      return Coordinates.getSwingX(mySceneView, Coordinates.dpToPx(mySceneView, x));
    }

    @Override
    @SwingCoordinate
    public int getSwingY(@AndroidDpCoordinate float y) {
      return Coordinates.getSwingY(mySceneView, Coordinates.dpToPx(mySceneView, y));
    }

    @Override
    @AndroidDpCoordinate
    public float pxToDp(@AndroidCoordinate int px) {
      return Coordinates.pxToDp(mySceneView, px);
    }

    @Override
    public void repaint() {
      mySceneView.getSurface().repaint();
    }

    @Override
    @SwingCoordinate
    public int getSwingDimension(@AndroidDpCoordinate float dim) {
      return Coordinates.getSwingDimension(mySceneView, Coordinates.dpToPx(mySceneView, dim));
    }
  }
}
