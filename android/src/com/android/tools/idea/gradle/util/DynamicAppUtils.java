/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.ApkFileUnit;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Various utility methods to navigate through various parts (dynamic features, base split, etc.)
 * of dynamic apps.
 */
public class DynamicAppUtils {
  /**
   * Returns the list of dynamic feature {@link Module modules} that depend on this base module.
   */
  @NotNull
  public static List<Module> getDependentFeatureModulesForBase(@NotNull Module module) {
    AndroidModuleModel androidModule = AndroidModuleModel.get(module);
    if (androidModule == null) {
      return ImmutableList.of();
    }
    return getDependentFeatureModulesForBase(module.getProject(), androidModule.getAndroidProject());
  }

  /**
   * Returns the Base Module of the specified dynamic feature {@link Module module}, or null if none is found.
   */
  @Nullable
  public static Module getBaseFeature(@NotNull Module module) {
    String gradlePath = getGradlePath(module);
    if (gradlePath == null) {
      return null;
    }

    return ProjectSystemUtil.getAndroidFacets(module.getProject()).stream()
      .filter(facet -> {
        AndroidModuleModel baseModel = AndroidModuleModel.get(facet);
        return baseModel != null && baseModel.getAndroidProject().getDynamicFeatures().contains(gradlePath);
      })
      .findFirst()
      .map(AndroidFacet::getHolderModule)
      .orElse(null);
  }

