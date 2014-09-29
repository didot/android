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

import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardStepFixture;
import com.intellij.ui.table.TableView;
import org.fest.swing.cell.JTableCellReader;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.data.TableCell;
import org.fest.swing.data.TableCellFinder;
import org.fest.swing.data.TableCellInRowByValue;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ChooseSystemImageStepFixture extends AbstractWizardStepFixture {
  protected ChooseSystemImageStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(robot, target);
  }

  public ChooseSystemImageStepFixture selectSystemImage(@NotNull String releaseName,
                                                        @NotNull String apiLevel,
                                                        @NotNull String abiType,
                                                        @NotNull String targetName) {
    final TableView systemImageList = robot.finder().findByType(target, TableView.class);
    JTableFixture systemImageListFixture = new JTableFixture(robot, systemImageList);

    JTableCellFixture cell = systemImageListFixture.cell(
      TableCellInRowByValue.rowWithValue(releaseName, apiLevel, abiType, targetName).column(0));
    cell.select();
    return this;
  }
}
