/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.SourceProvider;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ide.common.packaging.PackagingUtils;
import com.android.ide.common.repository.GradleVersion;
import com.android.repository.Revision;
import com.android.sdklib.AndroidVersion;
import com.android.tools.fd.client.AppState;
import com.android.tools.fd.client.InstantRunClient;
import com.android.tools.fd.client.InstantRunClient.FileTransfer;
import com.android.tools.fd.client.UpdateMode;
import com.android.tools.fd.runtime.ApplicationPatch;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.project.PostProjectSetupTasksExecutor;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.rendering.LogWrapper;
import com.android.tools.idea.run.*;
import com.android.tools.idea.stats.UsageTracker;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.JavaExecutionStack;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XExecutionStack;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.tools.idea.fd.InstantRunArtifactType.*;

/**
 * The {@linkplain InstantRunManager} is responsible for handling Instant Run related functionality
 * in the IDE: determining if an app is running with the fast deploy runtime, whether it's up to date, communicating with it, etc.
 */
public final class InstantRunManager implements ProjectComponent {
  public static final String MINIMUM_GRADLE_PLUGIN_VERSION_STRING = "2.0.0-alpha7";
  public static final GradleVersion MINIMUM_GRADLE_PLUGIN_VERSION = GradleVersion.parse(MINIMUM_GRADLE_PLUGIN_VERSION_STRING);
  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("InstantRun", ToolWindowId.RUN);

  public static final Logger LOG = Logger.getInstance("#InstantRun");
  private static final ILogger ILOGGER = new LogWrapper(LOG);

  @NotNull private final Project myProject;
  @NotNull private final FileChangeListener myFileChangeListener;

  /** Don't call directly: this is a project component instantiated by the IDE; use {@link #get(Project)} instead! */
  @SuppressWarnings("WeakerAccess") // Called by infrastructure
  public InstantRunManager(@NotNull Project project) {
    myProject = project;
    myFileChangeListener = new FileChangeListener(project);
    myFileChangeListener.setEnabled(InstantRunSettings.isInstantRunEnabled(project));
  }

  /** Returns the per-project instance of the fast deploy manager */
  @NotNull
  public static InstantRunManager get(@NotNull Project project) {
    //noinspection ConstantConditions
    return project.getComponent(InstantRunManager.class);
  }

  /** Finds the devices associated with all run configurations for the given project */
  @NotNull
  public static List<IDevice> findDevices(@Nullable Project project) {
    if (project == null) {
      return Collections.emptyList();
    }

    List<RunContentDescriptor> runningProcesses = ExecutionManager.getInstance(project).getContentManager().getAllDescriptors();
    if (runningProcesses.isEmpty()) {
      return Collections.emptyList();
    }
    List<IDevice> devices = Lists.newArrayList();
    for (RunContentDescriptor descriptor : runningProcesses) {
      ProcessHandler processHandler = descriptor.getProcessHandler();
      if (processHandler == null || processHandler.isProcessTerminated() || processHandler.isProcessTerminating()) {
        continue;
      }

      devices.addAll(getConnectedDevices(processHandler));
    }

    return devices;
  }

  @NotNull
  private static List<IDevice> getConnectedDevices(@NotNull ProcessHandler processHandler) {
    if (processHandler.isProcessTerminated() || processHandler.isProcessTerminating()) {
      return Collections.emptyList();
    }

    if (processHandler instanceof AndroidProcessHandler) {
      return ImmutableList.copyOf(((AndroidProcessHandler)processHandler).getDevices());
    }
    else {
      Client c = processHandler.getUserData(AndroidProgramRunner.ANDROID_DEBUG_CLIENT);
      if (c != null && c.isValid()) {
        return Collections.singletonList(c.getDevice());
      }
    }

    return Collections.emptyList();
  }

  @NotNull
  public static AndroidVersion getMinDeviceApiLevel(@NotNull ProcessHandler processHandler) {
    AndroidVersion version = processHandler.getUserData(AndroidProgramRunner.ANDROID_DEVICE_API_LEVEL);
    return version == null ? AndroidVersion.DEFAULT : version;
  }

