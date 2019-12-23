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
package com.android.tools.idea.gradle.run;

import static com.android.builder.model.AndroidProject.PROPERTY_APK_SELECT_CONFIG;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_ABI;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_API;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_API_CODENAME;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_DENSITY;
import static com.android.builder.model.AndroidProject.PROPERTY_DEPLOY_AS_INSTANT_APP;
import static com.android.builder.model.AndroidProject.PROPERTY_EXTRACT_INSTANT_APK;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.run.editor.ProfilerState.ANDROID_ADVANCED_PROFILING_TRANSFORMS;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_RUN_SYNC_NEEDED_BEFORE_RUNNING;
import static com.intellij.openapi.util.io.FileUtil.createTempFile;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.run.AndroidAppRunConfigurationBase;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.PreferGradleMake;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.stats.RunStats;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Ordering;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileWithCompileBeforeLaunchOption;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.ThreeState;
import icons.AndroidIcons;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.Icon;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides the "Gradle-aware Make" task for Run Configurations, which
 * <ul>
 * <li>is only available in Android Studio</li>
 * <li>delegates to the regular "Make" if the project is not an Android Gradle project</li>
 * <li>otherwise, invokes Gradle directly, to build the project</li>
 * </ul>
 */
public class MakeBeforeRunTaskProvider extends BeforeRunTaskProvider<MakeBeforeRunTask> {
  @NotNull public static final Key<MakeBeforeRunTask> ID = Key.create("Android.Gradle.BeforeRunTask");
  private static int DEVICE_SPEC_TIMEOUT_SECONDS = 10;

  public static final String TASK_NAME = "Gradle-aware Make";

  @NotNull private final Project myProject;
  @NotNull private final AndroidProjectInfo myAndroidProjectInfo;
  @NotNull private final GradleProjectInfo myGradleProjectInfo;
  @NotNull private final GradleTaskRunnerFactory myTaskRunnerFactory;

  public MakeBeforeRunTaskProvider(@NotNull Project project) {
    myProject = project;
    myAndroidProjectInfo = AndroidProjectInfo.getInstance(project);
    myGradleProjectInfo = GradleProjectInfo.getInstance(project);
    myTaskRunnerFactory = new GradleTaskRunnerFactory(myProject);
  }

  @Override
  public Key<MakeBeforeRunTask> getId() {
    return ID;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AndroidIcons.Android;
  }

  @Nullable
  @Override
  public Icon getTaskIcon(MakeBeforeRunTask task) {
    return AndroidIcons.Android;
  }

  @Override
  public String getName() {
    return TASK_NAME;
  }

  @Override
  public String getDescription(MakeBeforeRunTask task) {
    String goal = task.getGoal();
    return isEmpty(goal) ? TASK_NAME : "gradle " + goal;
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Nullable
  @Override
  public MakeBeforeRunTask createTask(@NotNull RunConfiguration runConfiguration) {
    // "Gradle-aware Make" is only available in Android Studio.
    if (configurationTypeIsSupported(runConfiguration)) {
      MakeBeforeRunTask task = new MakeBeforeRunTask();
      if (configurationTypeIsEnabledByDefault(runConfiguration)) {
        // For Android configurations, we want to replace the default make, so this new task needs to be enabled.
        // In AndroidRunConfigurationType#configureBeforeTaskDefaults we disable the default make, which is
        // enabled by default. For other configurations we leave it disabled, so we don't end up with two different
        // make steps executed by default. If the task is added to the run configuration manually, it will be
        // enabled by the UI layer later.
        task.setEnabled(true);
      }
      return task;
    }
    else {
      return null;
    }
  }

  public boolean configurationTypeIsSupported(@NotNull RunConfiguration runConfiguration) {
    if (myAndroidProjectInfo.isApkProject()) {
      return false;
    }
    return runConfiguration instanceof PreferGradleMake || isUnitTestConfiguration(runConfiguration);
  }

  public boolean configurationTypeIsEnabledByDefault(@NotNull RunConfiguration runConfiguration) {
    return runConfiguration instanceof PreferGradleMake;
  }

  private static boolean isUnitTestConfiguration(@NotNull RunConfiguration runConfiguration) {
    return runConfiguration instanceof JUnitConfiguration ||
           // Avoid direct dependency on the TestNG plugin:
           runConfiguration.getClass().getSimpleName().equals("TestNGConfiguration");
  }

  @Override
  public boolean configureTask(@NotNull RunConfiguration runConfiguration, @NotNull MakeBeforeRunTask task) {
    GradleEditTaskDialog dialog = new GradleEditTaskDialog(myProject);
    dialog.setGoal(task.getGoal());
    dialog.setAvailableGoals(createAvailableTasks());
    if (!dialog.showAndGet()) {
      // since we allow tasks without any arguments (assumed to be equivalent to assembling the app),
      // we need a way to specify that a task is not valid. This is because of the current restriction
      // of this API, where the return value from configureTask is ignored.
      task.setInvalid();
      return false;
    }

    task.setGoal(dialog.getGoal());
    return true;
  }

  @NotNull
  private List<String> createAvailableTasks() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> gradleTasks = new ArrayList<>();
    for (Module module : moduleManager.getModules()) {
      GradleFacet facet = GradleFacet.getInstance(module);
      if (facet == null) {
        continue;
      }

      GradleModuleModel gradleModuleModel = facet.getGradleModuleModel();
      if (gradleModuleModel == null) {
        continue;
      }

      gradleTasks.addAll(gradleModuleModel.getTaskNames());
    }
    return gradleTasks;
  }

