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
package com.android.tools.idea.gradle.project.sync.idea.data.service;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.android.AndroidModuleCleanupStep;
import com.android.tools.idea.gradle.project.sync.setup.post.MemorySettingsPostSyncChecker;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectStructureUsageTracker;
import com.android.tools.idea.gradle.project.sync.setup.post.TimeBasedReminder;
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.GradlePluginUpgrade;
import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.SystemProperties;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Service that sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
 */
public class AndroidModuleModelDataService extends ModuleModelDataService<AndroidModuleModel> {
  @NotNull private final ModuleSetupContext.Factory myModuleSetupContextFactory;
  @NotNull private final AndroidModuleSetup myModuleSetup;
  @NotNull private final AndroidModuleValidator.Factory myModuleValidatorFactory;
  @NotNull private final AndroidModuleCleanupStep myCleanupStep;

  // This constructor is called by the IDE. See this module's plugin.xml file, implementation of extension 'externalProjectDataService'.
  @SuppressWarnings("unused")
  public AndroidModuleModelDataService() {
    this(new ModuleSetupContext.Factory(), new AndroidModuleSetup(), new AndroidModuleValidator.Factory(), new AndroidModuleCleanupStep());
  }

  @VisibleForTesting
  AndroidModuleModelDataService(@NotNull ModuleSetupContext.Factory moduleSetupContextFactory,
                                @NotNull AndroidModuleSetup moduleSetup,
                                @NotNull AndroidModuleValidator.Factory moduleValidatorFactory,
                                @NotNull AndroidModuleCleanupStep cleanupStep) {
    myModuleSetupContextFactory = moduleSetupContextFactory;
    myModuleSetup = moduleSetup;
    myModuleValidatorFactory = moduleValidatorFactory;
    myCleanupStep = cleanupStep;
  }

  @Override
  @NotNull
  public Key<AndroidModuleModel> getTargetDataKey() {
    return ANDROID_MODEL;
  }

  @Override
  protected void importData(@NotNull Collection<DataNode<AndroidModuleModel>> toImport,
                            @NotNull Project project,
                            @NotNull IdeModifiableModelsProvider modelsProvider,
                            @NotNull Map<String, AndroidModuleModel> modelsByModuleName) {
    AndroidModuleValidator moduleValidator = myModuleValidatorFactory.create(project);
    for (Module module : modelsProvider.getModules()) {
      AndroidModuleModel androidModel = modelsByModuleName.get(module.getName());
      setUpModule(module, moduleValidator, modelsProvider, androidModel);
    }

    if (!modelsByModuleName.isEmpty()) {
      moduleValidator.fixAndReportFoundIssues();
    }
  }

  private void setUpModule(@NotNull Module module,
                           @NotNull AndroidModuleValidator moduleValidator,
                           @NotNull IdeModifiableModelsProvider modelsProvider,
                           @Nullable AndroidModuleModel androidModel) {
    if (androidModel != null) {
      ModuleSetupContext context = myModuleSetupContextFactory.create(module, modelsProvider);
      myModuleSetup.setUpModule(context, androidModel);
      moduleValidator.validate(module, androidModel);
    }
    else {
      onModelNotFound(module, modelsProvider);
    }
  }

  @Override
  protected void onModelNotFound(@NotNull Module module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    myCleanupStep.cleanUpModule(module, modelsProvider);
  }

  @TestOnly
  @NotNull
  public AndroidModuleSetup getModuleSetup() {
    return myModuleSetup;
  }

  @Override
  public void onSuccessImport(@NotNull Collection<DataNode<AndroidModuleModel>> imported,
                              @Nullable ProjectData projectData,
                              @NotNull Project project,
                              @NotNull IdeModelsProvider modelsProvider) {
    if (GradlePluginUpgrade.shouldRecommendPluginUpgrade(project)) {
      GradlePluginUpgrade.recommendPluginUpgrade(project);
    }

    MemorySettingsPostSyncChecker
        .checkSettings(project, new TimeBasedReminder(project, "memory.settings.postsync", TimeUnit.DAYS.toMillis(1)));

    new ProjectStructureUsageTracker(project).trackProjectStructure();
  }
}
