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
package com.android.tools.idea.gradle.parser;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

/**
 * GradleBuildFile uses PSI to parse build.gradle files and provides high-level methods to read and mutate the file. For many things in
 * the file it uses a simple key/value interface to set and retrieve values. Since a user can potentially edit a build.gradle file by
 * hand and make changes that we are unable to parse, there is also a
 * {@link #canParseValue(BuildFileKey)} method that will query if the value can
 * be edited by this class or not.
 *
 * Note that if you do any mutations on the PSI structure you must be inside a write action. See
 * {@link com.intellij.util.ActionRunner#runInsideWriteAction}.
 */
public class GradleBuildFile extends GradleGroovyFile {
  public static final String GRADLE_PLUGIN_CLASSPATH = "com.android.tools.build:gradle:";

  public GradleBuildFile(@NotNull VirtualFile buildFile, @NotNull Project project) {
    super(buildFile, project);
  }

  /**
   * @return true if the build file has a value for this key that we know how to safely parse and modify; false if it has user modifications
   * and should be left alone.
   */
  public boolean canParseValue(@NotNull BuildFileKey key) {
    checkInitialized();
    return canParseValue(myGroovyFile, key);
  }

  /**
   * @return true if the build file has a value for this key that we know how to safely parse and modify; false if it has user modifications
   * and should be left alone.
   */
  public boolean canParseValue(@NotNull GrStatementOwner root, @NotNull BuildFileKey key) {
    checkInitialized();
    GrMethodCall method = getMethodCallByPath(root, key.getPath());
    if (method == null) {
      return false;
    }
    if (key.isArgumentIsClosure()) {
      return key.canParseValue(getMethodClosureArgument(method));
    } else {
      return key.canParseValue(getArguments(method));
    }
  }

  /**
   * Returns the value in the file for the given key, or null if not present.
   */
  public @Nullable Object getValue(@NotNull BuildFileKey key) {
    checkInitialized();
    return getValue(myGroovyFile, key);
  }

  /**
   * Returns the value in the file for the given key, or null if not present.
   */
  public @Nullable Object getValue(@NotNull GrStatementOwner root, @NotNull BuildFileKey key) {
    checkInitialized();
    GrMethodCall method = getMethodCallByPath(root, key.getPath());
    if (method == null) {
      return null;
    }
    if (key.isArgumentIsClosure()) {
      return key.getValue(getMethodClosureArgument(method));
    } else {
      return key.getValue(getArguments(method));
    }
  }

  /**
   * Given a path to a method, returns the first argument of that method that is a closure, or null.
   */
  public @Nullable GrStatementOwner getClosure(String path) {
    checkInitialized();
    GrMethodCall method = getMethodCallByPath(myGroovyFile, path);
    if (method == null) {
      return null;
    }
    return getMethodClosureArgument(method);
  }

  /**
   * Modifies the value in the file. Must be run inside a write action.
   */
  public void setValue(@NotNull BuildFileKey key, @NotNull Object value) {
    checkInitialized();
    commitDocumentChanges();
    setValue(myGroovyFile, key, value);
  }

  /**
   * Modifies the value in the file. Must be run inside a write action.
   */
  public void setValue(@NotNull GrStatementOwner root, @NotNull BuildFileKey key, @NotNull Object value) {
    checkInitialized();
    commitDocumentChanges();
    GrMethodCall method = getMethodCallByPath(root, key.getPath());
    if (method != null) {
      if (key.isArgumentIsClosure()) {
        key.setValue(myProject, getMethodClosureArgument(method), value);
      } else {
        key.setValue(myProject, getArguments(method), value);
      }
    }
  }
}
