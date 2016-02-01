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
package com.android.tools.idea.gradle.structure.model;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class PsdModuleEditor extends PsdChildEditor {
  @NotNull private final String myGradlePath;

  // Module can be null in the case of new modules created in the PSD.
  @Nullable private final Module myModule;

  private boolean myInitParsedModel;
  private GradleBuildModel myParsedModel;
  private String myModuleName;

  protected PsdModuleEditor(@NotNull PsdProjectEditor parent,
                            @NotNull Module module,
                            @NotNull String gradlePath) {
    super(parent);
    myModule = module;
    myGradlePath = gradlePath;
    myModuleName = module.getName();
  }

  @Override
  @NotNull
  public PsdProjectEditor getParent() {
    return (PsdProjectEditor)super.getParent();
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public String getGradlePath() {
    return myGradlePath;
  }

  @Override
  public boolean isEditable() {
    return myParsedModel != null;
  }

  @Nullable
  public GradleBuildModel getParsedModel() {
    if (!myInitParsedModel) {
      myInitParsedModel = true;
      if (myModule != null) {
        myParsedModel = GradleBuildModel.get(myModule);
      }
    }
    return myParsedModel;
  }

  @Nullable
  public Module getModule() {
    return myModule;
  }

  public Icon getModuleIcon() {
    return AllIcons.Nodes.Module;
  }
}
