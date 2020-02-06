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
package com.android.tools.idea.common.surface;

import com.android.annotations.concurrency.GuardedBy;
import com.android.resources.ScreenRound;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.configurations.Configuration;
import com.google.common.collect.ImmutableList;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * View of a {@link Scene} used in a {@link DesignSurface}.
 */
public abstract class SceneView {
  @NotNull private final DesignSurface mySurface;
  @NotNull private final SceneManager myManager;
  private final Object myLayersCacheLock = new Object();
  @GuardedBy("myLayersCacheLock")
  private ImmutableList<Layer> myLayersCache;
  @SwingCoordinate private int x;
  @SwingCoordinate private int y;

  /**
   * A {@link SceneContext} which offers the rendering and/or picking information for this {@link SceneView}
   */
  @NotNull private final SceneContext myContext = new SceneViewTransform();

  public SceneView(@NotNull DesignSurface surface, @NotNull SceneManager manager) {
    mySurface = surface;
    myManager = manager;
  }

  @NotNull
  protected abstract ImmutableList<Layer> createLayers();

  /**
   * If Layers are not exist, they will be created by {@link #createLayers()}. This should happen only once.
   */
  @NotNull
  private ImmutableList<Layer> getLayers() {
    synchronized (myLayersCacheLock) {
      if (myLayersCache == null) {
        myLayersCache = createLayers();
      }
      return myLayersCache;
    }
  }

  @NotNull
  public Scene getScene() {
    return myManager.getScene();
  }

  /**
   * Returns the current size of the view. This is the same as {@link #getPreferredSize()} but accounts for the current zoom level.
   *
   * @param dimension optional existing {@link Dimension} instance to be reused. If not null, the values will be set and this instance
   *                  returned.
   */
  @NotNull
  @SwingCoordinate
  public Dimension getSize(@Nullable Dimension dimension) {
    if (dimension == null) {
      dimension = new Dimension();
    }

    Dimension preferred = getPreferredSize(dimension);
    double scale = getScale();

    dimension.setSize((int)(scale * preferred.width), (int)(scale * preferred.height));
    return dimension;
  }

  @NotNull
  public Dimension getPreferredSize() {
    return getPreferredSize(null);
  }

  /**
   * Returns the current size of the view. This is the same as {@link #getPreferredSize()} but accounts for the current zoom level.
   */
  @NotNull
  @SwingCoordinate
  public Dimension getSize() {
    return getSize(null);
  }

  /**
   * Get the height of the name label which displays the name on the top of SceneView.
   * The value is sum of text height and space between text and SceneView.
   */
  abstract public int getNameLabelHeight();

  @NotNull
  abstract public Dimension getPreferredSize(@Nullable Dimension dimension);

  @NotNull
  public Configuration getConfiguration() {
    return getSceneManager().getModel().getConfiguration();
  }

  /**
   * @deprecated This method will be removed in the future. The Model should be obtained by {@link SceneManager#getModel()} instead.
   */
  @Deprecated
  @NotNull
  public NlModel getModel() {
    return myManager.getModel();
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return getSurface().getSelectionModel();
  }

  /**
   * Returns null if the screen is rectangular; if not, it returns a shape (round for AndroidWear etc)
   */
  @Nullable
  public Shape getScreenShape() {
    Device device = getConfiguration().getDevice();
    if (device == null) {
      return null;
    }

    Screen screen = device.getDefaultHardware().getScreen();
    if (screen.getScreenRound() != ScreenRound.ROUND) {
      return null;
    }

    Dimension size = getSize();

    int chin = screen.getChin();
    int originX = getX();
    int originY = getY();
    if (chin == 0) {
      // Plain circle
      return new Ellipse2D.Double(originX, originY, size.width, size.height);
    }
    else {
      int height = size.height * chin / screen.getYDimension();
      Area a1 = new Area(new Ellipse2D.Double(originX, originY, size.width, size.height + height));
      Area a2 = new Area(new Rectangle2D.Double(originX, originY + 2 * (size.height + height) - height, size.width, height));
      a1.subtract(a2);
      return a1;
    }
  }

  @NotNull
  public DesignSurface getSurface() {
    return mySurface;
  }

  public double getScale() {
    return getSurface().getScale();
  }

  public float getSceneScalingFactor() {
    return getSceneManager().getSceneScalingFactor();
  }

  public final void setLocation(@SwingCoordinate int screenX, @SwingCoordinate int screenY) {
    x = screenX;
    y = screenY;
  }

  @SwingCoordinate
  public int getX() {
    return x;
  }

  @SwingCoordinate
  public int getY() {
    return y;
  }

  /**
   * Custom translation to apply when converting between android coordinate space and swing coordinate space.
   */
  @SwingCoordinate
  public int getContentTranslationX() {
    return 0;
  }

