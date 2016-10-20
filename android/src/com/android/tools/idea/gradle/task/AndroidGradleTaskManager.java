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
package com.android.tools.idea.gradle.task;

import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.List;

import static com.android.tools.idea.gradle.util.Projects.isDirectGradleInvocationEnabled;
import static com.android.tools.idea.gradle.util.Projects.requiresAndroidModel;

/**
 * Executes Gradle tasks.
 */
public class AndroidGradleTaskManager implements GradleTaskManagerExtension {
  @Override
  public boolean executeTasks(@NotNull ExternalSystemTaskId id,
                              @NotNull List<String> taskNames,
                              @NotNull String projectPath,
                              @Nullable GradleExecutionSettings settings,
                              @NotNull List<String> vmOptions,
                              @NotNull List<String> scriptParameters,
                              @Nullable String debuggerSetup,
                              @NotNull ExternalSystemTaskNotificationListener listener) throws ExternalSystemException {
    GradleInvoker gradleInvoker = findGradleInvoker(id);
    if (gradleInvoker != null) {
      GradleInvoker.RequestSettings requestSettings = new GradleInvoker.RequestSettings(gradleInvoker.getProject(), taskNames, id);

      // @formatter:off
      requestSettings.setJvmArguments(vmOptions)
                     .setCommandLineArguments(scriptParameters)
                     .setTaskListener(listener)
                     .setWaitForCompletion(true);
      // @formatter:on

      gradleInvoker.executeTasks(requestSettings);
      return true;
    }
    // Returning false gives control back to the framework, and the task(s) will be invoked by IDEA.
    return false;
  }

  @Override
  public boolean cancelTask(@NotNull ExternalSystemTaskId id, @NotNull ExternalSystemTaskNotificationListener listener) {
    GradleInvoker gradleInvoker = findGradleInvoker(id);
    if (gradleInvoker != null) {
      gradleInvoker.stopBuild(id);
      return true;
    }
    return false;
  }

  @Nullable
  private static GradleInvoker findGradleInvoker(ExternalSystemTaskId id) {
    Project project = id.findProject();
    if (project != null && requiresAndroidModel(project) && isDirectGradleInvocationEnabled(project)) {
      return GradleInvoker.getInstance(project);
    }
    return null;
  }
}
