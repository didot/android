/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.model;

import com.android.annotations.Nullable;
import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Creates a deep copy of {@link AndroidBundle}.
 *
 * @see IdeAndroidProject
 */
public class IdeAndroidBundle implements AndroidBundle, Serializable {
  @NotNull private final MavenCoordinates myResolvedCoordinates;
  @NotNull private final File myBundle;
  @NotNull private final File myFolder;
  @NotNull private final List<IdeAndroidLibrary> myLibraryDependencies;
  @NotNull private final Collection<IdeJavaLibrary> myJavaDependencies;
  @NotNull private final File myManifest;
  @NotNull private final File myJarFile;
  @NotNull private final File myResFolder;
  @NotNull private final File myAssetsFolder;
  @Nullable private final String myProject;
  @Nullable private final String myName;
  @Nullable private final MavenCoordinates myRequestedCoordinates;
  @Nullable private final String myProjectVariant;
  private final boolean mySkipped;
  private final boolean myProvided;

  public IdeAndroidBundle(@NotNull AndroidBundle bundle, @NotNull Map<Library, Library> seen, @NotNull GradleVersion gradleVersion) {
    myResolvedCoordinates = new IdeMavenCoordinates(bundle.getResolvedCoordinates(), gradleVersion);

    myBundle = bundle.getBundle();
    myFolder = bundle.getFolder();

    myLibraryDependencies = new ArrayList<>();
    for (AndroidLibrary dependency : bundle.getLibraryDependencies()) {
      if (!seen.containsKey(dependency)) {
        seen.put(dependency, new IdeAndroidLibrary(dependency, seen, gradleVersion));
      }
      myLibraryDependencies.add((IdeAndroidLibrary)seen.get(dependency));
    }

    myJavaDependencies = new ArrayList<>();
    for (JavaLibrary dependency : bundle.getJavaDependencies()) {
      if (!seen.containsKey(dependency)) {
        seen.put(dependency, new IdeJavaLibrary(dependency, seen, gradleVersion));
      }
      myJavaDependencies.add((IdeJavaLibrary)seen.get(dependency));
    }

    myManifest = bundle.getManifest();
    myJarFile = bundle.getJarFile();
    myResFolder = bundle.getResFolder();
    myAssetsFolder = bundle.getAssetsFolder();
    myProject = bundle.getProject();
    myName = bundle.getName();

    MavenCoordinates liRequestedCoordinate = bundle.getRequestedCoordinates();
    myRequestedCoordinates = liRequestedCoordinate == null ? null :new IdeMavenCoordinates(liRequestedCoordinate, gradleVersion);

    myProjectVariant = bundle.getProjectVariant();
    mySkipped = bundle.isSkipped();

    boolean provided = false;
    try {
      provided = bundle.isProvided();
    }
    catch (NullPointerException e) {
      provided = false;
    }
    finally {
      myProvided = provided;
    }
  }

  @Override
  @NotNull
  public MavenCoordinates getResolvedCoordinates() {
    return myResolvedCoordinates;
  }

  @Override
  @NotNull
  public File getBundle() {
    return myBundle;
  }

  @Override
  @NotNull
  public File getFolder() {
    return myFolder;
  }

  @Override
  @NotNull
  public List<? extends AndroidLibrary> getLibraryDependencies() {
    return myLibraryDependencies;
  }

  @Override
  @NotNull
  public Collection<? extends JavaLibrary> getJavaDependencies() {
    return myJavaDependencies;
  }

  @Override
  @NotNull
  public File getManifest() {
    return myManifest;
  }

  @Override
  @NotNull
  public File getJarFile() {
    return myJarFile;
  }

  @Override
  @NotNull
  public File getResFolder() {
    return myResFolder;
  }

  @Override
  @NotNull
  public File getAssetsFolder() {
    return myAssetsFolder;
  }

  @Override
  @Nullable
  public String getProject() {
    return myProject;
  }

  @Override
  @Nullable
  public String getName() {
    return myName;
  }

  @Override
  @Nullable
  public MavenCoordinates getRequestedCoordinates() {
    return myRequestedCoordinates;
  }

  @Override
  @Nullable
  public String getProjectVariant() {
    return myProjectVariant;
  }

  @Override
  public boolean isSkipped() {
    return mySkipped;
  }

  @Override
  public boolean isProvided() {
    return myProvided;
  }
}
