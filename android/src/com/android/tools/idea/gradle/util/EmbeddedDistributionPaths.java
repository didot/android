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
package com.android.tools.idea.gradle.util;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.android.download.AndroidProfilerDownloader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.sdk.IdeSdks.MAC_JDK_CONTENT_PATH;
import static com.intellij.openapi.util.io.FileUtil.*;

public class EmbeddedDistributionPaths {
  @NotNull
  public static EmbeddedDistributionPaths getInstance() {
    return ServiceManager.getService(EmbeddedDistributionPaths.class);
  }

  @NotNull
  public List<File> findAndroidStudioLocalMavenRepoPaths() {
    File defaultRootDirPath = getDefaultRootDirPath();
    if (defaultRootDirPath != null) {
      // Release build
      File repoPath = new File(defaultRootDirPath, "m2repository");
      return repoPath.isDirectory() ? ImmutableList.of(repoPath) : ImmutableList.of();
    }
    // Development build
    List<File> repoPaths = new ArrayList<>();

    // Add prebuilt offline repo
    String studioCustomRepo = System.getenv("STUDIO_CUSTOM_REPO");
    if (studioCustomRepo != null) {
      File customRepoPath = new File(toCanonicalPath(toSystemDependentName(studioCustomRepo)));
      if (!customRepoPath.isDirectory()) {
        throw new IllegalArgumentException("Invalid path in STUDIO_CUSTOM_REPO environment variable");
      }
      repoPaths.add(customRepoPath);
    }
    else {
      File localGMaven = new File(PathManager.getHomePath() + toSystemDependentName("/../../out/repo"));
      if (localGMaven.isDirectory()) {
        repoPaths.add(localGMaven);
      }
      File prebuiltOfflineM2 = new File(toCanonicalPath(getIdeHomePath() + toSystemDependentName("/../../prebuilts/tools/common/offline-m2")));
      getLog().info("Looking for embedded Maven repo at '" + prebuiltOfflineM2.getPath() + "'");
      if (prebuiltOfflineM2.isDirectory()) {
        repoPaths.add(prebuiltOfflineM2);
      }
    }

    // Add locally published offline studio repo
    File localOfflineRepoPath = new File(PathManager.getHomePath() + toSystemDependentName("/../../out/studio/repo"));
    if (localOfflineRepoPath.isDirectory()) {
      repoPaths.add(localOfflineRepoPath);
    }

    if (StudioFlags.SHIPPED_SYNC_ENABLED.get()) {
      // Add a repo generated from/for New Project Wizard projects
      File npwRepoPath = new File(PathManager.getHomePath() +
                                  toSystemDependentName("/../adt/idea/android/testData/nosyncbuilder/offline_repo"));
      if (npwRepoPath.isDirectory()) {
        repoPaths.add(npwRepoPath);
      }
    }

    return repoPaths;
  }

  @NotNull
  public File findEmbeddedProfilerTransform(@NotNull AndroidVersion version) {
    String path = "plugins/android/resources/profilers-transform.jar";
    File file = new File(PathManager.getHomePath(), path);
    if (file.exists()) {
      return file;
    }
    AndroidProfilerDownloader.makeSureProfilerIsInPlace();
    File dir = AndroidProfilerDownloader.getHostDir(path);
    if (dir.exists()) {
      return dir;
    }
    // Development build
    String relativePath = toSystemDependentName("/../../bazel-genfiles/tools/base/profiler/transform/profilers-transform.jar");
    return new File(PathManager.getHomePath() + relativePath);
  }

