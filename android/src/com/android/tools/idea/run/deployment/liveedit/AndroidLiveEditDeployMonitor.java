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
package com.android.tools.idea.run.deployment.liveedit;

import static com.android.tools.idea.run.deployment.liveedit.ErrorReporterKt.errorMessage;

import com.android.annotations.Nullable;
import com.android.annotations.Trace;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.AdbInstaller;
import com.android.tools.deployer.Installer;
import com.android.tools.deployer.MetricsRecorder;
import com.android.tools.deployer.tasks.LiveUpdateDeployer;
import com.android.tools.idea.editors.literals.EditState;
import com.android.tools.idea.editors.literals.EditStatus;
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration;
import com.android.tools.idea.editors.literals.LiveEditService;
import com.android.tools.idea.editors.literals.LiveLiteralsMonitorHandler;
import com.android.tools.idea.editors.literals.LiveLiteralsService;
import com.android.tools.idea.editors.literals.EditEvent;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.run.deployment.AndroidExecutionTarget;
import com.android.tools.idea.util.StudioPathManager;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LiveEditEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension;

/**
 * Helper to set up Live Literal deployment monitoring.
 *
 * Since the UI / UX of this is still not fully agreed upon. This class is design to have MVP like
 * functionality just enough for compose user group to dogfood for now.
 *
  * The LiveEdit change detection & handling flow is as follows:
 * There are three thread contexts:
 * - The UI thread, which reports PSI events
 * - The LiveEditService executor, which queues changes and schedules
 *   LiveEdit pushes. Single-threaded.
 * - The AndroidLiveEditDeployMonitor executor, which handles
 *   compile/push of LiveEdit changes. Single-threaded.
 *
 * ┌──────────┐         ┌───────────┐
 * │ UI Thread├─────────┤PSIListener├─handleChangeEvent()─────────────────────────►
 * └──────────┘         └──────────┬┘
 *                                 │
 * ┌───────────────────────┐   ┌───▼────────┐
 * │LiveEditServiceExecutor├───┤EditListener├─────────────────────────────────────►
 * └───────────────────────┘   └──────┬─────┘
 *                                    │
 *                                    │                       ┌─────┐
 *                                    ├──────────────────────►│QUEUE│
 *                                    │                       └──┬──┘
 *                                    │ schedule()               │
 * ┌──────────────────────────┐       │                          │
 * │AndroidEditServiceExecutor├───────▼──────────────────────────▼──processChanges()
 * └──────────────────────────┘
 *
 * It is important that both executors owned by LiveEdit are single-threaded,
 * in order to ensure that each processes events serially without any races.
 *
 * LiveEditService registers a single PSI listener with the PsiManager.
 * This listener receives callbacks on the UI thread when PSI
 * events are generated. There is one LiveEditService instance per Project.
 *
 * AndroidLiveEditDeployMonitor registers one LiveEditService.EditListener
 * per Project with the corresponding Project's LiveEditService. When the
 * LiveEditService receives PSI events, the listener receives a callback
 * on a single-threaded application thread pool owned by the
 * LiveEditService.
 *
 * The EditListener callback enqueues the event in a collection of
 * "unhandled" events, schedules a LiveEdit compile+push, and returns
 * quickly to allow the thread pool to continue enqueuing events.
 *
 * The scheduled LiveEdit compile+push is executed on a single-threaded
 * executor owned by the EditListener. It handles changes as follows:
 * 1. Lock the queue  of unhandled changes
 * 2. Make a copy of the queue, clear the queue, then unlock the queue
 * 3. If the copy is empty, return
 * 4. Attempt to compile and push the copied changes
 * 5. If the compilation is successful, return.
 * 6. If the compilation is cancelled, lock queue, read-add the removed
 * events, then schedule another compile+push
 *
 * Compilation may be cancelled by PSI write actions, such as the user
 * continuing to type after making a change. It may also be prevented by
 * an ongoing write action, or a PSI write action from another source,
 * which is why it is safer to schedule a retry rather than assuming
 * whatever PSI modification cancelled the change will cause a LiveEdit
 * push.
 *
 * Note that this retry logic does NOT apply if the compilation explicitly
 * fails; only if it is cancelled by PSI write actions.
 *
 * Compilation is responsible for handling duplicate changes
 * originating from the same file, and performs de-duplication logic to
 * ensure that the same file is not re-compiled multiple times.
 */
