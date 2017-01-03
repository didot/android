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
package com.android.tools.idea.editors.hierarchyview.ui;

import com.android.annotations.Nullable;
import com.android.tools.adtui.workbench.ToolContent;
import com.android.tools.idea.editors.hierarchyview.LayoutInspectorContext;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PropertiesTablePanel extends JScrollPane implements ToolContent<LayoutInspectorContext> {
  @Override
  public void dispose() {

  }

  @Override
  public void setToolContext(@Nullable LayoutInspectorContext toolContext) {
    if (toolContext != null) {
      this.setViewportView(toolContext.getPropertiesTable());
    }
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return this;
  }
}
