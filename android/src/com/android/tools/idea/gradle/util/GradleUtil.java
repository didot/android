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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.model.*;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.MavenRepositories;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModel;
import com.android.tools.idea.gradle.project.sync.facet.gradle.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.jarFinder.InternetAttachSourceProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_HIGHER;
import static com.android.tools.idea.gradle.AndroidGradleModel.getTestArtifacts;
import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE_TRANSLATE;
import static com.android.tools.idea.gradle.util.EmbeddedDistributionPaths.findEmbeddedGradleDistributionPath;
import static com.android.tools.idea.gradle.util.GradleBuilds.ENABLE_TRANSLATION_JVM_ARG;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.Projects.requiresAndroidModel;
import static org.jetbrains.android.util.AndroidUtils.isAndroidStudio;
import static com.android.tools.idea.startup.GradleSpecificInitializer.GRADLE_DAEMON_TIMEOUT_MS;
import static com.google.common.base.Splitter.on;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.notification.NotificationType.WARNING;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getExecutionSettings;
import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ArrayUtil.toStringArray;
import static com.intellij.util.SystemProperties.getUserHome;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;

/**
 * Utilities related to Gradle.
 */
public final class GradleUtil {
  public static final ProjectSystemId GRADLE_SYSTEM_ID = GradleConstants.SYSTEM_ID;

  @NonNls public static final String BUILD_DIR_DEFAULT_NAME = "build";
  @NonNls public static final String GRADLEW_PROPERTIES_PATH = join(FD_GRADLE_WRAPPER, FN_GRADLE_WRAPPER_PROPERTIES);

  private static final Logger LOG = Logger.getInstance(GradleUtil.class);

  /**
   * Finds characters that shouldn't be used in the Gradle path.
   * <p/>
   * I was unable to find any specification for Gradle paths. In my experiments, Gradle only failed with slashes. This list may grow if
   * we find any other unsupported characters.
   */
  private static final CharMatcher ILLEGAL_GRADLE_PATH_CHARS_MATCHER = CharMatcher.anyOf("\\/");

  private GradleUtil() {
  }

  @NotNull
  public static Collection<File> getGeneratedSourceFolders(@NotNull BaseArtifact artifact) {
    try {
      Collection<File> folders = artifact.getGeneratedSourceFolders();
      // JavaArtifactImpl#getGeneratedSourceFolders returns null even though BaseArtifact#getGeneratedSourceFolders is marked as @NonNull.
      // See https://code.google.com/p/android/issues/detail?id=216236
      return folders != null ? folders : Collections.emptyList();
    }
    catch (UnsupportedMethodException e) {
      // Model older than 1.2.
    }
    return Collections.emptyList();
  }

  @NotNull
  public static Dependencies getDependencies(@NotNull BaseArtifact artifact, @Nullable GradleVersion modelVersion) {
    return artifact.getDependencies();
  }

  public static boolean androidModelSupportsInstantApps(@NotNull GradleVersion modelVersion) {
    return modelVersion.compareIgnoringQualifiers("2.3.0") >= 0;
  }

  public static void clearStoredGradleJvmArgs(@NotNull Project project) {
    GradleSettings settings = GradleSettings.getInstance(project);
    String existingJvmArgs = settings.getGradleVmOptions();
    settings.setGradleVmOptions(null);
    if (!isEmptyOrSpaces(existingJvmArgs)) {
      invokeAndWaitIfNeeded((Runnable)() -> {
        String jvmArgs = existingJvmArgs.trim();
        String msg =
          String.format("Starting with version 1.3, Android Studio no longer supports IDE-specific Gradle JVM arguments.\n\n" +
                        "Android Studio will now remove any stored Gradle JVM arguments.\n\n" +
                        "Would you like to copy these JVM arguments:\n%1$s\n" +
                        "to the project's gradle.properties file?\n\n" +
                        "(Any existing JVM arguments in the gradle.properties file will be overwritten.)", jvmArgs);

        int result = Messages.showYesNoDialog(project, msg, "Gradle Settings", getQuestionIcon());
        if (result == Messages.YES) {
          try {
            GradleProperties gradleProperties = new GradleProperties(project);
            gradleProperties.setJvmArgs(jvmArgs);
            gradleProperties.save();
          }
          catch (IOException e) {
            String err = String.format("Failed to copy JVM arguments '%1$s' to the project's gradle.properties file.", existingJvmArgs);
            LOG.info(err, e);

            String cause = e.getMessage();
            if (isNotEmpty(cause)) {
              err += String.format("<br>\nCause: %1$s", cause);
            }

            AndroidGradleNotification.getInstance(project).showBalloon("Gradle Settings", err, ERROR);
          }
        }
        else {
          String text =
            String.format("JVM arguments<br>\n'%1$s'<br>\nwere not copied to the project's gradle.properties file.", existingJvmArgs);
          AndroidGradleNotification.getInstance(project).showBalloon("Gradle Settings", text, WARNING);
        }
      });
    }
  }

