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
package com.android.tools.idea.uibuilder.surface;

import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.intellij.util.ui.UIUtil;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * View of a device/screen/layout.
 * This is actually painted by {@link ScreenViewLayer}.
 */
abstract class ScreenViewBase extends SceneView {
  protected boolean myIsSecondary;

  // Use cached to avoid deriving same font multiple times.
  @NotNull private Font myLabelFont;

  protected ScreenViewBase(@NotNull NlDesignSurface surface, @NotNull LayoutlibSceneManager manager, @NotNull ShapePolicy shapePolicy) {
    super(surface, manager, shapePolicy);
    myLabelFont = createLabelFont();
  }

  @NotNull
  @Override
  public Insets getMargin() {
    if (getSurface().isShowModelNames()) {
      Graphics graphics = getSurface().getGraphics();
      if (graphics != null) {
        FontMetrics metrics = graphics.getFontMetrics(myLabelFont);
        //noinspection UseDPIAwareInsets, this margin is not scaled
        return new Insets(metrics.getHeight() + NlConstants.NAME_LABEL_BOTTOM_MARGIN_PX, 0, 0, 0);
      }
    }
    return NO_MARGIN;
  }

  @NotNull
  public Font getLabelFont() {
    return myLabelFont;
  }

  @NotNull
  @Override
  public LayoutlibSceneManager getSceneManager() {
    return (LayoutlibSceneManager)super.getSceneManager();
  }

  @NotNull
  @Override
  public NlDesignSurface getSurface() {
    return (NlDesignSurface)super.getSurface();
  }

  @Nullable
  public RenderResult getResult() {
    return getSceneManager().getRenderResult();
  }

  /**
   * Set if this is the second SceneView in the associcated Scene/SceneManager.
   * @param isSecondary the new value to indicated if this is the second SceneView in associated Scene/SceneManager.
   */
  final void setSecondary(boolean isSecondary) {
    myIsSecondary = isSecondary;
  }

  /**
   * @return true if this is second SceneView in the associated Scene/SceneManager, false otherwise. The default value is false.
   */
  protected final boolean isSecondary() {
    return myIsSecondary;
  }

  /**
   * This function is called when the UI of the {@link com.android.tools.idea.common.surface.DesignSurface} is changes such like
   * switching to presentation mode, changing Studio Look & Feel, or change appearance of preferences.
   */
  @Override
  public void updateUI() {
    myLabelFont = createLabelFont();
  }

  @NotNull
  private static Font createLabelFont() {
    @SuppressWarnings("StaticMethodReferencedViaSubclass") // For coding convention, using UIUtil instead of StartupUiUtil
    Font labelFont = UIUtil.getLabelFont();
    return labelFont.deriveFont(labelFont.getSize() - 1f);
  }
}
