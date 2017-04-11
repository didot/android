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
package com.android.tools.idea.naveditor.editor;

import com.android.tools.idea.uibuilder.editor.ToolbarActionGroups;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

/**
 * Toolbar actions for the navigation editor
 */
public class NavToolbarActionGroups extends ToolbarActionGroups {
  public NavToolbarActionGroups(@NotNull DesignSurface surface) {
    super(surface);
  }

  @NotNull
  @Override
  protected ActionGroup getNorthGroup() {
    // TODO
    return new DefaultActionGroup();
  }

  @NotNull
  @Override
  protected ActionGroup getEastGroup() {
    // TODO
    return new DefaultActionGroup();
  }
}
