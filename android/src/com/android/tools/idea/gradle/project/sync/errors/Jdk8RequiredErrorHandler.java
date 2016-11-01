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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.Jdks;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class Jdk8RequiredErrorHandler extends SyncErrorHandler {
  @NotNull private final Jdks myJdks;

  public Jdk8RequiredErrorHandler() {
    this(Jdks.getInstance());
  }

  public Jdk8RequiredErrorHandler(@NotNull Jdks jdks) {
    myJdks = jdks;
  }

  @Nullable
  @Override
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull NotificationData notification, @NotNull Project project) {
    // Example:
    // com/android/jack/api/ConfigNotSupportedException : Unsupported major.minor version 52.0
    String text = rootCause.getMessage();
    if (isNotEmpty(text) && text.contains("Unsupported major.minor version 52.0")) {
      if (!text.endsWith(".")) {
        text += ".";
      }
      text += " Please use JDK 8 or newer.";
      updateUsageTracker();
      return text;
    }
    return null;
  }

  @NotNull
  @Override
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull NotificationData notification,
                                                              @NotNull Project project,
                                                              @NotNull String text) {
    return myJdks.getWrongJdkQuickFixes(project);
  }
}
