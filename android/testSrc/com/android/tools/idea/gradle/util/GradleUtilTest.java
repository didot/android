/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static com.google.common.io.Files.createTempDir;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeBaseArtifact;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.level2.IdeDependencies;
import com.android.ide.common.repository.GradleVersion;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;

/**
 * Tests for {@link GradleUtil}.
 */
public class GradleUtilTest {
  private File myTempDir;

  @After
  public void tearDown() {
    if (myTempDir != null) {
      delete(myTempDir);
    }
  }

  @Test
  public void getGradleInvocationJvmArgWithNullBuildMode() {
    assertNull(GradleUtil.getGradleInvocationJvmArg(null));
  }

  @Test
  public void getGradleInvocationJvmArgWithAssembleTranslateBuildMode() {
    assertEquals("-DenableTranslation=true", GradleUtil.getGradleInvocationJvmArg(BuildMode.ASSEMBLE_TRANSLATE));
  }

  @Test
  public void getPathSegments() {
    List<String> pathSegments = GradleUtil.getPathSegments("foo:bar:baz");
    assertEquals(Lists.newArrayList("foo", "bar", "baz"), pathSegments);
  }

  @Test
  public void getPathSegmentsWithEmptyString() {
    List<String> pathSegments = GradleUtil.getPathSegments("");
    assertEquals(0, pathSegments.size());
  }

  @Test
  public void getGradleBuildFilePath() {
    myTempDir = Files.createTempDir();
    File buildFilePath = GradleUtil.getGradleBuildFilePath(myTempDir);
    assertEquals(new File(myTempDir, FN_BUILD_GRADLE), buildFilePath);
  }

  @Test
  public void getKtsGradleBuildFilePath() throws IOException {
    myTempDir = createTempDir();
    File ktsBuildFilePath = new File(myTempDir, FN_BUILD_GRADLE_KTS);
    writeToFile(ktsBuildFilePath, "");
    assertEquals(ktsBuildFilePath, GradleUtil.getGradleBuildFilePath(myTempDir));
  }

  @Test
  public void getGradleWrapperVersionWithUrl() {
    // Tries both http and https, bin and all. Also versions 2.2.1, 2.2 and 1.12
    String url = "https://services.gradle.org/distributions/gradle-2.2.1-all.zip";
    String version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "https://services.gradle.org/distributions/gradle-2.2.1-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "http://services.gradle.org/distributions/gradle-2.2.1-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "http://services.gradle.org/distributions/gradle-2.2.1-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2.1", version);

    url = "https://services.gradle.org/distributions/gradle-2.2-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "https://services.gradle.org/distributions/gradle-2.2-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "http://services.gradle.org/distributions/gradle-2.2-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "http://services.gradle.org/distributions/gradle-2.2-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("2.2", version);

    url = "https://services.gradle.org/distributions/gradle-1.12-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    url = "https://services.gradle.org/distributions/gradle-1.12-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    url = "http://services.gradle.org/distributions/gradle-1.12-all.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    url = "http://services.gradle.org/distributions/gradle-1.12-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertEquals("1.12", version);

    // Use custom URL.
    url = "http://myown.com/gradle-2.2.1-bin.zip";
    version = GradleUtil.getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
    assertNull(version);
  }

  @Test
  public void mapConfigurationName() {
    assertEquals("compile", GradleUtil.mapConfigurationName("compile", "2.3.2", false));
    assertEquals("testCompile", GradleUtil.mapConfigurationName("testCompile", "2.3.2", false));
    assertEquals("androidTestCompile", GradleUtil.mapConfigurationName("androidTestCompile", "2.3.2", false));
    assertEquals("provided", GradleUtil.mapConfigurationName("provided", "2.3.2", false));
    assertEquals("testProvided", GradleUtil.mapConfigurationName("testProvided", "2.3.2", false));

    assertEquals("implementation", GradleUtil.mapConfigurationName("compile", "3.0.0-alpha1", false));
    assertEquals("testImplementation", GradleUtil.mapConfigurationName("testCompile", "3.0.0-alpha1", false));
    assertEquals("androidTestImplementation", GradleUtil.mapConfigurationName("androidTestCompile", "3.0.0-alpha1", false));
    assertEquals("compileOnly", GradleUtil.mapConfigurationName("provided", "3.0.0-alpha1, false", false));
    assertEquals("testCompileOnly", GradleUtil.mapConfigurationName("testProvided", "3.0.0-alpha1", false));

    assertEquals("api", GradleUtil.mapConfigurationName("compile", "3.0.0-alpha1", true));
    assertEquals("testApi", GradleUtil.mapConfigurationName("testCompile", "3.0.0-alpha1", true));
    assertEquals("androidTestApi", GradleUtil.mapConfigurationName("androidTestCompile", "3.0.0-alpha1", true));
    assertEquals("compileOnly", GradleUtil.mapConfigurationName("provided", "3.0.0-alpha1", true));
    assertEquals("testCompileOnly", GradleUtil.mapConfigurationName("testProvided", "3.0.0-alpha1", true));
  }