  @Nullable
  public File findEmbeddedGradleDistributionPath() {
    File distributionPath = getDefaultRootDirPath();
    if (distributionPath != null) {
      // Release build
      Logger log = getLog();
      File embeddedPath = new File(distributionPath, "gradle-" + GRADLE_LATEST_VERSION);
      log.info("Looking for embedded Gradle distribution at '" + embeddedPath.getPath() + "'");
      if (embeddedPath.isDirectory()) {
        log.info("Found embedded Gradle " + GRADLE_LATEST_VERSION);
        return embeddedPath;
      }
      log.info("Unable to find embedded Gradle " + GRADLE_LATEST_VERSION);
      return null;
    }

    // Development build.
    String ideHomePath = getIdeHomePath();
    String relativePath = toSystemDependentName("/../../tools/external/gradle");
    distributionPath = new File(toCanonicalPath(ideHomePath + relativePath));
    if (distributionPath.isDirectory()) {
      return distributionPath;
    }

    // Development build.
    String localDistributionPath = System.getProperty("local.gradle.distribution.path");
    if (localDistributionPath != null) {
      distributionPath = new File(toCanonicalPath(localDistributionPath));
      if (distributionPath.isDirectory()) {
        return distributionPath;
      }
    }

    return null;
  }

  @Nullable
  public File findEmbeddedGradleDistributionFile(@NotNull String gradleVersion) {
    File distributionPath = findEmbeddedGradleDistributionPath();
    if (distributionPath != null) {
      File allDistributionFile = new File(distributionPath, "gradle-" + gradleVersion + "-all.zip");
      if (allDistributionFile.isFile() && allDistributionFile.exists()) {
        return allDistributionFile;
      }

      File binDistributionFile = new File(distributionPath, "gradle-" + gradleVersion + "-bin.zip");
      if (binDistributionFile.isFile() && binDistributionFile.exists()) {
        return binDistributionFile;
      }
    }
    return null;
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(EmbeddedDistributionPaths.class);
  }

  @Nullable
  private static File getDefaultRootDirPath() {
    String ideHomePath = getIdeHomePath();
    File rootDirPath = new File(ideHomePath, "gradle");
    return rootDirPath.isDirectory() ? rootDirPath : null;
  }

  @Nullable
  public File tryToGetEmbeddedJdkPath() {
    try {
      return getEmbeddedJdkPath();
    }
    catch (Throwable t) {
      Logger.getInstance(EmbeddedDistributionPaths.class).warn("Failed to find a valid embedded JDK", t);
      return null;
    }
  }

  @NotNull
  public File getEmbeddedJdkPath() {
    String ideHomePath = getIdeHomePath();

    File jdkRootPath = new File(ideHomePath, SystemInfo.isMac ? join("jre", "jdk") : "jre");
    if (jdkRootPath.isDirectory()) {
      // Release build.
      return getSystemSpecificJdkPath(jdkRootPath);
    }

    // If AndroidStudio runs from IntelliJ IDEA sources
    if (System.getProperty("android.test.embedded.jdk") != null) {
      File jdkDir = new File(System.getProperty("android.test.embedded.jdk"));
      assert jdkDir.exists();
      return jdkDir;
    }

    // Development build.
    String jdkDevPath = System.getProperty("studio.dev.jdk", ideHomePath + "/../../prebuilts/studio/jdk");
    String relativePath = toSystemDependentName(jdkDevPath);
    jdkRootPath = new File(toCanonicalPath(relativePath));
    if (SystemInfo.isWindows) {
      jdkRootPath = new File(jdkRootPath, "win64");
    }
    else if (SystemInfo.isLinux) {
      jdkRootPath = new File(jdkRootPath, "linux");
    }
    else if (SystemInfo.isMac) {
      jdkRootPath = new File(jdkRootPath, "mac");
    }
    return getSystemSpecificJdkPath(jdkRootPath);
  }

  @NotNull
  private static File getSystemSpecificJdkPath(File jdkRootPath) {
    if (SystemInfo.isMac) {
      jdkRootPath = new File(jdkRootPath, MAC_JDK_CONTENT_PATH);
    }
    if (!jdkRootPath.isDirectory()) {
      throw new Error(String.format("Incomplete or corrupted installation - \"%s\" directory does not exist", jdkRootPath.toString()));
    }
    return jdkRootPath;
  }

  @NotNull
  private static String getIdeHomePath() {
    return toSystemDependentName(PathManager.getHomePath());
  }
}
