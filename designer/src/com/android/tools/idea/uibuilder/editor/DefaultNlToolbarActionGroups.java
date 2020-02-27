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
package com.android.tools.idea.uibuilder.editor;

import static com.android.tools.idea.common.surface.DesignSurfaceShortcut.DESIGN_MODE;
import static com.android.tools.idea.common.surface.DesignSurfaceShortcut.NEXT_DEVICE;

import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.actions.BlueprintAndDesignModeAction;
import com.android.tools.idea.actions.BlueprintModeAction;
import com.android.tools.idea.actions.DesignModeAction;
import com.android.tools.idea.common.actions.IssueNotificationAction;
import com.android.tools.idea.common.actions.NextDeviceAction;
import com.android.tools.idea.common.actions.ToggleDeviceOrientationAction;
import com.android.tools.idea.common.editor.ToolbarActionGroups;
import com.android.tools.idea.configurations.DeviceMenuAction;
import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.configurations.OrientationMenuAction;
import com.android.tools.idea.configurations.TargetMenuAction;
import com.android.tools.idea.configurations.ThemeMenuAction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.common.actions.RefreshRenderAction;
import com.android.tools.idea.uibuilder.actions.LayoutEditorHelpAssistantAction;
import com.android.tools.idea.uibuilder.actions.SwitchDesignModeAction;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneMode;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.keymap.KeymapUtil;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

/**
 * Permanent toolbar for the {@link NlDesignSurface}. This toolbar and its contained object
 * life cycles should match the {@link com.android.tools.idea.common.surface.DesignSurface} one.
 */
public final class DefaultNlToolbarActionGroups extends ToolbarActionGroups {

  public DefaultNlToolbarActionGroups(@NotNull NlDesignSurface surface) {
    super(surface);
  }

  @NotNull
  @Override
  protected ActionGroup getEastGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(LayoutEditorHelpAssistantAction.BUNDLE_ID));
    return group;
  }

  @NotNull
  @Override
  protected ActionGroup getNorthGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    if (isInVisualizationTool()) {
      // There is no north group in visualization for now.
      return group;
    }

    group.add(DESIGN_MODE.registerForHiddenAction(createDesignModeAction(),
                                                  new SwitchDesignModeAction((NlDesignSurface)mySurface), mySurface, this));
    group.addSeparator();

    OrientationMenuAction orientationMenuAction = new OrientationMenuAction(mySurface::getConfiguration, mySurface);
    appendShortcutText(orientationMenuAction, ToggleDeviceOrientationAction.getInstance());
    group.add(orientationMenuAction);

    group.addSeparator();
    DeviceMenuAction menuAction = new DeviceMenuAction(mySurface::getConfiguration);
    group.add(NEXT_DEVICE.registerForHiddenAction(menuAction, new NextDeviceAction(menuAction), mySurface, this));

    group.add(new TargetMenuAction(mySurface::getConfiguration));
    group.add(new ThemeMenuAction(mySurface::getConfiguration));

    group.addSeparator();

    group.add(new LocaleMenuAction(mySurface::getConfiguration));
    return group;
  }

  private void appendShortcutText(@NotNull AnAction targetAction , @NotNull AnAction action) {
    String shortcutsText = KeymapUtil.getPreferredShortcutText(action.getShortcutSet().getShortcuts());
    Presentation presentation = targetAction.getTemplatePresentation();
    if (!shortcutsText.isEmpty()) {
      presentation.setDescription(String.format("%s (%s)", presentation.getDescription(), shortcutsText));
    }
  }

  @NotNull
  private DropDownAction createDesignModeAction() {
    DropDownAction designSurfaceMenu = new DropDownAction(null, "Select Design Surface", StudioIcons.LayoutEditor.Toolbar.VIEW_MODE);
    designSurfaceMenu.addAction(new DesignModeAction((NlDesignSurface)mySurface));
    designSurfaceMenu.addAction(new BlueprintModeAction((NlDesignSurface)mySurface));
    designSurfaceMenu.addAction(new BlueprintAndDesignModeAction((NlDesignSurface)mySurface));
    designSurfaceMenu.addSeparator();
    // Get the action instead of creating a new one, to make the popup menu display the shortcut.
    designSurfaceMenu.addAction(RefreshRenderAction.getInstance());
    return designSurfaceMenu;
  }

  @NotNull
  @Override
  protected ActionGroup getNorthEastGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    if (isInVisualizationTool()) {
      // Ignore Issue panel in visualisation.
      group.addAll(getZoomActionsWithShortcuts(mySurface, this));
      return group;
    }
    addActionsWithSeparator(group, getZoomActionsWithShortcuts(mySurface, this));
    group.add(IssueNotificationAction.getInstance());
    return group;
  }

  private boolean isInVisualizationTool() {
    return StudioFlags.NELE_VISUALIZATION.get() && ((NlDesignSurface) mySurface).getSceneMode() == SceneMode.VISUALIZATION;
  }
}