  @Test
  public void useCompatibilityConfigurationNames() {
    assertTrue(GradleUtil.useCompatibilityConfigurationNames(GradleVersion.parse("2.3.2")));
    assertFalse(GradleUtil.useCompatibilityConfigurationNames((GradleVersion)null));
    assertFalse(GradleUtil.useCompatibilityConfigurationNames(GradleVersion.parse("3.0.0-alpha1")));
    assertFalse(GradleUtil.useCompatibilityConfigurationNames(GradleVersion.parse("3.0.0")));
    assertFalse(GradleUtil.useCompatibilityConfigurationNames(GradleVersion.parse("4.0.0")));
  }

  @Test
  public void isAaptGeneratedSourceFolder() {
    myTempDir = createTempDir();

    // 3.1 and below:
    checkIfRecognizedAsAapt("generated/source/r/debug");
    checkIfRecognizedAsAapt("generated/source/r/flavorOneFlavorTwo/debug");
    checkIfRecognizedAsAapt("generated/source/r/androidTest/debug");
    checkIfRecognizedAsAapt("generated/source/r/androidTest/flavorOneFlavorTwo/debug");

    // 3.2:
    checkIfRecognizedAsAapt("generated/not_namespaced_r_class_sources/debug/processDebugResources/r");
    checkIfRecognizedAsAapt("generated/not_namespaced_r_class_sources/flavorOneFlavorTwoDebug/processDebugResources/r");
    checkIfRecognizedAsAapt("generated/not_namespaced_r_class_sources/debug/generateDebugRFile/out"); // Library projects.
    checkIfRecognizedAsAapt("generated/not_namespaced_r_class_sources/debugAndroidTest/processDebugAndroidTestResources/r");
    checkIfRecognizedAsAapt("generated/not_namespaced_r_class_sources/flavorOneFlavorTwoDebugAndroidTest/processFlavorOneFlavorTwoDebugAndroidTestResources/r");
  }

  private void checkIfRecognizedAsAapt(@NotNull String path) {
    File dir = new File(myTempDir, FileUtils.toSystemDependentPath(path));
    assertTrue(dir + " not recognized as R classes directory.", GradleUtil.isAaptGeneratedSourcesFolder(dir, myTempDir));
  }

  @Test
  public void isDataBindingGeneratedSourceFolder() {
    myTempDir = createTempDir();

    // Ignore generated data binding base-class directory...
    assertTrue(isRecognizedAsDataBindingBaseClass("generated/data_binding_base_class_source_out/debug/dataBindingGenBaseClassesDebug/out"));

    // Do NOT ignore generated data binding classes under other locations
    assertFalse(isRecognizedAsDataBindingBaseClass("generated/source/apt/debug"));
    assertFalse(isRecognizedAsDataBindingBaseClass("generated/source/kapt/debug"));
  }

  private boolean isRecognizedAsDataBindingBaseClass(@NotNull String path) {
    File dir = new File(myTempDir, FileUtils.toSystemDependentPath(path));
    return GradleUtil.isDataBindingGeneratedBaseClassesFolder(dir, myTempDir);
  }

  @Test
  public void getModuleDependencies() {
    // Create mock objects.
    IdeVariant variant = mock(IdeVariant.class);
    IdeAndroidArtifact mainArtifact = mock(IdeAndroidArtifact.class);
    IdeBaseArtifact testArtifact = mock(IdeBaseArtifact.class);
    Library dependency1 = mock(Library.class);
    Library dependency2 = mock(Library.class);
    IdeDependencies mainArtifactLevel2Dependencies = mock(IdeDependencies.class);
    IdeDependencies testArtifactLevel2Dependencies = mock(IdeDependencies.class);

    // Mock the main artifact calls.
    when(variant.getMainArtifact()).thenReturn(mainArtifact);
    when(mainArtifact.getLevel2Dependencies()).thenReturn(mainArtifactLevel2Dependencies);
    when(mainArtifactLevel2Dependencies.getModuleDependencies()).thenReturn(Collections.singletonList(dependency1));

    // Mock the test artifact calls.
    when(variant.getTestArtifacts()).thenReturn(Collections.singletonList(testArtifact));
    when(testArtifact.getLevel2Dependencies()).thenReturn(testArtifactLevel2Dependencies);
    when(testArtifactLevel2Dependencies.getModuleDependencies()).thenReturn(Arrays.asList(dependency1, dependency2));

    // Call the method being tested.
    List<Library> dependencies = GradleUtil.getModuleDependencies(variant);

    // Verify the result. Repeated dependency1 object should be added only once.
    assertThat(dependencies).containsExactlyElementsIn(Arrays.asList(dependency1, dependency2));
  }

  @Test
  public void getParentModulesPaths() {
    assertEquals(Lists.newArrayList(":foo"), GradleUtil.getParentModulesPaths(":foo:buz"));
    assertEquals(Lists.newArrayList(), GradleUtil.getParentModulesPaths(":foo"));
    assertEquals(Lists.newArrayList(), GradleUtil.getParentModulesPaths(":"));
    assertEquals(Lists.newArrayList(":foo", ":foo:bar", ":foo:bar:buz"), GradleUtil.getParentModulesPaths(":foo:bar:buz:lib"));
  }
}
