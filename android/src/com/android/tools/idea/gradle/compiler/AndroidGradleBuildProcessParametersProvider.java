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
package com.android.tools.idea.gradle.compiler;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.net.HttpConfigurable;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createJvmArg;

/**
 * Adds Gradle jars to the build process' classpath and adds extra Gradle-related configuration options.
 */
public class AndroidGradleBuildProcessParametersProvider extends BuildProcessParametersProvider {
  private static final Logger LOG = Logger.getInstance(AndroidGradleBuildProcessParametersProvider.class);
  private static final ProjectSystemId SYSTEM_ID = GradleConstants.SYSTEM_ID;

  @NotNull private final Project myProject;

  private List<String> myClasspath;

  public AndroidGradleBuildProcessParametersProvider(@NotNull Project project) {
    myProject = project;
  }

  /**
   * Adds Gradle to the build process' classpath.
   *
   * @return a list containing the locations of all jars necessary to include Gradle in the classpath.
   */
  @Override
  @NotNull
  public List<String> getClassPath() {
    if (myClasspath == null) {
      myClasspath = getGradleClassPath();
    }
    return myClasspath;
  }

  @NotNull
  private static List<String> getGradleClassPath() {
    String gradleLibDirPath = null;
    String gradleToolingApiJarPath = PathUtil.getJarPathForClass(ProjectConnection.class);
    if (!Strings.isNullOrEmpty(gradleToolingApiJarPath)) {
      gradleLibDirPath = PathUtil.getParentPath(gradleToolingApiJarPath);
    }
    if (Strings.isNullOrEmpty(gradleLibDirPath)) {
      return Collections.emptyList();
    }
    List<String> classpath = Lists.newArrayList();
    File gradleLibDir = new File(gradleLibDirPath);
    if (!gradleLibDir.isDirectory()) {
      return Collections.emptyList();
    }
    File[] children = FileUtil.notNullize(gradleLibDir.listFiles());
    for (File child : children) {
      if (child.isFile() && child.getName().endsWith(SdkConstants.DOT_JAR)) {
        classpath.add(child.getAbsolutePath());
      }
    }
    return classpath;
  }

  @Override
  @NotNull
  public List<String> getVMArguments() {
    if (!Projects.isGradleProject(myProject)) {
      return Collections.emptyList();
    }
    GradleSettings settings = (GradleSettings)ExternalSystemApiUtil.getSettings(myProject, SYSTEM_ID);

    GradleSettings.MyState state = settings.getState();
    assert state != null;
    Set<GradleProjectSettings> allProjectsSettings = state.getLinkedExternalProjectsSettings();

    GradleProjectSettings projectSettings = getFirstNotNull(allProjectsSettings);
    if (projectSettings == null) {
      String format = "Unable to obtain Gradle project settings for project '%1$s', located at '%2$s'";
      String msg = String.format(format, myProject.getName(), myProject.getBasePath());
      LOG.info(msg);
      return Collections.emptyList();
    }
    List<String> jvmArgs = Lists.newArrayList();

    GradleExecutionSettings executionSettings =
      ExternalSystemApiUtil.getExecutionSettings(myProject, projectSettings.getExternalProjectPath(), SYSTEM_ID);
    //noinspection TestOnlyProblems
    populateJvmArgs(executionSettings, jvmArgs);

    BuildMode buildMode = Projects.getBuildModeFrom(myProject);
    if (buildMode == null) {
      buildMode = BuildMode.DEFAULT_BUILD_MODE;
    }
    Projects.removeBuildActionFrom(myProject);
    jvmArgs.add(createJvmArg(BuildProcessJvmArgs.BUILD_ACTION, buildMode.name()));

    addHttpProxySettings(jvmArgs);

    return jvmArgs;
  }

  @Nullable
  private static GradleProjectSettings getFirstNotNull(Set<GradleProjectSettings> allProjectSettings) {
    for (GradleProjectSettings settings : allProjectSettings) {
      if (settings != null) {
        return settings;
      }
    }
    return null;
  }

