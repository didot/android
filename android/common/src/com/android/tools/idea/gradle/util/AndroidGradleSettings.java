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
package com.android.tools.idea.gradle.util;

import com.android.SdkConstants;
import com.google.common.base.Strings;
import com.google.common.io.Closeables;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * Utility methods related to Gradle-specific Android settings.
 */
public final class AndroidGradleSettings {
  private static final Logger LOG = Logger.getInstance(AndroidGradleSettings.class);

  @NonNls private static final String JVM_ARG_FORMAT = "-D%1$s=%2$s";
  @NonNls private static final String ANDROID_HOME_JVM_ARG = "android.home";

  private AndroidGradleSettings() {
  }

  /**
   * Indicates whether the path of the Android SDK home directory is specified in a local.properties file or in the ANDROID_HOME environment
   * variable.
   *
   * @param projectDir the project directory.
   * @return {@code true} if the Android SDK home directory is specified in the project's local.properties file or in the ANDROID_HOME
   *         environment variable; {@code false} otherwise.
   */
  public static boolean isAndroidHomeKnown(@NotNull File projectDir) {
    String androidHome = getAndroidHomeFromLocalPropertiesFile(projectDir);
    if (!Strings.isNullOrEmpty(androidHome)) {
      String msg = String.format("Found Android SDK home at '%1$s' (from local.properties file)", androidHome);
      LOG.info(msg);
      return true;
    }
    return false;
  }

  @Nullable
  private static String getAndroidHomeFromLocalPropertiesFile(@NotNull File projectDir) {
    File filePath = new File(projectDir, SdkConstants.FN_LOCAL_PROPERTIES);
    if (!filePath.isFile()) {
      return null;
    }
    Properties properties = new Properties();
    FileInputStream fileInputStream = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      fileInputStream = new FileInputStream(filePath);
      properties.load(fileInputStream);
    } catch (FileNotFoundException e) {
      return null;
    } catch (IOException e) {
      String msg = String.format("Failed to read file '%1$s'", filePath.getPath());
      LOG.error(msg, e);
      return null;
    } finally {
      Closeables.closeQuietly(fileInputStream);
    }
    return properties.getProperty(SdkConstants.SDK_DIR_PROPERTY);
  }

  @NotNull
  public static String createAndroidHomeJvmArg(@NotNull String androidHome) {
    return createJvmArg(ANDROID_HOME_JVM_ARG, androidHome);
  }

  @NotNull
  public static String createJvmArg(@NotNull String name, @NotNull String value) {
    return String.format(JVM_ARG_FORMAT, name, value);
  }
}
