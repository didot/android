/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.uipreview;


import com.android.tools.idea.configurations.*;
import com.android.tools.idea.rendering.RefreshRenderAction;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderedImage;
import com.android.tools.idea.rendering.SaveScreenshotAction;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.components.JBScrollPane;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLayoutPreviewToolWindowForm implements Disposable, ConfigurationListener, RenderContext,
                                                           ResourceFolderManager.ResourceFolderListener {
  private JPanel myContentPanel;
  private AndroidLayoutPreviewPanel myPreviewPanel;
  private JBScrollPane myScrollPane;
  private JPanel mySecondToolBarPanel;
  private JPanel myFirstToolbarPanel;
  private PsiFile myFile;
  private Configuration myConfiguration;
  private AndroidFacet myFacet;
  private final AndroidLayoutPreviewToolWindowManager myToolWindowManager;
  private final ActionToolbar myActionToolBar;

  public AndroidLayoutPreviewToolWindowForm(final Project project, AndroidLayoutPreviewToolWindowManager toolWindowManager) {
    Disposer.register(this, myPreviewPanel);

    myToolWindowManager = toolWindowManager;

    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new ZoomToFitAction());
    actionGroup.add(new ZoomActualAction());
    actionGroup.addSeparator();
    actionGroup.add(new ZoomInAction());
    actionGroup.add(new ZoomOutAction());
    actionGroup.addSeparator();
    actionGroup.add(new RefreshRenderAction(this));
    actionGroup.add(new SaveScreenshotAction(this));
    myActionToolBar = ActionManager.getInstance().createActionToolbar("LayoutPreview", actionGroup, true);
    myActionToolBar.setReservePlaceAutoPopupIcon(false);

    ActionToolbar optionsToolBar = RenderOptionsMenuBuilder.create(this, project).addHideOption().addDeviceFrameOption()
      .addRetinaOption().build();
    JComponent toolbar = myActionToolBar.getComponent();
    ConfigurationToolBar configToolBar = new ConfigurationToolBar(this);
    myFirstToolbarPanel.add(configToolBar, BorderLayout.CENTER);
    mySecondToolBarPanel.add(optionsToolBar.getComponent(), BorderLayout.EAST);
    mySecondToolBarPanel.add(toolbar, BorderLayout.CENTER);

    myContentPanel.addComponentListener(new ComponentListener() {
      @Override
      public void componentResized(ComponentEvent e) {
        myPreviewPanel.updateImageSize();
      }

      @Override
      public void componentMoved(ComponentEvent e) {
      }

      @Override
      public void componentShown(ComponentEvent e) {
      }

      @Override
      public void componentHidden(ComponentEvent e) {
      }
    });

    myScrollPane.getHorizontalScrollBar().setUnitIncrement(5);
    myScrollPane.getVerticalScrollBar().setUnitIncrement(5);
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public boolean setFile(@Nullable PsiFile file) {
    final boolean fileChanged = !Comparing.equal(myFile, file);
    myFile = file;

    if (fileChanged) {
      if (myConfiguration != null) {
        myConfiguration.removeListener(this);
        myConfiguration = null;
      }

      if (myFacet != null) {
        myFacet.getResourceFolderManager().removeListener(this);
      }

      if (file != null) {
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
          myFacet = AndroidFacet.getInstance(file);
          if (myFacet != null) {
            myFacet.getResourceFolderManager().removeListener(this);
            myFacet.getResourceFolderManager().addListener(this);
            ConfigurationManager manager = myFacet.getConfigurationManager();
            myConfiguration = manager.getConfiguration(virtualFile);
            myConfiguration.removeListener(this);
            myConfiguration.addListener(this);
          }
        }
      }
    }

    return true;
  }

  private void saveState() {
    if (myConfiguration != null) {
      myConfiguration.save();
    }
  }

  @Override
  public void dispose() {
  }

  @Nullable
  public RenderResult getRenderResult() {
    return myPreviewPanel.getRenderResult();
  }

  public void setRenderResult(@NotNull final RenderResult renderResult, @Nullable final TextEditor editor) {
    myPreviewPanel.setRenderResult(renderResult, editor);
  }

  @NotNull
  public AndroidLayoutPreviewPanel getPreviewPanel() {
    return myPreviewPanel;
  }

  public void updatePreviewPanel() {
    myPreviewPanel.update();
  }

  // ---- Implements RenderContext ----

  @Override
  @Nullable
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @Override
  public void setConfiguration(@NotNull Configuration configuration) {
    if (configuration != myConfiguration) {
      if (myConfiguration != null) {
        myConfiguration.removeListener(this);
      }
      myConfiguration = configuration;
      myConfiguration.addListener(this);
      changed(MASK_ALL);
      // TODO: Cause immediate toolbar updates?
    }
  }

  @Override
  public void requestRender() {
    if (myFile != null) {
      myToolWindowManager.render();
      myToolWindowManager.flush();
      myPreviewPanel.update();
    }
  }

  @Override
  @NotNull
  public UsageType getType() {
    return UsageType.XML_PREVIEW;
  }

  @Nullable
  @Override
  public XmlFile getXmlFile() {
    return (XmlFile)myFile;
  }

  @Nullable
  @Override
  public VirtualFile getVirtualFile() {
    return myFile != null ? myFile.getVirtualFile() : null;
  }

  @Nullable
  @Override
  public Module getModule() {
    if (myFile != null) {
      final AndroidFacet facet = AndroidFacet.getInstance(myFile);
      if (facet != null) {
        return facet.getModule();
      }
    }

    return null;
  }

  @Override
  public boolean hasAlphaChannel() {
    return myPreviewPanel.hasAlphaChannel();
  }

  @Override
  @NotNull
  public Component getComponent() {
    return myPreviewPanel.getRenderComponent();
  }

  @Override
  public void updateLayout() {
    myPreviewPanel.update();
    myPreviewPanel.getRenderComponent().repaint();
  }

  @Override
  public void setDeviceFramesEnabled(boolean on) {
    myPreviewPanel.setDeviceFramesEnabled(on);
  }

  @Nullable
  @Override
  public BufferedImage getRenderedImage() {
    RenderResult result = myPreviewPanel.getRenderResult();
    if (result != null) {
      RenderedImage renderedImage = result.getImage();
      if (renderedImage != null) {
        return renderedImage.getOriginalImage();
      }
    }
    return null;
  }

  @Override
  @NotNull
  public Dimension getFullImageSize() {
    return myPreviewPanel.getFullImageSize();
  }

  @Override
  @NotNull
  public Dimension getScaledImageSize() {
    return myPreviewPanel.getScaledImageSize();
  }

  @Override
  @NotNull
  public Rectangle getClientArea() {
    return myScrollPane.getViewport().getViewRect();
  }

  @Override
  public boolean supportsPreviews() {
    return true;
  }

  @Nullable
  @Override
  public RenderPreviewManager getPreviewManager(boolean createIfNecessary) {
    return myPreviewPanel.getPreviewManager(this, createIfNecessary);
  }

  @Override
  public void setMaxSize(int width, int height) {
    myPreviewPanel.setMaxSize(width, height);
  }

  @Override
  public void zoomFit(boolean onlyZoomOut, boolean allowZoomIn) {
    myPreviewPanel.setZoomToFit(true);
  }

  // ---- Implements ConfigurationListener ----

  @Override
  public boolean changed(int flags) {
    saveState();
    myToolWindowManager.render();

    RenderPreviewManager previewManager = myPreviewPanel.getPreviewManager(this, false);
    if (previewManager != null) {
      previewManager.configurationChanged(flags);
    }

    return true;
  }

  // ---- Implements ResourceFolderManager.ResourceFolderListener ----

  @Override
  public void resourceFoldersChanged(@NotNull AndroidFacet facet,
                                     @NotNull List<VirtualFile> folders,
                                     @NotNull Collection<VirtualFile> added,
                                     @NotNull Collection<VirtualFile> removed) {
    // The project app should already have been refreshed by their own variant listener
    myToolWindowManager.render();
  }

  private class ZoomInAction extends AnAction {
    ZoomInAction() {
      super(AndroidBundle.message("android.layout.preview.zoom.in.action.text"), null, AndroidIcons.ZoomIn);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myPreviewPanel.zoomIn();
      myActionToolBar.updateActionsImmediately();
    }
  }

  private class ZoomOutAction extends AnAction {
    ZoomOutAction() {
      super(AndroidBundle.message("android.layout.preview.zoom.out.action.text"), null, AndroidIcons.ZoomOut);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myPreviewPanel.zoomOut();
      myActionToolBar.updateActionsImmediately();
    }
  }

  private class ZoomActualAction extends AnAction {
    ZoomActualAction() {
      super(AndroidBundle.message("android.layout.preview.zoom.actual.action.text"), null, AndroidIcons.ZoomActual);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myPreviewPanel.zoomActual();
      myActionToolBar.updateActionsImmediately();
    }
  }

  private class ZoomToFitAction extends ToggleAction {
    ZoomToFitAction() {
      super(AndroidBundle.message("android.layout.preview.zoom.to.fit.action.text"), null, AndroidIcons.ZoomFit);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myPreviewPanel.isZoomToFit();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myPreviewPanel.setZoomToFit(state);
      myActionToolBar.updateActionsImmediately();
    }
  }
}
