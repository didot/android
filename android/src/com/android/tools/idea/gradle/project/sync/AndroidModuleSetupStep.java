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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

public abstract class AndroidModuleSetupStep {
  public static final ExtensionPointName<AndroidModuleSetupStep>
    EXTENSION_POINT_NAME = ExtensionPointName.create("com.android.gradle.androidModuleSetupStep");

  @NotNull
  public static AndroidModuleSetupStep[] getExtensions() {
    return EXTENSION_POINT_NAME.getExtensions();
  }

  public abstract void setUpModule(@NotNull Module module,
                                   @NotNull AndroidGradleModel androidModel,
                                   @NotNull IdeModifiableModelsProvider ideModelsProvider,
                                   @NotNull SyncAction.ModuleModels gradleModels,
                                   @NotNull ProgressIndicator indicator);

  @NotNull
  public abstract String getDescription();
}
