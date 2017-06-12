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
package com.android.tools.idea.gradle.project.sync.setup.module.dependency;

import com.android.builder.model.level2.Library;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.Level2AndroidLibraryStub;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.Level2JavaLibraryStub;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.Level2ModuleLibraryStub;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.google.common.collect.Lists;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.builder.model.level2.Library.LIBRARY_JAVA;
import static com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency.PathType.BINARY;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

/**
 * Tests for {@link DependenciesExtractor}.
 */
public class DependenciesExtractorTest extends IdeaTestCase {
  private AndroidProjectStub myAndroidProject;
  private VariantStub myVariant;

  private DependenciesExtractor myDependenciesExtractor;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myAndroidProject = TestProjects.createBasicProject();
    myVariant = myAndroidProject.getFirstVariant();
    assertNotNull(myVariant);

    myDependenciesExtractor = new DependenciesExtractor();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myAndroidProject != null) {
        myAndroidProject.dispose();
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testExtractFromJavaLibrary() {
    File jarFile = new File("~/repo/guava/guava-11.0.2.jar");
    Library javaLibrary = new Level2JavaLibraryStub(LIBRARY_JAVA, "guava", jarFile);

    myVariant.getMainArtifact().getLevel2Dependencies().addJavaLibrary(javaLibrary);
    myVariant.getInstrumentTestArtifact().getLevel2Dependencies().addJavaLibrary(javaLibrary);

    Collection<LibraryDependency> dependencies = myDependenciesExtractor.extractFrom(myVariant).onLibraries();
    assertThat(dependencies).hasSize(1);

    LibraryDependency dependency = getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals("guava-11.0.2", dependency.getName());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(COMPILE, dependency.getScope());

    File[] binaryPaths = dependency.getPaths(BINARY);
    assertThat(binaryPaths).hasLength(1);
    assertEquals(jarFile, binaryPaths[0]);
  }

  public void testExtractFromAndroidLibraryWithLocalJar() {
    String rootDirPath = myAndroidProject.getRootDir().getPath();
    File libJar = new File(rootDirPath, join("bundle_aar", "androidLibrary.jar"));
    File resFolder = new File(rootDirPath, join("bundle_aar", "res"));
    File localJar = new File(rootDirPath, "local.jar");

    Level2AndroidLibraryStub library = new Level2AndroidLibraryStub() {
      @Override
      @NotNull
      public String getJarFile() {
        return libJar.getPath();
      }

      @Override
      @NotNull
      public String getArtifactAddress() {
        return "com.android.support:support-core-ui:25.3.1@aar";
      }

      @Override
      @NotNull
      public String getResFolder() {
        return resFolder.getPath();
      }

      @Override
      @NotNull
      public Collection<String> getLocalJars() {
        return Collections.singletonList(localJar.getPath());
      }
    };

    myVariant.getMainArtifact().getLevel2Dependencies().addAndroidLibrary(library);
    myVariant.getInstrumentTestArtifact().getLevel2Dependencies().addAndroidLibrary(library);

    List<LibraryDependency> dependencies = Lists.newArrayList(myDependenciesExtractor.extractFrom(myVariant).onLibraries());
    assertThat(dependencies).hasSize(1);

    LibraryDependency dependency = dependencies.get(0);
    assertNotNull(dependency);
    assertEquals("support-core-ui-25.3.1", dependency.getName());

    File[] binaryPaths = dependency.getPaths(BINARY);
    assertThat(binaryPaths).hasLength(3);
    assertThat(binaryPaths).asList().containsAllOf(localJar, libJar, resFolder);
  }

  public void testExtractFromModuleDependency() {
    String gradlePath = "abc:xyz:library";
    Level2ModuleLibraryStub library = new Level2ModuleLibraryStub() {
      @Override
      @NotNull
      public String getProjectPath() {
        return gradlePath;
      }
    };
    myVariant.getMainArtifact().getLevel2Dependencies().addModuleDependency(library);
    myVariant.getInstrumentTestArtifact().getLevel2Dependencies().addModuleDependency(library);
    Collection<ModuleDependency> dependencies = myDependenciesExtractor.extractFrom(myVariant).onModules();
    assertThat(dependencies).hasSize(1);

    ModuleDependency dependency = getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals(gradlePath, dependency.getGradlePath());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(COMPILE, dependency.getScope());

    LibraryDependency backup = dependency.getBackupDependency();
    assertNull(backup);
  }
}
