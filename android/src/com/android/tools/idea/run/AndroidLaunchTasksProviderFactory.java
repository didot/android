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

import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.idea.fd.InstantRunGradleUtils;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunStatsService;
import com.android.tools.idea.fd.InstantRunUtils;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.tasks.LaunchTasksProviderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLaunchTasksProviderFactory implements LaunchTasksProviderFactory {
  private final AndroidRunConfigurationBase myRunConfig;
  private final ExecutionEnvironment myEnv;
  private final AndroidFacet myFacet;
  private final ApkProvider myApkProvider;
  private final LaunchOptions myLaunchOptions;
  private final ProcessHandler myPreviousSessionProcessHandler;

  public AndroidLaunchTasksProviderFactory(@NotNull AndroidRunConfigurationBase runConfig,
                                           @NotNull ExecutionEnvironment env,
                                           @NotNull AndroidFacet facet,
                                           @NotNull ApkProvider apkProvider,
                                           @NotNull LaunchOptions launchOptions,
                                           @Nullable ProcessHandler processHandler) {
    myRunConfig = runConfig;
    myEnv = env;
    myFacet = facet;
    myApkProvider = apkProvider;
    myLaunchOptions = launchOptions;
    myPreviousSessionProcessHandler = processHandler;
  }

  @NotNull
  @Override
  public LaunchTasksProvider get() {
    InstantRunStatsService.get(myEnv.getProject()).notifyDeployStarted();

    if (InstantRunUtils.isInstantRunEnabled(myEnv) && canHotSwap()) {
      return new HotSwapTasksProvider(myRunConfig, myEnv, myFacet, myApkProvider, myLaunchOptions);
    }

    return new AndroidLaunchTasksProvider(myRunConfig, myEnv, myFacet, myApkProvider, myLaunchOptions);
  }

  // Returns whether the build results indicate that we can perform a hotswap
  private boolean canHotSwap() {
    if (myPreviousSessionProcessHandler == null) {
      // if there is no existing session, then even though the build ids might match, we can't use hotswap (possibly use dexswap)
      return false;
    }

    if (InstantRunUtils.needsFullBuild(myEnv) || !InstantRunUtils.isAppRunning(myEnv)) {
      // We knew before the build that we couldn't hot swap..
      return false;
    }

    AndroidGradleModel model = AndroidGradleModel.get(myFacet);
    InstantRunBuildInfo info = model == null ? null : InstantRunGradleUtils.getBuildInfo(model);
    boolean canHotswap = info != null && info.canHotswap();

    if (!canHotswap) {
      if (info == null) {
        InstantRunManager.LOG.info("No build-info.xml file");
      }
      else {
        InstantRunManager.LOG.info("Cannot swap according to build-info.xml");
      }
    }

    return canHotswap;
  }
}