  /**
   * Checks whether the app associated with the given module is already running on the given device
   *
   * @param device the device to check
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   * @return true if the app is already running in foreground and is listening for incremental updates
   */
  public static boolean isAppInForeground(@NotNull IDevice device, @NotNull Module module) {
    return getInstantRunClient(module).getAppState(device) == AppState.FOREGROUND;
  }

  /**
   * Returns the build id in the project as seen by the IDE
   *
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   * @return the build id, if found
   */
  @Nullable
  private static String getLocalBuildTimestamp(@NotNull Module module) {
    AndroidGradleModel model = getAppModel(module);
    InstantRunBuildInfo buildInfo = model == null ? null : InstantRunBuildInfo.get(model);
    return buildInfo == null ? null : buildInfo.getTimeStamp();
  }

  /**
   * Checks whether the local and remote build timestamps match.
   *
   * @param device the device to pull the id from
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   * @return true if the build timestamps match. If not, there has been some intermediate build locally (or a clean)
   *              such that Gradle state doesn't match what's on the device
   */
  public static boolean buildTimestampsMatch(@NotNull IDevice device, @NotNull Module module) {
    String localTimestamp = getLocalBuildTimestamp(module);
    if (StringUtil.isEmpty(localTimestamp)) {
      LOG.info("Local build timestamp is empty!");
      return false;
    }

    if (InstantRunClient.USE_BUILD_ID_TEMP_FILE) {
      // If the build id is saved in /data/local/tmp, then the build id isn't cleaned when the package is uninstalled.
      // So we first check that the app is still installed. Note: this doesn't yet guarantee that you have uninstalled and then
      // re-installed a different apk with the same package name..
      // https://code.google.com/p/android/issues/detail?id=198715

      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null : "Instant Run requires a Gradle model";
      String pkgName;
      try {
        pkgName = ApkProviderUtil.computePackageName(facet);
      }
      catch (ApkProvisionException e) {
        throw new RuntimeException(e); // should not happen for Gradle projects..
      }

      // check whether the package is installed on the device: we do this by checking the package manager for whether the app exists,
      // but we could potentially simplify this to just checking whether the package folder exists
      if (ServiceManager.getService(InstalledApkCache.class).getInstallState(device, pkgName) == null) {
        LOG.info("Package " + pkgName + " was not detected on the device.");
        return false;
      }
    }

    String deviceBuildTimestamp = getInstantRunClient(module).getDeviceBuildTimestamp(device);
    LOG.info(String.format("Build timestamps: Local: %1$s, Device: %2$s", localTimestamp, deviceBuildTimestamp));
    return localTimestamp.equals(deviceBuildTimestamp);
  }

  public static boolean apiLevelsMatch(@NotNull IDevice device, @NotNull Module module) {
    AndroidGradleModel model = getAppModel(module);
    InstantRunBuildInfo buildInfo = model == null ? null : InstantRunBuildInfo.get(model);
    return buildInfo != null && buildInfo.getApiLevel() == device.getVersion().getApiLevel();
  }

  /**
   * Called after a build &amp; successful push to device: updates the build id on the device to whatever the
   * build id was assigned by Gradle.
   *
   * @param device the device to push to
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   */
  public static void transferLocalIdToDeviceId(@NotNull IDevice device, @NotNull Module module) {
    String buildId = getLocalBuildTimestamp(module);
    assert !StringUtil.isEmpty(buildId) : "Unable to detect build timestamp";

    getInstantRunClient(module).transferLocalIdToDeviceId(device, buildId);
  }

  /**
   * Restart the activity on this device, if it's running and is in the foreground
   * @param device the device to apply the change to
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   */
  public static void restartActivity(@NotNull IDevice device, @NotNull Module module) {
    getInstantRunClient(module).restartActivity(device);
  }

  @Nullable
  private static AndroidGradleModel getAppModel(@NotNull Module module) {
    AndroidFacet facet = findAppModule(module, module.getProject());
    if (facet == null) {
      return null;
    }

    return AndroidGradleModel.get(facet);
  }

