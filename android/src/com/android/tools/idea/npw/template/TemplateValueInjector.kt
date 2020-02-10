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
package com.android.tools.idea.npw.template

import com.android.AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE
import com.android.SdkConstants
import com.android.ide.common.repository.GradleVersion
import com.android.repository.Revision
import com.android.sdklib.AndroidVersion.VersionCodes.P
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.gradle.npw.project.GradleBuildSettings.getRecommendedBuildToolsRevision
import com.android.tools.idea.gradle.npw.project.GradleBuildSettings.needsExplicitBuildToolsVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.npw.ThemeHelper
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getInitialDomain
import com.android.tools.idea.npw.module.ConfigureAndroidModuleStep
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.projectsystem.AndroidModulePaths
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.templates.KeystoreUtils
import com.android.tools.idea.templates.KeystoreUtils.getDebugKeystore
import com.android.tools.idea.templates.KeystoreUtils.getOrCreateDefaultDebugKeystore
import com.android.tools.idea.templates.TemplateAttributes.ATTR_AIDL_DIR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_AIDL_OUT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_ANDROIDX_SUPPORT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_THEME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_THEME_APP_BAR_OVERLAY
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_THEME_EXISTS
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_THEME_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_THEME_NO_ACTION_BAR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_THEME_POPUP_OVERLAY
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BASE_FEATURE_DIR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BASE_FEATURE_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BASE_FEATURE_RES_DIR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API_REVISION
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API_STRING
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_TOOLS_VERSION
import com.android.tools.idea.templates.TemplateAttributes.ATTR_COMPANY_DOMAIN
import com.android.tools.idea.templates.TemplateAttributes.ATTR_DEBUG_KEYSTORE_SHA1
import com.android.tools.idea.templates.TemplateAttributes.ATTR_EXPLICIT_BUILD_TOOLS_VERSION
import com.android.tools.idea.templates.TemplateAttributes.ATTR_GRADLE_PLUGIN_VERSION
import com.android.tools.idea.templates.TemplateAttributes.ATTR_GRADLE_VERSION
import com.android.tools.idea.templates.TemplateAttributes.ATTR_HAS_APPLICATION_THEME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_DYNAMIC_FEATURE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LOW_MEMORY
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_NEW_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_KOTLIN_EAP_REPO
import com.android.tools.idea.templates.TemplateAttributes.ATTR_KOTLIN_VERSION
import com.android.tools.idea.templates.TemplateAttributes.ATTR_LANGUAGE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MANIFEST_DIR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MANIFEST_OUT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MIN_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MIN_API_LEVEL
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MODULE_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_PACKAGE_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_PROJECT_OUT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_RES_DIR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_RES_OUT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_SDK_DIR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_SOURCE_PROVIDER_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_SRC_DIR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_SRC_OUT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_TARGET_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_TARGET_API_STRING
import com.android.tools.idea.templates.TemplateAttributes.ATTR_TEST_DIR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_TEST_OUT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_THEME_EXISTS
import com.android.tools.idea.templates.TemplateAttributes.ATTR_TOP_OUT
import com.android.tools.idea.templates.TemplateMetadata.getBuildApiString
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Iterables
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.android.facet.SourceProviderManager
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.sdk.AndroidPlatform
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.versions.bundledRuntimeVersion
import java.io.File
import java.util.HashMap

/**
 * Utility class that sets common Template values used by a project Module.
 */
@Deprecated("Replace with ModuleTemplateData")
class TemplateValueInjector(private val myTemplateValues: MutableMap<String, Any>) {
  private val log: Logger
    get() = Logger.getInstance(TemplateValueInjector::class.java)

