/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcechooser.preview;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HideableDecorator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.actions.CreateXmlResourcePanel;
import org.jetbrains.android.actions.NewResourceCreationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Function;

/**
 * A tab that goes inside the {@link EditResourcePanel}
 */
public abstract class ResourceEditorTab {

  private JPanel myFullPanel;
  private JPanel myExpertPlaceholder;
  private JPanel myExpertPanel;

  private JPanel myEditorPanel;
  private HideableDecorator myExpertDecorator;

  private @NotNull CreateXmlResourcePanel myLocationSettings;
  private @NotNull ChooseResourceDialog.ResourceNameVisibility myResourceNameVisibility;
  private final @NotNull String myTabTitle;

  public ResourceEditorTab(@NotNull Module module, @NotNull String tabTitle, @NotNull Component centerPanel,
                           @NotNull ChooseResourceDialog.ResourceNameVisibility resourceNameVisibility,
                           @NotNull ResourceFolderType folderType, boolean changeFileNameVisible, final @NotNull ResourceType resourceType) {
    myResourceNameVisibility = resourceNameVisibility;
    myTabTitle = tabTitle;

    myExpertDecorator = new HideableDecorator(myExpertPlaceholder, "Device Configuration", true) {
      private void pack() {
        // Hack to not shrink the window too small when we close or open the advanced panel.
        ApplicationManager.getApplication().invokeLater(() -> SwingUtilities.getWindowAncestor(myExpertPlaceholder).pack(),
                                                        ModalityState.any());
      }

      @Override
      protected void on() {
        super.on();
        pack();
      }

      @Override
      protected void off() {
        super.off();
        pack();
      }
    };
    myExpertDecorator.setContentComponent(myExpertPanel);

    myEditorPanel.add(centerPanel);
    myFullPanel.setBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS));

    NewResourceCreationHandler newResourceHandler = NewResourceCreationHandler.getInstance(module.getProject());

    Function<Module, IdeResourceNameValidator> nameValidatorFactory =
      selectedModule -> IdeResourceNameValidator.forResourceName(resourceType, AppResourceRepository.getOrCreateInstance(selectedModule));

    // There is no need to choose the resource name or value here (controlled by parent).
    myLocationSettings = newResourceHandler.createNewResourceValuePanel(module, resourceType, folderType, "", "",
                                                                        false /* chooseName */, false /* chooseValue */,
                                                                        changeFileNameVisible, null, null, nameValidatorFactory);

    myExpertPanel.add(myLocationSettings.getPanel());
  }

  @NotNull
  public JPanel getFullPanel() {
    return myFullPanel;
  }

  public void setLocationSettingsOpen(boolean open) {
    myExpertDecorator.setOn(open);
  }

  @NotNull
  public CreateXmlResourcePanel getLocationSettings() {
    return myLocationSettings;
  }

  @NotNull
  public ChooseResourceDialog.ResourceNameVisibility getResourceNameVisibility() {
    return myResourceNameVisibility;
  }

  @Nullable/*if there is no error*/
  public ValidationInfo doValidate() {
    return getLocationSettings().doValidate();
  }

  /**
   * Save to the project/disk whatever is open in this editor.
   * @return the value that is returned by the resource chooser.
   */
  @NotNull
  public abstract String doSave();

  @NotNull
  public Module getSelectedModule() {
    Module module = getLocationSettings().getModule();
    assert module != null;
    return module;
  }

  @Nullable
  public VirtualFile getResourceDirectory() {
    return getLocationSettings().getResourceDirectory();
  }

  @NotNull
  public IdeResourceNameValidator getValidator() {
    return getLocationSettings().getResourceNameValidator();
  }

  @NotNull
  public String getTabTitle() {
    return myTabTitle;
  }
}
