/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport;

import com.android.tools.profiler.proto.Common;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for preprocessing transport events before they are inserted into the data store. Does not modify the event.
 * <p>
 * For instance, energy profiler preprocesses CPU and network events to calculate energy usage.
 */
public interface TransportEventPreprocessor {
  /**
   * @return True if the event should be preprocessed by this preprocessor.
   */
  boolean shouldPreprocess(Common.Event event);

  /**
   * Preprocess the event. Only applies if {@link #shouldPreprocess(Common.Event)} returns true.
   *
   * @return new events generated by the preprocessor, if any.
   */
  @NotNull
  Iterable<Common.Event> preprocessEvent(Common.Event event);
}
