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
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.core.MouseButton;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;


public class ConfigureBasicActivityStepFixture<W extends AbstractWizardFixture> extends AbstractWizardStepFixture<ConfigureBasicActivityStepFixture, W> {

  /**
   * This is the list of labels used to find the right text input field.
   * There is no really good way to access this fields programmatically, as they are defined on the template files.
   * For example see: tools/base/templates/activities/BasicActivity/template.xml
   */
  public enum ActivityTextField {
    NAME("Activity Name"),
    LAYOUT("Layout Name"),
    TITLE("Title"),
    HIERARCHICAL_PARENT("Hierarchical Parent"),
    PACKAGE_NAME("Package name");

    private final String labelText;

    ActivityTextField(String labelText) {
      this.labelText = labelText;
    }

    private String getLabelText() {
      return labelText;
    }
  }

  ConfigureBasicActivityStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ConfigureBasicActivityStepFixture.class, wizard, target);
  }

  @NotNull
  public ConfigureBasicActivityStepFixture<W> selectLauncherActivity() {
    JCheckBox checkBox = robot().finder().find(target(), Matchers.byText(JCheckBox.class, "Launcher Activity"));
    new JCheckBoxFixture(robot(), checkBox).select();
    return this;
  }

  @NotNull
  public ConfigureBasicActivityStepFixture<W> enterTextFieldValue(@NotNull ActivityTextField activityField, @NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel(activityField.getLabelText());
    robot().click(textField, MouseButton.LEFT_BUTTON, 3); // select all; particularly for pseudo-JTextComponent EditorComponentImpl
    new JTextComponentFixture(robot(), textField).enterText(text);
    return this;
  }

  @NotNull
  public String getTextFieldValue(@NotNull ActivityTextField activityField) {
    return findTextFieldWithLabel(activityField.getLabelText()).getText();
  }

  @NotNull
  public ConfigureBasicActivityStepFixture<W> setSourceLanguage(@NotNull String sourceLanguage) {
    new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Source Language", JComboBox.class, true))
      .selectItem(sourceLanguage);
    return this;
  }

  @NotNull
  public ConfigureBasicActivityStepFixture<W> setTargetSourceSet(@NotNull String targetSource) {
    new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Target Source Set", JComboBox.class, true))
      .selectItem(targetSource);
    return this;
  }
}
