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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Creates a deep copy of a {@link DependencyGraphs}.
 */
public final class IdeDependencyGraphs implements DependencyGraphs, Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final List<GraphItem> myCompileDependencies;
  @NotNull private final List<GraphItem> myPackageDependencies;
  @NotNull private final List<String> myProvidedLibraries;
  @NotNull private final List<String> mySkippedLibraries;

  public IdeDependencyGraphs(@Nullable DependencyGraphs graphs) {
    if (graphs != null) {
      myCompileDependencies = copy(graphs.getCompileDependencies());
      myPackageDependencies = copy(graphs.getPackageDependencies());
      myProvidedLibraries = new ArrayList<>(graphs.getProvidedLibraries());
      mySkippedLibraries = new ArrayList<>(graphs.getSkippedLibraries());
    }
    else {
      myCompileDependencies = Collections.emptyList();
      myPackageDependencies = Collections.emptyList();
      myProvidedLibraries = Collections.emptyList();
      mySkippedLibraries = Collections.emptyList();
    }
  }

  @NotNull
  private static List<GraphItem> copy(@NotNull List<GraphItem> dependencies) {
    return dependencies.stream().map(IdeGraphItem::new).collect(Collectors.toList());
  }

  @Override
  @NotNull
  public List<GraphItem> getCompileDependencies() {
    return myCompileDependencies;
  }

  @Override
  @NotNull
  public List<GraphItem> getPackageDependencies() {
    return myPackageDependencies;
  }

  @Override
  @NotNull
  public List<String> getProvidedLibraries() {
    return myProvidedLibraries;
  }

  @Override
  @NotNull
  public List<String> getSkippedLibraries() {
    return mySkippedLibraries;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeDependencyGraphs)) {
      return false;
    }
    IdeDependencyGraphs graphs = (IdeDependencyGraphs)o;
    return Objects.equals(myCompileDependencies, graphs.myCompileDependencies) &&
           Objects.equals(myPackageDependencies, graphs.myPackageDependencies) &&
           Objects.equals(myProvidedLibraries, graphs.myProvidedLibraries) &&
           Objects.equals(mySkippedLibraries, graphs.mySkippedLibraries);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myCompileDependencies, myPackageDependencies, myProvidedLibraries, mySkippedLibraries);
  }

  @Override
  public String toString() {
    return "IdeDependencyGraphs{" +
           "myCompileDependencies=" + myCompileDependencies +
           ", myPackageDependencies=" + myPackageDependencies +
           ", myProvidedLibraries=" + myProvidedLibraries +
           ", mySkippedLibraries=" + mySkippedLibraries +
           '}';
  }
}
