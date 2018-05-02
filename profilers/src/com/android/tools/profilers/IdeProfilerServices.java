/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers;

import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.CpuProfilerConfigModel;
import com.android.tools.profilers.cpu.ProfilingConfiguration;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public interface IdeProfilerServices {
  /**
   * Executor to run the tasks that should get back to the main thread.
   */
  @NotNull
  Executor getMainExecutor();

  /**
   * Executor to run the tasks that should run in a thread from the pool.
   */
  @NotNull
  Executor getPoolExecutor();

  /**
   * Saves a file to the file system and have IDE internal state reflect this file addition.
   *
   * @param file                     File to save to.
   * @param fileOutputStreamConsumer {@link Consumer} to write the file contents into the supplied {@link FileOutputStream}.
   * @param postRunnable             A callback for when the system finally finishes writing to and synchronizing the file.
   */
  void saveFile(@NotNull File file, @NotNull Consumer<FileOutputStream> fileOutputStreamConsumer, @Nullable Runnable postRunnable);

  /**
   * Returns a service that can navigate to a target code location.
   *
   * Implementors of this method should be sure to return the same instance each time, not a new
   * instance per call.
   */
  @NotNull
  CodeNavigator getCodeNavigator();

  /**
   * Returns an opt-in service that can report when certain features were used.
   *
   * Implementors of this method should be sure to return the same instance each time, not a new
   * instance per call.
   */
  @NotNull
  FeatureTracker getFeatureTracker();

  /**
   * Either enable advanced profiling or present the user with UI to make enabling it easy.
   *
   * By default, advanced profiling features are not turned on, as they require instrumenting the
   * user's code, which at the very least requires a rebuild. Moreover, this may even potentially
   * interfere with the user's app logic or slow it down.
   *
   * If this method is called, it means the user has expressed an intention to enable advanced
   * profiling. It is up to the implementor of this method to help the user accomplish this
   * request.
   */
  void enableAdvancedProfiling();

  @NotNull
  FeatureConfig getFeatureConfig();

  /**
   * Allows the profiler to cache settings within the current studio session.
   * e.g. settings are only preserved across profiling sessions within the same studio instance.
   */
  @NotNull
  ProfilerPreferences getTemporaryProfilerPreferences();

  /**
   * Allows the profiler to cache settings across multiple studio sessions.
   * e.g. settings are preserved when studio restarts.
   */
  @NotNull
  ProfilerPreferences getPersistentProfilerPreferences();

  /**
   * Open the dialog for managing the CPU profiling configurations.
   *
   * @param profilerModel    {@link CpuProfilerConfigModel} corresponding to the {@link ProfilingConfiguration} to be selected when opening
                             the dialog.
   * @param deviceLevel      API level of the device.
   * @param dialogCallback   Callback to be called once the dialog is closed. Takes a {@link ProfilingConfiguration}
   *                         that was selected on the configurations list when the dialog was closed.
   */
  void openCpuProfilingConfigurationsDialog(CpuProfilerConfigModel profilerModel, int deviceLevel,
                                            Consumer<ProfilingConfiguration> dialogCallback);

  /**
   * Displays a yes/no dialog warning the user the trace file is too large to be parsed and asking them if parsing should proceed.
   *
   * @param yesCallback callback to be run if user clicks "Yes"
   * @param noCallback  callback to be run if user clicks "No"
   */
  void openParseLargeTracesDialog(Runnable yesCallback, Runnable noCallback);

  /**
   * Returns the profiling configurations saved by the user for a project.
   */
  List<ProfilingConfiguration> getUserCpuProfilerConfigs();

  /**
   * @return default profiling configurations.
   */
  List<ProfilingConfiguration> getDefaultCpuProfilerConfigs();

  /**
   * Whether a native CPU profiling configuration is preferred over a Java one.
   * Native configurations can be preferred for native projects, for instance.
   */
  boolean isNativeProfilingConfigurationPreferred();

  /**
   * Displays a balloon message showing the user that an error has occurred.
   *
   * @param title   title of the message
   * @param body    body of the message, followed by a hyperlink as specified by next two parameters
   * @param url     destination of the hyperlink that follows the body, if neither url or urlText is null
   * @param urlText shown text of the hyperlink that follows the body, if neither url or urlText is null
   */
  void showErrorBalloon(@NotNull String title, @NotNull String body, @Nullable String url, @Nullable String urlText);

  /**
   * Wraps the supplied expection in a NoPiiException that is then sent to the crash report.
   * This function should only be called when we are sure there is no PII within the exception message.
   * The NoPiiException uploads the full exception message to the crash report site. This can then be
   * to diagnose and root cause issues.
   * @param t throwable to be wrapped. The exception should not contain PII within the message.
   */
  void reportNoPiiException(@NotNull Throwable t);
}
