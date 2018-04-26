/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import org.fest.swing.core.MouseButton;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class GenerateApkWithReleaseVariantTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Verifies that gradle sync works fine with library having shrinkResources enabled on debug and release build variants
   *
   * <p>TT ID: 2604a622-87f1-47dc-bc2d-55c62859b3a5
   *
   * <pre>
   *   Test steps:
   *   1. Create a Project with any Activity
   *   2. Create a Android Library module
   *   3. Right click on app module | Module settings | Dependencies | Add Library module as dependency to app
   *   4. Open mylibrary | build.gradle
   *   5. Add "shrinkResources true" to release variant
   *   6. Gradle sync (Verify)
   *   Verify:
   *   Error should show up that shrinker is not allowed for library modules
   *   "Error:Resource shrinker cannot be used for libraries."
   * </pre>
   */
  @Test
  @RunIn(TestGroup.SANITY_BAZEL)
  public void generateApkWithReleaseVariant() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importSimpleLocalApplication();

    ideFrame.invokeMenuPath("File", "New", "New Module...");

    NewModuleWizardFixture.find(ideFrame)
                          .chooseModuleType("Android Library")
                          .clickNextToStep("Configure the new module")
                          .clickFinish();
    ideFrame.waitForGradleProjectSyncToFinish();

    ideFrame.getProjectView()
            .selectAndroidPane()
            .clickPath(MouseButton.RIGHT_BUTTON, "app")
            .openFromMenu(ProjectStructureDialogFixture::find, "Open Module Settings")
            .selectDependenciesTab()
            .addModuleDependency(":mylibrary")
            .clickOk();

    ideFrame.getEditor()
            .open("mylibrary/build.gradle")
            .moveBetween("release {", "")
            .enterText("\nshrinkResources true");
    ideFrame.requestProjectSync();

    ideFrame.waitForGradleProjectSyncToFail(Wait.seconds(20));

    assertThat(ideFrame.getBuildToolWindow().getSyncConsoleViewText()).contains("Resource shrinker cannot be used for libraries.");
  }
}
