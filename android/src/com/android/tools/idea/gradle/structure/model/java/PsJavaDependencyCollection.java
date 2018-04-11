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
package com.android.tools.idea.gradle.structure.model.java;

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.model.java.JarLibraryDependency;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModelCollection;
import com.android.tools.idea.gradle.structure.model.PsParsedDependencies;
import com.google.common.collect.Maps;
import org.gradle.tooling.model.GradleModuleVersion;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class PsJavaDependencyCollection implements PsModelCollection<PsJavaDependency> {
  @NotNull private final PsJavaModule myParent;

  @NotNull private final Map<String, PsLibraryJavaDependency> myLibraryDependenciesBySpec = Maps.newHashMap();

  PsJavaDependencyCollection(@NotNull PsJavaModule parent) {
    myParent = parent;
    addDependencies();
  }

  private void addDependencies() {
    PsParsedDependencies parsedDependencies = myParent.getParsedDependencies();

    JavaModuleModel gradleModel = myParent.getGradleModel();
    for (JarLibraryDependency libraryDependency : gradleModel.getJarLibraryDependencies()) {
      GradleModuleVersion moduleVersion = libraryDependency.getModuleVersion();
      if (moduleVersion != null) {
        PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(moduleVersion);
        List<ArtifactDependencyModel>
          parsed = parsedDependencies.findLibraryDependencies(moduleVersion.getGroup(), moduleVersion.getName());
        if (!parsed.isEmpty()) {
          PsLibraryJavaDependency dependency =
            new PsLibraryJavaDependency(myParent, spec, libraryDependency, parsed.stream().findFirst().orElse(null));
          myLibraryDependenciesBySpec.put(spec.toString(), dependency);
        }
      }
    }
  }

  @Override
  public void forEach(@NotNull Consumer<PsJavaDependency> consumer) {
    forEachDependency(myLibraryDependenciesBySpec, consumer);
  }

  private static void forEachDependency(@NotNull Map<String, ? extends PsJavaDependency> dependenciesBySpec,
                                        @NotNull Consumer<PsJavaDependency> consumer) {
    dependenciesBySpec.values().forEach(consumer);
  }

  void forEachDeclaredDependency(@NotNull Consumer<PsJavaDependency> consumer) {
    forEachDeclaredDependency(myLibraryDependenciesBySpec, consumer);
  }

  private static void forEachDeclaredDependency(@NotNull Map<String, ? extends PsJavaDependency> dependenciesBySpec,
                                                @NotNull Consumer<PsJavaDependency> consumer) {
    dependenciesBySpec.values().stream().filter(PsJavaDependency::isDeclared).forEach(consumer);
  }
}