public class AndroidLiveEditDeployMonitor {
  // TODO: The logging is overly excessive for now given we have no UI to provide feedback to the user
  // when things go wrong. This will be changed in the final product.
  private static final LogWrapper LOGGER = new LogWrapper(Logger.getInstance(AndroidLiveEditDeployMonitor.class));

  private static final EditStatus UPDATE_IN_PROGRESS = new EditStatus(EditState.IN_PROGRESS, "Live edit update in progress");

  private static final EditStatus DISCONNECTED = new EditStatus(EditState.PAUSED, "No apps are ready to receive live edits");

  private final @NotNull Project project;

  private final @NotNull LinkedHashMap<String, SourceInlineCandidate> sourceInlineCandidateCache;

  private @Nullable String applicationId;

  private final ScheduledExecutorService methodChangesExecutor = Executors.newSingleThreadScheduledExecutor();

  private final @NotNull AtomicReference<EditStatus> editStatus = new AtomicReference<>(LiveEditService.DISABLED_STATUS);

  private class EditStatusGetter implements LiveEditService.EditStatusProvider {
    @NotNull
    @Override
    public EditStatus invoke() {
      if (StringUtil.isEmpty(applicationId)) {
        return LiveEditService.DISABLED_STATUS;
      }

      boolean noRunningLiveEditApp = deviceIterator(project, applicationId).noneMatch(d -> {
        for (Client client : d.getClients()) {
          if (applicationId.equals(client.getClientData().getPackageName())) {
            return true;
          }
        }
        return false;
      });
      EditStatus status = editStatus.get();
      if (noRunningLiveEditApp) {
        // Set state to paused if no devices in past deploys are present but we have edits queued.
        return status.getEditState() == EditState.DISABLED ? status : DISCONNECTED;
      }
      return status;
    }
  }

  private class EditsListener implements Disposable {
    // Care should be given when modifying this field to preserve atomicity.
    private final ConcurrentLinkedQueue<EditEvent> changedMethodQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void dispose() {
      editStatus.set(LiveEditService.DISABLED_STATUS);
      methodChangesExecutor.shutdownNow();
    }

    // This method is invoked on the listener executor thread in LiveEditService and does not block the UI thread.
    public void onLiteralsChanged(EditEvent event) {
      if (!LiveEditApplicationConfiguration.Companion.getInstance().isLiveEditDevice()) {
        return;
      }

      if (StringUtil.isEmpty(applicationId)) {
        return;
      }

      if (editStatus.get().getEditState() == EditState.ERROR) {
        return;
      }

      changedMethodQueue.add(event);
      methodChangesExecutor.schedule(this::processChanges, LiveEditAdvancedConfiguration.getInstance().getRefreshRateMs(), TimeUnit.MILLISECONDS);
    }

    private void processChanges() {
      if (changedMethodQueue.isEmpty()) {
        return;
      }

      List<EditEvent> copy = new ArrayList<>();
      changedMethodQueue.removeIf(e -> {
        copy.add(e);
        return true;
      });

      editStatusChanged(editStatus.getAndUpdate(editStatus -> {
        switch (editStatus.getEditState()) {
          case PAUSED:
          case UP_TO_DATE:
          case IN_PROGRESS:
            return UPDATE_IN_PROGRESS;
          default:
            return editStatus;
        }
      }));

      if (!handleChangedMethods(project, applicationId, copy)) {
        changedMethodQueue.addAll(copy);
        methodChangesExecutor.schedule(this::processChanges, LiveEditAdvancedConfiguration.getInstance().getRefreshRateMs(), TimeUnit.MILLISECONDS);
      }
    }
  }

