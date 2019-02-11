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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.blame.*;
import com.android.ide.common.blame.Message.Kind;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.intellij.build.FilePosition;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.FileMessageEventImpl;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.build.output.BuildOutputParser;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.function.Consumer;

import static com.android.ide.common.blame.parser.JsonEncodedGradleMessageParser.STDOUT_ERROR_TAG;

/**
 * Parser got errors returned by the Android Gradle Plugin in AGPBI json format.
 */
public class GradleBuildOutputParser implements BuildOutputParser {
  private static final String MESSAGES_GROUP = "Android errors";
  @VisibleForTesting static final String END_DETAIL = "* Try:";

  @NotNull private ArrayList<String> myBufferedLines = new ArrayList<>();
  @Nullable private Object myBuildId;

  @Override
  public boolean parse(@NotNull String line, @NotNull BuildOutputInstantReader reader, @NotNull Consumer<? super BuildEvent> messageConsumer) {
    // Clear lines if build id changed
    if (reader.getBuildId() != myBuildId) {
      myBufferedLines.clear();
      myBuildId = null;
    }

    if (line.startsWith(STDOUT_ERROR_TAG)) {
      // Message started, start storing lines
      myBuildId = reader.getBuildId();
      myBufferedLines.clear();
      myBufferedLines.add(line);
    }
    else if (line.equals(END_DETAIL)) {
      // Message just ended
      if (myBuildId != null) {
        processMessage(messageConsumer);
      }
      myBuildId = null;
      myBufferedLines.clear();
      return true;
    }
    else if (myBuildId != null) {
      myBufferedLines.add(line);
    }
    return false;
  }

  /**
   * Process an error message stored in myBufferedLines
   * @param messageConsumer
   */
  private void processMessage(@NotNull Consumer<? super MessageEvent> messageConsumer) {
    assert myBuildId != null;
    String line = myBufferedLines.get(0);
    String jsonString = line.substring(STDOUT_ERROR_TAG.length()).trim();
    if (jsonString.isEmpty()) {
      return;
    }
    String detailMessage = StringUtil.join(myBufferedLines, SystemProperties.getLineSeparator());
    GsonBuilder gsonBuilder = new GsonBuilder();
    MessageJsonSerializer.registerTypeAdapters(gsonBuilder);
    Gson gson = gsonBuilder.create();
    try {
      Message msg = gson.fromJson(jsonString, Message.class);
      boolean validPosition = false;
      for (SourceFilePosition sourceFilePosition : msg.getSourceFilePositions()) {
        FilePosition filePosition = convertToFilePosition(sourceFilePosition);
        if (filePosition != null) {
          validPosition = true;
          messageConsumer.accept(
            new FileMessageEventImpl(myBuildId, convertKind(msg.getKind()), MESSAGES_GROUP, msg.getText(), detailMessage, filePosition));
        }
      }
      if (!validPosition) {
        messageConsumer.accept(new MessageEventImpl(myBuildId, convertKind(msg.getKind()), MESSAGES_GROUP, msg.getText(), detailMessage));
      }
    }
    catch (JsonParseException ignored) {
      messageConsumer.accept(new MessageEventImpl(myBuildId, MessageEvent.Kind.WARNING, MESSAGES_GROUP, line, detailMessage));
    }
  }

  /**
   * Convert from {@link Message.Kind} to {@link MessageEvent.Kind}
   * @param kind a value from {@link Message.Kind}.
   * @return the equivalent {@link MessageEvent.Kind} or {@link MessageEvent.Kind#ERROR} if no correspondence exists.
   */
  @Contract(pure = true)
  @NotNull
  private static MessageEvent.Kind convertKind(@NotNull Kind kind) {
    switch (kind) {
      case ERROR:
        return MessageEvent.Kind.ERROR;
      case WARNING:
        return MessageEvent.Kind.WARNING;
      case INFO:
        return MessageEvent.Kind.INFO;
      case STATISTICS:
        return MessageEvent.Kind.STATISTICS;
      case UNKNOWN:
        return MessageEvent.Kind.ERROR;
      case SIMPLE:
        return MessageEvent.Kind.SIMPLE;
      default:
        return MessageEvent.Kind.ERROR;
    }
  }

  /**
   * Convert from {@link SourceFilePosition} to {@link FilePosition}
   * @param sourceFilePosition
   * @return converted FilePosition or null if the position is empty
   */
  @Nullable
  private static FilePosition convertToFilePosition(@NotNull SourceFilePosition sourceFilePosition) {
    File sourceFile = sourceFilePosition.getFile().getSourceFile();
    if (sourceFile == null) {
      return null;
    }
    SourcePosition position = sourceFilePosition.getPosition();
    int startLine = position.getStartLine();
    int endLine = position.getEndLine();
    int startColumn = position.getStartColumn();
    int endColumn = position.getEndColumn();
    return new FilePosition(sourceFile, startLine, startColumn, endLine, endColumn);
  }

  @VisibleForTesting
  boolean processingMessage() {
    return myBuildId != null;
  }
}
