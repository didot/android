/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.model.AndroidContentRoot;
import com.android.tools.idea.gradle.model.AndroidDependencies;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.intellij.externalSystem.JavaProjectData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Imports a Android-Gradle projects into IDEA. The set of projects to import may include regular Java projects as well.
 */
class ProjectResolver {
  private static final Logger LOG = Logger.getInstance(ProjectResolver.class);

  @NotNull final GradleExecutionHelper myHelper;

  ProjectResolver(@NotNull GradleExecutionHelper helper) {
    myHelper = helper;
  }

  /**
   * Imports multiple Android-Gradle projects. The set of projects to import may include regular Java projects as well.
   *
   * </p>Since the Android Gradle model does not support multiple projects, we query the Gradle Tooling API for a regular Java
   * multi-project. Then, for each of the modules in the imported project, we query for an (@link AndroidProject Android Gradle model.) If
   * we get one we create an IDE module from it, otherwise we just use the regular Java module. Unfortunately, this process requires
   * creation of multiple {@link ProjectConnection}s.
   *
   * @param id          id of the current 'resolve project info' task.
   * @param projectPath absolute path of the parent folder of the build.gradle file.
   * @param settings    settings to use for the project resolving; {@code null} as indication that no specific settings are required.
   * @param connection  Gradle Tooling API connection to the project to import.
   * @param listener    callback to be notified about the execution
   * @return the imported project, or {@link null} if the project to import is not an Android-Gradle project.
   */
  @Nullable
  DataNode<ProjectData> resolveProjectInfo(@NotNull ExternalSystemTaskId id,
                                           @NotNull String projectPath,
                                           @Nullable GradleExecutionSettings settings,
                                           @NotNull ProjectConnection connection,
                                           @NotNull ExternalSystemTaskNotificationListener listener) {
    ModelBuilder<IdeaProject> modelBuilder = myHelper.getModelBuilder(IdeaProject.class, id, settings, connection, listener);
    IdeaProject ideaProject = modelBuilder.get();
    if (ideaProject == null || ideaProject.getModules().isEmpty()) {
      return null;
    }

    String name = new File(projectPath).getName();
    DataNode<ProjectData> projectInfo = createProjectInfo(projectPath, name);

    AndroidProject first = null;

    for (IdeaModule module : ideaProject.getModules()) {
      IdeaGradleProject gradleProject = new IdeaGradleProject(module.getName(), module.getGradleProject().getPath());
      String relativePath = getRelativePath(gradleProject);
      File moduleDir;
      if (relativePath.isEmpty()) {
        moduleDir = new File(projectPath);
      }
      else {
        moduleDir = new File(projectPath, relativePath);
      }
      File gradleBuildFile = new File(moduleDir, SdkConstants.FN_BUILD_GRADLE);
      if (!gradleBuildFile.isFile()) {
        continue;
      }
      String moduleDirPath = moduleDir.getPath();
      AndroidProject androidProject = getAndroidProject(id, moduleDirPath, listener, settings);
      if (androidProject != null) {
        if (!GradleModelVersionCheck.isSupportedVersion(androidProject)) {
          throw new IllegalStateException(GradleModelConstants.UNSUPPORTED_MODEL_VERSION_ERROR);
        }
        createModuleInfo(module, androidProject, projectInfo, moduleDirPath, gradleProject);
        if (first == null) {
          first = androidProject;
        }
        continue;
      }
      if (hasAndroidTasks(module.getGradleProject())) {
        // AndroidProject is null for pre-0.5.0 projects.
        throw new IllegalStateException(GradleModelConstants.UNSUPPORTED_MODEL_VERSION_ERROR);
      }
      File gradleSettingsFile = new File(moduleDir, SdkConstants.FN_SETTINGS_GRADLE);
      if (gradleSettingsFile.isFile()) {
        // This is just a root folder for a group of Gradle projects. Set the Gradle project to null so the JPS builder won't try to
        // compile it using Gradle. We still need to create the module to display files inside it.
        gradleProject = null;
      }
      createModuleInfo(module, projectInfo, moduleDirPath, gradleProject);
    }

    if (first == null) {
      // Don't import project if we don't have at least one AndroidProject.
      return null;
    }

    populateDependencies(projectInfo);
    return projectInfo;
  }

  @NotNull
  private static DataNode<ProjectData> createProjectInfo(@NotNull String projectDirPath, @NotNull String name) {
    ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, projectDirPath, projectDirPath);
    projectData.setName(name);