  public static boolean isSupportedGradleVersion(@NotNull GradleVersion gradleVersion) {
    GradleVersion supported = GradleVersion.parse(GRADLE_MINIMUM_VERSION);
    return supported.compareTo(gradleVersion) <= 0;
  }

  /**
   * This is temporary, until the model returns more outputs per artifact.
   * Deprecating since the model 0.13 provides multiple outputs per artifact if split apks are enabled.
   */
  @Deprecated
  @NotNull
  public static AndroidArtifactOutput getOutput(@NotNull AndroidArtifact artifact) {
    Collection<AndroidArtifactOutput> outputs = artifact.getOutputs();
    assert !outputs.isEmpty();
    AndroidArtifactOutput output = getFirstItem(outputs);
    assert output != null;
    return output;
  }

  @NotNull
  public static Icon getModuleIcon(@NotNull Module module) {
    AndroidGradleModel androidModel = AndroidGradleModel.get(module);
    if (androidModel != null) {
      int projectType = androidModel.getProjectType();
      if (projectType == PROJECT_TYPE_APP || projectType == PROJECT_TYPE_INSTANTAPP) {
        return AndroidIcons.AppModule;
      }
      return AndroidIcons.LibraryModule;
    }
    return requiresAndroidModel(module.getProject()) ? AllIcons.Nodes.PpJdk : AllIcons.Nodes.Module;
  }

  @Nullable
  public static AndroidProject getAndroidProject(@NotNull Module module) {
    AndroidGradleModel gradleModel = AndroidGradleModel.get(module);
    return gradleModel != null ? gradleModel.getAndroidProject() : null;
  }

  @Nullable
  public static NativeAndroidProject getNativeAndroidProject(@NotNull Module module) {
    NativeAndroidGradleModel gradleModel = NativeAndroidGradleModel.get(module);
    return gradleModel != null ? gradleModel.getNativeAndroidProject() : null;
  }

  /**
   * Returns the Gradle "logical" path (using colons as separators) if the given module represents a Gradle project or sub-project.
   *
   * @param module the given module.
   * @return the Gradle path for the given module, or {@code null} if the module does not represent a Gradle project or sub-project.
   */
  @Nullable
  public static String getGradlePath(@NotNull Module module) {
    AndroidGradleFacet facet = AndroidGradleFacet.getInstance(module);
    return facet != null ? facet.getConfiguration().GRADLE_PROJECT_PATH : null;
  }

