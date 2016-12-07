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

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.fd.InstantRunBuildAnalyzer;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerContext;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.tasks.*;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;

public class AndroidLaunchTasksProvider implements LaunchTasksProvider {
  private final AndroidRunConfigurationBase myRunConfig;
  private final ExecutionEnvironment myEnv;
  private final Project myProject;
  private final AndroidFacet myFacet;
  private final InstantRunBuildAnalyzer myInstantRunBuildAnalyzer;
  private final ApplicationIdProvider myApplicationIdProvider;
  private final ApkProvider myApkProvider;
  private final LaunchOptions myLaunchOptions;

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
  public List<LaunchTask> getTasks(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter consolePrinter)
    throws ExecutionException {
    final List<LaunchTask> launchTasks = Lists.newArrayList();

    if (myLaunchOptions.isClearLogcatBeforeStart()) {
      launchTasks.add(new ClearLogcatTask(myProject));
    }

    launchTasks.add(new DismissKeyguardTask());

    String packageName;
    try {
      launchTasks.addAll(getDeployTasks(device));

      packageName = myApplicationIdProvider.getPackageName();
      LaunchTask appLaunchTask = myRunConfig.getApplicationLaunchTask(myApplicationIdProvider, myFacet,
                                                                      myLaunchOptions.isDebug(), launchStatus);
      if (appLaunchTask != null) {
        launchTasks.add(appLaunchTask);
      }
    }
    catch (ApkProvisionException e) {
      Logger.getInstance(AndroidLaunchTasksProvider.class).error(e);
      launchStatus.terminateLaunch("Unable to determine application id: " + e);
      return Collections.emptyList();
    }

    if (myInstantRunBuildAnalyzer != null) {
      launchTasks.add(new LaunchInstantRunServiceTask(packageName));
    }

    if (!myLaunchOptions.isDebug() && myLaunchOptions.isOpenLogcatAutomatically()) {
      launchTasks.add(new ShowLogcatTask(myProject, packageName));
    }

    if (myInstantRunBuildAnalyzer != null) {
      launchTasks.add(myInstantRunBuildAnalyzer.getNotificationTask());
    }

    for (AndroidLaunchTaskContributor taskContributor : AndroidLaunchTaskContributor.EP_NAME.getExtensions()) {
      if (taskContributor.isApplicable(myFacet.getModule())) {
        launchTasks.add(taskContributor.getTask(packageName));
      }
    }

    return launchTasks;
  }

  @NotNull
  private List<LaunchTask> getDeployTasks(@NotNull final IDevice device) throws ApkProvisionException, ExecutionException {
    if (myInstantRunBuildAnalyzer != null) {
      return myInstantRunBuildAnalyzer.getDeployTasks(myLaunchOptions);
    }

    // regular APK deploy flow
    if (!myLaunchOptions.isDeploy()) {
      return Collections.emptyList();
    }

    if (myFacet.getProjectType() == PROJECT_TYPE_INSTANTAPP) {
      return ImmutableList.of(new DeployIapkTask(myApkProvider.getApks(device)));
    }

    InstantRunManager.LOG.info("Using legacy/main APK deploy task");
    return ImmutableList.of(new DeployApkTask(myProject, myLaunchOptions, myApkProvider.getApks(device)));
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
}
