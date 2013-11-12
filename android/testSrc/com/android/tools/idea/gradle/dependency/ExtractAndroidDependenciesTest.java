/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.dependency;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidLibraryStub;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.ArtifactInfoStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.google.common.collect.Lists;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Tests for {@link Dependency#extractFrom(com.android.tools.idea.gradle.IdeaAndroidProject)}.
 */
public class ExtractAndroidDependenciesTest extends IdeaTestCase {
  private IdeaAndroidProject myIdeaAndroidProject;
  private AndroidProjectStub myAndroidProject;
  private VariantStub myVariant;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myAndroidProject = TestProjects.createBasicProject();
    myVariant = myAndroidProject.getFirstVariant();
    assertNotNull(myVariant);

    File rootDir = myAndroidProject.getRootDir();
    myIdeaAndroidProject = new IdeaAndroidProject(myAndroidProject.getName(), rootDir, myAndroidProject, myVariant.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    if (myAndroidProject != null) {
      myAndroidProject.dispose();
    }
    super.tearDown();
  }

  public void testExtractFromWithJar() {
    File jarFile = new File("~/repo/guava/guava-11.0.2.jar");

    myVariant.getMainArtifactInfo().getDependencies().addJar(jarFile);
    getArtifactInfo().getDependencies().addJar(jarFile);

    Collection<Dependency> dependencies = Dependency.extractFrom(myIdeaAndroidProject);
    assertEquals(1, dependencies.size());

    LibraryDependency dependency = (LibraryDependency)ContainerUtil.getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals("guava-11.0.2", dependency.getName());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(DependencyScope.COMPILE, dependency.getScope());

    Collection<String> binaryPaths = dependency.getPaths(LibraryDependency.PathType.BINARY);
    assertEquals(1, binaryPaths.size());
    assertEquals(jarFile.getPath(), ContainerUtil.getFirstItem(binaryPaths));
  }

  public void testExtractFromWithLibraryProject() {
    String rootDirPath = myAndroidProject.getRootDir().getPath();
    File libJar = new File(rootDirPath, "library.aar/library.jar");
    String gradlePath = "abc:xyz:library";
    AndroidLibraryStub library = new AndroidLibraryStub(libJar, gradlePath);

    myVariant.getMainArtifactInfo().getDependencies().addLibrary(library);
    getArtifactInfo().getDependencies().addLibrary(library);

    Collection<Dependency> dependencies = Dependency.extractFrom(myIdeaAndroidProject);
    assertEquals(1, dependencies.size());

    ModuleDependency dependency = (ModuleDependency)ContainerUtil.getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals("library", dependency.getName());
    assertEquals(gradlePath, dependency.getGradlePath());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(DependencyScope.COMPILE, dependency.getScope());

    LibraryDependency backup = dependency.getBackupDependency();
    assertNotNull(backup);
    assertEquals("library.aar", backup.getName());
    assertEquals(DependencyScope.COMPILE, backup.getScope());

    Collection<String> binaryPaths = backup.getPaths(LibraryDependency.PathType.BINARY);
    assertEquals(1, binaryPaths.size());
    assertEquals(libJar.getPath(), ContainerUtil.getFirstItem(binaryPaths));
  }

  public void testExtractFromWithLibraryAar() {
    String rootDirPath = myAndroidProject.getRootDir().getPath();
    File libJar = new File(rootDirPath, "library.aar/library.jar");
    AndroidLibraryStub library = new AndroidLibraryStub(libJar);

    myVariant.getMainArtifactInfo().getDependencies().addLibrary(library);
    getArtifactInfo().getDependencies().addLibrary(library);

    Collection<Dependency> dependencies = Dependency.extractFrom(myIdeaAndroidProject);
    assertEquals(1, dependencies.size());

    LibraryDependency dependency = (LibraryDependency)ContainerUtil.getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals("library.aar", dependency.getName());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(DependencyScope.COMPILE, dependency.getScope());

    Collection<String> binaryPaths = dependency.getPaths(LibraryDependency.PathType.BINARY);
    assertEquals(1, binaryPaths.size());
    assertEquals(libJar.getPath(), ContainerUtil.getFirstItem(binaryPaths));
  }

  public void testExtractFromWithLibraryLocalJar() {
    String rootDirPath = myAndroidProject.getRootDir().getPath();
    File libJar = new File(rootDirPath, "library.aar/library.jar");
    AndroidLibraryStub library = new AndroidLibraryStub(libJar);

    File localJar = new File(rootDirPath, "local.jar");
    library.addLocalJar(localJar);

    myVariant.getMainArtifactInfo().getDependencies().addLibrary(library);
    getArtifactInfo().getDependencies().addLibrary(library);

    List<Dependency> dependencies = Lists.newArrayList(Dependency.extractFrom(myIdeaAndroidProject));
    assertEquals(2, dependencies.size());

    LibraryDependency dependency = (LibraryDependency)dependencies.get(1);
    assertNotNull(dependency);
    assertEquals("local", dependency.getName());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(DependencyScope.COMPILE, dependency.getScope());

    Collection<String> binaryPaths = dependency.getPaths(LibraryDependency.PathType.BINARY);
    assertEquals(1, binaryPaths.size());
    assertEquals(localJar.getPath(), ContainerUtil.getFirstItem(binaryPaths));
  }

  public void testExtractFromWithProject() {
    String gradlePath = "abc:xyz:library";
    myVariant.getMainArtifactInfo().getDependencies().addProject(gradlePath);
    getArtifactInfo().getDependencies().addProject(gradlePath);
    Collection<Dependency> dependencies = Dependency.extractFrom(myIdeaAndroidProject);
    assertEquals(1, dependencies.size());

    ModuleDependency dependency = (ModuleDependency)ContainerUtil.getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals("library", dependency.getName());
    assertEquals(gradlePath, dependency.getGradlePath());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(DependencyScope.COMPILE, dependency.getScope());

    LibraryDependency backup = dependency.getBackupDependency();
    assertNull(backup);
  }

  @NotNull
  private ArtifactInfoStub getArtifactInfo() {
    ArtifactInfoStub testArtifactInfo = myVariant.getTestArtifactInfo();
    assertNotNull(testArtifactInfo);
    return testArtifactInfo;
  }
}
