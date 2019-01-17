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
package com.android.tools.idea.run;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.fd.InstantRunBuildAnalyzer;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerContext;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.editor.DeepLinkLaunch;
import com.android.tools.idea.run.tasks.*;
import com.android.tools.idea.run.ui.ApplyChangesAction;
import com.android.tools.idea.run.ui.CodeSwapAction;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.android.tools.idea.run.AndroidRunConfiguration.LAUNCH_DEEP_LINK;

public class AndroidLaunchTasksProvider implements LaunchTasksProvider {
  private final AndroidRunConfigurationBase myRunConfig;
  private final ExecutionEnvironment myEnv;
  private final AndroidFacet myFacet;
  private final InstantRunBuildAnalyzer myInstantRunBuildAnalyzer;
  private final ApplicationIdProvider myApplicationIdProvider;
  private final ApkProvider myApkProvider;
  private final LaunchOptions myLaunchOptions;
  private final Project myProject;

  public AndroidLaunchTasksProvider(@NotNull AndroidRunConfigurationBase runConfig,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull AndroidFacet facet,
                                    @Nullable InstantRunBuildAnalyzer instantRunBuildAnalyzer,
                                    @NotNull ApplicationIdProvider applicationIdProvider,
                                    @NotNull ApkProvider apkProvider,
                                    @NotNull LaunchOptions launchOptions) {
    myRunConfig = runConfig;
    myEnv = env;
    myProject = facet.getModule().getProject();
    myFacet = facet;
    myInstantRunBuildAnalyzer = instantRunBuildAnalyzer;
    myApplicationIdProvider = applicationIdProvider;
    myApkProvider = apkProvider;
    myLaunchOptions = launchOptions;
  }

  @NotNull
  @Override
  public List<LaunchTask> getTasks(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter consolePrinter) {
    final List<LaunchTask> launchTasks = Lists.newArrayList();

    if (myLaunchOptions.isClearLogcatBeforeStart()) {
      launchTasks.add(new ClearLogcatTask(myProject));
    }

    launchTasks.add(new DismissKeyguardTask());

    String packageName;
    try {
      packageName = myApplicationIdProvider.getPackageName();
      launchTasks.addAll(getDeployTasks(device, packageName));

      StringBuilder amStartOptions = new StringBuilder();
      // launch the contributors before launching the application in case
      // the contributors need to start listening on logcat for the application launch itself
      for (AndroidLaunchTaskContributor taskContributor : AndroidLaunchTaskContributor.EP_NAME.getExtensions()) {
        String amOptions = taskContributor.getAmStartOptions(myFacet.getModule(), packageName, myLaunchOptions, device);
        amStartOptions.append(amStartOptions.length() == 0 ? "" : " ").append(amOptions);

        LaunchTask task = taskContributor.getTask(myFacet.getModule(), packageName, myLaunchOptions);
        if (task != null) {
          launchTasks.add(task);
        }
      }

      boolean launchApp = true;
      if (StudioFlags.JVMTI_REFRESH.get()) {
        if (shouldApplyChanges() || shouldApplyCodeChanges()) {
          launchApp = false;
        }
      }

      if (launchApp && !shouldDeployAsInstant()) {
        // A separate deep link launch task is not necessary if launch will be handled by
        // RunInstantAppTask
        LaunchTask appLaunchTask = myRunConfig.getApplicationLaunchTask(myApplicationIdProvider, myFacet,
                                                                        amStartOptions.toString(),
                                                                        myLaunchOptions.isDebug(), launchStatus);
        if (appLaunchTask != null) {
          launchTasks.add(appLaunchTask);
        }
      }
    }
    catch (ApkProvisionException e) {
      Logger.getInstance(AndroidLaunchTasksProvider.class).error(e);
      launchStatus.terminateLaunch("Unable to determine application id: " + e);
      return Collections.emptyList();
    }
    catch (IllegalStateException e) {
      Logger.getInstance(AndroidLaunchTasksProvider.class).error(e);
      launchStatus.terminateLaunch(e.getMessage());
      return Collections.emptyList();
    }

    if (!myLaunchOptions.isDebug() && myLaunchOptions.isOpenLogcatAutomatically()) {
      launchTasks.add(new ShowLogcatTask(myProject, packageName));
    }

    if (myInstantRunBuildAnalyzer != null) {
      launchTasks.add(myInstantRunBuildAnalyzer.getNotificationTask());
    }

    return launchTasks;
  }