  /**
   * Adds, to the specified `templateValues`, common render template values like
   * [ATTR_BUILD_API], [ATTR_MIN_API], [ATTR_TARGET_API], etc.
   *
   * @param facet Android Facet (existing module)
   */
  fun setFacet(facet: AndroidFacet): TemplateValueInjector {
    addDebugKeyStore(myTemplateValues, facet)
    addApplicationTheme(myTemplateValues, facet)

    myTemplateValues[ATTR_IS_NEW_MODULE] = false
    myTemplateValues[ATTR_IS_LIBRARY_MODULE] = facet.configuration.isLibraryProject

    val appTheme = MergedManifestManager.getMergedManifestSupplier(facet.module).now?.manifestTheme
    myTemplateValues[ATTR_HAS_APPLICATION_THEME] = appTheme != null

    val module = facet.module
    val platform = AndroidPlatform.getInstance(module)
    if (platform != null) {
      val target = platform.target
      if (module.project.hasAndroidxSupport(false)) {
        myTemplateValues[ATTR_BUILD_API] = target.version.featureLevel
      }
      else {
        // The highest supported/recommended appCompact version is P(28)
        myTemplateValues[ATTR_BUILD_API] = target.version.featureLevel.coerceAtMost(P)
      }
      myTemplateValues[ATTR_BUILD_API_STRING] = getBuildApiString(target.version)
      // For parity with the value set in #setBuildVersion, the revision is set to 0 if the
      // release is not a preview.
      myTemplateValues[ATTR_BUILD_API_REVISION] = if (target.version.isPreview) target.revision else 0
    }

    val moduleInfo = AndroidModuleInfo.getInstance(facet)
    val minSdkVersion = moduleInfo.minSdkVersion
    val minSdkName = minSdkVersion.apiString

    myTemplateValues[ATTR_MIN_API] = minSdkName
    myTemplateValues[ATTR_TARGET_API] = moduleInfo.targetSdkVersion.apiLevel
    myTemplateValues[ATTR_MIN_API_LEVEL] = minSdkVersion.featureLevel

    val project = module.project
    addGradleVersions(project, false)
    addKotlinVersion()
    addAndroidxSupport(project, false)

    val projectType = facet.configuration.projectType
    if (projectType == PROJECT_TYPE_DYNAMIC_FEATURE) {
      setDynamicFeatureSupport(module)
    }
    return this
  }

  /**
   * Same as [setFacet], but uses a [AndroidVersionsInfo.VersionItem]. This version is used when the Module is not created yet.
   *
   * @param buildVersion Build version information for the new Module being created.
   * @param project      Used to find the Gradle Dependencies versions. If null, it will use the most recent values known.
   */
  @TestOnly
  fun setBuildVersion(buildVersion: AndroidVersionsInfo.VersionItem, project: Project?, isNewProject: Boolean): TemplateValueInjector {
    addDebugKeyStore(myTemplateValues, null)

    myTemplateValues[ATTR_IS_NEW_MODULE] = true // Android Modules are called Gradle Projects
    myTemplateValues[ATTR_THEME_EXISTS] = true // New modules always have a theme (unless its a library, but it will have no activity)

    setBuildAttributes(buildVersion, project, isNewProject)
    addGradleVersions(project, isNewProject)
    addKotlinVersion()
    addAndroidxSupport(project, isNewProject)
    return this
  }

  @TestOnly
  fun setBuildAttributes(buildVersion: AndroidVersionsInfo.VersionItem, project: Project?, isNewProject: Boolean) {
    myTemplateValues[ATTR_MIN_API_LEVEL] = buildVersion.minApiLevel
    myTemplateValues[ATTR_MIN_API] = buildVersion.minApiLevelStr
    if (project.hasAndroidxSupport(isNewProject)) {
      myTemplateValues[ATTR_BUILD_API] = buildVersion.buildApiLevel
    }
    else {
      // The highest supported/recommended appCompact version is P(28)
      myTemplateValues[ATTR_BUILD_API] = buildVersion.buildApiLevel.coerceAtMost(P)
    }

    myTemplateValues[ATTR_BUILD_API_STRING] = buildVersion.buildApiLevelStr
    myTemplateValues[ATTR_TARGET_API] = buildVersion.targetApiLevel
    myTemplateValues[ATTR_TARGET_API_STRING] = buildVersion.targetApiLevelStr
    val target = buildVersion.androidTarget
    // Note here that the target is null for a non-preview release
    // @see VersionItem#getAndroidTarget()
    myTemplateValues[ATTR_BUILD_API_REVISION] = target?.revision ?: 0
    if (target != null) { // this is a preview release
      val info = target.buildToolInfo
      if (info != null) {
        addBuildToolVersion(project, isNewProject, info.revision)
      }
    }
  }

