/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui.panels;

import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.JComponent;

public abstract class AbstractBuildAttributionInfoPanel extends JBPanel<AbstractBuildAttributionInfoPanel> {

  public static final int PREFERRED_PANEL_WIDTH = 700;

  public AbstractBuildAttributionInfoPanel() {
    super(new GridBagLayout());
    setBorder(JBUI.Borders.empty(16));
    withPreferredWidth(PREFERRED_PANEL_WIDTH);
  }

  public AbstractBuildAttributionInfoPanel init() {
    addHeader();
    addBody();
    return this;
  }

  private void addHeader() {
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1.0;
    c.anchor = GridBagConstraints.FIRST_LINE_START;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = JBUI.insetsBottom(8);
    add(createHeader(), c);
  }

  private void addBody() {
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 1;
    c.weightx = 1.0;
    c.weighty = 1.0;
    c.insets = JBUI.emptyInsets();
    c.fill = GridBagConstraints.BOTH;
    c.anchor = GridBagConstraints.FIRST_LINE_START;
    add(createBody(), c);
  }

  public abstract JComponent createHeader();

  public abstract JComponent createBody();
}
