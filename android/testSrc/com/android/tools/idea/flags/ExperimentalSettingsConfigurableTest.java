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
package com.android.tools.idea.flags;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.GradlePerProjectExperimentalSettings;
import com.android.tools.idea.rendering.RenderSettings;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ExperimentalSettingsConfigurable}.
 */
public class ExperimentalSettingsConfigurableTest extends IdeaTestCase {
  @Mock private GradleExperimentalSettings mySettings;
  @Mock private GradlePerProjectExperimentalSettings myPerProjectSettings;

  private ExperimentalSettingsConfigurable myConfigurable;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myConfigurable = new ExperimentalSettingsConfigurable(mySettings, myPerProjectSettings, new RenderSettings());
  }

  public void testIsModified() {
    myConfigurable.setMaxModuleCountForSourceGen(6);
    mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = 8;
    assertTrue(myConfigurable.isModified());
    mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = 6;
    assertFalse(myConfigurable.isModified());

    myConfigurable.setSkipSourceGenOnSync(true);
    mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = false;
    assertTrue(myConfigurable.isModified());
    mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
    assertFalse(myConfigurable.isModified());

    myConfigurable.setUseL2DependenciesInSync(true);
    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = false;
    assertTrue(myConfigurable.isModified());
    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = true;
    assertFalse(myConfigurable.isModified());

    myConfigurable.setUseSingleVariantSync(true);
    myPerProjectSettings.USE_SINGLE_VARIANT_SYNC = false;
    assertTrue(myConfigurable.isModified());
    myPerProjectSettings.USE_SINGLE_VARIANT_SYNC = true;
    assertFalse(myConfigurable.isModified());
  }

  public void testApply() throws ConfigurationException {
    myConfigurable.setMaxModuleCountForSourceGen(6);
    myConfigurable.setSkipSourceGenOnSync(true);
    myConfigurable.setUseL2DependenciesInSync(true);
    myConfigurable.setUseSingleVariantSync(true);

    myConfigurable.apply();

    assertEquals(6, mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN);
    assertTrue(mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC);
    assertTrue(mySettings.USE_L2_DEPENDENCIES_ON_SYNC);
    assertTrue(myPerProjectSettings.USE_SINGLE_VARIANT_SYNC);

    myConfigurable.setMaxModuleCountForSourceGen(8);
    myConfigurable.setSkipSourceGenOnSync(false);
    myConfigurable.setUseL2DependenciesInSync(false);
    myConfigurable.setUseSingleVariantSync(false);

    myConfigurable.apply();

    assertEquals(8, mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN);
    assertFalse(mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC);
    assertFalse(mySettings.USE_L2_DEPENDENCIES_ON_SYNC);
    assertFalse(myPerProjectSettings.USE_SINGLE_VARIANT_SYNC);
  }

  public void testReset() {
    mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
    mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = 6;
    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = true;
    myPerProjectSettings.USE_SINGLE_VARIANT_SYNC = true;

    myConfigurable.reset();

    assertTrue(myConfigurable.isSkipSourceGenOnSync());
    assertEquals(6, myConfigurable.getMaxModuleCountForSourceGen().intValue());
    assertTrue(myConfigurable.isUseL2DependenciesInSync());
    assertTrue(myConfigurable.isUseSingleVariantSync());

    mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = false;
    mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = 8;
    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = false;
    myPerProjectSettings.USE_SINGLE_VARIANT_SYNC = false;

    myConfigurable.reset();

    assertFalse(myConfigurable.isSkipSourceGenOnSync());
    assertEquals(8, myConfigurable.getMaxModuleCountForSourceGen().intValue());
    assertFalse(myConfigurable.isUseL2DependenciesInSync());
    assertFalse(myConfigurable.isUseSingleVariantSync());
  }
}