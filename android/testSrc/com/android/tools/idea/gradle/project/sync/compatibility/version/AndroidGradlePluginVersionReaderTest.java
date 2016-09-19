/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.compatibility.version;

import com.android.tools.idea.gradle.service.notification.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;

import java.util.List;

import static com.android.SdkConstants.GRADLE_EXPERIMENTAL_PLUGIN_RECOMMENDED_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.tools.idea.testing.TestProjectPaths.EXPERIMENTAL_PLUGIN;
import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link AndroidGradlePluginVersionReader}.
 */
public class AndroidGradlePluginVersionReaderTest extends AndroidGradleTestCase {
  private AndroidGradlePluginVersionReader myVersionReader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myVersionReader = new AndroidGradlePluginVersionReader();
  }

  public void testAppliesToWithAndroidModule() throws Exception {
    loadSimpleApplication();
    Module appModule = myModules.getAppModule();
    assertTrue(myVersionReader.appliesTo(appModule));
  }

  public void testAppliesToWithJavaModule() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    Module libModule = myModules.getModule("lib");
    assertFalse(myVersionReader.appliesTo(libModule));
  }

  public void testGetComponentVersion() throws Exception {
    loadSimpleApplication();
    Module appModule = myModules.getAppModule();
    String version = myVersionReader.getComponentVersion(appModule);
    assertEquals(GRADLE_PLUGIN_RECOMMENDED_VERSION, version);
  }

  public void testGetComponentVersionWithExperimentalPlugin() throws Exception {
    loadProject(EXPERIMENTAL_PLUGIN);
    Module appModule = myModules.getAppModule();
    String version = myVersionReader.getComponentVersion(appModule);
    assertEquals(GRADLE_EXPERIMENTAL_PLUGIN_RECOMMENDED_VERSION, version);
  }

  public void testGetQuickFixes() throws Exception {
    loadSimpleApplication();
    Module appModule = myModules.getAppModule();

    List<NotificationHyperlink> quickFixes = myVersionReader.getQuickFixes(appModule, VersionRange.parse("[1.2.0, +)"), null);
    assertThat(quickFixes).hasSize(1);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(FixAndroidGradlePluginVersionHyperlink.class);

    FixAndroidGradlePluginVersionHyperlink fixAndroidGradlePluginQuickFix = (FixAndroidGradlePluginVersionHyperlink)quickFix;
    assertEquals(GRADLE_PLUGIN_RECOMMENDED_VERSION, fixAndroidGradlePluginQuickFix.getPluginVersion().toString());
  }

  public void testGetQuickFixesWithExperimentalPlugin() throws Exception {
    loadProject(EXPERIMENTAL_PLUGIN);
    Module appModule = myModules.getAppModule();

    List<NotificationHyperlink> quickFixes = myVersionReader.getQuickFixes(appModule, VersionRange.parse("[0.7.0, +)"), null);
    assertThat(quickFixes).hasSize(1);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(FixAndroidGradlePluginVersionHyperlink.class);

    FixAndroidGradlePluginVersionHyperlink fixAndroidGradlePluginQuickFix = (FixAndroidGradlePluginVersionHyperlink)quickFix;
    assertEquals(GRADLE_EXPERIMENTAL_PLUGIN_RECOMMENDED_VERSION, fixAndroidGradlePluginQuickFix.getPluginVersion().toString());
  }
}