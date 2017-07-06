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
package com.android.tools.idea.npw.template;

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleVersion;
import com.android.repository.Revision;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.instantapp.InstantApps;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.npw.module.ConfigureAndroidModuleStep;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.templates.KeystoreUtils;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.android.tools.idea.templates.SupportLibrary;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_FEATURE;
import static com.android.tools.idea.gradle.npw.project.GradleBuildSettings.getRecommendedBuildToolsRevision;
import static com.android.tools.idea.templates.KeystoreUtils.getDebugKeystore;
import static com.android.tools.idea.templates.KeystoreUtils.getOrCreateDefaultDebugKeystore;
import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * Utility class that sets common Template values used by a project Module.
 */
public final class TemplateValueInjector {
  private static final String PROJECT_LOCATION_ID = "projectLocation";

  private final Map<String, Object> myTemplateValues;

  /**
   * @param templateValues Values will be added to this Map.
   */
  public TemplateValueInjector(@NotNull Map<String, Object> templateValues) {
    this.myTemplateValues = templateValues;
  }

  /**
   * Adds, to the specified <code>templateValues</code>, common render template values like
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_BUILD_API},
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_MIN_API},
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_TARGET_API}, etc.
   *
   * @param facet Android Facet (existing module)
   */
  public TemplateValueInjector setFacet(@NotNull AndroidFacet facet) {
    addDebugKeyStore(myTemplateValues, facet);

    myTemplateValues.put(ATTR_IS_NEW_PROJECT, false); // Android Modules are called Gradle Projects
    myTemplateValues.put(ATTR_IS_LIBRARY_MODULE, facet.isLibraryProject());

    String appTheme = MergedManifest.get(facet).getManifestTheme();
    myTemplateValues.put(ATTR_HAS_APPLICATION_THEME, appTheme != null);

    AndroidPlatform platform = AndroidPlatform.getInstance(facet.getModule());
    if (platform != null) {
      IAndroidTarget target = platform.getTarget();
      myTemplateValues.put(ATTR_BUILD_API, target.getVersion().getFeatureLevel());
      myTemplateValues.put(ATTR_BUILD_API_STRING, getBuildApiString(target.getVersion()));
      // For parity with the value set in #setBuildVersion, the revision is set to 0 if the
      // release is not a preview.
      myTemplateValues.put(ATTR_BUILD_API_REVISION, target.getVersion().isPreview() ? target.getRevision() : 0);
    }

    AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(facet);
    AndroidVersion minSdkVersion = moduleInfo.getMinSdkVersion();
    String minSdkName = minSdkVersion.getApiString();

    myTemplateValues.put(ATTR_MIN_API, minSdkName);
    myTemplateValues.put(ATTR_TARGET_API, moduleInfo.getTargetSdkVersion().getApiLevel());
    myTemplateValues.put(ATTR_MIN_API_LEVEL, minSdkVersion.getFeatureLevel());

    Project project = facet.getModule().getProject();
    addGradleVersions(project);

    if (facet.getProjectType() == PROJECT_TYPE_FEATURE) {
      setInstantAppSupport();

      Module baseFeature = InstantApps.findBaseFeature(project);
      AndroidModuleModel moduleModel = AndroidModuleModel.get(baseFeature);
      assert moduleModel != null;
      Collection<File> resDirectories = moduleModel.getDefaultSourceProvider().getResDirectories();
      assert !resDirectories.isEmpty();
      File baseModuleResourceRoot = resDirectories.iterator().next();

      myTemplateValues.put(ATTR_BASE_FEATURE_RES_DIR, baseModuleResourceRoot.getPath());
    }

    return this;
  }

  /**
   * Same as {@link #setFacet(AndroidFacet)}, but uses a {link AndroidVersionsInfo.VersionItem}. This version is used when the Module is
   * not created yet.
   *
   * @param buildVersion Build version information for the new Module being created.
   */
  public TemplateValueInjector setBuildVersion(@NotNull AndroidVersionsInfo.VersionItem buildVersion) {
    addDebugKeyStore(myTemplateValues, null);

    myTemplateValues.put(ATTR_IS_NEW_PROJECT, true); // Android Modules are called Gradle Projects
    myTemplateValues.put(ATTR_THEME_EXISTS, true); // New modules always have a theme (unless its a library, but it will have no activity)

    myTemplateValues.put(ATTR_MIN_API_LEVEL, buildVersion.getApiLevel());
    myTemplateValues.put(ATTR_MIN_API, buildVersion.getApiLevelStr());
    myTemplateValues.put(ATTR_BUILD_API, buildVersion.getBuildApiLevel());
    myTemplateValues.put(ATTR_BUILD_API_STRING, buildVersion.getBuildApiLevelStr());
    myTemplateValues.put(ATTR_TARGET_API, buildVersion.getTargetApiLevel());
    myTemplateValues.put(ATTR_TARGET_API_STRING, buildVersion.getTargetApiLevelStr());
    IAndroidTarget target = buildVersion.getAndroidTarget();
    // Note here that the target is null for a non-preview release
    // @see VersionItem#getAndroidTarget()
    myTemplateValues.put(ATTR_BUILD_API_REVISION, target == null ? 0 : target.getRevision());
    if (target != null) { // this is a preview release
      BuildToolInfo info = target.getBuildToolInfo();
      if (info != null) {
        myTemplateValues.put(ATTR_BUILD_TOOLS_VERSION, info.getRevision().toString());
      }
    }
    addGradleVersions(null);
    return this;
  }

