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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.testing.TestProjectPaths.*;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

/**
 * Tests for {@link PsAndroidModule}.
 */
public class PsAndroidModuleTest extends DependencyTestCase {

  public void testFlavorDimensions() throws Throwable {
    loadProject(PSD_SAMPLE);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    List<String> flavorDimensions = getFlavorDimensions(appModule);
    assertThat(flavorDimensions)
      .containsExactly("foo", "bar").inOrder();
  }

  public void testAddFlavorDimension() throws Throwable {
    loadProject(PSD_SAMPLE);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    appModule.addNewFlavorDimension("new");
    // A product flavor is required for successful sync.
    PsProductFlavor newInNew = appModule.addNewProductFlavor("new_in_new");
    newInNew.setDimension(new ParsedValue.Set.Parsed<String>("new", null));
    appModule.applyChanges();

    requestSyncAndWait();
    project = new PsProject(resolvedProject);
    appModule = (PsAndroidModule)project.findModuleByName("app");

    List<String> flavorDimensions = getFlavorDimensions(appModule);
    assertThat(flavorDimensions)
      .containsExactly("foo", "bar", "new").inOrder();
  }

  public void testRemoveFlavorDimension() throws Throwable {
    loadProject(PSD_SAMPLE);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    appModule.removeFlavorDimension("bar");
    // A product flavor must be removed for successful sync.
    appModule.removeProductFlavor(appModule.findProductFlavor("bar"));
    List<String> flavorDimensions = getFlavorDimensions(appModule);
    assertThat(flavorDimensions).containsExactly("foo", "bar").inOrder();
    appModule.applyChanges();

    requestSyncAndWait();
    project = new PsProject(resolvedProject);
    appModule = (PsAndroidModule)project.findModuleByName("app");

    flavorDimensions = getFlavorDimensions(appModule);
    assertThat(flavorDimensions).containsExactly("foo");
  }

  @NotNull
  private static List<String> getFlavorDimensions(@NotNull PsAndroidModule module) {
    return Lists.newArrayList(module.getFlavorDimensions());
  }

