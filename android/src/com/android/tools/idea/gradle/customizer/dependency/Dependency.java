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
package com.android.tools.idea.gradle.customizer.dependency;

import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.customizer.dependency.LibraryDependency.PathType.BINARY;
import static com.android.tools.idea.gradle.customizer.dependency.LibraryDependency.PathType.SOURCE;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.DependencyScope.TEST;
import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * An IDEA module's dependency on an artifact (e.g. a jar file or another IDEA module.)
 */
public abstract class Dependency {
  /**
   * The Android Gradle plug-in only supports "compile" and "test" scopes. This list is sorted by width of the scope, being "compile" a
   * wider scope than "test."
   */
  static final List<DependencyScope> SUPPORTED_SCOPES = Lists.newArrayList(COMPILE, TEST);

  // Without this '@SuppressWarnings' IDEA shows a warning because the field 'myScope' is not set directly in the constructor, and therefore
  // IDEA thinks it can be null, contradicting '@NotNull'. In reality, the field is set in the constructor by calling 'setScope'. To avoid
  // this warning we can either use '@SuppressWarnings' or duplicate, in the constructor, what 'setScope' is doing.
  @SuppressWarnings("NullableProblems")
  @NotNull
  private DependencyScope myScope;

  /**
   * Creates a new {@link Dependency} with {@link DependencyScope#COMPILE} scope.
   */
  Dependency() {
    this(COMPILE);
  }

  /**
   * Creates a new {@link Dependency}.
   *
   * @param scope the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  Dependency(@NotNull DependencyScope scope) throws IllegalArgumentException {
    setScope(scope);
  }

  @NotNull
  public final DependencyScope getScope() {
    return myScope;
  }

  /**
   * Sets the scope of this dependency.
   *
   * @param scope the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  void setScope(@NotNull DependencyScope scope) throws IllegalArgumentException {
    if (!SUPPORTED_SCOPES.contains(scope)) {
      String msg = String.format("'%1$s' is not a supported scope. Supported scopes are %2$s.", scope, SUPPORTED_SCOPES);
      throw new IllegalArgumentException(msg);
    }
    myScope = scope;
  }

  @NotNull
  public static DependencySet extractFrom(@NotNull AndroidGradleModel androidModel) {
    DependencySet dependencies = new DependencySet();
    GradleVersion modelVersion = androidModel.getModelVersion();

    for (BaseArtifact testArtifact : androidModel.getTestArtifactsInSelectedVariant()) {
      populate(dependencies, testArtifact, TEST, modelVersion);
    }

    AndroidArtifact mainArtifact = androidModel.getMainArtifact();
    populate(dependencies, mainArtifact, COMPILE, modelVersion);

    return dependencies;
  }

  @NotNull
  public static DependencySet extractFrom(@NotNull BaseArtifact artifact,
                                          @NotNull DependencyScope scope,
                                          @Nullable GradleVersion modelVersion) {
    DependencySet dependencies = new DependencySet();
    populate(dependencies, artifact, scope, modelVersion);
    return dependencies;
  }

  private static void populate(@NotNull DependencySet dependencies,
                               @NotNull BaseArtifact artifact,
                               @NotNull DependencyScope scope,
                               @Nullable GradleVersion modelVersion) {
    Dependencies artifactDependencies = getDependencies(artifact, modelVersion);
    boolean supportsDependencyGraph = modelVersion != null && androidModelSupportsDependencyGraph(modelVersion);
    boolean supportsInstantApps = modelVersion != null && androidModelSupportsInstantApps(modelVersion);

    addJavaLibraries(dependencies, artifactDependencies.getJavaLibraries(), scope, supportsDependencyGraph);

    Set<File> unique = Sets.newHashSet();
    for (AndroidLibrary library : artifactDependencies.getLibraries()) {
      addAndroidLibrary(library, dependencies, scope, unique, supportsDependencyGraph);
    }
    if (supportsInstantApps) {
      for (AndroidAtom androidAtom : artifactDependencies.getAtoms()) {
        addAndroidAtom(androidAtom, dependencies, scope, unique, supportsDependencyGraph);
      }
    }

    if (!supportsDependencyGraph) {
      // If the Android model is pre 2.2.0, invoke Dependencies.getProjects. In 2.2.0+, this method returns an empty collection.
      //noinspection deprecation
      for (String gradleProjectPath : artifactDependencies.getProjects()) {
        if (gradleProjectPath != null && !gradleProjectPath.isEmpty()) {
          ModuleDependency dependency = new ModuleDependency(gradleProjectPath, scope);
          dependencies.add(dependency);
        }
      }
    }
  }

  @NotNull
  private static String getBundleName(@NotNull AndroidBundle bundle) {
    MavenCoordinates coordinates = bundle.getResolvedCoordinates();
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
                                        @NotNull Set<File> unique,
                                        boolean supportsDependencyGraph) {
    if (isAlreadySeen(library, unique)) {
      return;
    }

    String gradleProjectPath = library.getProject();
    if (isNotEmpty(gradleProjectPath)) {
      dependencies.add(createModuleDependencyFromAndroidLibrary(library, scope, gradleProjectPath));
    }
    else {
      dependencies.add(createLibraryDependencyFromAndroidLibrary(library, scope));
    }

    addBundleTransitiveDependencies(library, dependencies, scope, unique, supportsDependencyGraph);
  }


  /**
   * Add an Android atom, along with any recursive atom dependencies
   */
  private static void addAndroidAtom(@NotNull AndroidAtom atom,
                                     @NotNull DependencySet dependencies,
                                     @NotNull DependencyScope scope,
                                     @NotNull Set<File> unique,
                                     boolean supportsDependencyGraph) {
    if (isAlreadySeen(atom, unique)) {
      return;
    }

    String gradleProjectPath = atom.getProject();
    if (isNotEmpty(gradleProjectPath)) {
      dependencies.add(createModuleDependencyFromAndroidBundle(atom, scope, gradleProjectPath));
    }
    else {
      dependencies.add(createLibraryDependencyFromAndroidBundle(atom, scope));
    }

    addAtomTransitiveDependencies(atom, dependencies, scope, unique, supportsDependencyGraph);
  }

