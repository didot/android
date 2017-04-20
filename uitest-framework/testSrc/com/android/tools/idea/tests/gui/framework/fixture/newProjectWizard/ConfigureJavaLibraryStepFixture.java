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
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import com.android.tools.adtui.LabelWithEditButton;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ConfigureJavaLibraryStepFixture<T extends ContainerFixture<JDialog>> extends AbstractWizardStepFixture<ConfigureJavaLibraryStepFixture> {
  @NotNull private T myWizard;

  public ConfigureJavaLibraryStepFixture(@NotNull T wizard) {
    super(ConfigureJavaLibraryStepFixture.class, wizard.robot(), wizard.target().getRootPane());
    myWizard = wizard;
  }

  @NotNull
  public T getWizard() {
    return myWizard;
  }

  @NotNull
  public ConfigureJavaLibraryStepFixture<T> enterLibraryName(@NotNull String name) {
    JTextField textField = robot().finder().findByLabel(target(), "Library name:", JTextField.class, true);
    replaceText(textField, name);
    return this;
  }

  @NotNull
  public ConfigureJavaLibraryStepFixture<T> enterPackageName(@NotNull String name) {
    LabelWithEditButton editLabelContainer = robot().finder().findByType(target(), LabelWithEditButton.class);
    JButtonFixture editButton = new JButtonFixture(robot(), robot().finder().findByType(editLabelContainer, JButton.class));
    editButton.click();

    replaceText(findTextFieldWithLabel("Java package name:"), name);

    editButton.click(); // click "Done"
    return this;
  }

  @NotNull
  public ConfigureJavaLibraryStepFixture<T> enterClassName(@NotNull String name) {
    JTextField textField = robot().finder().findByLabel(target(), "Java class name:", JTextField.class, true);
    replaceText(textField, name);
    return this;
  }

  @NotNull
  public ConfigureJavaLibraryStepFixture<T> setCreateGitIgnore(boolean select) {
    selectCheckBoxWithText("Create .gitignore file", select);
    return this;
  }
}
