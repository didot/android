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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ThemeEditorConstants;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.ui.resourcechooser.ResourceSwatchComponent;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.Module;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.List;

/**
 * Class that implements a {@link javax.swing.JTable} renderer and editor for drawable attributes.
 */
public class DrawableRendererEditor extends GraphicalResourceRendererEditor {
  /**
   * Minimum size in pixels for the drawable preview render. This doesn't need to be exact as the actual icon
   * will be scaled to match the swatch size.
   */
  private static final int MIN_DRAWABLE_PREVIEW_SIZE = JBUI.scale(25);

  private @Nullable RenderTask myRenderTask;

  public DrawableRendererEditor(@NotNull ThemeEditorContext context, @NotNull AndroidThemePreviewPanel previewPanel, boolean isEditor) {
    super(context, previewPanel, isEditor);
  }

  @NotNull
  public static RenderTask configureRenderTask(@NotNull final Module module, @NotNull final Configuration configuration) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    final RenderService service = RenderService.getInstance(facet);
    RenderLogger logger = new RenderLogger("ThemeEditorLogger", null);
    RenderTask task = service.createTask(null, configuration, logger, null);
    assert task != null;
    task.getLayoutlibCallback().setLogger(logger);
    return task;
  }

  @Override
  protected void updateComponent(@NotNull ThemeEditorContext context, @NotNull ResourceComponent component, @NotNull EditedStyleItem item) {
    assert context.getResourceResolver() != null;

    // Set a maximum size to avoid rendering big previews that then we will scale down to the size of the swatch icon.
    Dimension iconSize = component.getSwatchIconSize();
    // When the component it's been created but hasn't been added to the table yet, it might report a 0 size, so we set a minimum size.
    int iconWidth = Math.max(iconSize.width, MIN_DRAWABLE_PREVIEW_SIZE);
    int iconHeight = Math.max(iconSize.height, MIN_DRAWABLE_PREVIEW_SIZE);

    if (myRenderTask == null || myRenderTask.getModule() != context.getCurrentContextModule()) {
      myRenderTask = configureRenderTask(context.getCurrentContextModule(), context.getConfiguration());
    }

    myRenderTask.setMaxRenderSize(iconWidth, iconHeight);
    List<BufferedImage> images = myRenderTask.renderDrawableAllStates(item.getSelectedValue());
    ResourceSwatchComponent.SwatchIcon icon;
    if (images.isEmpty()) {
      icon = ResourceSwatchComponent.WARNING_ICON;
    }
    else {
      icon = new ResourceSwatchComponent.SquareImageIcon(Iterables.getLast(images));
      icon.setIsStack(images.size() > 1);
    }
    component.setSwatchIcon(icon);

    String nameText = String
      .format(ThemeEditorConstants.ATTRIBUTE_LABEL_TEMPLATE, ColorUtil.toHex(ThemeEditorConstants.RESOURCE_ITEM_COLOR),
              ThemeEditorUtils.getDisplayHtml(item));
    component.setNameText(nameText);
    component.setValueText(item.getValue());
  }

  @NotNull
  @Override
  protected EnumSet<ResourceType> getAllowedResourceTypes() {
    return GraphicalResourceRendererEditor.DRAWABLES_ONLY;
  }
}
