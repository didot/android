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
package com.android.tools.idea.run.deployment;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import java.awt.Component;
import javax.swing.Action;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

final class SelectDeploymentTargetsDialog extends DialogWrapper {
  @NotNull
  private final AsyncDevicesGetter myDevicesGetter;

  @NotNull
  private final Project myProject;

  SelectDeploymentTargetsDialog(@NotNull AsyncDevicesGetter devicesGetter, @NotNull Project project) {
    super(project);

    myDevicesGetter = devicesGetter;
    myProject = project;

    init();
    setTitle("Select Deployment Targets");
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    JComponent panel = new JPanel();
    GroupLayout layout = new GroupLayout(panel);
    Component label = new JLabel("Available devices");
    Component scrollPane = new JBScrollPane(new SelectDeploymentTargetsDialogTable(myDevicesGetter, myProject));

    Group horizontalGroup = layout.createParallelGroup()
      .addComponent(label)
      .addComponent(scrollPane);

    Group verticalGroup = layout.createSequentialGroup()
      .addComponent(label)
      .addComponent(scrollPane);

    layout.setAutoCreateGaps(true);
    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    panel.setLayout(layout);
    return panel;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }
}
