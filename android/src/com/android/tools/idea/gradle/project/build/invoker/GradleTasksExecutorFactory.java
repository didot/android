/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleTasksExecutorFactory {
  @NotNull
  public GradleTasksExecutor create(@NotNull GradleBuildInvoker.Request request,
                                    @NotNull BuildStopper buildStopper,
                                    @NotNull ExternalSystemTaskNotificationListener listener,
                                    @NotNull SettableFuture<@Nullable GradleInvocationResult> resultFuture) {
    GradleTasksExecutor executor = new GradleTasksExecutorImpl(request, buildStopper, listener, resultFuture);
    GradleBuildState.getInstance(request.getProject()).buildExecutorCreated(request);
    return executor;
  }
}
