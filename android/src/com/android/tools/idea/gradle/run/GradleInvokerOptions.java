/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.run;

import com.android.ddmlib.IDevice;
import com.android.resources.Density;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.fd.FileChangeListener;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.fd.InstantRunUtils;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.GradleBuilds;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.DeviceFutures;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GradleInvokerOptions {
  private static final Logger LOG = Logger.getInstance(GradleInvokerOptions.class);

  @NotNull public final List<String> tasks;
  @Nullable public final BuildMode buildMode;
  @NotNull public final List<String> commandLineArguments;

  private GradleInvokerOptions(@NotNull List<String> tasks, @Nullable BuildMode buildMode, @NotNull List<String> commandLineArguments) {
    this.tasks = tasks;
    this.buildMode = buildMode;
    this.commandLineArguments = commandLineArguments;
  }

  public static GradleInvokerOptions create(@NotNull Project project,
                                            @Nullable DataContext context,
                                            @NotNull RunConfiguration configuration,
                                            @NotNull ExecutionEnvironment env,
                                            @Nullable String userGoal) {
    if (!StringUtil.isEmpty(userGoal)) {
      return new GradleInvokerOptions(Collections.singletonList(userGoal), null, Collections.<String>emptyList());
    }

    final Module[] modules = getModules(project, context, configuration);
    if (MakeBeforeRunTaskProvider.isUnitTestConfiguration(configuration)) {
      // Make sure all "intermediates/classes" directories are up-to-date.
      Module[] affectedModules = getAffectedModules(project, modules);
      BuildMode buildMode = BuildMode.COMPILE_JAVA;
      List<String> tasks = GradleInvoker.findTasksToExecute(affectedModules, buildMode, GradleInvoker.TestCompileType.JAVA_TESTS);
      return new GradleInvokerOptions(tasks, buildMode, Collections.<String>emptyList());
    }

    List<String> cmdLineArgs = Lists.newArrayList();
    List<String> tasks = Lists.newArrayList();

    // Inject instant run attributes
    // Note that these are specifically not injected for the unit test configurations above
    if (InstantRunSettings.isInstantRunEnabled(project)) {
      boolean cleanBuild = InstantRunUtils.needsCleanBuild(env);
      boolean incrementalBuild = !cleanBuild && InstantRunUtils.isIncrementalBuild(env);

      cmdLineArgs.add(getInstantDevProperty(project, incrementalBuild));
      cmdLineArgs.addAll(getDeviceSpecificArguments(getTargetDevices(env)));

      if (cleanBuild) {
        tasks.add(GradleBuilds.CLEAN_TASK_NAME);
        tasks.addAll(GradleInvoker.findTasksToExecute(ModuleManager.getInstance(project).getModules(), BuildMode.SOURCE_GEN,
                                                      GradleInvoker.TestCompileType.NONE));
      }
      else if (incrementalBuild) {
        Module module = modules[0];
        LOG.info(String.format("Module %1$s can be updated incrementally.", module.getName()));
        AndroidGradleModel model = AndroidGradleModel.get(module);
        assert model != null : "Module selected for fast deploy, but doesn't seem to have the right gradle model";
        String dexTask = InstantRunManager.getIncrementalDexTask(model, module);
        return new GradleInvokerOptions(Collections.singletonList(dexTask), null, cmdLineArgs);
      }
    }

    BuildMode buildMode = BuildMode.ASSEMBLE;
    GradleInvoker.TestCompileType testCompileType = getTestCompileType(configuration);

    tasks.addAll(GradleInvoker.findTasksToExecute(modules, buildMode, testCompileType));
    return new GradleInvokerOptions(tasks, buildMode, cmdLineArgs);
  }

  @NotNull
  private static String getInstantDevProperty(@NotNull Project project, boolean incrementalBuild) {
    StringBuilder sb = new StringBuilder(50);
    sb.append("-Pandroid.optional.compilation=INSTANT_DEV");

    FileChangeListener.Changes changes = InstantRunManager.get(project).getChangesAndReset();
    if (!incrementalBuild) {
      // for non-incremental builds (i.e. assembleDebug), gradle wants us to pass an additional parameter RESTART_ONLY
      sb.append(",RESTART_ONLY");
    }
    else if (!changes.nonSourceChanges) {
      if (changes.localResourceChanges) {
        sb.append(",LOCAL_RES_ONLY");
      }
      if (changes.localJavaChanges) {
        sb.append(",LOCAL_JAVA_ONLY");
      }
    }

    return sb.toString();
  }

  @NotNull
  private static Module[] getModules(@NotNull Project project, @Nullable DataContext context, @Nullable RunConfiguration configuration) {
    if (configuration instanceof ModuleRunProfile) {
      // ModuleBasedConfiguration includes Android and JUnit run configurations, including "JUnit: Rerun Failed Tests",
      // which is AbstractRerunFailedTestsAction.MyRunProfile.
      return  ((ModuleRunProfile)configuration).getModules();
    }
    else {
      return Projects.getModulesToBuildFromSelection(project, context);
    }
  }

  @NotNull
  private static Module[] getAffectedModules(@NotNull Project project, @NotNull Module[] modules) {
    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    CompileScope scope = compilerManager.createModulesCompileScope(modules, true, true);
    return scope.getAffectedModules();
  }

  @NotNull
  private static GradleInvoker.TestCompileType getTestCompileType(@Nullable RunConfiguration runConfiguration) {
    String id = runConfiguration != null ? runConfiguration.getType().getId() : null;
    return GradleInvoker.getTestCompileType(id);
  }

  // These are defined in AndroidProject in the builder model for 1.5+; remove and reference directly
  // when Studio is updated to use the new model
  private static final String PROPERTY_BUILD_API = "android.injected.build.api";
  private static final String PROPERTY_BUILD_DENSITY = "android.injected.build.density";

  @NotNull
  private static List<String> getDeviceSpecificArguments(@NotNull Collection<IDevice> devices) {
    if (devices.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> properties = new ArrayList<String>(2);

    // Find the minimum value of the build API level and pass it to Gradle as a property
    AndroidVersion min = null;
    for (IDevice device : devices) {
      AndroidVersion version = device.getVersion();
      if (version != AndroidVersion.DEFAULT && (min == null || version.getFeatureLevel() < min.getFeatureLevel())) {
        min = version;
      }
    }
    if (min != null) {
      properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_API, min.getApiString()));
    }

    // If we are building for only one device, pass the density.
    if (devices.size() == 1) {
      int densityInteger = Iterables.getOnlyElement(devices).getDensity();
      Density density = Density.getEnum(densityInteger);
      if (density != null) {
        properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_DENSITY, density.getResourceValue()));
      }
    }

    return properties;
  }

  @NotNull
  private static Collection<IDevice> getTargetDevices(@NotNull ExecutionEnvironment env) {
    DeviceFutures deviceFutures = env.getCopyableUserData(AndroidRunConfigurationBase.DEVICE_FUTURES_KEY);
    if (deviceFutures == null) {
      return Collections.emptyList();
    }

    Collection<IDevice> readyDevices = deviceFutures.getIfReady();
    return readyDevices == null ? Collections.<IDevice>emptyList() : readyDevices;
  }
}
