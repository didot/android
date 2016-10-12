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
package com.android.tools.idea.gradle.project.sync.setup.module.android;

import com.android.builder.model.NativeAndroidProject;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.project.sync.SyncAction;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.setup.module.common.ContentEntriesSetup.removeExistingContentEntries;
import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;

public class ContentRootModuleSetupStep extends AndroidModuleSetupStep {
  @Override
  protected void doSetUpModule(@NotNull Module module,
                               @NotNull IdeModifiableModelsProvider ideModelsProvider,
                               @NotNull AndroidGradleModel androidModel,
                               @Nullable SyncAction.ModuleModels gradleModels,
                               @Nullable ProgressIndicator indicator) {
    ModifiableRootModel moduleModel = ideModelsProvider.getModifiableRootModel(module);
    boolean hasNativeModel = hasNativeModel(module, gradleModels);
    AndroidContentEntriesSetup setup = new AndroidContentEntriesSetup(androidModel, moduleModel, hasNativeModel);
    List<ContentEntry> contentEntries = findContentEntries(moduleModel, androidModel, hasNativeModel);
    setup.execute(contentEntries);
  }

  @NotNull
  private static List<ContentEntry> findContentEntries(@NotNull ModifiableRootModel moduleModel,
                                                       @NotNull AndroidGradleModel androidModel,
                                                       boolean hasNativeModel) {
    if (!hasNativeModel) {
      removeExistingContentEntries(moduleModel);
    }

    List<ContentEntry> contentEntries = new ArrayList<>();
    ContentEntry contentEntry = moduleModel.addContentEntry(androidModel.getRootDir());
    contentEntries.add(contentEntry);

    File buildFolderPath = androidModel.getAndroidProject().getBuildFolder();
    if (!isAncestor(androidModel.getRootDirPath(), buildFolderPath, false)) {
      contentEntries.add(moduleModel.addContentEntry(pathToIdeaUrl(buildFolderPath)));
    }
    return contentEntries;
  }

  private static boolean hasNativeModel(@NotNull Module module, @Nullable SyncAction.ModuleModels gradleModels) {
    return (gradleModels != null ? gradleModels.findModel(NativeAndroidProject.class) : NativeAndroidGradleModel.get(module)) == null;
  }

  @Override
  @NotNull
  public String getDescription() {
    return "Source folder(s) setup";
  }

  @Override
  public boolean invokeOnBuildVariantChange() {
    return true;
  }
}
