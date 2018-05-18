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
package com.android.tools.idea.tests.gui.instantapp;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.core.MouseButton;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class AddModuleTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Verifies that user is able to add a Instant App Feature module through the
   * new module wizard.
   *
   * <p>TT ID: d239df75-a7fc-4327-a5af-d6b2f6caba11
   *
   * <pre>
   *   Test steps:
   *   1. Import simple instant application project
   *   2. Go to File -> New module to open the new module dialog wizard.
   *   3. Follow through the wizard to add a new Instant App Feature module, accepting defaults.
   *   4. Complete the wizard and wait for the build to complete.
   *   Verify:
   *   1. The new Instant App Feature module's library is shown in the project explorer pane.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void addFeatureModule() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("TopekaInstantApp");

    ideFrame.invokeMenuPath("File", "New", "New Module...");

    NewModuleWizardFixture newModDialog = NewModuleWizardFixture.find(ideFrame);

    newModDialog.chooseModuleType("Instant App Feature Module")
      .clickNextToStep("Creates a new Android Instant App Feature module.")
      .clickNextToStep("Add an Activity to Mobile")
      .clickNextToStep("Configure Activity")
      .clickFinish();

    ideFrame.waitForGradleProjectSyncToFinish();

    ProjectViewFixture.PaneFixture androidPane = ideFrame.getProjectView().selectAndroidPane();
    androidPane.clickPath("feature");
  }

  /**
   * Verifies that user is able to add a instant app module through the
   * new module wizard.
   *
   * <p>TT ID: 6da70326-4b89-4f9b-9e08-573939bebfe5
   *
   * <pre>
   *   Test steps:
   *   1. Import simple application project
   *   2. Go to File -> New module to open the new module dialog wizard.
   *   3. Follow through the wizard to add a new instant module, accepting defaults.
   *   4. Complete the wizard and wait for the build to complete.
   *   Verify:
   *   1. The new instant module's library is shown in the project explorer pane.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void addInstantModule() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importSimpleLocalApplication();

    ideFrame.invokeMenuPath("File", "New", "New Module...");

    NewModuleWizardFixture.find(ideFrame)
      .chooseModuleType("Instant App")
      .clickNextToStep("Configure your module")
      .clickFinish();

    ideFrame.waitForGradleProjectSyncToFinish();

    ideFrame.getProjectView()
      .selectAndroidPane()
      .clickPath("instantapp");
  }
}
