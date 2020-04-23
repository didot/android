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

import static com.android.AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP;
import static com.android.tools.idea.run.AndroidRunConfiguration.LAUNCH_DEEP_LINK;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.deploy.DeploymentConfiguration;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerContext;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.editor.DeepLinkLaunch;
import com.android.tools.idea.run.tasks.ApplyChangesTask;
import com.android.tools.idea.run.tasks.ApplyCodeChangesTask;
import com.android.tools.idea.run.tasks.ClearLogcatTask;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.android.tools.idea.run.tasks.DeployTask;
import com.android.tools.idea.run.tasks.DismissKeyguardTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.tasks.RunInstantAppTask;
import com.android.tools.idea.run.tasks.ShowLogcatTask;
import com.android.tools.idea.run.tasks.UninstallIotLauncherAppsTask;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.SwapInfo;
import com.android.tools.idea.stats.RunStats;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLaunchTasksProvider implements LaunchTasksProvider {
  private final Logger myLogger = Logger.getInstance(AndroidLaunchTasksProvider.class);
  private final AndroidRunConfigurationBase myRunConfig;
  private final ExecutionEnvironment myEnv;
  private final AndroidFacet myFacet;
  private final ApplicationIdProvider myApplicationIdProvider;
  private final ApkProvider myApkProvider;
  private final LaunchOptions myLaunchOptions;
  private final Project myProject;

  public AndroidLaunchTasksProvider(@NotNull AndroidRunConfigurationBase runConfig,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull AndroidFacet facet,
                                    @NotNull ApplicationIdProvider applicationIdProvider,
                                    @NotNull ApkProvider apkProvider,
                                    @NotNull LaunchOptions launchOptions) {
    myRunConfig = runConfig;
    myEnv = env;
    myProject = facet.getModule().getProject();
    myFacet = facet;
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

    final boolean useApplyChanges = shouldApplyChanges() || shouldApplyCodeChanges();
    final boolean startNewAndroidApplication = !useApplyChanges && !shouldDeployAsInstant();
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

      if (startNewAndroidApplication) {
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
      if (useApplyChanges) {
        // ApkProvisionException should not happen for apply-changes launch. Log it at higher level.
        myLogger.error(e);
      } else {
        myLogger.warn(e);
      }
      launchStatus.terminateLaunch("Unable to determine application id: " + e,
                                   /*destroyProcess=*/startNewAndroidApplication);
      return Collections.emptyList();
    }
    catch (IllegalStateException e) {
      myLogger.error(e);
      launchStatus.terminateLaunch(e.getMessage(),
                                   /*destroyProcess=*/startNewAndroidApplication);
      return Collections.emptyList();
    }

    if (!myLaunchOptions.isDebug() && myLaunchOptions.isOpenLogcatAutomatically()) {
      launchTasks.add(new ShowLogcatTask(myProject, packageName));
    }

    return launchTasks;
  }

  @NotNull
  @VisibleForTesting
  List<LaunchTask> getDeployTasks(@NotNull final IDevice device, @NotNull final String packageName) throws ApkProvisionException {

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
      // Add packages to the deployment, filtering out any dynamic features that are disabled.
      ImmutableMap.Builder<String, List<File>> packages = ImmutableMap.builder();
      for (ApkInfo apkInfo : myApkProvider.getApks(device)) {
        packages.put(apkInfo.getApplicationId(), getFilteredFeatures(apkInfo, disabledFeatures));
      }

      // Set the appropriate action based on which deployment we're doing.
      if (shouldApplyChanges()) {

        tasks.add(new ApplyChangesTask(myProject, packages.build(), isApplyChangesFallbackToRun()));
      }
      else if (shouldApplyCodeChanges()) {
        tasks.add(new ApplyCodeChangesTask(myProject, packages.build(), isApplyCodeChangesFallbackToRun()));
      }
      else {
        tasks.add(new DeployTask(myProject, packages.build(), myLaunchOptions.getPmInstallOptions(), myLaunchOptions.getInstallOnAllUsers()));
      }
    }
    return ImmutableList.copyOf(tasks);
  }

  private boolean isApplyCodeChangesFallbackToRun() {
    return DeploymentConfiguration.getInstance().APPLY_CODE_CHANGES_FALLBACK_TO_RUN;
  }

  private boolean isApplyChangesFallbackToRun() {
    return DeploymentConfiguration.getInstance().APPLY_CHANGES_FALLBACK_TO_RUN;
  }

  @Override
  public void fillStats(RunStats stats) {
    stats.setApplyChangesFallbackToRun(isApplyChangesFallbackToRun());
    stats.setApplyCodeChangesFallbackToRun(isApplyCodeChangesFallbackToRun());
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

  @Nullable
  @Override
  public DebugConnectorTask getConnectDebuggerTask(@NotNull LaunchStatus launchStatus, @Nullable AndroidVersion version) {
    if (!myLaunchOptions.isDebug()) {
      return null;
    }
    Logger logger = Logger.getInstance(AndroidLaunchTasksProvider.class);

    Set<String> packageIds = Sets.newHashSet();
    String packageName = null;
    try {
      packageName = myApplicationIdProvider.getPackageName();
      packageIds.add(packageName);
    }
    catch (ApkProvisionException e) {
      logger.error(e);
    }

    try {
      String testPackageName = myApplicationIdProvider.getTestPackageName();
      if (testPackageName != null) {
        packageIds.add(testPackageName);
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
                                             packageName);
    }

    return null;
  }

  private boolean shouldDeployAsInstant() {
    return (myFacet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP ||
            myLaunchOptions.isDeployAsInstant());
  }

  private boolean shouldApplyChanges() {
    SwapInfo swapInfo = myEnv.getUserData(SwapInfo.SWAP_INFO_KEY);
    return swapInfo != null && swapInfo.getType() == SwapInfo.SwapType.APPLY_CHANGES;
  }

  private boolean shouldApplyCodeChanges() {
    SwapInfo swapInfo = myEnv.getUserData(SwapInfo.SWAP_INFO_KEY);
    return swapInfo != null && swapInfo.getType() == SwapInfo.SwapType.APPLY_CODE_CHANGES;
  }
}
