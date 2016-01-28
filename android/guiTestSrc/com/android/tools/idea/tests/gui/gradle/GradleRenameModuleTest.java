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

import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyTest.ExpectedModuleDependency;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.fixture.InputDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.SelectRefactoringDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@BelongsToTestGroups({PROJECT_SUPPORT})
public class GradleRenameModuleTest extends GuiTestCase {

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Test
  public void testRenameModule() throws IOException {
    importSimpleApplication();

    ProjectViewFixture.PaneFixture paneFixture = getIdeFrame().getProjectView().selectProjectPane();
    paneFixture.selectByPath("SimpleApplication", "app");
    invokeRefactor();

    SelectRefactoringDialogFixture selectRefactoringDialog = SelectRefactoringDialogFixture.findByTitle(myRobot);
    selectRefactoringDialog.selectRenameModule();
    selectRefactoringDialog.clickOk();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(myRobot, "Rename Module");
    renameModuleDialog.enterTextAndClickOk("app2");

    getIdeFrame().waitForBackgroundTasksToFinish();
    assertNotNull(getIdeFrame().findModule("app2"));
    assertNull("Module 'app' should not exist", getIdeFrame().findModule("app"));
  }

  @Test
  public void testRenameModuleAlsoChangeReferencesInBuildFile() throws IOException {
    importMultiModule();

    ProjectViewFixture.PaneFixture paneFixture = getIdeFrame().getProjectView().selectProjectPane();
    paneFixture.selectByPath("MultiModule", "library");
    invokeRefactor();

    SelectRefactoringDialogFixture selectRefactoringDialog = SelectRefactoringDialogFixture.findByTitle(myRobot);
    selectRefactoringDialog.selectRenameModule();
    selectRefactoringDialog.clickOk();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(myRobot, "Rename Module");
    renameModuleDialog.enterTextAndClickOk("newLibrary");

    getIdeFrame().waitForBackgroundTasksToFinish();
    assertNotNull(getIdeFrame().findModule("newLibrary"));

    // app module has two references to library module
    GradleBuildModelFixture buildModel = getIdeFrame().parseBuildFileForModule("app", true);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "debugCompile";
    expected.path = ":newLibrary";
    buildModel.requireDependency(expected);

    expected.configurationName = "releaseCompile";
    buildModel.requireDependency(expected);
  }

  @Test
  public void testCannotRenameRootModule() throws IOException {
    importSimpleApplication();

    ProjectViewFixture.PaneFixture paneFixture = getIdeFrame().getProjectView().selectProjectPane();
    paneFixture.selectByPath("SimpleApplication");
    invokeRefactor();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(myRobot, "Rename Module");
    renameModuleDialog.enterTextAndClickOk("SimpleApplication2");

    MessagesFixture errorMessage = MessagesFixture.findByTitle(myRobot, getIdeFrame().target(), "Rename Module");
    errorMessage.requireMessageContains("Can't rename root module");

    errorMessage.clickOk();
  }

  @Test
  public void testCannotRenameToExistedFile() throws IOException {
    importMultiModule();

    ProjectViewFixture.PaneFixture paneFixture = getIdeFrame().getProjectView().selectProjectPane();
    paneFixture.selectByPath("MultiModule", "app");
    invokeRefactor();

    SelectRefactoringDialogFixture selectRefactoringDialog = SelectRefactoringDialogFixture.findByTitle(myRobot);
    selectRefactoringDialog.selectRenameModule();
    selectRefactoringDialog.clickOk();

    InputDialogFixture renameModuleDialog = InputDialogFixture.findByTitle(myRobot, "Rename Module");
    renameModuleDialog.enterTextAndClickOk("library2");

    MessagesFixture errorMessage = MessagesFixture.findByTitle(myRobot, getIdeFrame().target(), "Rename Module");
    errorMessage.requireMessageContains("Rename folder failed");

    errorMessage.clickOk();
    // In this case, the rename diaglog will let you choose another name, click cancel to close the diaglog
    renameModuleDialog.clickCancel();
  }

  private void invokeRefactor() {
    getIdeFrame().invokeMenuPath("Refactor", "Rename...");
  }
}
