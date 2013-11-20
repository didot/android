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
package com.android.tools.idea.gradle.service.notification;

import com.android.SdkConstants;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationExtension;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.project.ProjectImportErrorHandler.*;

public class GradleNotificationExtension implements ExternalSystemNotificationExtension {
  private static final Pattern ERROR_LOCATION_IN_FILE_PATTERN = Pattern.compile("Build file '(.*)' line: ([\\d]+)");
  private static final Pattern ERROR_IN_FILE_PATTERN = Pattern.compile("Build file '(.*)'");
  private static final Pattern MISSING_DEPENDENCY_PATTERN = Pattern.compile("Could not find (.*)\\.");

  private static final NotificationType DEFAULT_NOTIFICATION_TYPE = NotificationType.ERROR;

  @NotNull
  @Override
  public ProjectSystemId getTargetExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @Nullable
  @Override
  public CustomizationResult customize(@NotNull Project project, @NotNull Throwable error, @Nullable UsageHint hint) {
    Throwable cause = error;
    if (error instanceof UndeclaredThrowableException) {
      cause = ((UndeclaredThrowableException)error).getUndeclaredThrowable();
      if (cause instanceof InvocationTargetException) {
        cause = ((InvocationTargetException)cause).getTargetException();
      }
    }
    if (cause instanceof ExternalSystemException) {
      return createNotification(project, (ExternalSystemException)cause);
    }
    return null;
  }

  @Nullable
  private static CustomizationResult createNotification(@NotNull Project project, @NotNull ExternalSystemException error) {
    String msg = error.getMessage();
    if (msg != null && !msg.isEmpty()) {
      if (msg.startsWith("Project is using an old version of the Android Gradle plug-in")) {
        //noinspection TestOnlyProblems
        return createNotification(project, msg, new SearchInBuildFilesHyperlink("com.android.tools.build:gradle"));
      }

      if (msg.contains(FAILED_TO_PARSE_SDK)) {
        String pathOfBrokenSdk = findPathOfSdkMissingOrEmptyAddonsFolder(project);
        String newMsg;
        if (pathOfBrokenSdk != null) {
          newMsg = String.format("The directory '%1$s', in the Android SDK at '%2$s', is either missing or empty", SdkConstants.FD_ADDONS,
                                 pathOfBrokenSdk);
          File sdkHomeDir = new File(pathOfBrokenSdk);
          if (!sdkHomeDir.canWrite()) {
            newMsg += String.format("\n\nCurrent user (%1$s) does not have write access to the SDK directory.", SystemProperties.getUserName());
          }
        }
        else {
          newMsg = splitLines(msg).get(0);
        }
        //noinspection TestOnlyProblems
        return createNotification(project, newMsg);
      }

      List<String> lines = splitLines(msg);
      String firstLine = lines.get(0);
      String lastLine = lines.get(lines.size() - 1);

      if (lastLine != null && lastLine.equals(OPEN_GRADLE_SETTINGS)) {
        //noinspection TestOnlyProblems
        return createNotification(project, msg, new OpenGradleSettingsHyperlink());
      }

      if (lastLine != null && (lastLine.contains(INSTALL_ANDROID_SUPPORT_REPO) || lastLine.contains(INSTALL_MISSING_PLATFORM))) {
        List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
        if (!facets.isEmpty()) {
          // We can only open SDK manager if the project has an Android facet. Android facet has a reference to the Android SDK manager.
          //noinspection TestOnlyProblems
          return createNotification(project, msg, new OpenAndroidSdkManagerHyperlink());
        }
        //noinspection TestOnlyProblems
        return createNotification(project, msg);
      }

      if (lastLine != null && lastLine.contains(SET_UP_HTTP_PROXY)) {
        //noinspection TestOnlyProblems
        return createNotification(project, msg, new OpenHttpSettingsHyperlink(), new OpenUrlHyperlink(
          "http://www.gradle.org/docs/current/userguide/userguide_single.html#sec:accessing_the_web_via_a_proxy",
          "Open Gradle documentation"));
      }

      if (lastLine != null && lastLine.contains(FIX_GRADLE_VERSION)) {
        List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
        NotificationHyperlink fixGradleVersionHyperlink = FixGradleVersionInWrapperHyperlink.createIfProjectUsesGradleWrapper(project);
        if (fixGradleVersionHyperlink != null) {
          hyperlinks.add(fixGradleVersionHyperlink);
        }
        hyperlinks.add(new OpenGradleSettingsHyperlink());
        //noinspection TestOnlyProblems
        return createNotification(project, msg, hyperlinks.toArray(new NotificationHyperlink[hyperlinks.size()]));
      }

      Matcher matcher = MISSING_DEPENDENCY_PATTERN.matcher(firstLine);
      if (matcher.matches() && lines.size() > 1 && lines.get(1).startsWith("Required by:")) {
        String dependency = matcher.group(1);
        if (!Strings.isNullOrEmpty(dependency)) {
          if (lastLine != null) {
            Pair<String, Integer> errorLocation = getErrorLocation(lastLine);
            if (errorLocation != null) {
              // We have a location in file, show the "Open File" hyperlink.
              String filePath = errorLocation.getFirst();
              int line = errorLocation.getSecond();
              //noinspection TestOnlyProblems
              return createNotification(project, msg, new OpenFileHyperlink(filePath, line - 1),
                                        new SearchInBuildFilesHyperlink(dependency));
            }
          }
          //noinspection TestOnlyProblems
          return createNotification(project, msg, new SearchInBuildFilesHyperlink(dependency));
        }
      }

      if (lastLine != null) {
        if (lastLine.contains(UNEXPECTED_ERROR_FILE_BUG)) {
          //noinspection TestOnlyProblems
          return createNotification(project, msg, new FileBugHyperlink(), new ShowLogHyperlink());
        }

        Pair<String, Integer> errorLocation = getErrorLocation(lastLine);
        if (errorLocation != null) {
          String filePath = errorLocation.getFirst();
          int line = errorLocation.getSecond();
          //noinspection TestOnlyProblems
          return createNotification(project, msg, new OpenFileHyperlink(filePath, line - 1));
        }
      }
    }
    return null;
  }

