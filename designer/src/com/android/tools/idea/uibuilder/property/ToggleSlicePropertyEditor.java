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
package com.android.tools.idea.uibuilder.property;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class ToggleSlicePropertyEditor extends AnAction {
  static final String SLICE_PROPERTY_EDITOR = "NlSlicePropertyEditor";

  private NlPropertiesManager myPropertiesManager;

  public ToggleSlicePropertyEditor(@NotNull NlPropertiesManager propertiesManager) {
    myPropertiesManager = propertiesManager;
  }

  @Override
  public void update(AnActionEvent event) {
    PropertiesComponent properties = PropertiesComponent.getInstance();
    boolean visible = properties.getBoolean(SLICE_PROPERTY_EDITOR);
    event.getPresentation().setText(!visible ? "Slice property editor" : "All properties table");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    PropertiesComponent properties = PropertiesComponent.getInstance();
    properties.setValue(SLICE_PROPERTY_EDITOR, !properties.getBoolean(SLICE_PROPERTY_EDITOR));
    myPropertiesManager.updateSelection();
  }
}
