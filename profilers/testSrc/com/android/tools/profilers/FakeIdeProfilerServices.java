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
package com.android.tools.profilers;

import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.ProfilingConfiguration;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.android.tools.profilers.stacktrace.FakeCodeNavigator;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

public final class FakeIdeProfilerServices implements IdeProfilerServices {

  public static final String FAKE_ART_SAMPLED_NAME = "Sampled";

  public static final String FAKE_ART_INSTRUMENTED_NAME = "Instrumented";

  public static final String FAKE_SIMPLEPERF_NAME = "Simpleperf";

  public static final String FAKE_ATRACE_NAME = "Atrace";

  private final FeatureTracker myFakeFeatureTracker = new FakeFeatureTracker();
  private final CodeNavigator myFakeNavigationService = new FakeCodeNavigator(myFakeFeatureTracker);

  /**
   * Callback to be run after the executor calls its execute() method.
   */
  @Nullable
  Runnable myOnExecute;

  /**
   * The pool executor runs code in a separate thread. Sometimes is useful to check the state of the profilers
   * just before calling pool executor's execute method (e.g. verifying Stage's transient status before making a gRPC call).
   */
  @Nullable
  Runnable myPrePoolExecute;

  /**
   * Can toggle for tests via {@link #enableAtrace(boolean)}, but each test starts with this defaulted to false.
   */
  private boolean myAtraceEnabled = false;

  /**
   * Toggle for including an energy profiler in our profiler view.
   */
  private boolean myEnergyProfilerEnabled = false;

  /**
   * Can toggle for tests via {@link #enableExportTrace(boolean)}, but each test starts with this defaulted to false.
   */
  private boolean myExportCpuTraceEnabled = false;

  /**
   * Toggle for faking fragments UI support in tests.
   */
  private boolean myFragmentsEnabled = true;

  /**
   * Can toggle for tests via {@link #enableImportTrace(boolean)}, but each test starts with this defaulted to false.
   */
  private boolean myImportCpuTraceEnabled = false;

  /**
   * JNI references alloc/dealloc events are tracked and shown.
   */
  private boolean myIsJniReferenceTrackingEnabled = false;

  /**
   * Toggle for faking live allocation tracking support in tests.
   */
  private boolean myLiveTrackingEnabled = false;

  /**
   * Toggle for faking memory snapshot support in tests.
   */
  private boolean myMemorySnapshotEnabled = true;

  /**
   * Whether a native CPU profiling configuration is preferred over a Java one.
   */
  private boolean myNativeProfilingConfigurationPreferred = false;

  /**
   * Whether long trace files should be parsed.
   */
  private boolean myShouldParseLongTraces = false;

  /**
   * Toggle for faking sessions UI support in tests.
   */
  private boolean mySessionsViewEnabled = true;

  /**
   * Toggle for faking session import support in tests.
   */
  private boolean mySessionsImportEnabled = true;

  /**
   * Can toggle for tests via {@link #enableStartupCpuProfiling(boolean)}, but each test starts with this defaulted to false.
   */
  private boolean myStartupCpuProfilingEnabled = false;

  /**
   * Can toggle for tests via {@link #enableCpuApiTracing(boolean)}, but each test starts with this defaulted to false.
   */
  private boolean myIsCpuApiTracingEnabled = false;

  /**
   * List of custom CPU profiling configurations.
   */
  private final List<ProfilingConfiguration> myCustomProfilingConfigurations = new ArrayList<>();

  @NotNull private final ProfilerPreferences myPersistentPreferences;
  @NotNull private final ProfilerPreferences myTemporaryPreferences;

  /**
   * Title of the error balloon displayed when {@link #showErrorBalloon} or {@link #showWarningBalloon} is called.
   */
  private String myBalloonTitle;
  /**
   * Body of the error balloon displayed when {@link #showErrorBalloon} or {@link #showWarningBalloon} is called.
   */
  private String myBalloonBody;
  /**
   * Url of the error balloon displayed when {@link #showErrorBalloon} or {@link #showWarningBalloon} is called.
   */
  private String myBalloonUrl;
  /**
   * Linked text of the error balloon displayed when {@link #showErrorBalloon} or {@link #showWarningBalloon} is called.
   */
  private String myBalloonUrlText;
  /**
   * When {@link #openListBoxChooserDialog} is called this index is used to return a specific element in the set of options.
   * If this index is out of bounds, null is returned.
   */
  private int myListBoxOptionsIndex;
  /**
   * Fake application id to be used by test.
   */
  private String myApplicationId = "";

