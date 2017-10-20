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

import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.sherpa.drawing.ColorSet;
import junit.framework.TestCase;
import org.mockito.InOrder;

import java.awt.*;

import static org.mockito.Mockito.*;

public class DrawActionHandleDragTest extends TestCase {
  private static final Color COLOR = Color.WHITE;
  private static final int X = 10;
  private static final int Y = 10;
  private static final int R = 5;
  private static final int MAX_DISTANCE = 5;

  public void testDrawActionHandleDrag() {
    for (int x = X - MAX_DISTANCE; x <= X + MAX_DISTANCE; x++) {
      for (int y = Y - MAX_DISTANCE; y <= Y + MAX_DISTANCE; y++) {
        verifyDrawActionHandleDrag(x, y);
      }
    }
  }

  private static void verifyDrawActionHandleDrag(int mouseX, int mouseY) {
    SceneContext sceneContext = mock(SceneContext.class);
    Graphics2D g = mock(Graphics2D.class);
    when(g.create()).thenReturn(g);
    DrawActionHandleDrag drawActionHandleDrag = new DrawActionHandleDrag(X, Y, R);
    ColorSet colorSet = mock(ColorSet.class);

    when(colorSet.getSelectedFrames()).thenReturn(COLOR);
    when(sceneContext.getMouseX()).thenReturn(mouseX);
    when(sceneContext.getMouseY()).thenReturn(mouseY);
    when(sceneContext.getColorSet()).thenReturn(colorSet);

    InOrder inOrder = inOrder(g);
    drawActionHandleDrag.paint(g, sceneContext);

    inOrder.verify(g).setColor(COLOR);
    inOrder.verify(g).fillOval(X - R, Y - R, 2 * R, 2 * R);

    inOrder.verify(g).setStroke(DrawActionHandleDrag.STROKE);
    inOrder.verify(g).drawLine(X, Y, mouseX, mouseY);
  }
}
