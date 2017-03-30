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
package com.android.tools.idea.uibuilder.handlers.linear;

import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.handlers.common.GenericLinearDragHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@link GenericLinearDragHandler} for LinearLayout
 */
class LinearDragHandler extends GenericLinearDragHandler {
  public LinearDragHandler(@NotNull ViewEditor editor,
                           @NotNull SceneComponent layout,
                           @NotNull List<NlComponent> components,
                           @NotNull DragType type,
                           @NotNull LinearLayoutHandler linearLayoutHandler) {
    super(editor, layout, components, type, linearLayoutHandler, linearLayoutHandler.isVertical(layout.getNlComponent()));
  }
}
