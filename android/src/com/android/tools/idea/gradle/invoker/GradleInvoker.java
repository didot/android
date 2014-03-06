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
package com.android.tools.idea.gradle.invoker;

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.GradleBuilds;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.util.Collection;
import java.util.List;

/**
 * Invokes Gradle tasks directly. Results of tasks execution are displayed in both the "Messages" tool window and the new "Gradle Console"
 * tool window.
 */
public class GradleInvoker {
  private static final Logger LOG = Logger.getInstance(GradleInvoker.class);

  @NotNull private final Project myProject;

  @NotNull private Collection<BeforeGradleInvocationTask> myBeforeTasks = Sets.newLinkedHashSet();
  @NotNull private Collection<AfterGradleInvocationTask> myAfterTasks = Sets.newLinkedHashSet();

  public static GradleInvoker getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleInvoker.class);
  }

  public GradleInvoker(@NotNull Project project) {
    myProject = project;
  }

  @VisibleForTesting
  void addBeforeGradleInvocationTask(@NotNull BeforeGradleInvocationTask task) {
    myBeforeTasks.add(task);
  }

  public void addAfterGradleInvocationTask(@NotNull AfterGradleInvocationTask task) {
    myAfterTasks.add(task);
  }

  public void removeAfterGradleInvocationTask(@NotNull AfterGradleInvocationTask task) {
    myAfterTasks.remove(task);
  }

  public void cleanProject() {
    setProjectBuildMode(BuildMode.CLEAN);
    List<String> tasks = Lists.newArrayList(GradleBuilds.CLEAN_TASK_NAME);
    executeTasks(tasks);
  }

  public void generateSources() {
    BuildMode buildMode = BuildMode.SOURCE_GEN;
    setProjectBuildMode(buildMode);

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> tasks = findTasksToExecute(moduleManager.getModules(), buildMode, GradleBuilds.TestCompileType.NONE);

    executeTasks(tasks);
  }

  public void compileJava(@NotNull Module[] modules) {
    BuildMode buildMode = BuildMode.COMPILE_JAVA;
    setProjectBuildMode(buildMode);
    List<String> tasks = findTasksToExecute(modules, buildMode, GradleBuilds.TestCompileType.NONE);
    executeTasks(tasks);
  }

  public void make(@NotNull Module[] modules, @NotNull GradleBuilds.TestCompileType testCompileType) {
    BuildMode buildMode = BuildMode.MAKE;
    setProjectBuildMode(buildMode);
    List<String> tasks = findTasksToExecute(modules, buildMode, testCompileType);
    executeTasks(tasks);
  }

  public void rebuild() {
    BuildMode buildMode = BuildMode.REBUILD;
    setProjectBuildMode(buildMode);

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> tasks = findTasksToExecute(moduleManager.getModules(), buildMode, GradleBuilds.TestCompileType.NONE);
    if (!tasks.isEmpty()) {
      tasks.add(0, GradleBuilds.CLEAN_TASK_NAME);
    }

    executeTasks(tasks);
  }

  private void setProjectBuildMode(@NotNull BuildMode buildMode) {
    BuildSettings.getInstance(myProject).setBuildMode(buildMode);
  }

  private List<String> findTasksToExecute(@NotNull Module[] modules,
                                          @NotNull BuildMode buildMode,
                                          @NotNull GradleBuilds.TestCompileType testCompileType) {
    List<String> tasks = Lists.newArrayList();
    for (Module module : modules) {
      AndroidGradleFacet androidGradleFacet = AndroidGradleFacet.getInstance(module);
      if (androidGradleFacet == null) {
        continue;
      }
      String gradleProjectPath = androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      JpsAndroidModuleProperties properties = androidFacet != null ? androidFacet.getProperties() : null;
      GradleBuilds.findAndAddBuildTask(module.getName(), buildMode, gradleProjectPath, properties, tasks, testCompileType);
    }

    if (tasks.isEmpty()) {
      // Unlikely to happen.
      String format = "Unable to find Gradle tasks for project '%1$s' using BuildMode %2$s";
      LOG.info(String.format(format, myProject.getName(), buildMode.name()));
    }
    return tasks;
  }

  public void executeTasks(@NotNull final List<String> gradleTasks) {
    LOG.info("About to execute Gradle tasks: " + gradleTasks);
    if (gradleTasks.isEmpty()) {
      return;
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      for (BeforeGradleInvocationTask listener : myBeforeTasks) {
        listener.execute(gradleTasks);
      }
      return;
    }

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
        AfterGradleInvocationTask[] afterGradleInvocationTasks =
          myAfterTasks.toArray(new AfterGradleInvocationTask[myAfterTasks.size()]);
        GradleTasksExecutor executor = new GradleTasksExecutor(myProject, gradleTasks, afterGradleInvocationTasks);
        executor.queue();
      }
    });
  }

  @VisibleForTesting
  interface BeforeGradleInvocationTask {
    void execute(@NotNull List<String> tasks);
  }

  public interface AfterGradleInvocationTask {
    void execute(@NotNull GradleInvocationResult result);
  }
}
