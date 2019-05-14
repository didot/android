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
package com.android.tools.idea.sdk;

import static com.android.tools.idea.sdk.IdeSdks.getJdkFromJavaHome;
import static com.intellij.ide.impl.NewProjectUtil.applyJdkToProject;
import static com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil.createAndAddSDK;
import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;
import static java.util.Collections.emptyList;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.sync.hyperlink.DownloadAndroidStudioHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.DownloadJdk8Hyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.SelectJdkFromFileSystemHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.UseEmbeddedJdkHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.UseJavaHomeAsJdkHyperlink;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.SystemProperties;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods related to IDEA JDKs.
 */
public class Jdks {
  @NotNull private static final Logger LOG = Logger.getInstance(Jdks.class);

  @NonNls public static final String DOWNLOAD_JDK_8_URL =
    "http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html";

  private static final LanguageLevel DEFAULT_LANG_LEVEL = JDK_1_8;

  @NotNull private final IdeInfo myIdeInfo;

  @NotNull
  public static Jdks getInstance() {
    return ServiceManager.getService(Jdks.class);
  }

  public Jdks(@NotNull IdeInfo ideInfo) {
    myIdeInfo = ideInfo;
  }

  @Nullable
  public Sdk chooseOrCreateJavaSdk() {
    return chooseOrCreateJavaSdk(null);
  }

