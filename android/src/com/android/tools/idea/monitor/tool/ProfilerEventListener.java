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
package com.android.tools.idea.monitor.tool;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * TODO: Move this interface out of the datastore module, this interface is pirmarily used for UI callbacks and should be independent
 * of the current datastore. Keeping for now until we decide on how to refactor.
 * Interface to support communications across segments via the {@link com.intellij.util.EventDispatcher¡} mechanism.
 * @param <T>   The profiler type that was expanded. This should be an enum of type BaseProfilerUiManager.ProfilerType.
 */
public interface ProfilerEventListener<T> extends EventListener {
  /**
   * Notifies that a profiler has been requested to expand (either from L1 -> L2, or L2 -> L3).
   */
  default void profilerExpanded(@NotNull T profilerType) {
  }

  /**
   * Notifies that profilers has been reset back to L1.
   */
  default void profilersReset() {
  }
}