  public AndroidLiveEditDeployMonitor(@NotNull LiveEditService liveEditService, @NotNull Project project) {
    this.project = project;
    this.sourceInlineCandidateCache = liveEditService.getInlineCandidateCache();
    EditsListener editsListener = new EditsListener();
    liveEditService.addOnEditListener(editsListener::onLiteralsChanged);
    liveEditService.addEditStatusProvider(new EditStatusGetter());
    Disposer.register(liveEditService, editsListener);
  }

  public Callable<?> getCallback(String applicationId, IDevice device) {
    String deviceId = device.getSerialNumber();

    // TODO: Don't use Live Literal's reporting
    LiveLiteralsService.getInstance(project).liveLiteralsMonitorStopped(deviceId + "#" + applicationId);

    if (!LiveEditApplicationConfiguration.Companion.getInstance().isLiveEditDevice()) {
      LOGGER.info("Live Edit on device disabled via settings.");
      return null;
    }

    if (!supportLiveEdits(device)) {
      LOGGER.info("Live edit not support for device %s targeting app %s", project.getName(), applicationId);
      return null;
    }

    LOGGER.info("Creating monitor for project %s targeting app %s", project.getName(), applicationId);

    return () -> methodChangesExecutor
      .schedule(
        () -> {
          this.applicationId = applicationId;
          LiveEditService.getInstance(project).resetState();
          editStatusChanged(editStatus.getAndSet(LiveEditService.UP_TO_DATE_STATUS));

          LiveLiteralsMonitorHandler.DeviceType deviceType;
          if (device.isEmulator()) {
            deviceType = LiveLiteralsMonitorHandler.DeviceType.EMULATOR;
          }
          else {
            deviceType = LiveLiteralsMonitorHandler.DeviceType.PHYSICAL;
          }

          LiveLiteralsService.getInstance(project).liveLiteralsMonitorStarted(deviceId + "#" + applicationId, deviceType);
        },
        0L,
        TimeUnit.NANOSECONDS)
      .get();
  }

  private static void checkJetpackCompose(@NotNull Project project) {
    final List<IrGenerationExtension> pluginExtensions = IrGenerationExtension.Companion.getInstances(project);
    boolean found = false;
    for (IrGenerationExtension extension : pluginExtensions) {
      if (extension.getClass().getName().equals("com.android.tools.compose.ComposePluginIrGenerationExtension")) {
        found = true;
        break;
      }
    }

    if (!found) {
      throw LiveEditUpdateException.compilationError("Cannot find Jetpack Compose plugin in Android Studio. Is it enabled?", null, null);
    }
  }

  private static void checkIwiAvailable() {
    if (StudioFlags.OPTIMISTIC_INSTALL_SUPPORT_LEVEL.get() == StudioFlags.OptimisticInstallSupportLevel.DISABLED) {
      throw LiveEditUpdateException.compilationError("Cannot perform Live Edit without optimistic install support", null, null);
    }
  }

