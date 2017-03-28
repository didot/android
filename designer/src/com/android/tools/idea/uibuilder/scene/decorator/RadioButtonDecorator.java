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
package com.android.tools.idea.uibuilder.scene.decorator;

import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintUtilities; // TODO: remove
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawTextRegion;
import com.android.tools.sherpa.drawing.ColorSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Support Buttons
 */
public class RadioButtonDecorator extends SceneDecorator {
  public static class DrawRadioButton extends DrawTextRegion {
    int[] xp = new int[3];
    int[] yp = new int[3];

    @Override
    public int getLevel() {
      return COMPONENT_LEVEL;
    }

    DrawRadioButton(@SwingCoordinate int x,
                    @SwingCoordinate int y,
                    @SwingCoordinate int width,
                    @SwingCoordinate int height,
                    int baselineOffset,
                    float scale,
                    String text) {
      super(x, y, width, height, baselineOffset, text, true, false, DrawTextRegion.TEXT_ALIGNMENT_VIEW_START,
            DrawTextRegion.TEXT_ALIGNMENT_CENTER, 32, scale);
    }

    public DrawRadioButton(String s) {
      String[] sp = s.split(",");
      int c = 0;
      x = Integer.parseInt(sp[c++]);
      y = Integer.parseInt(sp[c++]);
      width = Integer.parseInt(sp[c++]);
      height = Integer.parseInt(sp[c++]);
      myBaseLineOffset = Integer.parseInt(sp[c++]);
      mScale = java.lang.Float.parseFloat(sp[c++]);
      mFont = mFont.deriveFont(mFont.getSize() * mScale);
      mText = s.substring(s.indexOf('\"') + 1, s.lastIndexOf('\"'));
    }

    @Override
    public String serialize() {
      return this.getClass().getSimpleName() +
             "," +
             x +
             "," +
             y +
             "," +
             width +
             "," +
             height +
             "," +
             myBaseLineOffset +
             "," +
             mScale +
             ",\"" +
             mText +
             "\"";
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      int margin = height / 5;
      mHorizontalPadding = height;
      super.paint(g, sceneContext);
      ColorSet colorSet = sceneContext.getColorSet();
      if (colorSet.drawBackground()) {
        Stroke stroke = g.getStroke();
        g.setStroke(new BasicStroke(2));
        g.setColor(colorSet.getFakeUI());
        int side = height - margin * 2;
        g.drawRoundRect(x + margin, y + margin, side, side, side, side);
        g.setStroke(stroke);
      }
    }
  }

  @Override
  public void addContent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    super.addContent(list, time, sceneContext, component);
    @AndroidDpCoordinate Rectangle rect = new Rectangle();
    component.fillDrawRect(time, rect);
    @SwingCoordinate int l = sceneContext.getSwingX(rect.x);
    @SwingCoordinate int t = sceneContext.getSwingY(rect.y);
    @SwingCoordinate int w = sceneContext.getSwingDimension(rect.width);
    @SwingCoordinate int h = sceneContext.getSwingDimension(rect.height);
    String text = ConstraintUtilities.getResolvedText(component.getNlComponent());
    int baseLineOffset = sceneContext.getSwingDimension(component.getBaseline());
    float scale = (float)sceneContext.getScale();
    list.add(new DrawRadioButton(l, t, w, h, baseLineOffset, scale, text));
  }
}
