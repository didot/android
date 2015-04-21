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

import com.android.SdkConstants;
import com.android.tools.idea.editors.theme.ThemeEditorStyle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * A {@link ListCellRenderer} to render {@link com.android.tools.idea.editors.theme.ThemeEditorStyle} elements.
 */
public class StyleListCellRenderer extends JPanel implements ListCellRenderer {
  private final AndroidFacet myFacet;
  private final SimpleColoredComponent myStyleNameLabel = new SimpleColoredComponent();
  private final SimpleColoredComponent myDefaultLabel = new SimpleColoredComponent();

  public StyleListCellRenderer(AndroidFacet facet) {
    myFacet = facet;

    setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

    myStyleNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    myDefaultLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
    myDefaultLabel.append("DEFAULT", SimpleTextAttributes.GRAY_ATTRIBUTES);
    myDefaultLabel.setTextAlign(SwingConstants.RIGHT);

    add(myStyleNameLabel);
    add(Box.createHorizontalGlue());
    add(myDefaultLabel);
  }

  @Override
  @Nullable
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    if (value instanceof JSeparator) {
      return (JSeparator)value;
    }

    if (isSelected) {
      setBackground(list.getSelectionBackground());
      myStyleNameLabel.setForeground(list.getSelectionForeground());
      myDefaultLabel.setForeground(list.getSelectionForeground());
    } else {
      setBackground(list.getBackground());
      myStyleNameLabel.setForeground(list.getForeground());
      myDefaultLabel.setForeground(list.getForeground());
    }

    myStyleNameLabel.clear();

    if (value instanceof String) {
      myStyleNameLabel.append((String)value);
      myDefaultLabel.setVisible(false);
      return this;
    }
    if (!(value instanceof ThemeEditorStyle)) {
      return null;
    }

    ThemeEditorStyle style = (ThemeEditorStyle)value;
    ThemeEditorStyle parent = style.getParent();
    String styleName = style.getSimpleName();
    String parentName = parent != null ? parent.getSimpleName() : null;

    String defaultAppTheme = null;
    if (myFacet != null) {
      Manifest manifest = myFacet.getManifest();
      if (manifest != null) {
        defaultAppTheme = manifest.getApplication()
          .getXmlTag().getAttributeValue(SdkConstants.ATTR_THEME, SdkConstants.ANDROID_URI);
      }
    }

    if (!style.isProjectStyle()) {
      String simplifiedName = simplifyName(styleName);
      if (StringUtil.isEmpty(simplifiedName)) {
        myStyleNameLabel.append(styleName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      else {
        myStyleNameLabel.append(simplifiedName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        myStyleNameLabel.append(" [" + styleName + "]", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }
    else if (!isSelected && parentName != null && styleName.startsWith(parentName + ".")) {
      myStyleNameLabel.append(parentName + ".", SimpleTextAttributes.GRAY_ATTRIBUTES);
      myStyleNameLabel.append(styleName.substring(parentName.length() + 1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else {
      myStyleNameLabel.append(styleName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    myDefaultLabel.setVisible(style.getName().equals(defaultAppTheme));

    return this;
  }

  /**
   * Returns a more user-friendly version of a given themeName.
   * Aimed at framework themes with names of the form Theme.*.Light.*
   * or Theme.*.*
   */
  @NotNull
  private static String simplifyName(String themeName) {
    StringBuilder result = new StringBuilder();
    String[] pieces = themeName.split("\\.");
    if (pieces.length > 1) {
      result.append(pieces[1]);
      if (pieces.length > 2 && pieces[2].equals("Light")) {
        result.append(' ').append("Light");
      }
      else {
        result.append(' ').append("Dark");
      }
    }
    return result.toString();
  }
}
