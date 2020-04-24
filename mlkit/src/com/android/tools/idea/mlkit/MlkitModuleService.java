/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import com.android.tools.idea.mlkit.lightpsi.LightModelClass;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.mlkit.MlkitNames;
import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Module level service for ML Kit plugin.
 */
public class MlkitModuleService {
  private final Module myModule;
  private final Map<MlModelMetadata, LightModelClass> myLightModelClassMap = new ConcurrentHashMap<>();
  private final LightModelClassListProvider myLightModelClassListProvider;

  public static MlkitModuleService getInstance(@NotNull Module module) {
    return Objects.requireNonNull(ModuleServiceManager.getService(module, MlkitModuleService.class));
  }

  public MlkitModuleService(@NotNull Module module) {
    myModule = module;
    myLightModelClassListProvider = new LightModelClassListProvider(module);
  }

  @Nullable
  private LightModelClass getOrCreateLightModelClass(@NotNull MlModelMetadata modelMetadata) {
    return myLightModelClassMap.computeIfAbsent(modelMetadata, key -> {
      String packageName = ProjectSystemUtil.getModuleSystem(myModule).getPackageName();
      if (packageName == null) {
        Logger.getInstance(MlkitModuleService.class).warn("Can not determine the package name for module: " + myModule.getName());
        return null;
      }

      String modelFileUrl = modelMetadata.myModelFileUrl;
      VirtualFile modelFile = VirtualFileManager.getInstance().findFileByUrl(modelFileUrl);
      if (modelFile == null) {
        Logger.getInstance(MlkitModuleService.class).warn("Failed to find the virtual file for: " + modelFileUrl);
        return null;
      }

      String className = MlkitUtils.computeModelClassName(myModule, modelFile);
      if (Strings.isNullOrEmpty(className)) {
        Logger.getInstance(MlkitModuleService.class).warn("Can not determine the class name for: " + modelFileUrl);
        return null;
      }

      LightModelClassConfig classConfig =
        new LightModelClassConfig(modelMetadata, packageName + MlkitNames.PACKAGE_SUFFIX, className);
      return new LightModelClass(myModule, modelFile, classConfig);
    });
  }

  /**
   * Returns light model classes auto-generated for ML model files in this module's ml folder.
   */
  @NotNull
  public List<LightModelClass> getLightModelClassList() {
    if (!MlkitUtils.isMlModelBindingBuildFeatureEnabled(myModule) || DumbService.isDumb(myModule.getProject())) {
      return Collections.emptyList();
    }

    return CachedValuesManager.getManager(myModule.getProject()).getCachedValue(myModule, myLightModelClassListProvider);
  }

  private static class LightModelClassListProvider implements CachedValueProvider<List<LightModelClass>> {
    private final Module myModule;

    private LightModelClassListProvider(@NotNull Module module) {
      myModule = module;
    }

    @Nullable
    @Override
    public Result<List<LightModelClass>> compute() {
      MlkitModuleService service = getInstance(myModule);
      Set<MlModelMetadata> latestModelMetadataSet = new HashSet<>();
      GlobalSearchScope searchScope = MlModelFilesSearchScope.inModule(myModule);
      FileBasedIndex index = FileBasedIndex.getInstance();
      index.processAllKeys(MlModelFileIndex.INDEX_ID, key -> {
        index.processValues(MlModelFileIndex.INDEX_ID, key, null, (file, value) -> {
          latestModelMetadataSet.add(value);
          return true;
        }, searchScope);

        return true;
      }, searchScope, null);

      // Invalidates cached light classes that no longer have model file associated.
      List<MlModelMetadata> outdatedModelMetadataList = ContainerUtil
        .filter(service.myLightModelClassMap.keySet(), modelMetadata -> !latestModelMetadataSet.contains(modelMetadata));
      for (MlModelMetadata outdatedModelMetadata : outdatedModelMetadataList) {
        service.myLightModelClassMap.remove(outdatedModelMetadata);
      }

      List<LightModelClass> lightModelClassList = new ArrayList<>();
      for (MlModelMetadata modelMetadata : latestModelMetadataSet) {
        LightModelClass lightModelClass = service.getOrCreateLightModelClass(modelMetadata);
        if (lightModelClass != null) {
          lightModelClassList.add(lightModelClass);
        }
      }
      return CachedValueProvider.Result.create(lightModelClassList, ProjectMlModelFileTracker.getInstance(myModule.getProject()));
    }
  }
}
