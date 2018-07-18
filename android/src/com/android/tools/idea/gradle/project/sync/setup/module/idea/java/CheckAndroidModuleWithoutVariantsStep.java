/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.idea.java;

import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetupStep;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.PROJECT_STRUCTURE_ISSUES;
import static com.android.tools.idea.gradle.project.sync.setup.Facets.removeAllFacets;
import static com.android.tools.idea.project.messages.MessageType.ERROR;

public class CheckAndroidModuleWithoutVariantsStep extends JavaModuleSetupStep {
  @Override
  protected void doSetUpModule(@NotNull ModuleSetupContext context, @NotNull JavaModuleModel gradleModel) {
    Module module = context.getModule();

    if (gradleModel.isAndroidModuleWithoutVariants()) {
      // Only report an error if we have no SyncIssues, this error message is not very helpful and should only be used if we have no
      // idea what went wrong.
      if (gradleModel.getSyncIssues().isEmpty()) {
        // See https://code.google.com/p/android/issues/detail?id=170722
        GradleSyncMessages messages = GradleSyncMessages.getInstance(module.getProject());
        String[] text =
          {String.format("The module '%1$s' is an Android project without build variants, and cannot be built.", module.getName()),
            "Please fix the module's configuration in the build.gradle file and sync the project again.",};
        messages.report(new SyncMessage(PROJECT_STRUCTURE_ISSUES, ERROR, text));
      }
      cleanUpAndroidModuleWithoutVariants(module, context.getIdeModelsProvider());
      // No need to setup source folders, dependencies, etc. Since the Android project does not have variants, and because this can
      // happen due to a project configuration error and there is a lot of module configuration missing, there is no point on even trying.
      myShouldTerminateSetup = true;
    }
  }

  private static void cleanUpAndroidModuleWithoutVariants(@NotNull Module module, @NotNull IdeModifiableModelsProvider ideModelsProvider) {
    // Remove Android facet, otherwise the IDE will try to build the module, and fail. The facet may have been added in a previous
    // successful commit.
    removeAllFacets(ideModelsProvider.getModifiableFacetModel(module), AndroidFacet.ID);

    // Clear all source and exclude folders.
    ModifiableRootModel rootModel = ideModelsProvider.getModifiableRootModel(module);
    for (ContentEntry contentEntry : rootModel.getContentEntries()) {
      contentEntry.clearSourceFolders();
      contentEntry.clearExcludeFolders();
    }
  }
}


