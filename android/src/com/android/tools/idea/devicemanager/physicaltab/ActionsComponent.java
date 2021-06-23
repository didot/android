/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.scale.JBUIScale;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.Icon;
import javax.swing.JButton;
import org.jetbrains.annotations.NotNull;

final class ActionsComponent extends JBPanel<ActionsComponent> {
  private final @NotNull AbstractButton myActivateDeviceFileExplorerWindowButton;
  private final @NotNull AbstractButton myEditDeviceNameButton;
  private final @NotNull AbstractButton myRemoveButton;
  private final @NotNull AbstractButton myViewDetailsButton;

  ActionsComponent() {
    this(false);
  }

  @VisibleForTesting
  ActionsComponent(boolean addViewDetailsButton) {
    super(null);

    myActivateDeviceFileExplorerWindowButton = newJButton(AllIcons.General.OpenDiskHover);
    myEditDeviceNameButton = newJButton(AllIcons.Actions.Edit);
    myRemoveButton = newJButton(AllIcons.Actions.GC);
    myViewDetailsButton = newJButton(AllIcons.Actions.Preview);

    GroupLayout layout = new GroupLayout(this);
    int size = JBUIScale.scale(22);

    Group horizontalGroup = layout.createSequentialGroup()
      .addGap(0, 0, Short.MAX_VALUE)
      .addComponent(myActivateDeviceFileExplorerWindowButton, GroupLayout.PREFERRED_SIZE, size, GroupLayout.PREFERRED_SIZE)
      // .addComponent(myEditDeviceNameButton, GroupLayout.PREFERRED_SIZE, size, GroupLayout.PREFERRED_SIZE)
      .addComponent(myRemoveButton, GroupLayout.PREFERRED_SIZE, size, GroupLayout.PREFERRED_SIZE);

    if (addViewDetailsButton) {
      horizontalGroup.addComponent(myViewDetailsButton, GroupLayout.PREFERRED_SIZE, size, GroupLayout.PREFERRED_SIZE);
    }

    Group group = layout.createParallelGroup()
      .addComponent(myActivateDeviceFileExplorerWindowButton, GroupLayout.PREFERRED_SIZE, size, GroupLayout.PREFERRED_SIZE)
      // .addComponent(myEditDeviceNameButton, GroupLayout.PREFERRED_SIZE, size, GroupLayout.PREFERRED_SIZE)
      .addComponent(myRemoveButton, GroupLayout.PREFERRED_SIZE, size, GroupLayout.PREFERRED_SIZE);

    if (addViewDetailsButton) {
      group.addComponent(myViewDetailsButton, GroupLayout.PREFERRED_SIZE, size, GroupLayout.PREFERRED_SIZE);
    }

    Group verticalGroup = layout.createSequentialGroup()
      .addGap(0, 0, Short.MAX_VALUE)
      .addGroup(group)
      .addGap(0, 0, Short.MAX_VALUE);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }

  private static @NotNull AbstractButton newJButton(@NotNull Icon icon) {
    AbstractButton button = new JButton(icon);

    button.setBorderPainted(false);
    button.setContentAreaFilled(false);

    return button;
  }

  @NotNull AbstractButton getActivateDeviceFileExplorerWindowButton() {
    return myActivateDeviceFileExplorerWindowButton;
  }

  @NotNull AbstractButton getEditDeviceNameButton() {
    return myEditDeviceNameButton;
  }

  @NotNull AbstractButton getRemoveButton() {
    return myRemoveButton;
  }

  @NotNull AbstractButton getViewDetailsButton() {
    return myViewDetailsButton;
  }
}
