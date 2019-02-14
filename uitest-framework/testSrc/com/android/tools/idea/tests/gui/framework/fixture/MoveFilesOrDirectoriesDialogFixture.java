/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesDialog;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

public class MoveFilesOrDirectoriesDialogFixture extends IdeaDialogFixture<MoveFilesOrDirectoriesDialog> {

  @NotNull
  public static MoveFilesOrDirectoriesDialogFixture find(@NotNull Robot robot) {
    return new MoveFilesOrDirectoriesDialogFixture(robot, find(robot, MoveFilesOrDirectoriesDialog.class));
  }

  private MoveFilesOrDirectoriesDialogFixture(@NotNull Robot robot,
                                              @NotNull DialogAndWrapper<MoveFilesOrDirectoriesDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  public void clickOk() {
    GuiTests.findAndClickOkButton(this);
    waitUntilNotShowing();
  }
}
