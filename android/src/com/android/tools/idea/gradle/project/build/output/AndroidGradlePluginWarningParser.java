/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output;

import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.build.output.BuildOutputParser;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Parses out warnings generated by the Android Gradle Plugin. Only parses the first line of each warning since no delimiters are
 * present in the output.
 */
public class AndroidGradlePluginWarningParser implements BuildOutputParser {
  @NotNull
  private static final String ANDROID_MESSAGES_GROUP = "Android Gradle Plugin"; // Name of the message group to show in the build window
  @NotNull
  private static final String WARNING_PREFIX = "warning:"; // Prefix used by the Android Gradle Plugin when reporting errors.

  @Override
  public boolean parse(String line, BuildOutputInstantReader reader, Consumer<? super MessageEvent> messageConsumer) {
    if (WARNING_PREFIX.regionMatches(true, 0, line, 0, WARNING_PREFIX.length())) {
      String message = line.substring(WARNING_PREFIX.length()).trim();
      messageConsumer
        .accept(new MessageEventImpl(reader.getBuildId(), MessageEvent.Kind.WARNING, ANDROID_MESSAGES_GROUP, message, message));
      return true;
    }
    return false;
  }
}