  @NotNull
  private static ModuleDependency createModuleDependencyFromAndroidLibrary(@NotNull AndroidLibrary library,
                                                                           @NotNull DependencyScope scope,
                                                                           String gradleProjectPath) {
    // Add the aar as dependency in case there is a module dependency that cannot be satisfied (e.g. the module is outside of the
    // project.) If we cannot set the module dependency, we set a library dependency instead.
    LibraryDependency backup = createLibraryDependencyFromAndroidLibrary(library, scope);
    return createModuleDependency(scope, backup, gradleProjectPath);
  }

  @NotNull
  private static ModuleDependency createModuleDependencyFromAndroidBundle(@NotNull AndroidBundle bundle,
                                                                          @NotNull DependencyScope scope,
                                                                          String gradleProjectPath) {
    LibraryDependency backup = createLibraryDependencyFromAndroidBundle(bundle, scope);
    return createModuleDependency(scope, backup, gradleProjectPath);
  }

  @NotNull
  private static ModuleDependency createModuleDependency(@NotNull DependencyScope scope,
                                                         @NotNull LibraryDependency backup,
                                                         String gradleProjectPath) {
    ModuleDependency dependency = new ModuleDependency(gradleProjectPath, scope);
    dependency.setBackupDependency(backup);
    return dependency;
  }

  private static void addBundleTransitiveDependencies(@NotNull AndroidBundle bundle,
                                                      @NotNull DependencySet dependencies,
                                                      @NotNull DependencyScope scope,
                                                      @NotNull Set<File> unique,
                                                      boolean supportsDependencyGraph) {
    for (AndroidLibrary dependentLibrary : bundle.getLibraryDependencies()) {
      addAndroidLibrary(dependentLibrary, dependencies, scope, unique, supportsDependencyGraph);
    }
    if (supportsDependencyGraph) {
      addJavaLibraries(dependencies, bundle.getJavaDependencies(), scope, true);
    }
  }

