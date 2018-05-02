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
package com.android.tools.idea.naveditor.scene.draw;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.common.scene.draw.DrawCommandSerializationHelperKt;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;

import static com.android.tools.idea.naveditor.scene.NavDrawHelperKt.DRAW_NAV_SCREEN_LEVEL;
import static com.android.tools.idea.naveditor.scene.NavDrawHelperKt.setRenderingHints;

/**
 * {@link DrawCommand} that draws a screen in the navigation editor.
 */
public class DrawNavScreen extends NavBaseDrawCommand {
  @SwingCoordinate private final int myX;
  @SwingCoordinate private final int myY;
  @SwingCoordinate private final int myWidth;
  @SwingCoordinate private final int myHeight;
  @NotNull private final BufferedImage myImage;

  public DrawNavScreen(@SwingCoordinate int x, @SwingCoordinate int y, @SwingCoordinate int width, @SwingCoordinate int height,
                       @NotNull BufferedImage image) {
    myX = x;
    myY = y;
    myWidth = width;
    myHeight = height;
    myImage = image;
  }

  @Override
  public int getLevel() {
    return DRAW_NAV_SCREEN_LEVEL;
  }

  @Override
  public String serialize() {
    return DrawCommandSerializationHelperKt.buildString(getClass().getSimpleName(), myX, myY, myWidth, myHeight);
  }

  @Override
  protected void onPaint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    setRenderingHints(g);
    g.clipRect(myX, myY, myWidth, myHeight);
    // TODO: better scaling (similar to ScreenViewLayer)
    g.drawImage(myImage,  myX, myY, myWidth, myHeight, null);
  }
}