  @Override
  public boolean canExecuteTask(@NotNull RunConfiguration configuration, @NotNull MakeBeforeRunTask task) {
    return task.isValid();
  }

  /**
   * Execute the Gradle build task, returns {@code false} in case of any error.
   *
   * <p>Note: Error handling should be improved to notify user in case of {@code false} return value.
   * Currently, the caller does not expect exceptions, and there is no notification mechanism to propagate an
   * error message to the user. The current implementation uses logging (in idea.log) to report errors, whereas the
   * UI behavior is to merely stop the execution without any other sort of notification, which far from ideal.
   */
  @Override
  public boolean executeTask(@NotNull DataContext context,
                             @NotNull RunConfiguration configuration,
                             @NotNull ExecutionEnvironment env,
                             @NotNull MakeBeforeRunTask task) {
    RunStats stats = RunStats.from(env);
    try {
      stats.beginBeforeRunTasks();
      return doExecuteTask(context, configuration, env, task);
    }
    finally {
      stats.endBeforeRunTasks();
    }
  }

  @VisibleForTesting
  @Nullable
  String runGradleSyncIfNeeded(@NotNull RunConfiguration configuration, @NotNull DataContext context) {
    boolean syncNeeded = false;
    boolean forceFullVariantsSync = false;
    AtomicReference<String> errorMsgRef = new AtomicReference<>();

    // Invoke Gradle Sync if build files have been changed since last sync, and Sync-before-build option is enabled OR post build sync
    // if not supported. The later case requires Gradle Sync, because deploy relies on the models from Gradle Sync to get Apk locations.
    if (GradleSyncState.getInstance(myProject).isSyncNeeded() != ThreeState.NO &&
        (AndroidGradleBuildConfiguration.getInstance(myProject).SYNC_PROJECT_BEFORE_BUILD ||
         !isPostBuildSyncSupported(context, configuration))) {
      syncNeeded = true;
    }

    // If the project has native modules, and there're any un-synced variants.
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      NdkModuleModel ndkModel = NdkModuleModel.get(module);
      if (ndkModel != null && ndkModel.getVariants().size() < ndkModel.getNdkVariantNames().size()) {
        syncNeeded = true;
        forceFullVariantsSync = true;
        break;
      }
    }

