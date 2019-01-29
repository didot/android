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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A collection of options governing Android run configuration launch behavior.
 */
public final class LaunchOptions {

  public static final class Builder {
    private boolean myDeploy = true;
    private String myPmInstallOptions = null;
    private List<String> myDisabledDynamicFeatures = new ArrayList<>();
    private boolean myDebug = false;
    private boolean myOpenLogcatAutomatically = false;
    private boolean myClearLogcatBeforeStart = false;
    private boolean mySkipNoopApkInstallations = true;
    private boolean myForceStopRunningApp = true;
    private final Map<String, Object> myExtraOptions = new HashMap<>();
    private boolean myDeployAsInstant = false;

    private Builder() {
    }

    @NotNull
    public LaunchOptions build() {
      return new LaunchOptions(myDeploy,
                               myPmInstallOptions,
                               myDisabledDynamicFeatures,
                               myDebug,
                               myOpenLogcatAutomatically,
                               myClearLogcatBeforeStart,
                               mySkipNoopApkInstallations,
                               myForceStopRunningApp,
                               myExtraOptions,
                               myDeployAsInstant);
    }

    @NotNull
    public Builder setDeploy(boolean deploy) {
      myDeploy = deploy;
      return this;
    }

    @NotNull
    public Builder setPmInstallOptions(@Nullable String options) {
      myPmInstallOptions = options;
      return this;
    }

    @NotNull
    public Builder setDebug(boolean debug) {
      myDebug = debug;
      return this;
    }

    @NotNull
    public Builder setOpenLogcatAutomatically(boolean openLogcatAutomatically) {
      myOpenLogcatAutomatically = openLogcatAutomatically;
      return this;
    }

    @NotNull
    public Builder setClearLogcatBeforeStart(boolean clearLogcatBeforeStart) {
      myClearLogcatBeforeStart = clearLogcatBeforeStart;
      return this;
    }

    @NotNull
    public Builder setSkipNoopApkInstallations(boolean skipNoopApkInstallations) {
      mySkipNoopApkInstallations = skipNoopApkInstallations;
      return this;
    }

    @NotNull
    public Builder setForceStopRunningApp(boolean forceStopRunningApp) {
      myForceStopRunningApp = forceStopRunningApp;
      return this;
    }

    @NotNull
    public Builder addExtraOptions(@NotNull Map<String, Object> extraOptions) {
      myExtraOptions.putAll(extraOptions);
      return this;
    }

    public Builder setDisabledDynamicFeatures(List<String> disabledDynamicFeatures) {
      myDisabledDynamicFeatures = ImmutableList.copyOf(disabledDynamicFeatures);
      return this;
    }

    @NotNull
    public Builder setDeployAsInstant(boolean deployAsInstant) {
      myDeployAsInstant = deployAsInstant;
      return this;
    }
  }

  @NotNull
  public static Builder builder() {
    return new Builder();
  }

  private final boolean myDeploy;
  private final String myPmInstallOptions;
  private List<String> myDisabledDynamicFeatures;
  private final boolean myDebug;
  private final boolean myOpenLogcatAutomatically;
  private final boolean myClearLogcatBeforeStart;
  private final boolean mySkipNoopApkInstallations;
  private final boolean myForceStopRunningApp;
  private final Map<String, Object> myExtraOptions;
  private final boolean myDeployAsInstant;

  private LaunchOptions(boolean deploy,
                        @Nullable String pmInstallOptions,
                        @NotNull List<String> disabledDynamicFeatures,
                        boolean debug,
                        boolean openLogcatAutomatically,
                        boolean clearLogcatBeforeStart,
                        boolean skipNoopApkInstallations,
                        boolean forceStopRunningApp,
                        @NotNull Map<String, Object> extraOptions,
                        boolean deployAsInstant) {
    myDeploy = deploy;
    myPmInstallOptions = pmInstallOptions;
    myDisabledDynamicFeatures = disabledDynamicFeatures;
    myDebug = debug;
    myOpenLogcatAutomatically = openLogcatAutomatically;
    myClearLogcatBeforeStart = clearLogcatBeforeStart;
    mySkipNoopApkInstallations = skipNoopApkInstallations;
    myForceStopRunningApp = forceStopRunningApp;
    myExtraOptions = ImmutableMap.copyOf(extraOptions);
    myDeployAsInstant = deployAsInstant;
  }

  public boolean isDeploy() {
    return myDeploy;
  }

  @Nullable
  public String getPmInstallOptions() {
    return myPmInstallOptions;
  }

  @NotNull
  public List<String> getDisabledDynamicFeatures() {
    return myDisabledDynamicFeatures;
  }

  public boolean isDebug() {
    return myDebug;
  }

  public boolean isOpenLogcatAutomatically() {
    return myOpenLogcatAutomatically;
  }

  public boolean isClearLogcatBeforeStart() {
    return myClearLogcatBeforeStart;
  }

  public boolean isSkipNoopApkInstallations() {
    return mySkipNoopApkInstallations;
  }

  public boolean isForceStopRunningApp() {
    return myForceStopRunningApp;
  }

  @Nullable
  public Object getExtraOption(@NotNull String key) {
    return myExtraOptions.get(key);
  }

  public boolean isDeployAsInstant() { return myDeployAsInstant; }
}
