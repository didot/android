/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.profilers;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.profilers.profilingconfig.CpuProfilerConfigConverter;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.run.*;
import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.idea.run.profiler.CpuProfilerConfigsState;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTaskDurations;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.Profiler;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * A {@link AndroidLaunchTaskContributor} specific to profiler. For example, this contributor provides "--attach-agent $agentArgs"
 * extra option to "am start ..." command.
 */
public final class AndroidProfilerLaunchTaskContributor implements AndroidLaunchTaskContributor {
  private static Logger getLogger() {
    return Logger.getInstance(AndroidProfilerLaunchTaskContributor.class);
  }

  @NotNull
  @Override
  public LaunchTask getTask(@NotNull Module module, @NotNull String applicationId, @NotNull LaunchOptions launchOptions) {
    return new AndroidProfilerToolWindowLaunchTask(module);
  }

  @NotNull
  @Override
  public String getAmStartOptions(@NotNull Module module, @NotNull String applicationId, @NotNull LaunchOptions launchOptions,
                                  @NotNull IDevice device) {
    Object launchValue = launchOptions.getExtraOption(ProfileRunExecutor.PROFILER_LAUNCH_OPTION_KEY);
    if (!(launchValue instanceof Boolean && (Boolean)launchValue)) {
      // Not a profile action
      return "";
    }

    ProfilerService profilerService = ProfilerService.getInstance(module.getProject());
    long deviceId;
    try {
      deviceId = waitForPerfd(device, profilerService);
    }
    catch (InterruptedException | TimeoutException e) {
      getLogger().debug(e);
      // Don't attach JVMTI agent for now, there is a chance that it will be attached during runtime.
      return "";
    }

    StringBuilder args = new StringBuilder(getAttachAgentArgs(applicationId, profilerService, device, deviceId));
    args.append(" ").append(startStartupProfiling(applicationId, module, profilerService, device, deviceId));
    return args.toString();
  }

  @NotNull
  private static String getAttachAgentArgs(@NotNull String appPackageName,
                                           @NotNull ProfilerService profilerService,
                                           @NotNull IDevice device,
                                           long deviceId) {
    // --attach-agent flag was introduced from android API level 27.
    if (!StudioFlags.PROFILER_USE_JVMTI.get() || device.getVersion().getFeatureLevel() < AndroidVersion.VersionCodes.O_MR1) {
      return "";
    }
    Profiler.ConfigureStartupAgentResponse response = profilerService.getProfilerClient().getProfilerClient()
      .configureStartupAgent(Profiler.ConfigureStartupAgentRequest.newBuilder().setDeviceId(deviceId)
                               // TODO: Find a way of finding the correct ABI
                               .setAgentLibFileName(getAbiDependentLibPerfaName(device))
                               .setAppPackageName(appPackageName).build());
    return response.getAgentArgs().isEmpty() ? "" : "--attach-agent " + response.getAgentArgs();
  }

  /**
   * Starts startup profiling by RPC call to perfd.
   *
   * @return arguments used with --start-profiler flag, i.e "--start-profiler $filePath --sampling 100 --streaming",
   * the result is an empty string, when either startup CPU profiling is not enabled
   * or the selected CPU configuration is not an ART profiling.
   */
  @NotNull
  private static String startStartupProfiling(@NotNull String appPackageName,
                                              @NotNull Module module,
                                              @NotNull ProfilerService profilerService,
                                              @NotNull IDevice device,
                                              long deviceId) {
    if (!StudioFlags.PROFILER_STARTUP_CPU_PROFILING.get()) {
      return "";
    }

    AndroidRunConfigurationBase runConfig = getSelectedRunConfiguration(module.getProject());
    if (runConfig == null || !runConfig.getProfilerState().STARTUP_CPU_PROFILING_ENABLED) {
      return "";
    }

    String configName = runConfig.getProfilerState().STARTUP_CPU_PROFILING_CONFIGURATION_NAME;
    CpuProfilerConfig startupConfig = CpuProfilerConfigsState.getInstance(module.getProject()).getConfigByName(configName);
    if (startupConfig == null) {
      return "";
    }

    if (!isAtLeastO(device)) {
      AndroidNotification.getInstance(module.getProject()).showBalloon("Startup CPU Profiling",
                                                                       "Starting a method trace recording on startup is only " +
                                                                       "supported on devices with API levels 26 and higher.",
                                                                       NotificationType.WARNING);
      return "";
    }

    CpuProfiler.StartupProfilingRequest.Builder requestBuilder = CpuProfiler.StartupProfilingRequest
      .newBuilder()
      .setAppPackage(appPackageName)
      .setDeviceId(deviceId)
      .setConfiguration(CpuProfilerConfigConverter.toProto(startupConfig));

    if (requestBuilder.getConfiguration().getProfilerType() == CpuProfiler.CpuProfilerType.SIMPLEPERF) {
      requestBuilder.setAbiCpuArch(getSimpleperfAbi(device));
    }

    CpuProfiler.StartupProfilingResponse response = profilerService
      .getProfilerClient().getCpuClient()
      .startStartupProfiling(requestBuilder.build());

    if (response.getFilePath().isEmpty() || requestBuilder.getConfiguration().getProfilerType() != CpuProfiler.CpuProfilerType.ART) {
      return "";
    }

    StringBuilder argsBuilder = new StringBuilder("--start-profiler ").append(response.getFilePath());
    if (startupConfig.getTechnology() == CpuProfilerConfig.Technology.SAMPLED_JAVA) {
      argsBuilder.append(" --sampling ").append(startupConfig.getSamplingIntervalUs());
    }

    argsBuilder.append(" --streaming");
    return argsBuilder.toString();
  }

