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

import com.android.tools.idea.tests.gui.framework.fixture.JComponentFixture;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.ui.components.JBLabel;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JTextComponentMatcher;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

import static org.fest.swing.edt.GuiTask.execute;

public abstract class AbstractWizardStepFixture<S> extends JComponentFixture<S, JRootPane> {
  protected AbstractWizardStepFixture(@NotNull Class<S> selfType, @NotNull Robot robot, @NotNull JRootPane target) {
    super(selfType, robot, target);
  }

  @NotNull
  protected JComboBoxFixture findComboBoxWithLabel(@NotNull String label) {
    JComboBox comboBox = robot().finder().findByLabel(target(), label, JComboBox.class, true);
    return new JComboBoxFixture(robot(), comboBox);
  }

  @NotNull
  protected JTextComponent findTextFieldWithLabel(@NotNull String label) {
    // The label text may reference the input directly (a subclass of JTextComponent), or it may reference the container of the input
    // (for example ReferenceEditorComboWithBrowseButton (JPanel) or an EditorComboBox)
    JComponent comp = robot().finder().findByLabel(target(), label, JComponent.class, true);
    return robot().finder().find(comp, JTextComponentMatcher.any());
  }

  @NotNull
  protected JCheckBoxFixture selectCheckBoxWithText(@NotNull String text, boolean select) {
    JCheckBox cb = (JCheckBox)robot().finder().find(c -> c.isShowing() && c instanceof JCheckBox && ((JCheckBox)c).getText().equals(text));
    JCheckBoxFixture checkBox = new JCheckBoxFixture(robot(), cb);
    if (select) {
      checkBox.select();
    }
    else {
      checkBox.deselect();
    }
    return checkBox;
  }

  protected void replaceText(@NotNull JTextComponent textField, @NotNull String text) {
    // TODO: setText() does not use the robot but instead sets the text programmatically, which is not great.
    // Better use deleteText() here for the same effect, but we need to update FEST
    // 1 - FEST deleteText() calls scrollToVisible and EditorComponentImpl throws exception when it has only empty text
    // 2 - FEST assumes that all input components support "delete-previous" -> Throws ActionFailedException
    // 3 - IJ EditorComponent executes setText() on a transaction Guard, and it will be postponed if the shown textField is on a dialog
    execute(() -> ((TransactionGuardImpl) TransactionGuard.getInstance()).performUserActivity(
      () -> textField.setText(text)
    ));
  }

  public String getValidationText() {
    JBLabel validationLabel = robot().finder().findByName(target(), "ValidationLabel", JBLabel.class);
    Wait.seconds(1).expecting("validation text to appear").until(() -> validationLabel.getText().matches(".*\\S.*"));
    return validationLabel.getText();
  }
}
