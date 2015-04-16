/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.tools.idea.editors.theme.ThemeEditorStyle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * A {@link ListCellRenderer} to render {@link com.android.tools.idea.editors.theme.ThemeEditorStyle} elements.
 */
public class StyleListCellRenderer extends SimpleColoredComponent implements ListCellRenderer {

  @Override
  @Nullable
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    if (value instanceof JSeparator) {
      return (JSeparator)value;
    }
    clear();
    if (isSelected) {
      setBackground(list.getSelectionBackground());
      setForeground(list.getSelectionForeground());
    } else {
      setBackground(list.getBackground());
      setForeground(list.getForeground());
    }

    if (value instanceof String) {
      append((String)value);
      return this;
    }
    if (!(value instanceof ThemeEditorStyle)) {
      return null;
    }

    ThemeEditorStyle style = (ThemeEditorStyle)value;
    ThemeEditorStyle parent = style.getParent();
    String styleName = style.getSimpleName();
    String parentName = parent != null ? parent.getSimpleName() : null;

    if (!isSelected && style.isProjectStyle() && parentName != null && styleName.startsWith(parentName)) {
      append(parentName, SimpleTextAttributes.GRAY_ATTRIBUTES);
      append(".", SimpleTextAttributes.GRAY_ATTRIBUTES);
      append(styleName.substring(parentName.length() + 1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    } else {
      append(styleName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    return this;
  }
}