    if (syncNeeded) {
      GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(TRIGGER_RUN_SYNC_NEEDED_BEFORE_RUNNING);
      request.runInBackground = false;
      request.forceFullVariantsSync = forceFullVariantsSync;

      GradleSyncInvoker.getInstance().requestProjectSync(myProject, request, new GradleSyncListener() {
        @Override
        public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
          errorMsgRef.set(errorMessage);
        }
      });
    }

    return errorMsgRef.get();
  }

  /**
   * Returns true if there is no Android module, or all of Android modules support post build sync.
   */
  private boolean isPostBuildSyncSupported(@NotNull DataContext context,
                                           @NotNull RunConfiguration configuration) {
    Module[] modules = getModules(context, configuration);
    for (Module module : modules) {
      AndroidModuleModel androidModuleModel = AndroidModuleModel.get(module);
      if (androidModuleModel != null && !androidModuleModel.getFeatures().isPostBuildSyncSupported()) {
        return false;
      }
    }
    return true;
  }

  private boolean doExecuteTask(DataContext context, RunConfiguration configuration, ExecutionEnvironment env, MakeBeforeRunTask task) {
    if (!myAndroidProjectInfo.requiresAndroidModel() || !myGradleProjectInfo.isDirectGradleBuildEnabled()) {
      CompileStepBeforeRun regularMake = new CompileStepBeforeRun(myProject);
      return regularMake.executeTask(context, configuration, env, new CompileStepBeforeRun.MakeBeforeRunTask());
    }

    // If the model needs a sync, we need to sync "synchronously" before running.
    String errorMsg = runGradleSyncIfNeeded(configuration, context);
    if (errorMsg != null) {
      // Sync failed. There is no point on continuing, because most likely the model is either not there, or has stale information,
      // including the path of the APK.
      getLog().info("Unable to launch '" + TASK_NAME + "' task. Project sync failed with message: " + errorMsg);
      return false;
    }

    if (myProject.isDisposed()) {
      return false;
    }

    // Some configurations (e.g. native attach) don't require a build while running the configuration
    if (configuration instanceof RunProfileWithCompileBeforeLaunchOption &&
        ((RunProfileWithCompileBeforeLaunchOption)configuration).isExcludeCompileBeforeLaunchOption()) {
      return true;
    }

    // Compute modules to build
    Module[] modules = getModules(context, configuration);

    // Note: this before run task provider may be invoked from a context such as Java unit tests, in which case it doesn't have
    // the android run config context
    DeviceFutures deviceFutures = env.getCopyableUserData(DeviceFutures.KEY);
    List<AndroidDevice> targetDevices = deviceFutures == null ? Collections.emptyList() : deviceFutures.getDevices();
    List<String> cmdLineArgs;
    try {
      cmdLineArgs = getCommonArguments(modules, configuration, targetDevices);
    }
    catch (Exception e) {
      getLog().warn("Error generating command line arguments for Gradle task", e);
      return false;
    }

    BeforeRunBuilder builder = createBuilder(modules, configuration, targetDevices, task.getGoal());

    GradleTaskRunner.DefaultGradleTaskRunner runner = myTaskRunnerFactory.createTaskRunner(configuration);
    BuildSettings.getInstance(myProject).setRunConfigurationTypeId(configuration.getType().getId());
    try {
      boolean success = builder.build(runner, cmdLineArgs);

      if (configuration instanceof AndroidRunConfigurationBase) {
        Object model = runner.getModel();
        if (model instanceof OutputBuildAction.PostBuildProjectModels) {
          ((AndroidRunConfigurationBase)configuration).setOutputModel(new PostBuildModel((OutputBuildAction.PostBuildProjectModels)model));
        }
        else {
          getLog().info("Couldn't get post build models.");
        }
      }

      getLog().info("Gradle invocation complete, success = " + success);
      return success;
    }
    catch (InvocationTargetException e) {
      getLog().info("Unexpected error while launching gradle before run tasks", e);
      return false;
    }
    catch (InterruptedException e) {
      getLog().info("Interrupted while launching gradle before run tasks");
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(MakeBeforeRunTask.class);
  }

  /**
   * Returns the list of arguments to Gradle that are common to both instant and non-instant builds.
   */
  @NotNull
  private static List<String> getCommonArguments(@NotNull Module[] modules,
                                                 @NotNull RunConfiguration configuration,
                                                 @NotNull List<AndroidDevice> targetDevices) throws IOException {
    List<String> cmdLineArgs = new ArrayList<>();
    cmdLineArgs.addAll(getDeviceSpecificArguments(modules, configuration, targetDevices));
    cmdLineArgs.addAll(getProfilingOptions(configuration, targetDevices));
    return cmdLineArgs;
  }

  @NotNull
  public static List<String> getDeviceSpecificArguments(@NotNull Module[] modules,
                                                        @NotNull RunConfiguration configuration,
                                                        @NotNull List<AndroidDevice> devices) throws IOException {
    AndroidDeviceSpec deviceSpec = AndroidDeviceSpec.create(devices,
                                                            shouldCollectListOfLanguages(modules, configuration, devices),
                                                            DEVICE_SPEC_TIMEOUT_SECONDS,
                                                            TimeUnit.SECONDS);
    if (deviceSpec == null) {
      return Collections.emptyList();
    }

    List<String> properties = new ArrayList<>(2);
    if (useSelectApksFromBundleBuilder(modules, configuration, devices)) {
      // For the bundle tool, we create a temporary json file with the device spec and
      // pass the file path to the gradle task.
      File deviceSpecFile = deviceSpec.writeToJsonTempFile();
      properties.add(createProjectProperty(PROPERTY_APK_SELECT_CONFIG, deviceSpecFile.getAbsolutePath()));
      if (configuration instanceof AndroidAppRunConfigurationBase) {
        if (((AndroidAppRunConfigurationBase)configuration).DEPLOY_AS_INSTANT) {
          properties.add(createProjectProperty(PROPERTY_EXTRACT_INSTANT_APK, true));
        }
      }
    }
    else {
      // For non bundle tool deploy tasks, we have one argument per device spec property
      properties.add(createProjectProperty(PROPERTY_BUILD_API, Integer.toString(deviceSpec.getApiLevel())));
      if (deviceSpec.getApiCodeName() != null) {
        properties.add(createProjectProperty(PROPERTY_BUILD_API_CODENAME, deviceSpec.getApiCodeName()));
      }

      if (deviceSpec.getBuildDensity() != null) {
        properties.add(createProjectProperty(PROPERTY_BUILD_DENSITY, deviceSpec.getBuildDensity().getResourceValue()));
      }
      if (!deviceSpec.getBuildAbis().isEmpty()) {
        properties.add(createProjectProperty(PROPERTY_BUILD_ABI, Joiner.on(',').join(deviceSpec.getBuildAbis())));
      }
      if (configuration instanceof AndroidAppRunConfigurationBase) {
        if (((AndroidAppRunConfigurationBase)configuration).DEPLOY_AS_INSTANT) {
          properties.add(createProjectProperty(PROPERTY_DEPLOY_AS_INSTANT_APP, true));
        }
      }
    }
    return properties;
  }

  @NotNull
  private static List<String> getProfilingOptions(@NotNull RunConfiguration configuration, @NotNull List<AndroidDevice> devices)
    throws IOException {
    if (!(configuration instanceof AndroidRunConfigurationBase) || devices.isEmpty()) {
      return Collections.emptyList();
    }

    // Find the minimum API version in case both a pre-O and post-O devices are selected.
    // TODO: if a post-O app happened to be transformed, the agent needs to account for that.
    List<AndroidVersion> versionLists = devices.stream().map(AndroidDevice::getVersion).collect(Collectors.toList());
    AndroidVersion minVersion = Ordering.natural().min(versionLists);
    List<String> arguments = new LinkedList<>();
    ProfilerState state = ((AndroidRunConfigurationBase)configuration).getProfilerState();
    if (state.ADVANCED_PROFILING_ENABLED && minVersion.getFeatureLevel() >= AndroidVersion.VersionCodes.LOLLIPOP &&
        minVersion.getFeatureLevel() < AndroidVersion.VersionCodes.O) {
      File file = EmbeddedDistributionPaths.getInstance().findEmbeddedProfilerTransform(minVersion);
      arguments.add(createProjectProperty(ANDROID_ADVANCED_PROFILING_TRANSFORMS, file.getAbsolutePath()));

      Properties profilerProperties = state.toProperties();
      File propertiesFile = createTempFile("profiler", ".properties");
      propertiesFile.deleteOnExit(); // TODO: It'd be nice to clean this up sooner than at exit.

      Writer writer = new OutputStreamWriter(new FileOutputStream(propertiesFile), Charsets.UTF_8);
      profilerProperties.store(writer, "Android Studio Profiler Gradle Plugin Properties");
      writer.close();

      arguments.add(AndroidGradleSettings.createJvmArg("android.profiler.properties", propertiesFile.getAbsolutePath()));
    }
    return arguments;
  }

  @NotNull
  private static BeforeRunBuilder createBuilder(@NotNull Module[] modules,
                                                @NotNull RunConfiguration configuration,
                                                @NotNull List<AndroidDevice> targetDevices,
                                                @Nullable String userGoal) {
    if (modules.length == 0) {
      throw new IllegalStateException("Unable to determine list of modules to build");
    }

    if (!isEmpty(userGoal)) {
      ListMultimap<Path, String> tasks = ArrayListMultimap.create();
      StreamEx.of(modules)
        .map(module -> ExternalSystemApiUtil.getExternalRootProjectPath(module))
        .nonNull()
        .distinct()
        .map(path -> Paths.get(path))
        .forEach(path -> tasks.put(path, userGoal));
      return new DefaultGradleBuilder(tasks, null);
    }

    GradleModuleTasksProvider gradleTasksProvider = new GradleModuleTasksProvider(modules);

    TestCompileType testCompileType = TestCompileType.get(configuration.getType().getId());
    if (testCompileType == TestCompileType.UNIT_TESTS) {
      BuildMode buildMode = BuildMode.COMPILE_JAVA;
      return new DefaultGradleBuilder(gradleTasksProvider.getUnitTestTasks(buildMode), buildMode);
    }

    // Use the "select apks from bundle" task if using a "AndroidBundleRunConfiguration".
    // Note: This is very ad-hoc, and it would be nice to have a better abstraction for this special case.
    if (useSelectApksFromBundleBuilder(modules, configuration, targetDevices)) {
      return new DefaultGradleBuilder(gradleTasksProvider.getTasksFor(BuildMode.APK_FROM_BUNDLE, testCompileType),
                                      BuildMode.APK_FROM_BUNDLE);
    }
    return new DefaultGradleBuilder(gradleTasksProvider.getTasksFor(BuildMode.ASSEMBLE, testCompileType), BuildMode.ASSEMBLE);
  }

  private static boolean useSelectApksFromBundleBuilder(@NotNull Module[] modules,
                                                        @NotNull RunConfiguration configuration,
                                                        @NotNull List<AndroidDevice> targetDevices) {
    return Arrays.stream(modules).anyMatch(module -> DynamicAppUtils.useSelectApksFromBundleBuilder(module, configuration, targetDevices));
  }

  private static boolean shouldCollectListOfLanguages(@NotNull Module[] modules,
                                                      @NotNull RunConfiguration configuration,
                                                      @NotNull List<AndroidDevice> targetDevices) {
    // We should collect the list of languages only if *all* devices are verify the condition, otherwise we would
    // end up deploying language split APKs to devices that don't support them.
    return Arrays.stream(modules).allMatch(module -> DynamicAppUtils.shouldCollectListOfLanguages(module, configuration, targetDevices));
  }

  @NotNull
  private Module[] getModules(@Nullable DataContext context, @Nullable RunConfiguration configuration) {
    if (configuration instanceof ModuleRunProfile) {
      // If running JUnit tests for "whole project", all the modules should be compiled, but getModules() return an empty array.
      // See http://b.android.com/230678
      if (configuration instanceof AndroidJUnitConfiguration) {
        return ((AndroidJUnitConfiguration)configuration).getModulesToCompile();
      }
      // ModuleBasedConfiguration includes Android and JUnit run configurations, including "JUnit: Rerun Failed Tests",
      // which is AbstractRerunFailedTestsAction.MyRunProfile.
      return ((ModuleRunProfile)configuration).getModules();
    }
    else {
      return myGradleProjectInfo.getModulesToBuildFromSelection(context);
    }
  }

  @Nullable
  public static IDevice getLaunchedDevice(@NotNull AndroidDevice device) {
    if (!device.getLaunchedDevice().isDone()) {
      // If we don't have access to the device (this happens if the AVD is still launching)
      return null;
    }

    try {
      return device.getLaunchedDevice().get(1, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
    catch (ExecutionException | TimeoutException e) {
      return null;
    }
  }

  @VisibleForTesting
  static class GradleTaskRunnerFactory {
    @NotNull private final Project myProject;

    GradleTaskRunnerFactory(@NotNull Project project) {
      myProject = project;
    }

    @NotNull
    GradleTaskRunner.DefaultGradleTaskRunner createTaskRunner(@NotNull RunConfiguration configuration) {
      if (configuration instanceof AndroidRunConfigurationBase) {
        List<Module> modules = new ArrayList<>();
        Module selectedModule = ((AndroidRunConfigurationBase)configuration).getConfigurationModule().getModule();
        if (selectedModule != null) {
          modules.add(selectedModule);
          // Instrumented test support for Dynamic features: base-app module should be included explicitly,
          // then corresponding post build models are generated. And in this case, dynamic feature module
          // retrieved earlier is for test Apk.
          if (configuration instanceof AndroidTestRunConfiguration) {
            Module baseModule = DynamicAppUtils.getBaseFeature(selectedModule);
            if (baseModule != null) {
              modules.add(baseModule);
            }
          }
        }
        return GradleTaskRunner.newRunner(myProject, OutputBuildActionUtil.create(modules));
      }
      return GradleTaskRunner.newRunner(myProject, null);
    }
  }
}
