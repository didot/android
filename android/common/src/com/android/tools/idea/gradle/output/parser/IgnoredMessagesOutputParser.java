/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.output.parser;

import com.android.tools.idea.gradle.output.GradleMessage;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Pattern;

class IgnoredMessagesOutputParser implements CompilerOutputParser {
  private static final Pattern ERROR_COUNT_PATTERN = Pattern.compile("[\\d]+ error(s)?");

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<GradleMessage> messages)
    throws ParsingFailedException {
    return line.trim().equalsIgnoreCase("FAILED") || ERROR_COUNT_PATTERN.matcher(line).matches();
  }
}
