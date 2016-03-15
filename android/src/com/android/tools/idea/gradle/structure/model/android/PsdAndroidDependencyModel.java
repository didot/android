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

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModel;
import com.android.tools.idea.gradle.structure.model.PsdChildModel;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Set;

public abstract class PsdAndroidDependencyModel extends PsdChildModel implements PsdAndroidModel {
  @NotNull private final Set<PsdDependencyContainer> myContainers = Sets.newHashSet();

  @Nullable private DependencyModel myParsedModel;

  PsdAndroidDependencyModel(@NotNull PsdAndroidModuleModel parent,
                            @Nullable PsdAndroidArtifactModel container,
                            @Nullable DependencyModel parsedModel) {
    super(parent);
    myParsedModel = parsedModel;
    if (container != null) {
      addContainer(container);
    }
  }

  @Override
  @NotNull
  public AndroidGradleModel getAndroidGradleModel() {
    return getParent().getAndroidGradleModel();
  }

  @Override
  @NotNull
  public PsdAndroidModuleModel getParent() {
    return (PsdAndroidModuleModel)super.getParent();
  }

  void addContainer(@NotNull PsdAndroidArtifactModel artifactModel) {
    myContainers.add(new PsdDependencyContainer(artifactModel));
  }

  @TestOnly
  @NotNull
  public Set<String> getVariants() {
    Set<String> variants = Sets.newHashSet();
    for (PsdDependencyContainer container : myContainers) {
      variants.add(container.getVariant());
    }
    return variants;
  }

  @NotNull
  public Set<PsdDependencyContainer> getContainers() {
    return myContainers;
  }

  @Nullable
  public String getConfigurationName() {
    return myParsedModel != null ? myParsedModel.configurationName() : null;
  }

  @Override
  public boolean isEditable() {
    return myParsedModel != null;
  }

  @Nullable
  public DependencyModel getParsedModel() {
    return myParsedModel;
  }

  protected void setParsedModel(@Nullable DependencyModel parsedModel) {
    myParsedModel = parsedModel;
  }

  @NotNull
  public abstract String getValueAsText();

  public boolean isIn(@NotNull String artifactName, @Nullable String variantName) {
    for (PsdDependencyContainer container : myContainers) {
      if (artifactName.equals(container.getArtifact())) {
        if (variantName == null) {
          return true;
        }
        if (variantName.equals(container.getVariant())) {
          return true;
        }
      }
    }

    return false;
  }
}
