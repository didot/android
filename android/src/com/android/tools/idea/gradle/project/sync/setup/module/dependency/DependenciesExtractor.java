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

import com.android.builder.model.*;
import com.android.ide.common.builder.model.IdeVariant;
import com.google.common.collect.Sets;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import static com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency.PathType.BINARY;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.DependencyScope.TEST;
import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class DependenciesExtractor {
  @NotNull
  public static DependenciesExtractor getInstance() {
    return ServiceManager.getService(DependenciesExtractor.class);
  }

  @NotNull
  public DependencySet extractFrom(@NotNull IdeVariant variant) {
    DependencySet dependencies = new DependencySet();

    for (BaseArtifact testArtifact : variant.getTestArtifacts()) {
      populate(dependencies, testArtifact, TEST);
    }

    AndroidArtifact mainArtifact = variant.getMainArtifact();
    populate(dependencies, mainArtifact, COMPILE);

    return dependencies;
  }

  @NotNull
  public DependencySet extractFrom(@NotNull BaseArtifact artifact, @NotNull DependencyScope scope) {
    DependencySet dependencies = new DependencySet();
    populate(dependencies, artifact, scope);
    return dependencies;
  }

  private static void populate(@NotNull DependencySet dependencies,
                               @NotNull BaseArtifact artifact,
                               @NotNull DependencyScope scope) {
    Dependencies artifactDependencies = artifact.getDependencies();
    addJavaLibraries(dependencies, artifactDependencies.getJavaLibraries(), scope);

    Set<File> unique = Sets.newHashSet();
    for (AndroidLibrary library : artifactDependencies.getLibraries()) {
      addAndroidLibrary(library, dependencies, scope, unique);
    }

    for (String gradleProjectPath : artifactDependencies.getProjects()) {
      if (gradleProjectPath != null && !gradleProjectPath.isEmpty()) {
        ModuleDependency dependency = new ModuleDependency(gradleProjectPath, scope);
        dependencies.add(dependency);
      }
    }
  }

  @NotNull
  private static String getBundleName(@NotNull AndroidBundle bundle) {
    MavenCoordinates coordinates = bundle.getResolvedCoordinates();
    //noinspection ConstantConditions
    if (coordinates != null) {
      return coordinates.getArtifactId() + "-" + coordinates.getVersion();
    }
    File bundleFile = bundle.getBundle();
    return getNameWithoutExtension(bundleFile);
  }

  private static boolean isAlreadySeen(@NotNull AndroidBundle bundle, @NotNull Set<File> unique) {
    // We're using the library location as a unique handle rather than the AndroidLibrary instance itself, in case
    // the model just blindly manufactures library instances as it's following dependencies
    File folder = bundle.getFolder();
    if (unique.contains(folder)) {
      return true;
    }
    unique.add(folder);
    return false;
  }

  /**
   * Add an Android library, along with any recursive library dependencies
   */
  private static void addAndroidLibrary(@NotNull AndroidLibrary library,
                                        @NotNull DependencySet dependencies,
                                        @NotNull DependencyScope scope,
                                        @NotNull Set<File> unique) {
    if (isAlreadySeen(library, unique)) {
      return;
    }

    String gradleProjectPath = library.getProject();
    if (isNotEmpty(gradleProjectPath)) {
      ModuleDependency dependency = new ModuleDependency(gradleProjectPath, scope);
      // Add the aar as dependency in case there is a module dependency that cannot be satisfied (e.g. the module is outside of the
      // project.) If we cannot set the module dependency, we set a library dependency instead.
      dependency.setBackupDependency(createLibraryDependencyFromAndroidLibrary(library, scope));
      dependencies.add(dependency);
    }
    else {
      dependencies.add(createLibraryDependencyFromAndroidLibrary(library, scope));
    }

    addBundleTransitiveDependencies(library, dependencies, scope, unique);
  }

  private static void addBundleTransitiveDependencies(@NotNull AndroidBundle bundle,
                                                      @NotNull DependencySet dependencies,
                                                      @NotNull DependencyScope scope,
                                                      @NotNull Set<File> unique) {
    for (AndroidLibrary dependentLibrary : bundle.getLibraryDependencies()) {
      addAndroidLibrary(dependentLibrary, dependencies, scope, unique);
    }
  }

  @NotNull
  private static LibraryDependency createLibraryDependencyFromAndroidLibrary(@NotNull AndroidLibrary library,
                                                                             @NotNull DependencyScope scope) {
    LibraryDependency dependency = new LibraryDependency(library.getBundle(), getBundleName(library), scope);
    dependency.addPath(BINARY, library.getJarFile());
    dependency.addPath(BINARY, library.getResFolder());

    for (File localJar : library.getLocalJars()) {
      dependency.addPath(BINARY, localJar);
    }
    return dependency;
  }

  private static void addJavaLibraries(@NotNull DependencySet dependencies,
                                       @NotNull Collection<? extends JavaLibrary> libraries,
                                       @NotNull DependencyScope scope) {
    for (JavaLibrary library : libraries) {
      addJavaLibrary(library, dependencies, scope);
    }
  }

  private static void addJavaLibrary(@NotNull JavaLibrary library, @NotNull DependencySet dependencies, @NotNull DependencyScope scope) {
    dependencies.add(createLibraryDependencyFromJavaLibrary(library, scope));
    addJavaLibraries(dependencies, library.getDependencies(), scope);
  }

  @NotNull
  private static LibraryDependency createLibraryDependencyFromJavaLibrary(@NotNull JavaLibrary library, @NotNull DependencyScope scope) {
    File jarFilePath = library.getJarFile();
    return new LibraryDependency(jarFilePath, scope);
  }
}
