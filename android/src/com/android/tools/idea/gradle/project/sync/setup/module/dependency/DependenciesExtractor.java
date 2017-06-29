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
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.project.model.ide.android.IdeAndroidArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeBaseArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeLevel2Dependencies;
import com.android.tools.idea.gradle.project.model.ide.android.IdeVariant;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency.PathType.BINARY;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.DependencyScope.TEST;

/**
 * Creates {@link DependencySet} from variant or artifact.
 */
public class DependenciesExtractor {
  @NotNull
  public static DependenciesExtractor getInstance() {
    return ServiceManager.getService(DependenciesExtractor.class);
  }

  /**
   * Get a {@link DependencySet} contains merged dependencies from main artifact and test artifacts.
   *
   * @param variant the variant to extract dependencies from.
   * @return Instance of {@link DependencySet} retrieved from given variant.
   */
  @NotNull
  public DependencySet extractFrom(@NotNull IdeVariant variant) {
    DependencySet dependencies = new DependencySet();

    for (IdeBaseArtifact testArtifact : variant.getTestArtifacts()) {
      populate(dependencies, testArtifact, TEST);
    }

    IdeAndroidArtifact mainArtifact = variant.getMainArtifact();
    populate(dependencies, mainArtifact, COMPILE);

    return dependencies;
  }

  /**
   * @param artifact the artifact to extract dependencies from.
   * @param scope    Scope of the dependencies, e.g. "compile" or "test".
   * @return Instance of {@link DependencySet} retrieved from given artifact.
   */
  @NotNull
  public DependencySet extractFrom(@NotNull IdeBaseArtifact artifact, @NotNull DependencyScope scope) {
    DependencySet dependencies = new DependencySet();
    populate(dependencies, artifact, scope);
    return dependencies;
  }

  private static void populate(@NotNull DependencySet dependencies,
                               @NotNull IdeBaseArtifact artifact,
                               @NotNull DependencyScope scope) {
    IdeLevel2Dependencies artifactDependencies = artifact.getLevel2Dependencies();

    for (Library library : artifactDependencies.getJavaLibraries()) {
      dependencies.add(new LibraryDependency(library.getArtifact(), scope));
    }

    for (Library library : artifactDependencies.getAndroidLibraries()) {
      dependencies.add(createLibraryDependencyFromAndroidLibrary(library, scope));
    }

    for (Library library : artifactDependencies.getModuleDependencies()) {
      String gradleProjectPath = library.getProjectPath();
      if (gradleProjectPath != null && !gradleProjectPath.isEmpty()) {
        ModuleDependency dependency = new ModuleDependency(gradleProjectPath, scope);
        dependencies.add(dependency);
      }
    }
  }

  @NotNull
  private static LibraryDependency createLibraryDependencyFromAndroidLibrary(@NotNull Library library,
                                                                             @NotNull DependencyScope scope) {
    LibraryDependency dependency =
      new LibraryDependency(library.getArtifact(), getDependencyName(library, "-"), scope);
    dependency.addPath(BINARY, library.getJarFile());
    dependency.addPath(BINARY, library.getResFolder());

    for (String localJar : library.getLocalJars()) {
      dependency.addPath(BINARY, localJar);
    }
    return dependency;
  }

  /**
   * Get user friendly name of a level2 library dependency.
   *
   * @param library   Level 2 library instance.
   * @param separator String to connect artifact id and version, for example, "-" or ":".
   * @return User friendly name of the dependency.
   * For example, artifactId[:/-]version for external library dependency, and moduleName::variant for module dependency.
   */
  @NotNull
  public static String getDependencyName(@NotNull Library library, @NotNull String separator) {
    String artifactAddress = library.getArtifactAddress();
    GradleCoordinate coordinates = GradleCoordinate.parseCoordinateString(artifactAddress);
    // Artifact address for external libraries are in the format of groupId:artifactId:version@packing, thus can be converted to GradleCoordinate.
    // But artifact address for module dependency is in the format of :moduleName::variant, trim the leading : for module dependency.
    if (coordinates != null) {
      return coordinates.getArtifactId() + separator + coordinates.getVersion();
    }
    else {
      return StringUtil.trimLeading(artifactAddress, ':');
    }
  }
}