  @VisibleForTesting
  void populateJvmArgs(@NotNull GradleExecutionSettings executionSettings, @NotNull List<String> jvmArgs) {
    long daemonMaxIdleTimeInMs = executionSettings.getRemoteProcessIdleTtlInMs();
    jvmArgs.add(createJvmArg(BuildProcessJvmArgs.GRADLE_DAEMON_MAX_IDLE_TIME_IN_MS, String.valueOf(daemonMaxIdleTimeInMs)));

    String gradleHome = executionSettings.getGradleHome();
    if (gradleHome != null && !gradleHome.isEmpty()) {
      gradleHome = FileUtil.toSystemDependentName(gradleHome);
      jvmArgs.add(createJvmArg(BuildProcessJvmArgs.GRADLE_HOME_DIR_PATH, gradleHome));
    }

    String serviceDirectory = executionSettings.getServiceDirectory();
    if (serviceDirectory != null && !serviceDirectory.isEmpty()) {
      serviceDirectory = FileUtil.toSystemDependentName(serviceDirectory);
      jvmArgs.add(createJvmArg(BuildProcessJvmArgs.GRADLE_SERVICE_DIR_PATH, serviceDirectory));
    }

    ProjectRootManagerEx rootManager = ProjectRootManagerEx.getInstanceEx(myProject);
    Sdk projectSdk = rootManager.getProjectSdk();

    if (projectSdk != null) {
      String javaHome = projectSdk.getHomePath();
      if (javaHome != null && !javaHome.isEmpty()) {
        javaHome = FileUtil.toSystemDependentName(javaHome);
        jvmArgs.add(createJvmArg(BuildProcessJvmArgs.GRADLE_JAVA_HOME_DIR_PATH, javaHome));
      }
    }

    String basePath = FileUtil.toSystemDependentName(myProject.getBasePath());
    jvmArgs.add(createJvmArg(BuildProcessJvmArgs.PROJECT_DIR_PATH, basePath));

    boolean verboseProcessing = executionSettings.isVerboseProcessing();
    jvmArgs.add(createJvmArg(BuildProcessJvmArgs.USE_GRADLE_VERBOSE_LOGGING, String.valueOf(verboseProcessing)));

    String jvmOptions = executionSettings.getDaemonVmOptions();
    int jvmOptionCount = 0;
    if (jvmOptions != null && !jvmOptions.isEmpty()) {
      CommandLineTokenizer tokenizer = new CommandLineTokenizer(jvmOptions);
      while(tokenizer.hasMoreTokens()) {
        String name = BuildProcessJvmArgs.GRADLE_DAEMON_JVM_OPTION_PREFIX + jvmOptionCount;
        jvmArgs.add(createJvmArg(name, tokenizer.nextToken()));
        jvmOptionCount++;
      }
    }
    jvmArgs.add(createJvmArg(BuildProcessJvmArgs.GRADLE_DAEMON_JVM_OPTION_COUNT, jvmOptionCount));
  }

  private static void addHttpProxySettings(@NotNull List<String> jvmArgs) {
    List<KeyValue<String, String>> proxyProperties = HttpConfigurable.getJvmPropertiesList(false, null);
    //noinspection TestOnlyProblems
    populateHttpProxyProperties(jvmArgs, proxyProperties);
  }

  @VisibleForTesting
  static void populateHttpProxyProperties(List<String> jvmArgs, List<KeyValue<String, String>> properties) {
    int propertyCount = properties.size();
    jvmArgs.add(createJvmArg(BuildProcessJvmArgs.HTTP_PROXY_PROPERTY_COUNT, propertyCount));

    for (int i = 0; i < propertyCount; i++) {
      KeyValue<String, String> property = properties.get(i);
      String name = BuildProcessJvmArgs.HTTP_PROXY_PROPERTY_PREFIX + i;
      String value = property.getKey() + BuildProcessJvmArgs.HTTP_PROXY_PROPERTY_SEPARATOR + property.getValue();
      jvmArgs.add(createJvmArg(name, value));
    }
  }
}
