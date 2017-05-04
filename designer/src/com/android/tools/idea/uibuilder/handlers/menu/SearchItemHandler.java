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
package com.android.tools.idea.uibuilder.handlers.menu;

import com.android.resources.ResourceType;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.model.NlAttributesHolder;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.DRAWABLE_PREFIX;

final class SearchItemHandler {
  private static final String SEARCH_ICON = "ic_search_black_24dp";

  private SearchItemHandler() {
  }

  static boolean handles(@NotNull NlAttributesHolder item) {
    return (DRAWABLE_PREFIX + SEARCH_ICON).equals(item.getAndroidAttribute(ATTR_ICON));
  }

  static void onChildInserted(@NotNull ViewEditor editor) {
    if (!editor.moduleContainsResource(ResourceType.DRAWABLE, SEARCH_ICON)) {
      editor.copyVectorAssetToMainModuleSourceSet(SEARCH_ICON);
    }
  }

  @SuppressWarnings("SameReturnValue")
  static boolean onCreate(@NotNull ViewEditor editor, @NotNull NlComponent newChild, @NotNull InsertType type) {
    if (!type.equals(InsertType.CREATE)) {
      return true;
    }

    String value = editor.getMinSdkVersion().getApiLevel() < 11 ? "android.support.v7.widget.SearchView" : "android.widget.SearchView";
    newChild.setAndroidAttribute("actionViewClass", value);

    return true;
  }
}
