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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.NewModuleDialogFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests, that newly generated modules work, even with older gradle plugin versions.
 */
@RunIn(TestGroup.TEST_SUPPORT)
@RunWith(GuiTestRunner.class)
public class NewModuleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testNewModuleOldGradle() throws Exception {
    String gradleFileContents = guiTest.importSimpleApplication()
      // the oldest combination we support:
      .updateAndroidGradlePluginVersion("1.0.0")
      .updateGradleWrapperVersion("2.2.1")
      .getEditor()
      .open("app/build.gradle")
      // delete lines using DSL features added after Android Gradle 1.0.0
      .moveBetween("use", "Library")
      .invokeAction(EditorFixture.EditorAction.DELETE_LINE)
      .moveBetween("test", "Compile")
      .invokeAction(EditorFixture.EditorAction.DELETE_LINE)
      .getIdeFrame()
      .requestProjectSync()
      .waitForGradleProjectSyncToFinish()
      .openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Android Library")
      .clickNextToStep("Android Library")
      .setModuleName("somelibrary")
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("somelibrary/build.gradle")
      .getCurrentFileContents();
    assertThat(gradleFileContents).doesNotContain("testCompile");
    assertAbout(file()).that(new File(guiTest.getProjectPath(), "somelibrary/src/main")).isDirectory();
    assertAbout(file()).that(new File(guiTest.getProjectPath(), "somelibrary/src/test")).doesNotExist();
  }

  /**
   * Verifies addition of new application module to application.
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TR ID: C14578813
   * <pre>
   *   Test Steps
   *   1. File -> new module
   *   2. Select Phone & Tablet module
   *   3. Choose no activity
   *   3. Wait for build to complete
   *   Verification
   *   a new folder matching the module name should have been created.
   * </pre>
   */
  @Test
  public void createNewAppModuleWithDefaults() throws Exception {
    guiTest.importSimpleApplication()
      .openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Phone & Tablet Module")
      .clickNextToStep("Phone & Tablet Module")
      .setModuleName("application-module")
      .clickNextToStep("Add an Activity to Mobile")
      .chooseActivity("Add No Activity")
      .clickFinish()
      .waitForGradleProjectSyncToFinish();
    assertAbout(file()).that(new File(guiTest.getProjectPath(), "application-module")).isDirectory();
  }

  /**
   * Verifies addition of new library module to application.
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TR ID: C14578813
   * <pre>
   *   Test Steps
   *   Create a new project
   *   1. File > New Module
   *   2. Choose Android Library
   *   3. Click Finish
   *   Verification
   *   a new folder matching the module name should have been created
   * </pre>
   */
  @Test
  public void createNewLibraryModuleWithDefaults() throws Exception {
    guiTest.importSimpleApplication()
      .openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Android Library")
      .clickNextToStep("Android Library")
      .setModuleName("library-module")
      .clickFinish()
      .waitForGradleProjectSyncToFinish();
    assertAbout(file()).that(new File(guiTest.getProjectPath(), "library-module")).isDirectory();
  }
}