  public void testProductFlavors() throws Throwable {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    List<PsProductFlavor> productFlavors = appModule.getProductFlavors();
    assertThat(productFlavors.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("basic", "paid").inOrder();
    assertThat(productFlavors).hasSize(2);

    PsProductFlavor basic = appModule.findProductFlavor("basic");
    assertNotNull(basic);
    assertTrue(basic.isDeclared());

    PsProductFlavor release = appModule.findProductFlavor("paid");
    assertNotNull(release);
    assertTrue(release.isDeclared());
  }

  public void testAddProductFlavor() throws Throwable {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    List<PsProductFlavor> productFlavors = appModule.getProductFlavors();
    assertThat(productFlavors.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("basic", "paid").inOrder();

    appModule.addNewProductFlavor("new_flavor");

    productFlavors = appModule.getProductFlavors();
    assertThat(productFlavors.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("basic", "paid", "new_flavor").inOrder();

    PsProductFlavor newFlavor = appModule.findProductFlavor("new_flavor");
    assertNotNull(newFlavor);
    assertNull(newFlavor.getResolvedModel());

    appModule.applyChanges();
    requestSyncAndWait();
    project = new PsProject(resolvedProject);
    appModule = (PsAndroidModule)project.findModuleByName("app");

    productFlavors = appModule.getProductFlavors();
    assertThat(productFlavors.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("basic", "paid", "new_flavor").inOrder();

    newFlavor = appModule.findProductFlavor("new_flavor");
    assertNotNull(newFlavor);
    assertNotNull(newFlavor.getResolvedModel());
  }

  public void testRemoveProductFlavor() throws Throwable {
    loadProject(PSD_SAMPLE);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    List<PsProductFlavor> productFlavors = appModule.getProductFlavors();
    assertThat(productFlavors.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("basic", "paid", "bar").inOrder();

    appModule.removeProductFlavor(appModule.findProductFlavor("paid"));

    productFlavors = appModule.getProductFlavors();
    assertThat(productFlavors.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("basic", "bar").inOrder();

    appModule.applyChanges();
    requestSyncAndWait();
    project = new PsProject(resolvedProject);
    appModule = (PsAndroidModule)project.findModuleByName("app");

    productFlavors = appModule.getProductFlavors();
    assertThat(productFlavors.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("basic", "bar").inOrder();
  }

  public void testBuildTypes() throws Throwable {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    List<PsBuildType> buildTypes = appModule.getBuildTypes();
    assertThat(buildTypes.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("release", "debug").inOrder();
    assertThat(buildTypes).hasSize(2);

    PsBuildType release = appModule.findBuildType("release");
    assertNotNull(release);
    assertTrue(release.isDeclared());

    PsBuildType debug = appModule.findBuildType("debug");
    assertNotNull(debug);
    assertTrue(!debug.isDeclared());
  }

  public void testAddBuildType() throws Throwable {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    List<PsBuildType> buildTypes = appModule.getBuildTypes();
    assertThat(buildTypes.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("release", "debug").inOrder();

    appModule.addNewBuildType("new_build_type");

    buildTypes = appModule.getBuildTypes();
    assertThat(buildTypes.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("release", "debug", "new_build_type").inOrder();

    PsBuildType newBuildType = appModule.findBuildType("new_build_type");
    assertNotNull(newBuildType);
    assertNull(newBuildType.getResolvedModel());

    appModule.applyChanges();
    requestSyncAndWait();
    project = new PsProject(resolvedProject);
    appModule = (PsAndroidModule)project.findModuleByName("app");

    buildTypes = appModule.getBuildTypes();
    assertThat(buildTypes.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("release", "new_build_type", "debug").inOrder();  // "debug" is not declared and goes last.

    newBuildType = appModule.findBuildType("new_build_type");
    assertNotNull(newBuildType);
    assertNotNull(newBuildType.getResolvedModel());
  }

  public void testRemoveBuildType() throws Throwable {
    loadProject(PSD_SAMPLE);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    List<PsBuildType> buildTypes = appModule.getBuildTypes();
    assertThat(buildTypes.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("release", "debug").inOrder();

    appModule.removeBuildType(appModule.findBuildType("release"));

    buildTypes = appModule.getBuildTypes();
    assertThat(buildTypes.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("debug");

    appModule.applyChanges();
    requestSyncAndWait();
    project = new PsProject(resolvedProject);
    appModule = (PsAndroidModule)project.findModuleByName("app");

    buildTypes = appModule.getBuildTypes();
    assertThat(buildTypes.stream().map(v -> v.getName()).collect(toList()))
      .containsExactly("debug", "release").inOrder();  // "release" is not declared and goes last.

    PsBuildType release = appModule.findBuildType("release");
    assertNotNull(release);
    assertFalse(release.isDeclared());
  }

  public void testVariants() throws Throwable {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    Collection<PsVariant> variants = appModule.getVariants();
    assertThat(variants).hasSize(4);

    PsVariant paidDebug = appModule.findVariant("paidDebug");
    assertNotNull(paidDebug);
    List<String> flavors = paidDebug.getProductFlavors();
    assertThat(flavors).containsExactly("paid");

    PsVariant paidRelease = appModule.findVariant("paidRelease");
    assertNotNull(paidRelease);
    flavors = paidRelease.getProductFlavors();
    assertThat(flavors).containsExactly("paid");

    PsVariant basicDebug = appModule.findVariant("basicDebug");
    assertNotNull(basicDebug);
    flavors = basicDebug.getProductFlavors();
    assertThat(flavors).containsExactly("basic");

    PsVariant basicRelease = appModule.findVariant("basicRelease");
    assertNotNull(basicRelease);
    flavors = basicRelease.getProductFlavors();
    assertThat(flavors).containsExactly("basic");
  }

  public void testCanDependOnModules() throws Throwable {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByName("app");
    assertNotNull(appModule);

    PsAndroidModule libModule = (PsAndroidModule)project.findModuleByName("lib");
    assertNotNull(libModule);

    assertTrue(appModule.canDependOn(libModule));
    assertFalse(libModule.canDependOn(appModule));
  }

  public void testSigningConfigs() throws Throwable {
    loadProject(BASIC);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByGradlePath(":");
    assertNotNull(appModule);

    List<PsSigningConfig> signingConfigs = appModule.getSigningConfigs();
    assertThat(signingConfigs).hasSize(2);

    PsSigningConfig myConfig = appModule.findSigningConfig("myConfig");
    assertNotNull(myConfig);
    assertTrue(myConfig.isDeclared());

    PsSigningConfig debugConfig = appModule.findSigningConfig("debug");
    assertNotNull(debugConfig);
    assertTrue(!debugConfig.isDeclared());
  }

  public void testAddSigningConfig() throws Throwable {
    loadProject(BASIC);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByGradlePath(":");
    assertNotNull(appModule);

    List<PsSigningConfig> signingConfigs = appModule.getSigningConfigs();
    assertThat(signingConfigs.stream().map(v -> v.getName()).collect(toList())).containsExactly("myConfig", "debug").inOrder();

    PsSigningConfig myConfig = appModule.addNewSigningConfig("config2");
    myConfig.setStoreFile(new ParsedValue.Set.Parsed<File>(new File("/tmp/1"), null));

    assertNotNull(myConfig);
    assertTrue(myConfig.isDeclared());

    signingConfigs = appModule.getSigningConfigs();
    assertThat(signingConfigs.stream().map(v -> v.getName()).collect(toList())).containsExactly("myConfig", "debug", "config2").inOrder();

    appModule.applyChanges();
    requestSyncAndWait();
    project = new PsProject(resolvedProject);
    appModule = (PsAndroidModule)project.findModuleByGradlePath(":");

    signingConfigs = appModule.getSigningConfigs();
    assertThat(signingConfigs.stream().map(v -> v.getName()).collect(toList())).containsExactly("myConfig", "config2", "debug").inOrder();
  }

  public void testRemoveSigningConfig() throws Throwable {
    loadProject(BASIC);

    Project resolvedProject = myFixture.getProject();
    PsProject project = new PsProject(resolvedProject);

    PsAndroidModule appModule = (PsAndroidModule)project.findModuleByGradlePath(":");
    assertNotNull(appModule);

    List<PsSigningConfig> signingConfigs = appModule.getSigningConfigs();
    assertThat(signingConfigs.stream().map(v -> v.getName()).collect(toList())).containsExactly("myConfig", "debug").inOrder();

    appModule.removeSigningConfig(appModule.findSigningConfig("myConfig"));
    appModule.removeBuildType(appModule.findBuildType("debug"));  // Remove (clean) the build type that refers to the signing config.

    signingConfigs = appModule.getSigningConfigs();
    assertThat(signingConfigs.stream().map(v -> v.getName()).collect(toList())).containsExactly("debug");

    appModule.applyChanges();
    requestSyncAndWait();
    project = new PsProject(resolvedProject);
    appModule = (PsAndroidModule)project.findModuleByGradlePath(":");

    signingConfigs = appModule.getSigningConfigs();
    assertThat(signingConfigs.stream().map(v -> v.getName()).collect(toList())).containsExactly("debug");
  }
}
