/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property2.action;

import com.android.tools.property.panel.api.FilteredPTableModel;
import com.android.tools.property.panel.api.TableLineModel;
import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionLayoutAttributesModel;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.xml.XmlTag;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

public class DeleteCustomFieldAction extends AnAction {
  private final FilteredPTableModel<NelePropertyItem> myTableModel;
  private TableLineModel myLineModel;

  public DeleteCustomFieldAction(@NotNull FilteredPTableModel<NelePropertyItem> tableModel) {
    super(null, "Remove Selected Property", StudioIcons.Common.REMOVE);
    myTableModel = tableModel;
  }

  public void setLineModel(@NotNull TableLineModel lineModel) {
    myLineModel = lineModel;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    if (myLineModel == null) {
      return;
    }
    NelePropertyItem property = (NelePropertyItem)myLineModel.getSelectedItem();
    if (property == null) {
      return;
    }
    XmlTag tag = MotionLayoutAttributesModel.getTag(property);
    if (tag == null) {
      return;
    }
    Runnable applyToModel = () -> myTableModel.deleteItem(property);

    MotionLayoutAttributesModel model = (MotionLayoutAttributesModel)property.getModel();
    model.deleteTag(tag, applyToModel);
  }
}