  /**
   * Adds, to the specified `templateValues`, common Module roots template values like
   * [ATTR_PROJECT_OUT], [ATTR_SRC_DIR], [ATTR_SRC_OUT], etc.
   *
   * @param paths       Project paths
   * @param packageName Package Name for the module
   */
  @TestOnly
  fun setModuleRoots(paths: AndroidModulePaths, projectPath: String, moduleName: String, packageName: String): TemplateValueInjector {
    val moduleRoot = paths.moduleRoot!!

    // Register the resource directories associated with the active source provider
    myTemplateValues[ATTR_PROJECT_OUT] = FileUtil.toSystemIndependentName(moduleRoot.absolutePath)

    val srcDir = paths.getSrcDirectory(packageName)
    if (srcDir != null) {
      myTemplateValues[ATTR_SRC_DIR] = getRelativePath(moduleRoot, srcDir)!!
      myTemplateValues[ATTR_SRC_OUT] = FileUtil.toSystemIndependentName(srcDir.absolutePath)
    }

    val testDir = paths.getTestDirectory(packageName)
    if (testDir != null) {
      myTemplateValues[ATTR_TEST_DIR] = getRelativePath(moduleRoot, testDir)!!
      myTemplateValues[ATTR_TEST_OUT] = FileUtil.toSystemIndependentName(testDir.absolutePath)
    }

    val resDir = Iterables.getFirst(paths.resDirectories, null)
    if (resDir != null) {
      myTemplateValues[ATTR_RES_DIR] = getRelativePath(moduleRoot, resDir)!!
      myTemplateValues[ATTR_RES_OUT] = FileUtil.toSystemIndependentName(resDir.path)
    }

    val manifestDir = paths.manifestDirectory
    if (manifestDir != null) {
      myTemplateValues[ATTR_MANIFEST_DIR] = getRelativePath(moduleRoot, manifestDir)!!
      myTemplateValues[ATTR_MANIFEST_OUT] = FileUtil.toSystemIndependentName(manifestDir.path)
    }

    val aidlDir = paths.getAidlDirectory(packageName)
    if (aidlDir != null) {
      myTemplateValues[ATTR_AIDL_DIR] = getRelativePath(moduleRoot, aidlDir)!!
      myTemplateValues[ATTR_AIDL_OUT] = FileUtil.toSystemIndependentName(aidlDir.path)
    }

    myTemplateValues[ATTR_TOP_OUT] = projectPath
    myTemplateValues[ATTR_MODULE_NAME] = moduleName.trimStart { it == ':' } // The templates already add an initial ":"
    myTemplateValues[ATTR_PACKAGE_NAME] = packageName
    return this
  }

  /**
   * Adds, to the specified `templateValues`, common render template values like
   * [com.android.tools.idea.templates.TemplateAttributes.ATTR_GRADLE_PLUGIN_VERSION],
   * [com.android.tools.idea.templates.TemplateAttributes.ATTR_GRADLE_VERSION], etc.
   */
  @TestOnly
  fun setProjectDefaults(project: Project?, isNewProject: Boolean): TemplateValueInjector {
    // For now, our definition of low memory is running in a 32-bit JVM. In this case, we have to be careful about the amount of memory we
    // request for the Gradle build.
    myTemplateValues[ATTR_IS_LOW_MEMORY] = SystemInfo.is32Bit

    addGradleVersions(project, isNewProject)
    addKotlinVersion()

    if (project != null) {
      myTemplateValues[ATTR_TOP_OUT] = project.basePath!!
    }

    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val progress = StudioLoggerProgressIndicator(ConfigureAndroidModuleStep::class.java)

    addBuildToolVersion(project, isNewProject, getRecommendedBuildToolsRevision(sdkHandler, progress))

    val sdkLocation = sdkHandler.location
    if (sdkLocation != null) {
      myTemplateValues[ATTR_SDK_DIR] = sdkLocation.path
    }
    return this
  }

