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

import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.naveditor.scene.draw.DrawActionHandle;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import junit.framework.TestCase;
import org.mockito.InOrder;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.idea.uibuilder.scene.draw.DrawCommand;

import java.util.ArrayList;

import java.awt.*;

import static org.mockito.Mockito.*;

public class ActionHandleTargetTest extends TestCase {
  private static int X = 10;
  private static int Y = 10;
  private static Color FRAMES = Color.RED;
  private static Color SELECTEDFRAMES = Color.GREEN;
  private static Color HIGHLIGHTEDFRAMES = Color.BLUE;
  private static Color BACKGROUND = Color.WHITE;

  private Scene myScene;
  private ColorSet myColorSet;
  private SceneContext mySceneContext;
  private SceneComponent mySceneComponent;
  private ActionHandleTarget myActionHandleTarget;

  private void setup() {
    myScene = mock(Scene.class);
    myColorSet = mock(ColorSet.class);
    when(myColorSet.getFrames()).thenReturn(FRAMES);
    when(myColorSet.getSelectedFrames()).thenReturn(SELECTEDFRAMES);
    when(myColorSet.getHighlightedFrames()).thenReturn(HIGHLIGHTEDFRAMES);
    when(myColorSet.getBackground()).thenReturn(BACKGROUND);

    mySceneContext = mock(SceneContext.class);
    when(mySceneContext.getColorSet()).thenReturn(myColorSet);
    when(mySceneContext.getSwingX(anyFloat())).thenReturn(X);
    when(mySceneContext.getSwingY(anyFloat())).thenReturn(Y);

    mySceneComponent = mock(SceneComponent.class);
    when(mySceneComponent.getScene()).thenReturn(myScene);
    when(mySceneComponent.isSelected()).thenReturn(false);
    when(mySceneComponent.getDrawState()).thenReturn(SceneComponent.DrawState.NORMAL);

    myActionHandleTarget = new ActionHandleTarget(mySceneComponent);
  }

  public void testActionHandle() {
    setup();
    verifyDrawCommands(0, 0, FRAMES);

    // hover over component
    when(mySceneComponent.getDrawState()).thenReturn(SceneComponent.DrawState.HOVER);
    verifyDrawCommands(0, DrawActionHandle.SMALL_RADIUS, HIGHLIGHTEDFRAMES);

    // hover over target
    myActionHandleTarget.setOver(true);
    verifyDrawCommands(DrawActionHandle.SMALL_RADIUS, DrawActionHandle.LARGE_RADIUS, HIGHLIGHTEDFRAMES);

    // move away from target
    myActionHandleTarget.setOver(false);
    verifyDrawCommands(DrawActionHandle.LARGE_RADIUS, DrawActionHandle.SMALL_RADIUS, HIGHLIGHTEDFRAMES);

    // move away from component
    when(mySceneComponent.getDrawState()).thenReturn(SceneComponent.DrawState.NORMAL);
    verifyDrawCommands(DrawActionHandle.SMALL_RADIUS, 0, FRAMES);

    // hover over component
    when(mySceneComponent.getDrawState()).thenReturn(SceneComponent.DrawState.HOVER);
    verifyDrawCommands(0, DrawActionHandle.SMALL_RADIUS, HIGHLIGHTEDFRAMES);

    // select component
    when(mySceneComponent.isSelected()).thenReturn(true);
    verifyDrawCommands(DrawActionHandle.SMALL_RADIUS, DrawActionHandle.SMALL_RADIUS, SELECTEDFRAMES);

    // hover over target
    myActionHandleTarget.setOver(true);
    verifyDrawCommands(DrawActionHandle.SMALL_RADIUS, DrawActionHandle.LARGE_RADIUS, SELECTEDFRAMES);

    // move away from component and target
    myActionHandleTarget.setOver(false);
    when(mySceneComponent.getDrawState()).thenReturn(SceneComponent.DrawState.NORMAL);
    verifyDrawCommands(DrawActionHandle.LARGE_RADIUS, DrawActionHandle.SMALL_RADIUS, SELECTEDFRAMES);

    // deselect component
    when(mySceneComponent.isSelected()).thenReturn(false);
    verifyDrawCommands(DrawActionHandle.SMALL_RADIUS, 0, FRAMES);
  }

  private void verifyDrawCommands(int initialRadius,
                                  int finalRadius,
                                  Color borderColor) {

    DisplayList displayList = new DisplayList();
    myActionHandleTarget.render(displayList, mySceneContext);
    ArrayList<DrawCommand> list = displayList.getCommands();
    assertEquals(list.size(), 1);
    DrawActionHandle testHandle = new DrawActionHandle(X, Y, initialRadius, finalRadius, borderColor, BACKGROUND);
    assertEquals(testHandle.serialize(), list.get(0).serialize());
  }
}