    DataNode<ProjectData> projectInfo = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);

    // Gradle API doesn't expose project compile output path yet.
    JavaProjectData javaProjectData = new JavaProjectData(GradleConstants.SYSTEM_ID, projectDirPath + "/build/classes");
    projectInfo.createChild(JavaProjectData.KEY, javaProjectData);

    return projectInfo;
  }

  @NotNull
  private static String getRelativePath(@NotNull IdeaGradleProject gradleProject) {
    String separator = File.separator;
    if (separator.equals("\\")) {
      separator = "\\\\";
    }
    String gradleProjectPath = gradleProject.getGradleProjectPath();
    if (SdkConstants.GRADLE_PATH_SEPARATOR.equals(gradleProjectPath)) {
      return "";
    }
    return gradleProjectPath.replaceAll(SdkConstants.GRADLE_PATH_SEPARATOR, separator);
  }

  @Nullable
  private AndroidProject getAndroidProject(@NotNull final ExternalSystemTaskId id,
                                           @NotNull String projectPath,
                                           @NotNull final ExternalSystemTaskNotificationListener listener,
                                           @Nullable final GradleExecutionSettings settings) {
    AndroidProject androidProject = myHelper.execute(projectPath, settings, new Function<ProjectConnection, AndroidProject>() {
      @Nullable
      @Override
      public AndroidProject fun(ProjectConnection connection) {
        try {
          ModelBuilder<AndroidProject> modelBuilder = myHelper.getModelBuilder(AndroidProject.class, id, settings, connection, listener);
          return modelBuilder.get();
        }
        catch (RuntimeException e) {
          handleProjectImportError(e);
        }
        return null;
      }
    });
    return androidProject;
  }

  private static void handleProjectImportError(@NotNull RuntimeException e) {
    if (e instanceof UnknownModelException) {
      return;
    }
    Throwable root = e;
    if (e instanceof BuildException) {
      root = ExceptionUtil.getRootCause(e);
    }
    LOG.error(root);
  }

  private static boolean hasAndroidTasks(@NotNull GradleProject gradleProject) {
    for (GradleTask task : gradleProject.getTasks()) {
      if (task.getName() != null && task.getName().startsWith("android")) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static DataNode<ModuleData> createModuleInfo(@NotNull IdeaModule module,
                                                       @NotNull AndroidProject androidProject,
                                                       @NotNull DataNode<ProjectData> projectInfo,
                                                       @NotNull String moduleDirPath,
                                                       @NotNull IdeaGradleProject gradleProject) {
    String moduleName = module.getName();
    ModuleData moduleData = createModuleData(module, projectInfo, moduleName, moduleDirPath);
    DataNode<ModuleData> moduleInfo = projectInfo.createChild(ProjectKeys.MODULE, moduleData);

    Variant selectedVariant = getFirstVariant(androidProject);
    IdeaAndroidProject ideaAndroidProject = new IdeaAndroidProject(moduleName, moduleDirPath, androidProject, selectedVariant.getName());
    addContentRoot(ideaAndroidProject, moduleInfo, moduleDirPath);

    moduleInfo.createChild(AndroidProjectKeys.IDE_ANDROID_PROJECT, ideaAndroidProject);
    moduleInfo.createChild(AndroidProjectKeys.IDE_GRADLE_PROJECT, gradleProject);
    return moduleInfo;
  }

  @NotNull
  private static Variant getFirstVariant(@NotNull AndroidProject androidProject) {
    Map<String, Variant> variants = androidProject.getVariants();
    if (variants.size() == 1) {
      Variant variant = ContainerUtil.getFirstItem(variants.values());
      assert variant != null;
      return variant;
    }
    List<String> variantNames = Lists.newArrayList(variants.keySet());
    Collections.sort(variantNames);
    return variants.get(variantNames.get(0));
  }

  private static void addContentRoot(@NotNull IdeaAndroidProject androidProject,
                                     @NotNull DataNode<ModuleData> moduleInfo,
                                     @NotNull String moduleDirPath) {
    final ContentRootData contentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, moduleDirPath);
    AndroidContentRoot.ContentRootStorage storage = new AndroidContentRoot.ContentRootStorage() {
      @Override
      @NotNull
      public String getRootDirPath() {
        return contentRootData.getRootPath();
      }

      @Override
      public void storePath(@NotNull ExternalSystemSourceType sourceType, @NotNull File directory) {
        contentRootData.storePath(sourceType, directory.getAbsolutePath());
      }
    };
    AndroidContentRoot.storePaths(androidProject, storage);
    moduleInfo.createChild(ProjectKeys.CONTENT_ROOT, contentRootData);
  }

  @NotNull
  private static DataNode<ModuleData> createModuleInfo(@NotNull IdeaModule module,
                                                       @NotNull DataNode<ProjectData> projectInfo,
                                                       @NotNull String moduleDirPath,
                                                       @Nullable IdeaGradleProject gradleProject) {
    ModuleData moduleData = createModuleData(module, projectInfo, module.getName(), moduleDirPath);
    DataNode<ModuleData> moduleInfo = projectInfo.createChild(ProjectKeys.MODULE, moduleData);

    // Populate content roots.
    for (IdeaContentRoot from : module.getContentRoots()) {
      if (from == null || from.getRootDirectory() == null) {
        continue;
      }
      ContentRootData contentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, from.getRootDirectory().getAbsolutePath());
      GradleContentRoot.storePaths(from, contentRootData);
      moduleInfo.createChild(ProjectKeys.CONTENT_ROOT, contentRootData);
    }

    moduleInfo.createChild(AndroidProjectKeys.IDEA_MODULE, module);
    if (gradleProject != null) {
      moduleInfo.createChild(AndroidProjectKeys.IDE_GRADLE_PROJECT, gradleProject);
    }
    return moduleInfo;
  }

  private static ModuleData createModuleData(@NotNull IdeaModule module,
                                             @NotNull DataNode<ProjectData> projectInfo,
                                             @NotNull String name,
                                             @NotNull String dirPath) {
    String moduleConfigPath = GradleUtil.getConfigPath(module.getGradleProject(), projectInfo.getData().getLinkedExternalProjectPath());
    return new ModuleData(GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), name, dirPath, moduleConfigPath);
  }

  private static void populateDependencies(@NotNull DataNode<ProjectData> projectInfo) {
    Collection<DataNode<ModuleData>> modules = ExternalSystemApiUtil.getChildren(projectInfo, ProjectKeys.MODULE);
    for (DataNode<ModuleData> moduleInfo : modules) {
      IdeaAndroidProject androidProject = getIdeaAndroidProject(moduleInfo);
      if (androidProject != null) {
        populateDependencies(projectInfo, moduleInfo, androidProject);
        continue;
      }
      IdeaModule module = extractIdeaModule(moduleInfo);
      if (module != null) {
        GradleDependencies.populate(moduleInfo, projectInfo, module);
      }
    }
  }

  @Nullable
  static IdeaAndroidProject getIdeaAndroidProject(@NotNull DataNode<ModuleData> moduleInfo) {
    return getFirstNodeData(moduleInfo, AndroidProjectKeys.IDE_ANDROID_PROJECT);
  }

  private static void populateDependencies(@NotNull final DataNode<ProjectData> projectInfo,
                                           @NotNull final DataNode<ModuleData> moduleInfo,
                                           @NotNull IdeaAndroidProject ideaAndroidProject) {
    final Collection<DataNode<ModuleData>> modules = ExternalSystemApiUtil.getChildren(projectInfo, ProjectKeys.MODULE);
    AndroidDependencies.DependencyFactory dependencyFactory = new AndroidDependencies.DependencyFactory() {
      @Override
      public void addLibraryDependency(@NotNull DependencyScope scope, @NotNull String name, @NotNull File binaryPath) {
        LibraryDependency dependency = new LibraryDependency(name);
        dependency.setScope(scope);
        dependency.addPath(LibraryPathType.BINARY, binaryPath);
        dependency.addTo(moduleInfo, projectInfo);
      }

      @Override
      public void addModuleDependency(@NotNull DependencyScope scope, @NotNull String name, @NotNull String modulePath) {
        String dependencyName = name;
        for (DataNode<ModuleData> module : modules) {
          String moduleName = module.getData().getName();
          if (moduleName.equals(moduleInfo.getData().getName())) {
            // this is the same module as the one we are configuring.
            continue;
          }
          IdeaGradleProject gradleProject = getFirstNodeData(module, AndroidProjectKeys.IDE_GRADLE_PROJECT);
          if (gradleProject != null && Objects.equal(modulePath, gradleProject.getGradleProjectPath())) {
            dependencyName = moduleName;
            break;
          }
        }
        ModuleDependency dependency = new ModuleDependency(dependencyName);
        dependency.setScope(scope);
        dependency.addTo(moduleInfo, projectInfo);
      }
    };
    AndroidDependencies.populate(ideaAndroidProject, dependencyFactory);
  }

  @Nullable
  private static <T> T getFirstNodeData(@NotNull DataNode<ModuleData> moduleInfo, @NotNull Key<T> key) {
    Collection<DataNode<T>> children = ExternalSystemApiUtil.getChildren(moduleInfo, key);
    return getFirstNodeData(children);
  }

  @Nullable
  private static IdeaModule extractIdeaModule(@NotNull DataNode<ModuleData> moduleInfo) {
    Collection<DataNode<IdeaModule>> modules = ExternalSystemApiUtil.getChildren(moduleInfo, AndroidProjectKeys.IDEA_MODULE);
    // it is safe to remove this node. We only needed it to resolve dependencies.
    moduleInfo.getChildren().removeAll(modules);
    return getFirstNodeData(modules);
  }

  @Nullable
  static <T> T getFirstNodeData(Collection<DataNode<T>> nodes) {
    DataNode<T> node = ContainerUtil.getFirstItem(nodes);
    return node != null ? node.getData() : null;
  }
}
