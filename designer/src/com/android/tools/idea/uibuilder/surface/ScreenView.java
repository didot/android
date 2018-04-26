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
package com.android.tools.idea.uibuilder.surface;

import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneLayer;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

/**
 * View of a device/screen/layout.
 * This is actually painted by {@link ScreenViewLayer}.
 */
public class ScreenView extends ScreenViewBase {

  /**
   * True if we are previewing a non-layout file in Preview Dialog (e.g. Previewing Vector Drawable), false otherwise.
   */
  protected boolean myIsPreviewingNonLayoutFileInPreviewDialog;

  public ScreenView(@NotNull NlDesignSurface surface, @NotNull LayoutlibSceneManager manager) {
    super(surface, manager);
    if (getSurface().isPreviewSurface() && AndroidPsiUtils.getResourceType(getSceneManager().getModel().getFile()) != ResourceType.LAYOUT) {
      myIsPreviewingNonLayoutFileInPreviewDialog = true;
    }
    else {
      myIsPreviewingNonLayoutFileInPreviewDialog = false;
    }
  }

  @NotNull
  @Override
  protected ImmutableList<Layer> createLayers() {
    ImmutableList.Builder<Layer> builder = ImmutableList.builder();

    if (!myIsPreviewingNonLayoutFileInPreviewDialog) {
      builder.add(new BorderLayer(this));
    }
    builder.add(new ScreenViewLayer(this));
    builder.add(new SelectionLayer(this));

    SceneLayer sceneLayer = new SceneLayer(getSurface(), this, false);
    sceneLayer.setAlwaysShowSelection(true);
    builder.add(sceneLayer);
    if (getSurface().getLayoutType().isSupportedByDesigner()) {
      builder.add(new CanvasResizeLayer(getSurface(), this));
    }
    return builder.build();
  }
}
