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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.intellij.openapi.actionSystem.impl.ActionButton;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FlavorsTabFixture extends ProjectStructureDialogFixture {

  FlavorsTabFixture(JDialog dialog, IdeFrameFixture ideFrameFixture) {
    super(dialog, ideFrameFixture);
  }

  public FlavorsTabFixture clickAddButton() {
    ActionButton addButton = robot().finder().find(target(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton button) {
        String toolTipText = button.getToolTipText();
        return button.isShowing() && toolTipText != null && toolTipText.startsWith("Add");
      }
    });
    robot().click(addButton);
    return this;
  }

  public FlavorsTabFixture setFlavorName(final String name) {
    setTextField("Name:", name);
    return this;
  }

  public FlavorsTabFixture setVersionName(String versionName) {
    setTextField("Version Name", versionName);
    return this;
  }

  public FlavorsTabFixture setVersionCode(String versionCode) {
    setTextField("Version Code", versionCode);
    return this;
  }

  public FlavorsTabFixture setMinSdkVersion(String sdk) {
    new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Min Sdk Version", JComboBox.class, true)).selectItem(sdk);
    return this;
  }

  public FlavorsTabFixture setTargetSdkVersion(String sdk) {
    new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Target Sdk Version", JComboBox.class, true)).selectItem(sdk);
    return this;
  }

  private void setTextField(String label, String text) {
    JTextField textField = robot().finder().findByLabel(target(), label, JTextField.class, true);
    new JTextComponentFixture(robot(), textField).deleteText().enterText(text);
  }
}