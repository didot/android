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
package com.android.tools.idea.gradle.variant.view;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;

import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_MODULES;
import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_NATIVE_MODULES;
import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;

public class BuildVariantUpdaterIntegTest extends AndroidGradleTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // This test requires Single Variant Sync to be turned off
    GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = false;
  }

  public void testWithModules() throws Exception {
    loadProject(DYNAMIC_APP);

    AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
    AndroidModuleModel featureAndroidModel = AndroidModuleModel.get(getModule("feature1"));
    assertNotNull(appAndroidModel);
    assertNotNull(featureAndroidModel);
    assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
    assertEquals("debug", featureAndroidModel.getSelectedVariant().getName());

    BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), "app", "release");

    assertEquals("release", appAndroidModel.getSelectedVariant().getName());
    assertEquals("release", featureAndroidModel.getSelectedVariant().getName());

    // Gets served from cache.
    BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), "app", "debug");
    assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
    assertEquals("debug", featureAndroidModel.getSelectedVariant().getName());
  }

  public void testWithProductFlavors() throws Exception {
    loadProject(DEPENDENT_MODULES);

    AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
    AndroidModuleModel libAndroidModel = AndroidModuleModel.get(getModule("lib"));
    assertNotNull(appAndroidModel);
    assertNotNull(libAndroidModel);
    assertEquals("basicDebug", appAndroidModel.getSelectedVariant().getName());
    assertEquals("debug", libAndroidModel.getSelectedVariant().getName());

    // Triggers a sync.
    BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), "app", "basicRelease");
    assertEquals("basicRelease", appAndroidModel.getSelectedVariant().getName());
    assertEquals("release", libAndroidModel.getSelectedVariant().getName());

    // Gets served from cache.
    BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), "app", "basicDebug");
    assertEquals("basicDebug", appAndroidModel.getSelectedVariant().getName());
    assertEquals("debug", libAndroidModel.getSelectedVariant().getName());
  }

  public void testWithNativeModulesChangeBuildVariant() throws Exception {
    loadProject(DEPENDENT_NATIVE_MODULES);

    AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
    AndroidModuleModel lib1AndroidModel = AndroidModuleModel.get(getModule("lib1"));
    AndroidModuleModel lib2AndroidModel = AndroidModuleModel.get(getModule("lib2"));
    AndroidModuleModel lib3AndroidModel = AndroidModuleModel.get(getModule("lib3"));
    assertNotNull(appAndroidModel);
    assertNotNull(lib1AndroidModel);
    assertNotNull(lib2AndroidModel);
    assertNotNull(lib3AndroidModel);
    assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib1AndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib2AndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib3AndroidModel.getSelectedVariant().getName());

    NdkModuleModel appNdkModel = NdkModuleModel.get(getModule("app"));
    NdkModuleModel lib1NdkModel = NdkModuleModel.get(getModule("lib1"));
    NdkModuleModel lib2NdkModel = NdkModuleModel.get(getModule("lib2"));
    NdkModuleModel lib3NdkModel = NdkModuleModel.get(getModule("lib3"));
    assertNotNull(appNdkModel);
    assertNull(lib1NdkModel);
    assertNotNull(lib2NdkModel);
    assertNotNull(lib3NdkModel);
    assertEquals("debug-x86", appNdkModel.getSelectedVariant().getName());
    assertEquals("debug-x86", lib2NdkModel.getSelectedVariant().getName());
    assertEquals("debug-x86", lib3NdkModel.getSelectedVariant().getName());

    // Triggers a sync.
    BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), "app", "release");
    assertEquals("release", appAndroidModel.getSelectedVariant().getName());
    assertEquals("release", lib1AndroidModel.getSelectedVariant().getName());
    assertEquals("release", lib2AndroidModel.getSelectedVariant().getName());
    assertEquals("release", lib3AndroidModel.getSelectedVariant().getName());
    assertEquals("release-x86", appNdkModel.getSelectedVariant().getName());
    assertEquals("release-x86", lib2NdkModel.getSelectedVariant().getName());
    assertEquals("release-x86", lib3NdkModel.getSelectedVariant().getName());

    // Gets served from cache.
    BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), "app", "debug");
    assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib1AndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib2AndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib3AndroidModel.getSelectedVariant().getName());
    assertEquals("debug-x86", appNdkModel.getSelectedVariant().getName());
    assertEquals("debug-x86", lib2NdkModel.getSelectedVariant().getName());
    assertEquals("debug-x86", lib3NdkModel.getSelectedVariant().getName());
  }

  public void testWithNativeModulesChangeAbi() throws Exception {
    loadProject(DEPENDENT_NATIVE_MODULES);

    AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
    AndroidModuleModel lib1AndroidModel = AndroidModuleModel.get(getModule("lib1"));
    AndroidModuleModel lib2AndroidModel = AndroidModuleModel.get(getModule("lib2"));
    AndroidModuleModel lib3AndroidModel = AndroidModuleModel.get(getModule("lib3"));
    assertNotNull(appAndroidModel);
    assertNotNull(lib1AndroidModel);
    assertNotNull(lib2AndroidModel);
    assertNotNull(lib3AndroidModel);
    assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib1AndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib2AndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib3AndroidModel.getSelectedVariant().getName());

    NdkModuleModel appNdkModel = NdkModuleModel.get(getModule("app"));
    NdkModuleModel lib1NdkModel = NdkModuleModel.get(getModule("lib1"));
    NdkModuleModel lib2NdkModel = NdkModuleModel.get(getModule("lib2"));
    NdkModuleModel lib3NdkModel = NdkModuleModel.get(getModule("lib3"));
    assertNotNull(appNdkModel);
    assertNull(lib1NdkModel);
    assertNotNull(lib2NdkModel);
    assertNotNull(lib3NdkModel);
    assertEquals("debug-x86", appNdkModel.getSelectedVariant().getName());
    assertEquals("debug-x86", lib2NdkModel.getSelectedVariant().getName());
    assertEquals("debug-x86", lib3NdkModel.getSelectedVariant().getName());

    // Triggers a sync.
    BuildVariantUpdater.getInstance(getProject()).updateSelectedAbi(getProject(), "app", "armeabi-v7a");
    assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib1AndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib2AndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib3AndroidModel.getSelectedVariant().getName());
    assertEquals("debug-armeabi-v7a", appNdkModel.getSelectedVariant().getName());
    assertEquals("debug-armeabi-v7a", lib2NdkModel.getSelectedVariant().getName());
    assertEquals("debug-armeabi-v7a", lib3NdkModel.getSelectedVariant().getName());

    // Gets served from cache.
    BuildVariantUpdater.getInstance(getProject()).updateSelectedAbi(getProject(), "app", "x86");
    assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib1AndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib2AndroidModel.getSelectedVariant().getName());
    assertEquals("debug", lib3AndroidModel.getSelectedVariant().getName());
    assertEquals("debug-x86", appNdkModel.getSelectedVariant().getName());
    assertEquals("debug-x86", lib2NdkModel.getSelectedVariant().getName());
    assertEquals("debug-x86", lib3NdkModel.getSelectedVariant().getName());
  }
}
