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
package com.android.tools.idea.npw.instantapp;

import com.android.tools.adtui.LabelWithEditButton;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.npw.validator.ModuleValidator;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static org.jetbrains.android.util.AndroidBundle.message;


/**
 * This class configures Instant App specific data such as the name of the atom to be created and the path to assign to the default Activity
 */
public final class ConfigureInstantModuleStep extends ModelWizardStep<NewModuleModel> {
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  @NotNull private StudioWizardStepPanel myRootPanel;
  @NotNull private ValidatorPanel myValidatorPanel;

  private JTextField mySplitNameField;
  private JPanel myPanel;
  private LabelWithEditButton myPackageName;

  public ConfigureInstantModuleStep(@NotNull NewModuleModel moduleModel, StringProperty projectLocation) {
    super(moduleModel, message("android.wizard.module.new.instant.app.title"));

    NewModuleModel model = getModel();
    TextProperty splitFieldText = new TextProperty(mySplitNameField);
    TextProperty packageNameText = new TextProperty(myPackageName);

    BoolProperty isPackageNameSynced = new BoolValueProperty(true);
    ObservableString computedFeatureModulePackageName = model.computedFeatureModulePackageName();
    myBindings.bind(model.packageName(), packageNameText, model.instantApp());
    myBindings.bind(packageNameText, computedFeatureModulePackageName, isPackageNameSynced);

    myBindings.bindTwoWay(splitFieldText, model.splitName());
    myListeners.receive(packageNameText, value -> isPackageNameSynced.set(value.equals(computedFeatureModulePackageName.get())));

    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myValidatorPanel.registerValidator(splitFieldText, new ModuleValidator(projectLocation));

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return mySplitNameField;
  }

  @Override
  protected boolean shouldShow() {
    return getModel().instantApp().get();
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    // For instant apps, the Module name is the same as the split name.
    // Doing the assignment during onProceeding guarantees a valid module name and also that we are an instant app
    getModel().moduleName().set(getModel().splitName().get());
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }
}
