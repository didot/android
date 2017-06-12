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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;

import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.util.ArrayUtilRt.EMPTY_FILE_ARRAY;

/**
 * An IDEA module's dependency on a library (e.g. a jar file.)
 */
public class LibraryDependency extends Dependency {
  @NotNull private final Map<PathType, Collection<File>> myPathsByType = new EnumMap<>(PathType.class);
  @NotNull private final File myArtifactPath;

  private String myName;

  /**
   * Creates a new {@link LibraryDependency}.
   *
   * @param artifactPath the path, in the file system, of the binary file that represents the library to depend on.
   * @param scope        the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  @VisibleForTesting
  public LibraryDependency(@NotNull File artifactPath, @NotNull DependencyScope scope) {
    this(artifactPath, getNameWithoutExtension(artifactPath), scope);
    addPath(PathType.BINARY, artifactPath);
  }

  /**
   * Creates a new {@link LibraryDependency}.
   *
   * @param artifactPath the path, in the file system, of the binary file that represents the library to depend on.
   * @param name  the name of the library to depend on.
   * @param scope the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  LibraryDependency(@NotNull File artifactPath, @NotNull String name, @NotNull DependencyScope scope) {
    super(scope);
    myArtifactPath = artifactPath;
    setName(name);
  }

  @VisibleForTesting
  public void addPath(@NotNull PathType type, @NotNull File path) {
    Collection<File> paths = myPathsByType.get(type);
    if (paths == null) {
      paths = new HashSet<>();
      myPathsByType.put(type, paths);
    }
    paths.add(path);
  }

  void addPath(@NotNull PathType type, @NotNull String path) {
    addPath(type, new File(path));
  }

  @NotNull
  public File[] getPaths(@NotNull PathType type) {
    Collection<File> paths = myPathsByType.get(type);
    return paths == null || paths.isEmpty() ? EMPTY_FILE_ARRAY : paths.toArray(new File[paths.size()]);
  }

  @NotNull
  public File getArtifactPath() {
    return myArtifactPath;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  void setName(@NotNull String name) {
    myName = name;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" +
           "name='" + myName + '\'' +
           ", scope=" + getScope() +
           ", pathsByType=" + myPathsByType +
           "]";
  }

  public enum PathType {
    BINARY, DOCUMENTATION
  }
}
