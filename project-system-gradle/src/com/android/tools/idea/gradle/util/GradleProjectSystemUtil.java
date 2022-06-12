/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.SdkConstants.FD_RES_CLASS;
import static com.android.SdkConstants.FD_SOURCE_GEN;
import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;
import static com.google.common.collect.Iterables.getOnlyElement;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.model.IdeBaseArtifact;
import com.android.tools.idea.gradle.model.IdeBaseArtifactCore;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData;
import com.android.tools.idea.projectsystem.FilenameConstants;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleProjectSystemUtil {
  /**
   * Checks if the given folder contains sources generated by aapt. When the IDE uses light R and Manifest classes, these folders are not
   * marked as sources of the module.
   *
   * <p>Note that folder names used by AGP suggest this is only for generated R.java files (generated/source/r,
   * generate/not_namespaced_r_class_sources) but in reality this is where aapt output goes, so this includes Manifest.java if custom
   * permissions are defined in the manifest.
   */
  public static boolean isAaptGeneratedSourcesFolder(@NotNull File folder, @NotNull File buildFolder) {
    File generatedFolder = new File(buildFolder, FilenameConstants.GENERATED);

    // Folder used in 3.1 and below. Additional level added below for androidTest.
    File generatedSourceR = FileUtils.join(generatedFolder, FD_SOURCE_GEN, FD_RES_CLASS);
    // Naming convention used in 3.2 and above, if R.java files are generated at all.
    File rClassSources = new File(generatedFolder, FilenameConstants.NOT_NAMESPACED_R_CLASS_SOURCES);

    return FileUtil.isAncestor(generatedSourceR, folder, false) || FileUtil.isAncestor(rClassSources, folder, false);
  }

  /**
   * Checks if the given folder contains "Binding" base classes generated by data binding. The IDE provides light versions of these classes,
   * so it can be useful to ignore them as source folders.
   *
   * See {@link FilenameConstants#DATA_BINDING_BASE_CLASS_SOURCES} for a bit more detail.
   *
   * TODO(b/129543943): Investigate moving this logic into the data binding module
   */
  @VisibleForTesting
  public static boolean isDataBindingGeneratedBaseClassesFolder(@NotNull File folder, @NotNull File buildFolder) {
    File generatedFolder = new File(buildFolder, FilenameConstants.GENERATED);
    File dataBindingSources = new File(generatedFolder, FilenameConstants.DATA_BINDING_BASE_CLASS_SOURCES);
    return FileUtil.isAncestor(dataBindingSources, folder, false);
  }

  /**
   * Checks if the given folder contains navigation arg classes by safe arg. When the IDE uses light safe arg classes,
   * these folders are not marked as sources of the module.
   */
  public static boolean isSafeArgGeneratedSourcesFolder(@NotNull File folder, @NotNull File buildFolder) {
    if (!StudioFlags.NAV_SAFE_ARGS_SUPPORT.get()) return false;

    File generatedFolder = new File(buildFolder, FilenameConstants.GENERATED);
    File safeArgClassSources = FileUtils.join(generatedFolder, FD_SOURCE_GEN, FilenameConstants.SAFE_ARG_CLASS_SOURCES);

    return FileUtil.isAncestor(safeArgClassSources, folder, false);
  }

  /**
   * Wrapper around {@link IdeBaseArtifact#getGeneratedSourceFolders()} that skips the aapt sources folder when light classes are used by the
   * IDE.
   */
  public static Collection<File> getGeneratedSourceFoldersToUse(@NotNull IdeBaseArtifactCore artifact, @NotNull GradleAndroidModelData model) {
    File buildFolder = model.getAndroidProject().getBuildFolder();
    return artifact.getGeneratedSourceFolders()
      .stream()
      .filter(folder -> !isAaptGeneratedSourcesFolder(folder, buildFolder))
      .filter(folder -> !isDataBindingGeneratedBaseClassesFolder(folder, buildFolder))
      .filter(folder -> !isSafeArgGeneratedSourcesFolder(folder, buildFolder))
      .collect(Collectors.toList());
  }

  @NotNull
  public static String createFullTaskName(@NotNull String gradleProjectPath, @NotNull String taskName) {
    if (gradleProjectPath.endsWith(GRADLE_PATH_SEPARATOR)) {
      // Prevent double colon when dealing with root module (e.g. "::assemble");
      return gradleProjectPath + taskName;
    }
    return gradleProjectPath + GRADLE_PATH_SEPARATOR + taskName;
  }

  /**
   * Returns true if we should use compatibility configuration names (such as "compile") instead
   * of the modern configuration names (such as "api" or "implementation") for the given project
   *
   * @param project the project to consult
   * @return true if we should use compatibility configuration names
   */
  public static boolean useCompatibilityConfigurationNames(@NotNull Project project) {
    return useCompatibilityConfigurationNames(getAndroidGradleModelVersionInUse(project));
  }

  /**
   * Returns true if we should use compatibility configuration names (such as "compile") instead
   * of the modern configuration names (such as "api" or "implementation") for the given Gradle version
   *
   * @param gradleVersion the Gradle plugin version to check
   * @return true if we should use compatibility configuration names
   */
  public static boolean useCompatibilityConfigurationNames(@Nullable GradleVersion gradleVersion) {
    return gradleVersion != null && gradleVersion.getMajor() < 3;
  }

  /**
   * Determines version of the Android gradle plugin (and model) used by the project. The result can be absent if there are no android
   * modules in the project or if the last sync has failed.
   */
  @Nullable
  public static GradleVersion getAndroidGradleModelVersionInUse(@NotNull Project project) {
    Set<String> foundInLibraries = Sets.newHashSet();
    Set<String> foundInApps = Sets.newHashSet();
    for (Module module : ModuleManager.getInstance(project).getModules()) {

      GradleAndroidModel androidModel = GradleAndroidModel.get(module);
      if (androidModel != null) {
        IdeAndroidProject androidProject = androidModel.getAndroidProject();
        String modelVersion = androidProject.getAgpVersion();
        if (androidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_APP) {
          foundInApps.add(modelVersion);
        }
        else {
          foundInLibraries.add(modelVersion);
        }
      }
    }

    String found = null;

    // Prefer the version in app.
    if (foundInApps.size() == 1) {
      found = getOnlyElement(foundInApps);
    }
    else if (foundInApps.isEmpty() && foundInLibraries.size() == 1) {
      found = getOnlyElement(foundInLibraries);
    }

    return found != null ? GradleVersion.tryParseAndroidGradlePluginVersion(found) : null;
  }

  @Nullable
  public static GradleVersion getAndroidGradleModelVersionInUse(@NotNull Module module) {
    GradleAndroidModel androidModel = GradleAndroidModel.get(module);
    if (androidModel != null) {
      IdeAndroidProject androidProject = androidModel.getAndroidProject();
      return GradleVersion.tryParse(androidProject.getAgpVersion());
    }

    return null;
  }

  /**
   * Returns the list of {@link Module modules} to build for a given base module.
   */
  @NotNull
  public static List<Module> getModulesToBuild(@NotNull Module module) {
    return Stream
      .concat(Stream.of(module), getModuleSystem(module).getDynamicFeatureModules().stream())
      .collect(Collectors.toList());
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(GradleProjectSystemUtil.class);
  }

  /**
   * Attempts to retrieve the {@link GradleAndroidModel} for the module containing the given file.
   *
   * @param file           the given file.
   * @param honorExclusion if {@code true}, this method will return {@code null} if the given file is "excluded".
   * @return the {@code AndroidModuleModel} for the module containing the given file, or {@code null} if the module is not an Android
   * module.
   */
  @Nullable
  public static GradleAndroidModel findAndroidModelInModule(@NotNull Project project, @NotNull VirtualFile file, boolean honorExclusion) {
    Module module = ProjectFileIndex.getInstance(project).getModuleForFile(file, honorExclusion);
    if (module == null) {
      return null;
    }

    if (module.isDisposed()) {
      getLogger().warn("Attempted to get an Android Facet from a disposed module");
      return null;
    }

    return GradleAndroidModel.get(module);
  }

  /**
   * Attempts to retrieve the {@link GradleAndroidModel} for the module containing the given file.
   * <p/>
   * This method will return {@code null} if the file is "excluded" or if the module the file belongs to is not an Android module.
   *
   * @param file the given file.
   * @return the {@code AndroidModuleModel} for the module containing the given file, or {@code null} if the file is "excluded" or if the
   * module the file belongs to is not an Android module.
   */
  @Nullable
  public static GradleAndroidModel findAndroidModelInModule(@NotNull Project project, @NotNull VirtualFile file) {
    return findAndroidModelInModule(project, file, true /* ignore "excluded files */);
  }

  @NotNull
  public static List<Module> getAppHolderModulesSupportingBundleTask(@NotNull Project project) {
    return ProjectStructure.getInstance(project).getAppHolderModules().stream()
      .filter(GradleProjectSystemUtil::supportsBundleTask)
      .collect(Collectors.toList());
  }

  /**
   * Returns {@code true} if the module supports the "bundle" task, i.e. if the Gradle
   * plugin associated to the module is of high enough version number and supports
   * the "Bundle" tool.
   */
  public static boolean supportsBundleTask(@NotNull Module module) {
    GradleAndroidModel androidModule = GradleAndroidModel.get(module);
    if (androidModule == null) {
      return false;
    }
    return !StringUtil.isEmpty(androidModule.getSelectedVariant().getMainArtifact().getBuildInformation().getBundleTaskName());
  }
}