  /**
   * Checks whether the app associated with the given module is capable of being run time patched
   * (whether or not it's running). This checks whether we have a Gradle project, and if that
   * Gradle project is using a recent enough Gradle plugin with incremental support, etc. It
   * also checks whether the user has disabled instant run.
   *
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   * @return true if the app is using an incremental support enabled Gradle plugin
   */
  public static boolean isPatchableApp(@NotNull Module module) {
    if (!InstantRunSettings.isInstantRunEnabled(module.getProject())) {
      return false;
    }

    return isPatchableApp(getAppModel(module));
  }

  public static boolean isPatchableApp(@Nullable AndroidGradleModel model) {
    if (model == null) {
      return false;
    }

    String version = model.getAndroidProject().getModelVersion();

    /**
     * {@link Revision} doesn't support comparing across -alpha and -beta minor versions, so we use
     * {@link PostProjectSetupTasksExecutor#needsUpdate}.
     */
    if (PostProjectSetupTasksExecutor.needsUpdate(version, MINIMUM_GRADLE_PLUGIN_VERSION_STRING)) {
      LOG.info("Instant run is not supported by current version: " + version + ", requires: " + MINIMUM_GRADLE_PLUGIN_VERSION_STRING);
      return false;
    }

    try {
      return model.getSelectedVariant().getMainArtifact().getInstantRun().isSupportedByArtifact();
    } catch (Exception e) {
      LOG.info("Instant Run not supported by current variant: " + version);
      return false;
    }
  }

  /** Returns true if the device is capable of running Instant Run */
  public static boolean isInstantRunCapableDeviceVersion(@NotNull AndroidVersion version) {
    return version.getApiLevel() >= 15;
  }

  /**
   * Returns true if a full build is required for the app. Currently, this is the
   * case if something in the manifest has changed (at the moment, we're only looking
   * at manifest file edits, not diffing the contents or disregarding "irrelevant"
   * edits such as whitespace or comments.
   */
  public static boolean needsFullBuild(@NotNull IDevice device, @NotNull Module module) {
    AndroidVersion deviceVersion = device.getVersion();
    if (!isInstantRunCapableDeviceVersion(deviceVersion)) {
      String message = "Device with API level " + deviceVersion + " not capable of instant run.";
      LOG.info(message);
      UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_INSTANTRUN, UsageTracker.ACTION_INSTANTRUN_FULLBUILD, message, null);
      return true;
    }

