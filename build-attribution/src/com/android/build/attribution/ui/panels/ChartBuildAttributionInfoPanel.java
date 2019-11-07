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

import static com.android.build.attribution.ui.panels.BuildAttributionPanelsKt.verticalRuler;

import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

public abstract class ChartBuildAttributionInfoPanel extends AbstractBuildAttributionInfoPanel {

  @Override
  public JComponent createBody() {
    JBPanel body = new JBPanel(new GridBagLayout());

    addLegend(body);
    addChart(body);
    addInfo(body);

    addBottomFiller(body);
    return body;
  }

  private static void addBottomFiller(JBPanel body) {
    GridBagConstraints c = new GridBagConstraints();
    c.gridy = 2;
    c.gridx = 0;
    c.weighty = 1.0;
    c.fill = GridBagConstraints.BOTH;
    c.insets = JBUI.emptyInsets();
    body.add(new JBPanel(), c);
  }

  private void addLegend(JBPanel body) {
    JComponent legend = createLegend();
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1.0;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.anchor = GridBagConstraints.FIRST_LINE_END;
    c.fill = GridBagConstraints.NONE;
    c.insets = JBUI.insetsBottom(8);
    if (legend != null) {
      body.add(legend, c);
    }
    else {
      body.add(new JBPanel(), c);
    }
  }

  private void addChart(JBPanel body) {
    JComponent chart = createChart();
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 1;
    c.weightx = 0.0;
    c.anchor = GridBagConstraints.FIRST_LINE_START;
    c.fill = GridBagConstraints.NONE;
    c.insets = JBUI.insetsRight(8);
    body.add(chart, c);
  }

  private void addInfo(JBPanel body) {
    JComponent info = createRightInfoPanel();

    GridBagConstraints rulerConstraints = new GridBagConstraints();
    rulerConstraints.gridx = 1;
    rulerConstraints.gridy = 1;
    rulerConstraints.weightx = 0.0;
    rulerConstraints.weighty = 0.0;
    rulerConstraints.gridheight = 1;
    rulerConstraints.gridwidth = 1;
    rulerConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
    rulerConstraints.fill = GridBagConstraints.VERTICAL;
    rulerConstraints.insets = JBUI.emptyInsets();

    GridBagConstraints panelConstraints = new GridBagConstraints();
    panelConstraints.gridx = 2;
    panelConstraints.gridy = 1;
    panelConstraints.weightx = 1.0;
    panelConstraints.weighty = 1.0;
    panelConstraints.gridheight = 2;
    panelConstraints.insets = JBUI.insetsLeft(8);
    panelConstraints.fill = GridBagConstraints.BOTH;
    if (info != null) {
      body.add(verticalRuler(), rulerConstraints);
      body.add(info, panelConstraints);
    }
    else {
      //need to add horizontal filler
      body.add(new JBPanel<>(), panelConstraints);
    }
  }

  @NotNull
  public abstract JComponent createChart();

  public abstract JComponent createLegend();

  public abstract JComponent createRightInfoPanel();
}
