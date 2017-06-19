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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WizardUtils {
  private WizardUtils() {
  }

  public static void createNewProject(@NotNull GuiTestRule guiTest, @NotNull String activity) {
    createNewProject(guiTest, null, activity);
  }

  public static void createNewProject(@NotNull GuiTestRule guiTest, @Nullable String domain, @NotNull String activity) {
    NewProjectWizardFixture wizard = guiTest.welcomeFrame().createNewProject();

    if (domain != null) {
      wizard.getConfigureAndroidProjectStep()
        .enterCompanyDomain(domain);
    }

    wizard.clickNext();

    wizard.clickNext();

    wizard.chooseActivity(activity);
    wizard.clickNext();

    wizard.clickFinish();
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
  }
}
