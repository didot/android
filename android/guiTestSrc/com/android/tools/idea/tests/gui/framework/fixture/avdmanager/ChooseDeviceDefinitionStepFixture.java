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
package com.android.tools.idea.tests.gui.framework.fixture.avdmanager;

import com.android.sdklib.devices.Device;
import com.android.tools.idea.avdmanager.ChooseDeviceDefinitionStep;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardStepFixture;
import com.android.tools.idea.wizard.LabelWithEditLink;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.ComponentFixture;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ChooseDeviceDefinitionStepFixture extends AbstractWizardStepFixture {
  public ChooseDeviceDefinitionStepFixture(@NotNull Robot robot, @NotNull JRootPane rootPane) {
    super(robot, rootPane);
  }

  public ChooseDeviceDefinitionStepFixture enterSearchTerm(@NotNull String searchTerm) {
    SearchTextField searchField = robot.finder().findByType(target, SearchTextField.class);
    replaceText(searchField.getTextEditor(), searchTerm);
    return this;
  }

  public ChooseDeviceDefinitionStepFixture selectDeviceByName(@NotNull final String deviceName) {
    final TableView deviceList = robot.finder().find(target, new GenericTypeMatcher<TableView>(TableView.class) {
      @Override
      protected boolean isMatching(TableView component) {
        return component.getColumnCount() > 1; // There are two tables on this step, but the category table only has 1 column
      }
    });
    JTableFixture deviceListFixture = new JTableFixture(robot, deviceList);

    TableCell cell = deviceListFixture.cell(deviceName);
    deviceListFixture.selectCell(cell);
    return this;
  }
}