  @Nullable
  public Sdk chooseOrCreateJavaSdk(@Nullable LanguageLevel langLevel) {
    if (langLevel == null) {
      langLevel = DEFAULT_LANG_LEVEL;
    }
    if (myIdeInfo.isAndroidStudio() && !IdeSdks.getInstance().isUsingEmbeddedJdk()) {
      File viableJdkPath = EmbeddedDistributionPaths.getInstance().tryToGetEmbeddedJdkPath();
      if (viableJdkPath == null) {
        // Set JRE that this process started with if no embedded JDK has been found.
        viableJdkPath = new File(System.getProperty("java.home"));
      }

      Sdk jdk = createJdk(viableJdkPath.getPath());
      assert jdk != null && isApplicableJdk(jdk, langLevel);
      return jdk;
    }
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (isApplicableJdk(sdk, langLevel)) {
        return sdk;
      }
    }
    String jdkHomePath = getJdkHomePath(langLevel);
    if (jdkHomePath != null) {
      return createJdk(jdkHomePath);
    }
    return null;
  }

  public boolean isApplicableJdk(@NotNull Sdk jdk) {
    return isApplicableJdk(jdk, null);
  }

  public boolean isApplicableJdk(@NotNull Sdk jdk, @Nullable LanguageLevel langLevel) {
    if (!(jdk.getSdkType() instanceof JavaSdk)) {
      return false;
    }
    if (langLevel == null) {
      langLevel = DEFAULT_LANG_LEVEL;
    }
    JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
    if (version != null) {
      return hasMatchingLangLevel(version, langLevel);
    }
    return false;
  }

  @Nullable
  private static String getJdkHomePath(@NotNull LanguageLevel langLevel) {
    Collection<String> jdkHomePaths = new ArrayList<>(JavaSdk.getInstance().suggestHomePaths());
    if (jdkHomePaths.isEmpty()) {
      return null;
    }
    // prefer jdk path of getJavaHome(), since we have to allow access to it in tests
    // see AndroidProjectDataServiceTest#testImportData()
    List<String> list = new ArrayList<>();
    String javaHome = SystemProperties.getJavaHome();

    if (javaHome != null && !javaHome.isEmpty()) {
      for (Iterator<String> it = jdkHomePaths.iterator(); it.hasNext(); ) {
        String path = it.next();

        if (path != null && javaHome.startsWith(path)) {
          it.remove();
          list.add(path);
        }
      }
    }
    list.addAll(jdkHomePaths);
    return getBestJdkHomePath(list, langLevel);
  }

  @Nullable
  private static String getBestJdkHomePath(@NotNull Collection<String> jdkHomePaths, @NotNull LanguageLevel langLevel) {
    // Search for JDKs in both the suggest folder and all its sub folders.
    List<String> roots = Lists.newArrayList();
    for (String jdkHomePath : jdkHomePaths) {
      if (isNotEmpty(jdkHomePath)) {
        roots.add(jdkHomePath);
        roots.addAll(getChildrenPaths(jdkHomePath));
      }
    }
    return getBestJdk(roots, langLevel);
  }

  @NotNull
  private static List<String> getChildrenPaths(@NotNull String dirPath) {
    File dir = new File(dirPath);
    if (!dir.isDirectory()) {
      return emptyList();
    }
    List<String> childrenPaths = Lists.newArrayList();
    for (File child : notNullize(dir.listFiles())) {
      boolean directory = child.isDirectory();
      if (directory) {
        childrenPaths.add(child.getAbsolutePath());
      }
    }
    return childrenPaths;
  }

  @Nullable
  private static String getBestJdk(@NotNull List<String> jdkRoots, @NotNull LanguageLevel langLevel) {
    String bestJdk = null;
    for (String jdkRoot : jdkRoots) {
      if (JavaSdk.getInstance().isValidSdkHome(jdkRoot)) {
        if (bestJdk == null && hasMatchingLangLevel(jdkRoot, langLevel)) {
          bestJdk = jdkRoot;
        }
        else if (bestJdk != null) {
          bestJdk = selectJdk(bestJdk, jdkRoot, langLevel);
        }
      }
    }
    return bestJdk;
  }

  @Nullable
  private static String selectJdk(@NotNull String jdk1, @NotNull String jdk2, @NotNull LanguageLevel langLevel) {
    if (hasMatchingLangLevel(jdk1, langLevel)) {
      return jdk1;
    }
    if (hasMatchingLangLevel(jdk2, langLevel)) {
      return jdk2;
    }
    return null;
  }

  private static boolean hasMatchingLangLevel(@NotNull String jdkRoot, @NotNull LanguageLevel langLevel) {
    JavaSdkVersion version = getVersion(jdkRoot);
    if (version == null) version = JavaSdkVersion.JDK_1_0;
    return hasMatchingLangLevel(version, langLevel);
  }

  @VisibleForTesting
  static boolean hasMatchingLangLevel(@NotNull JavaSdkVersion jdkVersion, @NotNull LanguageLevel langLevel) {
    LanguageLevel max = jdkVersion.getMaxLanguageLevel();
    return max.isAtLeast(langLevel);
  }

  @Nullable
  public JavaSdkVersion findVersion(@NotNull File jdkRoot) {
    return getVersion(jdkRoot.getPath());
  }

  @Nullable
  private static JavaSdkVersion getVersion(String jdkRoot) {
    String version = JavaSdk.getInstance().getVersionString(jdkRoot);
    return isEmpty(version) ? null : JavaSdkVersion.fromVersionString(version);
  }

  @Nullable
  public Sdk createJdk(@NotNull String jdkHomePath) {
    Sdk jdk = createAndAddSDK(jdkHomePath, JavaSdk.getInstance());
    if (jdk == null) {
      String msg = String.format("Unable to create JDK from path '%1$s'", jdkHomePath);
      LOG.error(msg);
    }
    return jdk;
  }

  @Nullable
  public Sdk createEmbeddedJdk() {
    if (myIdeInfo.isAndroidStudio()) {
      File path = EmbeddedDistributionPaths.getInstance().tryToGetEmbeddedJdkPath();
      if (path == null) {
        return null;
      }
      Sdk jdk = createJdk(path.getPath());
      assert jdk != null;
      return jdk;
    }
    return null;
  }

  public void setJdk(@NotNull Project project, @NotNull Sdk jdk) {
    applyJdkToProject(project, jdk);
  }

  @NotNull
  public List<NotificationHyperlink> getWrongJdkQuickFixes(@NotNull Project project) {
    List<NotificationHyperlink> quickFixes = Lists.newArrayList();

    if (myIdeInfo.isAndroidStudio()) {
      IdeSdks ideSdks = IdeSdks.getInstance();
      if (!ideSdks.isUsingJavaHomeJdk()) {
        String javaHome = getJdkFromJavaHome();
        if (javaHome != null) {
          if (ideSdks.validateJdkPath(new File(javaHome)) != null) {
            NotificationHyperlink useJavaHomeHyperlink = UseJavaHomeAsJdkHyperlink.create();
            if (useJavaHomeHyperlink != null) {
              quickFixes.add(useJavaHomeHyperlink);
            }
          }
        }
      }
      if (quickFixes.isEmpty()) {
        File embeddedJdkPath = EmbeddedDistributionPaths.getInstance().tryToGetEmbeddedJdkPath();
        if (embeddedJdkPath != null && isJdkRunnableOnPlatform(embeddedJdkPath.getAbsolutePath())) {
          quickFixes.add(new UseEmbeddedJdkHyperlink());
        }
        else {
          quickFixes.add(new DownloadAndroidStudioHyperlink());
        }
      }
    }

    quickFixes.add(new DownloadJdk8Hyperlink());

    NotificationHyperlink selectJdkHyperlink = SelectJdkFromFileSystemHyperlink.create(project);
    if (selectJdkHyperlink != null) {
      quickFixes.add(selectJdkHyperlink);
    }

    return quickFixes;
  }

  public static boolean isJdkRunnableOnPlatform(@NotNull Sdk jdk) {
    if (!(jdk.getSdkType() instanceof JavaSdk)) {
      return false;
    }

    if (!SystemInfo.isWindows || !SystemInfo.is32Bit) {
      // We only care about bitness compatibility on Windows. Elsewhere we just assume things are fine, because
      // nowadays virtually all Mac and Linux installations are 64 bits. No need to spend cycles on running 'java -version'
      return true;
    }

    JavaSdk javaSdk = (JavaSdk)jdk.getSdkType();
    String javaExecutablePath = javaSdk.getVMExecutablePath(jdk);
    return runAndCheckJVM(javaExecutablePath);
  }

  private static boolean isJdkRunnableOnPlatform(@NotNull String jdkHome) {
    return runAndCheckJVM(FileUtil.join(jdkHome, "bin", "java"));
  }

  private static boolean runAndCheckJVM(@NotNull String javaExecutablePath) {
    LOG.info("Checking java binary: " + javaExecutablePath);
    GeneralCommandLine commandLine = new GeneralCommandLine(javaExecutablePath);
    commandLine.addParameter("-version");
    try {
      CapturingAnsiEscapesAwareProcessHandler process = new CapturingAnsiEscapesAwareProcessHandler(commandLine);
      int exitCode = process.runProcess().getExitCode();
      return (exitCode == 0);
    }
    catch (ExecutionException e) {
      LOG.info("Could not invoke 'java -version'", e);
      return false;
    }
  }
}
