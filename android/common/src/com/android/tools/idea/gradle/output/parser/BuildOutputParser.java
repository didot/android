/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.ide.common.blame.output.GradleMessage;
import com.android.ide.common.blame.parser.ParsingFailedException;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.blame.parser.aapt.AaptOutputParser;
import com.android.ide.common.blame.parser.util.OutputLineReader;
import com.android.tools.idea.gradle.output.parser.androidPlugin.*;
import com.android.tools.idea.gradle.output.parser.javac.JavacOutputParser;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Parses Gradle's build output and creates the messages to be displayed in the "Messages" tool window.
 */
public class BuildOutputParser{
  private static final PatternAwareOutputParser[] PARSERS =
    {new AndroidPluginOutputParser(), new GradleOutputParser(), new AaptOutputParser(), new XmlValidationErrorParser(),
      new BuildFailureParser(), new ManifestMergeFailureParser(), new DexExceptionParser(), new JavacOutputParser(),
      new MergingExceptionParser()};

  private final ToolOutputParser parser;

  public BuildOutputParser() {
    parser = new ToolOutputParser(PARSERS, new MessageBuildingSdkLog());
  }

  public List<GradleMessage> parseGradleOutput(String output) {
    return parser.parseToolOutput(output);
  }
}
