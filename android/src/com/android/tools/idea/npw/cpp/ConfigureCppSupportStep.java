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
package com.android.tools.idea.npw.cpp;

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.idea.npw.project.NewProjectModel;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.OptionalProperty;
import com.android.tools.idea.ui.properties.core.OptionalValueProperty;
import com.android.tools.idea.ui.properties.swing.SelectedItemProperty;
import com.android.tools.idea.ui.properties.swing.SelectedProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.base.Joiner;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBLabel;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;

import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * Step for configuring native (C++) related parameters in new project wizard
 */
public class ConfigureCppSupportStep extends ModelWizardStep<NewProjectModel> {
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private JPanel myRootPanel;
  private JComboBox<CppStandardType> myCppStandardCombo;
  private JCheckBox myExceptionSupportCheck;
  private JBLabel myIconLabel;
  private JComboBox<RuntimeLibraryType> myRuntimeLibraryCombo;
  private JCheckBox myRttiSupportCheck;
  private JBLabel myRuntimeLibraryLabel;

  public ConfigureCppSupportStep(@NotNull NewProjectModel model) {
    super(model, message("android.wizard.activity.add.cpp"));

    myIconLabel.setIcon(AndroidIcons.Wizards.CppConfiguration);

    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myRuntimeLibraryCombo.setModel(new CollectionComboBoxModel<>(Arrays.asList(RuntimeLibraryType.values())));
    OptionalProperty<RuntimeLibraryType> runtimeLibrary = new OptionalValueProperty<>(RuntimeLibraryType.GABIXX);
    myBindings.bindTwoWay(new SelectedItemProperty<>(myRuntimeLibraryCombo), runtimeLibrary);

    myCppStandardCombo.setModel(new CollectionComboBoxModel<>(Arrays.asList(CppStandardType.values())));
    OptionalProperty<CppStandardType> cppStandard = new OptionalValueProperty<>(CppStandardType.DEFAULT);
    myBindings.bindTwoWay(new SelectedItemProperty<>(myCppStandardCombo), cppStandard);

    BoolProperty exceptionSupport = new BoolValueProperty();
    myBindings.bindTwoWay(new SelectedProperty(myExceptionSupportCheck), exceptionSupport);

    BoolProperty rttiSupport = new BoolValueProperty();
    myBindings.bindTwoWay(new SelectedProperty(myRttiSupportCheck), rttiSupport);

    myListeners.listenAll(runtimeLibrary, cppStandard, exceptionSupport, rttiSupport).withAndFire(() -> {
      final ArrayList<Object> flags = new ArrayList<>();
      flags.add(cppStandard.getValueOr(CppStandardType.DEFAULT).getCompilerFlag());
      flags.add(rttiSupport.get() ? "-frtti" : null);
      flags.add(exceptionSupport.get() ? "-fexceptions" : null);

      getModel().cppFlags().set(Joiner.on(' ').skipNulls().join(flags));
    });

    // TODO: un-hide UI components once dsl support would be available
    myRuntimeLibraryCombo.setVisible(false);
    myRuntimeLibraryLabel.setVisible(false);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myCppStandardCombo;
  }

  @Override
  protected boolean shouldShow() {
    return getModel().enableCppSupport().get();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }
}
