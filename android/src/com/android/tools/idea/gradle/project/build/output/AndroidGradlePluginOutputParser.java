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

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.build.output.BuildOutputParser;
import java.util.Locale;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * Parses out warnings/errors generated by the Android Gradle Plugin. Only parses the first line of each warning/error since no delimiters
 * are present in the output. Everything is put into the Android Gradle Plugin if it starts with error: or warning:. Java compiler errors
 * will most likely contains a source file. If they don't there will be no easy way to tell them apart.
 */
public class AndroidGradlePluginOutputParser implements BuildOutputParser {
  /**
   * Name of the message group to show in the build window
   */
  @NotNull
  static final String ANDROID_GRADLE_PLUGIN_MESSAGES_GROUP = "Android Gradle Plugin";
  @NotNull
  private static final String WARNING_PREFIX = "warning:"; // Prefix used by the Android Gradle Plugin when reporting warnings.
  @NotNull
  private static final String ERROR_PREFIX = "error:"; // Prefix used by the Android Gradle Plugin when reporting error.

  @Override
  public boolean parse(String line, BuildOutputInstantReader reader, Consumer<? super BuildEvent> messageConsumer) {
    if (WARNING_PREFIX.regionMatches(true, 0, line, 0, WARNING_PREFIX.length())) {
      String message = line.substring(WARNING_PREFIX.length()).trim();
      messageConsumer
        .accept(
          new MessageEventImpl(reader.getParentEventId(), MessageEvent.Kind.WARNING, ANDROID_GRADLE_PLUGIN_MESSAGES_GROUP, message, message));
      return true;
    }
    if (ERROR_PREFIX.regionMatches(true, 0, line, 0, ERROR_PREFIX.length())) {
      String message = line.substring(ERROR_PREFIX.length()).trim();
      messageConsumer
        .accept(new MessageEventImpl(reader.getParentEventId(), MessageEvent.Kind.ERROR, ANDROID_GRADLE_PLUGIN_MESSAGES_GROUP, message, message));
      return true;
    }
    return false;
  }
}
