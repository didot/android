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

import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneLayer;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.type.LayoutFileType;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.flags.StudioFlags.NELE_RENDER_DIAGNOSTICS;

/**
 * View of a device/screen/layout.
 * This is actually painted by {@link ScreenViewLayer}.
 */
public class ScreenView extends ScreenViewBase {

  /**
   * True if we are previewing a non-layout file in Preview Dialog (e.g. Previewing Vector Drawable), false otherwise.
   */
  protected final boolean myShowBorder;

  public ScreenView(@NotNull NlDesignSurface surface, @NotNull LayoutlibSceneManager manager) {
    super(surface, manager);
    myShowBorder = !getSurface().isPreviewSurface() || surface.getLayoutType() == LayoutFileType.INSTANCE;
  }

  @NotNull
  @Override
  protected ImmutableList<Layer> createLayers() {
    ImmutableList.Builder<Layer> builder = ImmutableList.builder();

    if (myShowBorder) {
      builder.add(new BorderLayer(this));
    }
    builder.add(new ScreenViewLayer(this));

    SceneLayer sceneLayer = new SceneLayer(getSurface(), this, false);
    sceneLayer.setAlwaysShowSelection(true);
    builder.add(sceneLayer);
    if (getSceneManager().getModel().getType().isEditable()) {
      builder.add(new CanvasResizeLayer(getSurface(), this));
    }

    if (NELE_RENDER_DIAGNOSTICS.get()) {
      builder.add(new DiagnosticsLayer(getSurface()));
    }
    return builder.build();
  }
}
