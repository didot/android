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

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.messages.MessageBusConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Module level service for ML Kit plugin. */
public class MlkitModuleService {
  private final Module myModule;
  private final ModelFileModificationTracker myModelFileModificationTracker;

  public static MlkitModuleService getInstance(@NotNull Module module) {
    return Objects.requireNonNull(ModuleServiceManager.getService(module, MlkitModuleService.class));
  }

  public MlkitModuleService(Module module) {
    myModule = module;
    myModelFileModificationTracker = new ModelFileModificationTracker(module);
  }

  /** Returns light model classes auto-generated for ML model files in this module's assets folder. */
  @NotNull
  List<LightModelClass> getLightModelClassList() {
    Project project = myModule.getProject();
    return CachedValuesManager.getManager(project).getCachedValue(myModule, () -> {
      List<LightModelClass> lightModelClassList = new ArrayList<>();
      FileBasedIndex index = FileBasedIndex.getInstance();
      index.processAllKeys(MlModelFileIndex.INDEX_ID, key -> {
        index.processValues(MlModelFileIndex.INDEX_ID, key, null, (file, value) -> {
          LightModelClassConfig classConfig = MlModelClassGenerator.generateLightModelClass(myModule, value);
          lightModelClassList.add(new LightModelClass(myModule, classConfig));
          return true;
        }, GlobalSearchScope.moduleScope(myModule));

        return true;
      }, GlobalSearchScope.moduleScope(myModule), null);
      return CachedValueProvider.Result.create(lightModelClassList, myModelFileModificationTracker);
    });
  }

  private static class ModelFileModificationTracker implements ModificationTracker {
    private int myModificationCount;

    private ModelFileModificationTracker(Module module) {
      if (StudioFlags.MLKIT_LIGHT_CLASSES.get()) {
        MessageBusConnection connection = module.getMessageBus().connect(module);
        connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
          @Override
          public void after(@NotNull List<? extends VFileEvent> events) {
            for (VFileEvent event : events) {
              if (event.getFile() != null && MlkitUtils.isMlModelFileInAssetsFolder(event.getFile())) {
                PsiManager.getInstance(module.getProject()).dropResolveCaches();
                myModificationCount++;
                return;
              }
            }
          }
        });
      }
    }

    @Override
    public long getModificationCount() {
      return myModificationCount;
    }
  }
}
