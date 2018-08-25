// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.android.tools.idea.run;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.fd.*;
import com.android.tools.idea.fd.gradle.InstantRunGradleSupport;
import com.android.tools.idea.fd.gradle.InstantRunGradleUtils;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.run.editor.*;
import com.android.tools.idea.run.tasks.InstantRunNotificationTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProviderFactory;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.LaunchUtils;
import com.android.tools.idea.run.util.MultiUserUtils;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.fd.gradle.InstantRunGradleSupport.*;

public abstract class AndroidRunConfigurationBase extends ModuleBasedConfiguration<JavaRunConfigurationModule> implements PreferGradleMake {

  private static final Logger LOG = Logger.getInstance(AndroidRunConfigurationBase.class);

  private static final String GRADLE_SYNC_FAILED_ERR_MSG = "Gradle project sync failed. Please fix your project and try again.";

  /**
   * Element name used to group the {@link ProfilerState} settings
   */
  private static final String PROFILERS_ELEMENT_NAME = "Profilers";

  private static final DialogWrapper.DoNotAskOption ourKillLaunchOption = new MyDoNotPromptOption();

  public boolean CLEAR_LOGCAT = false;
  public boolean SHOW_LOGCAT_AUTOMATICALLY = false;
  public boolean SKIP_NOOP_APK_INSTALLATIONS = true; // skip installation if the APK hasn't hasn't changed
  public boolean FORCE_STOP_RUNNING_APP = true; // if no new apk is being installed, then stop the app before launching it again

  private final ProfilerState myProfilerState;

  private final boolean myAndroidTests;

  private final DeployTargetContext myDeployTargetContext = new DeployTargetContext();
  private final AndroidDebuggerContext myAndroidDebuggerContext = new AndroidDebuggerContext(AndroidJavaDebugger.ID);

  @NotNull
  @Transient
  // This is needed instead of having the output model directly because the apk providers can be created before getting the model.
  protected transient final DefaultPostBuildModelProvider myOutputProvider = new DefaultPostBuildModelProvider();

  public AndroidRunConfigurationBase(final Project project, final ConfigurationFactory factory, boolean androidTests) {
    super(new JavaRunConfigurationModule(project, false), factory);

    myProfilerState = new ProfilerState();
    myAndroidTests = androidTests;
  }

  @Override
  public final void checkConfiguration() throws RuntimeConfigurationException {
    List<ValidationError> errors = validate(null);
    if (errors.isEmpty()) {
      return;
    }
    // TODO: Do something with the extra error information? Error count?
    ValidationError topError = Ordering.natural().max(errors);
    switch (topError.getSeverity()) {
      case FATAL:
        throw new RuntimeConfigurationError(topError.getMessage(), topError.getQuickfix());
      case WARNING:
        throw new RuntimeConfigurationWarning(topError.getMessage(), topError.getQuickfix());
      case INFO:
      default:
        break;
    }
  }

  /**
   * We collect errors rather than throwing to avoid missing fatal errors by exiting early for a warning.
   * We use a separate method for the collection so the compiler prevents us from accidentally throwing.
   */
  public List<ValidationError> validate(@Nullable Executor executor) {
    List<ValidationError> errors = Lists.newArrayList();
    JavaRunConfigurationModule configurationModule = getConfigurationModule();
    try {
      configurationModule.checkForWarning();
    }
    catch (RuntimeConfigurationException e) {
      errors.add(ValidationError.fromException(e));
    }
    final Module module = configurationModule.getModule();
    if (module == null) {
      // Can't proceed, and fatal error has been caught in ConfigurationModule#checkForWarnings
      return errors;
    }

    final Project project = module.getProject();
    if (AndroidProjectInfo.getInstance(project).requiredAndroidModelMissing()) {
      errors.add(ValidationError.fatal(GRADLE_SYNC_FAILED_ERR_MSG));
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      // Can't proceed.
      return ImmutableList.of(ValidationError.fatal(AndroidBundle.message("no.facet.error", module.getName())));
    }
    if (!facet.isAppProject() && facet.getProjectType() != PROJECT_TYPE_TEST) {
      if (facet.isLibraryProject() || facet.getProjectType() == PROJECT_TYPE_FEATURE) {
        Pair<Boolean, String> result = supportsRunningLibraryProjects(facet);
        if (!result.getFirst()) {
          errors.add(ValidationError.fatal(result.getSecond()));
        }
      }
      else {
        errors.add(ValidationError.fatal(AndroidBundle.message("run.error.apk.not.valid")));
      }
    }
    if (facet.getConfiguration().getAndroidPlatform() == null) {
      errors.add(ValidationError.fatal(AndroidBundle.message("select.platform.error")));
    }
    if (facet.getManifest() == null && facet.getProjectType() != PROJECT_TYPE_INSTANTAPP) {
      errors.add(ValidationError.fatal(AndroidBundle.message("android.manifest.not.found.error")));
    }
    errors.addAll(getDeployTargetContext().getCurrentDeployTargetState().validate(facet));

    errors.addAll(getApkProvider(facet, getApplicationIdProvider(facet)).validate());

    errors.addAll(checkConfiguration(facet));
    AndroidDebuggerState androidDebuggerState = myAndroidDebuggerContext.getAndroidDebuggerState();
    if (androidDebuggerState != null) {
      errors.addAll(androidDebuggerState.validate(facet, executor));
    }

    errors.addAll(myProfilerState.validate());

    return errors;
  }

