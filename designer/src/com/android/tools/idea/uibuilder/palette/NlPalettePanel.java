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
package com.android.tools.idea.uibuilder.palette;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.splitter.ComponentsSplitter;
import com.android.tools.adtui.workbench.ToolContent;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.model.DnDTransferComponent;
import com.android.tools.idea.uibuilder.model.DnDTransferItem;
import com.android.tools.idea.uibuilder.model.ItemTransferable;
import com.android.tools.idea.uibuilder.model.NlLayoutType;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.adtui.splitter.SplitterUtil.setMinimumHeight;

public class NlPalettePanel extends JPanel implements Disposable, DataProvider, ToolContent<DesignSurface> {
  static final String PALETTE_MODE = "palette.mode";
  static final String PALETTE_PREVIEW_HEIGHT = "palette.preview.height";
  static final int DEFAULT_PREVIEW_HEIGHT = 140;

  private final NlPreviewPanel myPreviewPane;
  private final CopyProvider myCopyProvider;
  private final NlPaletteTreeGrid myPalettePanel;
  private final DependencyManager myDependencyManager;
  private final CopyPasteManager myCopyPasteManager;
  private final ComponentsSplitter mySplitter;
  private NlLayoutType myLayoutType;
  private Runnable myCloseAutoHideCallback;

  public NlPalettePanel(@NotNull Project project, @Nullable NlDesignSurface designSurface) {
    this(project, designSurface, CopyPasteManager.getInstance());
  }

  @VisibleForTesting
  NlPalettePanel(@NotNull Project project, @Nullable NlDesignSurface designSurface, @NotNull CopyPasteManager copyPasteManager) {
    myCopyPasteManager = copyPasteManager;
    IconPreviewFactory iconPreviewFactory = new IconPreviewFactory();
    Disposer.register(this, iconPreviewFactory);
    myDependencyManager = new DependencyManager(project, this, this);
    myPalettePanel = new NlPaletteTreeGrid(
      project, getInitialMode(), myDependencyManager, this::closeAutoHideToolWindow, designSurface, iconPreviewFactory);
    myPreviewPane = new NlPreviewPanel(new NlPreviewImagePanel(iconPreviewFactory, myDependencyManager, this::closeAutoHideToolWindow));
    myCopyProvider = new CopyProviderImpl();
    myPalettePanel.setSelectionListener(myPreviewPane);
    myLayoutType = NlLayoutType.UNKNOWN;

    // Use a ComponentSplitter instead of a Splitter here to avoid a fat splitter size.
    mySplitter = new ComponentsSplitter(true, true);
    mySplitter.setInnerComponent(myPalettePanel);
    mySplitter.setLastComponent(myPreviewPane);
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setLastSize(JBUI.scale(getInitialPreviewHeight()));
    myPalettePanel.addComponentListener(createPreviewHeightUpdater());
    setMinimumHeight(myPalettePanel, JBUI.scale(20));
    setMinimumHeight(myPreviewPane, JBUI.scale(40));
    Disposer.register(this, mySplitter);
    Disposer.register(this, myPalettePanel);
    Disposer.register(this, myPreviewPane);

    setLayout(new BorderLayout());
    add(mySplitter, BorderLayout.CENTER);

    setToolContext(designSurface);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return this;
  }

  @NotNull
  @Override
  public JComponent getFocusedComponent() {
    return myPalettePanel;
  }

  @Override
  public void requestFocus() {
    myPalettePanel.requestFocus();
  }

  @NotNull
  @TestOnly
  public NlPaletteTreeGrid getTreeGrid() {
    return myPalettePanel;
  }

  @TestOnly
  public ComponentsSplitter getSplitter() {
    return mySplitter;
  }

  @NotNull
  @Override
  public List<AnAction> getGearActions() {
    List<AnAction> actions = new ArrayList<>(3);
    actions.add(new TogglePaletteModeAction(this, PaletteMode.ICON_AND_NAME));
    actions.add(new TogglePaletteModeAction(this, PaletteMode.LARGE_ICONS));
    actions.add(new TogglePaletteModeAction(this, PaletteMode.SMALL_ICONS));
    return actions;
  }

  @Override
  public void registerCloseAutoHideWindow(@NotNull Runnable runnable) {
    myCloseAutoHideCallback = runnable;
  }

  @Override
  public boolean supportsFiltering() {
    return true;
  }

  @Override
  public void setFilter(@NotNull String filter) {
    myPalettePanel.setFilter(filter);
  }

  @Override
  public void setToolContext(@Nullable DesignSurface designSurface) {
    assert designSurface == null || designSurface instanceof NlDesignSurface;
    myPreviewPane.setDesignSurface(designSurface);
    Module module = getModule(designSurface);
    if (designSurface != null && module != null && myLayoutType != designSurface.getLayoutType()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null;
      myLayoutType = designSurface.getLayoutType();
      NlPaletteModel model = NlPaletteModel.get(facet);
      Palette palette = model.getPalette(myLayoutType);
      myPalettePanel.populateUiModel(palette, (NlDesignSurface)designSurface);
      myDependencyManager.setPalette(palette, module);
      repaint();
    }
  }

  private void closeAutoHideToolWindow() {
    if (myCloseAutoHideCallback != null) {
      myCloseAutoHideCallback.run();
    }
  }

  @Nullable
  private static Module getModule(@Nullable DesignSurface designSurface) {
    Configuration configuration =
      designSurface != null && designSurface.getLayoutType().isSupportedByDesigner() ? designSurface.getConfiguration() : null;
    return configuration != null ? configuration.getModule() : null;
  }

  @NotNull
  public PaletteMode getMode() {
    return myPalettePanel.getMode();
  }

  public void setMode(@NotNull PaletteMode mode) {
    myPalettePanel.setMode(mode);
    PropertiesComponent.getInstance().setValue(PALETTE_MODE, mode.toString(), PaletteMode.ICON_AND_NAME.toString());
  }

  private static PaletteMode getInitialMode() {
    try {
      return PaletteMode.valueOf(PropertiesComponent.getInstance().getValue(PALETTE_MODE, PaletteMode.ICON_AND_NAME.toString()));
    }
    catch (IllegalArgumentException unused) {
      return PaletteMode.ICON_AND_NAME;
    }
  }

  @NotNull
  private ComponentListener createPreviewHeightUpdater() {
    return new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        PropertiesComponent.getInstance().setValue(PALETTE_PREVIEW_HEIGHT, String.valueOf(AdtUiUtils.unscale(mySplitter.getLastSize())));
      }
    };
  }

  private static int getInitialPreviewHeight() {
    try {
      return Integer.parseInt(PropertiesComponent.getInstance().getValue(PALETTE_PREVIEW_HEIGHT, String.valueOf(DEFAULT_PREVIEW_HEIGHT)));
    }
    catch (NumberFormatException unused) {
      return DEFAULT_PREVIEW_HEIGHT;
    }
  }

  @Override
  public void dispose() {
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    return PlatformDataKeys.COPY_PROVIDER.is(dataId) ? myCopyProvider : null;
  }

  private class CopyProviderImpl implements CopyProvider {

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      Palette.Item item = myPalettePanel.getSelectedItem();
      if (item != null && !myDependencyManager.needsLibraryLoad(item)) {
        DnDTransferComponent component = new DnDTransferComponent(item.getTagName(), item.getXml(), 0, 0);
        myCopyPasteManager.setContents(new ItemTransferable(new DnDTransferItem(component)));
      }
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      Palette.Item item = myPalettePanel.getSelectedItem();
      return item != null && !myDependencyManager.needsLibraryLoad(item);
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return true;
    }
  }
}
