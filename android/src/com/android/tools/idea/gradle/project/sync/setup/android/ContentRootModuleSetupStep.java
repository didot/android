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
package com.android.tools.idea.gradle.project.sync.setup.android;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.project.sync.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.SyncAction;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.project.sync.setup.android.AndroidContentEntries.findOrCreateContentEntries;

public class ContentRootModuleSetupStep extends AndroidModuleSetupStep {
  @Override
  public void setUpModule(@NotNull Module module,
                          @NotNull AndroidGradleModel androidModel,
                          @NotNull IdeModifiableModelsProvider ideModelsProvider,
                          @NotNull SyncAction.ModuleModels gradleModels,
                          @NotNull ProgressIndicator indicator) {
    ModifiableRootModel rootModel = ideModelsProvider.getModifiableRootModel(module);
    AndroidContentEntries contentEntries = findOrCreateContentEntries(rootModel, androidModel);
    contentEntries.setUpContentEntries(gradleModels);
  }

  @Override
  @NotNull
  public String getDescription() {
    return "Source folder(s) setup";
  }
}
