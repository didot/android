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
package com.android.tools.idea.uibuilder.adaptiveicon;

import com.android.tools.idea.uibuilder.actions.SetZoomAction;
import com.android.tools.idea.uibuilder.actions.ZoomLabelAction;
import com.android.tools.idea.uibuilder.editor.ToolbarActionGroups;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ZoomType;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

public class AdaptiveIconActionGroups extends ToolbarActionGroups {
  public AdaptiveIconActionGroups(@NotNull DesignSurface surface) {
    super(surface);
  }

  @NotNull
  @Override
  protected ActionGroup getNorthGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    NlModel model = mySurface.getModel();
    if (model != null) {
      group.add(new DensityMenuAction(model));
    }
    group.add(new ShapeMenuAction((NlDesignSurface)mySurface));
    return group;
  }

  @NotNull
  @Override
  protected ActionGroup getEastGroup() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new SetZoomAction(mySurface, ZoomType.OUT));
    group.add(new ZoomLabelAction(mySurface));
    group.add(new SetZoomAction(mySurface, ZoomType.IN));
    group.add(new SetZoomAction(mySurface, ZoomType.FIT));

    return group;
  }
}