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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * This registry is used to keep track of the libraries that need to be removed between sync operations.
 */
public class SyncLibraryRegistry implements Disposable {
  @VisibleForTesting
  static final Key<SyncLibraryRegistry> KEY = Key.create("com.android.tools.gradle.sync.ProjectLibraryRegistry");

  @Nullable private Project myProject;

  @NotNull private final Map<String, Library> myProjectLibrariesByName = new HashMap<>();

  @NotNull
  public static SyncLibraryRegistry getInstance(@NotNull Project project) {
    SyncLibraryRegistry registry = project.getUserData(KEY);
    if (registry == null || registry.isDisposed()) {
      registry = new SyncLibraryRegistry(project);
      project.putUserData(KEY, registry);
    }
    return registry;
  }

  SyncLibraryRegistry(@NotNull Project project) {
    myProject = project;
    Disposer.register(project, this);
    registerExistingLibraries();
  }

  private void registerExistingLibraries() {
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(myProject);
    for (Library library : libraryTable.getLibraries()) {
      String name = library.getName();
      if (name != null) {
        myProjectLibrariesByName.put(name, library);
      }
    }
  }

  /**
   * Marks the given library as "used" by the project.
   *
   * @param library the library that is being used by the project.
   * @return {@code true} if the library was found in the registry; {@code false otherwise}.
   */
  public boolean markAsUsed(@NotNull Library library) {
    checkNotDisposed();
    String name = library.getName();
    if (name != null) {
      Library removed = myProjectLibrariesByName.remove(name);
      if (removed != null) {
        return true;
      }
      // This library is supposed to be used, but somehow we didn't find it.
      getLog().info("Failed to mark library '" + name + "' as \"used\". It is supposed to exist, but was not found");
    }
    return false;
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(SyncLibraryRegistry.class);
  }

  @NotNull
  public Collection<Library> getLibrariesToRemove() {
    checkNotDisposed();
    return myProjectLibrariesByName.values();
  }

  private void checkNotDisposed() {
    if (isDisposed()) {
      throw new IllegalStateException("Already disposed");
    }
  }

  @VisibleForTesting
  boolean isDisposed() {
    return Disposer.isDisposed(this);
  }

  @Override
  public void dispose() {
    assert myProject != null;
    myProject.putUserData(KEY, null);
    myProject = null;
    myProjectLibrariesByName.clear();
  }

  @VisibleForTesting
  @NotNull
  Map<String, Library> getProjectLibrariesByName() {
    return myProjectLibrariesByName;
  }
}
