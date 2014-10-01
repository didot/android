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

import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JButtonMatcher;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.fixture.ComponentFixture;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

/**
 * Base class for fixtures which control wizards that extend {@link com.android.tools.idea.wizard.DynamicWizard}
 */
public abstract class AbstractWizardFixture extends ComponentFixture<JDialog> {

  public AbstractWizardFixture(Robot robot, JDialog target) {
    super(robot, target);
  }

  @NotNull
  protected JRootPane findStepWithTitle(@NotNull final String title) {
    final JRootPane rootPane = target.getRootPane();
    Pause.pause(new Condition("'Panel with title \"" + title + "\" is visible'") {
      @Override
      public boolean test() {
        Collection<JLabel> found = robot.finder().findAll(rootPane, JLabelMatcher.withText(title).andShowing());
        return !found.isEmpty();
      }
    });
    return rootPane;
  }

  @NotNull
  protected JButton findButtonByText(@NotNull String text) {
    return robot.finder().find(target, JButtonMatcher.withText(text).andShowing());
  }
}
