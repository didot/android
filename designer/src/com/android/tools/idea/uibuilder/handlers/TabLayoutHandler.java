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
package com.android.tools.idea.uibuilder.handlers;

import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.*;

public class TabLayoutHandler extends HorizontalScrollViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_TAB_MODE,
      ATTR_TAB_GRAVITY,
      ATTR_TAB_CONTENT_START,
      ATTR_THEME,
      ATTR_BACKGROUND,
      ATTR_TAB_INDICATOR_COLOR,
      ATTR_TAB_SELECTED_TEXT_COLOR,
      ATTR_TAB_TEXT_APPEARANCE);
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent node,
                          @NotNull InsertType insertType) {
    if (insertType.isCreate()) {
      // Insert a couple of TabItems:
      NlComponent tab1 = node.createChild(editor, CLASS_TAB_ITEM, null, InsertType.VIEW_HANDLER);
      NlComponent tab2 = node.createChild(editor, CLASS_TAB_ITEM, null, InsertType.VIEW_HANDLER);
      NlComponent tab3 = node.createChild(editor, CLASS_TAB_ITEM, null, InsertType.VIEW_HANDLER);

      tab1.setAndroidAttribute(ATTR_TEXT, "Left");
      tab2.setAndroidAttribute(ATTR_TEXT, "Center");
      tab3.setAndroidAttribute(ATTR_TEXT, "Right");
    }

    return true;
  }

  @Override
  public boolean acceptsChild(@NotNull NlComponent layout,
                              @NotNull NlComponent newChild) {
    return newChild.getTagName().equals(TAB_ITEM);
  }

  @Override
  public void onChildInserted(@NotNull NlComponent layout,
                              @NotNull NlComponent newChild,
                              @NotNull InsertType insertType) {
    if (newChild.getAndroidAttribute(ATTR_TEXT) == null) {
      newChild.setAndroidAttribute(ATTR_TEXT, "Tab" + (layout.getChildren().size() + 1));
    }
  }
}