  /**
   * Returns the list of dynamic feature {@link Module modules} that depend on this base module.
   */
  @NotNull
  public static List<Module> getDependentFeatureModulesForBase(@NotNull Project project, @NotNull IdeAndroidProject androidProject) {
    Map<String, Module> featureMap = getDynamicFeaturesMap(project);
    return androidProject.getDynamicFeatures().stream()
      .map(featurePath -> featureMap.get(featurePath))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  /**
   * Returns the list of {@link Module modules} to build for a given base module.
   */
  @NotNull
  public static List<Module> getModulesToBuild(@NotNull Module module) {
    return Stream
      .concat(Stream.of(module), getDependentFeatureModulesForBase(module).stream())
      .collect(Collectors.toList());
  }

  /**
   * Returns the list of {@link Module} instances corresponding to feature modules (legacy or dynamic)
   * that depend on the given module.
   *
   * <p>Returns an empty list if feature-on-feature support is disabled.
   *
   * @param featureModule an instant or dynamic feature
   */
  @NotNull
  public static List<Module> getFeatureModulesDependingOnFeature(@NotNull Module featureModule) {
    if (!StudioFlags.SUPPORT_FEATURE_ON_FEATURE_DEPS.get()) {
      return ImmutableList.of();
    }

    // We need to remove modules that belong to the same Gradle project as the feature module e.g the  androidTest and unitTest modules
    return selectFeatureModules(removeModulesIntheSameGradleProject(
      ModuleManager.getInstance(featureModule.getProject()).getModuleDependentModules(ModuleSystemUtil.getMainModule(featureModule))
        .stream(), featureModule));
  }

  /**
   * Returns the list of {@link Module} instances corresponding to feature modules (legacy or dynamic)
   * on which the given feature module depends.
   *
   * <p>Returns an empty list if feature-on-feature support is disabled.
   *
   * @param featureModule an instant or dynamic feature
   */
  @NotNull
  public static List<Module> getFeatureModuleDependenciesForFeature(@NotNull Module featureModule) {
    if (!StudioFlags.SUPPORT_FEATURE_ON_FEATURE_DEPS.get()) {
      return ImmutableList.of();
    }

    // We need to remove modules that belong to the same Gradle project as the feature module e.g the  androidTest and unitTest modules
    return selectFeatureModules(removeModulesIntheSameGradleProject(
      Stream.of(ModuleRootManager.getInstance(ModuleSystemUtil.getMainModule(featureModule)).getDependencies()), featureModule));
  }

  @NotNull
  public static Stream<Module> removeModulesIntheSameGradleProject(@NotNull Stream<Module> modules, @NotNull Module moduleOfProjectToRemove) {
    return modules.filter(m -> ModuleSystemUtil.getHolderModule(m) != ModuleSystemUtil.getHolderModule(moduleOfProjectToRemove));
  }

  /**
   * Returns {@code true} if the base module is instant enabled
   */
  public static boolean baseIsInstantEnabled(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidModuleModel model = AndroidModuleModel.get(module);
      if (model != null && model.isBaseSplit()) {
        if (model.getSelectedVariant().getInstantAppCompatible()) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public static List<Module> getAppHolderModulesSupportingBundleTask(@NotNull Project project) {
    return ProjectStructure.getInstance(project).getAppHolderModules().stream()
      .filter(DynamicAppUtils::supportsBundleTask)
      .collect(Collectors.toList());
  }

  /**
   * Returns {@code true} if the module supports the "bundle" task, i.e. if the Gradle
   * plugin associated to the module is of high enough version number and supports
   * the "Bundle" tool.
   */
  public static boolean supportsBundleTask(@NotNull Module module) {
    AndroidModuleModel androidModule = AndroidModuleModel.get(module);
    if (androidModule == null) {
      return false;
    }
    return !StringUtil.isEmpty(androidModule.getSelectedVariant().getMainArtifact().getBuildInformation().getBundleTaskName());
  }

  /**
   * Returns {@code true} if a module should be built using the "select apks from bundle" task
   */
  public static boolean useSelectApksFromBundleBuilder(@NotNull Module module,
                                                       @NotNull AndroidRunConfigurationBase configuration,
                                                       @Nullable AndroidVersion minTargetDeviceVersion) {
    boolean alwaysDeployApkFromBundle = false;
    boolean deployForTests = configuration instanceof AndroidTestRunConfiguration;

    if (configuration instanceof AndroidRunConfiguration) {
      AndroidRunConfiguration androidConfiguration = (AndroidRunConfiguration)configuration;
      alwaysDeployApkFromBundle = AndroidRunConfiguration.shouldDeployApkFromBundle(androidConfiguration);
    }

    return useSelectApksFromBundleBuilder(module, alwaysDeployApkFromBundle, deployForTests, minTargetDeviceVersion);
  }

  public static boolean useSelectApksFromBundleBuilder(@NotNull Module module,
                                                       boolean alwaysDeployApkFromBundle,
                                                       boolean deployForTests,
                                                       @Nullable AndroidVersion minTargetDeviceVersion) {
    if (alwaysDeployApkFromBundle) {
      return true;
    }
    // If any device is pre-L *and* module has a dynamic feature, we need to use the bundle tool
    if (minTargetDeviceVersion != null && minTargetDeviceVersion.getFeatureLevel() < AndroidVersion.VersionCodes.LOLLIPOP &&
        !getDependentFeatureModulesForBase(module).isEmpty()) {
      return true;
    }

    // Instrumented test support for Dynamic Features
    if (deployForTests) {
      AndroidModuleModel androidModuleModel = AndroidModuleModel.get(module);
      if (androidModuleModel != null) {
        if (androidModuleModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Returns {@code true} if we should collect the list of languages of the target devices
   * when deploying an app.
   */
  public static boolean shouldCollectListOfLanguages(@NotNull Module module,
                                                     @NotNull AndroidRunConfigurationBase configuration,
                                                     @Nullable AndroidVersion minTargetDeviceVersion) {
    // Don't collect if not using the bundle tool
    if (!useSelectApksFromBundleBuilder(module, configuration, minTargetDeviceVersion)) {
      return false;
    }

    // Only collect if all devices are L or later devices, because pre-L devices don't support split apks, meaning
    // they don't support install on demand, meaning all languages should be installed.
    return minTargetDeviceVersion != null && minTargetDeviceVersion.getFeatureLevel() >= AndroidVersion.VersionCodes.LOLLIPOP;
  }

  /**
   * Returns the list of dynamic feature {@link Module modules} that depend on this base module and are instant app compatible.
   */
  @NotNull
  public static List<Module> getDependentInstantFeatureModules(@NotNull Module module) {
    AndroidModuleModel androidModule = AndroidModuleModel.get(module);
    if (androidModule == null) {
      return ImmutableList.of();
    }
    return getDependentInstantFeatureModules(module.getProject(), androidModule.getAndroidProject());
  }

  /**
   * Returns the list of dynamic feature {@link Module modules} that depend on this base module and are instant app compatible.
   */
  @NotNull
  public static List<Module> getDependentInstantFeatureModules(@NotNull Project project, @NotNull IdeAndroidProject androidProject) {
    Map<String, Module> featureMap = getDynamicFeaturesMap(project);
    return androidProject.getDynamicFeatures().stream()
      .map(featurePath -> featureMap.get(featurePath))
      .filter(Objects::nonNull)
      .filter(f -> AndroidModuleModel.get(f).getSelectedVariant().getInstantAppCompatible())
      .collect(Collectors.toList());
  }

  public static boolean isFeatureEnabled(@NotNull List<String> myDisabledFeatures, @NotNull ApkFileUnit apkFileUnit) {
    return myDisabledFeatures.stream().noneMatch(m -> featureNameEquals(apkFileUnit, m));
  }

  public static boolean featureNameEquals(@NotNull ApkFileUnit apkFileUnit, @NotNull String featureName) {
    return StringUtil.equals(featureName.replace('-', '_'), apkFileUnit.getModuleName());
  }

  @NotNull
  private static Map<String, Module> getDynamicFeaturesMap(@NotNull Project project) {
    return ProjectSystemUtil.getAndroidFacets(project).stream()
      .map(facet -> {
        // Check the module is a "dynamic feature"
        AndroidModuleModel model = AndroidModuleModel.get(facet);
        if (model == null) {
          return null;
        }
        if (model.getAndroidProject().getProjectType() != IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE) {
          return null;
        }
        String gradlePath = getGradlePath(facet.getHolderModule());
        if (gradlePath == null) {
          return null;
        }
        return Pair.create(gradlePath, facet.getHolderModule());
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toMap(p -> p.first, p -> p.second, DynamicAppUtils::handleModuleAmbiguity));
  }

  /**
   * Finds the modules in a stream that are either legacy or dynamic features. If there are multiple modules belonging to the same
   * dynamic feature (i.e Gradle Project) this method will only return the holder modules.
   */
  @NotNull
  private static List<Module> selectFeatureModules(Stream<Module> moduleStream) {
    return moduleStream.map(ModuleSystemUtil::getHolderModule).distinct().filter(module -> {
      AndroidModuleModel androidModuleModel = AndroidModuleModel.get(module);
      if (androidModuleModel == null) {
        return false;
      }
      IdeAndroidProjectType type = androidModuleModel.getAndroidProject().getProjectType();
      return type == IdeAndroidProjectType.PROJECT_TYPE_FEATURE || // Legacy
             type == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE;
    }).collect(Collectors.toList());
  }

  /**
   * Find the gradle path of the module
   *
   * @return The path of the specified module, or null if it can't retrieve it.
   */
  @Nullable
  private static String getGradlePath(@NotNull Module module) {
    GradleFacet facet = GradleFacet.getInstance(module);
    if (facet == null) {
      return null;
    }
    GradleModuleModel gradleModel = facet.getGradleModuleModel();
    if (gradleModel == null) {
      return null;
    }
    return gradleModel.getGradlePath();
  }

  @NotNull
  private static Module handleModuleAmbiguity(@NotNull Module m1, @NotNull Module m2) {
    getLogger().warn(String.format("Unexpected ambiguity processing modules: %s - %s", m1.getName(), m2.getName()));
    return m1;
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(DynamicAppUtils.class);
  }
}
