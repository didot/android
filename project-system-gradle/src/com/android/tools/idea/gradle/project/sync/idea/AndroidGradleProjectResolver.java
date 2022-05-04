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
package com.android.tools.idea.gradle.project.sync.idea;

import static com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION;
import static com.android.tools.idea.flags.StudioFlags.DISABLE_FORCED_UPGRADES;
import static com.android.tools.idea.gradle.project.sync.IdeAndroidModelsKt.ideAndroidSyncErrorToException;
import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.simulateRegisteredSyncError;
import static com.android.tools.idea.gradle.project.sync.errors.GradleDistributionInstallIssueCheckerKt.COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX;
import static com.android.tools.idea.gradle.project.sync.idea.AndroidExtraModelProviderConfiguratorKt.configureAndGetExtraModelProvider;
import static com.android.tools.idea.gradle.project.sync.idea.DependencyUtilKt.findSourceSetDataForArtifact;
import static com.android.tools.idea.gradle.project.sync.idea.KotlinPropertiesKt.preserveKotlinUserDataInDataNodes;
import static com.android.tools.idea.gradle.project.sync.idea.SdkSyncUtil.syncAndroidSdks;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NATIVE_VARIANTS;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.PROJECT_CLEANUP_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.SYNC_ISSUE;
import static com.android.tools.idea.gradle.project.sync.idea.issues.GradleWrapperImportCheck.validateGradleWrapper;
import static com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgrade.displayForceUpdatesDisabledMessage;
import static com.android.tools.idea.gradle.project.upgrade.ProjectUpgradeNotificationKt.expireProjectUpgradeNotifications;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.ANDROID_HOME_JVM_ARG;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.utils.BuildScriptUtil.findGradleSettingsFile;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE_SYNC;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.LIBRARY_DEPENDENCY;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isInProcessMode;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static com.intellij.util.PathUtil.getJarPathForClass;
import static com.intellij.util.PathUtil.toSystemIndependentName;
import static java.util.Collections.emptyList;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.CONFIGURATION_ARTIFACTS;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.RESOLVED_SOURCE_SETS;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getModuleId;

import android.annotation.SuppressLint;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.gradle.model.GradlePluginModel;
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifacts;
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel;
import com.android.repository.Revision;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.LibraryFilePaths;
import com.android.tools.idea.gradle.LibraryFilePaths.ArtifactPaths;
import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.model.IdeArtifactName;
import com.android.tools.idea.gradle.model.IdeBaseArtifactCore;
import com.android.tools.idea.gradle.model.IdeModuleSourceSet;
import com.android.tools.idea.gradle.model.IdeSourceProvider;
import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.model.IdeVariantCore;
import com.android.tools.idea.gradle.model.impl.IdeLibraryModelResolverImpl;
import com.android.tools.idea.gradle.model.impl.IdeResolvedLibraryTable;
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedLibraryTable;
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedLibraryTableImpl;
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.V2NdkModel;
import com.android.tools.idea.gradle.project.sync.IdeAndroidModels;
import com.android.tools.idea.gradle.project.sync.IdeAndroidNativeVariantsModels;
import com.android.tools.idea.gradle.project.sync.IdeAndroidSyncError;
import com.android.tools.idea.gradle.project.sync.SdkSync;
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.idea.data.model.ProjectCleanupModel;
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys;
import com.android.tools.idea.gradle.project.sync.idea.issues.JdkImportCheck;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath;
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.stats.UsageTrackerUtils;
import com.android.utils.StringHelper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.externalSystem.JavaModuleData;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.LibraryLevel;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.project.TestData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.PathsList;
import com.intellij.util.SystemProperties;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipException;
import kotlin.Unit;
import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel;
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel;
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel;
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptModelBuilderService;
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptSourceSetModel;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleExecutionWorkspace;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleModuleDataKt;

/**
 * Imports Android-Gradle projects into IDEA.
 */
@Order(ExternalSystemConstants.UNORDERED)
public final class AndroidGradleProjectResolver extends AbstractProjectResolverExtension implements AndroidGradleProjectResolverMarker {
  /**
   * Stores a collection of variants of the data node tree for previously synced build variants.
   * <p>
   * NOTE: This key/data is not directly processed by any data importers.
   */
  @NotNull public static final com.intellij.openapi.externalSystem.model.Key<VariantProjectDataNodes>
    CACHED_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS =
    com.intellij.openapi.externalSystem.model.Key.create(VariantProjectDataNodes.class, 1 /* not used */);

  /**
   * Stores a collection of internal in-memory properties used by Kotlin 1.6.20 IDE plugin so that they can be restored when the data node
   * tree is re-used to re-import a build variant it represents.
   * <p>
   * NOTE: This key/data is not directly processed by any data importers.
   */
  @NotNull public static final com.intellij.openapi.externalSystem.model.Key<KotlinProperties>
    KOTLIN_PROPERTIES =
    com.intellij.openapi.externalSystem.model.Key.create(KotlinProperties.class, 1 /* not used */);

  public static final GradleVersion MINIMUM_SUPPORTED_VERSION = GradleVersion.parse(GRADLE_PLUGIN_MINIMUM_VERSION);
  public static final String BUILD_SYNC_ORPHAN_MODULES_NOTIFICATION_GROUP_NAME = "Build sync orphan modules";

  private static final Key<Boolean> IS_ANDROID_PLUGIN_REQUESTING_KOTLIN_GRADLE_MODEL_KEY =
    Key.create("IS_ANDROID_PLUGIN_REQUESTING_KOTLIN_GRADLE_MODEL_KEY");

