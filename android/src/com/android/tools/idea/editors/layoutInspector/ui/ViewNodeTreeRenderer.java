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
package com.android.tools.idea.editors.layoutInspector.ui;

import com.android.tools.idea.editors.layoutInspector.model.ViewNode;
import com.android.tools.idea.editors.strings.FontUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;

import javax.swing.*;
import java.awt.*;

public class ViewNodeTreeRenderer extends ColoredTreeCellRenderer {

  @Override
  public void customizeCellRenderer(JTree tree, Object nodeValue, boolean selected,
                                    boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (!(nodeValue instanceof ViewNode)) {
      return;
    }

    ViewNode node = (ViewNode)nodeValue;
    String[] name = node.name.split("\\.");
    append(name[name.length - 1] + " ",
           node.isDrawn() ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
    setIcon(AndroidDomElementDescriptorProvider.getIconForViewTag(name[name.length - 1]));
    if (node.displayInfo.contentDesc != null) {
      Font currentFont = getFont();
      Font f = FontUtil.getFontAbleToDisplay(node.displayInfo.contentDesc, currentFont);
      if (f != null && f != currentFont) {
        setFont(f);
      }
      append(node.displayInfo.contentDesc,
             node.isDrawn() ? SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES : SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    }
  }
}
