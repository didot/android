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
package com.android.tools.idea.common.scene;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.BlueprintColorSet;
import com.intellij.reference.SoftReference;
import java.awt.Rectangle;
import java.util.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This represents the Transform between dp to screen space.
 * There are two ways to create it get() or get(ScreenView)
 */
public class SceneContext {
  ColorSet myColorSet;
  // Picker is used to record all graphics drawn to support selection
  final ScenePicker myGraphicsPicker = new ScenePicker();
  Object myFoundObject;
  Long myTime;
  @SwingCoordinate private int myMouseX = -1;
  @SwingCoordinate private int myMouseY = -1;
  private boolean myShowOnlySelection = false;
  @NotNull
  @SwingCoordinate
  private Rectangle myRenderableBounds = new Rectangle();

  private SceneContext() {
    myTime = System.currentTimeMillis();
    myGraphicsPicker.setSelectListener((over, dist)->{
      myFoundObject = over;
    });
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
  public int getSwingXDip(@AndroidDpCoordinate float x) {
    return (int) x;
  }

  @SwingCoordinate
  public int getSwingYDip(@AndroidDpCoordinate float y) {
    return (int) y;
  }

  @SwingCoordinate
  public int getSwingX(@AndroidCoordinate int x) {
    return x;
  }

  @SwingCoordinate
  public int getSwingY(@AndroidCoordinate int y) {
    return y;
  }

  @SwingCoordinate
  public int getSwingDimensionDip(@AndroidDpCoordinate float dim) {
    return (int) dim;
  }

  @SwingCoordinate
  public int getSwingDimension(@AndroidCoordinate int dim) {
    return dim;
  }

  public ColorSet getColorSet() {
    return myColorSet;
  }

  public ScenePicker getScenePicker() {
    return myGraphicsPicker;
  }

  /**
   * Find objects drawn on the scene.
   * Objects drawn with this sceneContext can record there shapes
   * and this find can be used to detect them.
   * @param x
   * @param y
   * @return
   */
  public Object findClickedGraphics(@SwingCoordinate int x, @SwingCoordinate int y) {
    myFoundObject = null;
    myGraphicsPicker.find(x, y);
    return myFoundObject;
  }

  public double getScale() { return 1; }

  public void setRenderableBounds(@NotNull @SwingCoordinate Rectangle bounds) {
    myRenderableBounds.setBounds(bounds);
  }

  @NotNull
  @SwingCoordinate
  public Rectangle getRenderableBounds() {
    return myRenderableBounds;
  }

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
    sceneViewTransform.myColorSet = sceneView.getColorSet();

    cache.put(sceneView, new SoftReference<>(sceneViewTransform));
    return sceneViewTransform;
  }

  private static WeakHashMap<SceneView, SoftReference<SceneViewTransform>> cache = new WeakHashMap<>();

  @Nullable
  public DesignSurface getSurface() {
    return null;
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

    @NotNull
    @Override
    public DesignSurface getSurface() {
      return mySceneView.getSurface();
    }

    @Override
    public double getScale() { return mySceneView.getScale(); }

    @Override
    @SwingCoordinate
    public int getSwingXDip(@AndroidDpCoordinate float x) {
      return Coordinates.getSwingX(mySceneView, Coordinates.dpToPx(mySceneView, x));
    }

    @Override
    @SwingCoordinate
    public int getSwingYDip(@AndroidDpCoordinate float y) {
      return Coordinates.getSwingY(mySceneView, Coordinates.dpToPx(mySceneView, y));
    }

    @Override
    @SwingCoordinate
    public int getSwingX(@AndroidCoordinate int x) {
      return Coordinates.getSwingX(mySceneView, x);
    }

    @Override
    @SwingCoordinate
    public int getSwingY(@AndroidCoordinate int y) {
      return Coordinates.getSwingY(mySceneView, y);
    }

    @Override
    @AndroidDpCoordinate
    public float pxToDp(@AndroidCoordinate int px) {
      return Coordinates.pxToDp(mySceneView, px);
    }

    @Override
    public void repaint() {
      mySceneView.getSurface().needsRepaint();
    }

    @Override
    @SwingCoordinate
    public int getSwingDimensionDip(@AndroidDpCoordinate float dim) {
      return Coordinates.getSwingDimension(mySceneView, Coordinates.dpToPx(mySceneView, dim));
    }

    @Override
    @SwingCoordinate
    public int getSwingDimension(@AndroidCoordinate int dim) {
      return Coordinates.getSwingDimension(mySceneView, dim);
    }
  }
}
