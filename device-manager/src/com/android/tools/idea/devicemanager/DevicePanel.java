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
package com.android.tools.idea.devicemanager;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBPanel;
import javax.swing.JComponent;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DevicePanel extends JBPanel<DevicePanel> implements Disposable {
  protected final @Nullable Project myProject;

  protected JTable myTable;
  protected JComponent myScrollPane;
  protected DetailsPanelPanel myDetailsPanelPanel;

  protected DevicePanel(@Nullable Project project) {
    super(null);
    myProject = project;
  }

  protected final void initTable() {
    myTable = newTable();
    myTable.getSelectionModel().addListSelectionListener(new ViewDetailsListSelectionListener(this));
  }

  protected abstract @NotNull JTable newTable();

  protected final void initDetailsPanelPanel() {
    myDetailsPanelPanel = new DetailsPanelPanel(myScrollPane);
    Disposer.register(this, myDetailsPanelPanel);
  }

  @Override
  public final void dispose() {
  }

  public final @Nullable Project getProject() {
    return myProject;
  }

  public final boolean hasDetails() {
    return myDetailsPanelPanel.getSplitter().isPresent();
  }

  public final void viewDetails() {
    viewDetails(DetailsPanel.DEVICE_INFO_TAB_INDEX);
  }

  public final void viewDetails(int index) {
    DetailsPanel panel = newDetailsPanel();

    panel.getCloseButton().addActionListener(event -> myDetailsPanelPanel.removeSplitter());
    panel.getTabbedPane().ifPresent(pane -> pane.setSelectedIndex(index));

    myDetailsPanelPanel.viewDetails(panel);
  }

  protected abstract @NotNull DetailsPanel newDetailsPanel();
}