  private static final Key<Boolean> IS_ANDROID_PLUGIN_REQUESTING_KAPT_GRADLE_MODEL_KEY =
    Key.create("IS_ANDROID_PLUGIN_REQUESTING_KAPT_GRADLE_MODEL_KEY");

  @NotNull private final CommandLineArgs myCommandLineArgs;

  private @Nullable Project myProject;
  private final Map<GradleProjectPath, DataNode<? extends ModuleData>> myModuleDataByGradlePath = new LinkedHashMap<>();
  private final Map<String, GradleProjectPath> myGradlePathByModuleId = new LinkedHashMap<>();
  private IdeResolvedLibraryTable myResolvedModuleDependencies = null;
  private final List<Long> myKotlinCacheOriginIdentifiers = new ArrayList<>();

  public AndroidGradleProjectResolver() {
    this(new CommandLineArgs());
  }

  @NonInjectable
  @VisibleForTesting
  AndroidGradleProjectResolver(@NotNull CommandLineArgs commandLineArgs) {
    myCommandLineArgs = commandLineArgs;
  }

  @Override
  public void setProjectResolverContext(@NotNull ProjectResolverContext projectResolverContext) {
    myProject = projectResolverContext.getExternalSystemTaskId().findProject();
    // Setting this flag on the `projectResolverContext` tells the Kotlin IDE plugin that we are requesting `KotlinGradleModel` for all
    // modules. This is to be able to provide additional arguments to the model builder and avoid unnecessary processing of currently the
    // inactive build variants.
    projectResolverContext.putUserData(IS_ANDROID_PLUGIN_REQUESTING_KOTLIN_GRADLE_MODEL_KEY, true);
    // Similarly for KAPT.
    projectResolverContext.putUserData(IS_ANDROID_PLUGIN_REQUESTING_KAPT_GRADLE_MODEL_KEY, true);
    myResolvedModuleDependencies = null;
    myKotlinCacheOriginIdentifiers.clear();
    super.setProjectResolverContext(projectResolverContext);
  }

  @Override
  @Nullable
  public DataNode<ModuleData> createModule(@NotNull IdeaModule gradleModule, @NotNull DataNode<ProjectData> projectDataNode) {
    if (!isAndroidGradleProject()) {
      return nextResolver.createModule(gradleModule, projectDataNode);
    }

    IdeAndroidModels androidModels = resolverCtx.getExtraProject(gradleModule, IdeAndroidModels.class);
    DataNode<ModuleData> moduleDataNode = nextResolver.createModule(gradleModule, projectDataNode);
    if (moduleDataNode == null) {
      return null;
    }

    createAndAttachModelsToDataNode(projectDataNode, moduleDataNode, gradleModule, androidModels);
    patchLanguageLevels(moduleDataNode, gradleModule, androidModels != null ? androidModels.getAndroidProject() : null);

    registerModuleData(gradleModule, moduleDataNode);
    recordKotlinCacheOriginIdentifiers(gradleModule);

    return moduleDataNode;
  }

  private void registerModuleData(@NotNull IdeaModule gradleModule,
                                  DataNode<ModuleData> moduleDataNode) {
    ProjectIdentifier projectIdentifier = gradleModule.getGradleProject().getProjectIdentifier();

    Collection<DataNode<GradleSourceSetData>> sourceSetNodes = findAll(moduleDataNode, GradleSourceSetData.KEY);

    if (!sourceSetNodes.isEmpty()) {
      // ":" and similar holder projects do not have any source sets and should not be a target of module dependencies.
      sourceSetNodes.forEach(node -> {
        IdeModuleSourceSet sourceSet = ModuleUtil.getIdeModuleSourceSet(node.getData());

        if (sourceSet.getCanBeConsumed()) {
          GradleProjectPath gradleProjectPath = new GradleSourceSetProjectPath(
            toSystemIndependentName(projectIdentifier.getBuildIdentifier().getRootDir().getPath()),
            projectIdentifier.getProjectPath(),
            sourceSet
          );
          myModuleDataByGradlePath.put(gradleProjectPath, node);
          myGradlePathByModuleId.put(node.getData().getId(), gradleProjectPath);
        }
      });
    }
  }

  private void recordKotlinCacheOriginIdentifiers(@NotNull IdeaModule gradleModule) {
    var mppModel = resolverCtx.getExtraProject(gradleModule, KotlinMPPGradleModel.class);
    var kotlinModel = resolverCtx.getExtraProject(gradleModule, KotlinGradleModel.class);
    if (mppModel != null && kotlinModel != null) {
      if (mppModel.getPartialCacheAware().getCacheOriginIdentifier() != kotlinModel.getPartialCacheAware().getCacheOriginIdentifier()) {
        throw new IllegalStateException("Mpp and Kotlin model cacheOriginIdentifier's do not match");
      }
    }
    var cacheOriginIdentifier = 0L;
    if (mppModel != null) cacheOriginIdentifier = mppModel.getPartialCacheAware().getCacheOriginIdentifier();
    if (kotlinModel != null) cacheOriginIdentifier = kotlinModel.getPartialCacheAware().getCacheOriginIdentifier();
    if (cacheOriginIdentifier != 0L) {
      myKotlinCacheOriginIdentifiers.add(cacheOriginIdentifier);
    }
  }