  /**
   * Returns whether the given module is the module corresponding to the project root (i.e. gradle path of ":") and has no source roots.
   * <p/>
   * The default Android Studio projects create an empty module at the root level. In theory, users could add sources to that module, but
   * we expect that most don't and keep that as a module simply to tie together other modules.
   */
  public static boolean isRootModuleWithNoSources(@NotNull Module module) {
    if (ModuleRootManager.getInstance(module).getSourceRoots().length == 0) {
      String gradlePath = getGradlePath(module);
      if (gradlePath == null || gradlePath.equals(":")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the library dependencies in the given variant. This method checks dependencies in the main and test (as currently selected
   * in the UI) artifacts. The dependency lookup is not transitive (only direct dependencies are returned.)
   */
  @NotNull
  public static List<AndroidLibrary> getDirectLibraryDependencies(@NotNull Variant variant, @NotNull AndroidGradleModel androidModel) {
    List<AndroidLibrary> libraries = Lists.newArrayList();

    GradleVersion modelVersion = androidModel.getModelVersion();

    AndroidArtifact mainArtifact = variant.getMainArtifact();
    Dependencies dependencies = getDependencies(mainArtifact, modelVersion);
    libraries.addAll(dependencies.getLibraries());

    for (BaseArtifact testArtifact : getTestArtifacts(variant)) {
      dependencies = getDependencies(testArtifact, modelVersion);
      libraries.addAll(dependencies.getLibraries());
    }
    return libraries;
  }

  @Nullable
  public static Module findModuleByGradlePath(@NotNull Project project, @NotNull String gradlePath) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
      if (gradleFacet != null) {
        if (gradlePath.equals(gradleFacet.getConfiguration().GRADLE_PROJECT_PATH)) {
          return module;
        }
      }
    }
    return null;
  }

  @NotNull
  public static List<String> getPathSegments(@NotNull String gradlePath) {
    return on(GRADLE_PATH_SEPARATOR).omitEmptyStrings().splitToList(gradlePath);
  }

  /**
   * Returns the build.gradle file in the given module. This method first checks if the Gradle model has the path of the build.gradle
   * file for the given module. If it doesn't find it, it tries to find a build.gradle inside the module's root directory.
   *
   * @param module the given module.
   * @return the build.gradle file in the given module, or {@code null} if it cannot be found.
   */
  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull Module module) {
    AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
    if (gradleFacet != null && gradleFacet.getGradleModuleModel() != null) {
      return gradleFacet.getGradleModuleModel().getBuildFile();
    }
    // At the time we're called, module.getModuleFile() may be null, but getModuleFilePath returns the path where it will be created.
    File moduleFilePath = new File(module.getModuleFilePath());
    File parentFile = moduleFilePath.getParentFile();
    return parentFile != null ? getGradleBuildFile(parentFile) : null;
  }

