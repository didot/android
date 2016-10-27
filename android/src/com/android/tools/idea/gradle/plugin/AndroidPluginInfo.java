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
package com.android.tools.idea.gradle.plugin;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.util.BuildFileProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.CLASSPATH;
import static com.android.tools.idea.gradle.dsl.model.values.GradleValue.getValues;
import static com.android.tools.idea.gradle.plugin.AndroidPluginGeneration.COMPONENT;
import static com.intellij.openapi.module.ModuleUtilCore.findModuleForFile;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class AndroidPluginInfo {
  @NotNull private final Module myModule;
  @NotNull private final AndroidPluginGeneration myPluginGeneration;
  @Nullable private final GradleVersion myPluginVersion;

  /**
   * Attempts to obtain information about the Android plugin from the project's "application" module in the given project.
   *
   * @param project the given project.
   * @return the Android plugin information, if the "application" module was found; {@code null} otherwise.
   */
  @Nullable
  public static AndroidPluginInfo find(@NotNull Project project) {
    return find(project, false);
  }

  /**
   * Attempts to obtain information about the Android plugin from the project's "application" module in the given project.
   * <p/>
   * This method ignores any {@code AndroidProject}s and reads the plugin's information from build.gradle files.
   *
   * @param project the given project.
   * @return the Android plugin information, if the "application" module was found; {@code null} otherwise.
   */
  @Nullable
  public static AndroidPluginInfo searchInBuildFilesOnly(@NotNull Project project) {
    return find(project, true);
  }

  @Nullable
  private static AndroidPluginInfo find(@NotNull Project project, boolean searchInBuildFilesOnly) {
    Module appModule = null;
    AndroidGradleModel appGradleModel = null;

    if (!searchInBuildFilesOnly) {
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        AndroidGradleModel gradleModel = AndroidGradleModel.get(module);
        // TODO: what if a project has an app and an iapk?
        if (gradleModel != null && gradleModel.getProjectType() == PROJECT_TYPE_APP) {
          // This is the 'app' module in the project.
          appModule = module;
          appGradleModel = gradleModel;
          break;
        }
      }
    }

    GradleVersion pluginVersion = appGradleModel != null ? appGradleModel.getModelVersion() : null;
    AndroidPluginGeneration pluginGeneration = null;
    if (appModule != null) {
      pluginGeneration = AndroidPluginGeneration.find(appModule);
      if (pluginGeneration == COMPONENT) {
        // "Experimental" plugin does not retrieve correct version yet.
        pluginVersion = null;
      }
    }

    boolean appModuleFound = appModule != null;
    boolean pluginVersionFound = pluginVersion != null;

    if (!appModuleFound || !pluginVersionFound) {
      // Try to find 'app' module or plugin version by reading build.gradle files.
      BuildFileSearchResult result = searchInBuildFiles(project, !appModuleFound, !pluginVersionFound);
      if (result.appVirtualFile != null) {
        appModule = findModuleForFile(result.appVirtualFile, project);
      }
      if (isNotEmpty(result.pluginVersion)) {
        pluginVersion = GradleVersion.tryParse(result.pluginVersion);
      }
      if (pluginGeneration == null) {
        pluginGeneration = result.pluginGeneration;
      }
    }

    if (appModule != null && pluginGeneration != null) {
      return new AndroidPluginInfo(appModule, pluginGeneration, pluginVersion);
    }

    return null;
  }

  @NotNull
  private static BuildFileSearchResult searchInBuildFiles(@NotNull Project project,
                                                          boolean searchForAppModule,
                                                          boolean searchForPluginVersion) {
    BuildFileSearchResult result = new BuildFileSearchResult();

    BuildFileProcessor.getInstance().processRecursively(project, buildModel -> {
      boolean keepSearchingForAppModule = searchForAppModule && result.appVirtualFile == null;
      if (keepSearchingForAppModule) {
        List<String> pluginIds = getValues(buildModel.appliedPlugins());
        for (AndroidPluginGeneration generation : AndroidPluginGeneration.values()) {
          if (generation.isApplicationPluginIdIn(pluginIds)) {
            result.appVirtualFile = buildModel.getVirtualFile();
            result.pluginGeneration = generation;
            keepSearchingForAppModule = false;
            break;
          }
        }
      }

      boolean keepSearchingForPluginVersion = searchForPluginVersion && result.pluginVersion == null;
      if (keepSearchingForPluginVersion) {
        DependenciesModel dependencies = buildModel.buildscript().dependencies();
        for (ArtifactDependencyModel dependency : dependencies.artifacts(CLASSPATH)) {
          for (AndroidPluginGeneration generation : AndroidPluginGeneration.values()) {
            if (generation.isAndroidPlugin(dependency.name().value(), dependency.group().value())) {
              String version = dependency.version().value();
              if (isNotEmpty(version)) {
                result.pluginVersion = version;
              }
              keepSearchingForPluginVersion = false;
              break;
            }
          }
          if (!keepSearchingForPluginVersion) {
            break;
          }
        }
      }
      return keepSearchingForAppModule || keepSearchingForPluginVersion;
    });

    return result;
  }

  private AndroidPluginInfo(@NotNull Module module,
                            @NotNull AndroidPluginGeneration pluginGeneration,
                            @Nullable GradleVersion pluginVersion) {
    myModule = module;
    myPluginGeneration = pluginGeneration;
    myPluginVersion = pluginVersion;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public AndroidPluginGeneration getPluginGeneration() {
    return myPluginGeneration;
  }

  @Nullable
  public GradleVersion getPluginVersion() {
    return myPluginVersion;
  }

  public boolean isExperimental() {
    return getPluginGeneration() == COMPONENT;
  }

  private static class BuildFileSearchResult {
    @Nullable VirtualFile appVirtualFile;
    @Nullable AndroidPluginGeneration pluginGeneration;
    @Nullable String pluginVersion;
  }
}