  private void patchLanguageLevels(DataNode<ModuleData> moduleDataNode,
                                   @NotNull IdeaModule gradleModule,
                                   @Nullable IdeAndroidProject androidProject) {
    DataNode<JavaModuleData> javaModuleData = find(moduleDataNode, JavaModuleData.KEY);
    if (javaModuleData == null) {
      return;
    }
    JavaModuleData moduleData = javaModuleData.getData();
    if (androidProject != null) {
      LanguageLevel languageLevel = LanguageLevel.parse(androidProject.getJavaCompileOptions().getSourceCompatibility());
      moduleData.setLanguageLevel(languageLevel);
      moduleData.setTargetBytecodeVersion(androidProject.getJavaCompileOptions().getTargetCompatibility());
    }
    else {
      // Workaround BaseGradleProjectResolverExtension since the IdeaJavaLanguageSettings doesn't contain any information.
      // For this we set the language level based on the "main" source set of the module.
      // TODO: Remove once we have switched to module per source set. The base resolver should handle that correctly.
      ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
      if (externalProject != null) {
        // main should always exist, if it doesn't other things will fail before this.
        ExternalSourceSet externalSourceSet = externalProject.getSourceSets().get("main");
        if (externalSourceSet != null) {
          LanguageLevel languageLevel = LanguageLevel.parse(externalSourceSet.getSourceCompatibility());
          moduleData.setLanguageLevel(languageLevel);
          moduleData.setTargetBytecodeVersion(externalSourceSet.getTargetCompatibility());
        }
      }
    }
  }

  @Override
  public void populateModuleCompileOutputSettings(@NotNull IdeaModule gradleModule,
                                                  @NotNull DataNode<ModuleData> ideModule) {
    super.populateModuleCompileOutputSettings(gradleModule, ideModule);
    CompilerOutputUtilKt.setupCompilerOutputPaths(ideModule);
  }

  @Override
  public @NotNull Set<Class<?>> getToolingExtensionsClasses() {
    return ImmutableSet.of(KaptModelBuilderService.class, Unit.class);
  }

