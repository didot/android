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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.Strings;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.util.Pair;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler;

import java.io.File;
import java.util.regex.Pattern;

/**
 * Provides better error messages for project import failures.
 */
public class ProjectImportErrorHandler extends AbstractProjectImportErrorHandler {
  public static final String FAILED_TO_PARSE_SDK = "failed to parse SDK";
  public static final String INSTALL_ANDROID_SUPPORT_REPO = "Please install the Android Support Repository from the Android SDK Manager.";
  public static final String INSTALL_MISSING_PLATFORM = "Please install the missing platform from the Android SDK Manager.";
  public static final String FIX_SDK_DIR_PROPERTY = "Please fix the 'sdk.dir' property in the local.properties file.";

  private static final Pattern SDK_NOT_FOUND = Pattern.compile("The SDK directory '(.*?)' does not exist.");

  private static final String EMPTY_LINE = "\n\n";
  private static final String UNSUPPORTED_GRADLE_VERSION_ERROR =
    "Gradle version " + GradleUtil.GRADLE_MINIMUM_VERSION + " is required";
  private static final String SDK_DIR_PROPERTY_MISSING = "No sdk.dir property defined in local.properties file.";

  @Override
  @Nullable
  public ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    if (error instanceof ExternalSystemException) {
      // This is already a user-friendly error.
      return (ExternalSystemException)error;
    }

    Pair<Throwable, String> rootCauseAndLocation = getRootCauseAndLocation(error);
    Throwable rootCause = rootCauseAndLocation.getFirst();

    String location = rootCauseAndLocation.getSecond();
    if (location == null && !Strings.isNullOrEmpty(buildFilePath)) {
      location = String.format("Build file: '%1$s'", buildFilePath);
    }

    if (isOldGradleVersion(rootCause)) {
      String msg = String.format("You are using an old, unsupported version of Gradle. Please use version %1$s or greater.",
                                 GradleUtil.GRADLE_MINIMUM_VERSION);
      msg += ('\n' + FIX_GRADLE_VERSION);
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof IllegalStateException) {
      String msg = rootCause.getMessage();
      if (msg != null && msg.startsWith("failed to find target android-")) {
        String newMsg = msg + EMPTY_LINE + INSTALL_MISSING_PLATFORM;
        // Location of build.gradle is useless for this error. Omitting it.
        return createUserFriendlyError(newMsg, null);
      }
    }

    if (rootCause instanceof OutOfMemoryError) {
      // The OutOfMemoryError happens in the Gradle daemon process.
      String originalMessage = rootCause.getMessage();
      String msg = "Out of memory";
      if (originalMessage != null && !originalMessage.isEmpty()) {
        msg = msg + ": " + originalMessage;
      }
      if (msg.endsWith("Java heap space")) {
        msg += ". Configure Gradle memory settings using '-Xmx' JVM option (e.g. '-Xmx2048m'.)";
      } else if (!msg.endsWith(".")) {
        msg += ".";
      }
      msg += EMPTY_LINE + OPEN_GRADLE_SETTINGS;
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof ClassNotFoundException) {
      String msg = String.format("Unable to load class '%1$s'.", rootCause.getMessage()) + EMPTY_LINE +
                   UNEXPECTED_ERROR_FILE_BUG;
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof RuntimeException) {
      String msg = rootCause.getMessage();

      // With this condition we cover 2 similar messages about the same problem.
      if (msg != null && msg.contains("Could not find") && msg.contains("com.android.support:support")) {
        // We keep the original error message and we append a hint about how to fix the missing dependency.
        String newMsg = msg + EMPTY_LINE + INSTALL_ANDROID_SUPPORT_REPO;
        // Location of build.gradle is useless for this error. Omitting it.
        return createUserFriendlyError(newMsg, null);
      }

      if (msg != null && msg.contains(FAILED_TO_PARSE_SDK)) {
        String newMsg = msg + EMPTY_LINE + "The Android SDK may be missing the directory 'add-ons'.";
        // Location of build.gradle is useless for this error. Omitting it.
        return createUserFriendlyError(newMsg, null);
      }

      if (msg != null && (msg.equals(SDK_DIR_PROPERTY_MISSING) || SDK_NOT_FOUND.matcher(msg).matches())) {
        String newMsg = msg;
        File buildProperties = new File(projectPath, SdkConstants.FN_LOCAL_PROPERTIES);
        if (buildProperties.isFile()) {
          newMsg += EMPTY_LINE + FIX_SDK_DIR_PROPERTY;
        }
        return createUserFriendlyError(newMsg, null);
      }
    }

    return createUserFriendlyError(rootCause.getMessage(), location);
  }

  private static boolean isOldGradleVersion(@NotNull Throwable error) {
    if (error instanceof UnsupportedVersionException) {
      return true;
    }
    if (error instanceof UnsupportedMethodException) {
      String msg = error.getMessage();
      if (msg != null && msg.contains("GradleProject.getBuildScript")) {
        return true;
      }
    }
    if (error instanceof ClassNotFoundException) {
      String msg = error.getMessage();
      if (msg != null && msg.contains(ToolingModelBuilderRegistry.class.getName())) {
        return true;
      }
    }
    if (error instanceof RuntimeException) {
      String msg = error.getMessage();
      if (msg != null && msg.startsWith(UNSUPPORTED_GRADLE_VERSION_ERROR)) {
        return true;
      }
    }
    String errorToString = error.toString();
    return errorToString != null && errorToString.startsWith("org.gradle.api.internal.MissingMethodException");
  }
}
