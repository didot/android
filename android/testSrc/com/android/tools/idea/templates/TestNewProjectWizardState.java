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
package com.android.tools.idea.templates;

import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.WizardConstants;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.templates.KeystoreUtils.getOrCreateDefaultDebugKeystore;
import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;
import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.templates.TemplateTest.ATTR_CREATE_ACTIVITY;

/**
 * Helper class that tracks the Project Wizard State (Project template, plus its Module and Activity State)
 */
public class TestNewProjectWizardState {
  private static final String APPLICATION_NAME = "My Application";
  private final Template myProjectTemplate;
  private final TestTemplateWizardState myModuleState = new TestTemplateWizardState();
  private final TestTemplateWizardState myActivityState = new TestTemplateWizardState();

  public TestNewProjectWizardState(@NotNull Template moduleTemplate) {
    myModuleState.myTemplate = moduleTemplate;
    myProjectTemplate = Template.createFromName(CATEGORY_PROJECTS, WizardConstants.PROJECT_TEMPLATE_NAME);

    // ------------------ MODULE STATE ---------------
    new TemplateValueInjector(myModuleState.getParameters())
      .setProjectDefaults(null);

    myModuleState.put(ATTR_APP_TITLE, APPLICATION_NAME);
    myModuleState.put(ATTR_HAS_APPLICATION_THEME, true);
    myModuleState.put(ATTR_IS_LAUNCHER, true);
    myModuleState.put(ATTR_IS_NEW_PROJECT, true);
    myModuleState.put(ATTR_THEME_EXISTS, true);
    myModuleState.put(ATTR_CREATE_ACTIVITY, true);
    myModuleState.put(ATTR_IS_LIBRARY_MODULE, false);
    myModuleState.put(ATTR_TOP_OUT, WizardUtils.getProjectLocationParent().getPath());

    final int DEFAULT_MIN = SdkVersionInfo.LOWEST_ACTIVE_API;
    myModuleState.put(ATTR_MIN_API_LEVEL, DEFAULT_MIN);
    myModuleState.put(ATTR_MIN_API, Integer.toString(DEFAULT_MIN));
    myModuleState.setParameterDefaults();

    // ------------------ ACTIVITY STATE ---------------
    try {
      myActivityState.put(ATTR_DEBUG_KEYSTORE_SHA1, KeystoreUtils.sha1(getOrCreateDefaultDebugKeystore()));
    }
    catch (Exception e) {
      Logger.getInstance(TestNewProjectWizardState.class).info("Could not compute SHA1 hash of debug keystore.", e);
    }

    updateParameters();
  }

  @NotNull
  public TestTemplateWizardState getActivityTemplateState() {
    return myActivityState;
  }

  public TestTemplateWizardState getModuleTemplateState() {
    return myModuleState;
  }

  /**
   * Call this to have this state object propagate common parameter values to sub-state objects
   * (i.e. states for other template wizards that are part of the same dialog).
   */
  public void updateParameters() {
    myActivityState.getParameters().putAll(myModuleState.getParameters());
  }

  public Template getProjectTemplate() {
    return myProjectTemplate;
  }
}
