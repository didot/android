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
package com.android.tools.idea.tests.gui.framework.fixture.npw;

import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

public class ConfigureAndroidModuleStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<ConfigureAndroidModuleStepFixture, W> {

  ConfigureAndroidModuleStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ConfigureAndroidModuleStepFixture.class, wizard, target);
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<W> enterModuleName(@NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel("Application/Library name");
    replaceText(textField, text);
    return this;
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<W> selectMinimumSdkApi(@NotNull String api) {
    new ApiLevelComboBoxFixture(robot(), robot().finder().findByName(target(), "Mobile.minSdk", JComboBox.class))
      .selectApiLevel(api);
    return this;
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<W> setSourceLanguage(@NotNull String sourceLanguage) {
    new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Language", JComboBox.class, true))
      .selectItem(sourceLanguage);
    return this;
  }
}