  /**
   * Returns the build.gradle file that is expected right in the directory at the given path. For example, if the directory path is
   * '~/myProject/myModule', this method will look for the file '~/myProject/myModule/build.gradle'.
   * <p>
   * <b>Note:</b> Only use this method if you do <b>not</b> have a reference to a {@link Module}. Otherwise use
   * {@link #getGradleBuildFile(Module)}.
   * </p>
   *
   * @param dirPath the given directory path.
   * @return the build.gradle file in the directory at the given path, or {@code null} if there is no build.gradle file in the given
   * directory path.
   */
  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull File dirPath) {
    File gradleBuildFilePath = getGradleBuildFilePath(dirPath);
    return findFileByIoFile(gradleBuildFilePath, true);
  }

  /**
   * Returns the path of a build.gradle file in the directory at the given path. For example, if the directory path is
   * '~/myProject/myModule', this method will return the path '~/myProject/myModule/build.gradle'. Please note that a build.gradle file
   * may not exist at the returned path.
   * <p>
   * <b>Note:</b> Only use this method if you do <b>not</b> have a reference to a {@link Module}. Otherwise use
   * {@link #getGradleBuildFile(Module)}.
   * </p>
   *
   * @param dirPath the given directory path.
   * @return the path of a build.gradle file in the directory at the given path.
   */
  @NotNull
  public static File getGradleBuildFilePath(@NotNull File dirPath) {
    return new File(dirPath, FN_BUILD_GRADLE);
  }

  @Nullable
  public static VirtualFile getGradleSettingsFile(@NotNull File dirPath) {
    File gradleSettingsFilePath = getGradleSettingsFilePath(dirPath);
    return findFileByIoFile(gradleSettingsFilePath, true);
  }

  @NotNull
  public static File getGradleSettingsFilePath(@NotNull File dirPath) {
    return new File(dirPath, FN_SETTINGS_GRADLE);
  }

  @Nullable
  public static GradleExecutionSettings getOrCreateGradleExecutionSettings(@NotNull Project project, boolean useEmbeddedGradle) {
    GradleExecutionSettings executionSettings = getGradleExecutionSettings(project);
    if (isAndroidStudio() && useEmbeddedGradle) {
      if (executionSettings == null) {
        File gradlePath = findEmbeddedGradleDistributionPath();
        assert gradlePath != null && gradlePath.isDirectory();
        executionSettings = new GradleExecutionSettings(gradlePath.getPath(), null, LOCAL, null, false);
        File jdkPath = IdeSdks.getInstance().getJdkPath();
        if (jdkPath != null) {
          executionSettings.setJavaHome(jdkPath.getPath());
        }
      }
    }
    return executionSettings;
  }

  @Nullable
  public static GradleExecutionSettings getGradleExecutionSettings(@NotNull Project project) {
    GradleProjectSettings projectSettings = getGradleProjectSettings(project);
    if (projectSettings == null) {
      File baseDirPath = getBaseDirPath(project);
      String msg = String
        .format("Unable to obtain Gradle project settings for project '%1$s', located at '%2$s'", project.getName(), baseDirPath.getPath());
      LOG.info(msg);
      return null;
    }

    try {
      GradleExecutionSettings settings = getExecutionSettings(project, projectSettings.getExternalProjectPath(), GRADLE_SYSTEM_ID);
      if (settings != null) {
        // By setting the Gradle daemon timeout to -1, we don't allow IDEA to set it to 1 minute. Gradle daemons need to be reused as
        // much as possible. The default timeout is 3 hours.
        settings.setRemoteProcessIdleTtlInMs(GRADLE_DAEMON_TIMEOUT_MS);
      }
      return settings;
    }
    catch (IllegalArgumentException e) {
      LOG.info("Failed to obtain Gradle execution settings", e);
      return null;
    }
  }

  @Nullable
  private static GradleProjectSettings getGradleProjectSettings(@NotNull Project project) {
    return GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project);
  }

  @VisibleForTesting
  @Nullable
  static String getGradleInvocationJvmArg(@Nullable BuildMode buildMode) {
    if (ASSEMBLE_TRANSLATE == buildMode) {
      return AndroidGradleSettings.createJvmArg(ENABLE_TRANSLATION_JVM_ARG, true);
    }
    return null;
  }

  public static void stopAllGradleDaemonsAndRestart() {
    DefaultGradleConnector.close();
    Application application = ApplicationManager.getApplication();
    if (application instanceof ApplicationImpl) {
      ((ApplicationImpl)application).restart(true);
    }
    else {
      application.restart();
    }
  }

  /**
   * Converts a Gradle project name into a system dependent path relative to root project. Please note this is the default mapping from a
   * Gradle "logical" path to a physical path. Users can override this mapping in settings.gradle and this mapping may not always be
   * accurate.
   * <p/>
   * E.g. ":module" becomes "module" and ":directory:module" is converted to "directory/module"
   */
  @NotNull
  public static String getDefaultPhysicalPathFromGradlePath(@NotNull String gradlePath) {
    List<String> segments = getPathSegments(gradlePath);
    return join(toStringArray(segments));
  }

  /**
   * Obtains the default path for the module (Gradle sub-project) with the given name inside the given directory.
   */
  @NotNull
  public static File getModuleDefaultPath(@NotNull VirtualFile parentDir, @NotNull String gradlePath) {
    assert gradlePath.length() > 0;
    String relativePath = getDefaultPhysicalPathFromGradlePath(gradlePath);
    return new File(virtualToIoFile(parentDir), relativePath);
  }

  /**
   * Tests if the Gradle path is valid and return index of the offending character or -1 if none.
   */
  public static int isValidGradlePath(@NotNull String gradlePath) {
    return ILLEGAL_GRADLE_PATH_CHARS_MATCHER.indexIn(gradlePath);
  }

  /**
   * Checks if the project already has a module with given Gradle path.
   */
  public static boolean hasModule(@Nullable Project project, @NotNull String gradlePath, boolean checkProjectFolder) {
    if (project == null) {
      return false;
    }
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (gradlePath.equals(getGradlePath(module))) {
        return true;
      }
    }
    if (checkProjectFolder) {
      File location = getModuleDefaultPath(project.getBaseDir(), gradlePath);
      if (location.isFile()) {
        return true;
      }
      else if (location.isDirectory()) {
        File[] children = location.listFiles();
        return children == null || children.length > 0;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
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

      AndroidGradleModel androidModel = AndroidGradleModel.get(module);
      if (androidModel != null) {
        AndroidProject androidProject = androidModel.getAndroidProject();
        String modelVersion = androidProject.getModelVersion();
        if (androidModel.getProjectType() == PROJECT_TYPE_APP) {
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

    return found != null ? GradleVersion.tryParse(found) : null;
  }

  public static void attemptToUseEmbeddedGradle(@NotNull Project project) {
    if (isAndroidStudio()) {
      GradleWrapper gradleWrapper = GradleWrapper.find(project);
      if (gradleWrapper != null) {
        String gradleVersion = null;
        try {
          Properties properties = gradleWrapper.getProperties();
          String url = properties.getProperty(DISTRIBUTION_URL_PROPERTY);
          gradleVersion = getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
        }
        catch (IOException e) {
          LOG.warn("Failed to read file " + gradleWrapper.getPropertiesFilePath().getPath());
        }
        if (gradleVersion != null &&
            isCompatibleWithEmbeddedGradleVersion(gradleVersion) &&
            !GradleLocalCache.getInstance().containsGradleWrapperVersion(gradleVersion, project)) {
          File embeddedGradlePath = findEmbeddedGradleDistributionPath();
          if (embeddedGradlePath != null) {
            GradleProjectSettings gradleSettings = getGradleProjectSettings(project);
            if (gradleSettings != null) {
              gradleSettings.setDistributionType(LOCAL);
              gradleSettings.setGradleHome(embeddedGradlePath.getPath());
            }
          }
        }
      }
    }
  }

  @VisibleForTesting
  @Nullable
  static String getGradleWrapperVersionOnlyIfComingForGradleDotOrg(@Nullable String url) {
    if (url != null) {
      int foundIndex = url.indexOf("://");
      if (foundIndex != -1) {
        String protocol = url.substring(0, foundIndex);
        if (protocol.equals("http") || protocol.equals("https")) {
          String expectedPrefix = protocol + "://services.gradle.org/distributions/gradle-";
          if (url.startsWith(expectedPrefix)) {
            // look for "-" before "bin" or "all"
            foundIndex = url.indexOf('-', expectedPrefix.length());
            if (foundIndex != -1) {
              String version = url.substring(expectedPrefix.length(), foundIndex);
              if (isNotEmpty(version)) {
                return version;
              }
            }
          }
        }
      }
    }
    return null;
  }

  // Currently, the latest Gradle version is 2.2.1, and we consider 2.2 and 2.2.1 as compatible.
  private static boolean isCompatibleWithEmbeddedGradleVersion(@NotNull String gradleVersion) {
    return gradleVersion.equals(GRADLE_MINIMUM_VERSION) || gradleVersion.equals(GRADLE_LATEST_VERSION);
  }

  /**
   * Returns {@code true} if the main artifact of the given Android model depends on the given artifact, which consists of a group id and an
   * artifact id, such as {@link SdkConstants#APPCOMPAT_LIB_ARTIFACT}.
   *
   * @param androidModel the Android model to check
   * @param artifact     the artifact
   * @return {@code true} if the project depends on the given artifact (including transitively)
   */
  public static boolean dependsOn(@NonNull AndroidGradleModel androidModel, @NonNull String artifact) {
    Dependencies dependencies = androidModel.getSelectedMainCompileDependencies();
    return dependsOn(dependencies, artifact);
  }

  @Nullable
  public static GradleVersion getModuleDependencyVersion(@NonNull AndroidGradleModel androidModel, @NonNull String artifact) {
    Dependencies dependencies = androidModel.getSelectedMainCompileDependencies();
    for (AndroidLibrary library : dependencies.getLibraries()) {
      String version = getDependencyVersion(library, artifact, true);
      if (version != null) {
        return GradleVersion.tryParse(version);
      }
    }
    return null;
  }

  /**
   * Returns {@code true} if the androidTest artifact of the given Android model depends on the given artifact, which consists of a group id
   * and an artifact id, such as {@link SdkConstants#APPCOMPAT_LIB_ARTIFACT}.
   *
   * @param androidModel the Android model to check
   * @param artifact     the artifact
   * @return {@code true} if the project depends on the given artifact (including transitively)
   */
  public static boolean dependsOnAndroidTest(@NonNull AndroidGradleModel androidModel, @NonNull String artifact) {
    Dependencies dependencies = androidModel.getSelectedAndroidTestCompileDependencies();
    if (dependencies == null) {
      return false;
    }
    return dependsOn(dependencies, artifact);
  }

  /**
   * Returns {@code true} if the given dependencies include the given artifact, which consists of a group id and an artifact id, such as
   * {@link SdkConstants#APPCOMPAT_LIB_ARTIFACT}.
   *
   * @param dependencies the Gradle dependencies object to check
   * @param artifact     the artifact
   * @return {@code true} if the dependencies include the given artifact (including transitively)
   */
  private static boolean dependsOn(@NonNull Dependencies dependencies, @NonNull String artifact) {
    for (AndroidLibrary library : dependencies.getLibraries()) {
      if (dependsOn(library, artifact, true)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns {@code true} if the given library depends on the given artifact, which consists a group id and an artifact id, such as
   * {@link SdkConstants#APPCOMPAT_LIB_ARTIFACT}.
   *
   * @param library      the Gradle library to check
   * @param artifact     the artifact
   * @param transitively if {@code false}, checks only direct dependencies, otherwise checks transitively
   * @return {@code true} if the project depends on the given artifact
   */
  public static boolean dependsOn(@NonNull AndroidLibrary library, @NonNull String artifact, boolean transitively) {
    return getDependencyVersion(library, artifact, transitively) != null;
  }

  private static String getDependencyVersion(@NonNull AndroidLibrary library, @NonNull String artifact, boolean transitively) {
    MavenCoordinates resolvedCoordinates = library.getResolvedCoordinates();
    if (resolvedCoordinates != null) {
      if (artifact.endsWith(resolvedCoordinates.getArtifactId()) &&
          artifact.equals(resolvedCoordinates.getGroupId() + ':' + resolvedCoordinates.getArtifactId())) {
        return resolvedCoordinates.getVersion();
      }
    }

    if (transitively) {
      for (AndroidLibrary dependency : library.getLibraryDependencies()) {
        String version = getDependencyVersion(dependency, artifact, true);
        if (version != null) {
          return version;
        }
      }
    }
    return null;
  }

  public static boolean hasCause(@NotNull Throwable e, @NotNull Class<?> causeClass) {
    // We want to ignore class loader difference, that's why we just compare fully-qualified class names here.
    String causeClassName = causeClass.getName();
    for (Throwable ex = e; ex != null; ex = ex.getCause()) {
      if (causeClassName.equals(ex.getClass().getName())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static File getGradleUserSettingsFile() {
    String homePath = getUserHome();
    if (homePath == null) {
      return null;
    }
    return new File(homePath, join(DOT_GRADLE, FN_GRADLE_PROPERTIES));
  }

  @Nullable
  public static DataNode<ProjectData> getCachedProjectData(@NotNull Project project) {
    ProjectDataManager dataManager = ProjectDataManager.getInstance();
    ExternalProjectInfo projectInfo = dataManager.getExternalProjectData(project, GRADLE_SYSTEM_ID, getBaseDirPath(project).getPath());
    return projectInfo != null ? projectInfo.getExternalProjectStructure() : null;
  }

  /**
   * Indicates whether <a href="https://code.google.com/p/android/issues/detail?id=170841">a known layout rendering issue</a> is present in
   * the given model.
   *
   * @param model the given model.
   * @return {@true} if the model has the layout rendering issue; {@code false} otherwise.
   */
  public static boolean hasLayoutRenderingIssue(@NotNull AndroidProject model) {
    String modelVersion = model.getModelVersion();
    return modelVersion.startsWith("1.2.0") || modelVersion.equals("1.2.1") || modelVersion.equals("1.2.2");
  }

  @Nullable
  public static VirtualFile findSourceJarForLibrary(@NotNull File libraryFilePath) {
    return findArtifactFileInRepository(libraryFilePath, "-sources.jar", true);
  }

  @Nullable
  public static VirtualFile findPomForLibrary(@NotNull File libraryFilePath) {
    return findArtifactFileInRepository(libraryFilePath, ".pom", false);
  }

  @Nullable
  private static VirtualFile findArtifactFileInRepository(@NotNull File libraryFilePath,
                                                          @NotNull String fileNameSuffix,
                                                          boolean searchInIdeCache) {
    VirtualFile realJarFile = findFileByIoFile(libraryFilePath, true);

    if (realJarFile == null) {
      // Unlikely to happen. At this point the jar file should exist.
      return null;
    }

    VirtualFile parent = realJarFile.getParent();
    String name = getNameWithoutExtension(libraryFilePath);
    String sourceFileName = name + fileNameSuffix;
    if (parent != null) {

      // Try finding sources in the same folder as the jar file. This is the layout of Maven repositories.
      VirtualFile sourceJar = parent.findChild(sourceFileName);
      if (sourceJar != null) {
        return sourceJar;
      }

      // Try the parent's parent. This is the layout of the repository cache in .gradle folder.
      parent = parent.getParent();
      if (parent != null) {
        for (VirtualFile child : parent.getChildren()) {
          if (!child.isDirectory()) {
            continue;
          }
          sourceJar = child.findChild(sourceFileName);
          if (sourceJar != null) {
            return sourceJar;
          }
        }
      }
    }

    if (searchInIdeCache) {
      // Try IDEA's own cache.
      File librarySourceDirPath = InternetAttachSourceProvider.getLibrarySourceDir();
      File sourceJar = new File(librarySourceDirPath, sourceFileName);
      return findFileByIoFile(sourceJar, true);
    }
    return null;
  }

  public static void setBuildToolsVersion(@NotNull Project project, @NotNull String version) {
    List<GradleBuildModel> modelsToUpdate = Lists.newArrayList();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        GradleBuildModel buildModel = GradleBuildModel.get(module);
        if (buildModel != null) {
          AndroidModel android = buildModel.android();
          if (android != null && !version.equals(android.buildToolsVersion())) {
            android.setBuildToolsVersion(version);
            modelsToUpdate.add(buildModel);
          }
        }
      }
    }

    if (!modelsToUpdate.isEmpty()) {
      runWriteCommandAction(project, () -> {
        for (GradleBuildModel buildModel : modelsToUpdate) {
          buildModel.applyChanges();
        }
      });
    }
  }

  @Nullable
  public static AndroidLibrary findLibrary(@NotNull File bundleDir, @NotNull Variant variant, @Nullable GradleVersion modelVersion) {
    AndroidArtifact artifact = variant.getMainArtifact();
    Dependencies dependencies = getDependencies(artifact, modelVersion);
    for (AndroidLibrary library : dependencies.getLibraries()) {
      AndroidLibrary result = findLibrary(library, bundleDir);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  @Nullable
  public static AndroidLibrary findLibrary(@NotNull AndroidLibrary library, @NotNull File bundleDir) {
    if (filesEqual(bundleDir, library.getFolder())) {
      return library;
    }

    for (AndroidLibrary dependency : library.getLibraryDependencies()) {
      AndroidLibrary result = findLibrary(dependency, bundleDir);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  public enum PluginType {
    STANDARD("gradle", GRADLE_PLUGIN_RECOMMENDED_VERSION),
    EXPERIMENTAL("gradle-experimental", GRADLE_EXPERIMENTAL_PLUGIN_RECOMMENDED_VERSION);

    @NotNull private final String pluginName;
    @NotNull private final String defaultVersion;

    PluginType(@NotNull String pluginName, @NotNull String defaultVersion) {
      this.pluginName = pluginName;
      this.defaultVersion = defaultVersion;
    }
  }

  @NotNull
  public static String getLatestKnownPluginVersion(PluginType pluginType) {
    FileOp fop = FileOpUtils.create();
    Optional<GradleCoordinate> highestValueCoordinate = EmbeddedDistributionPaths.findAndroidStudioLocalMavenRepoPaths().stream()
      .map(path -> MavenRepositories.getHighestInstalledVersion("com.android.tools.build", pluginType.pluginName, path, null, true, fop))
      .filter(coordinate -> coordinate != null)
      .max(COMPARE_PLUS_HIGHER);

    if (!highestValueCoordinate.isPresent()) {
      if (isAndroidStudio() && !AndroidPlugin.isGuiTestingMode() &&
          !ApplicationManager.getApplication().isInternal() &&
          !ApplicationManager.getApplication().isUnitTestMode()) {
        // In a release build, Android Studio must find the latest version in its offline repo(s).
        throw new IllegalStateException("Gradle plugin missing from the offline Maven repo");
      } else {
        // In all other scenarios we will not throw an exception, but use the last known version from SdkConstants.
        // TODO: revisit this when tests are running with the latest (source) build.
        LOG.info(pluginType.pluginName + " plugin missing the offline Maven repo, will use default " + pluginType.defaultVersion);
        return pluginType.defaultVersion;
      }
    }

    return highestValueCoordinate.get().getRevision();
  }
}