  @TestOnly
  fun setLanguage(language: Language): TemplateValueInjector {
    myTemplateValues[ATTR_LANGUAGE] = language.toString()
    return this
  }

  private fun setDynamicFeatureSupport(module: Module): TemplateValueInjector {
    myTemplateValues[ATTR_IS_DYNAMIC_FEATURE] = true

    val baseFeature = DynamicAppUtils.getBaseFeature(module) ?:
                      throw RuntimeException("Dynamic Feature Module '" + module.name + "' has no Base Module")

    return setBaseFeature(baseFeature)
  }

  private fun setBaseFeature(baseFeature: Module): TemplateValueInjector {

    fun String.toPath() = VfsUtilCore.urlToPath(this)

    val androidFacet = AndroidFacet.getInstance(baseFeature)!!
    val rootFolder = AndroidRootUtil.findModuleRootFolderPath(baseFeature)
    val mainSourceProvider = SourceProviderManager.getInstance(androidFacet).mainIdeaSourceProvider
    val baseModuleResourceRootPath = mainSourceProvider.resDirectories.firstOrNull()?.path
                                     ?: mainSourceProvider.resDirectoryUrls.first().toPath()

    myTemplateValues[ATTR_BASE_FEATURE_NAME] = GradleUtil.getGradlePath(baseFeature).orEmpty()
    myTemplateValues[ATTR_BASE_FEATURE_DIR] = rootFolder?.path.orEmpty()
    myTemplateValues[ATTR_BASE_FEATURE_RES_DIR] = baseModuleResourceRootPath
    return this
  }

  @VisibleForTesting
  fun addGradleVersions(project: Project?, isNewProject: Boolean): TemplateValueInjector {
    myTemplateValues[ATTR_GRADLE_PLUGIN_VERSION] = determineGradlePluginVersion(project, isNewProject).toString()
    myTemplateValues[ATTR_GRADLE_VERSION] = SdkConstants.GRADLE_LATEST_VERSION
    return this
  }

  fun addTemplateAdditionalValues(packageName: String, template: ObjectProperty<NamedModuleTemplate>): TemplateValueInjector {
    myTemplateValues[ATTR_PACKAGE_NAME] = packageName
    myTemplateValues[ATTR_SOURCE_PROVIDER_NAME] = template.get().name
    myTemplateValues[ATTR_COMPANY_DOMAIN] = getInitialDomain()
    return this
  }

  private fun addKotlinVersion() {
    val kotlinVersion = bundledRuntimeVersion()
    // Always add the kotlin version attribute. If we are adding a new kotlin activity, we may need to add dependencies
    myTemplateValues[ATTR_KOTLIN_VERSION] = kotlinVersion
    myTemplateValues[ATTR_KOTLIN_EAP_REPO] = setOf("rc", "eap", "-M").any { it in kotlinVersion }
  }

  private fun addBuildToolVersion(project: Project?, isNewProject: Boolean, buildToolRevision: Revision) {
    val gradlePluginVersion = determineGradlePluginVersion(project, isNewProject)
    myTemplateValues[ATTR_BUILD_TOOLS_VERSION] = buildToolRevision.toString()
    myTemplateValues[ATTR_EXPLICIT_BUILD_TOOLS_VERSION] = needsExplicitBuildToolsVersion(gradlePluginVersion, buildToolRevision)
  }

  private fun addAndroidxSupport(project: Project?, isNewProject: Boolean) {
    myTemplateValues[ATTR_ANDROIDX_SUPPORT] = project.hasAndroidxSupport(isNewProject)
  }