  /**
   * Returns whether the configuration supports running library projects, and if it doesn't, then an explanation as to why it doesn't.
   */
  protected abstract Pair<Boolean, String> supportsRunningLibraryProjects(@NotNull AndroidFacet facet);

  @NotNull
  protected abstract List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet);

  /**
   * Subclasses should override to adjust the launch options.
   */
  @NotNull
  protected LaunchOptions.Builder getLaunchOptions() {
    return LaunchOptions.builder()
      .setClearLogcatBeforeStart(CLEAR_LOGCAT)
      .setSkipNoopApkInstallations(SKIP_NOOP_APK_INSTALLATIONS)
      .setForceStopRunningApp(FORCE_STOP_RUNNING_APP);
  }

  @Override
  public Collection<Module> getValidModules() {
    final List<Module> result = new ArrayList<>();
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    for (Module module : modules) {
      if (AndroidFacet.getInstance(module) != null) {
        result.add(module);
      }
    }
    return result;
  }

  @NotNull
  public List<DeployTargetProvider> getApplicableDeployTargetProviders() {
    List<DeployTargetProvider> targets = Lists.newArrayList();

    for (DeployTargetProvider target : getDeployTargetContext().getDeployTargetProviders()) {
      if (target.isApplicable(myAndroidTests)) {
        targets.add(target);
      }
    }

    return targets;
  }

  protected void validateBeforeRun(@NotNull Executor executor) throws ExecutionException {
    List<ValidationError> errors = validate(executor);
    ValidationUtil.promptAndQuickFixErrors(getProject(), errors);
  }

  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    validateBeforeRun(executor);

    final Module module = getConfigurationModule().getModule();
    assert module != null : "Enforced by fatal validation check in checkConfiguration.";
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null : "Enforced by fatal validation check in checkConfiguration.";

    final Project project = env.getProject();
    final boolean forceColdswap = !InstantRunUtils.isInvokedViaHotswapAction(env);
    final boolean instantRunEnabled = InstantRunSettings.isInstantRunEnabled();
    final AndroidSessionInfo existingSessionInfo = AndroidSessionInfo.findOldSession(project, null, getUniqueID());

    boolean couldHaveHotswapped = false;
    DeviceFutures deviceFutures = null;

    final boolean isDebugging = executor instanceof DefaultDebugExecutor;

    // Figure out deploy target, prompt user if needed (ignore completely if user chose to hotswap).
    if (forceColdswap) {
      DeployTarget deployTarget = getDeployTarget(executor, env, isDebugging, facet);
      if (deployTarget == null) { // if user doesn't select a deploy target from the dialog
        return null;
      }

      DeployTargetState deployTargetState = getDeployTargetContext().getCurrentDeployTargetState();
      if (deployTarget.hasCustomRunProfileState(executor)) {
        return deployTarget.getRunProfileState(executor, env, deployTargetState);
      }

      deviceFutures = deployTarget.getDevices(deployTargetState, facet, getDeviceCount(isDebugging), isDebugging, getUniqueID());
      if (deviceFutures == null) {
        // The user deliberately canceled, or some error was encountered and exposed by the chooser. Quietly exit.
        return null;
      }
    }

    // prepare instant run session based on chosen deploy target.
    if (supportsInstantRun() && instantRunEnabled && existingSessionInfo != null) {
      PrepareSessionResult result = prepareInstantRunSession(existingSessionInfo, executor, facet, project, deviceFutures, forceColdswap);
      // returns null if we prompt user and they choose to abort the Run
      if (result == null) {
        return null;
      }
      if (deviceFutures == null && !forceColdswap) { // if user used apply changes, then set deviceFutures based on session
        deviceFutures = result.futures;
      }
      couldHaveHotswapped = result.couldHaveHotswapped;
    }

    if (deviceFutures == null || deviceFutures.get().isEmpty()) {
      throw new ExecutionException(AndroidBundle.message("deployment.target.not.found"));
    }

    InstantRunContext instantRunContext = null;
    if (supportsInstantRun() && instantRunEnabled) {
      instantRunContext = ensureGradleSupport(executor, env, module, facet, project, existingSessionInfo, deviceFutures);
    }
    else {
      logInstantRunOffReason(instantRunEnabled);
    }

    if (isDebugging) {
      String error = canDebug(deviceFutures, facet, module.getName());
      if (error != null) {
        throw new ExecutionException(error);
      }
    }

    ProcessHandler processHandler = null;
    if (existingSessionInfo != null && existingSessionInfo.getExecutorId().equals(executor.getId())) {
      processHandler = existingSessionInfo.getProcessHandler();
    }

    // Store the chosen target on the execution environment so before-run tasks can access it.
    env.putCopyableUserData(AndroidRunConfigContext.KEY,
                            createAndroidRunConfigContext(executor, forceColdswap, existingSessionInfo, couldHaveHotswapped, deviceFutures));
    // Save the instant run context so that before-run task can access it
    env.putCopyableUserData(InstantRunContext.KEY, instantRunContext);

    ApplicationIdProvider applicationIdProvider = getApplicationIdProvider(facet);

    LaunchTasksProviderFactory providerFactory =
      createLaunchTasksProviderFactory(env, facet, deviceFutures,
                                       applicationIdProvider, instantRunContext, processHandler, isDebugging);

    InstantRunStatsService.get(project).notifyBuildStarted();
    return new AndroidRunState(env, getName(), module, applicationIdProvider, getConsoleProvider(), deviceFutures, providerFactory,
                               processHandler);
  }

  /**
   * Checks Instant Run is supported based on gradle version and the chosen deploy target.
   * Prompts user to install required platform if not installed.
   */
  @Nullable
  private InstantRunContext ensureGradleSupport(@NotNull Executor executor,
                                                @NotNull ExecutionEnvironment env,
                                                @NotNull Module module,
                                                @NotNull AndroidFacet facet,
                                                @NotNull Project project,
                                                @Nullable AndroidSessionInfo info,
                                                @NotNull DeviceFutures deviceFutures) {
    InstantRunGradleSupport gradleSupport = canInstantRun(module, deviceFutures.getDevices());
    if (gradleSupport == TARGET_PLATFORM_NOT_INSTALLED) {
      if(promptInstallTargetPlatform(project, deviceFutures)) {
        gradleSupport = SUPPORTED;
      }
    }

    if (gradleSupport == SUPPORTED) {
      InstantRunUtils.setInstantRunEnabled(env, true);
      return InstantRunGradleUtils.createGradleProjectContext(facet);
    }
    else {
      notifyInstantRunDisabled(executor, env, info, gradleSupport);
    }
    return null;
  }

  @NotNull
  private LaunchTasksProviderFactory createLaunchTasksProviderFactory(@NotNull ExecutionEnvironment env,
                                                                      @NotNull AndroidFacet facet,
                                                                      @NotNull DeviceFutures deviceFutures,
                                                                      @NotNull ApplicationIdProvider applicationIdProvider,
                                                                      @Nullable InstantRunContext instantRunContext,
                                                                      @Nullable ProcessHandler processHandler, boolean isDebugging) {
    LaunchOptions launchOptions = getLaunchOptions()
      .setDebug(isDebugging)
      .build();
    return new AndroidLaunchTasksProviderFactory(this, env, facet, applicationIdProvider, getApkProvider(facet, applicationIdProvider),
                                                 deviceFutures, launchOptions,
                                                 processHandler, instantRunContext);
  }

  @NotNull
  private AndroidRunConfigContext createAndroidRunConfigContext(@NotNull Executor executor,
                                                                boolean forceColdswap,
                                                                @Nullable AndroidSessionInfo existingSessionInfo,
                                                                boolean couldHaveHotswapped,
                                                                @NotNull DeviceFutures deviceFutures) {
    AndroidRunConfigContext runConfigContext = new AndroidRunConfigContext();
    runConfigContext.setTargetDevices(deviceFutures);
    runConfigContext.setSameExecutorAsPreviousSession(isSameExecutorAsPreviousSession(executor, existingSessionInfo));
    runConfigContext.setForceColdSwap(forceColdswap, couldHaveHotswapped);
    return runConfigContext;
  }

  /**
   * There is an existing AndroidSessionInfo. Determines the deviceFutures based on previous session
   * and prepares the session for Instant Run.
   * @return a data class that holds the deviceFutures and boolean for if user could have
   * used hotswap button.
   */
  @VisibleForTesting
  @Nullable
  PrepareSessionResult prepareInstantRunSession(@NotNull final AndroidSessionInfo info,
                                                @NotNull final Executor executor,
                                                @NotNull final AndroidFacet facet,
                                                @NotNull final Project project,
                                                @Nullable final DeviceFutures chosenDeviceFutues,
                                                final boolean forceColdswap) {
    // Detect devices to fast deploy to from existing session
    final DeviceFutures sessionDeviceFutures = getFastDeployDevices(executor, AndroidModuleModel.get(facet), info);;
    boolean couldHaveHotswapped = false;

    if (sessionDeviceFutures == null && !forceColdswap) {
      // If we should not be fast deploying, but there is an existing session, then terminate those sessions. Otherwise, we might end up
      // with 2 active sessions of the same launch, especially if we first think we can do a fast deploy, then end up doing a full launch
      if (!promptAndKillSession(executor, project, info)) {
        return null;
      }
    }
    else if (sessionDeviceFutures != null && sessionDeviceFutures.allMatch(chosenDeviceFutues)) { // kill if forceColdswap to same device
      // the user could have invoked the hotswap action in this scenario, but they chose to force a coldswap (by pressing run)
      couldHaveHotswapped = true;

      // forcibly kill app in case of run action (which forces a cold swap)
      // normally, installing the apk will force kill the app, but we need to forcibly kill it in the case that there were no changes
      killSession(info);
    }

    return new PrepareSessionResult(sessionDeviceFutures, couldHaveHotswapped);
  }

  private void notifyInstantRunDisabled(@NotNull final Executor executor,
                                        @NotNull final ExecutionEnvironment env,
                                        @Nullable final AndroidSessionInfo info,
                                        @NotNull final InstantRunGradleSupport gradleSupport) {
    InstantRunManager.LOG.warn("Instant Run enabled, but not doing an instant run build since: " + gradleSupport);
    // IR is disabled, we only want to display IR notification on start of session to avoid spamming user on each run.
    if (!isSameExecutorAsPreviousSession(executor, info)) {
      String notificationText = gradleSupport.getUserNotification();
      if (notificationText != null) {
        InstantRunNotificationTask.showNotification(env.getProject(), null, notificationText);
      }
    }
  }

  /**
   * Request user ot install the target platform so Instant Run can be used.
   * Returns true if it is installed, false otherwise.
   */
  private boolean promptInstallTargetPlatform(@NotNull final Project project,
                                              @NotNull final DeviceFutures deviceFutures) {
    AndroidVersion version = deviceFutures.getDevices().get(0).getVersion();
    String message = AndroidBundle.message("instant.run.quickfix.missing.platform", SdkVersionInfo.getVersionWithCodename(version));
    int result = Messages.showYesNoDialog(project,
                                          message,
                                          "Instant Run",
                                          "Install and Continue", // yes button
                                          "Proceed without Instant Run", // no button
                                          Messages.getQuestionIcon());
    if (result == Messages.OK) { // if ok, install platform and continue with instant run
      ModelWizardDialog dialog =
        SdkQuickfixUtils.createDialogForPaths(project, ImmutableList.of(DetailsTypes.getPlatformPath(version)));
      if (dialog == null) {
        LOG.warn("Unable to get quick fix wizard to install missing platform required for instant run.");
      }
      else if (dialog.showAndGet()) {
        return true;
      }
    }
    return false;
  }

  private void logInstantRunOffReason(boolean instantRunEnabled) {
    String msg = "Not using instant run for this launch: ";
    if (instantRunEnabled) {
      msg += getType().getDisplayName() + " does not support instant run";
    }
    else {
      msg += "instant run is disabled";
    }
    InstantRunManager.LOG.info(msg);
  }


  private boolean isSameExecutorAsPreviousSession(@NotNull Executor executor, @Nullable AndroidSessionInfo info) {
    return info != null && executor.getId().equals(info.getExecutorId());
  }

  private static void killSession(@NotNull AndroidSessionInfo info) {
    info.getProcessHandler().destroyProcess();
  }

  /**
   * Retrieves DeviceFutures based on the AndroidSessionInfo for Instant Run.
   */
  @VisibleForTesting
  @Nullable
  protected static DeviceFutures getFastDeployDevices(@NotNull Executor executor,
                                                      @Nullable AndroidModuleModel model,
                                                      @NotNull AndroidSessionInfo info) {

    if (!info.getExecutorId().equals(executor.getId())) {
      String msg =
        String.format("Cannot Instant Run since old executor (%1$s) doesn't match current executor (%2$s)", info.getExecutorId(),
                      executor.getId());
      InstantRunManager.LOG.info(msg);
      return null;
    }

    List<IDevice> devices = info.getDevices();
    if (devices.isEmpty()) {
      InstantRunManager.LOG.info("Cannot Instant Run since we could not locate the devices from the existing launch session");
      return null;
    }

    if (devices.size() > 1) {
      InstantRunManager.LOG.info("Last run was on > 1 device, not reusing devices and prompting again");
      return null;
    }

    AndroidVersion version = devices.get(0).getVersion();
    InstantRunGradleSupport status = InstantRunGradleUtils.getIrSupportStatus(model, version);
    if (status != SUPPORTED) {
      InstantRunManager.LOG.info("Cannot Instant Run: " + status);
      return null;
    }

    return DeviceFutures.forDevices(devices);
  }

  private static String canDebug(@NotNull DeviceFutures deviceFutures, @NotNull AndroidFacet facet, @NotNull String moduleName) {
    // If we are debugging on a device, then the app needs to be debuggable
    for (ListenableFuture<IDevice> future : deviceFutures.get()) {
      if (!future.isDone()) {
        // this is an emulator, and we assume that all emulators are debuggable
        continue;
      }

      IDevice device = Futures.getUnchecked(future);
      if (!LaunchUtils.canDebugAppOnDevice(facet, device)) {
        return AndroidBundle.message("android.cannot.debug.noDebugPermissions", moduleName, device.getName());
      }
    }

    return null;
  }

  @Nullable
  private DeployTarget getDeployTarget(@NotNull Executor executor,
                                       @NotNull ExecutionEnvironment env,
                                       boolean debug,
                                       @NotNull AndroidFacet facet) {
    DeployTargetProvider currentTargetProvider = getDeployTargetContext().getCurrentDeployTargetProvider();

    DeployTarget deployTarget;
    if (currentTargetProvider.requiresRuntimePrompt()) {
      deployTarget =
        currentTargetProvider.showPrompt(
          executor,
          env,
          facet,
          getDeviceCount(debug),
          myAndroidTests,
          getDeployTargetContext().getDeployTargetStates(),
          getUniqueID(),
          LaunchCompatibilityCheckerImpl.create(facet)
        );
      if (deployTarget == null) {
        return null;
      }
    }
    else {
      deployTarget = currentTargetProvider.getDeployTarget();
    }

    return deployTarget;
  }

  /**
   * Prompts user to kill the existing android session on the device
   * @return true if session is killed, false if user choose to not kill session.
   */
  private boolean promptAndKillSession(@NotNull Executor executor, @NotNull Project project, @NotNull AndroidSessionInfo info) {
    String previousExecutorId = info.getExecutorId();
    String currentExecutorId = executor.getId();

    if (ourKillLaunchOption.isToBeShown()) {
      String msg, noText;
      if (previousExecutorId.equals(currentExecutorId)) {
        msg = "Restart App?\nThe app is already running. Would you like to kill it and restart the session?";
        noText = "Cancel";
      }
      else {
        String previousExecutorActionName = info.getExecutorActionName();
        String currentExecutorActionName = executor.getActionName();
        msg = String
          .format("To switch from %1$s to %2$s, the app has to restart. Continue?", previousExecutorActionName, currentExecutorActionName);
        noText = "Cancel " + currentExecutorActionName;
      }

      String title = "Launching " + getName();
      String yesText = "Restart " + getName();
      if (Messages.NO ==
          Messages.showYesNoDialog(project, msg, title, yesText, noText, AllIcons.General.QuestionDialog, ourKillLaunchOption)) {
        return false;
      }
    }

    LOG.info("Disconnecting existing session of the same launch configuration");
    killSession(info);
    return true;
  }

  @NotNull
  protected ApplicationIdProvider getApplicationIdProvider(@NotNull AndroidFacet facet) {
    if (facet.getAndroidModel() != null && facet.getAndroidModel() instanceof AndroidModuleModel) {
      return new GradleApplicationIdProvider(facet, myOutputProvider);
    }
    return new NonGradleApplicationIdProvider(facet);
  }

  @NotNull
  protected abstract ApkProvider getApkProvider(@NotNull AndroidFacet facet, @NotNull ApplicationIdProvider applicationIdProvider);

  @NotNull
  protected abstract ConsoleProvider getConsoleProvider();

  @Nullable
  protected abstract LaunchTask getApplicationLaunchTask(@NotNull ApplicationIdProvider applicationIdProvider,
                                                         @NotNull AndroidFacet facet,
                                                         boolean waitForDebugger,
                                                         @NotNull LaunchStatus launchStatus);

  public boolean monitorRemoteProcess() {
    return true;
  }

  @NotNull
  public final DeviceCount getDeviceCount(boolean debug) {
    return DeviceCount.fromBoolean(supportMultipleDevices() && !debug);
  }

  /**
   * @return true iff this run configuration supports deploying to multiple devices.
   */
  protected abstract boolean supportMultipleDevices();

  /**
   * @return true iff this run configuration supports instant run.
   */
  public boolean supportsInstantRun() {
    return false;
  }

  // Overridden in subclasses that allow customization of deployment user id
  public int getUserIdFromAmParameters() {
    return MultiUserUtils.PRIMARY_USERID;
  }

  @VisibleForTesting
  @NotNull
  InstantRunGradleSupport canInstantRun(@NotNull Module module,
                                        @NotNull List<AndroidDevice> targetDevices) {
    if (targetDevices.size() != 1) {
      return CANNOT_BUILD_FOR_MULTIPLE_DEVICES;
    }

    AndroidDevice device = targetDevices.get(0);
    AndroidVersion version = device.getVersion();
    if (!InstantRunManager.isInstantRunCapableDeviceVersion(version)) {
      return API_TOO_LOW_FOR_INSTANT_RUN;
    }

    IDevice targetDevice = MakeBeforeRunTaskProvider.getLaunchedDevice(device);
    if (targetDevice != null) {
      if (MultiUserUtils.hasMultipleUsers(targetDevice, 200, TimeUnit.MILLISECONDS, false)) {
        if (getUserIdFromAmParameters() != MultiUserUtils.PRIMARY_USERID || // run config explicitly specifies launching as a different user
            !MultiUserUtils.isCurrentUserThePrimaryUser(targetDevice, 200, TimeUnit.MILLISECONDS,
                                                        true)) { // activity manager says current user is not primary
          return CANNOT_DEPLOY_FOR_SECONDARY_USER;
        }
      }
    }

    InstantRunGradleSupport irSupportStatus =
      InstantRunGradleUtils.getIrSupportStatus(InstantRunGradleUtils.getAppModel(module), version);
    if (irSupportStatus != SUPPORTED) {
      return irSupportStatus;
    }

    if (!InstantRunGradleUtils.appHasCode(AndroidFacet.getInstance(module))) {
      return HAS_CODE_FALSE;
    }

    // Gradle will instrument against the runtime android.jar (see commit 353f46cbc7363e3fca44c53a6dc0b4d17347a6ac).
    // This means that the SDK platform corresponding to the device needs to be installed, otherwise the build will fail.
    // We do this as the last check because it is actually possible to recover from this failure. In the future, maybe issues
    // that have fixes will have to be handled in a more generic way.
    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    if (platform == null) {
      return SUPPORTED;
    }

    IAndroidTarget[] targets = platform.getSdkData().getTargets();
    for (int i = targets.length - 1; i >= 0; i--) {
      if (!targets[i].isPlatform()) {
        continue;
      }

      if (targets[i].getVersion().equals(version)) {
        return SUPPORTED;
      }
    }

    return TARGET_PLATFORM_NOT_INSTALLED;
  }

  public void setOutputModel(@NotNull PostBuildModel outputModel) {
    myOutputProvider.setOutputModel(outputModel);
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    readModule(element);
    DefaultJDOMExternalizer.readExternal(this, element);

    myDeployTargetContext.readExternal(element);
    myAndroidDebuggerContext.readExternal(element);

    Element profilersElement = element.getChild(PROFILERS_ELEMENT_NAME);
    if (profilersElement != null) {
      myProfilerState.readExternal(profilersElement);
    }
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeModule(element);
    DefaultJDOMExternalizer.writeExternal(this, element);

    myDeployTargetContext.writeExternal(element);
    myAndroidDebuggerContext.writeExternal(element);

    Element profilersElement = new Element(PROFILERS_ELEMENT_NAME);
    element.addContent(profilersElement);
    myProfilerState.writeExternal(profilersElement);
  }

  public boolean isNativeLaunch() {
    AndroidDebugger<?> androidDebugger = myAndroidDebuggerContext.getAndroidDebugger();
    if (androidDebugger == null) {
      return false;
    }
    return !androidDebugger.getId().equals(AndroidJavaDebugger.ID);
  }

  @NotNull
  public DeployTargetContext getDeployTargetContext() {
    return myDeployTargetContext;
  }

  @NotNull
  public AndroidDebuggerContext getAndroidDebuggerContext() {
    return myAndroidDebuggerContext;
  }

  /**
   * Returns the current {@link ProfilerState} for this configuration.
   */
  @NotNull
  public ProfilerState getProfilerState() {
    return myProfilerState;
  }

  private static class MyDoNotPromptOption implements DialogWrapper.DoNotAskOption {
    public static final String PROMPT_KEY = "android.show.prompt.kill.session";
    private boolean myShow = PropertiesComponent.getInstance().getBoolean(PROMPT_KEY, false);

    @Override
    public boolean isToBeShown() {
      return !myShow;
    }

    @Override
    public void setToBeShown(boolean toBeShown, int exitCode) {
      myShow = !toBeShown;
      PropertiesComponent.getInstance().setValue(PROMPT_KEY, myShow);
    }

    @Override
    public boolean canBeHidden() {
      return true;
    }

    @Override
    public boolean shouldSaveOptionsOnCancel() {
      return true;
    }

    @NotNull
    @Override
    public String getDoNotShowMessage() {
      return "Do not ask again";
    }
  }

  private static class DefaultPostBuildModelProvider implements PostBuildModelProvider {
    @Nullable
    @Transient
    private transient PostBuildModel myBuildOutputs = null;

    public void setOutputModel(@NotNull PostBuildModel postBuildModel) {
      myBuildOutputs = postBuildModel;
    }

    @Nullable
    @Override
    public PostBuildModel getPostBuildModel() {
      return myBuildOutputs;
    }
  }

  @VisibleForTesting
  static class PrepareSessionResult {
    DeviceFutures futures;
    boolean couldHaveHotswapped;

    public PrepareSessionResult(DeviceFutures futures, boolean couldHaveHotswapped) {
      this.futures = futures;
      this.couldHaveHotswapped = couldHaveHotswapped;
    }
  }
}
