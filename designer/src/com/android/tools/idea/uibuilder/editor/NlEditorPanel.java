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

import com.android.tools.adtui.workbench.*;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.startup.DelayedInitialization;
import com.android.tools.idea.uibuilder.mockup.editor.MockupToolDefinition;
import com.android.tools.idea.uibuilder.model.NlLayoutType;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.palette.NlPaletteDefinition;
import com.android.tools.idea.uibuilder.property.NlPropertyPanelDefinition;
import com.android.tools.idea.uibuilder.structure.NlComponentTreeDefinition;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.rendering.RenderService.MOCKUP_EDITOR_ENABLED;

/**
 * Assembles a designer editor from various components
 */
public class NlEditorPanel extends WorkBench<DesignSurface> {
  private final AndroidFacet myFacet;
  private final NlEditor myEditor;
  private final XmlFile myFile;
  private final DesignSurface mySurface;
  private final JPanel myContentPanel;
  private boolean myIsActive;

  public NlEditorPanel(@NotNull NlEditor editor, @NotNull Project project, @NotNull AndroidFacet facet, @NotNull VirtualFile file) {
    super(project, "NELE_EDITOR", editor);
    setOpaque(true);

    myFacet = facet;
    myEditor = editor;
    myFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(project, file);
    assert myFile != null : file;
    myContentPanel = new JPanel(new BorderLayout());

    if (NlLayoutType.typeOf(myFile) == NlLayoutType.NAV) {
      mySurface = new NavDesignSurface(facet, editor);
    }
    else {
      mySurface = new NlDesignSurface(project, false, editor);
      ((NlDesignSurface)mySurface).setCentered(true);
    }

    setLoadingText("Wait for build to complete");
    DelayedInitialization.getInstance(project).runAfterBuild(this::initNeleModel, this::buildError);
  }

  // Build was either cancelled or there was an error
  private void buildError() {
    setLoadingText("Build error. Please fix any errors and retry building.");
  }

  private void initNeleModel() {
    UIUtil.invokeLaterIfNeeded(this::initNeleModelOnEventDispatchThread);
  }

  private void initNeleModelOnEventDispatchThread() {
    if (Disposer.isDisposed(myEditor) || myContentPanel.getComponentCount() > 0) {
      return;
    }
    Project project = myFacet.getModule().getProject();

    NlModel model = NlModel.create(mySurface, myEditor, myFacet, myFile);
    mySurface.setModel(model);
    Disposer.register(myEditor, mySurface);

    JComponent toolbarComponent = mySurface.getActionManager().createToolbar(model);
    myContentPanel.add(toolbarComponent, BorderLayout.NORTH);
    myContentPanel.add(mySurface);

    List<ToolWindowDefinition<DesignSurface>> tools = new ArrayList<>(4);
    tools.add(new NlPropertyPanelDefinition(myFacet, Side.RIGHT, Split.TOP, AutoHide.DOCKED));
    if (NlLayoutType.typeOf(myFile) != NlLayoutType.NAV) {
      tools.add(new NlPaletteDefinition(project, Side.LEFT, Split.TOP, AutoHide.DOCKED));
      tools.add(new NlComponentTreeDefinition(project, Side.LEFT, Split.BOTTOM, AutoHide.DOCKED));
      if (MOCKUP_EDITOR_ENABLED) {
        tools.add(new MockupToolDefinition(Side.RIGHT, Split.TOP, AutoHide.AUTO_HIDE));
      }
    }

    init(myContentPanel, mySurface, tools);
    if (myIsActive) {
      model.activate();
    }
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return mySurface.getPreferredFocusedComponent();
  }

  public void activate() {
    mySurface.activate();
    myIsActive = true;
  }

  public void deactivate() {
    mySurface.deactivate();
    myIsActive = false;
  }

  @NotNull
  public DesignSurface getSurface() {
    return mySurface;
  }
}