  public FakeIdeProfilerServices() {
    myPersistentPreferences = new FakeProfilerPreferences();
    myTemporaryPreferences = new FakeProfilerPreferences();
  }

  @NotNull
  @Override
  public Executor getMainExecutor() {
    return (runnable) -> {
      runnable.run();
      if (myOnExecute != null) {
        myOnExecute.run();
      }
    };
  }

  @NotNull
  @Override
  public Executor getPoolExecutor() {
    return (runnable) -> {
      if (myPrePoolExecute != null) {
        myPrePoolExecute.run();
      }
      runnable.run();
    };
  }

  @Override
  public void saveFile(@NotNull File file, @NotNull Consumer<FileOutputStream> fileOutputStreamConsumer, @Nullable Runnable postRunnable) {
  }

  @NotNull
  @Override
  public CodeNavigator getCodeNavigator() {
    return myFakeNavigationService;
  }

  @NotNull
  @Override
  public FeatureTracker getFeatureTracker() {
    return myFakeFeatureTracker;
  }

  @Override
  public void enableAdvancedProfiling() {
    // No-op.
  }

  @NotNull
  @Override
  public String getApplicationId() {
    return myApplicationId;
  }

  public void setApplicationId(@NotNull String name) {
    myApplicationId = name;
  }

  @NotNull
  @Override
  public FeatureConfig getFeatureConfig() {
    return new FeatureConfig() {
      @Override
      public boolean isAtraceEnabled() {
        return myAtraceEnabled;
      }

      @Override
      public boolean isCpuApiTracingEnabled() {
        return myIsCpuApiTracingEnabled;
      }

      @Override
      public boolean isEnergyProfilerEnabled() {
        return myEnergyProfilerEnabled;
      }

      @Override
      public boolean isExportCpuTraceEnabled() {
        return myExportCpuTraceEnabled;
      }

      @Override
      public boolean isFragmentsEnabled() {
        return myFragmentsEnabled;
      }

      @Override
      public boolean isImportCpuTraceEnabled() {
        return myImportCpuTraceEnabled;
      }

      @Override
      public boolean isJniReferenceTrackingEnabled() { return myIsJniReferenceTrackingEnabled; }

      @Override
      public boolean isLiveAllocationsEnabled() {
        return myLiveTrackingEnabled;
      }

      @Override
      public boolean isMemoryCaptureFilterEnabled() {
        return false;
      }

      @Override
      public boolean isMemorySnapshotEnabled() {
        return myMemorySnapshotEnabled;
      }

      @Override
      public boolean isPerformanceMonitoringEnabled() {
        return false;
      }

      @Override
      public boolean isSessionImportEnabled() {
        return mySessionsImportEnabled;
      }

      @Override
      public boolean isSessionsEnabled() {
        return mySessionsViewEnabled;
      }

      @Override
      public boolean isStartupCpuProfilingEnabled() {
        return myStartupCpuProfilingEnabled;
      }
    };
  }

  @NotNull
  @Override
  public ProfilerPreferences getTemporaryProfilerPreferences() {
    return myTemporaryPreferences;
  }

  @NotNull
  @Override
  public ProfilerPreferences getPersistentProfilerPreferences() {
    return myPersistentPreferences;
  }

  @Override
  public void openParseLargeTracesDialog(Runnable yesCallback, Runnable noCallback) {
    if (myShouldParseLongTraces) {
      yesCallback.run();
    }
    else {
      noCallback.run();
    }
  }

  @Override
  public <T> T openListBoxChooserDialog(@NotNull String title,
                                        @Nullable String message,
                                        @NotNull T[] options,
                                        @NotNull Function<T, String> listBoxPresentationAdapter) {
    if (myListBoxOptionsIndex >= 0 && myListBoxOptionsIndex < options.length) {
      return options[myListBoxOptionsIndex];
    }
    return null;
  }

  /**
   * Sets the listbox options return element index. If this is set to an index out of bounds null is returned.
   */
  public void setListBoxOptionsIndex(int optionIndex) {
    myListBoxOptionsIndex = optionIndex;
  }

  public void setShouldParseLongTraces(boolean shouldParseLongTraces) {
    myShouldParseLongTraces = shouldParseLongTraces;
  }

  public void addCustomProfilingConfiguration(String name, CpuProfiler.CpuProfilerType type) {
    ProfilingConfiguration config =
      new ProfilingConfiguration(name, type, CpuProfiler.CpuProfilerMode.UNSPECIFIED_MODE);
    myCustomProfilingConfigurations.add(config);
  }

