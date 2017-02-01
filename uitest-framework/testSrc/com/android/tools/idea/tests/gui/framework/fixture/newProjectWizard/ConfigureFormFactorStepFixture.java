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
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ConfigureFormFactorStepFixture extends AbstractWizardStepFixture<ConfigureFormFactorStepFixture> {
  protected ConfigureFormFactorStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(ConfigureFormFactorStepFixture.class, robot, target);
  }

  @NotNull
  public ConfigureFormFactorStepFixture selectMinimumSdkApi(@NotNull FormFactor formFactor, @NotNull String api) {
    String formFactorName = formFactor.toString();
    JCheckBoxFixture checkBox =
      new JCheckBoxFixture(robot(), GuiTests.waitUntilShowing(robot(), target(), new GenericTypeMatcher<JCheckBox>(JCheckBox.class) {
        @Override
        protected boolean isMatching(@NotNull JCheckBox checkBox) {
          String text = checkBox.getText();
          // "startsWith" instead of "equals" because the UI may add "(Not installed)" at the end.
          return text != null && text.startsWith(formFactorName);
        }
      }));
    checkBox.requireEnabled().select();

    ApiLevelComboBoxFixture apiLevelComboBox =
      new ApiLevelComboBoxFixture(robot(), robot().finder().findByName(target(), formFactor.id + ".minSdk", JComboBox.class));
    apiLevelComboBox.selectApiLevel(api);
    return this;
  }

  @NotNull
  public ConfigureFormFactorStepFixture selectInstantAppSupport(@NotNull FormFactor formFactor) {
    findInstantAppCheckbox(formFactor).select();
    return this;
  }

  public ConfigureFormFactorStepFixture waitForErrorMessageToContain(@NotNull String errorMessage) {
    GuiTests.waitUntilShowing(robot(), new GenericTypeMatcher<JLabel>(JLabel.class) {
      @Override
      protected boolean isMatching(@NotNull JLabel label) {
        String text = label.getText();
        return text != null && text.contains(errorMessage);
      }
    });
    return this;
  }

  @NotNull
  public JCheckBoxFixture findInstantAppCheckbox(@NotNull FormFactor formFactor) {
    return new JCheckBoxFixture(robot(), robot().finder().findByName(target(), formFactor.id + ".instantApp", JCheckBox.class, false));
  }

}