  @NotNull
  @VisibleForTesting
  List<LaunchTask> getDeployTasks(@NotNull final IDevice device, @NotNull final String packageName) throws ApkProvisionException {

    if (myInstantRunBuildAnalyzer != null) {
      return myInstantRunBuildAnalyzer.getDeployTasks(device, myLaunchOptions);
    }

    // regular APK deploy flow
    if (!myLaunchOptions.isDeploy()) {
      return Collections.emptyList();
    }

    List<LaunchTask> tasks = new ArrayList<>();
    if (StudioFlags.UNINSTALL_LAUNCHER_APPS_ENABLED.get() &&
        device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)) {
      tasks.add(new UninstallIotLauncherAppsTask(myProject, packageName));
    }
    List<String> disabledFeatures = myLaunchOptions.getDisabledDynamicFeatures();
    if (shouldDeployAsInstant()) {
      AndroidRunConfiguration runConfig = (AndroidRunConfiguration)myRunConfig;
      DeepLinkLaunch.State state = (DeepLinkLaunch.State)runConfig.getLaunchOptionState(LAUNCH_DEEP_LINK);
      assert state != null;
      tasks.add(new RunInstantAppTask(myApkProvider.getApks(device), state.DEEP_LINK, disabledFeatures));
    }
    else {
      // Use new deployment if it is enabled and supported.
      if (StudioFlags.UNIFIED_DEPLOYMENT.get() || StudioFlags.JVMTI_REFRESH.get()) {

        // Add packages to the deployment, filtering out any dynamic features that are disabled.
        ImmutableMap.Builder<String, List<File>> packages = ImmutableMap.builder();
        for (ApkInfo apkInfo : myApkProvider.getApks(device)) {
          packages.put(apkInfo.getApplicationId(), getFilteredFeatures(apkInfo, disabledFeatures));
        }

        // Set the appropriate action based on which deployment we're doing.
        if (shouldApplyChanges()) {
          tasks.add(new ApplyChangesTask(myProject, packages.build()));
        }
        else if (shouldApplyCodeChanges()) {
          tasks.add(new ApplyCodeChangesTask(myProject, packages.build()));
        }
        else {
          tasks.add(new DeployTask(myProject, packages.build(), myLaunchOptions.getPmInstallOptions()));
        }
      } else {
        InstantRunManager.LOG.info("Using non-instant run deploy tasks (single and split apks apps)");
        // Add tasks for each apk (or split-apk) returned by the apk provider
        tasks.addAll(createDeployTasks(myApkProvider.getApks(device),
                                       apks -> new DeployApkTask(myProject, myLaunchOptions, ImmutableList.copyOf(apks)),
                                       apkInfo -> new SplitApkDeployTask(myProject,
                                                                         new DynamicAppDeployTaskContext(apkInfo, disabledFeatures))));
      }
    }
    return ImmutableList.copyOf(tasks);
  }

  @NotNull
  private static List<File> getFilteredFeatures(ApkInfo apkInfo, List<String> disabledFeatures) {
    if (apkInfo.getFiles().size() > 1) {
      return apkInfo.getFiles().stream()
        .filter(feature -> DynamicAppUtils.isFeatureEnabled(disabledFeatures, feature))
        .map(file -> file.getApkFile())
        .collect(Collectors.toList());
    } else {
      return ImmutableList.of(apkInfo.getFile());
    }
  }

  /**
   * Returns a list of launch tasks, both single apk or split apk, required to deploy the given list of apks.
   * Note: Since single apk launch task can handle more than one apk, single apk tasks are merged in batches.
   */
  @NotNull
  private static List<LaunchTask> createDeployTasks(@NotNull Collection<ApkInfo> apks,
                                                    @NotNull Function<List<ApkInfo>, LaunchTask> singleApkTaskFactory,
                                                    @NotNull Function<ApkInfo, LaunchTask> splitApkTaskFactory) {
    List<LaunchTask> result = new ArrayList<>();
    List<ApkInfo> singleApkTasks = new ArrayList<>();
    for (ApkInfo apkInfo : apks) {
      if (apkInfo.getFiles().size() > 1) {
        if (!singleApkTasks.isEmpty()) {
          result.add(singleApkTaskFactory.apply(ImmutableList.copyOf(singleApkTasks)));
          singleApkTasks.clear();
        }
        result.add(splitApkTaskFactory.apply(apkInfo));
      }
      else {
        singleApkTasks.add(apkInfo);
      }
    }

    if (!singleApkTasks.isEmpty()) {
      result.add(singleApkTaskFactory.apply(singleApkTasks));
    }
    return result;
  }

  @Nullable
  @Override
  public DebugConnectorTask getConnectDebuggerTask(@NotNull LaunchStatus launchStatus, @Nullable AndroidVersion version) {
    if (!myLaunchOptions.isDebug()) {
      return null;
    }
    Logger logger = Logger.getInstance(AndroidLaunchTasksProvider.class);

    Set<String> packageIds = Sets.newHashSet();
    try {
      String packageName = myApplicationIdProvider.getPackageName();
      packageIds.add(packageName);
    }
    catch (ApkProvisionException e) {
      logger.error(e);
    }

    try {
      String packageName = myApplicationIdProvider.getTestPackageName();
      if (packageName != null) {
        packageIds.add(packageName);
      }
    }
    catch (ApkProvisionException e) {
      // not as severe as failing to obtain package id for main application
      logger
        .warn("Unable to obtain test package name, will not connect debugger if tests don't instantiate main application");
    }

    AndroidDebuggerContext androidDebuggerContext = myRunConfig.getAndroidDebuggerContext();
    AndroidDebugger debugger = androidDebuggerContext.getAndroidDebugger();
    if (debugger == null) {
      logger.warn("Unable to determine debugger to use for this launch");
      return null;
    }
    logger.info("Using debugger: " + debugger.getId());

    AndroidDebuggerState androidDebuggerState = androidDebuggerContext.getAndroidDebuggerState();
    if (androidDebuggerState != null) {
      //noinspection unchecked
      return debugger.getConnectDebuggerTask(myEnv,
                                             version,
                                             packageIds,
                                             myFacet,
                                             androidDebuggerState,
                                             myRunConfig.getType().getId(),
                                             monitorRemoteProcess());
    }

    return null;
  }

  @Override
  public boolean createsNewProcess() {
    return true;
  }

  @Override
  public boolean monitorRemoteProcess() {
    return myRunConfig.monitorRemoteProcess();
  }

  private boolean shouldDeployAsInstant() {
    return (myFacet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP ||
            myLaunchOptions.isDeployAsInstant());
  }

  private boolean shouldApplyChanges() {
    return Boolean.TRUE.equals(myEnv.getCopyableUserData(ApplyChangesAction.KEY));
  }

  private boolean shouldApplyCodeChanges() {
    return Boolean.TRUE.equals(myEnv.getCopyableUserData(CodeSwapAction.KEY));
  }
}
