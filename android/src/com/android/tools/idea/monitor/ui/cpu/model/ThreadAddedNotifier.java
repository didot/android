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
package com.android.tools.idea.monitor.ui.cpu.model;

import com.android.tools.profilers.cpu.ThreadStateDataSeries;

/**
 * Notifies when a new thread model was added because a new thread was polled from the device.
 */
public interface ThreadAddedNotifier {
  void threadAdded(ThreadStateDataSeries threadStateDataSeries);

  default void threadsAdded(ThreadStateDataSeries[] threadStateDataSeriesList) {
    for(ThreadStateDataSeries model : threadStateDataSeriesList) {
      threadAdded(model);
    }
  }
}
