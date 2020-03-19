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
package com.android.tools.idea.common.editor;

import com.android.tools.adtui.stdui.KeyBindingKt;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides and handles actions for a {@link DesignerEditor}.
 */
public abstract class ActionManager<S extends DesignSurface> {
  protected final S mySurface;

  protected ActionManager(@NotNull S surface) {
    mySurface = surface;
  }

  protected static void registerAction(@NotNull AnAction action,
                                       @NonNls String actionId,
                                       @NotNull JComponent component) {
    Arrays.stream(com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction(actionId).getShortcutSet().getShortcuts())
      .filter(shortcut -> shortcut instanceof KeyboardShortcut && ((KeyboardShortcut)shortcut).getSecondKeyStroke() == null)
      .forEach(shortcut -> registerAction(action, ((KeyboardShortcut)shortcut).getFirstKeyStroke(), component));
  }

  protected static void registerAction(@NotNull AnAction action,
                                       @NotNull KeyStroke keyStroke,
                                       @NotNull JComponent component) {
    KeyBindingKt.registerAnActionKey(component, () -> action, keyStroke, action.getClass().getSimpleName(), JComponent.WHEN_FOCUSED);
  }

  @NotNull
  public JComponent createToolbar() {
    return new ActionsToolbar(mySurface, mySurface).getToolbarComponent();
  }

  @NotNull
  public JComponent createDesignSurfaceToolbar() {
    return new DesignSurfaceActionsToolbar(mySurface, mySurface, mySurface).getDesignSurfaceToolbar();
  }

  /**
   * Returns a pre-registered action for the given action name. See {@link com.intellij.openapi.actionSystem.IdeActions}
   */
  @Nullable
  protected static AnAction getRegisteredActionByName(@NotNull String actionName) {
    return com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction(actionName);
  }


  /**
   * Register keyboard shortcuts onto the provided component.
   *
   * @param component        The component onto which shortcut should be registered.
   */
  public abstract void registerActionsShortcuts(@NotNull JComponent component);

  /**
   * Creates the actions for the pop-up menu (a.k.a. context menu) for the given {@link NlComponent}.
   *
   * @param leafComponent The target component for the pop-up menu (e.g. The right-clicked component)
   */
  @NotNull
  public abstract DefaultActionGroup getPopupMenuActions(@Nullable NlComponent leafComponent);

  /**
   * Creates the actions for the given {@link NlComponent}s.
   *
   * @param selection The selected {@link NlComponent}s in {@link DesignSurface}.
   */
  @NotNull
  public abstract DefaultActionGroup getToolbarActions(@NotNull List<NlComponent> selection);

  /**
   * Returns the context toolbar for a {@link SceneView}. This toolbar should contain actions
   * that are specific to this {@link SceneView}. The method returns null if no toolbar is needed.
   */
  @Nullable
  public JComponent getSceneViewContextToolbar(@NotNull SceneView sceneView) {
    return null;
  }
}
