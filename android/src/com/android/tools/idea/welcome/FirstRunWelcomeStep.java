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
package com.android.tools.idea.welcome;

import com.android.tools.idea.wizard.FormFactorUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Welcome page for the first run wizard
 */
public final class FirstRunWelcomeStep extends FirstRunWizardStep {
  private JPanel myRoot;
  private JLabel myIcons;
  private JPanel myExistingSdkMessage;
  private JPanel myNewSdkMessage;

  public FirstRunWelcomeStep(boolean sdkExists) {
    super("Welcome");
    myIcons.setIcon(FormFactorUtils.getFormFactorsImage(myIcons, false));
    myExistingSdkMessage.setVisible(sdkExists);
    myNewSdkMessage.setVisible(!sdkExists);
    setComponent(myRoot);
  }

  @Override
  public void init() {

  }

  @Override
  public boolean isStepVisible() {
    return InstallerData.get() == null;
  }

  @NotNull
  @Override
  public JLabel getMessageLabel() {
    throw new IllegalStateException();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    // Doesn't matter
    return myIcons;
  }
}
