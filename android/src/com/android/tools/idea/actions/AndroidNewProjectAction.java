/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.actions;

import com.android.tools.idea.npw.NewProjectWizardDynamic;
import com.android.tools.idea.npw.WizardUtils;
import com.android.tools.idea.npw.project.ConfigureAndroidProjectStep;
import com.android.tools.idea.npw.project.NewProjectModel;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import org.jetbrains.annotations.NotNull;


public class AndroidNewProjectAction extends AnAction implements DumbAware {
  public AndroidNewProjectAction() {
    this("New Project...");
  }

  public AndroidNewProjectAction(@NotNull String text) {
    super(text);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.getPresentation().setIcon(AllIcons.Welcome.CreateNewProject);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (WizardUtils.isNpwModelWizardEnabled(e, WizardUtils.Feature.NEW_PROJECT)) {
      NewProjectModel model = new NewProjectModel();
      ModelWizard wizard = new ModelWizard.Builder()
        .addStep(new ConfigureAndroidProjectStep(model))
        .build();
      new StudioWizardDialogBuilder(wizard, "Create New Project").build().show();
    }
    else {
      try {
        NewProjectWizardDynamic dialog = new NewProjectWizardDynamic(null, null);
        dialog.init();
        dialog.show();
      }
      catch (IllegalStateException error) {
        Logger.getInstance(AndroidNewProjectAction.class).warn("Unable to launch New Project Wizard", error);
      }
    }
  }
}
