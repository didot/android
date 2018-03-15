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

import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsModuleDependency;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;
import static com.android.tools.idea.gradle.util.GradleUtil.getModuleIcon;
import static icons.StudioIcons.Shell.Filetree.ANDROID_MODULE;

public class PsModuleAndroidDependency extends PsAndroidDependency implements PsModuleDependency {
  @NotNull private final String myGradlePath;
  @NotNull private final String myName;

  @Nullable private final String myConfigurationName;
  @Nullable private final Module myResolvedModel;

  PsModuleAndroidDependency(@NotNull PsAndroidModule parent,
                            @NotNull String gradlePath,
                            @NotNull Collection<PsAndroidArtifact> artifacts,
                            @Nullable String configurationName,
                            @Nullable Module resolvedModel,
                            @NotNull Collection<ModuleDependencyModel> parsedModels) {
    super(parent, artifacts, parsedModels);
    myGradlePath = gradlePath;
    myConfigurationName = configurationName;
    myResolvedModel = resolvedModel;
    String name;
    if (resolvedModel != null) {
      name = resolvedModel.getName();
    }
    else {
      name = parsedModels.stream().findFirst().map(v -> v.name()).orElse(null);
    }
    assert name != null;
    myName = name;
  }

  @Override
  @NotNull
  public String getGradlePath() {
    return myGradlePath;
  }

  @Override
  @Nullable
  public String getConfigurationName() {
    return myConfigurationName;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @Nullable
  public Icon getIcon() {
    if (myResolvedModel != null) {
      return getModuleIcon(myResolvedModel);
    }
    return ANDROID_MODULE;
  }

  @Override
  public void addParsedModel(@NotNull DependencyModel parsedModel) {
    assert parsedModel instanceof ModuleDependencyModel;
    super.addParsedModel(parsedModel);
  }

  @Override
  @NotNull
  public String toText(@NotNull TextType type) {
    return myName;
  }

  @Override
  @Nullable
  public Module getResolvedModel() {
    return myResolvedModel;
  }

  @Nullable
  public PsAndroidArtifact findReferredArtifact() {
    PsModule referred = getParent().getParent().findModuleByGradlePath(getGradlePath());
    String moduleVariantName = getConfigurationName();
    if (moduleVariantName != null && referred instanceof PsAndroidModule) {
      PsAndroidModule androidModule = (PsAndroidModule)referred;
      PsVariant moduleVariant = androidModule.findVariant(moduleVariantName);
      if (moduleVariant != null) {
        return moduleVariant.findArtifact(ARTIFACT_MAIN);
      }
    }
    return null;
  }
}
