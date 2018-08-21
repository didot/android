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

import com.android.tools.idea.gradle.project.AndroidGradleProjectComponent;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssuesReporter;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.ModelNotFoundInCacheException;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlyProjectModels;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.android.tools.idea.gradle.util.GradleProjects.open;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.invokeLater;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.scheduleExternalViewStructureUpdate;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;

class SyncResultHandler {
  @NotNull private final Project myProject;
  @NotNull private final GradleSyncState mySyncState;
  @NotNull private final GradleProjectInfo myProjectInfo;
  @NotNull private final ProjectSetup.Factory myProjectSetupFactory;
  @NotNull private final PostSyncProjectSetup myPostSyncProjectSetup;

  SyncResultHandler(@NotNull Project project) {
    this(project, GradleSyncState.getInstance(project), GradleProjectInfo.getInstance(project), new ProjectSetup.Factory(),
         PostSyncProjectSetup.getInstance(project));
  }

  @VisibleForTesting
  SyncResultHandler(@NotNull Project project,
                    @NotNull GradleSyncState syncState,
                    @NotNull GradleProjectInfo projectInfo,
                    @NotNull ProjectSetup.Factory projectSetupFactory,
                    @NotNull PostSyncProjectSetup postSyncProjectSetup) {
    myProject = project;
    mySyncState = syncState;
    myProjectInfo = projectInfo;
    myProjectSetupFactory = projectSetupFactory;
    myPostSyncProjectSetup = postSyncProjectSetup;
  }

