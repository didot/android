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
package com.android.tools.idea.welcome;

import com.android.annotations.VisibleForTesting;
import com.android.prefs.AndroidLocation;
import com.android.tools.idea.wizard.WizardUtils;
import com.google.common.base.Objects;
import com.google.common.io.Closeables;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Wrapper around data passed from the installer.
 */
public class InstallerData {
  public static final String PATH_FIRST_RUN_PROPERTIES = FileUtil.join("studio", "installer", "firstrun.properties");
  @Nullable private final String myJavaDir;
  @Nullable private final String myAndroidSrc;
  @Nullable private final String myAndroidDest;

  @VisibleForTesting
  InstallerData(@Nullable String javaDir, @Nullable String androidSrc, @Nullable String androidDest) {
    myJavaDir = javaDir;
    myAndroidSrc = androidSrc;
    myAndroidDest = androidDest;
  }

  @Nullable
  private static InstallerData parse() {
    Properties properties = readProperties();
    if (properties == null) {
      return null;
    }
    return new InstallerData(getIfExists(properties, "jdk.dir"), getIfExists(properties, "androidsdk.repo"),
                             properties.getProperty("androidsdk.dir"));
  }

  @Nullable
  private static Properties readProperties() {
    try {
      File file = new File(AndroidLocation.getFolder(), PATH_FIRST_RUN_PROPERTIES);
      if (file.isFile()) {
        FileInputStream stream = null;
        try {
          stream = new FileInputStream(file);
          Properties properties = new Properties();
          properties.load(stream);
          return properties;
        }
        catch (IOException e) {
          Logger.getInstance(InstallerData.class).error(e);
        }
        finally {
          Closeables.closeQuietly(stream);
        }
      }
    }
    catch (AndroidLocation.AndroidLocationException e) {
      Logger.getInstance(InstallerData.class).error(e);
    }
    return null;
  }

  @Nullable
  private static String getIfExists(Properties properties, String propertyName) {
    String path = properties.getProperty(propertyName);
    if (!StringUtil.isEmptyOrSpaces(path)) {
      File file = new File(path);
      return file.isDirectory() ? file.getAbsolutePath() : null;
    }
    return null;
  }

  @VisibleForTesting
  static synchronized void set(@Nullable InstallerData data) {
    Holder.INSTALLER_DATA = data;
  }

  @Nullable
  public static synchronized InstallerData get() {
    return Holder.INSTALLER_DATA;
  }

  @Nullable
  public String getJavaDir() {
    return myJavaDir;
  }

  @Nullable
  public String getAndroidSrc() {
    return myAndroidSrc;
  }

  @Nullable
  public String getAndroidDest() {
    return myAndroidDest;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).add("jdk.dir", myJavaDir).add("androidsdk.repo", myAndroidSrc).add("androidsdk.dir", myAndroidDest)
      .toString();
  }

  public boolean hasValidSdkLocation() {
    return !WizardUtils.validateLocation(getAndroidDest(), SdkComponentsStep.FIELD_SDK_LOCATION, false).isError();
  }

  public boolean hasValidJdkLocation() {
    return JdkLocationStep.validateJdkLocation(getJavaDir()) == null;
  }

  private static class Holder {
    @Nullable private static InstallerData INSTALLER_DATA = parse();
  }
}
