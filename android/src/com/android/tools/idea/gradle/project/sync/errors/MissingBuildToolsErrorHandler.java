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

import com.android.tools.idea.gradle.project.sync.hyperlink.InstallBuildToolsHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.NotificationHyperlink;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.MISSING_BUILD_TOOLS;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class MissingBuildToolsErrorHandler extends BaseSyncErrorHandler {
  private final Pattern MISSING_BUILD_TOOLS_PATTERN = Pattern.compile("(Cause: )?(F|f)ailed to find Build Tools revision (.*)");

  @Override
  @Nullable
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull NotificationData notification, @NotNull Project project) {
    String text = rootCause.getMessage();
    if (isNotEmpty(text)) {
      Matcher matcher = MISSING_BUILD_TOOLS_PATTERN.matcher(getFirstLineMessage(text));
      if ((rootCause instanceof IllegalStateException || rootCause instanceof ExternalSystemException) && matcher.matches()) {
        updateUsageTracker(MISSING_BUILD_TOOLS);
        return text;
      }
    }
    return null;
  }

  @Override
  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull NotificationData notification,
                                                              @NotNull Project project,
                                                              @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    //If get to this point, the message matches patter
    Matcher matcher = MISSING_BUILD_TOOLS_PATTERN.matcher(getFirstLineMessage(text));
    if (matcher.matches()) {
      String version = matcher.group(3);
      hyperlinks.add(new InstallBuildToolsHyperlink(version, null));
    }
    return hyperlinks;
  }
}