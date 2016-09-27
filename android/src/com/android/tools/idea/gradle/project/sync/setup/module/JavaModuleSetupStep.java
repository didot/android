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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.android.tools.idea.gradle.JavaProject;
import com.android.tools.idea.gradle.project.sync.SyncAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

public abstract class JavaModuleSetupStep {
  private static final ExtensionPointName<JavaModuleSetupStep>
    EXTENSION_POINT_NAME = ExtensionPointName.create("com.android.gradle.sync.javaModuleConfigurationStep");

  @NotNull
  public static JavaModuleSetupStep[] getExtensions() {
    return EXTENSION_POINT_NAME.getExtensions();
  }

  public abstract void setUpModule(@NotNull Module module,
                                   @NotNull JavaProject javaProject,
                                   @NotNull IdeModifiableModelsProvider ideModelsProvider,
                                   @NotNull SyncAction.ModuleModels gradleModels,
                                   @NotNull ProgressIndicator indicator);

  @NotNull
  public abstract String getDescription();
}
