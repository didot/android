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

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_FEATURE;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureAndroidModuleStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.util.xml.GenericAttributeValue;
import java.io.IOException;
import org.fest.swing.timing.Wait;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that newly created Instant App modules do not have errors in them
 */
@RunWith(GuiTestRemoteRunner.class)
public class NewInstantAppModuleTest {
  private static final String SAVED_COMPANY_DOMAIN = "SAVED_COMPANY_DOMAIN";

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Nullable private String myOldSavedCompanyDomain;

  @Before
  public void before() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    myOldSavedCompanyDomain = propertiesComponent.getValue(SAVED_COMPANY_DOMAIN);
    propertiesComponent.setValue(SAVED_COMPANY_DOMAIN, "aia.example.com");
    SdkReplacer.replaceSdkLocationAndActivate(null, true);
  }

  @After
  public void after() {
    PropertiesComponent.getInstance().setValue(SAVED_COMPANY_DOMAIN, myOldSavedCompanyDomain);
    SdkReplacer.putBack();
  }

  // TODO: add tests for warnings in code - requires way to separate warnings from SimpleApplication out from warnings in new module

  @Test
  public void testCanBuildDefaultNewInstantAppFeatureModules() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleOldInstantApp");
    addNewFeatureModule("feature1");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testCanBuildEmptyNewInstantAppFeatureModules() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleOldInstantApp");
    addNewFeatureModule("feature1", "Add No Activity");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testCanBuildProjectWithMultipleFeatureModules() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleOldInstantApp");
    addNewFeatureModule("base1", null);
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    assertThat(ideFrame.invokeProjectMake().isBuildSuccessful()).isTrue();
    addNewFeatureModule("feature1", null);
    assertThat(ideFrame.invokeProjectMake().isBuildSuccessful()).isTrue();

    // Check that the modules are correctly added to the project
    assertValidFeatureModule(ideFrame.getModule("base1"));
    assertValidFeatureModule(ideFrame.getModule("feature1"));
    assertNotNull(ideFrame.getModule("instantapp"));

    // Verify application attributes are in feature1 (the base feature) and not in feature2
    ideFrame.getEditor()
      .open("base/src/main/AndroidManifest.xml")
      .moveBetween("android:label=", "")
      .moveBetween("android:theme=", "");

    ideFrame.getEditor()
      .open("feature/src/main/AndroidManifest.xml")
      .moveBetween("<application>", "");
  }

  @Test
  public void testCanBuildProjectWithEmptySecondFeatureModule() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleOldInstantApp");
    addNewFeatureModule("feature1");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
    addNewFeatureModule("feature2", "Add No Activity");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }


  @Test
  public void testPackageGeneratedCorrectly() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleOldInstantApp");
    addNewFeatureModule("feature1");

    Module module = guiTest.ideFrame().getModule("feature1");
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertNotNull(facet);
    Manifest manifest = facet.getManifest();
    assertNotNull(manifest);

    ApplicationManager.getApplication().runReadAction(() -> {
      GenericAttributeValue<String> packageAttribute = manifest.getPackage();
      assertNotNull(packageAttribute);
      assertThat(packageAttribute.isValid()).isTrue();
      assertThat(packageAttribute.getStringValue()).isEqualTo("com.thebigg.aia.simpleoldinstantapp.feature1");
    });
  }

  @Test
  public void testAddNewInstantAppModule() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleOldInstantApp");
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    NewModuleWizardFixture newModuleWizardFixture = ideFrame.openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...");

   newModuleWizardFixture.chooseModuleType("Instant App")
    .clickNext() // Selected App
    .clickFinish();

    ideFrame
      .waitForGradleProjectSyncToFinish(Wait.seconds(20))
      .waitForBuildToFinish(SOURCE_GEN);

    Module module = ideFrame.getModule("instantapp2");
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertNotNull(facet);
    assertEquals(PROJECT_TYPE_INSTANTAPP, facet.getConfiguration().getProjectType());
  }

  private void addNewFeatureModule(@Nullable String moduleName) {
    addNewFeatureModule(moduleName, null);
  }

  private void addNewFeatureModule(@Nullable String moduleName, @Nullable String activityType) {
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    NewModuleWizardFixture newModuleWizardFixture = ideFrame.openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...");

    // Make sure minSdkVersion in the new module is the same with app module.
    GradleBuildModel buildModel = ProjectBuildModel.get(ideFrame.getProject()).getModuleBuildModel(ideFrame.getModule("app"));
    assertNotNull(buildModel);

    ConfigureAndroidModuleStepFixture<NewModuleWizardFixture> configureAndroidModuleStep = newModuleWizardFixture
      .chooseModuleType("Instant App Feature Module")
      .clickNext() // Selected App
      .getConfigureAndroidModuleStep()
      .selectMinimumSdkApi(String.valueOf(buildModel.android().defaultConfig().minSdkVersion()));

    if (moduleName != null) {
      configureAndroidModuleStep.enterModuleName(moduleName);
    }

    newModuleWizardFixture
      .clickNext(); // Default options

    if (activityType != null) {
      newModuleWizardFixture.chooseActivity(activityType);
      if (!activityType.equals("Add No Activity")) {
        newModuleWizardFixture.clickNext();
      }
    }
    else {
      newModuleWizardFixture
        .clickNext(); // Default activity
    }

    newModuleWizardFixture
      .clickFinish() // Default parameters
      .waitForGradleProjectSyncToFinish(Wait.seconds(20))
      .waitForBuildToFinish(SOURCE_GEN);
  }

  private static void assertValidFeatureModule(Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertNotNull(facet);
    assertEquals(PROJECT_TYPE_FEATURE, facet.getConfiguration().getProjectType());
  }
}