    if (manifestChanged(device, module)) {
      // Yes, some resources have changed.
      String message = "Manifest or some resource referenced from the manifest has changed.";
      LOG.info(message);
      UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_INSTANTRUN, UsageTracker.ACTION_INSTANTRUN_FULLBUILD, message, null);
      return true;
    }

    return false;
  }

  /**
   * Returns true if the manifest or a resource referenced from the manifest has changed since the last manifest push to the device
   */
  private static boolean manifestChanged(@NotNull IDevice device, @NotNull Module module) {
    AndroidFacet facet = findAppModule(module, module.getProject());
    if (facet == null) {
      return false;
    }

    String pkgName = getPackageName(facet);
    if (pkgName == null) {
      return true;
    }

    InstalledPatchCache cache = ServiceManager.getService(InstalledPatchCache.class);

    long currentTimeStamp = getManifestLastModified(facet);
    long installedTimeStamp = cache.getInstalledManifestTimestamp(device, pkgName);

    if (currentTimeStamp <= installedTimeStamp) {
      // See if the resources have changed.
      // Since this method can be called before we've built, we're looking at the previous
      // manifest now. However, the above timestamp check will already cause us to treat
      // manifest edits as requiring restarts -- so the goal here is to look for the referenced
      // resources from the manifest (when the manifest itself hasn't been edited) and see
      // if any of *them* have changed.
      HashCode currentHash = InstalledPatchCache.computeManifestResources(facet);
      HashCode installedHash = cache.getInstalledManifestResourcesHash(device, pkgName);
      if (installedHash != null && !installedHash.equals(currentHash)) {
        return true;
      }

      return false;
    }

    return true;
  }

  public static boolean hasLocalCacheOfDeviceData(@NotNull IDevice device, @NotNull Module module) {
    AndroidFacet facet = findAppModule(module, module.getProject());
    if (facet == null) {
      return false;
    }

    String pkgName = getPackageName(facet);
    if (pkgName == null) {
      return true;
    }

    InstalledPatchCache cache = ServiceManager.getService(InstalledPatchCache.class);
    return cache.getInstalledManifestTimestamp(device, pkgName) > 0;
  }

  /**
   * Returns the timestamp of the most recently modified manifest file applicable for the given facet
   */
  private static long getManifestLastModified(@NotNull AndroidFacet facet) {
    long maxLastModified = 0L;
    AndroidModel androidModel = facet.getAndroidModel();
    if (androidModel != null) {
      // Suppress deprecation: the recommended replacement is not suitable
      // (that's an API for VirtualFiles; we need the java.io.File instances)
      //noinspection deprecation
      for (SourceProvider provider : androidModel.getActiveSourceProviders()) {
        File manifest = provider.getManifestFile();
        long lastModified = manifest.lastModified();
        maxLastModified = Math.max(maxLastModified, lastModified);
      }
    }

    return maxLastModified;
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "InstantRunManager";
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  public FileChangeListener.Changes getChangesAndReset() {
    return myFileChangeListener.getChangesAndReset();
  }

  /** Synchronizes the file listening state with whether instant run is enabled */
  static void updateFileListener(@NotNull Project project) {
    InstantRunManager manager = get(project);
    manager.myFileChangeListener.setEnabled(InstantRunSettings.isInstantRunEnabled(project));
  }

  /** Looks up the merged manifest file for a given facet */
  @Nullable
  public static File findMergedManifestFile(@NotNull AndroidFacet facet) {
    AndroidGradleModel model = AndroidGradleModel.get(facet);
    if (model != null) {
      AndroidArtifact mainArtifact = model.getSelectedVariant().getMainArtifact();
      Collection<AndroidArtifactOutput> outputs = mainArtifact.getOutputs();
      for (AndroidArtifactOutput output : outputs) {
        // For now, use first manifest file that exists
        File manifest = output.getGeneratedManifest();
        if (manifest.exists()) {
          return manifest;
        }
      }
    }

    return null;
  }

  @NotNull
  public static String getIncrementalDexTask(@NotNull AndroidGradleModel model, @NotNull Module module) {
    assert isInstantRunSupported(model) : module;
    String taskName = model.getSelectedVariant().getMainArtifact().getInstantRun().getIncrementalAssembleTaskName();
    String gradlePath = GradleUtil.getGradlePath(module);
    if (gradlePath != null) {
      taskName = gradlePath + ":" + taskName;
    }
    return taskName;
  }

  /** Returns true if Instant Run is supported for this gradle model (whether or not it's enabled) */
  public static boolean isInstantRunSupported(@NotNull AndroidGradleModel model) {
    String version = model.getAndroidProject().getModelVersion();
    try {
      GradleVersion modelVersion = GradleVersion.tryParse(version);
      return modelVersion == null || modelVersion.compareTo(MINIMUM_GRADLE_PLUGIN_VERSION) >= 0;
    } catch (NumberFormatException e) {
      Logger.getInstance(InstantRunManager.class).warn("Failed to parse '" + version + "'", e);
      return false;
    }
  }

  @NotNull
  private static InstantRunClient getInstantRunClient(@NotNull Module module) {
    AndroidFacet facet = findAppModule(module, module.getProject());
    assert facet != null : module;
    AndroidGradleModel model = AndroidGradleModel.get(facet);
    assert model != null;
    return getInstantRunClient(model, facet);
  }

  @NotNull
  private static InstantRunClient getInstantRunClient(@NotNull AndroidGradleModel model, @NotNull AndroidFacet facet) {
    String packageName = getPackageName(facet);
    assert packageName != null : "Unable to obtain package name for " + facet.getModule().getName();
    long token = PackagingUtils.computeApplicationHash(model.getAndroidProject().getBuildFolder());
    return new InstantRunClient(packageName, new InstantRunUserFeedback(facet.getModule()), ILOGGER, token);
  }

  /**
   * Pushes the artifacts in the given {@link InstantRunBuildInfo} to the given device.
   * If the app is running, the artifacts are sent directly to the server running as part of the app.
   * Otherwise, we save it to a file on the device.
   *
   * @return true if the method handled app restart; false if the caller needs to
   * manually starts the app.
   */
  public boolean pushArtifacts(@NotNull final IDevice device,
                               @NotNull final AndroidFacet facet,
                               @NotNull UpdateMode updateMode,
                               @NotNull InstantRunBuildInfo buildInfo) throws InstantRunPushFailedException {
    if (!buildInfo.canHotswap()) {
      updateMode = updateMode.combine(UpdateMode.COLD_SWAP);
    }

    AndroidGradleModel model = AndroidGradleModel.get(facet);
    assert model != null : "Instant Run push artifacts called without a Gradle model";

    List<FileTransfer> files = Lists.newArrayList();
    InstantRunClient client = getInstantRunClient(model, facet);

    boolean appRunning = isAppInForeground(device, facet.getModule());

    List<InstantRunArtifact> artifacts = buildInfo.getArtifacts();
    for (InstantRunArtifact artifact : artifacts) {
      InstantRunArtifactType type = artifact.type;
      File file = artifact.file;
      switch (type) {
        case MAIN:
          // Should only be used here when we're doing a *compatible*
          // resource swap and also got an APK for split. Ignore here.
          continue;
        case SPLIT:
          // Should never be used with this method: APK splits should
          // be pushed by SplitApkDeployTask
          assert false : artifact;
          break;
        case RESOURCES:
          updateMode = updateMode.combine(UpdateMode.WARM_SWAP);
          files.add(FileTransfer.createResourceFile(file));
          break;
        case DEX:
          String name = file.getParentFile().getName() + "-" + file.getName();
          files.add(FileTransfer.createSliceDex(file, name));
          break;
        case RESTART_DEX:
          files.add(FileTransfer.createRestartDex(file));
          break;
        case RELOAD_DEX:
          if (appRunning) {
            files.add(FileTransfer.createHotswapPatch(file));
          } else {
            // Gradle created a reload dex, but the app is no longer running.
            // If it created a cold swap artifact, we can use it; otherwise we're out of luck.
            if (!buildInfo.hasOneOf(DEX, RESTART_DEX, SPLIT)) {
              throw new InstantRunPushFailedException("Can't apply hot swap patch: app is no longer running");
            }
          }
          break;
        default:
          assert false : artifact;
      }
    }

    boolean needRestart;
    String pkgName = getPackageName(facet);
    String buildId = getLocalBuildTimestamp(facet.getModule());
    assert !StringUtil.isEmpty(buildId) : "Unable to detect build timestamp";

    if (appRunning) {
      List<ApplicationPatch> changes = getApplicationPatches(files);
      Project project = facet.getModule().getProject();
      boolean restartActivity = InstantRunSettings.isRestartActivity(project);
      boolean showToast = InstantRunSettings.isShowToastEnabled(project);
      client.pushPatches(device, buildId, changes, updateMode, restartActivity, showToast);

      // Note that while we update the patch cache with the resource file timestamp here,
      // we *don't* do that for the manifest file: the resource timestamp is updated because
      // the resource files will be pushed to the app, but the manifest changes can't be.
      if (pkgName != null) {
        refreshDebugger(pkgName);
      }
      needRestart = false;
    }
    else {
      // Push to data directory
      client.pushFiles(files, device, buildId);
      needRestart = true;
    }

    logFilesPushed(files, needRestart);
    return needRestart;
  }

  private static void logFilesPushed(@NotNull List<FileTransfer> files, boolean needRestart) {
    StringBuilder sb = new StringBuilder("Pushing files: ");
    if (needRestart) {
      sb.append("(needs restart) ");
    }

    sb.append('[');
    String separator = "";
    for (int i = 0; i < files.size(); i++) {
      sb.append(separator);
      sb.append(files.get(i).source.getName());
      sb.append(" as ");
      sb.append(files.get(i).name);

      separator = ", ";
    }
    sb.append(']');

    LOG.info(sb.toString());
  }

  @NonNull
  private static List<ApplicationPatch> getApplicationPatches(List<FileTransfer> files) {
    List<ApplicationPatch> changes = new ArrayList<ApplicationPatch>(files.size());
    for (FileTransfer file : files) {
      try {
        changes.add(file.getPatch());
      } catch (IOException e) {
        LOG.warn("Couldn't read file " + file);
      }
    }
    return changes;
  }

  private void refreshDebugger(@NotNull String packageName) {
    // First we reapply the breakpoints on the new code, otherwise the breakpoints
    // remain set on the old classes and will never be hit again.
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        DebuggerManagerEx debugger = DebuggerManagerEx.getInstanceEx(myProject);
        if (!debugger.getSessions().isEmpty()) {
          List<Breakpoint> breakpoints = debugger.getBreakpointManager().getBreakpoints();
          for (Breakpoint breakpoint : breakpoints) {
            if (breakpoint.isEnabled()) {
              breakpoint.setEnabled(false);
              breakpoint.setEnabled(true);
            }
          }
        }
      }
    });

    // Now we refresh the call-stacks and the variable panes.
    DebuggerManagerEx debugger = DebuggerManagerEx.getInstanceEx(myProject);
    for (final DebuggerSession session : debugger.getSessions()) {
      Client client = session.getProcess().getProcessHandler().getUserData(AndroidProgramRunner.ANDROID_DEBUG_CLIENT);
      if (client != null && client.isValid() && StringUtil.equals(packageName, client.getClientData().getClientDescription())) {
        session.getProcess().getManagerThread().invoke(new DebuggerCommandImpl() {
          @Override
          protected void action() throws Exception {
            DebuggerContextImpl context = session.getContextManager().getContext();
            SuspendContextImpl suspendContext = context.getSuspendContext();
            if (suspendContext != null) {
              XExecutionStack stack = suspendContext.getActiveExecutionStack();
              if (stack != null) {
                ((JavaExecutionStack)stack).initTopFrame();
              }
            }
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                session.refresh(false);
                XDebugSession xSession = session.getXDebugSession();
                if (xSession != null) {
                  xSession.rebuildViews();
                }
              }
            });
          }
        });
      }
    }
  }

  public static void displayVerifierStatus(@NotNull AndroidFacet facet, @NotNull InstantRunBuildInfo buildInfo) {
    if (!buildInfo.canHotswap()) {
      String status = buildInfo.getVerifierStatus();
      if (status.isEmpty()) {
        return;
      }

      // Convert tokens like "FIELD_REMOVED" to "Field Removed" for better readability
      status = StringUtil.capitalizeWords(status.toLowerCase(Locale.US).replace('_', ' '), true);
      @Language("HTML") String message = "Instant Run couldn't apply changes on the fly: " + status;
      new InstantRunUserFeedback(facet.getModule()).verifierFailure(message);

      LOG.info("Instant run verifier failure: " + status);
      UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_INSTANTRUN, UsageTracker.ACTION_INSTANTRUN_FULLBUILD, status, null);
    }
  }

  @Nullable
  private static String getPackageName(@Nullable AndroidFacet facet) {
    if (facet == null) {
      return null;
    }

    try {
      return ApkProviderUtil.computePackageName(facet);
    }
    catch (ApkProvisionException e) {
      return null;
    }
  }

  @Nullable
  public static AndroidFacet findAppModule(@Nullable Module module, @NotNull Project project) {
    if (module != null) {
      assert module.getProject() == project;
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && !facet.isLibraryProject()) {
        return facet;
      }
    }

    // TODO: Here we should really look for app modules that *depend*
    // on the given module (if non null), not just use the first app
    // module we find.

    for (Module m : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(m);
      if (facet != null && !facet.isLibraryProject()) {
        return facet;
      }
    }
    return null;
  }

  public static void showToast(@NotNull IDevice device, @NotNull Module module, @NotNull final String message) {
    try {
      getInstantRunClient(module).showToast(device, message);
    }
    catch (Throwable e) {
      LOG.warn(e);
    }
  }
}