  /**
   * Creates and attaches the following models to the moduleNode depending on the type of module:
   * <ul>
   *   <li>GradleAndroidModel</li>
   *   <li>NdkModuleModel</li>
   *   <li>GradleModuleModel</li>
   *   <li>JavaModuleModel</li>
   * </ul>
   *
   * @param moduleNode    the module node to attach the models to
   * @param gradleModule  the module in question
   * @param androidModels the android project models obtained from this module (null is none found)
   */
  private void createAndAttachModelsToDataNode(@NotNull DataNode<ProjectData> projectDataNode,
                                               @NotNull DataNode<ModuleData> moduleNode,
                                               @NotNull IdeaModule gradleModule,
                                               @Nullable IdeAndroidModels androidModels) {
    String moduleName = moduleNode.getData().getInternalName();
    File rootModulePath = FilePaths.stringToFile(moduleNode.getData().getLinkedExternalProjectPath());

    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    KaptGradleModel kaptGradleModel =
      (androidModels != null) ? androidModels.getKaptGradleModel() : resolverCtx.getExtraProject(gradleModule, KaptGradleModel.class);
    GradlePluginModel gradlePluginModel = resolverCtx.getExtraProject(gradleModule, GradlePluginModel.class);
    BuildScriptClasspathModel buildScriptClasspathModel = resolverCtx.getExtraProject(gradleModule, BuildScriptClasspathModel.class);

    GradleAndroidModel androidModel = null;
    NdkModuleModel ndkModuleModel = null;
    GradleModuleModel gradleModel = null;
    Collection<IdeSyncIssue> issueData = null;

    if (androidModels != null) {
      androidModel = createGradleAndroidModel(moduleName, rootModulePath, androidModels);
      issueData = androidModels.getSyncIssues();
      String ndkModuleName = moduleName + "." + ModuleUtil.getModuleName(androidModel.getMainArtifactCore().getName());
      ndkModuleModel = maybeCreateNdkModuleModel(ndkModuleName, rootModulePath, androidModels);
    }

    Collection<String> gradlePluginList = (gradlePluginModel == null) ? ImmutableList.of() : gradlePluginModel.getGradlePluginList();
    File gradleSettingsFile = findGradleSettingsFile(rootModulePath);
    boolean hasArtifactsOrNoRootSettingsFile = !(gradleSettingsFile.isFile() && !hasArtifacts(externalProject));

    if (hasArtifactsOrNoRootSettingsFile || androidModel != null) {
      gradleModel =
        createGradleModuleModel(moduleName,
                                gradleModule,
                                androidModels == null ? null : androidModels.getAndroidProject().getAgpVersion(),
                                buildScriptClasspathModel,
                                gradlePluginList);
    }

    if (gradleModel != null) {
      moduleNode.createChild(GRADLE_MODULE_MODEL, gradleModel);
    }
    if (androidModel != null) {
      moduleNode.createChild(ANDROID_MODEL, androidModel);
    }
    if (ndkModuleModel != null) {
      moduleNode.createChild(NDK_MODEL, ndkModuleModel);
    }
    if (issueData != null) {
      issueData.forEach(it -> moduleNode.createChild(SYNC_ISSUE, it));
    }
    // We also need to patch java modules as we disabled the kapt resolver.
    // Setup Kapt this functionality should be done by KaptProjectResovlerExtension if possible.
    // If we have module per sourceSet turned on we need to fill in the GradleSourceSetData for each of the artifacts.
    if (androidModel != null) {
      IdeVariantCore variant = androidModel.getSelectedVariantCore();
      GradleSourceSetData prodModule = createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, variant.getMainArtifact().getName(),
                                                                             null
      );
      IdeBaseArtifactCore unitTest = variant.getUnitTestArtifact();
      if (unitTest != null) {
        createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, unitTest.getName(), prodModule);
      }
      IdeBaseArtifactCore androidTest = variant.getAndroidTestArtifact();
      if (androidTest != null) {
        createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, androidTest.getName(), prodModule);
      }
      IdeBaseArtifactCore testFixtures = variant.getTestFixturesArtifact();
      if (testFixtures != null) {
        createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, testFixtures.getName(), prodModule);
      }

     // Setup testData nodes for testing sources used by Gradle test runners.
      createAndSetupTestDataNode(moduleNode, androidModel);
    }

    patchMissingKaptInformationOntoModelAndDataNode(androidModel, moduleNode, kaptGradleModel);

    // Populate extra things
    populateAdditionalClassifierArtifactsModel(gradleModule);
  }

  @NotNull
  private static GradleModuleModel createGradleModuleModel(String moduleName,
                                                           @NotNull IdeaModule gradleModule,
                                                           @Nullable String modelVersionString,
                                                           BuildScriptClasspathModel buildScriptClasspathModel,
                                                           Collection<String> gradlePluginList) {
    File buildScriptPath;
    try {
      buildScriptPath = gradleModule.getGradleProject().getBuildScript().getSourceFile();
    }
    catch (UnsupportedOperationException e) {
      buildScriptPath = null;
    }

    return new GradleModuleModel(
      moduleName,
      gradleModule.getGradleProject(),
      gradlePluginList,
      buildScriptPath,
      (buildScriptClasspathModel == null) ? null : buildScriptClasspathModel.getGradleVersion(),
      modelVersionString
    );
  }

  @Nullable
  private static NdkModuleModel maybeCreateNdkModuleModel(@NotNull String moduleName,
                                                          @NotNull File rootModulePath,
                                                          @NotNull IdeAndroidModels ideModels) {
    // Prefer V2 NativeModule if available
    String selectedAbiName = ideModels.getSelectedAbiName();
    // If there are models we have a selected ABI name.
    if (selectedAbiName == null) return null;
    if (ideModels.getV2NativeModule() != null) {
      return new NdkModuleModel(moduleName,
                                rootModulePath,
                                ideModels.getSelectedVariantName(),
                                selectedAbiName,
                                new V2NdkModel(ideModels.getAndroidProject().getAgpVersion(), ideModels.getV2NativeModule()));
    }
    // V2 model not available, fallback to V1 model.
    if (ideModels.getV1NativeProject() != null) {
      List<IdeNativeVariantAbi> ideNativeVariantAbis = new ArrayList<>();
      if (ideModels.getV1NativeVariantAbi() != null) {
        ideNativeVariantAbis.add(ideModels.getV1NativeVariantAbi());
      }

      return new NdkModuleModel(moduleName,
                                rootModulePath,
                                ideModels.getSelectedVariantName(),
                                selectedAbiName,
                                ideModels.getV1NativeProject(),
                                ideNativeVariantAbis);
    }
    return null;
  }

  @NotNull
  private static GradleAndroidModel createGradleAndroidModel(String moduleName,
                                                             File rootModulePath,
                                                             @NotNull IdeAndroidModels ideModels) {

    return GradleAndroidModel.create(moduleName,
                                     rootModulePath,
                                     ideModels.getAndroidProject(),
                                     ideModels.getFetchedVariants(),
                                     ideModels.getSelectedVariantName());
  }

  /**
   * Get test tasks for a given android model.
   *
   * @return the test task for the module. This does not include the full task path, but only the task name.
   * The full task path will be configured later at the execution level in the Gradle producers.
   */
  static private String getTasksFromAndroidModuleData(@NotNull GradleAndroidModel androidModuleModel) {
    final String variant = androidModuleModel.getSelectedVariantCore().getName();
    return StringHelper.appendCapitalized("test", variant, "unitTest");
  }

  @SuppressLint("NewApi")
  private void createAndSetupTestDataNode(@NotNull DataNode<ModuleData> moduleDataNode,
                                          @NotNull GradleAndroidModel gradleAndroidModel) {
    // Get the unit test task for the current module.
    String testTaskName = getTasksFromAndroidModuleData(gradleAndroidModel);
    ModuleData moduleData = moduleDataNode.getData();
    String gradlePath = GradleProjectResolverUtil.getGradlePath(moduleData);
    String compositeBuildGradlePath = GradleModuleDataKt.getCompositeBuildGradlePath(moduleData);
    String fullGradlePath = compositeBuildGradlePath + gradlePath;

    Set<String> sourceFolders = new HashSet<>();
    for (IdeSourceProvider sourceProvider : gradleAndroidModel.getTestSourceProviders(IdeArtifactName.UNIT_TEST)) {
      for (File sourceFolder : getAllSourceFolders(sourceProvider)) {
        sourceFolders.add(sourceFolder.getPath());
      }
    }
    String taskNamePrefix = fullGradlePath.equals(":") ? fullGradlePath : fullGradlePath + ":";
    TestData testData = new TestData(GradleConstants.SYSTEM_ID, testTaskName, taskNamePrefix + testTaskName, sourceFolders);
    moduleDataNode.createChild(ProjectKeys.TEST, testData);
  }

  private GradleSourceSetData createAndSetupGradleSourceSetDataNode(@NotNull DataNode<ModuleData> parentDataNode,
                                                                    @NotNull IdeaModule gradleModule,
                                                                    @NotNull IdeArtifactName artifactName,
                                                                    @Nullable GradleSourceSetData productionModule) {
    String moduleId = computeModuleIdForArtifact(resolverCtx, gradleModule, artifactName);
    String readableArtifactName = ModuleUtil.getModuleName(artifactName);
    String moduleExternalName = gradleModule.getName() + ":" + readableArtifactName;
    String moduleInternalName =
      parentDataNode.getData().getInternalName() + "." + readableArtifactName;

    GradleSourceSetData sourceSetData =
      new GradleSourceSetData(moduleId, moduleExternalName, moduleInternalName, parentDataNode.getData().getModuleFileDirectoryPath(),
                              parentDataNode.getData().getLinkedExternalProjectPath());

    if (productionModule != null) {
      sourceSetData.setProductionModuleId(productionModule.getInternalName());
    }

    parentDataNode.createChild(GradleSourceSetData.KEY, sourceSetData);
    return sourceSetData;
  }

  private static String computeModuleIdForArtifact(@NotNull ProjectResolverContext resolverCtx,
                                                   @NotNull IdeaModule gradleModule,
                                                   @NotNull IdeArtifactName artifactName) {
    return getModuleId(resolverCtx, gradleModule) + ":" + ModuleUtil.getModuleName(artifactName);
  }

  /**
   * Adds the Kapt generated source directories to Android models generated source folders and sets up the kapt generated class library
   * for both Android and non-android modules.
   * <p>
   * This should probably not be done here. If we need this information in the Android model then this should
   * be the responsibility of the Android Gradle plugin. If we don't then this should be handled by the
   * KaptProjectResolverExtension, however as of now this class only works when module per source set is
   * enabled.
   */
  public static void patchMissingKaptInformationOntoModelAndDataNode(@Nullable GradleAndroidModel androidModel,
                                                                     @NotNull DataNode<ModuleData> moduleDataNode,
                                                                     @Nullable KaptGradleModel kaptGradleModel) {
    if (androidModel == null || kaptGradleModel == null || !kaptGradleModel.isEnabled()) {
      return;
    }

    kaptGradleModel.getSourceSets().forEach(sourceSet -> {
      Pair<IdeVariantCore, DataNode<GradleSourceSetData>> result = findVariantAndDataNode(sourceSet, androidModel, moduleDataNode);
      if (result == null) {
        // No artifact was found for the current source set
        return;
      }

      IdeVariantCore variant = result.first;
      if (variant.equals(androidModel.getSelectedVariantCore())) {
        File classesDirFile = sourceSet.getGeneratedClassesDirFile();
        addToNewOrExistingLibraryData(result.second, "kaptGeneratedClasses", Collections.singleton(classesDirFile), sourceSet.isTest());
      }
    });
  }

  private static void addToNewOrExistingLibraryData(@NotNull DataNode<GradleSourceSetData> moduleDataNode,
                                                    @NotNull String name,
                                                    @NotNull Set<File> files,
                                                    boolean isTest) {
    // Code adapted from KaptProjectResolverExtension
    LibraryData newLibrary = new LibraryData(GRADLE_SYSTEM_ID, name);
    LibraryData existingData = moduleDataNode.getChildren().stream().map(DataNode::getData).filter(
        (data) -> data instanceof LibraryDependencyData &&
                  newLibrary.getExternalName().equals(((LibraryDependencyData)data).getExternalName()))
      .map(data -> ((LibraryDependencyData)data).getTarget()).findFirst().orElse(null);

    if (existingData != null) {
      files.forEach((file) -> existingData.addPath(LibraryPathType.BINARY, file.getAbsolutePath()));
    }
    else {
      files.forEach((file) -> newLibrary.addPath(LibraryPathType.BINARY, file.getAbsolutePath()));
      LibraryDependencyData libraryDependencyData = new LibraryDependencyData(moduleDataNode.getData(), newLibrary, LibraryLevel.MODULE);
      libraryDependencyData.setScope(isTest ? DependencyScope.TEST : DependencyScope.COMPILE);
      moduleDataNode.createChild(LIBRARY_DEPENDENCY, libraryDependencyData);
    }
  }

  @Nullable
  private static Pair<IdeVariantCore, DataNode<GradleSourceSetData>> findVariantAndDataNode(@NotNull KaptSourceSetModel sourceSetModel,
                                                                                            @NotNull GradleAndroidModel androidModel,
                                                                                            @NotNull DataNode<ModuleData> moduleNode) {
    String sourceSetName = sourceSetModel.getSourceSetName();
    if (!sourceSetModel.isTest()) {
      @Nullable IdeVariantCore variant = androidModel.findVariantCoreByName(sourceSetName);
      return variant == null ? null : Pair.create(variant, findSourceSetDataForArtifact(moduleNode, variant.getMainArtifact()));
    }

    // Check if it's android test source set.
    String androidTestSuffix = "AndroidTest";
    if (sourceSetName.endsWith(androidTestSuffix)) {
      String variantName = sourceSetName.substring(0, sourceSetName.length() - androidTestSuffix.length());
      @Nullable IdeVariantCore variant = androidModel.findVariantCoreByName(variantName);
      IdeBaseArtifactCore artifact = variant == null ? null : variant.getAndroidTestArtifact();
      return artifact == null ? null : Pair.create(variant, findSourceSetDataForArtifact(moduleNode, artifact));
    }

    // Check if it's test fixtures source set.
    String testFixturesSuffix = "TestFixtures";
    if (sourceSetName.endsWith(testFixturesSuffix)) {
      String variantName = sourceSetName.substring(0, sourceSetName.length() - testFixturesSuffix.length());
      @Nullable IdeVariantCore variant = androidModel.findVariantCoreByName(variantName);
      IdeBaseArtifactCore artifact = variant == null ? null : variant.getTestFixturesArtifact();
      return artifact == null ? null : Pair.create(variant, findSourceSetDataForArtifact(moduleNode, artifact));
    }

    // Check if it's unit test source set.
    String unitTestSuffix = "UnitTest";
    if (sourceSetName.endsWith(unitTestSuffix)) {
      String variantName = sourceSetName.substring(0, sourceSetName.length() - unitTestSuffix.length());
      @Nullable IdeVariantCore variant = androidModel.findVariantCoreByName(variantName);
      IdeBaseArtifactCore artifact = variant == null ? null : variant.getUnitTestArtifact();
      return artifact == null ? null : Pair.create(variant, findSourceSetDataForArtifact(moduleNode, artifact));
    }

    return null;
  }

  private void populateAdditionalClassifierArtifactsModel(@NotNull IdeaModule gradleModule) {
    Project project = getProject();
    AdditionalClassifierArtifactsModel artifacts = resolverCtx.getExtraProject(gradleModule, AdditionalClassifierArtifactsModel.class);
    if (artifacts != null && project != null) {
      LibraryFilePaths.getInstance(project).populate(artifacts);
    }
  }

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    DataNode<GradleAndroidModel> GradleAndroidModelNode = ExternalSystemApiUtil.find(ideModule, AndroidProjectKeys.ANDROID_MODEL);
    // Only process android modules.
    if (GradleAndroidModelNode == null) {
      super.populateModuleContentRoots(gradleModule, ideModule);
      return;
    }

    nextResolver.populateModuleContentRoots(gradleModule, ideModule);

    ContentRootUtilKt.setupAndroidContentEntriesPerSourceSet(ideModule, GradleAndroidModelNode.getData());
  }

  private static boolean hasArtifacts(@Nullable ExternalProject externalProject) {
    return externalProject != null && !externalProject.getArtifacts().isEmpty();
  }

  @Override
  public void populateModuleDependencies(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule,
                                         @NotNull DataNode<ProjectData> ideProject) {
    DataNode<GradleAndroidModel> androidModelNode = ExternalSystemApiUtil.find(ideModule, AndroidProjectKeys.ANDROID_MODEL);
    // Don't process non-android modules here.
    if (androidModelNode == null) {
      super.populateModuleDependencies(gradleModule, ideModule, ideProject);
      return;
    }
    if (myResolvedModuleDependencies == null) {
      IdeUnresolvedLibraryTableImpl ideLibraryTable = resolverCtx.getModels().getModel(IdeUnresolvedLibraryTableImpl.class);
      if (ideLibraryTable == null) {
        throw new IllegalStateException("IdeLibraryTableImpl is unavailable in resolverCtx when GradleAndroidModel's are present");
      }
      myResolvedModuleDependencies = buildResolvedLibraryTable(ideProject, ideLibraryTable);
      ideProject.createChild(
        AndroidProjectKeys.IDE_LIBRARY_TABLE,
        myResolvedModuleDependencies
      );
    }

    androidModelNode.getData()
      .setResolver(IdeLibraryModelResolverImpl.fromLibraryTable(myResolvedModuleDependencies));

    // Call all the other resolvers to ensure that any dependencies that they need to provide are added.
    nextResolver.populateModuleDependencies(gradleModule, ideModule, ideProject);

    AdditionalClassifierArtifactsModel additionalArtifacts =
      resolverCtx.getExtraProject(gradleModule, AdditionalClassifierArtifactsModel.class);
    // TODO: Log error messages from additionalArtifacts.

    GradleExecutionSettings settings = resolverCtx.getSettings();
    GradleExecutionWorkspace workspace = (settings == null) ? null : settings.getExecutionWorkspace();

    Map<String, AdditionalClassifierArtifacts> additionalArtifactsMap;
    if (additionalArtifacts != null) {
      additionalArtifactsMap =
        additionalArtifacts
          .getArtifacts()
          .stream()
          .collect(
            Collectors.toMap((k) -> String.format("%s:%s:%s", k.getId().getGroupId(), k.getId().getArtifactId(), k.getId().getVersion()),
                             (k) -> k
            ));
    }
    else {
      additionalArtifactsMap = ImmutableMap.of();
    }


    Project project = getProject();
    LibraryFilePaths libraryFilePaths;
    if (project == null) {
      libraryFilePaths = null;
    }
    else {
      libraryFilePaths = LibraryFilePaths.getInstance(project);
    }

    Function<String, AdditionalArtifactsPaths> artifactLookup = (artifactId) -> {
      // First check to see if we just obtained any paths from Gradle. Since we don't request all the paths this can be null
      // or contain an incomplete set of entries. In order to complete this set we need to obtains the reminder from LibraryFilePaths cache.
      AdditionalClassifierArtifacts artifacts = additionalArtifactsMap.get(artifactId);
      if (artifacts != null) {
        return new AdditionalArtifactsPaths(artifacts.getSources(), artifacts.getJavadoc(), artifacts.getSampleSources());
      }

      // Then check to see whether we already have the library cached.
      if (libraryFilePaths != null) {
        ArtifactPaths cachedPaths = libraryFilePaths.getCachedPathsForArtifact(artifactId);
        if (cachedPaths != null) {
          return new AdditionalArtifactsPaths(cachedPaths.sources, cachedPaths.javaDoc, cachedPaths.sampleSource);
        }
      }
      return null;
    };

    DependencyUtilKt.setupAndroidDependenciesForMpss(
      ideModule,
      gradleProjectPath -> {
        DataNode<? extends ModuleData> node = myModuleDataByGradlePath.get(gradleProjectPath);
        if (node == null) return null;
        return node.getData();
      },
      artifactLookup::apply,
      androidModelNode.getData(),
      androidModelNode.getData().getSelectedVariant(),
      project
    );
  }

  private @NotNull IdeResolvedLibraryTable buildResolvedLibraryTable(@NotNull DataNode<ProjectData> ideProject,
                                                                     @NotNull IdeUnresolvedLibraryTable ideLibraryTable) {
    Map<String, String> artifactToModuleIdMap = ideProject.getUserData(CONFIGURATION_ARTIFACTS);
    Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> resolvedSourceSets = ideProject.getUserData(RESOLVED_SOURCE_SETS);
    checkNotNull(artifactToModuleIdMap, "Implementation of GradleProjectResolver has changed");
    checkNotNull(resolvedSourceSets, "Implementation of GradleProjectResolver has changed");
    return new ResolvedLibraryTableBuilder(
      myGradlePathByModuleId::get,
      myModuleDataByGradlePath::get,
      artifact -> resolveArtifact(artifactToModuleIdMap, artifact)
    ).buildResolvedLibraryTable(ideLibraryTable);
  }

  private @Nullable GradleProjectPath resolveArtifact(@NotNull Map<String, String> artifactToModuleIdMap, @NotNull File artifact) {
    return myGradlePathByModuleId.get(artifactToModuleIdMap.get(ExternalSystemApiUtil.toCanonicalPath(artifact.getPath())));
  }

  @SuppressWarnings("UnstableApiUsage")
  @Override
  public void resolveFinished(@NotNull DataNode<ProjectData> projectDataNode) {
    preserveKotlinUserDataInDataNodes(projectDataNode, myKotlinCacheOriginIdentifiers);
    disableOrphanModuleNotifications();
  }

  /**
   * A method that resets the configuration of "Build sync orphan modules" notification group to "not display" and "not log"
   * in order to prevent a notification which allows users to restore the removed module as a non-Gradle module. Non-Gradle modules
   * are not supported by AS in Gradle projects.
   */
  private static void disableOrphanModuleNotifications() {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      NotificationsConfiguration
        .getNotificationsConfiguration()
        .changeSettings(BUILD_SYNC_ORPHAN_MODULES_NOTIFICATION_GROUP_NAME, NotificationDisplayType.NONE, false, false);
    }
  }

  // Indicates it is an "Android" project if at least one module has an AndroidProject.
  private boolean isAndroidGradleProject() {
    return resolverCtx.hasModulesWithModel(IdeAndroidModels.class);
  }

  @Override
  public void populateProjectExtraModels(@NotNull IdeaProject gradleProject, @NotNull DataNode<ProjectData> projectDataNode) {
    Project project = getProject();
    if (project != null) {
      attachVariantsSavedFromPreviousSyncs(project, projectDataNode);
    }

    IdeAndroidSyncError syncError = resolverCtx.getModels().getModel(IdeAndroidSyncError.class);
    if (syncError != null) {
      throw ideAndroidSyncErrorToException(syncError);
    }

    // This is used in the special mode sync to fetch additional native variants.
    for (IdeaModule gradleModule : gradleProject.getModules()) {
      IdeAndroidNativeVariantsModels nativeVariants = resolverCtx.getExtraProject(gradleModule, IdeAndroidNativeVariantsModels.class);
      if (nativeVariants != null) {
        projectDataNode.createChild(NATIVE_VARIANTS,
                                    new IdeAndroidNativeVariantsModelsWrapper(
                                      GradleProjectResolverUtil.getModuleId(resolverCtx, gradleModule),
                                      nativeVariants
                                    ));
      }
    }
    if (isAndroidGradleProject()) {
      projectDataNode.createChild(PROJECT_CLEANUP_MODEL, ProjectCleanupModel.getInstance());
    }

    super.populateProjectExtraModels(gradleProject, projectDataNode);
  }

  /**
   * This method is not used. Its functionality is only present when not using a
   * {@link ProjectImportModelProvider}. See: {@link #getModelProvider}
   */
  @Override
  @NotNull
  public Set<Class<?>> getExtraProjectModelClasses() {
    throw new UnsupportedOperationException("getExtraProjectModelClasses() is not used when getModelProvider() is overridden.");
  }

  @NotNull
  @Override
  public ProjectImportModelProvider getModelProvider() {
    return configureAndGetExtraModelProvider(resolverCtx);
  }

  @Override
  public void preImportCheck() {
    // Don't run pre-import checks for the buildSrc project.
    if (resolverCtx.getBuildSrcGroup() != null) {
      return;
    }

    simulateRegisteredSyncError();

    String projectPath = resolverCtx.getProjectPath();
    syncAndroidSdks(SdkSync.getInstance(), projectPath);

    Project project = getProject();
    GradleExecutionSettings settings = resolverCtx.getSettings();
    if (settings != null) { // In Android Studio we always have settings.
      JdkImportCheck.validateProjectGradleJdk(settings.getJavaHome());
    }
    validateGradleWrapper(projectPath);

    displayInternalWarningIfForcedUpgradesAreDisabled();
    expireProjectUpgradeNotifications(project);

    if (IdeInfo.getInstance().isAndroidStudio()) {
      // Don't execute in IDEA in order to avoid conflicting behavior with IDEA's proxy support in gradle project.
      // (https://youtrack.jetbrains.com/issue/IDEA-245273, see BaseResolverExtension#getExtraJvmArgs)
      // To be discussed with the AOSP team to find a way to unify configuration across IDEA and AndroidStudio.
      cleanUpHttpProxySettings();
    }
  }

  @Override
  @NotNull
  public List<Pair<String, String>> getExtraJvmArgs() {
    if (isInProcessMode(GRADLE_SYSTEM_ID)) {
      List<Pair<String, String>> args = new ArrayList<>();

      if (IdeInfo.getInstance().isAndroidStudio()) {
        // Inject javaagent args.
        TraceSyncUtil.addTraceJvmArgs(args);
      }
      else {
        LocalProperties localProperties = getLocalProperties();
        if (localProperties.getAndroidSdkPath() == null) {
          File androidHomePath = IdeSdks.getInstance().getAndroidSdkPath();
          // In Android Studio, the Android SDK home path will never be null. It may be null when running in IDEA.
          if (androidHomePath != null) {
            args.add(Pair.create(ANDROID_HOME_JVM_ARG, androidHomePath.getPath()));
          }
        }
      }
      return args;
    }
    return emptyList();
  }

  @NotNull
  private LocalProperties getLocalProperties() {
    File projectDir = FilePaths.stringToFile(resolverCtx.getProjectPath());
    try {
      return new LocalProperties(projectDir);
    }
    catch (IOException e) {
      String msg = String.format("Unable to read local.properties file in project '%1$s'", projectDir.getPath());
      throw new ExternalSystemException(msg, e);
    }
  }

  @Override
  @NotNull
  public List<String> getExtraCommandLineArgs() {
    Project project = getProject();
    return myCommandLineArgs.get(project);
  }

  @NotNull
  @Override
  public ExternalSystemException getUserFriendlyError(@Nullable BuildEnvironment buildEnvironment,
                                                      @NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    String msg = error.getMessage();
    if (msg != null) {
      Throwable rootCause = getRootCause(error);
      if (rootCause instanceof ClassNotFoundException) {
        msg = rootCause.getMessage();
        // Project is using an old version of Gradle (and most likely an old version of the plug-in.)
        if (isUsingUnsupportedGradleVersion(msg)) {
          AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder();
          // @formatter:off
          event.setCategory(GRADLE_SYNC)
               .setKind(GRADLE_SYNC_FAILURE_DETAILS)
               .setGradleSyncFailure(GradleSyncFailure.UNSUPPORTED_GRADLE_VERSION);
          // @formatter:on;
          UsageTrackerUtils.withProjectId(event, getProject());
          UsageTracker.log(event);

          return new ExternalSystemException("The project is using an unsupported version of Gradle.");
        }
      }
      else if (rootCause instanceof ZipException) {
        if (msg.startsWith(COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX)) {
          return new ExternalSystemException(msg);
        }
      }
    }
    return super.getUserFriendlyError(buildEnvironment, error, projectPath, buildFilePath);
  }

  private static boolean isUsingUnsupportedGradleVersion(@Nullable String errorMessage) {
    return "org.gradle.api.artifacts.result.ResolvedComponentResult".equals(errorMessage) ||
           "org.gradle.api.artifacts.result.ResolvedModuleVersionResult".equals(errorMessage);
  }

  public static boolean shouldDisableForceUpgrades() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return true;
    if (SystemProperties.getBooleanProperty("studio.skip.agp.upgrade", false)) return true;
    if (StudioFlags.DISABLE_FORCED_UPGRADES.get()) return true;
    return false;
  }

  private void displayInternalWarningIfForcedUpgradesAreDisabled() {
    if (DISABLE_FORCED_UPGRADES.get()) {
      Project project = getProject();
      if (project != null) {
        displayForceUpdatesDisabledMessage(project);
      }
    }
  }

  private void cleanUpHttpProxySettings() {
    Project project = getProject();
    if (project != null) {
      ApplicationManager.getApplication().invokeAndWait(() -> HttpProxySettingsCleanUp.cleanUp(project));
    }
  }

  @Override
  public void enhanceRemoteProcessing(@NotNull SimpleJavaParameters parameters) {
    PathsList classPath = parameters.getClassPath();
    classPath.add(getJarPathForClass(getClass()));
    classPath.add(getJarPathForClass(Revision.class));
    classPath.add(getJarPathForClass(AndroidGradleSettings.class));
  }

  @Nullable
  public static String getModuleIdForModule(@NotNull Module module) {
    ExternalSystemModulePropertyManager propertyManager = ExternalSystemModulePropertyManager.getInstance(module);
    String rootProjectPath = propertyManager.getRootProjectPath();
    if (rootProjectPath != null) {
      String gradlePath = propertyManager.getLinkedProjectId();
      if (gradlePath != null) {
        return createUniqueModuleId(rootProjectPath, gradlePath);
      }
    }
    return null;
  }

  @NotNull private static final Key<VariantProjectDataNodes> VARIANTS_SAVED_FROM_PREVIOUS_SYNCS =
    new Key<>("variants.saved.from.previous.syncs");

  public static void saveCurrentlySyncedVariantsForReuse(@NotNull Project project) {
    @Nullable ExternalProjectInfo data =
      ProjectDataManager.getInstance().getExternalProjectData(project, GradleConstants.SYSTEM_ID, project.getBasePath());
    if (data == null) return;
    @Nullable DataNode<ProjectData> currentDataNodes = data.getExternalProjectStructure();
    if (currentDataNodes == null) return;

    project.putUserData(
      AndroidGradleProjectResolver.VARIANTS_SAVED_FROM_PREVIOUS_SYNCS,
      VariantProjectDataNodes.Companion.collectCurrentAndPreviouslyCachedVariants(currentDataNodes));
  }

  public static void clearVariantsSavedForReuse(@NotNull Project project) {
    project.putUserData(
      AndroidGradleProjectResolver.VARIANTS_SAVED_FROM_PREVIOUS_SYNCS,
      null);
  }

  @VisibleForTesting
  public static void attachVariantsSavedFromPreviousSyncs(Project project, @NotNull DataNode<ProjectData> projectDataNode) {
    @Nullable VariantProjectDataNodes
      projectUserData = project.getUserData(VARIANTS_SAVED_FROM_PREVIOUS_SYNCS);
    if (projectUserData != null) {
      projectDataNode.createChild(CACHED_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS, projectUserData);
    }
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  private static Collection<File> getAllSourceFolders(IdeSourceProvider provider) {
    return Stream.of(
      provider.getJavaDirectories(),
      provider.getKotlinDirectories(),
      provider.getResDirectories(),
      provider.getAidlDirectories(),
      provider.getRenderscriptDirectories(),
      provider.getAssetsDirectories(),
      provider.getJniLibsDirectories()
    ).flatMap(Collection::stream).collect(Collectors.toList());
  }
}