  private static boolean isAtLeastO(@NotNull IDevice device) {
    return device.getVersion().getFeatureLevel() >= AndroidVersion.VersionCodes.O;
  }

  @Nullable
  private static AndroidRunConfigurationBase getSelectedRunConfiguration(@NotNull Project project) {
    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
    if (settings != null && settings.getConfiguration() instanceof AndroidRunConfigurationBase) {
      return (AndroidRunConfigurationBase)settings.getConfiguration();
    }
    return null;
  }

  /**
   * Waits for perfd to come online for maximum 1 minute.
   *
   * @return ID of device, i.e {@link Common.Device#getDeviceId()}
   */
  private static long waitForPerfd(@NotNull IDevice device, @NotNull ProfilerService profilerService)
    throws InterruptedException, TimeoutException {
    // Wait for perfd to come online for 1 minute.
    for (int i = 0; i < 60; ++i) {
      Profiler.GetDevicesResponse response =
        profilerService.getProfilerClient().getProfilerClient()
          .getDevices(Profiler.GetDevicesRequest.getDefaultInstance());

      for (Common.Device profilerDevice : response.getDeviceList()) {
        if (profilerDevice.getSerial().equals(device.getSerialNumber())) {
          return profilerDevice.getDeviceId();
        }
      }
      Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    }
    throw new TimeoutException("Timeout waiting for perfd");
  }

  @NotNull
  private static String getAbiDependentLibPerfaName(IDevice device) {
    String abi = getBestAbi(device,
                            "plugins/android/resources/perfa",
                            "../../bazel-bin/tools/base/profiler/native/perfa/android",
                            "libperfa.so");
    return abi.isEmpty() ? "" : String.format("libperfa_%s.so", abi);
  }

  @NotNull
  private static String getSimpleperfAbi(IDevice device) {
    return getBestAbi(device,
                      "plugins/android/resources/simpleperf",
                      "../../prebuilts/tools/common/simpleperf",
                      "simpleperf");
  }

  /**
   * @return the most preferred ABI according to {@link IDevice#getAbis()} for which
   *         {@param fileName} exists in {@param releaseDir} or {@param devDir}
   */
  @NotNull
  private static String getBestAbi(@NotNull IDevice device,
                                   @NotNull String releaseDir,
                                   @NotNull String devDir,
                                   @NotNull String fileName) {
    File dir = new File(PathManager.getHomePath(), releaseDir);
    if (!dir.exists()) {
      dir = new File(PathManager.getHomePath(), devDir);
    }
    for (String abi : device.getAbis()) {
      File candidate = new File(dir, abi + "/" + fileName);
      if (candidate.exists()) {
        return Abi.getEnum(abi).getCpuArch();
      }
    }
    return "";
  }

  public static final class AndroidProfilerToolWindowLaunchTask implements LaunchTask {
    @NotNull private final Module myModule;

    public AndroidProfilerToolWindowLaunchTask(@NotNull Module module) {
      myModule = module;
    }

    @NotNull
    @Override
    public String getDescription() {
      return "Presents the Profiler Tool Window";
    }

    @Override
    public int getDuration() {
      return LaunchTaskDurations.LAUNCH_ACTIVITY;
    }

    @Override
    public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
      ApplicationManager.getApplication().invokeLater(
        () -> {
          Project project = myModule.getProject();
          ToolWindow window = ToolWindowManagerEx.getInstanceEx(project).getToolWindow(AndroidProfilerToolWindowFactory.ID);
          if (window != null) {
            window.setShowStripeButton(true);
            AndroidProfilerToolWindow profilerToolWindow = AndroidProfilerToolWindowFactory.getProfilerToolWindow(project);
            if (profilerToolWindow != null) {
              profilerToolWindow.profileProject(myModule, device);
            }
          }
        });
      return true;
    }
  }
}