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
package com.android.tools.idea.naveditor.surface;

import com.android.SdkConstants;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.surface.InteractionManager;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.intellij.openapi.fileEditor.FileEditorManager;

import javax.swing.*;
import java.awt.event.MouseEvent;

import static com.android.tools.idea.naveditor.NavModelBuilderUtil.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link NavDesignSurface}
 */
public class NavDesignSurfaceTest extends NavigationTestCase {

  public void testComponentActivated() throws Exception {
    NavDesignSurface surface = new NavDesignSurface(getProject(), getTestRootDisposable());
    SyncNlModel model = model("nav.xml", rootComponent()
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_main")
          .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME, "mytest.navtest.MainActivity"),
        fragmentComponent("fragment2")
          .withLayoutAttribute("activity_main2")
          .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME, "mytest.navtest.BlankFragment"))
    ).build();
    surface.setModel(model);
    surface.notifyComponentActivate(model.find("fragment1"));
    FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    assertEquals("activity_main.xml", editorManager.getOpenFiles()[0].getName());
    editorManager.closeFile(editorManager.getOpenFiles()[0]);
    surface.notifyComponentActivate(model.find("fragment2"));
    assertEquals("activity_main2.xml", editorManager.getOpenFiles()[0].getName());
  }

  public void testNoLayoutComponentActivated() throws Exception {
    NavDesignSurface surface = new NavDesignSurface(getProject(), getTestRootDisposable());
    SyncNlModel model = model("nav.xml", rootComponent()
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME, "mytest.navtest.MainActivity"),
        fragmentComponent("fragment2")
          .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME, "mytest.navtest.BlankFragment"))
    ).build();
    surface.setModel(model);
    surface.notifyComponentActivate(model.find("fragment1"));
    FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    assertEquals("MainActivity.java", editorManager.getOpenFiles()[0].getName());
    editorManager.closeFile(editorManager.getOpenFiles()[0]);
    surface.notifyComponentActivate(model.find("fragment2"));
    assertEquals("BlankFragment.java", editorManager.getOpenFiles()[0].getName());
  }

  public void testSubflowActivated() throws Exception {
    NavDesignSurface surface = new NavDesignSurface(getProject(), getTestRootDisposable());
    SyncNlModel model = model("nav.xml", rootComponent()
      .unboundedChildren(
        fragmentComponent("fragment1"),
        navigationComponent("subnav")
          .unboundedChildren(fragmentComponent("fragment2")))
    ).build();
    surface.setModel(model);
    assertEquals(model.getComponents().get(0), surface.getCurrentNavigation());
    NlComponent subnav = model.find("subnav");
    surface.notifyComponentActivate(subnav);
    assertEquals(subnav, surface.getCurrentNavigation());
  }

  public void testDoubleClickFragment() throws Exception {
    SyncNlModel model = model("nav.xml", rootComponent()
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_main"),
        fragmentComponent("fragment2")
          .withLayoutAttribute("activity_main2"))
    ).build();

    NavDesignSurface surface = (NavDesignSurface)model.getSurface();
    when(surface.getLayeredPane()).thenReturn(mock(JComponent.class));
    InteractionManager interactionManager = new InteractionManager(surface);
    interactionManager.registerListeners();

    SceneView view = new NavView(surface, model);
    when(surface.getSceneView(anyInt(), anyInt())).thenReturn(view);

    surface.getScene().layout(0, SceneContext.get(view));
    SceneComponent fragment = surface.getScene().getSceneComponent("fragment1");
    int x = Coordinates.getSwingXDip(view, fragment.getDrawX()) + 5;
    int y = Coordinates.getSwingXDip(view, fragment.getDrawY()) + 5;
    LayoutTestUtilities.clickMouse(interactionManager, MouseEvent.BUTTON1, 2, x, y, 0);

    verify(surface).notifyComponentActivate(eq(fragment.getNlComponent()), anyInt(), anyInt());
  }
}
