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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.run.editor.AndroidDebugger;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.module.Module;
import org.fest.swing.cell.JComboBoxCellReader;
import org.fest.swing.cell.JListCellReader;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.*;

public class EditConfigurationsDialogFixture extends IdeaDialogFixture<EditConfigurationsDialog> {
  @NotNull
  public static EditConfigurationsDialogFixture find(@NotNull Robot robot) {
    return new EditConfigurationsDialogFixture(robot, find(robot, EditConfigurationsDialog.class));
  }

  private EditConfigurationsDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<EditConfigurationsDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  private EditConfigurationsDialogFixture clickDebugger() {
    findAndClickLabelWhenEnabled(this, "Debugger");
    return this;
  }

  private static final JComboBoxCellReader DEBUGGER_PICKER_READER =
    (jComboBox, index) -> (GuiQuery.getNonNull(() -> ((AndroidDebugger)jComboBox.getItemAt(index)).getDisplayName()));

  private static final JListCellReader CONFIGURATION_CELL_READER = (jList, index) ->
    ((ConfigurationType)jList.getModel().getElementAt(index)).getDisplayName();

  private static final JComboBoxCellReader MODULE_PICKER_READER = (jComboBox, index) -> {
    Object element = jComboBox.getItemAt(index);
    if (element != null) {
      return ((Module)element).getName();
    }
    return null; // The element at index 0 is null. Deal with it specially.
  };

  @NotNull
  public EditConfigurationsDialogFixture selectDebuggerType(@NotNull String typeName) {
    clickDebugger();
    JComboBoxFixture comboBoxFixture = new JComboBoxFixture(robot(), robot().finder().findByType(target(), JComboBox.class));
    comboBoxFixture.replaceCellReader(DEBUGGER_PICKER_READER);
    comboBoxFixture.selectItem(typeName);
    return this;
  }

  @NotNull
  public void clickOk() {
    findAndClickOkButton(this);
  }

  @NotNull
  public EditConfigurationsDialogFixture clickAddNewConfigurationButton() {
    ActionButton addNewConfigurationButton = robot().finder().find(target(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton button) {
        String toolTipText = button.getToolTipText();
        return button.isShowing() && toolTipText != null && toolTipText.startsWith("Add New Configuration");
      }
    });
    robot().click(addNewConfigurationButton);
    return this;
  }

  @NotNull
  public EditConfigurationsDialogFixture selectConfigurationType(@NotNull String confTypeName) {
    JListFixture listFixture= new JListFixture(robot(), waitForPopup(robot()));
    listFixture.replaceCellReader(CONFIGURATION_CELL_READER);
    listFixture.clickItem(confTypeName);
    return this;
  }

  @NotNull
  public EditConfigurationsDialogFixture enterAndroidInstrumentedTestConfigurationName(@NotNull String text) {
    JTextField textField = robot().finder().findByLabel(target(), "Name:", JTextField.class, true);
    new JTextComponentFixture(robot(), textField).deleteText().enterText(text);
    return this;
  }

  @NotNull
  public EditConfigurationsDialogFixture selectModuleForAndroidInstrumentedTestsConfiguration(@NotNull String moduleName) {
    JComboBoxFixture comboBoxFixture = new JComboBoxFixture(robot(), robot().finder().findByType(ModulesComboBox.class, true));
    comboBoxFixture.replaceCellReader(MODULE_PICKER_READER);
    comboBoxFixture.selectItem(moduleName);
    return this;
  }
}
