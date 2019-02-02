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

import static com.android.tools.idea.npw.FormFactor.MOBILE;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.instantapp.InstantAppUrlFinder;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.InspectCodeDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import com.intellij.openapi.module.Module;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that newly created Instant App projects do not have errors in them
 */
@RunWith(GuiTestRemoteRunner.class)
public class NewInstantAppTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void before() {
    SdkReplacer.replaceSdkLocationAndActivate("TestValue", true);
  }

  @After
  public void after() {
    StudioFlags.UAB_NEW_PROJECT_INSTANT_APP_IS_DYNAMIC_APP.clearOverride();
    SdkReplacer.putBack();
  }

  //Not putting this in before() as I plan to add some tests that work on non-default projects.
  private void createAndOpenDefaultAIAProject(@NotNull String projectName, @Nullable String featureModuleName,
                                              @Nullable String activityName, boolean includeUrl) {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject();

    if (StudioFlags.NPW_DYNAMIC_APPS.get()) {
      // No longer possible to customize Instant Apps (eg "feature module name" and "include URL")
      assert featureModuleName == null;
      assert !includeUrl;

      newProjectWizard
        .getChooseAndroidProjectStep()
        .chooseActivity(activityName == null ? "Empty Activity" : activityName)
        .wizard()
        .clickNext()
        .getConfigureNewAndroidProjectStep()
        .setSourceLanguage("Java")
        .enterName(projectName)
        .selectMinimumSdkApi("23")
        .setIncludeInstantApp(true)
        .wizard()
        .clickFinish();
    }
    else {
      newProjectWizard
        .getConfigureAndroidProjectStep()
        .enterCompanyDomain("test.android.com")
        .enterApplicationName(projectName)
        .wizard()
        .clickNext() // Complete project configuration
        .getConfigureFormFactorStep()
        .selectMinimumSdkApi(MOBILE, "23")
        .selectInstantAppSupport(MOBILE)
        .wizard()
        .clickNext(); // Complete form factor configuration

      if (featureModuleName != null) {
        newProjectWizard
          .getConfigureInstantModuleStep()
          .enterFeatureModuleName(featureModuleName);
      }

      newProjectWizard
        .clickNext() // Complete configuration of Instant App Module
        .chooseActivity(activityName == null ? "Empty Activity" : activityName)
        .clickNext() // Complete "Add Activity" step
        .getConfigureActivityStep()
        .selectIncludeUrl(includeUrl)
        .wizard()
        .clickFinish();
    }

    guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish()
      .findRunApplicationButton().waitUntilEnabledAndShowing(); // Wait for the toolbar to be ready

    if(!StudioFlags.UAB_NEW_PROJECT_INSTANT_APP_IS_DYNAMIC_APP.get()) {
      guiTest.ideFrame()
             .getProjectView()
             .selectAndroidPane()
             .clickPath(featureModuleName == null ? "feature" : featureModuleName);
    }
  }

  private void createAndOpenDefaultAIAProject(@NotNull String projectName, @Nullable String featureModuleName,
                                              @Nullable String activityName) {
    createAndOpenDefaultAIAProject(projectName, featureModuleName, activityName, false);
  }

  private void testNoWarningsInDefaultNewInstantAppProjects(boolean instantFlagOn, String testName) {
    StudioFlags.UAB_NEW_PROJECT_INSTANT_APP_IS_DYNAMIC_APP.override(instantFlagOn);
    String projectName = "Warning";
    createAndOpenDefaultAIAProject(projectName, null, null);

    String inspectionResults = guiTest.ideFrame()
      .openFromMenu(InspectCodeDialogFixture::find, "Analyze", "Inspect Code...")
      .clickOk()
      .getResults();

    verifyOnlyExpectedWarnings(inspectionResults,
                               "Project '.*" + testName + "/Warning' Warning",
                               "    Android",
                               "        Lint",
                               "            Correctness",
                               "                Obsolete Gradle Dependency",
                               "                    build.gradle",
                               "                        A newer version of .*",
                               "            Performance",
                               "                Unused resources",
                               "                    mobile_navigation.xml",
                               "                        The resource 'R.navigation.mobile_navigation' appears to be unused",
                               "            Security",
                               "                AllowBackup/FullBackupContent Problems",
                               "                    AndroidManifest.xml",
                               "                        On SDK version 23 and up, your app data will be automatically backed up and .*",
                               "            Usability",
                               "                Missing support for Firebase App Indexing",
                               "                    AndroidManifest.xml",
                               "                        App is not indexable by Google Search; consider adding at least one Activity .*",
                               "    Java",
                               "        Declaration redundancy",
                               "            Redundant throws clause",
                               "                ExampleInstrumentedTest",
                               "                ExampleUnitTest",
                               "                    The declared exception 'Exception' is never thrown",
                               "            Unnecessary module dependency",
                               "                app",
                               "                    Module 'app' sources do not depend on module 'base' sources",
                               "                    Module 'app' sources do not depend on module 'feature' sources",
                               "                feature",
                               "                    Module 'feature' sources do not depend on module 'base' sources",
                               "    XML",
                               "        Unused XML schema declaration",
                               "            AndroidManifest.xml",
                               "                Namespace declaration is never used",
                               "        XML tag empty body",
                               "            strings.xml",
                               "                XML tag has empty body"
    );
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/116163055
  @Test
  public void testNoWarningsInDefaultNewInstantAppProjects_NO_UAB() {
    testNoWarningsInDefaultNewInstantAppProjects(false, "testNoWarningsInDefaultNewInstantAppProjects_NO_UAB");
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/116163055
  @Test
  public void testNoWarningsInDefaultNewInstantAppProjects_UAB() {
    testNoWarningsInDefaultNewInstantAppProjects(true, "testNoWarningsInDefaultNewInstantAppProjects_UAB");
  }

  @Test
  public void testCanBuildDefaultNewInstantAppProjects_NO_UAB() {
    StudioFlags.UAB_NEW_PROJECT_INSTANT_APP_IS_DYNAMIC_APP.override(false);
    createAndOpenDefaultAIAProject("BuildApp", null, null);

    guiTest.ideFrame().getEditor()
      .open("base/build.gradle") // Check "base" dependencies
      .moveBetween("application project(':app')", "")
      .moveBetween("feature project(':feature')", "")
      .open("feature/build.gradle") // Check "feature" dependencies
      .moveBetween("implementation project(':base')", "")
      .open("app/build.gradle") // Check "app" dependencies
      .moveBetween("implementation project(':feature')", "")
      .moveBetween("implementation project(':base')", "")
      .open("base/src/main/AndroidManifest.xml")
      .moveBetween("android:name=\"aia-compat-api-min-version\"", "")
      .moveBetween("android:value=\"1\"", "");

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testCanBuildDefaultNewInstantAppProjects_UAB() {
    //TODO: check for dist:module
    StudioFlags.UAB_NEW_PROJECT_INSTANT_APP_IS_DYNAMIC_APP.override(true);
    createAndOpenDefaultAIAProject("BuildApp", null, null);
    String manifestContent = guiTest.ideFrame().getEditor()
                                    .open("app/src/main/res/layout/activity_main.xml")
                                    .open("app/src/main/AndroidManifest.xml")
                                    .getCurrentFileContents();
    assertThat(manifestContent).contains("xmlns:dist=\"http://schemas.android.com/apk/distribution\"");
    assertThat(manifestContent).contains("<dist:module dist:instant=\"true\" />");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  private void testCanBuildNewInstantAppProjectsWithEmptyActivityWithoutUrls(boolean instantFlagOn, String activityMainXml, String manifestPath) {
    StudioFlags.UAB_NEW_PROJECT_INSTANT_APP_IS_DYNAMIC_APP.override(instantFlagOn);
    createAndOpenDefaultAIAProject("BuildApp", null, null, false);
    String manifestContent = guiTest.ideFrame().getEditor()
      .open(activityMainXml)
      .open(manifestPath)
      .getCurrentFileContents();

    assertThat(manifestContent).contains("android.intent.action.MAIN");
    assertThat(manifestContent).contains("android.intent.category.LAUNCHER");
    assertThat(manifestContent).doesNotContain("android:host=");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testCanBuildNewInstantAppProjectsWithEmptyActivityWithoutUrls_NO_UAB() {
    testCanBuildNewInstantAppProjectsWithEmptyActivityWithoutUrls(false, "feature/src/main/res/layout/activity_main.xml", "feature/src/main/AndroidManifest.xml");
  }
  @Test
  public void testCanBuildNewInstantAppProjectsWithEmptyActivityWithoutUrls_UAB() {
    testCanBuildNewInstantAppProjectsWithEmptyActivityWithoutUrls(true, "app/src/main/res/layout/activity_main.xml", "app/src/main/AndroidManifest.xml");
  }

  @Test
  public void testCanBuildNewInstantAppProjectsWithLoginActivity() {
    StudioFlags.UAB_NEW_PROJECT_INSTANT_APP_IS_DYNAMIC_APP.override(false);
    if (StudioFlags.NPW_DYNAMIC_APPS.get()) {
      createAndOpenDefaultAIAProject("BuildApp", null, null);
      guiTest.ideFrame()
             .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Activity", "Login Activity")
             .clickFinish();

      String baseStrings = guiTest.ideFrame()
                                  .waitForGradleProjectSyncToFinish()
                                  .getEditor()
                                  .open("base/src/main/res/values/strings.xml")
                                  .getCurrentFileContents();

      assertThat(baseStrings).contains("title_activity_login");
      assertAbout(file()).that(guiTest.getProjectPath("feature/src/main/res/layout/activity_login.xml")).isFile();
    }
    else {
      createAndOpenDefaultAIAProject("BuildApp", null, "Login Activity", true);
      guiTest.ideFrame().getEditor()
             .open("feature/src/main/res/layout/activity_login.xml")
             .open("feature/src/main/AndroidManifest.xml")
             .moveBetween("android:order=", "")
             .moveBetween("android:host=", "")
             .moveBetween("android:pathPattern=", "")
             .moveBetween("android:scheme=\"https", "")
             .moveBetween("android.intent.action.", "MAIN")
             .moveBetween("android.intent.category.", "LAUNCHER");
    }

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testCanBuildNewInstantAppProjectsWithLoginActivity_UAB() {
    StudioFlags.UAB_NEW_PROJECT_INSTANT_APP_IS_DYNAMIC_APP.override(true);
    createAndOpenDefaultAIAProject("BuildApp", null, null);
    guiTest.ideFrame()
           .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Activity", "Login Activity")
           .clickFinish();

    String baseStrings = guiTest.ideFrame()
                                .waitForGradleProjectSyncToFinish()
                                .getEditor()
                                .open("app/src/main/res/values/strings.xml")
                                .getCurrentFileContents();

    assertThat(baseStrings).contains("title_activity_login");
    assertAbout(file()).that(guiTest.getProjectPath("app/src/main/res/layout/activity_login.xml")).isFile();

  }

  @Test
  public void newInstantAppProjectWithFullScreenActivity_NO_UAB() {
    StudioFlags.UAB_NEW_PROJECT_INSTANT_APP_IS_DYNAMIC_APP.override(false);
    createAndOpenDefaultAIAProject("BuildApp", null, "Fullscreen Activity");
    guiTest.ideFrame().getEditor()
      .open("feature/src/main/res/layout/activity_fullscreen.xml")
      .open("base/src/main/res/values/attrs.xml") // Make sure "Full Screen" themes, colors and styles are on the base module
      .moveBetween("ButtonBarContainerTheme", "")
      .open("base/src/main/res/values/colors.xml")
      .moveBetween("black_overlay", "")
      .open("base/src/main/res/values/styles.xml")
      .moveBetween("FullscreenTheme", "")
      .moveBetween("FullscreenActionBarStyle", "");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void newInstantAppProjectWithFullScreenActivity_UAB() {
    StudioFlags.UAB_NEW_PROJECT_INSTANT_APP_IS_DYNAMIC_APP.override(true);
    createAndOpenDefaultAIAProject("BuildApp", null, "Fullscreen Activity");
    guiTest.ideFrame().getEditor()
           .open("app/src/main/res/layout/activity_fullscreen.xml")
           .open("app/src/main/res/values/attrs.xml") // Make sure "Full Screen" themes, colors and styles are on the base module
           .moveBetween("ButtonBarContainerTheme", "")
           .open("app/src/main/res/values/colors.xml")
           .moveBetween("black_overlay", "")
           .open("app/src/main/res/values/styles.xml")
           .moveBetween("FullscreenTheme", "")
           .moveBetween("FullscreenActionBarStyle", "");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test // b/68122671
  public void addMapActivityToExistingIappModule_NO_UAB() {
    addMapActivityToExistingIappModule(false, "base/src/debug/res/values/google_maps_api.xml", "base/src/release/res/values/google_maps_api.xml");
  }

  @Test // b/68122671
  public void addMapActivityToExistingIappModule_UAB() {
    addMapActivityToExistingIappModule(true, "app/src/debug/res/values/google_maps_api.xml", "app/src/release/res/values/google_maps_api.xml");
  }

  // b/68122671
  private void addMapActivityToExistingIappModule(boolean instantFlagOn, String debugPath, String releasePath) {
    StudioFlags.UAB_NEW_PROJECT_INSTANT_APP_IS_DYNAMIC_APP.override(instantFlagOn);
    createAndOpenDefaultAIAProject("BuildApp", null, null);
    guiTest.ideFrame()
           .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Google", "Google Maps Activity")
           .clickFinish();

    guiTest.ideFrame()
           .waitForGradleProjectSyncToFinish();

    assertAbout(file()).that(guiTest.getProjectPath(debugPath)).isFile();
    assertAbout(file()).that(guiTest.getProjectPath(releasePath)).isFile();
  }

  @Test // b/68478730
  public void addMasterDetailActivityToExistingIappModule_NO_UAB() {
    addMasterDetailActivityToExistingIappModule(false, "base/src/main/res/values/strings.xml");
  }

  @Test // b/68478730
  public void addMasterDetailActivityToExistingIappModule_UAB() {
    addMasterDetailActivityToExistingIappModule(true, "app/src/main/res/values/strings.xml");
  }


  // b/68478730
  private void addMasterDetailActivityToExistingIappModule(boolean instantFlagOn, String stringPath) {
    StudioFlags.UAB_NEW_PROJECT_INSTANT_APP_IS_DYNAMIC_APP.override(instantFlagOn);
    createAndOpenDefaultAIAProject("BuildApp", null, null);
    guiTest.ideFrame()
           .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Activity", "Master/Detail Flow")
           .clickFinish();

    String baseStrings = guiTest.ideFrame()
                                .waitForGradleProjectSyncToFinish()
                                .getEditor()
                                .open(stringPath)
                                .getCurrentFileContents();

    assertThat(baseStrings).contains("title_item_detail");
    assertThat(baseStrings).contains("title_item_list");
  }

  @Test // b/68684401
  public void addFullscreenActivityToExistingIappModule_NO_UAB() {
    addFullscreenActivityToExistingIappModule(false, "base/src/main/res/values/strings.xml");
  }

  @Test // b/68684401
  public void addFullscreenActivityToExistingIappModule_UAB() {
    addFullscreenActivityToExistingIappModule(true, "app/src/main/res/values/strings.xml");
  }

  @Test
  public void testValidPathInDefaultNewInstantAppProjects() {
    if (StudioFlags.NPW_DYNAMIC_APPS.get()) {
      return; // On the new NPW design, Instant Apps are created with default options. This test no longer applies
    }

    createAndOpenDefaultAIAProject("RouteApp", "routefeature", null, true);

    Module module = guiTest.ideFrame().getModule("routefeature");
    assertThat(new InstantAppUrlFinder(module).getAllUrls()).isNotEmpty();
  }

  // b/68684401
  private void addFullscreenActivityToExistingIappModule(boolean instantFlagOn, String stringXmlPath) {
    StudioFlags.UAB_NEW_PROJECT_INSTANT_APP_IS_DYNAMIC_APP.override(instantFlagOn);
    createAndOpenDefaultAIAProject("BuildApp", null, null);
    guiTest.ideFrame()
           .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Activity", "Fullscreen Activity")
           .clickFinish();

    String baseStrings = guiTest.ideFrame()
                                .waitForGradleProjectSyncToFinish()
                                .getEditor()
                                .open(stringXmlPath)
                                .getCurrentFileContents();

    assertThat(baseStrings).contains("title_activity_fullscreen");
  }

  @Test
  public void testCanCustomizeFeatureModuleInNewInstantAppProjects() {
    if (StudioFlags.NPW_DYNAMIC_APPS.get()) {
      return; // On the new NPW design, Instant Apps are created with default options. This test no longer applies
    }

    createAndOpenDefaultAIAProject("SetFeatureNameApp", "testfeaturename", null);

    guiTest.ideFrame().getModule("testfeaturename");
  }

  // With warnings coming from multiple projects the order of warnings is not deterministic, also there are some warnings that show up only
  // on local machines. This method allows us to check that the warnings in the actual result are a sub-set of the expected warnings.
  // This is not a perfect solution, but this state where we have multiple warnings on a new project should only be temporary
  public static void verifyOnlyExpectedWarnings(@NotNull String actualResults, @NotNull String... acceptedWarnings) {
    ArrayList<String> actualResultLines = new ArrayList<>();

    outLoop:
    for (String resultLine : actualResults.split("\n")) {
      for (String acceptedWarning : acceptedWarnings) {
        if (resultLine.matches(acceptedWarning)) {
          continue outLoop;
        }
      }
      actualResultLines.add(resultLine);
    }

    assertThat(actualResultLines).isEmpty();
  }
}