  /**
   * Custom translation to apply when converting between android coordinate space and swing coordinate space.
   */
  @SwingCoordinate
  public int getContentTranslationY() {
    return 0;
  }

  @NotNull
  public SceneManager getSceneManager() {
    return myManager;
  }

  @NotNull
  public abstract ColorSet getColorSet();

  @NotNull
  final public SceneContext getContext() {
    return myContext;
  }

  /**
   * Called when {@link DesignSurface#updateUI()} is called.
   */
  public void updateUI() {
  }

  /**
   * Called by the surface when the {@link SceneView} needs to be painted
   * @param graphics
   */
  final void paint(@NotNull Graphics2D graphics) {
    for (Layer layer : getLayers()) {
      if (layer.isVisible()) {
        layer.paint(graphics);
      }
    }
  }

  /**
   * Called when a drag operation starts on the {@link DesignSurface}
   */
  final void onDragStart() {
    for (Layer layer : getLayers()) {
      if (layer instanceof SceneLayer) {
        SceneLayer sceneLayer = (SceneLayer)layer;
        if (sceneLayer.isShowOnHover()) {
          sceneLayer.setShowOnHover(true);
        }
      }
    }
  }

  /**
   * Called when a drag operation ends on the {@link DesignSurface}
   */
  final void onDragEnd() {
    for (Layer layer : getLayers()) {
      if (layer instanceof SceneLayer) {
        SceneLayer sceneLayer = (SceneLayer)layer;
        if (sceneLayer.isShowOnHover()) {
          sceneLayer.setShowOnHover(false);
        }
      }
    }
  }

  public void dispose() {
    synchronized (myLayersCacheLock) {
      if (myLayersCache != null) {
        // TODO(b/148936113)
        myLayersCache.forEach(Layer::dispose);
      }
    }
  }

  /**
   * Called by the {@link DesignSurface} on mouse hover. The coordinates might be outside of the boundaries of this {@link SceneView}
   */
  final void onHover(@SwingCoordinate int mouseX, @SwingCoordinate int mouseY) {
    for (Layer layer : getLayers()) {
      layer.onHover(mouseX, mouseY);
    }
  }

  /**
   * Set the ConstraintsLayer and SceneLayer layers to paint, even if they are set to paint only on mouse hover
   *
   * @param value if true, force painting
   */
  public final void setForceLayersRepaint(boolean value) {
    for (Layer layer : getLayers()) {
      if (layer instanceof SceneLayer) {
        SceneLayer sceneLayer = (SceneLayer)layer;
        sceneLayer.setTemporaryShow(value);
      }
    }
  }

  /**
   * The {@link SceneContext} based on a {@link SceneView}.
   *
   * TODO: b/140160277
   *   For historical reason we put the Coordinate translation in {@link SceneContext} instead of using
   *   {@link SceneView} directly. Maybe we can remove {@link SceneContext} and just use {@link SceneView} only.
   */
  private class SceneViewTransform extends SceneContext {

    private SceneViewTransform() {
    }

    @Override
    @NotNull
    public ColorSet getColorSet() {
      return SceneView.this.getColorSet();
    }

    @NotNull
    @Override
    public DesignSurface getSurface() {
      return SceneView.this.getSurface();
    }

    @Override
    public double getScale() {
      return SceneView.this.getScale();
    }

    @Override
    @SwingCoordinate
    public int getSwingXDip(@AndroidDpCoordinate float x) {
      return Coordinates.getSwingX(SceneView.this, Coordinates.dpToPx(SceneView.this, x));
    }

    @Override
    @SwingCoordinate
    public int getSwingYDip(@AndroidDpCoordinate float y) {
      return Coordinates.getSwingY(SceneView.this, Coordinates.dpToPx(SceneView.this, y));
    }

    @Override
    @SwingCoordinate
    public int getSwingX(@AndroidCoordinate int x) {
      return Coordinates.getSwingX(SceneView.this, x);
    }

    @Override
    @SwingCoordinate
    public int getSwingY(@AndroidCoordinate int y) {
      return Coordinates.getSwingY(SceneView.this, y);
    }

    @Override
    @AndroidDpCoordinate
    public float pxToDp(@AndroidCoordinate int px) {
      return Coordinates.pxToDp(SceneView.this, px);
    }

    @Override
    public void repaint() {
      getSurface().needsRepaint();
    }

    @Override
    @SwingCoordinate
    public int getSwingDimensionDip(@AndroidDpCoordinate float dim) {
      return Coordinates.getSwingDimension(SceneView.this, Coordinates.dpToPx(SceneView.this, dim));
    }

    @Override
    @SwingCoordinate
    public int getSwingDimension(@AndroidCoordinate int dim) {
      return Coordinates.getSwingDimension(SceneView.this, dim);
    }
  }
}
