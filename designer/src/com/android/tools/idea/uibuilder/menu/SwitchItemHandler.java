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
package com.android.tools.idea.uibuilder.menu;

import com.android.resources.ResourceType;
import com.android.tools.idea.XmlBuilder;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.model.NlAttributesHolder;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

final class SwitchItemHandler {
  private static final String SWITCH_ITEM = "switch_item";

  // @formatter:off
  @Language("XML")
  private static final String SWITCH_ITEM_XML = new XmlBuilder()
    .startTag(RELATIVE_LAYOUT)
    .attribute("xmlns", ANDROID_NS_NAME, ANDROID_URI)
    .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
    .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_MATCH_PARENT)
      .startTag(SWITCH)
      .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
      .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
      .androidAttribute(ATTR_LAYOUT_CENTER_HORIZONTAL, true)
      .androidAttribute(ATTR_LAYOUT_CENTER_VERTICAL, true)
      .endTag(SWITCH)
    .endTag(RELATIVE_LAYOUT)
    .toString();
  // @formatter:on

  private SwitchItemHandler() {
  }

  static boolean handles(@NotNull NlAttributesHolder item) {
    return (LAYOUT_RESOURCE_PREFIX + SWITCH_ITEM).equals(item.getAttribute(AUTO_URI, "actionLayout"));
  }

  @SuppressWarnings("SameReturnValue")
  static boolean onCreate(@NotNull ViewEditor editor, @NotNull InsertType type) {
    if (type.equals(InsertType.CREATE) && !editor.moduleContainsResource(ResourceType.LAYOUT, SWITCH_ITEM)) {
      editor.copyLayoutToMainModuleSourceSet(SWITCH_ITEM, SWITCH_ITEM_XML);
    }

    return true;
  }
}
