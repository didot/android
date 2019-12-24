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

import com.google.common.collect.Lists;
import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.DependencyScope.TEST;

/**
 * An IDEA module's dependency on an artifact (e.g. a jar file or another IDEA module.)
 */
public abstract class Dependency {
  /**
   * The Android Gradle plug-in only supports "compile" and "test" scopes. This list is sorted by width of the scope, being "compile" a
   * wider scope than "test."
   */
  static final List<DependencyScope> SUPPORTED_SCOPES = Lists.newArrayList(COMPILE, TEST);

  @NotNull
  private final DependencyScope myScope;

  /**
   * Creates a new {@link Dependency}.
   *
   * @param scope the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  Dependency(@NotNull DependencyScope scope) throws IllegalArgumentException {
    if (!SUPPORTED_SCOPES.contains(scope)) {
      String msg = String.format("'%1$s' is not a supported scope. Supported scopes are %2$s.", scope, SUPPORTED_SCOPES);
      throw new IllegalArgumentException(msg);
    }
    myScope = scope;
  }

  @NotNull
  public final DependencyScope getScope() {
    return myScope;
  }
}
