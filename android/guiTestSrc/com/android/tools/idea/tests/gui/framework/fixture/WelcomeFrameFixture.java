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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.ComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class WelcomeFrameFixture extends ComponentFixture<WelcomeFrame> {
  @NotNull
  public static WelcomeFrameFixture find(@NotNull Robot robot) {
    JFrame visibleFrame = WindowManagerEx.getInstanceEx().findVisibleFrame();
    if (visibleFrame instanceof WelcomeFrame) {
      return new WelcomeFrameFixture(robot, (WelcomeFrame)visibleFrame);
    }
    throw new ComponentLookupException("Unable to find 'Welcome' window");
  }

  private WelcomeFrameFixture(@NotNull Robot robot, @NotNull WelcomeFrame target) {
    super(robot, target);
  }

  public ActionButtonWithTextFixture newProjectButton() {
    ActionButtonWithText button = robot.finder().find(target, new GenericTypeMatcher<ActionButtonWithText>(ActionButtonWithText.class) {
      @Override
      protected boolean isMatching(ActionButtonWithText buttonWithText) {
        AnAction action = buttonWithText.getAction();
        if (action != null) {
          String id = ActionManager.getInstance().getId(action);
          return "WelcomeScreen.CreateNewProject".equals(id);
        }
        return false;
      }
    });
    return new ActionButtonWithTextFixture(robot, button);
  }
}
