/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Document;
import java.util.Set;

import static com.android.tools.idea.wizard.WizardConstants.APPLICATION_NAME_KEY;
import static com.android.tools.idea.wizard.WizardConstants.PROJECT_LOCATION_KEY;
import static com.android.tools.idea.wizard.WizardConstants.SELECTED_MODULE_TYPE_KEY;
import static com.android.tools.idea.wizard.ConfigureAndroidProjectStep.PACKAGE_NAME_DERIVER;
import static com.android.tools.idea.wizard.ConfigureAndroidProjectStep.SAVED_COMPANY_DOMAIN;

/**
 * Configuration for a new Android module
 */
public class ConfigureAndroidModuleStepDynamic extends DynamicWizardStepWithHeaderAndDescription {
  private static final Logger LOG = Logger.getInstance(ConfigureAndroidModuleStepDynamic.class);

  private CreateModuleTemplate myModuleType;
  private FormFactorApiComboBox mySdkControls;
  private Project myProject;
  private JTextField myModuleName;
  private JPanel myPanel;
  private JTextField myAppName;
  private LabelWithEditLink myPackageName;

  public ConfigureAndroidModuleStepDynamic(@Nullable Project project, @Nullable Disposable parentDisposable) {
    super("Configure your new module", null, null, parentDisposable);
    myProject = project;
    setBodyComponent(myPanel);
  }

  @Override
  public void init() {
    String projectLocation = myState.get(PROJECT_LOCATION_KEY);
    super.init();
    myState.put(PROJECT_LOCATION_KEY, projectLocation);
    register(FormFactorUtils.getModuleNameKey(getModuleType().formFactor), myModuleName);
    CreateModuleTemplate moduleType = getModuleType();
    mySdkControls.init(moduleType.formFactor, moduleType.templateMetadata.getMinSdk());
    mySdkControls.register(this);

    register(WizardConstants.APPLICATION_NAME_KEY, myAppName);
    register(WizardConstants.PACKAGE_NAME_KEY, myPackageName, new ComponentBinding<String, LabelWithEditLink>() {
      @Override
      public void setValue(@Nullable String newValue, @NotNull LabelWithEditLink component) {
        newValue = newValue == null ? "" : newValue;
        component.setText(newValue);
      }

      @Nullable
      @Override
      public String getValue(@NotNull LabelWithEditLink component) {
        return component.getText();
      }

      @Nullable
      @Override
      public Document getDocument(@NotNull LabelWithEditLink component) {
        return component.getDocument();
      }
    });
    registerValueDeriver(WizardConstants.PACKAGE_NAME_KEY, PACKAGE_NAME_DERIVER);

    if (StringUtil.isEmptyOrSpaces(myState.get(APPLICATION_NAME_KEY))) {
      String name = myState.getNotNull(WizardConstants.IS_LIBRARY_KEY, false) ? "My Library" : "My Application";
      myState.put(WizardConstants.APPLICATION_NAME_KEY, name);
      String savedCompanyDomain = PropertiesComponent.getInstance().getValue(SAVED_COMPANY_DOMAIN);
      myState.put(WizardConstants.COMPANY_DOMAIN_KEY, savedCompanyDomain);
    }
    super.init();
  }

  private void createUIComponents() {
    mySdkControls = new FormFactorApiComboBox();
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    CreateModuleTemplate moduleType = getModuleType();
    if (moduleType != null && moduleType.formFactor != null && moduleType.templateMetadata != null) {
      myModuleType = moduleType;
      registerValueDeriver(FormFactorUtils.getModuleNameKey(moduleType.formFactor), ourModuleNameDeriver);
    } else {
      LOG.error("init() Called on ConfigureAndroidModuleStepDynamic with an incorrect selected ModuleType");
    }
    if (mySdkControls != null) {
      mySdkControls.loadSavedApi();
    }
    invokeUpdate(null);
  }

  @Nullable
  private CreateModuleTemplate getModuleType() {
    ModuleTemplate moduleTemplate = myState.get(SELECTED_MODULE_TYPE_KEY);
    if (moduleTemplate instanceof CreateModuleTemplate) {
      CreateModuleTemplate type = (CreateModuleTemplate)moduleTemplate;
      if (type.formFactor != null && type.templateMetadata != null) {
        return type;
      }
    }
    return null;
  }

  @Override
  public void deriveValues(Set<ScopedStateStore.Key> modified) {
    super.deriveValues(modified);
    if (mySdkControls != null) {
      mySdkControls.deriveValues(myState, modified);
    }
  }

  @Override
  public boolean validate() {
    setErrorHtml("");
    return validateAppName() && validatePackageName() && validateApiLevel();
  }

  private boolean validateApiLevel() {
    if (mySdkControls == null || mySdkControls.getItemCount() < 1) {
      setErrorHtml("No supported platforms found. Please install the proper platform or add-on through the SDK manager.");
      return false;
    }
    return true;
  }

  private final ValueDeriver<String> ourModuleNameDeriver = new ValueDeriver<String>() {
    @Nullable
    @Override
    public Set<ScopedStateStore.Key<?>> getTriggerKeys() {
      return makeSetOf(APPLICATION_NAME_KEY);
    }

    @Nullable
    @Override
    public String deriveValue(@NotNull ScopedStateStore state, @Nullable ScopedStateStore.Key changedKey, @Nullable String currentValue) {
      String appName = state.get(APPLICATION_NAME_KEY);
      if (appName == null) {
        appName = myModuleType.formFactor.toString();
      }
      return WizardUtils.computeModuleName(appName, getProject());
    }
  };

  protected boolean validateAppName() {
    String appName = myState.get(APPLICATION_NAME_KEY);
    if (appName == null || appName.isEmpty()) {
      setErrorHtml("Please enter an application name (shown in launcher), or a descriptive name for your library");
      return false;
    } else if (Character.isLowerCase(appName.charAt(0))) {
      setErrorHtml("The application name for most apps begins with an uppercase letter");
    }
    return true;
  }

  protected boolean validatePackageName() {
    String packageName = myState.get(WizardConstants.PACKAGE_NAME_KEY);
    if (packageName == null) {
      setErrorHtml("Please enter a package name (This package uniquely identifies your application or library)");
      return false;
    } else {
      String message = AndroidUtils.validateAndroidPackageName(packageName);
      if (message != null) {
        setErrorHtml("Invalid package name: " + message);
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isStepVisible() {
    return getModuleType() != null;
  }

  @NotNull
  @Override
  public String getStepName() {
    return "New Android Module Configuration";
  }

  @Nullable
  @Override
  protected JComponent getHeader() {
    return NewModuleWizardDynamic.buildHeader();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myAppName;
  }
}
