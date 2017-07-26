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
package com.android.tools.idea.flags;

import com.android.flags.Flag;
import com.android.flags.FlagGroup;
import com.android.flags.FlagOverrides;
import com.android.flags.Flags;
import com.android.flags.overrides.DefaultFlagOverrides;
import com.android.flags.overrides.PropertyOverrides;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

/**
 * A collection of all feature flags used by Android Studio. These flags can be used to gate
 * features entirely or branch internal logic of features, e.g. for experimentation or easy
 * rollback.
 *
 * For information on how to add your own flags, see the README.md file under
 * "//tools/base/flags".
 */
public final class StudioFlags {
  private static final Flags FLAGS = createFlags();

  @NotNull
  private static Flags createFlags() {
    Application app = ApplicationManager.getApplication();
    FlagOverrides userOverrides;
    if (app != null && !app.isUnitTestMode()) {
      userOverrides = StudioFlagSettings.getInstance();
    }
    else {
      userOverrides = new DefaultFlagOverrides();
    }
    return new Flags(userOverrides, new PropertyOverrides());
  }

  private static final FlagGroup NPW = new FlagGroup(FLAGS, "npw", "New Project Wizard");
  public static final Flag<Boolean> NPW_NEW_PROJECT = Flag.create(
    NPW, "new.project", "Migrate \"New Project\"",
    "Use the new wizard framework for the \"New > New Project...\" wizard flow.",
    true);

  public static final Flag<Boolean> NPW_NEW_MODULE = Flag.create(
    NPW, "new.module", "Migrate \"New Module\"",
    "Use the new wizard framework for the \"New > New Module...\" wizard flow.",
    true);

  public static final Flag<Boolean> NPW_IMPORT_MODULE = Flag.create(
    NPW, "import.module", "Migrate \"Import Module\"",
    "Use the new wizard framework for the \"New > Import Module...\" wizard flow.",
    true);

  public static final Flag<Boolean> NPW_KOTLIN = Flag.create(
    NPW, "kotlin", "Enable Kotlin projects",
    "Add an option in the new wizard flow to create a Kotlin project.",
    true);

  private static final FlagGroup PROFILER = new FlagGroup(FLAGS, "profiler", "Android Profiler");
  public static final Flag<Boolean> PROFILER_ENABLED = Flag.create(
    PROFILER, "enabled", "Enable \"Android Profiler\" toolbar",
    "Enable the new Android Profiler toolbar, which replaces the Android Monitor toolbar " +
    "and provides more advanced CPU, event, memory, and network profiling information.",
    true);

  public static final Flag<Boolean> PROFILER_USE_JVMTI = Flag.create(
    PROFILER, "jvmti", "Enable JVMTI profiling",
    "Use JVMTI for profiling devices with Android O or newer. " +
    "This unlocks even more profiling features for these devices.",
    true);

  public static final Flag<Boolean> PROFILER_USE_SIMPLEPERF = Flag.create(
    PROFILER, "simpleperf", "Enable Simpleperf profiling",
    "Use Simpleperf for CPU profiling on devices with Android O or newer. " +
    "Simpleperf is a native profiler tool built for Android.",
    false
  );

  public static final Flag<Boolean> PROFILER_SHOW_THREADS_VIEW = Flag.create(
    PROFILER, "threads.view", "Show network threads view",
    "Show a view in the network profiler that groups connections by their creation thread.",
    false);

  public static final Flag<Boolean> PROFILER_USE_LIVE_ALLOCATIONS = Flag.create(
    PROFILER, "livealloc", "Enable JVMTI-based live allocation tracking",
    "For Android O or newer, allocations are tracked all the time while inside the Memory Profiler.",
    true);

  private static final FlagGroup NELE = new FlagGroup(FLAGS, "nele", "Layout Editor");
  public static final Flag<Boolean> NELE_ANIMATIONS_PREVIEW = Flag.create(
    NELE, "animated.preview", "Show preview animations toolbar",
    "Show an animations bar that allows playback of vector drawable animations.",
    false);

  public static final Flag<Boolean> NELE_SAMPLE_DATA = Flag.create(
    NELE, "mock.data", "Enable \"Sample Data\" for the layout editor",
    "Enables the use of @sample references in the tools namespace to use sample data.",
    true);

  public static final Flag<Boolean> NELE_MOCKUP_EDITOR = Flag.create(
    NELE, "mockup.editor", "Enable the Mockup Editor",
    "Enable the Mockup Editor to ease the creation of Layouts from a design file.",
    false);

  public static final Flag<Boolean> NELE_LIVE_RENDER = Flag.create(
    NELE, "live.render", "Enable the Live Render",
    "Enable the continuous rendering of the surface when moving/resizing components.",
    false);

  public static final Flag<Boolean> NELE_WIDGET_ASSISTANT = Flag.create(
    NELE, "widget.assistant", "Enable the properties panel Widget Assistant",
    "Enable the Widget Assistant that provides common shortcuts for certain widgets.",
    false);

  public static final Flag<Boolean> NELE_CONVERT_VIEW = Flag.create(
    NELE, "convert.view", "Enable the Convert View Action",
    "Enable the Convert View Action when right clicking on a component",
    false);

  private static final FlagGroup ASSISTANT = new FlagGroup(FLAGS, "assistant", "Assistants");
  public static final Flag<Boolean> CONNECTION_ASSISTANT_ENABLED = Flag.create(
    ASSISTANT, "connection.enabled", "Enable the connection assistant",
    "If enabled, user can access the Connection Assistant under \"Tools\" and \"Deploy Target Dialog\"",
    false);

  public static final Flag<Boolean> NELE_TARGET_RELATIVE = Flag.create(
    NELE, "target.relative", "Enable the target architecture in relative layout",
    "Enable the new Target architecture in relative layout",
    false);

  private StudioFlags() {
  }
}