  private fun addDebugKeyStore(templateValues: MutableMap<String, Any>, facet: AndroidFacet?) {
    try {
      val sha1File = facet?.let { getDebugKeystore(it) } ?: getOrCreateDefaultDebugKeystore()
      templateValues[ATTR_DEBUG_KEYSTORE_SHA1] = KeystoreUtils.sha1(sha1File)
    }
    catch (e: Exception) {
      log.info("Could not compute SHA1 hash of debug keystore.", e)
      templateValues[ATTR_DEBUG_KEYSTORE_SHA1] = ""
    }
  }

  /**
   * Helper method for converting two paths relative to one another into a String path, since this
   * ends up being a common pattern when creating values to put into our template's data model.
   */
  private fun getRelativePath(base: File, file: File): String? =
  // Note: Use FileUtil.getRelativePath(String, String, char) instead of FileUtil.getRelativePath(File, File), because the second version
    // will use the base.getParent() if base directory is not yet created  (when adding a new module, the directory is created later)
    FileUtil.getRelativePath(FileUtil.toSystemIndependentName(base.path), FileUtil.toSystemIndependentName(file.path), '/')

  /**
   * Find the most appropriated Gradle Plugin version for the specified project.
   *
   * @param project If `null` (ie we are creating a new project) returns the recommended gradle version.
   */
  private fun determineGradlePluginVersion(project: Project?, isNewProject: Boolean): GradleVersion {
    val defaultGradleVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
    if (project == null || isNewProject) {
      return defaultGradleVersion
    }

    val versionInUse = GradleUtil.getAndroidGradleModelVersionInUse(project)
    if (versionInUse != null) {
      return versionInUse
    }

    val androidPluginInfo = AndroidPluginInfo.findFromBuildFiles(project)
    return androidPluginInfo?.pluginVersion ?: defaultGradleVersion
  }

  private fun addApplicationTheme(templateValues: MutableMap<String, Any>, facet: AndroidFacet) {
    val module = facet.module
    val projectFile = module.project.projectFile ?: return

    val helper = ThemeHelper(module)
    val themeName = helper.appThemeName ?: return

    val configuration = ConfigurationManager.getOrCreateInstance(module).getConfiguration(projectFile)

    val map = HashMap<String, Any>()
    map[ATTR_APP_THEME_NAME] = themeName
    map[ATTR_APP_THEME_EXISTS] = true

    ApplicationManager.getApplication().runReadAction {
      val hasActionBar = ThemeHelper.hasActionBar(configuration, themeName)
      addDerivedTheme(map, themeName, ATTR_APP_THEME_NO_ACTION_BAR, hasActionBar == false, helper, configuration)
      addDerivedTheme(map, themeName, ATTR_APP_THEME_APP_BAR_OVERLAY, false, helper, configuration)
      addDerivedTheme(map, themeName, ATTR_APP_THEME_POPUP_OVERLAY, false, helper, configuration)
      templateValues[ATTR_APP_THEME] = map
    }
  }

  private fun addDerivedTheme(map: MutableMap<String, Any>,
                              themeName: String,
                              derivedThemeName: String,
                              useBaseThemeAsDerivedTheme: Boolean,
                              helper: ThemeHelper,
                              configuration: Configuration) {
    var fullThemeName = if (useBaseThemeAsDerivedTheme) themeName else "$themeName.$derivedThemeName"
    var exists = ThemeHelper.themeExists(configuration, fullThemeName)
    if (!exists && !helper.isLocalTheme(themeName)) {
      fullThemeName = derivedThemeName
      exists = helper.isLocalTheme(derivedThemeName)
    }
    map[ATTR_APP_THEME_NAME + derivedThemeName] = fullThemeName
    map[ATTR_APP_THEME_EXISTS + derivedThemeName] = exists
  }
}

// Note: New projects are always created with androidx dependencies
fun Project?.hasAndroidxSupport(isNewProject: Boolean) = this == null || isNewProject || this.isAndroidx()
