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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.GradlePerProjectExperimentalSettings;
import com.android.tools.idea.gradle.project.ProjectBuildFileChecksums;
import com.android.tools.idea.gradle.project.sync.GradleSync;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.ModelNotFoundInCacheException;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup.createProjectSetupFromCacheTaskWithStartMessage;

public class NewGradleSync implements GradleSync {
  @NotNull private final Project myProject;
  @NotNull private final GradleSyncMessages mySyncMessages;
  @NotNull private final SyncExecutor mySyncExecutor;
  @NotNull private final SyncResultHandler myResultHandler;
  @NotNull private final ProjectBuildFileChecksums.Loader myBuildFileChecksumsLoader;
  @NotNull private final CachedProjectModels.Loader myProjectModelsCacheLoader;
  @NotNull private final SyncExecutionCallback.Factory myCallbackFactory;

  public static boolean isLevel4Model() {
    return StudioFlags.L4_DEPENDENCY_MODEL.get();
  }

  public static boolean isEnabled(@NotNull Project project) {
    return StudioFlags.NEW_SYNC_INFRA_ENABLED.get() || GradlePerProjectExperimentalSettings.getInstance(project).USE_SINGLE_VARIANT_SYNC;
  }

  public static boolean isSingleVariantSync(@NotNull Project project) {
    return StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.get() ||
           GradlePerProjectExperimentalSettings.getInstance(project).USE_SINGLE_VARIANT_SYNC;
  }

  public NewGradleSync(@NotNull Project project) {
    this(project, GradleSyncMessages.getInstance(project), new SyncExecutor(project), new SyncResultHandler(project),
         new ProjectBuildFileChecksums.Loader(), new CachedProjectModels.Loader(), new SyncExecutionCallback.Factory());
  }

  @VisibleForTesting
  NewGradleSync(@NotNull Project project,
                @NotNull GradleSyncMessages syncMessages,
                @NotNull SyncExecutor syncExecutor,
                @NotNull SyncResultHandler resultHandler,
                @NotNull ProjectBuildFileChecksums.Loader buildFileChecksumsLoader,
                @NotNull CachedProjectModels.Loader projectModelsCacheLoader,
                @NotNull SyncExecutionCallback.Factory callbackFactory) {
    myProject = project;
    mySyncMessages = syncMessages;
    mySyncExecutor = syncExecutor;
    myResultHandler = resultHandler;
    myBuildFileChecksumsLoader = buildFileChecksumsLoader;
    myProjectModelsCacheLoader = projectModelsCacheLoader;
    myCallbackFactory = callbackFactory;
  }

  @Override
  public void sync(@NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener listener) {
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      mySyncMessages.removeAllMessages();
      sync(request, new EmptyProgressIndicator(), listener);
      return;
    }
    Task task = createSyncTask(request, listener);
    application.invokeLater(() -> {
      // IDEA's own sync infrastructure removes all messages at the beginning of every sync: ExternalSystemUtil#refreshProject.
      mySyncMessages.removeAllMessages();
      task.queue();
    }, ModalityState.defaultModalityState());
  }

  @VisibleForTesting
  @NotNull
  Task createSyncTask(@NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener listener) {
    String title = "Gradle Sync"; // TODO show Gradle feedback

    ProgressExecutionMode executionMode = request.getProgressExecutionMode();
    Task syncTask;
    switch (executionMode) {
      case MODAL_SYNC:
        syncTask = new Task.Modal(myProject, title, true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            sync(request, indicator, listener);
          }
        };
        break;
      case IN_BACKGROUND_ASYNC:
        syncTask = new Task.Backgroundable(myProject, title, true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            sync(request, indicator, listener);
          }
        };
        break;
      default:
        throw new IllegalArgumentException(executionMode + " is not a supported execution mode");
    }
    return syncTask;
  }

  private void sync(@NotNull GradleSyncInvoker.Request request, @NotNull ProgressIndicator indicator,
                    @Nullable GradleSyncListener syncListener) {
    if (request.useCachedGradleModels) {
      // Use models from disk cache.
      ProjectBuildFileChecksums buildFileChecksums = myBuildFileChecksumsLoader.loadFromDisk(myProject);
      if (buildFileChecksums != null && buildFileChecksums.canUseCachedData()) {
        CachedProjectModels projectModelsCache = myProjectModelsCacheLoader.loadFromDisk(myProject);
        if (projectModelsCache != null) {
          PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
          setupRequest.usingCachedGradleModels = true;
          setupRequest.generateSourcesAfterSync = false;
          setupRequest.lastSyncTimestamp = buildFileChecksums.getLastGradleSyncTimestamp();
          // @formatter:on

          setSkipAndroidPluginUpgrade(request, setupRequest);
          // Create a new taskId when using cache
          ExternalSystemTaskId taskId = createProjectSetupFromCacheTaskWithStartMessage(myProject);

          try {
            myResultHandler.onSyncSkipped(projectModelsCache, setupRequest, indicator, syncListener, taskId);
            return;
          }
          catch (ModelNotFoundInCacheException e) {
            Logger.getInstance(NewGradleSync.class).warn("Restoring project state from cache failed. Performing a Gradle Sync.", e);
          }
        }
      }
    }

    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = request.generateSourcesOnSuccess;
    setupRequest.cleanProjectAfterSync = request.cleanProject;
    setSkipAndroidPluginUpgrade(request, setupRequest);

    SyncExecutionCallback callback = myCallbackFactory.create();
    callback.doWhenRejected(() -> myResultHandler.onSyncFailed(callback, syncListener));
    if (request.variantOnlySyncOptions != null) {
      callback.doWhenDone(() -> myResultHandler.onVariantOnlySyncFinished(callback, setupRequest, indicator, syncListener));
      mySyncExecutor.syncProject(indicator, callback, request.variantOnlySyncOptions);
    }
    else {
      callback.doWhenDone(() -> myResultHandler.onSyncFinished(callback, setupRequest, indicator, syncListener));
      mySyncExecutor.syncProject(indicator, callback);
    }
  }

  private static void setSkipAndroidPluginUpgrade(@NotNull GradleSyncInvoker.Request syncRequest,
                                                  @NotNull PostSyncProjectSetup.Request setupRequest) {
    if (ApplicationManager.getApplication().isUnitTestMode() && syncRequest.skipAndroidPluginUpgrade) {
      setupRequest.skipAndroidPluginUpgrade = true;
    }
  }
}
