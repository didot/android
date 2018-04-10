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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.structure.model.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

public class PsJavaModule extends PsModule {
  @NotNull private final JavaModuleModel myGradleModel;

  private PsJavaDependencyCollection myDependencyCollection;

  public PsJavaModule(@NotNull PsProject parent,
                      @NotNull Module resolvedModel,
                      @NotNull String gradlePath,
                      @NotNull JavaModuleModel gradleModel,
                      @NotNull GradleBuildModel parsedModel) {
    super(parent, resolvedModel, gradlePath, parsedModel);
    myGradleModel = gradleModel;
  }

  @Override
  @NotNull
  public List<String> getConfigurations() {
    return getGradleModel().getConfigurations();
  }

  @Override
  public boolean canDependOn(@NotNull PsModule module) {
    // Java libraries can depend on any type of modules, including Android apps (when a Java library is actually a 'test'
    // module for the Android app.)
    return true;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.PpJdk;
  }

  @NotNull
  public JavaModuleModel getGradleModel() {
    return myGradleModel;
  }

  @Override
  @NotNull
  public String getGradlePath() {
    String gradlePath = super.getGradlePath();
    assert gradlePath != null;
    return gradlePath;
  }

  @Override
  @NotNull
  public Module getResolvedModel() {
    Module model = super.getResolvedModel();
    assert model != null;
    return model;
  }

  public void forEachDeclaredDependency(@NotNull Consumer<PsJavaDependency> consumer) {
    getOrCreateDependencyCollection().forEachDeclaredDependency(consumer);
  }

  public void forEachDependency(@NotNull Consumer<PsJavaDependency> consumer) {
    getOrCreateDependencyCollection().forEach(consumer);
  }

  @NotNull
  private PsJavaDependencyCollection getOrCreateDependencyCollection() {
    return myDependencyCollection == null ? myDependencyCollection = new PsJavaDependencyCollection(this) : myDependencyCollection;
  }

  @Override
  public void addLibraryDependency(@NotNull String library, @NotNull List<String> scopesNames) {
    // Update/reset the "parsed" model.
    addLibraryDependencyToParsedModel(scopesNames, library);

    // Reset dependencies.
    myDependencyCollection = null;

    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(library);
    assert spec != null;
    fireLibraryDependencyAddedEvent(spec);
    setModified(true);
  }

  @Override
  public void addModuleDependency(@NotNull String modulePath, @NotNull List<String> scopesNames) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeDependency(@NotNull PsDependency dependency) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setLibraryDependencyVersion(@NotNull PsArtifactDependencySpec spec,
                                          @NotNull String configurationName,
                                          @NotNull String newVersion) {
    throw new UnsupportedOperationException();
  }
}