  void onSyncFinished(@NotNull SyncExecutionCallback callback,
                      @NotNull PostSyncProjectSetup.Request setupRequest,
                      @NotNull ProgressIndicator indicator,
                      @Nullable GradleSyncListener syncListener) {
    SyncProjectModels models = callback.getSyncModels();
    ExternalSystemTaskId taskId = callback.getTaskId();
    if (models != null) {
      try {
        setUpProject(models, setupRequest, indicator, syncListener, taskId);
        Runnable runnable = () -> {
          boolean isTest = ApplicationManager.getApplication().isUnitTestMode();
          boolean isImportedProject = myProjectInfo.isImportedProject();
          if (isImportedProject && (!isTest || !GradleProjectImporter.ourSkipSetupFromTest)) {
            open(myProject);
          }
          if (!isTest) {
            CommandProcessor.getInstance().runUndoTransparentAction(() -> myProject.save());
          }
          if (isImportedProject) {
            // We need to do this because AndroidGradleProjectComponent#projectOpened is being called when the project is created, instead
            // of when the project is opened. When 'projectOpened' is called, the project is not fully configured, and it does not look
            // like it is Gradle-based, resulting in listeners (e.g. modules added events) not being registered. Here we force the
            // listeners to be registered.
            AndroidGradleProjectComponent projectComponent = AndroidGradleProjectComponent.getInstance(myProject);
            projectComponent.configureGradleProject();
          }
        };
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          runnable.run();
        }
        else {
          invokeLater(myProject, runnable);
        }
      }
      catch (Throwable e) {
        notifyAndLogSyncError(nullToUnknownErrorCause(getRootCauseMessage(e)), e, syncListener);
      }
    }
    else {
      // SyncAction.ProjectModels should not be null. Something went wrong.
      notifyAndLogSyncError("Gradle did not return any project models", null /* no exception */, syncListener);
    }
  }

  private void setUpProject(@NotNull SyncProjectModels models,
                            @NotNull PostSyncProjectSetup.Request setupRequest,
                            @NotNull ProgressIndicator indicator,
                            @Nullable GradleSyncListener syncListener,
                            @Nullable ExternalSystemTaskId taskId) {
    try {
      if (syncListener != null) {
        syncListener.setupStarted(myProject);
      }
      mySyncState.setupStarted();

      ProjectSetup projectSetup = myProjectSetupFactory.create(myProject);
      projectSetup.setUpProject(models, indicator);
      projectSetup.commit();
      SyncIssuesReporter.getInstance().report(ModuleManager.getInstance(myProject).getModules());
      scheduleExternalViewStructureUpdate(myProject, SYSTEM_ID);

      if (syncListener != null) {
        syncListener.syncSucceeded(myProject);
      }

      StartupManager.getInstance(myProject)
                    .runWhenProjectIsInitialized(() -> myPostSyncProjectSetup.setUpProject(setupRequest, indicator, taskId));
    }
    catch (Throwable e) {
      notifyAndLogSyncError(nullToUnknownErrorCause(getRootCauseMessage(e)), e, syncListener);
    }
  }

  void onSyncSkipped(@NotNull CachedProjectModels projectModelsCache,
                     @NotNull PostSyncProjectSetup.Request setupRequest,
                     @NotNull ProgressIndicator indicator,
                     @Nullable GradleSyncListener syncListener,
                     @Nullable ExternalSystemTaskId taskId) throws ModelNotFoundInCacheException {
    if (syncListener != null) {
      syncListener.setupStarted(myProject);
    }
    mySyncState.setupStarted();
    ProjectSetup projectSetup = myProjectSetupFactory.create(myProject);
    projectSetup.setUpProject(projectModelsCache, indicator);
    projectSetup.commit();
    SyncIssuesReporter.getInstance().report(ModuleManager.getInstance(myProject).getModules());

    if (syncListener != null) {
      syncListener.syncSkipped(myProject);
    }

    StartupManager.getInstance(myProject)
                  .runWhenProjectIsInitialized(() -> myPostSyncProjectSetup.setUpProject(setupRequest, indicator, taskId));
  }

  void onSyncFailed(@NotNull SyncExecutionCallback callback, @Nullable GradleSyncListener syncListener) {
    Throwable error = callback.getSyncError();
    String errorMessage = error != null ? getRootCauseMessage(error) : callback.getError();
    errorMessage = nullToUnknownErrorCause(errorMessage);
    notifyAndLogSyncError(errorMessage, error, syncListener);
  }

  private void notifyAndLogSyncError(@NotNull String errorMessage, @Nullable Throwable error, @Nullable GradleSyncListener syncListener) {
    if (ApplicationManager.getApplication().isUnitTestMode() && error != null) {
      // This is extremely handy when debugging sync errors in tests. Do not remove.
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("***** sync error: " + error.getMessage());
      //noinspection CallToPrintStackTrace
      error.printStackTrace();
    }

    if (syncListener != null) {
      syncListener.syncFailed(myProject, errorMessage);
    }
    mySyncState.syncFailed(errorMessage);
    if (error != null) {
      getLog().warn("Gradle sync failed", error);
    }
    else {
      logSyncFailure(errorMessage);
    }
  }

  @Nullable
  private static String getRootCauseMessage(@NotNull Throwable error) {
    Throwable rootCause = getRootCause(error);
    String message = rootCause.getMessage();
    return isEmpty(message) ? rootCause.getClass().getName() : message;
  }

  @NotNull
  private static String nullToUnknownErrorCause(@Nullable String errorMessage) {
    return isEmpty(errorMessage) ? "<Unknown cause>" : errorMessage;
  }

  private static void logSyncFailure(@NotNull String errorMessage) {
    getLog().warn("Gradle sync failed: " + errorMessage);
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(SyncResultHandler.class);
  }

  void onVariantOnlySyncFinished(@NotNull SyncExecutionCallback callback,
                                 @NotNull PostSyncProjectSetup.Request setupRequest,
                                 @NotNull ProgressIndicator indicator,
                                 @Nullable GradleSyncListener syncListener) {
    VariantOnlyProjectModels models = callback.getVariantOnlyModels();
    ExternalSystemTaskId taskId = callback.getTaskId();
    if (models != null) {
      try {
        mySyncState.setupStarted();

        ProjectSetup projectSetup = myProjectSetupFactory.create(myProject);
        projectSetup.setUpProject(models, indicator);
        projectSetup.commit();
        SyncIssuesReporter.getInstance().report(ModuleManager.getInstance(myProject).getModules());

        if (syncListener != null) {
          syncListener.syncSucceeded(myProject);
        }
        StartupManager.getInstance(myProject)
                      .runWhenProjectIsInitialized(() -> myPostSyncProjectSetup.setUpProject(setupRequest, indicator, taskId));
      }
      catch (Throwable e) {
        notifyAndLogSyncError(nullToUnknownErrorCause(getRootCauseMessage(e)), e, syncListener);
      }
    }
    else {
      // SyncAction.ProjectModels should not be null. Something went wrong.
      notifyAndLogSyncError("Gradle did not return any project models", null /* no exception */, syncListener);
    }
  }
}
