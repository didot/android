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
package com.android.tools.idea.gradle.project.sync.ng;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedModuleModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.ModelNotFoundInCacheException;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.GradleModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetup;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

class CachedProjectModelsSetup extends ModuleSetup<CachedProjectModels> {
  @NotNull private final GradleModuleSetup myGradleModuleSetup;
  @NotNull private final AndroidModuleSetup myAndroidModuleSetup;
  @NotNull private final NdkModuleSetup myNdkModuleSetup;
  @NotNull private final JavaModuleSetup myJavaModuleSetup;
  @NotNull private final ModuleFinder.Factory myModuleFinderFactory;
  @NotNull private final CompositeBuildDataSetup myCompositeBuildDataSetup;
  @NotNull private final ExtraGradleSyncModelsManager myExtraModelsManager;

  CachedProjectModelsSetup(@NotNull Project project,
                           @NotNull IdeModifiableModelsProvider modelsProvider,
                           @NotNull ExtraGradleSyncModelsManager extraGradleSyncModelsManager,
                           @NotNull GradleModuleSetup gradleModuleSetup,
                           @NotNull AndroidModuleSetup androidModuleSetup,
                           @NotNull NdkModuleSetup ndkModuleSetup,
                           @NotNull JavaModuleSetup javaModuleSetup,
                           @NotNull ModuleSetupContext.Factory moduleSetupFactory,
                           @NotNull ModuleFinder.Factory moduleFinderFactory,
                           @NotNull CompositeBuildDataSetup compositeBuildDataSetup) {
    super(project, modelsProvider, moduleSetupFactory);
    myExtraModelsManager = extraGradleSyncModelsManager;
    myGradleModuleSetup = gradleModuleSetup;
    myAndroidModuleSetup = androidModuleSetup;
    myNdkModuleSetup = ndkModuleSetup;
    myJavaModuleSetup = javaModuleSetup;
    myModuleFinderFactory = moduleFinderFactory;
    myCompositeBuildDataSetup = compositeBuildDataSetup;
  }

  @Override
  public void setUpModules(@NotNull CachedProjectModels projectModels, @NotNull ProgressIndicator indicator)
    throws ModelNotFoundInCacheException {
    Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode()) {
      // Tests always run in EDT
      assert !application.isDispatchThread();
    }

    notifyModuleConfigurationStarted(indicator);

    myCompositeBuildDataSetup.setupCompositeBuildData(projectModels, myProject);
    List<GradleFacet> gradleFacets = new ArrayList<>();

    ModuleFinder moduleFinder = myModuleFinderFactory.create(myProject);

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (gradleFacet != null) {
        String gradlePath = gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
        if (isNotEmpty(gradlePath)) {
          moduleFinder.addModule(module, gradlePath);
          gradleFacets.add(gradleFacet);
        }
      }
    }

    SetupContextByModuleModel setupContextByModuleModel = new SetupContextByModuleModel();

    for (GradleFacet gradleFacet : gradleFacets) {
      String moduleName = gradleFacet.getModule().getName();
      CachedModuleModels moduleModelsCache = projectModels.findCacheForModule(moduleName);
      if (moduleModelsCache != null) {
        getModuleModelFromCache(gradleFacet, moduleModelsCache, moduleFinder, setupContextByModuleModel);
      }
    }
    setupModuleModels(setupContextByModuleModel, myGradleModuleSetup, myNdkModuleSetup, myAndroidModuleSetup, myJavaModuleSetup,
                      myExtraModelsManager);
  }

  private void getModuleModelFromCache(@NotNull GradleFacet gradleFacet,
                                       @NotNull CachedModuleModels cache,
                                       @NotNull ModuleFinder moduleFinder,
                                       @NotNull SetupContextByModuleModel setupContexts) throws ModelNotFoundInCacheException {
    Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode()) {
      // Tests always run in EDT
      assert !application.isDispatchThread();
    }

    Module module = gradleFacet.getModule();
    GradleModuleModel gradleModel = cache.findModel(GradleModuleModel.class);
    if (gradleModel == null) {
      throw new ModelNotFoundInCacheException(GradleModuleModel.class);
    }
    ModuleSetupContext context = myModuleSetupFactory.create(module, myModelsProvider, moduleFinder, cache);
    setupContexts.gradleSetupContexts.put(gradleModel, context);

    AndroidModuleModel androidModel = cache.findModel(AndroidModuleModel.class);
    if (androidModel != null) {
      setupContexts.androidSetupContexts.put(androidModel, context);
      NdkModuleModel ndkModel = cache.findModel(NdkModuleModel.class);
      if (ndkModel != null) {
        setupContexts.ndkSetupContexts.put(ndkModel, context);
      }
      return;
    }

    JavaModuleModel javaModel = cache.findModel(JavaModuleModel.class);
    if (javaModel != null) {
      setupContexts.javaSetupContexts.put(javaModel, context);
    }
  }
}
