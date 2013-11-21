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
package com.android.tools.idea.wizard;

import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.List;

import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE;

/**
 * {@linkplain NewModuleWizard} guides the user through adding a new module to an existing project. It has a template-based flow and as the
 * first step of the wizard allows the user to choose a template which will guide the rest of the wizard flow.
 */
public class NewModuleWizard extends TemplateWizard implements ChooseTemplateStep.TemplateChangeListener {
  private TemplateWizardModuleBuilder myModuleBuilder;
  private static final String PROJECT_NAME = "Android Project";
  private static final String MODULE_NAME = "Android Module";
  private static final String APP_NAME = "Android Application";
  private static final String LIB_NAME = "Android Library";

  public NewModuleWizard(@Nullable Project project) {
    super("New Module", project);
    getWindow().setMinimumSize(new Dimension(1000, 640));
    init();
  }

  @Override
  protected void init() {
    myModuleBuilder = new TemplateWizardModuleBuilder(null, null, myProject, AndroidIcons.Wizards.NewModuleSidePanel, mySteps, false) {
      @Override
      public void update() {
        super.update();
        NewModuleWizard.this.update();
      }
    };

    // Hide the library checkbox
    myModuleBuilder.myWizardState.myHidden.add(ATTR_IS_LIBRARY_MODULE);

    myModuleBuilder.mySteps.add(0, buildChooseModuleStep());
    super.init();
  }

  @Override
  public void update() {
    if (myModuleBuilder == null || !myModuleBuilder.myInitializationComplete) {
      return;
    }
    NewModuleWizardState wizardState = myModuleBuilder.myWizardState;
    myModuleBuilder.myConfigureAndroidModuleStep.setVisible(wizardState.myIsAndroidModule);
    if (wizardState.myIsAndroidModule) {
      myModuleBuilder.myConfigureAndroidModuleStep.updateStep();
    }
    myModuleBuilder.myTemplateParameterStep.setVisible(!wizardState.myIsAndroidModule);
    myModuleBuilder.myLauncherIconStep.setVisible(wizardState.myIsAndroidModule &&
                                                  wizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS));
    myModuleBuilder.myChooseActivityStep.setVisible(wizardState.myIsAndroidModule &&
                                                    wizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY));
    myModuleBuilder.myActivityTemplateParameterStep.setVisible(wizardState.myIsAndroidModule &&
                                                               wizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY));
    super.update();
  }

  public void createModule() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myModuleBuilder.createModule();
      }
    });
  }

  @Override
  public void templateChanged(String templateName) {
    myModuleBuilder.myConfigureAndroidModuleStep.refreshUiFromParameters();
    if (templateName.equals(LIB_NAME)) {
      myModuleBuilder.myWizardState.put(ATTR_IS_LIBRARY_MODULE, true);
    } else if (templateName.equals(APP_NAME)) {
      myModuleBuilder.myWizardState.put(ATTR_IS_LIBRARY_MODULE, false);
    }
  }

  /**
   * Create a template chooser step popuplated with the correct templates for the new modules.
   */
  private ChooseTemplateStep buildChooseModuleStep() {
    // We're going to build up our own list of templates here
    // This is a little hacky, we should clean this up later.
    ChooseTemplateStep chooseModuleStep =
      new ChooseTemplateStep(myModuleBuilder.myWizardState, null, myProject, AndroidIcons.Wizards.NewModuleSidePanel,
                             myModuleBuilder, this);
    List<ChooseTemplateStep.MetadataListItem> templateList =
      chooseModuleStep.getTemplateList(myModuleBuilder.myWizardState, CATEGORY_PROJECTS, null);
    // First, filter out the NewProject template and the NewModuleTemplate
    templateList = Lists.newArrayList(Iterables.filter(templateList, new Predicate<ChooseTemplateStep.MetadataListItem>() {
      @Override
      public boolean apply(@Nullable ChooseTemplateStep.MetadataListItem input) {
        String templateName = input.toString();
        return !(templateName.equals(PROJECT_NAME) || templateName.equals(MODULE_NAME));
      }
    }));
    // Now, we're going to add in two pointers to the same template
    File moduleTemplate = new File(TemplateManager.getTemplateRootFolder(),
                                   FileUtil.join(CATEGORY_PROJECTS, NewProjectWizardState.MODULE_TEMPLATE_NAME));
    TemplateManager manager = TemplateManager.getInstance();
    TemplateMetadata metadata = manager.getTemplate(moduleTemplate);

    ChooseTemplateStep.MetadataListItem appListItem = new ChooseTemplateStep.MetadataListItem(moduleTemplate, metadata) {
      @Override
      public String toString() {
        return APP_NAME;
      }
    };
    ChooseTemplateStep.MetadataListItem libListItem = new ChooseTemplateStep.MetadataListItem(moduleTemplate, metadata) {
      @Override
      public String toString() {
        return LIB_NAME;
      }
    };
    templateList.add(0, libListItem);
    templateList.add(0, appListItem);
    chooseModuleStep.setListData(templateList);
    return chooseModuleStep;
  }
}