  @Trace
  private boolean handleChangedMethods(Project project,
                                       String packageName,
                                       List<EditEvent> changes) {
    LOGGER.info("Change detected for project %s targeting app %s", project.getName(), packageName);

    LiveEditEvent.Builder event = LiveEditEvent.newBuilder();

    long start = System.nanoTime();
    long compileFinish, pushFinish;

    ArrayList<AndroidLiveEditCodeGenerator.CodeGeneratorOutput> compiled = new ArrayList<>();
    try {
      // Check that Jetpack Compose plugin is enabled otherwise inline linking will fail with
      // unclear BackendException
      checkJetpackCompose(project);
      checkIwiAvailable();
      List<AndroidLiveEditCodeGenerator.CodeGeneratorInput> inputs = changes.stream().map(
        change ->
          new AndroidLiveEditCodeGenerator.CodeGeneratorInput(change.getFile(), change.getOrigin(), change.getParentGroup()))
        .collect(Collectors.toList());
      if (!new AndroidLiveEditCodeGenerator(project, sourceInlineCandidateCache).compile(inputs, compiled)) {
        return false;
      }
    } catch (LiveEditUpdateException e) {
      updateEditStatus(new EditStatus(EditState.PAUSED, errorMessage(e)));
      return true;
    }

    // Ignore FunctionType.NONE, since those are changes to non-function elements. Counting any change to a non-function as a non-compose
    // change might make the data useless, as a lot of "noisy" class-level/file-level PSI events are generated along with function edits.
    event.setHasNonCompose(compiled.stream().anyMatch(c -> c.getFunctionType() == AndroidLiveEditCodeGenerator.FunctionType.KOTLIN));

    compileFinish = System.nanoTime();
    event.setCompileDurationMs(TimeUnit.NANOSECONDS.toMillis(compileFinish - start));
    LOGGER.info("LiveEdit compile completed in %dms", event.getCompileDurationMs());

    Optional<LiveUpdateDeployer.UpdateLiveEditError> error = deviceIterator(project, packageName)
      .map(device -> pushUpdatesToDevice(packageName, device, compiled))
      .flatMap(List::stream)
      .findFirst();

    if (error.isPresent()) {
      updateEditStatus(new EditStatus(EditState.ERROR, error.get().getMessage()));
      event.setStatus(errorToStatus(error.get()));
    } else {
      updateEditStatus(LiveEditService.UP_TO_DATE_STATUS);
      event.setStatus(LiveEditEvent.Status.SUCCESS);
    }

    pushFinish = System.nanoTime();
    event.setPushDurationMs(TimeUnit.NANOSECONDS.toMillis(pushFinish - compileFinish));
    LOGGER.info("LiveEdit push completed in %dms", event.getPushDurationMs());

    logLiveEditEvent(event);
    return true;
  }

  private static LiveEditEvent.Status errorToStatus(LiveUpdateDeployer.UpdateLiveEditError error) {
    switch(error.getType()) {
      case ADDED_METHOD:
        return LiveEditEvent.Status.UNSUPPORTED_ADDED_METHOD;
      case REMOVED_METHOD:
        return LiveEditEvent.Status.UNSUPPORTED_REMOVED_METHOD;
      case ADDED_CLASS:
        return LiveEditEvent.Status.UNSUPPORTED_ADDED_CLASS;
      case ADDED_FIELD:
      case MODIFIED_FIELD:
        return LiveEditEvent.Status.UNSUPPORTED_ADDED_FIELD;
      case REMOVED_FIELD:
        return LiveEditEvent.Status.UNSUPPORTED_REMOVED_FIELD;
      case MODIFIED_SUPER:
      case ADDED_INTERFACE:
      case REMOVED_INTERFACE:
        return LiveEditEvent.Status.UNSUPPORTED_MODIFY_INHERITANCE;
      default:
        return LiveEditEvent.Status.UNKNOWN;
    }
  }

  private static final Random random = new Random();
  private static void logLiveEditEvent(LiveEditEvent.Builder event) {
    // Because LiveEdit could conceivably run every time the user stops typing, we log only 10% of events.
    if (random.nextDouble() < 0.1) {
      UsageTracker.log(AndroidStudioEvent.newBuilder().setCategory(AndroidStudioEvent.EventCategory.DEPLOYMENT)
                         .setKind(AndroidStudioEvent.EventKind.LIVE_EDIT_EVENT).setLiveEditEvent(event));
    }
  }

  private void updateEditStatus(EditStatus status) {
    editStatusChanged(editStatus.getAndUpdate(editStatus -> {
      if (editStatus.getEditState() == LiveEditService.DISABLED_STATUS.getEditState()) {
        return LiveEditService.DISABLED_STATUS;
      }
      return status;
    }));
  }

