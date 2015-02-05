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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardFixture;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DeviceEditWizardFixture extends AbstractWizardFixture {

  public static DeviceEditWizardFixture find(@NotNull Robot robot) {
    JDialog dialog = GuiTests.waitUntilFound(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(JDialog dialog) {
        return "Hardware Profile Configuration".equals(dialog.getTitle()) && dialog.isShowing();
      }
    });
    return new DeviceEditWizardFixture(robot, dialog);
  }

  public DeviceEditWizardFixture(Robot robot, JDialog target) {
    super(robot, target);
  }

  public ConfigureDeviceOptionsStepFixture getConfigureDeviceOptionsStep() {
    JRootPane rootPane = findStepWithTitle("Configure Hardware Profile");
    return new ConfigureDeviceOptionsStepFixture(robot, rootPane);
  }

  @NotNull
  public DeviceEditWizardFixture clickFinish() {
    JButton button = findButtonByText("Finish");
    robot.click(button);
    return this;
  }
}
