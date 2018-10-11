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
import com.android.tools.idea.gradle.project.build.output.AndroidGradleSyncTextConsoleView;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandlerManager;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlyProjectModels;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlySyncAction;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlySyncOptions;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.impl.FinishBuildEventImpl;
import com.intellij.build.events.impl.OutputBuildEventImpl;
import com.intellij.build.events.impl.SkippedResultImpl;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.output.BuildOutputInstantReaderImpl;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.util.PropertiesComponent;
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
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.UnsupportedVersionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.List;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.simulateRegisteredSyncError;
import static com.android.tools.idea.gradle.project.sync.common.CommandLineArgs.isInTestingMode;
import static com.android.tools.idea.gradle.project.sync.ng.GradleSyncProgress.notifyProgress;
import static com.android.tools.idea.gradle.project.sync.ng.NewGradleSync.NOT_ELIGIBLE_FOR_SINGLE_VARIANT_SYNC;
import static com.android.tools.idea.gradle.project.sync.ng.NewGradleSync.isSingleVariantSync;
import static com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup.finishFailedSync;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.getOrCreateGradleExecutionSettings;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.convert;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
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

  @NotNull
  List<SyncModuleModels> fetchGradleModels(ProgressIndicator indicator) {
    GradleExecutionSettings executionSettings = findGradleExecutionSettings();
    Function<ProjectConnection, SyncProjectModels> syncFunction =
      connection -> doFetchModels(connection, executionSettings, indicator, createId(myProject), null, false/* full variants sync */);
    SyncProjectModels models = myHelper.execute(getBaseDirPath(myProject).getPath(), executionSettings, syncFunction);
    return models.getModuleModels();
  }

  void syncProject(@NotNull ProgressIndicator indicator,
                   @NotNull SyncExecutionCallback callback) {
    syncProject(indicator, callback, null /* full gradle sync*/, false);
  }

  void syncProject(@NotNull ProgressIndicator indicator,
                   @NotNull SyncExecutionCallback callback,
                   @NotNull VariantOnlySyncOptions options) {
    syncProject(indicator, callback, options, options.myShouldGenerateSources);
  }

  void syncProject(@NotNull ProgressIndicator indicator,
                   @NotNull SyncExecutionCallback callback,
                   @Nullable VariantOnlySyncOptions options,
                   boolean shouldGenerateSources) {
    if (myProject.isDisposed()) {
      callback.reject(String.format("Project '%1$s' is already disposed", myProject.getName()));
    }

    // TODO: Handle sync cancellation.

    GradleExecutionSettings executionSettings = findGradleExecutionSettings();

    Function<ProjectConnection, Void> syncFunction = connection -> {
      syncProject(connection, executionSettings, indicator, callback, options, shouldGenerateSources);
      return null;
    };

    try {
      myHelper.execute(getBaseDirPath(myProject).getPath(), executionSettings, syncFunction);
    }
    catch (Throwable e) {
      callback.setRejected(e);
    }
  }

  @NotNull
  private GradleExecutionSettings findGradleExecutionSettings() {
    GradleExecutionSettings executionSettings = getOrCreateGradleExecutionSettings(myProject);
    // We try to avoid passing JVM arguments, to share Gradle daemons between Gradle sync and Gradle build.
    // If JVM arguments from Gradle sync are different than the ones from Gradle build, Gradle won't reuse daemons. This is bad because
    // daemons are expensive (memory-wise) and slow to start.
    executionSettings.withArguments(myCommandLineArgs.get(myProject)).withVmOptions(emptyList());
    return executionSettings;
  }

  private void syncProject(@NotNull ProjectConnection connection,
                           @NotNull GradleExecutionSettings executionSettings,
                           @NotNull ProgressIndicator indicator,
                           @NotNull SyncExecutionCallback callback,
                           @Nullable VariantOnlySyncOptions options,
                           boolean shouldGenerateSources) {
    // Create a task id for this sync
    ExternalSystemTaskId id = createId(myProject);
    SyncViewManager syncViewManager = ServiceManager.getService(myProject, SyncViewManager.class);
    // Attach output
    //noinspection resource, IOResourceOpenedButNotSafelyClosed
    BuildOutputInstantReaderImpl buildOutputReader = new BuildOutputInstantReaderImpl(id, syncViewManager, emptyList());
    // Add a StartEvent to the build tool window
    String projectPath = getBaseDirPath(myProject).getPath();
    DefaultBuildDescriptor buildDescriptor = new DefaultBuildDescriptor(id, myProject.getName(), projectPath, currentTimeMillis());
    StartBuildEventImpl startEvent = new StartBuildEventImpl(buildDescriptor, "syncing...").withContentDescriptorSupplier(
      () -> {
        AndroidGradleSyncTextConsoleView consoleView = new AndroidGradleSyncTextConsoleView(myProject);
        return new RunContentDescriptor(consoleView, null, consoleView.getComponent(), "Gradle Sync");
      });
    syncViewManager.onEvent(startEvent);
    try {
      if (isInTestingMode()) {
        simulateRegisteredSyncError();
      }
      if (shouldGenerateSources) {
        if (options != null) {
          executeVariantOnlySyncAndGenerateSources(connection, executionSettings, indicator, id, buildOutputReader, callback, options);
        }
        else {
          executeFullSyncAndGenerateSources(connection, executionSettings, indicator, id, buildOutputReader, callback);
        }
      }
      else if (options != null) {
        executeVariantOnlySync(connection, executionSettings, indicator, id, buildOutputReader, callback, options);
      }
      else {
        executeFullSync(connection, executionSettings, indicator, id, buildOutputReader, callback);
      }
    }
    catch (RuntimeException e) {
      // If new sync is not supported, set project property and start another sync.
      if (e.getCause() instanceof NewGradleSyncNotSupportedException) {
        PropertiesComponent.getInstance(myProject).setValue(NOT_ELIGIBLE_FOR_SINGLE_VARIANT_SYNC, true);
        StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(false);
        GradleSyncState.getInstance(myProject).syncFailed(e.getCause().getMessage());
        GradleSyncInvoker.getInstance().requestProjectSync(myProject, GradleSyncInvoker.Request.projectModified());
      }
      else {
        myErrorHandlerManager.handleError(e);
        callback.setRejected(e);

        // Generate a failure result event, but make sure that it is generated after the errors generated by myErrorHandlerManager
        if (isInTestingMode()) {
          finishFailedSync(id, myProject);
        }
        else {
          ApplicationManager.getApplication().invokeLater(() -> finishFailedSync(id, myProject));
        }
      }
    }
    // Close the reader in case no end or cancelled events were created.
    buildOutputReader.close();
  }

  private void executeFullSync(@NotNull ProjectConnection connection,
                               @NotNull GradleExecutionSettings executionSettings,
                               @NotNull ProgressIndicator indicator,
                               @NotNull ExternalSystemTaskId id,
                               @NotNull BuildOutputInstantReaderImpl buildOutputReader,
                               @NotNull SyncExecutionCallback callback) {
    SyncProjectModels projectModels =
      doFetchModels(connection, executionSettings, indicator, id, buildOutputReader, isSingleVariantSync(myProject));
    callback.setDone(projectModels, id);
  }

  @NotNull
  private SyncProjectModels doFetchModels(@NotNull ProjectConnection connection,
                                          @NotNull GradleExecutionSettings executionSettings,
                                          @NotNull ProgressIndicator indicator,
                                          @NotNull ExternalSystemTaskId id,
                                          @Nullable BuildOutputInstantReaderImpl buildOutputReader,
                                          boolean isSingleVariantSync) {
    SyncAction syncAction = createSyncAction(false, isSingleVariantSync);
    BuildActionExecuter<SyncProjectModels> executor = connection.action(syncAction);

    prepare(executor, id, executionSettings, new GradleSyncNotificationListener(id, indicator, buildOutputReader), connection);
    return executor.run();
  }

  private void executeFullSyncAndGenerateSources(@NotNull ProjectConnection connection,
                                                 @NotNull GradleExecutionSettings executionSettings,
                                                 @NotNull ProgressIndicator indicator,
                                                 @NotNull ExternalSystemTaskId id,
                                                 @NotNull BuildOutputInstantReaderImpl buildOutputReader,
                                                 @NotNull SyncExecutionCallback callback) {
    SyncAction syncAction = createSyncAction(true, isSingleVariantSync(myProject));
    // We have to set an empty collection in #forTasks so Gradle knows we want to execute the build until run tasks step
    BuildActionExecuter<Void> executor = connection.action().projectsLoaded(syncAction, models -> callback.setDone(models, id))
                                                   .build().forTasks(emptyList());

    prepare(executor, id, executionSettings, new GradleSyncNotificationListener(id, indicator, buildOutputReader), connection);

    // If new API is not available (Gradle 4.7-), fall back to non compound sync.
    try {
      executor.run();
    }
    catch (UnsupportedVersionException e) {
      if (e.getMessage().contains("PhasedBuildActionExecuter API")) {
        executeFullSync(connection, executionSettings, indicator, id, buildOutputReader, callback);
      }
      else {
        throw e;
      }
    }
  }

  private static void executeVariantOnlySync(@NotNull ProjectConnection connection,
                                             @NotNull GradleExecutionSettings executionSettings,
                                             @NotNull ProgressIndicator indicator,
                                             @NotNull ExternalSystemTaskId id,
                                             @NotNull BuildOutputInstantReaderImpl buildOutputReader,
                                             @NotNull SyncExecutionCallback callback,
                                             @NotNull VariantOnlySyncOptions options) {
    VariantOnlySyncAction syncAction = new VariantOnlySyncAction(options);
    BuildActionExecuter<VariantOnlyProjectModels> executor = connection.action(syncAction);
    prepare(executor, id, executionSettings, new GradleSyncNotificationListener(id, indicator, buildOutputReader), connection);
    callback.setDone(executor.run(), id);
  }

  private static void executeVariantOnlySyncAndGenerateSources(@NotNull ProjectConnection connection,
                                                               @NotNull GradleExecutionSettings executionSettings,
                                                               @NotNull ProgressIndicator indicator,
                                                               @NotNull ExternalSystemTaskId id,
                                                               @NotNull BuildOutputInstantReaderImpl buildOutputReader,
                                                               @NotNull SyncExecutionCallback callback,
                                                               @NotNull VariantOnlySyncOptions options) {
    VariantOnlySyncAction syncAction = new VariantOnlySyncAction(options);
    // We have to set an empty collection in #forTasks so Gradle knows we want to execute the build until run tasks step
    BuildActionExecuter<Void> executor = connection.action().projectsLoaded(syncAction, models -> callback.setDone(models, id))
                                                   .build().forTasks(emptyList());
    prepare(executor, id, executionSettings, new GradleSyncNotificationListener(id, indicator, buildOutputReader), connection);

    // If new API is not available (Gradle 4.7-), fall back to non compound sync.
    try {
      executor.run();
    }
    catch (UnsupportedVersionException e) {
      if (e.getMessage().contains("PhasedBuildActionExecuter API")) {
        executeVariantOnlySync(connection, executionSettings, indicator, id, buildOutputReader, callback, options);
      }
      else {
        throw e;
      }
    }
  }

  @VisibleForTesting
  @NotNull
  SyncAction createSyncAction(boolean shouldGenerateSources, boolean isSingleVariantSync) {
    SyncActionOptions options = new SyncActionOptions();
    options.setSingleVariantSyncEnabled(isSingleVariantSync);
    if (options.isSingleVariantSyncEnabled()) {
      SelectedVariants selectedVariants = mySelectedVariantCollector.collectSelectedVariants();
      options.setSelectedVariants(selectedVariants);
      options.setShouldGenerateSources(shouldGenerateSources);
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
    @Nullable private final BuildOutputInstantReaderImpl myOutputReader;

    GradleSyncNotificationListener(@NotNull ExternalSystemTaskId taskId,
                                   @NotNull ProgressIndicator indicator,
                                   @Nullable BuildOutputInstantReaderImpl outputReader) {
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
      if (myOutputReader != null) {
        myOutputReader.append(text);
      }
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
      closeOutputReader();
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
      closeOutputReader();
    }

    private void closeOutputReader() {
      if (myOutputReader != null) {
        myOutputReader.close();
      }
    }
  }
}
