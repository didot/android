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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.gradle.project.sync.common.CommandLineArgs.isInTestingMode;
import static com.android.tools.idea.sdk.IdeSdks.MAC_JDK_CONTENT_PATH;
import static com.intellij.openapi.util.io.FileUtil.*;

public class EmbeddedDistributionPaths {
  @NotNull
  public static EmbeddedDistributionPaths getInstance() {
    return ServiceManager.getService(EmbeddedDistributionPaths.class);
  }

  @NotNull
  public List<File> findAndroidStudioLocalMavenRepoPaths() {
    if (!StudioFlags.USE_DEVELOPMENT_OFFLINE_REPOS.get() && !isInTestingMode()) {
      return ImmutableList.of();
    }
    return doFindAndroidStudioLocalMavenRepoPaths();
  }

  @VisibleForTesting
  @NotNull
  static List<File> doFindAndroidStudioLocalMavenRepoPaths() {
    List<File> repoPaths = new ArrayList<>();
    // Repo path candidates, the path should be relative to tools/idea.
    List<String> repoCandidates = new ArrayList<>();
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
      repoCandidates.add("/../../out/repo");
    }

    // Add locally published offline studio repo
    repoCandidates.add("/../../out/studio/repo");
    // Add prebuilts repo.
    repoCandidates.add("/../../prebuilts/tools/common/m2/repository");

    for (String candidate : repoCandidates) {
      File offlineRepo = new File(toCanonicalPath(PathManager.getHomePath() + toSystemDependentName(candidate)));
      if (offlineRepo.isDirectory()) {
        repoPaths.add(offlineRepo);
      }
    }

    return ImmutableList.copyOf(repoPaths);
  }

  @NotNull
  public File findEmbeddedProfilerTransform() {
    File file = new File(PathManager.getHomePath(), "plugins/android/resources/profilers-transform.jar");
    if (file.exists()) {
      return file;
    }

    // Development build
    String relativePath = toSystemDependentName("/../../bazel-bin/tools/base/profiler/transform/profilers-transform.jar");
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
    if (SystemInfo.isJavaVersionAtLeast(11, 0, 0)) {
      jdkRootPath = new File(jdkRootPath, "jdk11");
    }
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