  @Nullable
  private static Pair<String, Integer> getErrorLocation(@NotNull String msg) {
    Matcher matcher = ERROR_LOCATION_IN_FILE_PATTERN.matcher(msg);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      int line = -1;
      try {
        line = Integer.parseInt(matcher.group(2));
      }
      catch (NumberFormatException e) {
        // ignored.
      }
      return Pair.create(filePath, line);
    }

    matcher = ERROR_IN_FILE_PATTERN.matcher(msg);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      return Pair.create(filePath, -1);
    }
    return null;
  }

  @Nullable
  private static String findPathOfSdkMissingOrEmptyAddonsFolder(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
      if (moduleSdk != null && moduleSdk.getSdkType().equals(AndroidSdkType.getInstance())) {
        String sdkHomeDirPath = moduleSdk.getHomePath();
        File addonsDir = new File(sdkHomeDirPath, SdkConstants.FD_ADDONS);
        if (!addonsDir.isDirectory() || FileUtil.notNullize(addonsDir.listFiles()).length == 0) {
          return sdkHomeDirPath;
        }
      }
    }
    return null;
  }

  @NotNull
  private static List<String> splitLines(@NotNull String s) {
    return Lists.newArrayList(Splitter.on('\n').split(s));
  }

  @NotNull
  @VisibleForTesting
  static CustomizationResult createNotification(@NotNull Project project,
                                                @NotNull String errorMsg,
                                                @NotNull NotificationHyperlink... hyperlinks) {
    String text = errorMsg;
    NotificationListener notificationListener = null;
    int hyperlinkCount = hyperlinks.length;
    if (hyperlinkCount > 0) {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < hyperlinkCount; i++) {
        b.append(hyperlinks[i].toString());
        if (i < hyperlinkCount - 1) {
          b.append(" ");
        }
      }
      text += ('\n' + b.toString());
      notificationListener = new CustomNotificationListener(project, hyperlinks);
    }
    String title = String.format("Failed to refresh Gradle project '%1$s':", project.getName());
    return new CustomizationResult(title, text, DEFAULT_NOTIFICATION_TYPE, notificationListener);
  }
}
