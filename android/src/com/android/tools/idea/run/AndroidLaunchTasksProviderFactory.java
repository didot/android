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

import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.tasks.LaunchTasksProviderFactory;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class AndroidLaunchTasksProviderFactory implements LaunchTasksProviderFactory {
  private final AndroidRunConfigurationBase myRunConfig;
  private final ExecutionEnvironment myEnv;
  private final AndroidFacet myFacet;
  private final ApplicationIdProvider myApplicationIdProvider;
  private final ApkProvider myApkProvider;
  private final LaunchOptions myLaunchOptions;

  public AndroidLaunchTasksProviderFactory(@NotNull AndroidRunConfigurationBase runConfig,
                                           @NotNull ExecutionEnvironment env,
                                           @NotNull AndroidFacet facet,
                                           @NotNull ApplicationIdProvider applicationIdProvider,
                                           @NotNull ApkProvider apkProvider,
                                           @NotNull LaunchOptions launchOptions) {
    myRunConfig = runConfig;
    myEnv = env;
    myFacet = facet;
    myApplicationIdProvider = applicationIdProvider;
    myApkProvider = apkProvider;
    myLaunchOptions = launchOptions;
  }

  @NotNull
  @Override
  public LaunchTasksProvider get() {
    return new AndroidLaunchTasksProvider(myRunConfig, myEnv, myFacet, myApplicationIdProvider, myApkProvider, myLaunchOptions);
  }
}
