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
package com.android.tools.idea.rendering;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public class TestRenderingUtils {
  @NotNull
  public static BufferedImage getImageFromIcon(Icon icon) {
    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    icon.paintIcon(null, graphics, 0, 0);
    graphics.dispose();
    return image;
  }
}