  public TemplateValueInjector setJavaVersion(Project project) {
    // We can't JUST look at the overall project level language level, since
    // Gradle sync appears not to sync the overall project level; instead we
    // have to take the min of all the modules
    LanguageLevel min = ApplicationManager.getApplication().runReadAction((Computable<LanguageLevel>)() -> {
      LanguageLevel minResult = null;
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        LanguageLevelModuleExtension moduleLevelExt = LanguageLevelModuleExtensionImpl.getInstance(module);
        if (moduleLevelExt != null) {
          LanguageLevel moduleLevel = moduleLevelExt.getLanguageLevel();
          if (moduleLevel != null) {
            if (minResult == null || moduleLevel.compareTo(minResult) < 0) {
              minResult = moduleLevel;
            }
          }
        }
      }
      return minResult == null ? LanguageLevelProjectExtension.getInstance(project).getLanguageLevel() : minResult;
    });

    myTemplateValues.put(ATTR_JAVA_VERSION, min.getCompilerComplianceDefaultOption());

    return this;
  }

  /**
   * Adds, to the specified <code>templateValues</code>, common Module roots template values like
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_PROJECT_OUT},
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_SRC_DIR},
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_SRC_OUT}, etc.
   *
   * @param paths       Project paths
   * @param packageName Package Name for the module
   */
  public TemplateValueInjector setModuleRoots(@NotNull AndroidProjectPaths paths, @NotNull String packageName) {
    File moduleRoot = paths.getModuleRoot();

    assert moduleRoot != null;

    // Register the resource directories associated with the active source provider
    myTemplateValues.put(ATTR_PROJECT_OUT, FileUtil.toSystemIndependentName(moduleRoot.getAbsolutePath()));

    File srcDir = paths.getSrcDirectory(packageName);
    if (srcDir != null) {
      myTemplateValues.put(ATTR_SRC_DIR, getRelativePath(moduleRoot, srcDir));
      myTemplateValues.put(ATTR_SRC_OUT, FileUtil.toSystemIndependentName(srcDir.getAbsolutePath()));
    }

    File testDir = paths.getTestDirectory(packageName);
    if (testDir != null) {
      myTemplateValues.put(ATTR_TEST_DIR, getRelativePath(moduleRoot, testDir));
      myTemplateValues.put(ATTR_TEST_OUT, FileUtil.toSystemIndependentName(testDir.getAbsolutePath()));
    }

    File resDir = paths.getResDirectory();
    if (resDir != null) {
      myTemplateValues.put(ATTR_RES_DIR, getRelativePath(moduleRoot, resDir));
      myTemplateValues.put(ATTR_RES_OUT, FileUtil.toSystemIndependentName(resDir.getPath()));
    }

    File manifestDir = paths.getManifestDirectory();
    if (manifestDir != null) {
      myTemplateValues.put(ATTR_MANIFEST_DIR, getRelativePath(moduleRoot, manifestDir));
      myTemplateValues.put(ATTR_MANIFEST_OUT, FileUtil.toSystemIndependentName(manifestDir.getPath()));
    }

    File aidlDir = paths.getAidlDirectory(packageName);
    if (aidlDir != null) {
      myTemplateValues.put(ATTR_AIDL_DIR, getRelativePath(moduleRoot, aidlDir));
      myTemplateValues.put(ATTR_AIDL_OUT, FileUtil.toSystemIndependentName(aidlDir.getPath()));
    }

    myTemplateValues.put(PROJECT_LOCATION_ID, moduleRoot.getParent());

    // We're really interested in the directory name on disk, not the module name. These will be different if you give a module the same
    // name as its containing project.
    myTemplateValues.put(ATTR_MODULE_NAME, moduleRoot.getName());
    myTemplateValues.put(ATTR_PACKAGE_NAME, packageName);

    return this;
  }

  /**
   * Adds, to the specified <code>templateValues</code>, common render template values like
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_APP_TITLE},
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_GRADLE_PLUGIN_VERSION},
   * {@link com.android.tools.idea.templates.TemplateMetadata#ATTR_GRADLE_VERSION}, etc.
   */
  public TemplateValueInjector setProjectDefaults(@Nullable Project project, @NotNull String moduleTitle, boolean isInstantApp) {
    myTemplateValues.put(ATTR_APP_TITLE, moduleTitle);

    // For now, our definition of low memory is running in a 32-bit JVM. In this case, we have to be careful about the amount of memory we
    // request for the Gradle build.
    myTemplateValues.put(ATTR_IS_LOW_MEMORY, SystemInfo.is32Bit);

    addGradleVersions(project);
    myTemplateValues.put(ATTR_IS_GRADLE, true);

    // TODO: Check if this is used at all by the templates
    myTemplateValues.put("target.files", new HashSet<>());
    myTemplateValues.put("files.to.open", new ArrayList<>());

    // TODO: Check this one with Joe. It seems to be used by the old code on Import module, but can't find it on new code
    myTemplateValues.put(ATTR_CREATE_ACTIVITY, false);

    // TODO: This seems project stuff
    if (project != null) {
      myTemplateValues.put(ATTR_TOP_OUT, project.getBasePath());
    }

    final AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    ProgressIndicator progress = new StudioLoggerProgressIndicator(ConfigureAndroidModuleStep.class);
    Revision buildToolRevision = getRecommendedBuildToolsRevision(sdkHandler, progress, isInstantApp);
    myTemplateValues.put(ATTR_BUILD_TOOLS_VERSION, buildToolRevision.toString());

    File sdkLocation = sdkHandler.getLocation();
    if (sdkLocation != null) {
      myTemplateValues.put(ATTR_SDK_DIR, sdkLocation.getPath());

      String espressoVersion = RepositoryUrlManager.get().getLibraryRevision(SupportLibrary.ESPRESSO_CORE.getGroupId(),
                                                                             SupportLibrary.ESPRESSO_CORE.getArtifactId(),
                                                                             null, false, sdkLocation, FileOpUtils.create());
      if (espressoVersion != null) {
        myTemplateValues.put(ATTR_ESPRESSO_VERSION, espressoVersion);
      }
    }

    return this;
  }

  public TemplateValueInjector setInstantAppSupport() {
    myTemplateValues.put(ATTR_IS_INSTANT_APP, true);
    myTemplateValues.put(ATTR_INSTANT_APP_API_MIN_VERSION, InstantApps.getMinTargetSdk());
    return this;
  }

  private void addGradleVersions(@Nullable Project project) {
    myTemplateValues.put(ATTR_GRADLE_PLUGIN_VERSION, determineGradlePluginVersion(project));
    myTemplateValues.put(ATTR_GRADLE_VERSION, SdkConstants.GRADLE_LATEST_VERSION);
  }

  private static void addDebugKeyStore(@NotNull Map<String, Object> templateValues, @Nullable AndroidFacet facet) {
    try {
      File sha1File = facet == null ? getOrCreateDefaultDebugKeystore() : getDebugKeystore(facet);
      templateValues.put(ATTR_DEBUG_KEYSTORE_SHA1, KeystoreUtils.sha1(sha1File));
    }
    catch (Exception e) {
      getLog().info("Could not compute SHA1 hash of debug keystore.", e);
      templateValues.put(ATTR_DEBUG_KEYSTORE_SHA1, "");
    }
  }

  /**
   * Helper method for converting two paths relative to one another into a String path, since this
   * ends up being a common pattern when creating values to put into our template's data model.
   */
  @Nullable
  private static String getRelativePath(@NotNull File base, @NotNull File file) {
    // Note: Use FileUtil.getRelativePath(String, String, char) instead of FileUtil.getRelativePath(File, File), because the second version
    // will use the base.getParent() if base directory is not yet created  (when adding a new module, the directory is created later)
    return FileUtil.getRelativePath(FileUtil.toSystemIndependentName(base.getPath()),
                                    FileUtil.toSystemIndependentName(file.getPath()), '/');
  }

  /**
   * Find the most appropriated Gradle Plugin version for the specified project.
   *
   * @param project If {@code null} (ie we are creating a new project) returns the recommended gradle version.
   */
  @NotNull
  private static String determineGradlePluginVersion(@Nullable Project project) {
    String defaultGradleVersion = AndroidPluginGeneration.ORIGINAL.getLatestKnownVersion();
    if (project == null) {
      return defaultGradleVersion;
    }

    GradleVersion versionInUse = GradleUtil.getAndroidGradleModelVersionInUse(project);
    if (versionInUse != null) {
      return versionInUse.toString();
    }

    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.searchInBuildFilesOnly(project);
    GradleVersion pluginVersion = (androidPluginInfo == null) ? null : androidPluginInfo.getPluginVersion();
    return (pluginVersion == null) ? defaultGradleVersion : pluginVersion.toString();
  }

  private static Logger getLog() {
    return Logger.getInstance(TemplateValueInjector.class);
  }
}
