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

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;
import static com.android.tools.idea.gradle.project.sync.setup.Facets.removeAllFacets;

import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.util.containers.ContainerUtil;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class NdkModuleModelDataService extends ModuleModelDataService<NdkModuleModel> {
  @NotNull private final ModuleSetupContext.Factory myModuleSetupContextFactory;
  @NotNull private final NdkModuleSetup myModuleSetup;

  @SuppressWarnings("unused") // Instantiated by IDEA
  public NdkModuleModelDataService() {
    this(new ModuleSetupContext.Factory(), new NdkModuleSetup());
  }

  @VisibleForTesting
  NdkModuleModelDataService(@NotNull ModuleSetupContext.Factory moduleSetupContextFactory,
                            @NotNull NdkModuleSetup moduleSetup) {
    myModuleSetupContextFactory = moduleSetupContextFactory;
    myModuleSetup = moduleSetup;
  }

  @Override
  @NotNull
  public Key<NdkModuleModel> getTargetDataKey() {
    return NDK_MODEL;
  }

  @Override
  protected void importData(@NotNull Collection<? extends DataNode<NdkModuleModel>> toImport,
                            @NotNull Project project,
                            @NotNull IdeModifiableModelsProvider modelsProvider,
                            @NotNull Map<String, NdkModuleModel> modelsByModuleName) {
    for (Module module : modelsProvider.getModules()) {
      NdkModuleModel ndkModuleModel = modelsByModuleName.get(module.getName());
      if (ndkModuleModel != null) {
        ModuleSetupContext context = myModuleSetupContextFactory.create(module, modelsProvider);
        myModuleSetup.setUpModule(context, ndkModuleModel);
      }
    }
  }

  @Override
  protected List<@NotNull Module> eligibleOrphanCandidates(@NotNull Project project) {
    return ContainerUtil.map(ProjectFacetManager.getInstance(project).getFacets(NdkFacet.getFacetTypeId()), NdkFacet::getModule);
  }

  @Override
  public void removeData(Computable<? extends Collection<? extends Module>> toRemoveComputable,
                         @NotNull Collection<? extends DataNode<NdkModuleModel>> toIgnore,
                         @NotNull ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    for (Module module : toRemoveComputable.get()) {
      ModifiableFacetModel facetModel = modelsProvider.getModifiableFacetModel(module);
      removeAllFacets(facetModel, NdkFacet.getFacetTypeId());
    }
  }
}