  private static void addAtomTransitiveDependencies(@NotNull AndroidAtom atom,
                                                    @NotNull DependencySet dependencies,
                                                    @NotNull DependencyScope scope,
                                                    @NotNull Set<File> unique,
                                                    boolean supportsDependencyGraph) {
    for (AndroidAtom dependentAtom : atom.getAtomDependencies()) {
      addAndroidAtom(dependentAtom, dependencies, scope, unique, supportsDependencyGraph);
    }
    addBundleTransitiveDependencies(atom, dependencies, scope, unique, supportsDependencyGraph);
  }

  @NotNull
  private static LibraryDependency createLibraryDependencyFromAndroidBundle(@NotNull AndroidBundle bundle, @NotNull DependencyScope scope) {
    LibraryDependency dependency = new LibraryDependency(getBundleName(bundle), scope);
    dependency.addPath(BINARY, bundle.getJarFile());
    dependency.addPath(BINARY, bundle.getResFolder());

    VirtualFile sourceJar = findSourceJarForLibrary(bundle.getBundle());
    if (sourceJar != null) {
      File sourceJarFile = virtualToIoFile(sourceJar);
      dependency.addPath(SOURCE, sourceJarFile);
    }

    return dependency;
  }

  @NotNull
  private static LibraryDependency createLibraryDependencyFromAndroidLibrary(@NotNull AndroidLibrary library,
                                                                             @NotNull DependencyScope scope) {
    LibraryDependency dependency = createLibraryDependencyFromAndroidBundle(library, scope);

    for (File localJar : library.getLocalJars()) {
      dependency.addPath(BINARY, localJar);
    }

    return dependency;
  }

  private static void addJavaLibraries(@NotNull DependencySet dependencies,
                                       @NotNull Collection<? extends JavaLibrary> libraries,
                                       @NotNull DependencyScope scope,
                                       boolean supportsDependencyGraph) {
    for (JavaLibrary library : libraries) {
      if (supportsDependencyGraph) {
        ModuleDependency mainDependency = null;

        String gradleProjectPath = library.getProject();
        MavenCoordinates coordinates = library.getResolvedCoordinates();
        String classifier = coordinates.getClassifier();
        // If there is a classifier, set up the dependency as a 'library' dependency.
        // See https://code.google.com/p/android/issues/detail?id=215490
        if (isNotEmpty(gradleProjectPath) && isEmpty(classifier)) {
          // This is a module.
          mainDependency = new ModuleDependency(gradleProjectPath, scope);
          dependencies.add(mainDependency);
          // Add the dependencies of the module as well.
          // See https://code.google.com/p/android/issues/detail?id=210172
          addJavaLibraries(dependencies, library.getDependencies(), scope, true);
        }
        if (mainDependency == null) {
          // This is a library, not a module.
          addJavaLibrary(library, dependencies, scope, true);
        }
        else {
          // Add the jar as dependency in case there is a module dependency that cannot be satisfied (e.g. the module is outside of the
          // project.) If we cannot set the module dependency, we set a library dependency instead.
          LibraryDependency backup = createLibraryDependencyFromJavaLibrary(library, scope);
          mainDependency.setBackupDependency(backup);
        }
      }
      else {
        addJavaLibrary(library, dependencies, scope, false);
      }
    }
  }

  private static void addJavaLibrary(@NotNull JavaLibrary library,
                                     @NotNull DependencySet dependencies,
                                     @NotNull DependencyScope scope,
                                     boolean supportsDependencyGraph) {
    dependencies.add(createLibraryDependencyFromJavaLibrary(library, scope));
    addJavaLibraries(dependencies, library.getDependencies(), scope, supportsDependencyGraph);
  }

  @NotNull
  private static LibraryDependency createLibraryDependencyFromJavaLibrary(@NotNull JavaLibrary library, @NotNull DependencyScope scope) {
    return new LibraryDependency(library.getJarFile(), scope);
  }
}
