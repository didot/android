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
package com.android.tools.idea.common.property.editors;

import com.android.tools.idea.uibuilder.property.editors.BrowsePanel;
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;

public abstract class BaseComponentEditor implements NlComponentEditor, BrowsePanel.Context {
  protected static final JBColor DEFAULT_VALUE_TEXT_COLOR = new JBColor(Gray._128, Gray._128);
  protected static final JBColor CHANGED_VALUE_TEXT_COLOR = JBColor.BLUE;

  /** Horizontal gap between editor control and {@link BrowsePanel} */
  protected static final int HORIZONTAL_COMPONENT_GAP = SystemInfo.isMac ? 0 : 2;

  /** Horizontal spacing between label and editor in inspector */
  public static final int HORIZONTAL_SPACING = 4;

  /** Vertical spacing between editors in inspector */
  public static final int VERTICAL_SPACING = 2;

  /** Horizontal padding inside the edit control */
  public static final int HORIZONTAL_PADDING = 3;

  /** Horizontal spacing inside the combobox editor */
  public static final int HORIZONTAL_ENUM_PADDING = 7;

  /** Vertical padding inside the edit control */
  public static final int VERTICAL_PADDING = 2;

  /** Vertical padding inside the edit control with small font */
  protected static final int VERTICAL_PADDING_FOR_SMALL_FONT = 3;

  private final NlEditingListener myListener;

  private JLabel myLabel;

  public BaseComponentEditor(@NotNull NlEditingListener listener) {
    myListener = listener;
  }

  @Nullable
  @Override
  public JLabel getLabel() {
    return myLabel;
  }

  @Override
  public void setLabel(@NotNull JLabel label) {
    myLabel = label;
    label.setVisible(getComponent().isVisible());
  }

  @Override
  public void setVisible(boolean visible) {
    getComponent().setVisible(visible);
    if (myLabel != null) {
      myLabel.setVisible(visible);
    }
  }

  @Override
  public void refresh() {
    XmlTag tag = getProperty().getTag();
    if (tag == null || tag.isValid()) {
      setProperty(getProperty());
    }
  }

  @Override
  @Nullable
  public Object getValue() {
    return null;
  }

  @Override
  public void activate() {
  }

  @Override
  public void setEnabled(boolean enabled) {
    getComponent().setEnabled(enabled);
  }

  @Override
  public void requestFocus() {
    getComponent().requestFocus();
  }

  @Override
  public void cancelEditing() {
    myListener.cancelEditing(this);
  }

  @Override
  public void stopEditing(@Nullable Object newValue) {
    myListener.stopEditing(this, newValue);
    refresh();
  }

  @TestOnly
  public NlEditingListener getEditingListener() {
    return myListener;
  }

  protected void showBrowseDialog() {
    String newValue = BrowsePanel.showBrowseDialog(getProperty());
    if (newValue != null) {
      stopEditing(newValue);
    }
    else {
      cancelEditing();
    }
  }
}
