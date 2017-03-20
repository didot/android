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
package com.android.tools.idea.uibuilder.surface;

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.resources.ScreenRound;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneManager;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * View of a {@link Scene} used in a {@link DesignSurface}.
 */
public abstract class SceneView {
  protected final DesignSurface mySurface;
  protected final NlModel myModel;

  public SceneView(@NotNull DesignSurface surface, @NotNull NlModel model) {
    mySurface = surface;
    myModel = model;
    myModel.getSelectionModel().addListener((m, selection) -> ApplicationManager.getApplication().invokeLater(mySurface::repaint));
  }

  @NotNull
  public Scene getScene() {
    return mySurface.getScene();
  }

  /**
   * Returns the current size of the view. This is the same as {@link #getPreferredSize()} but accounts for the current zoom level.
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
    double scale = mySurface.getScale();

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

  @NotNull
  abstract public Dimension getPreferredSize(@Nullable Dimension dimension);

  public void switchDevice() {
    List<Device> devices = ConfigurationManager.getOrCreateInstance(myModel.getModule()).getDevices();
    List<Device> applicable = Lists.newArrayList();
    for (Device device : devices) {
      if (HardwareConfigHelper.isNexus(device)) {
        applicable.add(device);
      }
    }
    Configuration configuration = getConfiguration();
    Device currentDevice = configuration.getDevice();
    for (int i = 0, n = applicable.size(); i < n; i++) {
      if (applicable.get(i) == currentDevice) {
        Device newDevice = applicable.get((i + 1) % applicable.size());
        configuration.setDevice(newDevice, true);
        break;
      }
    }
  }

  public void toggleOrientation() {
    Configuration configuration = getConfiguration();
    configuration.getDeviceState();

    State current = configuration.getDeviceState();
    State flip = configuration.getNextDeviceState(current);
    if (flip != null) {
      configuration.setDeviceState(flip);
    }
  }

  @NotNull
  public Configuration getConfiguration() {
    return myModel.getConfiguration();
  }

  @NotNull
  public NlModel getModel() {
    return myModel;
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    // For now, the selection model is tied to the model itself.
    // This is deliberate: rather than having each view have its own
    // independent selection, when a file is shown multiple times on the screen,
    // selection is "synchronized" between the views by virtue of them all
    // sharing the same selection model, currently stashed in the model itself.
    return myModel.getSelectionModel();
  }

  /** Returns null if the screen is rectangular; if not, it returns a shape (round for AndroidWear etc) */
  @Nullable
  public Shape getScreenShape(int originX, int originY) {
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
    if (chin == 0) {
      // Plain circle
      return new Ellipse2D.Double(originX, originY, size.width, size.height);
    } else {
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
    return mySurface.getScale();
  }

  @SwingCoordinate
  public int getX() {
    return 0;
  }

  @SwingCoordinate
  public int getY() {
    return 0;
  }

  public void updateCursor(@SwingCoordinate int x, @SwingCoordinate int y) {
    getScene().mouseHover(SceneContext.get(this), Coordinates.getAndroidXDip(this, x), Coordinates.getAndroidYDip(this, y));
    mySurface.setCursor(getScene().getMouseCursor());
  }

  public SceneManager getSceneManager() {
    return mySurface.getSceneManager();
  }

  /**
   * Sets the tool tip to be shown
   * @param toolTip
   */
  public void setToolTip(String toolTip) {
     mySurface.setDesignToolTip(toolTip);
  }
}