  private static Stream<IDevice> deviceIterator(Project project, String packageName) {
    List<AndroidSessionInfo> sessions = AndroidSessionInfo.findActiveSession(project);
    if (sessions == null) {
      LOGGER.info("No running session found for %s", packageName);
      return Stream.empty();
    }

    return sessions
      .stream()
      .map(AndroidSessionInfo::getExecutionTarget)
      .filter(t -> t instanceof AndroidExecutionTarget)
      .flatMap(t -> ((AndroidExecutionTarget)t).getRunningDevices().stream())
      .filter(AndroidLiveEditDeployMonitor::supportLiveEdits);
  }

  private static List<LiveUpdateDeployer.UpdateLiveEditError> pushUpdatesToDevice(
      String packageName, IDevice device, List<AndroidLiveEditCodeGenerator.CodeGeneratorOutput> updates) {
    AdbClient adb = new AdbClient(device, LOGGER);
    MetricsRecorder metrics = new MetricsRecorder();
    Installer installer = new AdbInstaller(getLocalInstaller(), adb, metrics.getDeployMetrics(), LOGGER, AdbInstaller.Mode.DAEMON);
    LiveUpdateDeployer deployer = new LiveUpdateDeployer();

    // TODO: Batch multiple updates in one LiveEdit operation; listening to all PSI events means multiple class events can be
    //  generated from a single keystroke, leading to multiple LEs and multiple recomposes.
    List<LiveUpdateDeployer.UpdateLiveEditError> results = new ArrayList<>();
    updates.forEach(update -> {
      boolean useDebugMode = LiveEditAdvancedConfiguration.getInstance().getUseDebugMode();
      boolean usePartialRecompose = LiveEditAdvancedConfiguration.getInstance().getUsePartialRecompose() &&
                                    (update.getFunctionType() == AndroidLiveEditCodeGenerator.FunctionType.COMPOSABLE ||
                                     update.getHasGroupId());
      LiveUpdateDeployer.UpdateLiveEditsParam param =
        new LiveUpdateDeployer.UpdateLiveEditsParam(
          update.getClassName(), update.getMethodName(), update.getMethodDesc(),
          usePartialRecompose,
          update.getGroupId(),
          update.getClassData(),
          update.getSupportClasses(), useDebugMode);


      if (useDebugMode) {
        writeDebugToTmp(update.getClassName().replaceAll("/", ".") + ".class", update.getClassData());
        for (String supportClassName : update.getSupportClasses().keySet()) {
          byte[] bytecode = update.getSupportClasses().get(supportClassName);
          writeDebugToTmp(supportClassName.replaceAll("/", ".") + ".class", bytecode);
        }
      }

      results.addAll(deployer.updateLiveEdit(installer, adb, packageName, param));
    });
    return results;
  }

  private static void writeDebugToTmp(String name, byte[] data) {
    String tmpPath = System.getProperty("java.io.tmpdir");
    if (tmpPath == null) {
      return;
    }
    Path path = Paths.get(tmpPath, name);
    try {
      Files.write(path, data);
      LOGGER.info("Wrote debug file at '%s'", path.toAbsolutePath());
    }
    catch (IOException e) {
      LOGGER.info("Unable to write debug file '%s'", path.toAbsolutePath());
    }
  }

  private static boolean supportLiveEdits(IDevice device) {
    return device.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.R);
  }

  // TODO: Unify this part.
  private static String getLocalInstaller() {
    Path path;
    if (StudioPathManager.isRunningFromSources()) {
      // Development mode
      path = StudioPathManager.resolvePathFromSourcesRoot("bazel-bin/tools/base/deploy/installer/android-installer");
    } else {
      path = Paths.get(PathManager.getHomePath(), "plugins/android/resources/installer");
    }
    return path.toString();
  }

  private void editStatusChanged(@NotNull EditStatus oldStatus) {
    if (editStatus.get() != oldStatus) {
      ApplicationManager.getApplication().invokeLater(ActionToolbarImpl::updateAllToolbarsImmediately);
    }
  }
}
