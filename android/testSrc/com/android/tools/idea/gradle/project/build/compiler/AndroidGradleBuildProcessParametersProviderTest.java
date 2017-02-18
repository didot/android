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
package com.android.tools.idea.gradle.project.build.compiler;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.gradle.util.GradleBuilds;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.util.FilePaths.toSystemDependentPath;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.executeProjectChangeAction;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static org.easymock.EasyMock.*;

/**
 * Tests for {@link AndroidGradleBuildProcessParametersProvider}.
 */
public class AndroidGradleBuildProcessParametersProviderTest extends IdeaTestCase {
  private AndroidGradleBuildProcessParametersProvider myParametersProvider;
  private Sdk myJdk;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    IdeSdks ideSdks = IdeSdks.getInstance();
    if (IdeInfo.getInstance().isAndroidStudio()) {
      ApplicationManager.getApplication().runWriteAction(ideSdks::setUseEmbeddedJdk);
    }

    File jdkPath = ideSdks.getJdkPath();
    assert jdkPath != null;
    myJdk = Jdks.getInstance().createJdk(jdkPath.getPath());
    myParametersProvider = new AndroidGradleBuildProcessParametersProvider(myProject);
  }

  public void testPopulateJvmArgsWithGradleExecutionSettings() {
    executeProjectChangeAction(true, new DisposeAwareProjectChange(myProject) {
      @Override
      public void execute() {
        removeAllKnownJdks();
        String jdkHome = myJdk.getHomePath();
        assertNotNull(jdkHome);
        File jdkHomePath = toSystemDependentPath(jdkHome);
        IdeSdks.getInstance().setJdkPath(jdkHomePath);
      }
    });

    GradleExecutionSettings settings = createMock(GradleExecutionSettings.class);

    expect(settings.getGradleHome()).andReturn("~/gradle-1.6");
    expect(settings.isVerboseProcessing()).andReturn(true);
    expect(settings.getServiceDirectory()).andReturn("~./gradle");
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      expect(settings.getDaemonVmOptions()).andReturn("-Xmx2048m -XX:MaxPermSize=512m");
    }

    replay(settings);

    List<String> jvmArgList = Lists.newArrayList();
    myParametersProvider.populateJvmArgs(settings, jvmArgList);
    Map<String, String> jvmArgs = convertJvmArgsToMap(jvmArgList);

    verify(settings);

    String projectDirPath = Projects.getBaseDirPath(myProject).getPath();
    assertEquals(projectDirPath, jvmArgs.get("-Dcom.android.studio.gradle.project.path"));
    assertEquals("~" + File.separatorChar + "gradle-1.6", jvmArgs.get("-Dcom.android.studio.gradle.home.path"));
    assertEquals("true", jvmArgs.get("-Dcom.android.studio.gradle.use.verbose.logging"));
    assertEquals("~." + File.separatorChar + "gradle", jvmArgs.get("-Dcom.android.studio.gradle.service.dir.path"));

    if (IdeInfo.getInstance().
      isAndroidStudio()) {
      String javaPath = FileUtilRt.toSystemDependentName(EmbeddedDistributionPaths.getInstance().getEmbeddedJdkPath().getAbsolutePath());
      assertEquals(javaPath, jvmArgs.get("-Dcom.android.studio.gradle.java.home.path"));
    }
    else {
      assertEquals("-Xmx2048m", jvmArgs.get("-Dcom.android.studio.gradle.daemon.jvm.option.0"));
      assertEquals("-XX:MaxPermSize=512m", jvmArgs.get("-Dcom.android.studio.gradle.daemon.jvm.option.1"));
      String javaHomeDirPath = myJdk.getHomePath();
      assertNotNull(javaHomeDirPath);
      javaHomeDirPath = toSystemDependentName(javaHomeDirPath);
      assertEquals(javaHomeDirPath, jvmArgs.get("-Dcom.android.studio.gradle.java.home.path"));
    }
  }

  public void testPopulateHttpProxyProperties() {
    List<Pair<String, String>> properties = Lists.newArrayList();
    properties.add(Pair.create("http.proxyHost", "proxy.android.com"));
    properties.add(Pair.create("http.proxyPort", "8080"));

    List<String> jvmArgList = Lists.newArrayList();
    AndroidGradleBuildProcessParametersProvider.populateHttpProxyProperties(jvmArgList, properties);
    Map<String, String> jvmArgs = convertJvmArgsToMap(jvmArgList);

    assertEquals(2, jvmArgs.size());
    assertEquals("http.proxyHost:proxy.android.com", jvmArgs.get("-Dcom.android.studio.gradle.proxy.property.0"));
    assertEquals("http.proxyPort:8080", jvmArgs.get("-Dcom.android.studio.gradle.proxy.property.1"));
  }

  public void testPopulateGradleTasksToInvokeWithAssembleTranslate() {
    BuildSettings.getInstance(myProject).setModulesToBuild(new Module[] {myModule});
    List<String> jvmArgList = Lists.newArrayList();
    myParametersProvider.populateGradleTasksToInvoke(BuildMode.ASSEMBLE_TRANSLATE, jvmArgList);
    Map<String, String> jvmArgs = convertJvmArgsToMap(jvmArgList);

    assertEquals(1, jvmArgs.size());
    assertEquals(GradleBuilds.ASSEMBLE_TRANSLATE_TASK_NAME, jvmArgs.get("-Dcom.android.studio.gradle.gradle.tasks.0"));
  }

  public void testPopulateJvmArgsWithBuildConfiguration() {
    AndroidGradleBuildConfiguration configuration = new AndroidGradleBuildConfiguration();
    configuration.COMMAND_LINE_OPTIONS = "--stacktrace --offline";
    GradleSettings.getInstance(myProject).setOfflineWork(true);
    List<String> jvmArgList = Lists.newArrayList();
    AndroidGradleBuildProcessParametersProvider.populateJvmArgs(configuration, jvmArgList, myProject);
    Map<String, String> jvmArgs = convertJvmArgsToMap(jvmArgList);

    assertEquals(4, jvmArgs.size());
    assertEquals("true", jvmArgs.get("-Dcom.android.studio.gradle.offline.mode"));
    assertEquals("true", jvmArgs.get("-Dcom.android.studio.gradle.configuration.on.demand"));
    assertEquals("--stacktrace", jvmArgs.get("-Dcom.android.studio.gradle.daemon.command.line.option.0"));
    assertEquals("--offline", jvmArgs.get("-Dcom.android.studio.gradle.daemon.command.line.option.1"));
  }

  private static void removeAllKnownJdks() {
    ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
    for (Sdk jdk : jdkTable.getAllJdks()) {
      jdkTable.removeJdk(jdk);
    }
  }

  private static Map<String, String> convertJvmArgsToMap(List<String> jvmArgs) {
    Map<String, String> map = new HashMap<>();
    for (String arg : jvmArgs) {
      String[] pair = arg.split("=", 2);
      if (pair.length == 2) {
        map.put(pair[0], pair[1]);
      } else {
        map.put(pair[0], "");
      }
    }
    return map;
  }
}
