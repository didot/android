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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationHolder;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.editor.AnimatedComponentSplitter;
import com.android.tools.idea.uibuilder.mockup.editor.MockupEditor;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightFillLayout;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Assembles a designer editor from various components
 */
public class NlEditorPanel extends JPanel implements DesignerEditorPanelFacade {
  private final XmlFile myFile;
  private final DesignSurface mySurface;
  private final ThreeComponentsSplitter myContentSplitter;

  public NlEditorPanel(@NotNull NlEditor editor, @NotNull AndroidFacet facet, @NotNull VirtualFile file) {
    super(new BorderLayout());
    setOpaque(true);

    final Project project = facet.getModule().getProject();
    myFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(project, file);
    assert myFile != null : file;

    mySurface = new DesignSurface(project, this);
    Disposer.register(editor, mySurface);
    NlModel model = NlModel.create(mySurface, editor, facet, myFile);
    mySurface.setModel(model);


    JPanel contentPanel = new JPanel(new BorderLayout());
    JComponent toolbarComponent = mySurface.getActionManager().createToolbar(model);
    contentPanel.add(toolbarComponent, BorderLayout.NORTH);
    contentPanel.add(mySurface);

    // Hold the surface and MockupEditor
    AnimatedComponentSplitter surfaceMockupWrapper = new AnimatedComponentSplitter(false, true);
    surfaceMockupWrapper.setInnerComponent(contentPanel);

    if (Mockup.ENABLE_FEATURE) {
      MockupEditor mockupEditor = new MockupEditor(mySurface, model);
      mySurface.setMockupEditor(mockupEditor);
      surfaceMockupWrapper.setLastComponent(mockupEditor);
    }

    Disposer.register(editor, surfaceMockupWrapper);

    /**
     * Needed so the inner child of my ContentSplitter has LightFillLayout Manager
     * The {@link LightFillLayout} provides the UI for the minimized forms of the {@link LightToolWindow}
     * used for the palette and the structure/properties panes.
     **/
    JPanel lightFillLayoutPanel = new JPanel(new LightFillLayout());
    lightFillLayoutPanel.add(new JComponent() {
    }); // Need an empty action bar for the LightFillLayout otherwise it shrink on itself
    lightFillLayoutPanel.add(surfaceMockupWrapper);

    myContentSplitter = new ThreeComponentsSplitter();
    myContentSplitter.setDividerWidth(0);
    myContentSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"));
    myContentSplitter.setInnerComponent(lightFillLayoutPanel);
    myContentSplitter.setHonorComponentsMinimumSize(true);
    add(myContentSplitter, BorderLayout.CENTER);
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return mySurface.getPreferredFocusedComponent();
  }

  public void dispose() {
    NlPaletteManager.get(mySurface.getProject()).dispose(this);
    NlPropertiesWindowManager.get(mySurface.getProject()).dispose(this);
    Disposer.dispose(myContentSplitter);
  }

  public void activate() {
    mySurface.activate();
  }

  public void deactivate() {
    mySurface.deactivate();
  }

  @NotNull
  public XmlFile getFile() {
    return myFile;
  }

  @NotNull
  public DesignSurface getSurface() {
    return mySurface;
  }

  @Override
  public ThreeComponentsSplitter getContentSplitter() {
    return myContentSplitter;
  }

  /**
   * <b>Temporary</b> bridge to older Configuration actions. When we can ditch the old layout preview
   * and old layout editors, we no longer needs this level of indirection to let the configuration actions
   * talk to multiple different editor implementations, and the render actions can directly address DesignSurface.
   */
  public static class NlConfigurationHolder implements ConfigurationHolder {
    @NotNull private final DesignSurface mySurface;

    public NlConfigurationHolder(@NotNull DesignSurface surface) {
      mySurface = surface;
    }

    @Nullable
    @Override
    public Configuration getConfiguration() {
      return mySurface.getConfiguration();
    }
  }
}
