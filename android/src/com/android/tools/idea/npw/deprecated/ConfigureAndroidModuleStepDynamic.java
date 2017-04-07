/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.deprecated;

import com.android.tools.idea.npw.*;
import com.android.tools.adtui.LabelWithEditLink;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithHeaderAndDescription;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

import static com.android.tools.idea.npw.deprecated.ConfigureAndroidProjectStep.PACKAGE_NAME_DERIVER;
import static com.android.tools.idea.npw.deprecated.ConfigureAndroidProjectStep.SAVED_COMPANY_DOMAIN;
import static com.android.tools.idea.wizard.WizardConstants.*;
import static com.intellij.openapi.util.text.StringUtil.*;

/**
 * Configuration for a new Android module
 */
public class ConfigureAndroidModuleStepDynamic extends DynamicWizardStepWithHeaderAndDescription {
  private static final Logger LOG = Logger.getInstance(ConfigureAndroidModuleStepDynamic.class);
  private static final String TITLE = "Configure your new module";

  private final @NotNull FormFactor myFormFactor;
  private final @NotNull ScopedStateStore.Key<String> myAppNameKey;

  private final ValueDeriver<String> myModuleNameDeriver = new ValueDeriver<String>() {
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
        appName = myModuleType.getFormFactor().toString();
      }
      return WizardUtils.computeModuleName(appName, getProject());
    }
  };

  private CreateModuleTemplate myModuleType;
  private FormFactorApiComboBox mySdkControls;
  private JTextField myModuleName;
  private JPanel myPanel;
  private JTextField myAppName;
  private LabelWithEditLink myPackageName;
  private JCheckBox myInstantAppCheckbox;


  public ConfigureAndroidModuleStepDynamic(@Nullable Disposable parentDisposable, @NotNull FormFactor formFactor) {
    super(TITLE, null, parentDisposable);
    setBodyComponent(myPanel);
    myFormFactor = formFactor;
    myAppNameKey = FormFactorUtils.getModuleNameKey(myFormFactor);
  }

  @Override
  public void init() {
    String projectLocation = myState.get(PROJECT_LOCATION_KEY);
    super.init();
    myState.put(PROJECT_LOCATION_KEY, projectLocation);
    CreateModuleTemplate template = getFormfactorModuleTemplate();
    assert template != null;
    register(myAppNameKey, myModuleName);
    myModuleName.setName("ModuleName");
    mySdkControls.init(template.getFormFactor(), template.getMetadata().getMinSdk(), null, null, null);
    mySdkControls.registerWith(this);

    register(APPLICATION_NAME_KEY, myAppName);
    register(PACKAGE_NAME_KEY, myPackageName);
    registerValueDeriver(PACKAGE_NAME_KEY, PACKAGE_NAME_DERIVER);

    if (isEmptyOrSpaces(myState.get(APPLICATION_NAME_KEY))) {
      String name = myState.getNotNull(IS_LIBRARY_KEY, false) ? "My Library" : "My Application";
      myState.put(APPLICATION_NAME_KEY, name);
      String savedCompanyDomain = PropertiesComponent.getInstance().getValue(SAVED_COMPANY_DOMAIN);
      myState.put(COMPANY_DOMAIN_KEY, savedCompanyDomain);
    }

    if (template.getFormFactor() == FormFactor.MOBILE && myState.get(WH_SDK_ENABLED_KEY)) {
      register(IS_INSTANT_APP_KEY, myInstantAppCheckbox);
      myInstantAppCheckbox.setVisible(true);
    }

    super.init();
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    CreateModuleTemplate template = getFormfactorModuleTemplate();
    if (template != null && template.getFormFactor() != null && template.getMetadata() != null) {
      myModuleType = template;
      registerValueDeriver(FormFactorUtils.getModuleNameKey(template.getFormFactor()), myModuleNameDeriver);

      // As we are re-using the SAME Step object (this) and the SAME Path object (NewFormFactorModulePath) for both
      // new "phone and tablet module" and "android lib module" we have to update the title here.
      getComponent(); // ensure header created
      myHeader.setTitle(myModuleType.getName());
    } else {
      LOG.error("init() Called on ConfigureAndroidModuleStepDynamic with an incorrect selected ModuleType");
    }
    if (mySdkControls != null) {
      mySdkControls.loadSavedApi();
    }
    invokeUpdate(null);
  }

  @Override
  public boolean commitStep() {
    boolean commit = super.commitStep();

    if (commit && myPath instanceof NewFormFactorModulePath) {
      // Fix for https://code.google.com/p/android/issues/detail?id=78730
      // where creating a new module results in the activity classes with the
      // wrong package name.
      //
      // The reason that happens is that for some reason the packageName
      // parameter in the TemplateParameterStep2 state does not get updated.
      // And that happens because it's *normally* updated by
      // NewFormFactorModulePath#onPathStarted, which in the New Project Wizard
      // is called *after* the user has edited the package name in the first
      // wizard panel and has moved on to the next panel. That means that when
      // onPathStarted is called, and it pushes the default package into the
      // TemplateParameterStep2 map, it contains the updated package.
      //
      // However, in the new module wizard, NewFormFactorModulePath#onPathStarted
      // is called *before* the package has been edited.
      //
      // Having those values be "committed" from onPathStarted in NewFormFactorModule
      // seems wrong, but since we're in high resistance, we're going for a minimal
      // impact fix here: Fix this specifically for the New Module Wizard (this class)
      // where we deliberately update those values only when the values are
      // actually committed from the new module wizard package panel.
      ((NewFormFactorModulePath)myPath).updatePackageDerivedValues();
    }

    return commit;
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
    return validateAppName() && validateModuleName() && validatePackageName() && validateApiLevel();
  }

  @Override
  public boolean isStepVisible() {
    return getFormfactorModuleTemplate() != null;
  }

  @NotNull
  @Override
  public String getStepName() {
    return "New Android Module Configuration";
  }

  @NotNull
  @Override
  protected WizardStepHeaderSettings getStepHeader() {
    // this creates the initial setting, we will later update the title in {@link #onEnterStep()}
    return getFormfactorModuleTemplate() == null
           ? NewModuleWizardDynamic.buildHeader()
           : WizardStepHeaderSettings.createTitleAndIconHeader(getFormfactorModuleTemplate().getName(), myFormFactor.getIcon());
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myAppName;
  }

  private void createUIComponents() {
    mySdkControls = new FormFactorApiComboBox();
  }

  @Nullable
  private CreateModuleTemplate getFormfactorModuleTemplate() {
    ModuleTemplate moduleTemplate = myState.get(SELECTED_MODULE_TYPE_KEY);
    if (moduleTemplate instanceof CreateModuleTemplate) {
      CreateModuleTemplate type = (CreateModuleTemplate)moduleTemplate;
      if (type.getFormFactor() != null && type.getMetadata() != null) {
        return type;
      }
    }
    return null;
  }

  private boolean validateApiLevel() {
    if (mySdkControls == null || mySdkControls.getItemCount() < 1) {
      setErrorHtml("No supported platforms found. Please install the proper platform or add-on through the SDK manager.");
      return false;
    }
    return true;
  }

  private boolean validateAppName() {
    String appName = trim(myState.get(APPLICATION_NAME_KEY));
    if (isEmpty(appName)) {
      setErrorHtml("Please enter an application name (shown in launcher), or a descriptive name for your library");
      return false;
    } else if (!myState.getNotNull(IS_LIBRARY_KEY, false) && Character.isLowerCase(appName.charAt(0))) {
      setErrorHtml("The application name for most apps begins with an uppercase letter");
    }
    return true;
  }

  private boolean validateModuleName() {
    String moduleName = trim(myState.get(myAppNameKey));
    if (isEmpty(moduleName)) {
      setErrorHtml("Please enter an module name");
      return false;
    }
    else if (!WizardUtils.isUniqueModuleName(moduleName, getProject())) {
      setErrorHtml(String.format("A module named '%s' already exists", moduleName));
      return false;
    }
    return true;
  }

  private boolean validatePackageName() {
    String packageName = myState.get(PACKAGE_NAME_KEY);
    if (packageName == null) {
      setErrorHtml("Please enter a package name (This package uniquely identifies your application or library)");
      return false;
    } else {
      // Fully qualified reference can be deleted once com.android.tools.idea.npw.WizardUtils has been removed
      String message = com.android.tools.idea.ui.wizard.WizardUtils.validatePackageName(packageName);
      if (message != null) {
        setErrorHtml("Invalid package name: " + message);
        return false;
      }
    }
    return true;
  }
}
