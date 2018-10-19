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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.notification.NotificationListener;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.Nullable;

public interface LaunchTask {
  /**
   * A description which may get shown to the user as the task is being launched.
   * <p>
   * The description should start with a verb using present continuous tense for the verb,
   * e.g. "Launching X", "Opening Y", "Starting Z"
   */
  @NotNull
  String getDescription();

  @Nullable
  default String getFailureReason() {
    return null;
  }

  @Nullable
  default NotificationListener getNotificationListener() {
    return null;
  }

  int getDuration();

  boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer);

  @NotNull
  String getId();

  @NotNull
  default Collection<ApkInfo> getApkInfos() { return Collections.EMPTY_LIST; }
}
