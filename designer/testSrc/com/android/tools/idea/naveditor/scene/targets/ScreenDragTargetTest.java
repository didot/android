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

import com.android.SdkConstants;
import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.naveditor.surface.NavView;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.surface.InteractionManager;
import com.android.tools.idea.uibuilder.surface.SceneView;
import org.jetbrains.android.dom.navigation.NavigationSchema;

import static java.awt.event.MouseEvent.BUTTON1;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ScreenDragTarget}
 */
public class ScreenDragTargetTest extends NavigationTestCase {

  public void testMove() throws Exception {
    ComponentDescriptor root = component(NavigationSchema.TAG_NAVIGATION)
      .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/fragment2")
      .unboundedChildren(
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment1")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment2")),
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment2")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main2"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    NavDesignSurface surface = (NavDesignSurface)model.getSurface();
    SceneView view = new NavView(surface, model);
    when(surface.getCurrentSceneView()).thenReturn(view);
    when(surface.getSceneView(anyInt(), anyInt())).thenReturn(view);

    Scene scene = model.getSurface().getScene();
    scene.layout(0, SceneContext.get());

    SceneComponent component = scene.getSceneComponent("fragment2");
    InteractionManager interactionManager = new InteractionManager(surface);
    interactionManager.registerListeners();

    @AndroidDpCoordinate int x = component.getDrawX();
    @AndroidDpCoordinate int y = component.getDrawY();

    LayoutTestUtilities.pressMouse(interactionManager, BUTTON1, Coordinates.getSwingXDip(view, x + 10),
                                   Coordinates.getSwingYDip(view, y + 10), 0);
    LayoutTestUtilities.dragMouse(interactionManager, Coordinates.getSwingXDip(view, x), Coordinates.getSwingYDip(view, y),
                                  Coordinates.getSwingXDip(view, x + 30), Coordinates.getSwingYDip(view, y + 40), 0);
    LayoutTestUtilities.releaseMouse(interactionManager, BUTTON1, Coordinates.getSwingXDip(view, x + 30),
                                     Coordinates.getSwingYDip(view, y + 40), 0);

    assertEquals(x + 20, component.getDrawX());
    assertEquals(y + 30, component.getDrawY());
    interactionManager.unregisterListeners();

  }
}
