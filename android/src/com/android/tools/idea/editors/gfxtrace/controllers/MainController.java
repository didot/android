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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.execution.ui.layout.impl.ViewImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class MainController extends Controller {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new MainController(editor).myPanel;
  }

  @NotNull private JBPanel myPanel = new JBPanel(new BorderLayout());
  @NotNull private final RunnerLayoutUi myLayoutUi;
  @NotNull private final Content myAtomTab;
  @NotNull private final Content myStateTab;
  @NotNull private final Content myMemoryTab;
  @NotNull private final Content myGeoTab;

  private MainController(@NotNull GfxTraceEditor editor) {
    super(editor);

    JBLabel experimentalBanner = new JBLabel() {{
      setText("The GPU Debugger is currently in beta.");
      setIcon(AllIcons.General.BalloonWarning);
      setBackground(new JBColor(0xffee88, 0xa49152));
      setBorder(JBUI.Borders.empty(0, 10));
      setOpaque(true);
    }};

    experimentalBanner.setVisible(false);
    myEditor.addConnectionListener((server) -> experimentalBanner.setVisible(!server.getFeatures().isStable()));

    JBPanel top = new JBPanel(new GridLayout(2, 1));
    top.add(experimentalBanner);
    top.add(ContextController.createUI(editor));
    myPanel.add(top, BorderLayout.NORTH);

    // ThreeComponentsSplitter instead of JBSplitter as it lets us set an exact size for the first component.
    ThreeComponentsSplitter splitter = new ThreeComponentsSplitter(true) {
      @Override
      public void setBounds(int x, int y, int width, int height) {
        // for some strange reason Editors are resized to 0 when you switch away from them.
        // so we ignore resize to size 0 as that will set any divider positions to 0
        if (width > 0 && height > 0) {
          super.setBounds(x, y, width, height);
        }
      }
    };
    Disposer.register(this, splitter);
    myPanel.add(splitter, BorderLayout.CENTER);
    splitter.setDividerWidth(5);

    // Add the scrubber view to the top panel.
    splitter.setFirstComponent(ScrubberController.createUI(editor));
    splitter.setFirstSize(150);

    // Configure the image tabs.
    // we use RunnerLayoutUi to allow the user to drag the tabs out of the JBRunnerTabs
    myLayoutUi = RunnerLayoutUi.Factory.getInstance(editor.getProject()).create("gfx-trace-runnerId", editor.getName(), editor.getSessionName(), this);
    myAtomTab = addTab(myLayoutUi, AtomController.createUI(editor), "GPU Commands", PlaceInGrid.left);
    addTab(myLayoutUi, FrameBufferController.createUI(editor), "Framebuffer", PlaceInGrid.center);
    addTab(myLayoutUi, TexturesController.createUI(editor), "Textures", PlaceInGrid.center);
    myGeoTab = addTab(myLayoutUi, GeometryController.createUI(editor), "Geometry", PlaceInGrid.center);
    myStateTab = addTab(myLayoutUi, StateController.createUI(editor), "GPU State", PlaceInGrid.center);
    myMemoryTab = addTab(myLayoutUi, MemoryController.createUI(editor), "Memory", PlaceInGrid.center);

    splitter.setLastComponent(myLayoutUi.getComponent());

    // we need to make sure that none of the ThreeComponentsSplitter have a size 0
    // or we can risk either setting, or saving a size of 0 for one of its children
    // when GridImpl.addNotify or RunnerContentUi.MyComponent.removeNotify happens
    setInitialSizeRecursive(splitter);
  }

  private static Content addTab(@NotNull RunnerLayoutUi layoutUi, @NotNull JComponent component, @NotNull String name, @NotNull PlaceInGrid defaultPlace) {
    Content content = layoutUi.createContent(name + "-contentId", component, name, null, null);
    content.setCloseable(false);
    layoutUi.addContent(content, -1, defaultPlace, false);
    return content;
  }

  private static void setInitialSizeRecursive(Component component) {
    if (component instanceof ThreeComponentsSplitter) {
      // these values are almost never used by anyone,
      // but in the very rare event that we save the size
      // before we restore the correct value, it means we never
      // end up saving 0 as the size.
      component.setSize(1000, 500);
    }
    if (component instanceof Container) {
      for (Component comp : ((Container)component).getComponents()) {
        setInitialSizeRecursive(comp);
      }
    }
  }

  @Override
  public void notifyPath(PathEvent event) {
    if (event.findTypedMemoryPath() != null || event.findMemoryPath() != null) {
      select(myMemoryTab);
    }
    else if (event.findStatePath() != null) {
      select(myStateTab);
    }
    else if (event.findAtomPath() != null) {
      select(myAtomTab);
    }
  }

  private void select(Content content) {
    restoreIfMinimized(myLayoutUi, content);
    myLayoutUi.selectAndFocus(content, true, true, true);
  }

  /**
   * @see XWatchesViewImpl#showWatchesTab(XDebugSessionImpl)
   */
  public static void restoreIfMinimized(RunnerLayoutUi layoutUi, Content content) {
    JComponent component = layoutUi.getComponent();
    if (component instanceof DataProvider) {
      RunnerContentUi ui = RunnerContentUi.KEY.getData(((DataProvider)component));
      if (ui != null) {
        ui.restoreContent(content.getUserData(ViewImpl.ID));
      }
    }
  }

  @Override
  public void clear() {
    myPanel.removeAll();
  }
}
