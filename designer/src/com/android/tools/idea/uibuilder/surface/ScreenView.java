/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.ide.common.rendering.api.HardwareConfig;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * View of a device/screen/layout.
 * This is actually painted by {@link ScreenViewLayer}.
 */
public class ScreenView extends SceneView {
  private ScreenViewType myType;

  public enum ScreenViewType { NORMAL, BLUEPRINT }

  @SwingCoordinate private int x;
  @SwingCoordinate private int y;

  public ScreenView(NlDesignSurface surface, @NotNull ScreenViewType type, @NotNull NlModel model) {
    super(surface, model);
    myType = type;
  }

  /**
   * Returns the current type of this ScreenView
   */
  @NotNull
  public ScreenViewType getScreenViewType() { return myType; }

  /**
   * Set the type of this ScreenvVew

   * @param type ScreenViewType (NORMAL or BLUEPRINT)
   */
  public void setType(ScreenViewType type) {
    myType = type;
  }

  /**
   * Returns the current preferred size for the view.
   * @param dimension optional existing {@link Dimension} instance to be reused. If not null, the values will be set and this instance
   *                  returned.
   */
  @Override
  @NotNull
  public Dimension getPreferredSize(@Nullable Dimension dimension) {
    if (dimension == null) {
      dimension = new Dimension();
    }

    Configuration configuration = getConfiguration();
    Device device = configuration.getDevice();
    State state = configuration.getDeviceState();
    if (device != null && state != null) {
      HardwareConfig config =
        new HardwareConfigHelper(device).setOrientation(state.getOrientation()).getConfig();

      dimension.setSize(config.getScreenWidth(), config.getScreenHeight());
    }

    return dimension;
  }

  public void setLocation(@SwingCoordinate int screenX, @SwingCoordinate int screenY) {
    x = screenX;
    y = screenY;
  }

  @Override
  @SwingCoordinate
  public int getX() {
    return x;
  }

  @Override
  @SwingCoordinate
  public int getY() {
    return y;
  }

  @Nullable
  public Shape getScreenShape() {
    return getScreenShape(getX(), getY());
  }
}
