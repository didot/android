/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker;

import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.BuildAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Invokes Gradle tasks in the background.
 */
public interface GradleTasksExecutor {
  @NotNull
  ListenableFuture<GradleInvocationResult> execute(@NotNull GradleBuildInvoker.Request request,
                                                   @Nullable BuildAction<?> buildAction,
                                                   @NotNull BuildStopper buildStopper,
                                                   @NotNull ExternalSystemTaskNotificationListener listener);

  /**
   * This property does not return anything useful as its state can change at any moment. It should not be used.
   */
  @Deprecated
  boolean internalIsBuildRunning(@NotNull Project project);
}
