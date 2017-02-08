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
package com.android.tools.profilers.memory;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Function;

class SimpleColumnRenderer extends ColoredTreeCellRenderer {
  private final Function<MemoryObjectTreeNode, String> myTextGetter;
  private final Function<MemoryObjectTreeNode, Icon> myIconGetter;
  private final int myAlignment;

  public SimpleColumnRenderer(@NotNull Function<MemoryObjectTreeNode, String> textGetter,
                              @NotNull Function<MemoryObjectTreeNode, Icon> iconGetter,
                              int alignment) {
    myTextGetter = textGetter;
    myIconGetter = iconGetter;
    myAlignment = alignment;
  }

  @Override
  public void customizeCellRenderer(@NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    if (value instanceof MemoryObjectTreeNode) {
      /**
       * Note - the same text is added as a fragment tag so it can be exposed for us to validate
       * the values we are setting to the fragments.
       * See {@link com.intellij.ui.SimpleColoredComponent#getFragmentTag(int)} for details.
       */
      String text = myTextGetter.apply((MemoryObjectTreeNode)value);
      append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES, text);
      Icon icon = myIconGetter.apply((MemoryObjectTreeNode)value);
      if (icon != null) {
        setIcon(icon);
      }
      setTextAlign(myAlignment);
    }
  }
}
