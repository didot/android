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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DragDropInteraction;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JTreeFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Fixture wrapping the the layout editor for a particular file
 */
public class NlEditorFixture extends ComponentFixture<NlEditorFixture, Component> {
  private final IdeFrameFixture myFrame;
  private final DesignSurfaceFixture myDesignSurfaceFixture;
  private NlPropertyInspectorFixture myPropertyFixture;
  private ComponentDragAndDrop myDragAndDrop;

  public NlEditorFixture(@NotNull Robot robot, @NotNull IdeFrameFixture frame, @NotNull NlEditor editor) {
    super(NlEditorFixture.class, robot, editor.getComponent());
    myFrame = frame;
    myDesignSurfaceFixture = new DesignSurfaceFixture(robot, frame, editor.getComponent().getSurface());
    myDragAndDrop = new ComponentDragAndDrop(robot);
  }

  public void waitForRenderToFinish() {
    myDesignSurfaceFixture.waitForRenderToFinish();
  }

  @NotNull
  public NlComponentFixture findView(@NotNull String tag, int occurrence) {
    return myDesignSurfaceFixture.findView(tag, occurrence);
  }

  public void requireSelection(@NotNull List<NlComponentFixture> components) {
    myDesignSurfaceFixture.requireSelection(components);
  }

  public boolean hasRenderErrors() {
    return myDesignSurfaceFixture.hasRenderErrors();
  }

  public boolean errorPanelContains(@NotNull String errorText) {
    return myDesignSurfaceFixture.errorPanelContains(errorText);
  }

  @NotNull
  public NlPropertyInspectorFixture getPropertyInspector() {
    if (myPropertyFixture == null) {
      myPropertyFixture = new NlPropertyInspectorFixture(robot(), myFrame, NlPropertyInspectorFixture.create(robot()));
    }
    return myPropertyFixture;
  }

  @NotNull
  public NlEditorFixture dragComponentToSurface(@NotNull String path) {
    JTree tree = robot().finder().findByName("Palette Tree", JTree.class, true);
    new JTreeFixture(robot(), tree).drag(path);
    myDragAndDrop.drop(myDesignSurfaceFixture.target(), new Point(0, 0));
    return this;
  }
}
