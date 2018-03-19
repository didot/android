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
package com.android.tools.idea.npw.project;

import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.npw.template.RenderTemplateModel;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.wizard.model.WizardModel;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class NewProjectModuleModel extends WizardModel {

  @NotNull private final NewModuleModel myNewModuleModel;
  @NotNull private final RenderTemplateModel myNewRenderTemplateModel;

  public NewProjectModuleModel(@NotNull NewProjectModel projectModel) {
    myNewModuleModel = new NewModuleModel(projectModel, new File(""));

    NamedModuleTemplate dummyTemplate = GradleAndroidModuleTemplate.createDummyTemplate();
    myNewRenderTemplateModel = new RenderTemplateModel(myNewModuleModel, null, dummyTemplate, "");

    myNewModuleModel.getRenderTemplateValues().setValue(myNewRenderTemplateModel.getTemplateValues());
  }

  @NotNull
  public NewModuleModel getNewModuleModel() {
    return myNewModuleModel;
  }

  @NotNull
  public RenderTemplateModel getNewRenderTemplateModel() {
    return myNewRenderTemplateModel;
  }

  @Override
  protected void handleFinished() {
    myNewModuleModel.handleFinished();
    myNewRenderTemplateModel.handleFinished();
  }
}
