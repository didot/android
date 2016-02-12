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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.structure.model.PsdModuleModel;
import com.android.tools.idea.gradle.structure.model.PsdParsedDependencyModels;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.structure.model.android.Artifacts.createSpec;
import static com.android.tools.idea.gradle.structure.model.pom.MavenPoms.findDependenciesInPomFile;

class PsdAndroidDependencyModels {
  @NotNull private final PsdAndroidModuleModel myParent;

  // Key:
  // - For artifact dependencies: artifact spec, "com.google.guava:guava:19.0"
  // - For module dependencies: module's Gradle path
  @NotNull private final Map<String, PsdAndroidDependencyModel> myDependencyModels = Maps.newHashMap();

  PsdAndroidDependencyModels(@NotNull PsdAndroidModuleModel parent) {
    myParent = parent;
    for (PsdVariantModel variantModel : parent.getVariantModels()) {
      addDependencies(variantModel);
    }
  }

  private void addDependencies(@NotNull PsdVariantModel variantModel) {
    Variant variant = variantModel.getGradleModel();
    if (variant != null) {
      AndroidArtifact mainArtifact = variant.getMainArtifact();
      collectDependencies(mainArtifact, variantModel);

      for (AndroidArtifact artifact : variant.getExtraAndroidArtifacts()) {
        collectDependencies(artifact, variantModel);
      }

      for (JavaArtifact javaArtifact : variant.getExtraJavaArtifacts()) {
        collectDependencies(javaArtifact, variantModel);
      }
    }
  }

  private void collectDependencies(@NotNull BaseArtifact artifact, @NotNull PsdVariantModel variantModel) {
    Dependencies dependencies = artifact.getDependencies();

    for (AndroidLibrary androidLibrary : dependencies.getLibraries()) {
      String gradlePath = androidLibrary.getProject();
      if (gradlePath != null) {
        addModule(gradlePath, variantModel);
      }
      else {
        // This is an AAR
        addLibrary(androidLibrary, variantModel);
      }
    }

    for (JavaLibrary javaLibrary : dependencies.getJavaLibraries()) {
      addLibrary(javaLibrary, variantModel);
    }
  }

  private void addModule(@NotNull String gradlePath, @NotNull PsdVariantModel variantModel) {
    PsdParsedDependencyModels parsedDependencies = myParent.getParsedDependencyModels();

    ModuleDependencyModel matchingParsedDependency = parsedDependencies.findMatchingModuleDependency(gradlePath);

    Module module = null;
    PsdModuleModel moduleModel = myParent.getParent().findModelByGradlePath(gradlePath);
    if (moduleModel != null) {
      module = moduleModel.getModule();
    }
    PsdAndroidDependencyModel dependencyModel = findDependency(gradlePath);
    if (dependencyModel == null) {
      dependencyModel = new PsdModuleDependencyModel(myParent, gradlePath, module, matchingParsedDependency);
      myDependencyModels.put(gradlePath, dependencyModel);
    }
    dependencyModel.addContainer(variantModel);
  }

  @Nullable
  private PsdAndroidDependencyModel addLibrary(@NotNull Library library, @NotNull PsdVariantModel variantModel) {
    PsdParsedDependencyModels parsedDependencies = myParent.getParsedDependencyModels();

    MavenCoordinates coordinates = library.getResolvedCoordinates();
    if (coordinates != null) {
      ArtifactDependencyModel matchingParsedDependency = parsedDependencies.findMatchingArtifactDependency(coordinates);
      if (matchingParsedDependency != null) {
        String parsedVersionValue = matchingParsedDependency.version().value();
        if (parsedVersionValue != null) {
          // The dependency has a version in the build.gradle file.
          // "tryParse" just in case the build.file has an invalid version.
          GradleVersion parsedVersion = GradleVersion.tryParse(parsedVersionValue);

          GradleVersion versionFromGradle = GradleVersion.parse(coordinates.getVersion());
          if (parsedVersion != null && compare(parsedVersion, versionFromGradle) == 0) {
            // Match.
            ArtifactDependencySpec spec = ArtifactDependencySpec.create(matchingParsedDependency);
            return addLibrary(library, spec, variantModel, matchingParsedDependency);
          }
          else {
            // Version mismatch. This can happen when the project specifies an artifact version but Gradle uses a different version
            // from a transitive dependency.
            // Example:
            // 1. Module 'app' depends on module 'lib'
            // 2. Module 'app' depends on Guava 18.0
            // 3. Module 'lib' depends on Guava 19.0
            // Gradle will force module 'app' to use Guava 19.0

            // Create the dependency model that will be displayed in the "Dependencies" table.
            ArtifactDependencySpec spec = createSpec(coordinates);
            addLibrary(library, spec, variantModel, matchingParsedDependency);

            // Create a dependency model for the transitive dependency, so it can be displayed in the "Variants" tool window.
            return addLibrary(library, spec, variantModel, null);
          }
        }
      }
      else {
        // This dependency was not declared, it could be a transitive one.
        ArtifactDependencySpec spec = createSpec(coordinates);
        return addLibrary(library, spec, variantModel, null);
      }
    }
    return null;
  }

