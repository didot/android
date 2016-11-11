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
package com.android.tools.idea.gradle.project;

import com.android.tools.analytics.UsageTracker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.LocationAwareExternalSystemException;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.util.text.StringUtil.splitByLines;

/**
 * Provides better error messages for android projects import failures.
 */
public class ProjectImportErrorHandler extends AbstractProjectImportErrorHandler {

  private static final Pattern CLASS_NOT_FOUND_PATTERN = Pattern.compile("(.+) not found.");
  private static final Pattern ERROR_LOCATION_PATTERN = Pattern.compile(".* file '(.*)'( line: ([\\d]+))?");

  @Override
  @Nullable
  public ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    if (error instanceof ExternalSystemException) {
      // This is already a user-friendly error.
      //noinspection ThrowableResultOfMethodCallIgnored
      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                       .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                       .setGradleSyncFailure(GradleSyncFailure.UNKNOWN_GRADLE_FAILURE));

      return (ExternalSystemException)error;
    }

    Pair<Throwable, String> rootCauseAndLocation = getRootCauseAndLocation(error);
    Throwable rootCause = rootCauseAndLocation.getFirst();

    if (rootCause instanceof OutOfMemoryError) {
      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                       .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                       .setGradleSyncFailure(GradleSyncFailure.OUT_OF_MEMORY));

      // The OutOfMemoryError happens in the Gradle daemon process.
      String originalMessage = rootCause.getMessage();
      String msg = "Out of memory";
      if (originalMessage != null && !originalMessage.isEmpty()) {
        msg = msg + ": " + originalMessage;
      }
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof NoSuchMethodError) {
      String methodName = Strings.nullToEmpty(rootCause.getMessage());

      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                       .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                       .setGradleSyncFailure(GradleSyncFailure.METHOD_NOT_FOUND)
                                       .setGradleMissingSignature(methodName));

      String msg = String.format("Unable to find method '%1$s'.", methodName);
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof ClassNotFoundException) {
      String className = Strings.nullToEmpty(rootCause.getMessage());

      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                       .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                       .setGradleSyncFailure(GradleSyncFailure.CLASS_NOT_FOUND)
                                       .setGradleMissingSignature(className));

      Matcher matcher = CLASS_NOT_FOUND_PATTERN.matcher(className);
      if (matcher.matches()) {
        className = matcher.group(1);
      }

      String msg = String.format("Unable to load class '%1$s'.", className);
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    // Create ExternalSystemException or LocationAwareExternalSystemException, so that
    // it goes to SyncErrorHandlers directly.
    String location = rootCauseAndLocation.getSecond();
    String errMessage;
    if (rootCause.getMessage() == null) {
      StringWriter writer = new StringWriter();
      rootCause.printStackTrace(new PrintWriter(writer));
      errMessage = writer.toString();
    }
    else {
      errMessage = rootCause.getMessage();
    }

    if (!errMessage.isEmpty() && Character.isLowerCase(errMessage.charAt(0))) {
      // Message starts with lower case letter. Sentences should start with uppercase.
      errMessage = "Cause: " + errMessage;
    }

    ExternalSystemException exception = null;
    if (isNotEmpty(location)) {
      Pair<String, Integer> pair = getErrorLocation(location);
      if (pair != null) {
        exception = new LocationAwareExternalSystemException(errMessage, pair.first, pair.getSecond());
      }
    }
    if (exception == null) {
      exception = new ExternalSystemException(errMessage);
    }
    exception.initCause(rootCause);
    return exception;
  }

  // The default implementation in IDEA only retrieves the location in build.gradle files. This implementation also handle location in
  // settings.gradle file.
  @Override
  @Nullable
  public String getLocationFrom(@NotNull Throwable error) {
    String errorToString = error.toString();
    if (errorToString.contains("LocationAwareException")) {
      // LocationAwareException is never passed, but converted into a PlaceholderException that has the toString value of the original
      // LocationAwareException.
      String location = error.getMessage();
      if (location != null && (location.startsWith("Build file '") || location.startsWith("Settings file '"))) {
        // Only the first line contains the location of the error. Discard the rest.
        String[] lines = splitByLines(location);
        return lines.length > 0 ? lines[0] : null;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public ExternalSystemException createUserFriendlyError(@NotNull String msg, @Nullable String location, @NotNull String... quickFixes) {
    if (isNotEmpty(location)) {
      Pair<String, Integer> pair = getErrorLocation(location);
      if (pair != null) {
        return new LocationAwareExternalSystemException(msg, pair.first, pair.getSecond(), quickFixes);
      }
    }
    return new ExternalSystemException(msg, null, quickFixes);
  }

  @VisibleForTesting
  @Nullable
  static Pair<String, Integer> getErrorLocation(@NotNull String location) {
    Matcher matcher = ERROR_LOCATION_PATTERN.matcher(location);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      int line = -1;
      String lineAsText = matcher.group(3);
      if (lineAsText != null) {
        try {
          line = Integer.parseInt(lineAsText);
        }
        catch (NumberFormatException e) {
          // ignored.
        }
      }
      return Pair.create(filePath, line);
    }
    return null;
  }
}
