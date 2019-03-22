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

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleProjects.setSyncRequestedDuringBuild;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.clearStoredGradleJvmArgs;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.IN_BACKGROUND_ASYNC;
import static com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.MODAL_SYNC;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.ensureToolWindowContentInitialized;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;
import static java.lang.System.currentTimeMillis;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleTasksExecutor;
import com.android.tools.idea.gradle.project.build.output.AndroidGradleSyncTextConsoleView;
import com.android.tools.idea.gradle.project.importing.OpenMigrationToGradleUrlHyperlink;
import com.android.tools.idea.gradle.project.sync.cleanup.PreSyncProjectCleanUp;
import com.android.tools.idea.gradle.project.sync.idea.IdeaGradleSync;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.ng.NewGradleSync;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlySyncOptions;
import com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult;
import com.android.tools.idea.gradle.project.sync.precheck.PreSyncChecks;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.project.IndexingSuspender;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.Failure;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.FinishBuildEventImpl;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import java.util.List;
import java.util.Objects;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleSyncInvoker {
  @NotNull private final FileDocumentManager myFileDocumentManager;
  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final PreSyncProjectCleanUp myPreSyncProjectCleanUp;
  @NotNull private final PreSyncChecks myPreSyncChecks;

  @NotNull
  public static GradleSyncInvoker getInstance() {
    return ServiceManager.getService(GradleSyncInvoker.class);
  }

  public GradleSyncInvoker(@NotNull FileDocumentManager fileDocumentManager, @NotNull IdeInfo ideInfo) {
    this(fileDocumentManager, ideInfo, new PreSyncProjectCleanUp(), new PreSyncChecks());
  }

  private GradleSyncInvoker(@NotNull FileDocumentManager fileDocumentManager,
                            @NotNull IdeInfo ideInfo,
                            @NotNull PreSyncProjectCleanUp preSyncProjectCleanUp,
                            @NotNull PreSyncChecks preSyncChecks) {
    myFileDocumentManager = fileDocumentManager;
    myIdeInfo = ideInfo;
    myPreSyncProjectCleanUp = preSyncProjectCleanUp;
    myPreSyncChecks = preSyncChecks;
  }

  /**
   * This method should not be called within a {@link DumbModeTask}, the {@link IndexingSuspender} will take care of ensuring that
   * sync is not run at the same time as indexing.
   */
  public void requestProjectSyncAndSourceGeneration(@NotNull Project project,
                                                    @NotNull GradleSyncStats.Trigger trigger) {
    requestProjectSyncAndSourceGeneration(project, trigger, null);
  }

  /**
   * This method should not be called within a {@link DumbModeTask}, the {@link IndexingSuspender} will take care of ensuring that
   * sync is not run at the same time as indexing.
   */
  public void requestProjectSyncAndSourceGeneration(@NotNull Project project,
                                                    @NotNull GradleSyncStats.Trigger trigger,
                                                    @Nullable GradleSyncListener listener) {
    Request request = new Request(trigger);
    requestProjectSync(project, request, listener);
  }

  /**
   * This method should not be called within a {@link DumbModeTask}, the {@link IndexingSuspender} will take care of ensuring that
   * sync is not run at the same time as indexing.
   */
  public void requestProjectSync(@NotNull Project project, @NotNull Request request) {
    requestProjectSync(project, request, null);
  }

  /**
   * This method should not be called within a {@link DumbModeTask}, the {@link IndexingSuspender} will take care of ensuring that
   * sync is not run at the same time as indexing.
   */
  public void requestProjectSync(@NotNull Project project, @NotNull Request request, @Nullable GradleSyncListener listener) {
    if (GradleSyncState.getInstance(project).isSyncInProgress()) {
      return;
    }
    if (isBuildInProgress(project)) {
      setSyncRequestedDuringBuild(project, true);
      return;
    }

    Runnable syncTask = () -> {
      ensureToolWindowContentInitialized(project, GRADLE_SYSTEM_ID);
      if (prepareProject(project, listener)) {
        sync(project, request, listener);
      }
    };

    GradleSyncState.getInstance(project).syncTaskCreated(request);

    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      application.invokeAndWait(syncTask);
      return;
    }
    if (request.runInBackground) {
      TransactionGuard.getInstance().submitTransactionLater(project, syncTask);
    }
    else {
      TransactionGuard.getInstance().submitTransactionAndWait(syncTask);
    }
  }

  private static boolean isBuildInProgress(@NotNull Project project) {
    IdeFrame frame = ((WindowManagerEx)WindowManager.getInstance()).findFrameFor(project);
    StatusBarEx statusBar = frame == null ? null : (StatusBarEx)frame.getStatusBar();
    if (statusBar == null) {
      return false;
    }
    for (Pair<TaskInfo, ProgressIndicator> backgroundProcess : statusBar.getBackgroundProcesses()) {
      TaskInfo task = backgroundProcess.getFirst();
      if (task instanceof GradleTasksExecutor) {
        ProgressIndicator second = backgroundProcess.getSecond();
        if (second.isRunning()) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean prepareProject(@NotNull Project project, @Nullable GradleSyncListener listener) {
    GradleProjectInfo projectInfo = GradleProjectInfo.getInstance(project);
    if (AndroidProjectInfo.getInstance(project).requiresAndroidModel() || projectInfo.hasTopLevelGradleBuildFile()) {
      boolean isImportedProject = projectInfo.isImportedProject();
      if (!isImportedProject) {
        myFileDocumentManager.saveAllDocuments();
      }
      return true; // continue with sync.
    }
    invokeLaterIfProjectAlive(project, () -> {
      String msg = String.format("The project '%s' is not a Gradle-based project", project.getName());
      AndroidNotification.getInstance(project).showBalloon("Project Sync", msg, ERROR, new OpenMigrationToGradleUrlHyperlink());

      if (listener != null) {
        listener.syncFailed(project, msg);
      }
    });
    return false; // stop sync.
  }

  private void sync(@NotNull Project project, @NotNull Request request, @Nullable GradleSyncListener listener) {
    if (myIdeInfo.isAndroidStudio()) {
      // See https://code.google.com/p/android/issues/detail?id=169743
      // TODO move this method out of GradleUtil.
      clearStoredGradleJvmArgs(project);
    }

    invokeAndWaitIfNeeded((Runnable)() -> GradleSyncMessages.getInstance(project).removeProjectMessages());
    // Do not sync Sdk/Jdk when running from tests, these will be set up by the test infra.
    if (!request.skipPreSyncChecks) {
      PreSyncCheckResult checkResult = runPreSyncChecks(project);
      if (!checkResult.isSuccess()) {
        // User should have already warned that something is not right and sync cannot continue.
        String cause = nullToEmpty(checkResult.getFailureCause());
        handlePreSyncCheckFailure(project, cause, listener, request);
        return;
      }
    }

    // Do clean up tasks before calling sync started.
    // During clean up, we might change some gradle files, for example, gradle property files based on http settings, gradle wrappers and etc.
    // And any changes to gradle files after sync started will result in another sync needed.
    myPreSyncProjectCleanUp.cleanUp(project);

    // We only update UI on sync when re-importing projects. By "updating UI" we mean updating the "Build Variants" tool window and editor
    // notifications.  It is not safe to do this for new projects because the new project has not been opened yet.
    boolean isImportedProject = GradleProjectInfo.getInstance(project).isImportedProject();
    boolean started;
    if (request.useCachedGradleModels) {
      started = GradleSyncState.getInstance(project).skippedSyncStarted(!isImportedProject, request);
    }
    else {
      started = GradleSyncState.getInstance(project).syncStarted(!isImportedProject, request);
    }
    if (!started) {
      return;
    }

    if (listener != null) {
      listener.syncStarted(project, request.useCachedGradleModels, request.generateSourcesOnSuccess);
    }

    boolean useNewGradleSync = NewGradleSync.isEnabled(project);
    if (request.variantOnlySyncOptions == null) {
      removeAndroidModels(project);
    }

    GradleSync gradleSync = useNewGradleSync ? new NewGradleSync(project) : new IdeaGradleSync(project);
    gradleSync.sync(request, listener);
  }

  @VisibleForTesting
  @NotNull
  PreSyncCheckResult runPreSyncChecks(@NotNull Project project) {
    return myPreSyncChecks.canSyncAndTryToFix(project);
  }

  private static void handlePreSyncCheckFailure(@NotNull Project project,
                                                @NotNull String failureCause,
                                                @Nullable GradleSyncListener syncListener,
                                                @NotNull GradleSyncInvoker.Request request) {
    GradleSyncState syncState = GradleSyncState.getInstance(project);

    // Create an external task so we can display messages associated to it in the build view
    ExternalSystemTaskId taskId = createFailedPreCheckSyncTaskWithStartMessage(project);
    syncState.setExternalSystemTaskId(taskId);
    if (syncState.syncStarted(true, request)) {
      if (syncListener != null) {
        syncListener.syncStarted(project, request.useCachedGradleModels, request.generateSourcesOnSuccess);
      }
      syncState.syncFailed(failureCause);
      if (syncListener != null) {
        syncListener.syncFailed(project, failureCause);
      }
    }

    // Let build view know there were issues
    GradleSyncMessages messages = GradleSyncMessages.getInstance(project);
    List<Failure> failures = messages.showEvents(taskId);
    EventResult result = new FailureResultImpl(failures);
    FinishBuildEventImpl finishBuildEvent = new FinishBuildEventImpl(taskId, null, currentTimeMillis(), failureCause, result);
    ServiceManager.getService(project, SyncViewManager.class).onEvent(finishBuildEvent);
  }

  /**
   * Create a new {@link ExternalSystemTaskId} when sync cannot start due to pre check failures and add a StartBuildEvent to build view.
   *
   * @param project
   * @return
   */
  @NotNull
  public static ExternalSystemTaskId createFailedPreCheckSyncTaskWithStartMessage(@NotNull Project project) {
    // Create taskId
    ExternalSystemTaskId taskId = ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project);

    // Create StartBuildEvent
    String workingDir = toCanonicalPath(getBaseDirPath(project).getPath());
    DefaultBuildDescriptor buildDescriptor = new DefaultBuildDescriptor(taskId, "Preparing for sync", workingDir, currentTimeMillis());
    SyncViewManager syncManager = ServiceManager.getService(project, SyncViewManager.class);
    syncManager.onEvent(new StartBuildEventImpl(buildDescriptor, "Running pre sync checks...").withContentDescriptorSupplier(
      () -> {
        AndroidGradleSyncTextConsoleView consoleView = new AndroidGradleSyncTextConsoleView(project);
        return new RunContentDescriptor(consoleView, null, consoleView.getComponent(), "Gradle Sync");
      }));
    return taskId;
  }

  // See issue: https://code.google.com/p/android/issues/detail?id=64508
  private static void removeAndroidModels(@NotNull Project project) {
    // Remove all Android models from module. Otherwise, if re-import/sync fails, editors will not show the proper notification of the
    // failure.
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        facet.getConfiguration().setModel(null);
      }
    }
  }

  @NotNull
  public List<GradleModuleModels> fetchGradleModels(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    GradleSync gradleSync = NewGradleSync.isEnabled(project) ? new NewGradleSync(project) : new IdeaGradleSync(project);
    return gradleSync.fetchGradleModels(indicator);
  }

  public static class Request {
    public final GradleSyncStats.Trigger trigger;

    public boolean runInBackground = true;
    public boolean generateSourcesOnSuccess = true;
    public boolean cleanProject;
    public boolean useCachedGradleModels;
    public boolean skipAndroidPluginUpgrade;
    public boolean forceFullVariantsSync;
    public boolean skipPreSyncChecks;
    // Perform a variant-only sync if not null.
    @Nullable public VariantOnlySyncOptions variantOnlySyncOptions;

    @VisibleForTesting
    @NotNull
    public static Request testRequest() {
      return new Request(TRIGGER_TEST_REQUESTED);
    }

    public Request(@NotNull GradleSyncStats.Trigger trigger) {
      this.trigger = trigger;
    }

    @NotNull
    public ProgressExecutionMode getProgressExecutionMode() {
      return runInBackground ? IN_BACKGROUND_ASYNC : MODAL_SYNC;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Request request = (Request)o;
      return trigger == request.trigger &&
             runInBackground == request.runInBackground &&
             generateSourcesOnSuccess == request.generateSourcesOnSuccess &&
             cleanProject == request.cleanProject &&
             useCachedGradleModels == request.useCachedGradleModels &&
             skipAndroidPluginUpgrade == request.skipAndroidPluginUpgrade &&
             forceFullVariantsSync == request.forceFullVariantsSync &&
             skipPreSyncChecks == request.skipPreSyncChecks &&
             Objects.equals(variantOnlySyncOptions, request.variantOnlySyncOptions);
    }

    @Override
    public int hashCode() {
      return Objects
        .hash(trigger, runInBackground, generateSourcesOnSuccess, cleanProject, useCachedGradleModels, skipAndroidPluginUpgrade,
              forceFullVariantsSync, skipPreSyncChecks, variantOnlySyncOptions);
    }

    @Override
    public String toString() {
      return "RequestSettings{" +
             "trigger=" + trigger +
             ", runInBackground=" + runInBackground +
             ", generateSourcesOnSuccess=" + generateSourcesOnSuccess +
             ", cleanProject=" + cleanProject +
             ", useCachedGradleModels=" + useCachedGradleModels +
             ", skipAndroidPluginUpgrade=" + skipAndroidPluginUpgrade +
             ", forceFullVariantsSync=" + forceFullVariantsSync +
             ", skipPreSyncChecks=" + skipPreSyncChecks +
             ", variantOnlySyncOptions=" + variantOnlySyncOptions +
             '}';
    }
  }
}
