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
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ConfigureAndroidModuleStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewModuleWizardFixture;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.util.xml.GenericAttributeValue;
import org.fest.swing.timing.Wait;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.npw.deprecated.ConfigureAndroidProjectStep.SAVED_COMPANY_DOMAIN;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Test that newly created Instant App modules do not have errors in them
 */
@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRunner.class)
public class NewInstantAppModuleTest {
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
    guiTest.importSimpleApplication();
    addNewFeatureModule("feature1");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testPackageGeneratedCorrectly() throws IOException {
    guiTest.importSimpleApplication();
    addNewFeatureModule("feature");

    Module module = guiTest.ideFrame().getModule("feature");
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertNotNull(facet);
    Manifest manifest = facet.getManifest();
    assertNotNull(manifest);

    ApplicationManager.getApplication().runReadAction(() -> {
      GenericAttributeValue<String> packageAttribute = manifest.getPackage();
      assertNotNull(packageAttribute);
      assertThat(packageAttribute.isValid()).isTrue();
      assertThat(packageAttribute.getStringValue()).isEqualTo("com.example.aia.feature");
    });
  }

  @Test
  public void testAddNewInstantAppModule() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    ideFrame.openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Instant App")
      .clickNext() // Selected App
      .clickFinish();

    ideFrame
      .waitForGradleProjectSyncToFinish(Wait.seconds(20))
      .waitForBuildToFinish(SOURCE_GEN);
    assertThat(ideFrame.invokeProjectMake().isBuildSuccessful()).isTrue();

    Module module = ideFrame.getModule("instantapp");
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertNotNull(facet);
    assertEquals(PROJECT_TYPE_INSTANTAPP, facet.getProjectType());
  }

  private void addNewFeatureModule(@Nullable String moduleName) {
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    NewModuleWizardFixture newModuleWizardFixture = ideFrame.openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...");

    ConfigureAndroidModuleStepFixture configureAndroidModuleStep = newModuleWizardFixture
      .chooseModuleType("Feature Module")
      .clickNext() // Selected App
      .getConfigureAndroidModuleStep()
      .selectMinimumSdkApi("23");

    if (moduleName != null) {
      configureAndroidModuleStep.enterModuleName(moduleName);
    }

    newModuleWizardFixture
      .clickNext() // Default options
      .clickNext() // Default activity
      .clickFinish(); // Default parameters

    ideFrame
      .waitForGradleProjectSyncToFinish(Wait.seconds(20))
      .waitForBuildToFinish(SOURCE_GEN);
  }
}
