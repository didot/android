/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewGradleSync implements GradleSync {
  @NotNull private final Project myProject;
  @NotNull private final SyncExecutor mySyncExecutor;
  @NotNull private final SyncResultHandler myResultHandler;

  NewGradleSync(@NotNull Project project) {
    this(project, new SyncExecutor(project), new SyncResultHandler(project));
  }

  @VisibleForTesting
  NewGradleSync(@NotNull Project project, @NotNull SyncExecutor syncExecutor, @NotNull SyncResultHandler resultHandler) {
    myProject = project;
    mySyncExecutor = syncExecutor;
    myResultHandler = resultHandler;
  }

  @Override
  public void sync(@NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener listener) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      sync(listener, new EmptyProgressIndicator());
      return;
    }
    Task task = createSyncTask(request, listener);
    ApplicationManager.getApplication().invokeLater(task::queue, ModalityState.defaultModalityState());
  }

  @VisibleForTesting
  @NotNull
  Task createSyncTask(@NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener listener) {
    String title = "Gradle Sync"; // TODO show Gradle feedback

    ProgressExecutionMode executionMode = request.getProgressExecutionMode();
    switch (executionMode) {
      case MODAL_SYNC:
        return new Task.Modal(myProject, title, true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            sync(listener, indicator);
          }
        };
      case IN_BACKGROUND_ASYNC:
        return new Task.Backgroundable(myProject, title, true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            sync(listener, indicator);
          }
        };
      default:
        throw new IllegalArgumentException(executionMode + " is not a supported execution mode");
    }
  }

  private void sync(@Nullable GradleSyncListener syncListener, @NotNull ProgressIndicator indicator) {
    SyncExecutionCallback callback = mySyncExecutor.syncProject(indicator);
    // @formatter:off
    callback.doWhenDone(() -> myResultHandler.onSyncFinished(callback, indicator, syncListener))
            .doWhenRejected(() -> myResultHandler.onSyncFailed(callback, syncListener));
    // @formatter:on
  }

}
