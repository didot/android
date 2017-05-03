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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardStepFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;


public class ConfigureBasicActivityStepFixture extends AbstractWizardStepFixture<ConfigureBasicActivityStepFixture> {

  /**
   * This is the list of labels used to find the right text input field.
   * There is no really good way to access this fields programmatically, as they are defined on the template files.
   * For example see: tools/base/templates/activities/BasicActivity/template.xml
   */
  public enum ActivityTextField {
    NAME("Activity Name:"),
    LAYOUT("Layout Name:"),
    TITLE("Title:"),
    HIERARCHICAL_PARENT("Hierarchical Parent:"),
    PACKAGE_NAME("Package name:");

    private final String labelText;

    ActivityTextField(String labelText) {
      this.labelText = labelText;
    }

    private String getLabelText() {
      return labelText;
    }
  }

  private final AbstractWizardFixture myParent;

  private final IdeFrameFixture myIdeFrameFixture;

  ConfigureBasicActivityStepFixture(
    @NotNull IdeFrameFixture ideFrameFixture, @NotNull JRootPane target, @NotNull AbstractWizardFixture parent) {
    super(ConfigureBasicActivityStepFixture.class, ideFrameFixture.robot(), target);
    this.myIdeFrameFixture = ideFrameFixture;
    this.myParent = parent;
  }

  @NotNull
  public ConfigureBasicActivityStepFixture selectLauncherActivity() {
    JCheckBox checkBox = robot().finder().find(target(), Matchers.byText(JCheckBox.class, "Launcher Activity"));
    new JCheckBoxFixture(robot(), checkBox).select();
    return this;
  }

  @NotNull
  public ConfigureBasicActivityStepFixture selectUseFragment() {
    JCheckBox checkBox = robot().finder().find(target(), Matchers.byText(JCheckBox.class, "Use a Fragment"));
    new JCheckBoxFixture(robot(), checkBox).select();
    return this;
  }

  @NotNull
  public ConfigureBasicActivityStepFixture enterTextFieldValue(@NotNull ActivityTextField activityField, @NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel(activityField.getLabelText());
    replaceText(textField, text);

    return this;
  }

  @NotNull
  public String getTextFieldValue(@NotNull ActivityTextField activityField) {
    return findTextFieldWithLabel(activityField.getLabelText()).getText();
  }

  @NotNull
  public ConfigureBasicActivityStepFixture undoTextFieldValue(@NotNull ActivityTextField activityField) {
    JTextComponent textField = findTextFieldWithLabel(activityField.getLabelText());
    robot().rightClick(textField);

    JMenuItem popup = GuiTests.waitUntilShowing(robot(), null, Matchers.byText(JMenuItem.class, "Restore default value"));
    robot().click(popup);

    return this;
  }

  @NotNull
  public ConfigureBasicActivityStepFixture setTargetSourceSet(@NotNull String targetSourceSet) {
    new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Target Source Set:", JComboBox.class, true))
      .selectItem(targetSourceSet);
    return this;
  }

  @NotNull
  public ConfigureBasicActivityStepFixture setSourceLanguage(@NotNull String sourceLanguage) {
    new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Source Language:", JComboBox.class, true))
      .selectItem(sourceLanguage);
    return this;
  }

  @NotNull
  public IdeFrameFixture clickFinish() {
    myParent.clickFinish();
    Wait.seconds(5).expecting("dialog to disappear").until(() -> !target().isShowing());
    return myIdeFrameFixture;
  }
}