  @Nullable
  private PsdAndroidDependencyModel addLibrary(@NotNull Library library,
                                               @NotNull ArtifactDependencySpec resolvedSpec,
                                               @NotNull PsdVariantModel variantModel,
                                               @Nullable ArtifactDependencyModel parsedDependencyModel) {
    if (library instanceof AndroidLibrary) {
      AndroidLibrary androidLibrary = (AndroidLibrary)library;
      return addAndroidLibrary(androidLibrary, resolvedSpec, variantModel, parsedDependencyModel);
    }
    else if (library instanceof JavaLibrary) {
      JavaLibrary javaLibrary = (JavaLibrary)library;
      return addJavaLibrary(javaLibrary, resolvedSpec, variantModel, parsedDependencyModel);
    }
    return null;
  }

  @NotNull
  private PsdAndroidDependencyModel addAndroidLibrary(@NotNull AndroidLibrary androidLibrary,
                                                      @NotNull ArtifactDependencySpec resolvedSpec,
                                                      @NotNull PsdVariantModel variantModel,
                                                      @Nullable ArtifactDependencyModel parsedDependencyModel) {
    PsdAndroidDependencyModel dependencyModel = getOrCreateDependency(resolvedSpec, androidLibrary, parsedDependencyModel);

    for (AndroidLibrary library : androidLibrary.getLibraryDependencies()) {
      PsdAndroidDependencyModel transitive = addLibrary(library, variantModel);
      if (transitive != null && dependencyModel instanceof PsdLibraryDependencyModel) {
        PsdLibraryDependencyModel libraryDependencyModel = (PsdLibraryDependencyModel)dependencyModel;
        libraryDependencyModel.addTransitiveDependency(transitive.getValueAsText());
      }
    }

    dependencyModel.addContainer(variantModel);
    return dependencyModel;
  }

  @NotNull
  private PsdAndroidDependencyModel addJavaLibrary(@NotNull JavaLibrary javaLibrary,
                                                   @NotNull ArtifactDependencySpec resolvedSpec,
                                                   @NotNull PsdVariantModel variantModel,
                                                   @Nullable ArtifactDependencyModel parsedDependencyModel) {
    PsdAndroidDependencyModel dependencyModel = getOrCreateDependency(resolvedSpec, javaLibrary, parsedDependencyModel);

    for (JavaLibrary library : javaLibrary.getDependencies()) {
      PsdAndroidDependencyModel transitive = addLibrary(library, variantModel);
      if (transitive != null && dependencyModel instanceof PsdLibraryDependencyModel) {
        PsdLibraryDependencyModel libraryDependencyModel = (PsdLibraryDependencyModel)dependencyModel;
        libraryDependencyModel.addTransitiveDependency(transitive.getValueAsText());
      }
    }

    dependencyModel.addContainer(variantModel);
    return dependencyModel;
  }

  @VisibleForTesting
  static int compare(@NotNull GradleVersion parsedVersion, @NotNull GradleVersion versionFromGradle) {
    int result = versionFromGradle.compareTo(parsedVersion);
    if (result == 0) {
      return result;
    }
    else if (result < 0) {
      // The "parsed" version might have a '+' sign.
      if (parsedVersion.getMajorSegment().acceptsGreaterValue()) {
        return 0;
      }
      else if (parsedVersion.getMinorSegment() != null && parsedVersion.getMinorSegment().acceptsGreaterValue()) {
        return parsedVersion.getMajor() - versionFromGradle.getMajor();
      }
      else if (parsedVersion.getMicroSegment() != null && parsedVersion.getMicroSegment().acceptsGreaterValue()) {
        result = parsedVersion.getMajor() - versionFromGradle.getMajor();
        if (result != 0) {
          return result;
        }
        return parsedVersion.getMinor() - versionFromGradle.getMinor();
      }
    }
    return result;
  }

  @NotNull
  private PsdAndroidDependencyModel getOrCreateDependency(@NotNull ArtifactDependencySpec resolvedSpec,
                                                          @NotNull Library gradleModel,
                                                          @Nullable ArtifactDependencyModel parsedModel) {
    String key = resolvedSpec.toString();
    PsdAndroidDependencyModel dependencyModel = findDependency(key);
    if (dependencyModel == null) {
      dependencyModel = new PsdLibraryDependencyModel(myParent, resolvedSpec, gradleModel, parsedModel);
      myDependencyModels.put(key, dependencyModel);

      File libraryPath = null;
      if (gradleModel instanceof AndroidLibrary) {
        libraryPath = ((AndroidLibrary)gradleModel).getBundle();
      }
      else if (gradleModel instanceof JavaLibrary) {
        libraryPath = ((JavaLibrary)gradleModel).getJarFile();
      }
      List<ArtifactDependencySpec> pomDependencies = Collections.emptyList();
      if (libraryPath != null) {
        pomDependencies = findDependenciesInPomFile(libraryPath);
      }
      ((PsdLibraryDependencyModel)dependencyModel).setPomDependencies(pomDependencies);
    }
    return dependencyModel;
  }

  @NotNull
  List<PsdAndroidDependencyModel> getDeclaredDependencies() {
    List<PsdAndroidDependencyModel> models = Lists.newArrayList();
    for (PsdAndroidDependencyModel model : myDependencyModels.values()) {
      if (model.isEditable()) {
        models.add(model);
      }
    }
    return models;
  }

  @NotNull
  public List<PsdAndroidDependencyModel> getDependencies() {
    return Lists.newArrayList(myDependencyModels.values());
  }

  @Nullable
  public PsdAndroidDependencyModel findDependency(@NotNull String dependency) {
    return myDependencyModels.get(dependency);
  }
  @Nullable
  public PsdAndroidDependencyModel findDependency(@NotNull ArtifactDependencySpec dependency) {
    String key = dependency.toString();
    return myDependencyModels.get(key);
  }
}
