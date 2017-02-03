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

import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.InspectCodeDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ConfigureAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.InstantAppUrlFinder;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.android.SdkConstants.GRADLE_PLUGIN_AIA_VERSION;
import static com.android.tools.idea.npw.FormFactor.MOBILE;
import static com.android.tools.idea.npw.deprecated.NewFormFactorModulePath.setAiaPluginVersion;
import static com.android.tools.idea.npw.deprecated.NewFormFactorModulePath.setAiaSdkLocation;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.System.getenv;

/**
 * Test that newly created Instant App projects do not have errors in them
 */
@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRunner.class)
public class NewInstantAppTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void before() {
    setAiaPluginVersion(AndroidPluginGeneration.ORIGINAL.getLatestKnownVersion());
    setAiaSdkLocation("TestValue");
  }

  @After
  public void after() {
    setAiaPluginVersion(GRADLE_PLUGIN_AIA_VERSION);
    setAiaSdkLocation(getenv("WH_SDK"));
  }

  //Not putting this in before() as I plan to add some tests that work on non-default projects.
  private void createAndOpenDefaultAIAProject(@NotNull String projectName) {
    //TODO: There is some commonality between this code, the code in NewProjectTest and further tests I am planning, but there are also
    //      differences. Once AIA tests are completed this should be factored out into the NewProjectWizardFixture
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject();

    ConfigureAndroidProjectStepFixture configureAndroidProjectStep = newProjectWizard.getConfigureAndroidProjectStep();
    configureAndroidProjectStep
      .enterCompanyDomain("test.android.com")
      .enterApplicationName(projectName);
    guiTest.setProjectPath(configureAndroidProjectStep.getLocationInFileSystem());

    newProjectWizard
      .clickNext() // Complete project configuration
      .getConfigureFormFactorStep()
      .selectMinimumSdkApi(MOBILE, "16")
      .selectInstantAppSupport(MOBILE);

    newProjectWizard
      .clickNext() // Complete form factor configuration
      .clickNext() // Accept default values for Instant App Module
      .chooseActivity("Basic Activity")
      .clickNext() // Complete "Add Activity" step
      .clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
  }

  @Test
  public void testNoWarningsInDefaultNewInstantAppProjects() throws IOException {
    String projectName = "Warning";
    createAndOpenDefaultAIAProject(projectName);

    String inspectionResults = guiTest.ideFrame()
      // Need to sync twice otherwise you get an error on shutdown
      // TODO: find out why this is and handle it properly.
      .getEditor()
      .open("atom/build.gradle", EditorFixture.Tab.EDITOR)
      .moveBetween("", "compileSdkVersion")
      .enterText(" ")
      .awaitNotification(
        "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
      .performAction("Sync Now")
      .waitForGradleProjectSyncToFinish()
      // End
      .openFromMenu(InspectCodeDialogFixture::find, "Analyze", "Inspect Code...")
      .clickOk()
      .getResults();

    //Eventually this will be empty (or almost empty) for now we are just checking that there are no unexpected errors...
    assertThat(inspectionResults).isEqualTo(
      "Project '" + guiTest.getProjectPath() + "' " + projectName + "\n" +
      // TODO: Linting for Android Instant Apps needs to be updated as it isn't correctly picking up the dependencies
      "    Android > Lint > Correctness\n" +
      "        Menu namespace\n" +
      "            menu_main.xml\n" +
      "                Should use 'android:showAsAction' when not using the appcompat library\n" +
      // These warnings currently appear when testing locally but not on the buildbot. They should go away properly when prebuilts are
      // updated to the latest build tools.
      //"        Obsolete Gradle Dependency\n" +
      //"            build.gradle\n" +
      //"                Old buildToolsVersion 25.0.1; recommended version is 25.0.2 or later\n" +
      //"            build.gradle\n" +
      //"                Old buildToolsVersion 25.0.1; recommended version is 25.0.2 or later\n" +
      // This warning is unfortunate. We may want to get rid of it.
      "    Android > Lint > Security\n" +
      "        AllowBackup/FullBackupContent Problems\n" +
      "            AndroidManifest.xml\n" +
      "                On SDK version 23 and up, your app data will be automatically backed up and restored on app install. Consider adding the attribute 'android:fullBackupContent' to specify an '@xml' resource which configures which files to backup. More info: https://developer.android.com/training/backup/autosyncapi.html\n" +
      // TODO: Not valid for instant apps - linting needs updating
      "    Android > Lint > Usability > Icons\n" +
      "        Missing application icon\n" +
      "            AndroidManifest.xml\n" +
      "                Should explicitly set 'android:icon', there is no default\n" +
      // TODO: dependency is a packaging dependency - linting needs updating
      "    Declaration redundancy\n" +
      "        Unnecessary module dependency\n" +
      "            instant-app\n" +
      "                Module 'instant-app' sources do not depend on module 'atom' sources\n" +
      // TODO: harmless spelling in namespace declaration - dictionary needs updating
      "    Spelling\n" +
      "        Typo\n" +
      "            AndroidManifest.xml\n" +
      "                Typo: In word 'instantapp'\n" +
      "                Typo: In word 'instantapps'\n");
  }

  @Test
  public void testCanBuildDefaultNewInstantAppProjects() throws IOException {
    createAndOpenDefaultAIAProject("BuildApp");

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testValidPathInDefaultNewInstantAppProjects() throws IOException {
    createAndOpenDefaultAIAProject("RouteApp");

    Module module = guiTest.ideFrame().getModule("atom");
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertThat(facet).isNotNull();
    assertThat(new InstantAppUrlFinder(MergedManifest.get(facet)).getAllUrls()).isNotEmpty();
  }
}
