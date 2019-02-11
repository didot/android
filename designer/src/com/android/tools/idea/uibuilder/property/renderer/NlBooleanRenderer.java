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
package com.android.tools.idea.uibuilder.property.renderer;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.property.ptable.PTable;
import com.android.tools.property.ptable.PTableItem;
import com.android.tools.idea.common.property.NlProperty;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UIUtil;
import com.android.ide.common.rendering.api.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

public class NlBooleanRenderer extends NlAttributeRenderer {
  private final ThreeStateCheckBox myCheckbox;

  public NlBooleanRenderer() {
    JPanel panel = getContentPanel();

    myCheckbox = new ThreeStateCheckBox();
    panel.add(myCheckbox, BorderLayout.LINE_START);
  }

  @Override
  protected void customizeCellRenderer(@NotNull PTable table, @NotNull PTableItem item, boolean selected, boolean focus, int row, int col) {
    if (!(item instanceof NlProperty)) {
      return;
    }

    NlProperty property = (NlProperty)item;
    myCheckbox.setEnabled(true);

    String propValue = property.getValue();
    ThreeStateCheckBox.State state = getState(propValue);
    if (state == null && propValue != null) {
      ResourceResolver resourceResolver = property.getResolver();
      if (resourceResolver != null) {
        append(propValue, modifyAttributes(selected, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES));
        ResourceValue resource = resourceResolver.findResValue(propValue, false);
        String resolvedValue = resource != null ? resource.getValue() : null;
        state = getState(resolvedValue);
      }
    }

    if (state != null) {
      myCheckbox.setEnabled(true);
      myCheckbox.setState(state);
    } else {
      myCheckbox.setEnabled(false);
    }
  }

  @NotNull
  private static SimpleTextAttributes modifyAttributes(boolean selected, SimpleTextAttributes attributes) {
    return selected ? new SimpleTextAttributes(attributes.getStyle(), UIUtil.getTableSelectionForeground()) : attributes;
  }

  @Nullable
  public static ThreeStateCheckBox.State getState(@Nullable String s) {
    if (s == null) {
      return ThreeStateCheckBox.State.DONT_CARE;
    }
    else if (SdkConstants.VALUE_TRUE.equalsIgnoreCase(s)) {
      return ThreeStateCheckBox.State.SELECTED;
    }
    else if (SdkConstants.VALUE_FALSE.equalsIgnoreCase(s)) {
      return ThreeStateCheckBox.State.NOT_SELECTED;
    }
    else {
      return null;
    }
  }

  @Nullable
  public static Boolean getBoolean(ThreeStateCheckBox.State state) {
    switch (state) {
      case DONT_CARE:
        return null;
      case SELECTED:
        return Boolean.TRUE;
      case NOT_SELECTED:
      default:
        return Boolean.FALSE;
    }
  }

  public static Boolean getNextState(ThreeStateCheckBox.State state) {
    switch (state) {
      case DONT_CARE:
        return Boolean.TRUE;
      case SELECTED:
        return Boolean.FALSE;
      case NOT_SELECTED:
      default:
        return null;
    }
  }

  @Override
  public boolean canRender(@NotNull NlProperty item, @NotNull Set<AttributeFormat> formats) {
    return formats.contains(AttributeFormat.BOOLEAN);
  }
}
