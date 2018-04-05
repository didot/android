/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.sync.Modules;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleProjects.findModuleRootFolderPath;
import static com.google.common.base.Strings.nullToEmpty;

public class ModuleFinder {
  @NotNull public static final ModuleFinder EMPTY = new ModuleFinder();

  // With composite builds, modules in different projects can have duplicated gradle path, thus gradle path cannot be used as unique identifier.
  // However moduleId (i.e. project folder + gradle path) should be unique.
  // Keep myModulesByGradlePath for backwards compatibility - AGP prior to 3.1 doesn't provide project folder for module dependencies,
  // we have to rely on gradle path, which does not work properly with composite build.
  @NotNull private final Map<String, Module> myModulesByGradlePath = new HashMap<>();
  @NotNull private final Map<String, Module> myModulesByModuleId = new HashMap<>();
  // Map from module folder to project folder for included modules, this will be used to construct projectId for included modules.
  @NotNull private final Map<String, File> myIncludedProjectFolderByModuleFolder = new HashMap<>();

  private ModuleFinder() {
  }

  public ModuleFinder(@NotNull Project project) {
    populateIncludedProjectFolderByModuleFolder(project);
  }

  private void populateIncludedProjectFolderByModuleFolder(@NotNull Project project) {
    String projectPath = project.getBasePath();
    if (projectPath == null) {
      return;
    }
    GradleProjectSettings projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(projectPath);
    if (projectSettings == null) {
      return;
    }
    GradleProjectSettings.CompositeBuild compositeBuild = projectSettings.getCompositeBuild();
    if (compositeBuild == null) {
      return;
    }

    for (BuildParticipant participant : compositeBuild.getCompositeParticipants()) {
      for (String modulePath : participant.getProjects()) {
        String path = nullToEmpty(participant.getRootPath());
        myIncludedProjectFolderByModuleFolder.put(modulePath, new File(path));
      }
    }
  }

  public void addModule(@NotNull Module module, @NotNull String gradlePath) {
    myModulesByGradlePath.put(gradlePath, module);
    File folderPath = getFolderPathOf(module);
    if (folderPath != null) {
      myModulesByModuleId.put(Modules.createUniqueModuleId(folderPath, gradlePath), module);
    }
  }

  @Nullable
  private File getFolderPathOf(@NotNull Module module) {
    File moduleFolder = findModuleRootFolderPath(module);
    if (moduleFolder != null) {
      String modulePath = moduleFolder.getPath();
      if (myIncludedProjectFolderByModuleFolder.containsKey(modulePath)) {
        return myIncludedProjectFolderByModuleFolder.get(modulePath);
      }
    }
    return getBaseDirPath(module.getProject());
  }

  /**
   * This method finds module based on gradle path. This method should only be called for pre-3.1 AGP.
   * Use {@link #findModuleByModuleId(String)} for pre-3.1 AGP.
   *
   * @param gradlePath gradle path of a module.
   * @return the module with given gradle path.
   */
  @Nullable
  public Module findModuleByGradlePath(@NotNull String gradlePath) {
    return myModulesByGradlePath.get(gradlePath);
  }

  /**
   * This method finds module based on module id, as returned by {@link Modules#createUniqueModuleId(File, String)}.
   * Use this method for AGP 3.1 and higher.
   * Use {@link #findModuleByGradlePath(String)} for pre-3.1 AGP.
   *
   * @param moduleId the module id.
   * @return the module with given module id.
   */
  @Nullable
  public Module findModuleByModuleId(@NotNull String moduleId) {
    return myModulesByModuleId.get(moduleId);
  }

  /**
   * @return {@code true} if the given module comes from composite build.
   */
  public boolean isCompositeBuild(@NotNull Module module) {
    File moduleFolder = findModuleRootFolderPath(module);
    if (moduleFolder != null) {
      return myIncludedProjectFolderByModuleFolder.containsKey(moduleFolder.getPath());
    }
    return false;
  }

  @Override
  public String toString() {
    return "ModuleFinder{" +
           "myModulesByGradlePath=" + myModulesByGradlePath +
           ", myModulesByModuleId=" + myModulesByModuleId +
           ", myIncludedProjectFolderByModuleFolder=" + myIncludedProjectFolderByModuleFolder +
           '}';
  }

  public static class Factory {
    @NotNull
    public ModuleFinder create(@NotNull Project project) {
      return new ModuleFinder(project);
    }
  }
}
