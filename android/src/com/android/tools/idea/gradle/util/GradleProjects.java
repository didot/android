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
package com.android.tools.idea.gradle.util;

import static com.android.tools.idea.gradle.project.ProjectImportUtil.findImportTarget;
import static com.intellij.ide.impl.ProjectUtil.updateLastProjectLocation;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isExternalSystemAwareModule;
import static com.intellij.openapi.wm.impl.IdeFrameImpl.SHOULD_OPEN_IN_FULL_SCREEN;
import static java.lang.Boolean.TRUE;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.platform.PlatformProjectOpenProcessor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static com.android.tools.idea.gradle.project.ProjectImportUtil.findImportTarget;
import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.intellij.ide.impl.ProjectUtil.updateLastProjectLocation;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isExternalSystemAwareModule;
import static com.intellij.openapi.util.io.FileUtil.pathsEqual;

/**
 * Utility methods for {@link Project}s.
 */
public final class GradleProjects {
  private static final Key<Boolean> SYNC_REQUESTED_DURING_BUILD = Key.create("project.sync.requested.during.build");

  private GradleProjects() {
  }

  public static void executeProjectChanges(@NotNull Project project, @NotNull Runnable changes) {
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      if (!project.isDisposed()) {
        changes.run();
      }
      return;
    }
    ApplicationManager.getApplication().invokeAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      if (!project.isDisposed()) {
        ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(changes);
      }
    }));
  }

  /**
   * Opens the given project in the IDE.
   *
   * @param project the project to open.
   */
  public static void open(@NotNull Project project) {
    updateLastProjectLocation(project.getBasePath());

    Path projectDir = Paths.get(Objects.requireNonNull(project.getBasePath()));
    PlatformProjectOpenProcessor.openExistingProject(projectDir, projectDir, new OpenProjectTask(project));
  }

  public static boolean isOfflineBuildModeEnabled(@NotNull Project project) {
    return GradleSettings.getInstance(project).isOfflineWork();
  }

  /**
   * Indicates whether the given module is the one that represents the project or one of the projects included in the composite build.
   * <p>
   * For example, in this project:
   * <pre>
   * project1
   * - module1
   *   - module1.iml
   * - module2
   *   - module2.iml
   * -project1.iml
   * </pre>
   * "project1" is the module that represents the project.
   * </p>
   *
   * @param module the given module.
   * @return {@code true} if the given module is the one that represents the project, {@code false} otherwise.
   */
  public static boolean isGradleProjectModule(@NotNull Module module) {
    return ":".equals(getGradleModulePath(module));
  }

  /**
   * Returns the gradle path of a module, for example ":app" or ":lib:mylib". In the case of a module from a project included in the
   * composite build, the returned Gradle path is prefixed by the included project name, i.e. included_project:libs:mylib .
   * @param module the give module
   * @return the gradle path or {@code null} if not a gradle module or can't find the path.
   */
  @Nullable
  public static String getGradleModulePath(@NotNull Module module) {
    if (!isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
      return null;
    }
    String linkedProjectId = ExternalSystemModulePropertyManager.getInstance(module).getLinkedProjectId();
    if (linkedProjectId == null) {
      return null;
    }

    return linkedProjectId.contains(":") ? linkedProjectId : ":";
  }

  /**
   * Indicates whether the project in the given folder can be imported as a Gradle project.
   *
   * @param importSource the folder containing the project.
   * @return {@code true} if the project can be imported as a Gradle project, {@code false} otherwise.
   */
  public static boolean canImportAsGradleProject(@NotNull VirtualFile importSource) {
    VirtualFile target = findImportTarget(importSource);
    return (GradleConstants.EXTENSION.equals(target.getExtension()) ||
            target.getName().endsWith(GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION));
  }

  public static void setSyncRequestedDuringBuild(@NotNull Project project, @Nullable Boolean value) {
    project.putUserData(SYNC_REQUESTED_DURING_BUILD, value);
  }

  public static boolean isSyncRequestedDuringBuild(@NotNull Project project) {
    return SYNC_REQUESTED_DURING_BUILD.get(project, false);
  }

  public static boolean isIdeaAndroidModule(@NotNull Module module) {
    if (GradleFacet.getInstance(module) != null || JavaFacet.getInstance(module) != null) {
      return true;
    }
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet != null && androidFacet.requiresAndroidModel()) {
      return true;
    }
    return false;
  }

  /** Checks if the given project contains a module that contains code built by Android Studio's C++ support. */
  public static boolean containsExternalCppProjects(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
      if (ndkModuleModel != null) {
        return true;
      }
    }
    return false;
  }
}
