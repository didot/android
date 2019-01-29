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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.npw.model.NewProjectModel;
import com.android.tools.idea.npw.project.ChooseAndroidProjectStep;
import com.android.tools.idea.npw.project.deprecated.ConfigureAndroidProjectStep;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder.UxStyle.DYNAMIC_APP;
import static com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder.UxStyle.INSTANT_APP;
import static com.intellij.idea.ActionsBundle.actionText;


public class AndroidNewProjectAction extends AnAction implements DumbAware {
  public AndroidNewProjectAction() {
    this(actionText("NewDirectoryProject"));
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
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (!AndroidSdkUtils.isAndroidSdkAvailable()) {
      SdkQuickfixUtils.showSdkMissingDialog();
      return;
    }

    NewProjectModel projectModel = new NewProjectModel();
    ModelWizard wizard = null;
    StudioWizardDialogBuilder.UxStyle style;

    if (StudioFlags.NPW_DYNAMIC_APPS.get()) {
        wizard = new ModelWizard.Builder()
          .addStep(new ChooseAndroidProjectStep(projectModel))
          .build();
        style = DYNAMIC_APP;
    }
    else {
      wizard = new ModelWizard.Builder()
        .addStep(new ConfigureAndroidProjectStep(projectModel))
        .build();
      style = INSTANT_APP;
    }
    wizard.addResultListener(new ModelWizard.WizardListener() {
      @Override
      public void onWizardFinished(@NotNull ModelWizard.WizardResult result) {
        projectModel.onWizardFinished(result);
      }
    });
    new StudioWizardDialogBuilder(wizard, actionText("WelcomeScreen.CreateNewProject")).setUxStyle(style).build().show();
  }
}
