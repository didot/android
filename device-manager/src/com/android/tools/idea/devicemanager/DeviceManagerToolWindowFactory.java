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

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevicePanel;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDevicePanel;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

public final class DeviceManagerToolWindowFactory implements ToolWindowFactory, DumbAware {
  public static final String ID = "Device Manager";

  @SuppressWarnings("unused")
  private DeviceManagerToolWindowFactory() {
  }

  @Override
  public boolean isApplicable(@NotNull Project project) {
    return StudioFlags.ENABLE_NEW_DEVICE_MANAGER_PANEL.get();
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow window) {
    Disposable parent = Disposer.newDisposable("Device Manager parent");
    JComponent pane = newJBTabbedPane(project, parent);

    Content content = ContentFactory.SERVICE.getInstance().createContent(pane, null, false);
    content.setDisposer(parent);

    window.getContentManager().addContent(content);
  }

  private static @NotNull JComponent newJBTabbedPane(@NotNull Project project, @NotNull Disposable parent) {
    JBTabbedPane pane = new JBTabbedPane();
    pane.setTabComponentInsets(JBUI.emptyInsets());

    pane.addTab("Virtual", new VirtualDevicePanel(project, parent));
    pane.addTab("Physical", new PhysicalDevicePanel(project, parent));

    return pane;
  }
}
