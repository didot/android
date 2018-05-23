/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.gradle.dsl.model.dependencies.ExpectedModuleDependency;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.RenameModuleDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

@RunIn(TestGroup.UNRELIABLE) // b/77269009 @RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRemoteRunner.class)
public class GradleRenameModuleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Test
  public void testRenameModule() throws IOException {
    guiTest.importSimpleApplication()
      .getProjectView()
      .selectProjectPane()
      .clickPath("SimpleApplication", "app")
      .openFromMenu(RenameModuleDialogFixture::find, "Refactor", "Rename...")
      .enterText("app2")
      .clickOk()
      .waitForGradleProjectSyncToStart()
      .waitForGradleProjectSyncToFinish();
    assertThat(guiTest.ideFrame().hasModule("app2")).named("app2 module exists").isTrue();
    assertThat(guiTest.ideFrame().hasModule("app")).named("app module exists").isFalse();
  }

  @Test
  public void testRenameModuleAlsoChangeReferencesInBuildFile() throws IOException {
    guiTest.importMultiModule()
      .getProjectView()
      .selectProjectPane()
      .clickPath("MultiModule", "library")
      .openFromMenu(RenameModuleDialogFixture::find, "Refactor", "Rename...")
      .enterText("newLibrary")
      .clickOk();

    guiTest.waitForBackgroundTasks();
    assertThat(guiTest.ideFrame().hasModule("newLibrary")).named("newLibrary module exists").isTrue();

    // app module has two references to library module
    GradleBuildModelFixture buildModel = guiTest.ideFrame().parseBuildFileForModule("app");

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "debugImplementation";
    expected.path = ":newLibrary";
    buildModel.requireDependency(expected);

    expected.configurationName = "releaseImplementation";
    buildModel.requireDependency(expected);
  }

  @Test
  public void testCannotRenameRootModule() throws IOException {
    guiTest.importSimpleApplication()
      .getProjectView()
      .selectProjectPane()
      .clickPath("SimpleApplication")
      .openFromMenu(RenameModuleDialogFixture::find, "Refactor", "Rename...")
      .enterText("SimpleApplication2")
      .clickOkAndRequireError("Can't rename root module");
  }

  @Test
  public void testCannotRenameToExistingFile() throws IOException {
    guiTest.importMultiModule()
      .getProjectView()
      .selectProjectPane()
      .clickPath("MultiModule", "app")
      .openFromMenu(RenameModuleDialogFixture::find, "Refactor", "Rename...")
      .enterText("library2")
      .clickOkAndRequireError("Module named 'library2' already exist")
      .clickCancel();
  }
}