  @Override
  public List<ProfilingConfiguration> getUserCpuProfilerConfigs() {
    return myCustomProfilingConfigurations;
  }

  @Override
  public List<ProfilingConfiguration> getDefaultCpuProfilerConfigs() {
    ProfilingConfiguration artSampled = new ProfilingConfiguration(FAKE_ART_SAMPLED_NAME,
                                                                   CpuProfiler.CpuProfilerType.ART,
                                                                   CpuProfiler.CpuProfilerMode.SAMPLED);
    ProfilingConfiguration artInstrumented = new ProfilingConfiguration(FAKE_ART_INSTRUMENTED_NAME,
                                                                        CpuProfiler.CpuProfilerType.ART,
                                                                        CpuProfiler.CpuProfilerMode.INSTRUMENTED);
    ProfilingConfiguration simpleperf = new ProfilingConfiguration(FAKE_SIMPLEPERF_NAME,
                                                                   CpuProfiler.CpuProfilerType.SIMPLEPERF,
                                                                   CpuProfiler.CpuProfilerMode.SAMPLED);
    ProfilingConfiguration atrace = new ProfilingConfiguration(FAKE_ATRACE_NAME,
                                                               CpuProfiler.CpuProfilerType.ATRACE,
                                                               CpuProfiler.CpuProfilerMode.SAMPLED);
    return ImmutableList.of(artSampled, artInstrumented, simpleperf, atrace);
  }

  @Override
  public boolean isNativeProfilingConfigurationPreferred() {
    return myNativeProfilingConfigurationPreferred;
  }

  @Override
  public void showErrorBalloon(@NotNull String title, @NotNull String body, @Nullable String url, @Nullable String urlText) {
    showBalloon(title, body, url, urlText);
  }

  @Override
  public void showWarningBalloon(@NotNull String title, @NotNull String body, @Nullable String url, @Nullable String urlText) {
    showBalloon(title, body, url, urlText);
  }

  private void showBalloon(@NotNull String title, @NotNull String body, @Nullable String url, @Nullable String urlText) {
    myBalloonTitle = title;
    myBalloonBody = body;
    myBalloonUrl = url;
    myBalloonUrlText = urlText;
  }

  @Override
  public void reportNoPiiException(@NotNull Throwable t) {
    t.printStackTrace();
  }

  public String getBalloonTitle() {
    return myBalloonTitle;
  }

  public String getBalloonBody() {
    return myBalloonBody;
  }

  public String getBalloonUrl() {
    return myBalloonUrl;
  }

  public String getBalloonUrlText() {
    return myBalloonUrlText;
  }

  public void setNativeProfilingConfigurationPreferred(boolean nativeProfilingConfigurationPreferred) {
    myNativeProfilingConfigurationPreferred = nativeProfilingConfigurationPreferred;
  }

  public void setOnExecute(@Nullable Runnable onExecute) {
    myOnExecute = onExecute;
  }

  public void setPrePoolExecutor(@Nullable Runnable prePoolExecute) {
    myPrePoolExecute = prePoolExecute;
  }

  public void enableAtrace(boolean enabled) {
    myAtraceEnabled = enabled;
  }

  public void enableEnergyProfiler(boolean enabled) {
    myEnergyProfilerEnabled = enabled;
  }

  public void enableFragments(boolean enabled) {
    myFragmentsEnabled = enabled;
  }

  public void enableJniReferenceTracking(boolean enabled) { myIsJniReferenceTrackingEnabled = enabled; }

  public void enableLiveAllocationTracking(boolean enabled) {
    myLiveTrackingEnabled = enabled;
  }

  public void enableMemorySnapshot(boolean enabled) {
    myMemorySnapshotEnabled = enabled;
  }

  public void enableSessionsView(boolean enabled) {
    mySessionsViewEnabled = enabled;
  }

  public void enableSessionImport(boolean enabled) {
    mySessionsImportEnabled = enabled;
  }

  public void enableStartupCpuProfiling(boolean enabled) {
    myStartupCpuProfilingEnabled = enabled;
  }

  public void enableCpuApiTracing(boolean enabled) {
    myStartupCpuProfilingEnabled = enabled;
  }

  public void enableExportTrace(boolean enabled) {
    myExportCpuTraceEnabled = enabled;
  }

  public void enableImportTrace(boolean enabled) {
    myImportCpuTraceEnabled = enabled;
  }
}
