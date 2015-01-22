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

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.EditedStyleItem;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.attributes.AttributesTableModel;
import spantable.CellSpanModel;
import com.intellij.openapi.module.Module;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * Cell renderer used to add tooltips to table cells containing {@link ItemResourceValue}
 *
 * Uses delegate to draw "simple" extracted value (which is usually a String or a Boolean)
 */
public class DelegatingCellRenderer implements TableCellRenderer {
  private final Module myModule;
  private final Configuration myConfiguration;
  private final TableCellRenderer myDelegate;
  private final boolean myConvertValueToString;

  public DelegatingCellRenderer(final Module module,
                                final Configuration configuration,
                                boolean convertValueToString,
                                final TableCellRenderer delegate) {
    myModule = module;
    myConfiguration = configuration;
    myDelegate = delegate;
    myConvertValueToString = convertValueToString;
  }

  public DelegatingCellRenderer(final Module module, final Configuration configuration, final TableCellRenderer delegate) {
    this(module, configuration, true, delegate);
  }

  @Override
  public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
    final Object stringValue;
    final CellSpanModel model = (CellSpanModel)table.getModel();

    final String toolTipText;
    if (column == 1 && value instanceof EditedStyleItem) {
      final ItemResourceValue resValue = ((EditedStyleItem)value).getItemResourceValue();
      toolTipText = ThemeEditorUtils.generateToolTipText(resValue, myModule, myConfiguration);
      stringValue = AttributesTableModel.extractRealValue(resValue, model.getCellClass(row, column));
    }
    else {
      stringValue = value;
      toolTipText = null;
    }

    final Component returnedComponent =
      myDelegate.getTableCellRendererComponent(table, myConvertValueToString ? stringValue : value, isSelected, hasFocus, row, column);
    if (returnedComponent instanceof JComponent) {
      ((JComponent)returnedComponent).setToolTipText(toolTipText);
    }
    return returnedComponent;
  }
}
