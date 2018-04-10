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
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandlerManager;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.impl.FinishBuildEventImpl;
import com.intellij.build.events.impl.OutputBuildEventImpl;
import com.intellij.build.events.impl.SkippedResultImpl;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.output.BuildOutputInstantReaderImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.sync.ng.GradleSyncProgress.notifyProgress;
import static com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup.finishFailedSync;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.getOrCreateGradleExecutionSettings;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.convert;
import static java.lang.System.currentTimeMillis;
import static org.gradle.tooling.GradleConnector.newCancellationTokenSource;
import static org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.prepare;

class SyncExecutor {
  @NotNull private final Project myProject;
  @NotNull private final CommandLineArgs myCommandLineArgs;
  @NotNull private final SyncErrorHandlerManager myErrorHandlerManager;
  @NotNull private final ExtraGradleSyncModelsManager myExtraModelsManager;
  @NotNull private final SelectedVariantCollector mySelectedVariantCollector;
  @NotNull private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  SyncExecutor(@NotNull Project project) {
    this(project, ExtraGradleSyncModelsManager.getInstance(), new CommandLineArgs(true /* apply Java library plugin */),
         new SyncErrorHandlerManager(project), new SelectedVariantCollector(project));
  }

  @VisibleForTesting
  SyncExecutor(@NotNull Project project,
               @NotNull ExtraGradleSyncModelsManager extraModelsManager,
               @NotNull CommandLineArgs commandLineArgs,
               @NotNull SyncErrorHandlerManager errorHandlerManager,
               @NotNull SelectedVariantCollector selectedVariantCollector) {
    myProject = project;
    myCommandLineArgs = commandLineArgs;
    myErrorHandlerManager = errorHandlerManager;
    myExtraModelsManager = extraModelsManager;
    mySelectedVariantCollector = selectedVariantCollector;
  }

  void syncProject(@NotNull ProgressIndicator indicator, @NotNull SyncExecutionCallback callback) {
    if (myProject.isDisposed()) {
      callback.reject(String.format("Project '%1$s' is already disposed", myProject.getName()));
    }

    // TODO: Handle sync cancellation.

    GradleExecutionSettings executionSettings = getOrCreateGradleExecutionSettings(myProject);
    Function<ProjectConnection, Void> syncFunction = connection -> {
      syncProject(connection, executionSettings, indicator, callback);
      return null;
    };

    try {
      myHelper.execute(getBaseDirPath(myProject).getPath(), executionSettings, syncFunction);
    }
    catch (Throwable e) {
      callback.setRejected(e);
    }
  }

  private void syncProject(@NotNull ProjectConnection connection,
                           @NotNull GradleExecutionSettings executionSettings,
                           @NotNull ProgressIndicator indicator,
                           @NotNull SyncExecutionCallback callback) {
    SyncAction syncAction = createSyncAction();
    BuildActionExecuter<SyncProjectModels> executor = connection.action(syncAction);

    List<String> commandLineArgs = myCommandLineArgs.get(myProject);

    // Create a task id for this sync
    ExternalSystemTaskId id = createId(myProject);
    SyncViewManager syncViewManager = ServiceManager.getService(myProject, SyncViewManager.class);
    // Attach output
    //noinspection resource, IOResourceOpenedButNotSafelyClosed
    BuildOutputInstantReaderImpl buildOutputReader = new BuildOutputInstantReaderImpl(id, syncViewManager, Collections.emptyList());
    // Add a StartEvent to the build tool window
    String projectPath = getBaseDirPath(myProject).getPath();
    DefaultBuildDescriptor buildDescriptor = new DefaultBuildDescriptor(id, myProject.getName(), projectPath, currentTimeMillis());
    StartBuildEventImpl startEvent = new StartBuildEventImpl(buildDescriptor, "syncing...");
    syncViewManager.onEvent(startEvent);

    // We try to avoid passing JVM arguments, to share Gradle daemons between Gradle sync and Gradle build.
    // If JVM arguments from Gradle sync are different than the ones from Gradle build, Gradle won't reuse daemons. This is bad because
    // daemons are expensive (memory-wise) and slow to start.
    prepare(executor, id, executionSettings, new GradleSyncNotificationListener(id, indicator, buildOutputReader),
            Collections.emptyList() /* JVM args */, commandLineArgs, connection);

    CancellationTokenSource cancellationTokenSource = newCancellationTokenSource();
    executor.withCancellationToken(cancellationTokenSource.token());

    try {
      SyncProjectModels models = executor.run();
      callback.setDone(models, id);
    }
    catch (RuntimeException e) {
      myErrorHandlerManager.handleError(e);
      callback.setRejected(e);

      // Generate a failure result event, but make sure that it is generated after the errors generated by myErrorHandlerManager
      Runnable runnable = () -> {
        finishFailedSync(id, myProject);
      };
      ApplicationManager.getApplication().invokeLater(runnable);
    }
    // Close the reader in case no end or cancelled events were created.
    buildOutputReader.close();
  }

  @VisibleForTesting
  @NotNull
  SyncAction createSyncAction() {
    SyncActionOptions options = new SyncActionOptions();
    options.setSingleVariantSyncEnabled(StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.get());
    if (options.isSingleVariantSyncEnabled()) {
      SelectedVariants selectedVariants = mySelectedVariantCollector.collectSelectedVariants();
      options.setSelectedVariants(selectedVariants);
    }
    return new SyncAction(myExtraModelsManager.getAndroidModelTypes(), myExtraModelsManager.getJavaModelTypes(), options);
  }

  @NotNull
  private static ExternalSystemTaskId createId(@NotNull Project project) {
    return ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, RESOLVE_PROJECT, project);
  }

  @VisibleForTesting
  static class GradleSyncNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {
    @NotNull private final ProgressIndicator myIndicator;
    @NotNull private final ExternalSystemTaskId myTaskId;
    @NotNull private final BuildOutputInstantReaderImpl myOutputReader;

    GradleSyncNotificationListener(@NotNull ExternalSystemTaskId taskId,
                                   @NotNull ProgressIndicator indicator,
                                   @NotNull BuildOutputInstantReaderImpl outputReader) {
      myIndicator = indicator;
      myTaskId = taskId;
      myOutputReader = outputReader;
    }

    @Override
    public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
      Project project = myTaskId.findProject();
      if (project == null) {
        return;
      }
      ServiceManager.getService(project, SyncViewManager.class).onEvent(new OutputBuildEventImpl(id, text, stdOut));
      myOutputReader.append(text);
    }

    @Override
    public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
      notifyProgress(myIndicator, event.getDescription());
      if (event instanceof ExternalSystemTaskExecutionEvent) {
        Project project = myTaskId.findProject();
        if (project == null) {
          return;
        }
        BuildEvent buildEvent = convert((ExternalSystemTaskExecutionEvent)event);
        ServiceManager.getService(project, SyncViewManager.class).onEvent(buildEvent);
      }
    }

    @Override
    public void onEnd(@NotNull ExternalSystemTaskId id) {
      myOutputReader.close();
    }

    @Override
    public void onCancel(@NotNull ExternalSystemTaskId id) {
      super.onCancel(id);
      Project project = myTaskId.findProject();
      if (project != null) {
        // Cause build view to show as skipped all pending tasks (b/73397414)
        FinishBuildEventImpl event = new FinishBuildEventImpl(id, null, currentTimeMillis(), "cancelled", new SkippedResultImpl());
        ServiceManager.getService(project, SyncViewManager.class).onEvent(event);
      }
      myOutputReader.close();
    }
  }
}
