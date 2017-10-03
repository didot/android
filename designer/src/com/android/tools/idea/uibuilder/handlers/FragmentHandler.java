/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <fragment>} tag
 */
public final class FragmentHandler extends ViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_NAME,
      ATTR_LAYOUT,
      ATTR_CLASS);
  }

  @Override
  @Nullable
  public AttributeBrowser getBrowser(@NotNull String attributeName) {
    if (!attributeName.equals(ATTR_NAME)) {
      return null;
    }
    return FragmentHandler::browseClasses;
  }

  @Override
  @NotNull
  public String getTitle(@NotNull String tagName) {
    return "<fragment>";
  }

  @Override
  @NotNull
  public String getTitle(@NotNull NlComponent component) {
    return "<fragment>";
  }

  @NotNull
  @Override
  public String getTitleAttributes(@NotNull NlComponent component) {
    String name = component.getAttribute(ANDROID_URI, ATTR_NAME);
    return StringUtil.isEmpty(name) ? "" : "- " + name;
  }

  @Override
  @Language("XML")
  @NotNull
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case PREVIEW_ON_PALETTE:
      case DRAG_PREVIEW:
        return NO_PREVIEW;
      default:
        return super.getXml(tagName, xmlType);
    }
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    if (insertType == InsertType.CREATE) { // NOT InsertType.CREATE_PREVIEW
      if (newChild.getAttribute(ANDROID_URI, ATTR_NAME) != null) {
        return true;
      }
      String src = browseClasses(editor, null);
      if (src != null) {
        newChild.setAttribute(ANDROID_URI, ATTR_NAME, src);
        return true;
      }
      else {
        // Remove the view; the insertion was canceled
        return false;
      }
    }
    return true;
  }

  @Nullable
  private static String browseClasses(@NotNull ViewEditor editor, @Nullable String existingValue) {
    return editor.displayClassInput(Sets.newHashSet(CLASS_FRAGMENT, CLASS_V4_FRAGMENT), null, existingValue);
  }
}
