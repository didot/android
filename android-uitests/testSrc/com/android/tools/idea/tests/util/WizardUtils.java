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
package com.android.tools.idea.tests.util;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import org.jetbrains.annotations.NotNull;

public final class WizardUtils {
  private WizardUtils() {
  }

  /** @deprecated Avoid until b/66680171 is otherwise addressed. */
  @Deprecated
  public static void createNewProject(@NotNull GuiTestRule guiTest) {
    createNewProject(guiTest, "Empty Activity");
  }

  /** @deprecated Avoid until b/66680171 is otherwise addressed. */
  @Deprecated
  public static void createNewProject(@NotNull GuiTestRule guiTest, @NotNull String activity) {
    if (StudioFlags.NPW_DYNAMIC_APPS.get()) {
      guiTest
        .welcomeFrame()
        .createNewProject()
        .getChooseAndroidProjectStep()
        .chooseActivity(activity)
        .wizard()
        .clickNext()
        .getConfigureNewAndroidProjectStep()
        .enterPackageName("com.google.myapplication")
        .wizard()
        .clickFinish();
    }
    else {
      NewProjectWizardFixture wizard = guiTest.welcomeFrame().createNewProject();

      wizard.getConfigureAndroidProjectStep().enterCompanyDomain("google.com");
      wizard.clickNext();

      wizard.clickNext();

      wizard.chooseActivity(activity);
      wizard.clickNext();

      wizard.clickFinish();
    }
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
  }
}
