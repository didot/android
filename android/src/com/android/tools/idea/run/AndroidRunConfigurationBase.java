// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.android.tools.idea.run;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_FEATURE;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_TEST;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerContext;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.editor.AndroidJavaDebugger;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetContext;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.LaunchUtils;
import com.android.tools.idea.stats.RunStats;
import com.android.tools.idea.stats.RunStatsService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.Futures;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.annotations.Transient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jdom.Element;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AndroidRunConfigurationBase extends ModuleBasedConfiguration<JavaRunConfigurationModule, Element>
  implements PreferGradleMake {

  private static final String GRADLE_SYNC_FAILED_ERR_MSG = "Gradle project sync failed. Please fix your project and try again.";

  /**
   * Element name used to group the {@link ProfilerState} settings
   */
  private static final String PROFILERS_ELEMENT_NAME = "Profilers";

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
    if (!facet.getConfiguration().isAppProject() && facet.getConfiguration().getProjectType() != PROJECT_TYPE_TEST) {
      if (facet.getConfiguration().isLibraryProject() || facet.getConfiguration().getProjectType() == PROJECT_TYPE_FEATURE) {
        Pair<Boolean, String> result = supportsRunningLibraryProjects(facet);
        if (!result.getFirst()) {
          errors.add(ValidationError.fatal(result.getSecond()));
        }
      }
      else {
        errors.add(ValidationError.fatal(AndroidBundle.message("run.error.apk.not.valid")));
      }
    }
    if (facet.getAndroidPlatform() == null) {
      errors.add(ValidationError.fatal(AndroidBundle.message("select.platform.error")));
    }
    if (Manifest.getMainManifest(facet) == null && facet.getConfiguration().getProjectType() != PROJECT_TYPE_INSTANTAPP) {
      errors.add(ValidationError.fatal(AndroidBundle.message("android.manifest.not.found.error")));
    }
    errors.addAll(getDeployTargetContext().getCurrentDeployTargetState().validate(facet));

    errors.addAll(getApkProvider(facet, getApplicationIdProvider(facet), new ArrayList<>()).validate());

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
    return getDeployTargetContext().getApplicableDeployTargetProviders(myAndroidTests);
  }

  protected void validateBeforeRun(@NotNull Executor executor) throws ExecutionException {
    List<ValidationError> errors = validate(executor);
    ValidationUtil.promptAndQuickFixErrors(getProject(), errors);
  }

  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    RunStats stats = RunStatsService.get(getProject()).create();
    try {
      stats.start();
      RunProfileState state = doGetState(executor, env, stats);
      stats.markStateCreated();
      return state;
    }
    catch (Throwable t) {
      stats.abort();
      throw t;
    }
  }

  @Nullable
  public RunProfileState doGetState(@NotNull Executor executor,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull RunStats stats) throws ExecutionException {
    validateBeforeRun(executor);

    final Module module = getConfigurationModule().getModule();
    assert module != null : "Enforced by fatal validation check in checkConfiguration.";
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null : "Enforced by fatal validation check in checkConfiguration.";

    stats.setDebuggable(LaunchUtils.canDebugApp(facet));
    stats.setExecutor(executor.getId());

    updateExtraRunStats(stats);

    final boolean isDebugging = executor instanceof DefaultDebugExecutor;
    boolean userSelectedDeployTarget = getDeployTargetContext().getCurrentDeployTargetProvider().requiresRuntimePrompt();
    stats.setUserSelectedTarget(userSelectedDeployTarget);

    // Figure out deploy target, prompt user if needed (ignore completely if user chose to hotswap).
    DeployTarget deployTarget = getDeployTarget(executor, env, isDebugging, facet);
    if (deployTarget == null) { // if user doesn't select a deploy target from the dialog
      return null;
    }

    DeployTargetState deployTargetState = getDeployTargetContext().getCurrentDeployTargetState();
    if (deployTarget.hasCustomRunProfileState(executor)) {
      return deployTarget.getRunProfileState(executor, env, deployTargetState);
    }

    DeviceFutures deviceFutures = deployTarget.getDevices(deployTargetState, facet, getDeviceCount(isDebugging), isDebugging, hashCode());
    if (deviceFutures == null) {
      // The user deliberately canceled, or some error was encountered and exposed by the chooser. Quietly exit.
      return null;
    }

    // Record stat if we launched a device.
    stats.setLaunchedDevices(deviceFutures.getDevices().stream().anyMatch(device -> device instanceof LaunchableAndroidDevice));

    if (deviceFutures.get().isEmpty()) {
      throw new ExecutionException(AndroidBundle.message("deployment.target.not.found"));
    }

    if (isDebugging) {
      String error = canDebug(deviceFutures, facet, module.getName());
      if (error != null) {
        throw new ExecutionException(error);
      }
    }

    // Store the chosen target on the execution environment so before-run tasks can access it.
    env.putCopyableUserData(DeviceFutures.KEY, deviceFutures);

    // Save the stats so that before-run task can access it
    env.putUserData(RunStats.KEY, stats);

    ApplicationIdProvider applicationIdProvider = getApplicationIdProvider(facet);

    LaunchOptions.Builder launchOptions = getLaunchOptions()
      .setDebug(isDebugging);

    if (executor instanceof LaunchOptionsProvider) {
      launchOptions.addExtraOptions(((LaunchOptionsProvider)executor).getLaunchOptions());
    }

    ApkProvider apkProvider = getApkProvider(facet, applicationIdProvider, deviceFutures.getDevices());
    AndroidLaunchTasksProvider launchTasksProvider =
      new AndroidLaunchTasksProvider(this, env, facet, applicationIdProvider, apkProvider, launchOptions.build());

    return new AndroidRunState(env, getName(), module, applicationIdProvider, getConsoleProvider(), deviceFutures, launchTasksProvider);
  }

  private static String canDebug(@NotNull DeviceFutures deviceFutures, @NotNull AndroidFacet facet, @NotNull String moduleName) {
    // If we are debugging on a device, then the app needs to be debuggable
    for (AndroidDevice androidDevice : deviceFutures.getDevices()) {
      if (!androidDevice.isDebuggable() && !LaunchUtils.canDebugApp(facet)) {
        String deviceName;
        if (!androidDevice.getLaunchedDevice().isDone()) {
          deviceName = androidDevice.getName();
        }
        else {
          @SuppressWarnings("UnstableApiUsage")
          IDevice device = Futures.getUnchecked(androidDevice.getLaunchedDevice());
          deviceName = device.getName();
        }
        return AndroidBundle.message("android.cannot.debug.noDebugPermissions", moduleName, deviceName);
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
          hashCode(),
          LaunchCompatibilityCheckerImpl.create(facet, env, this)
        );
      if (deployTarget == null) {
        return null;
      }
    }
    else {
      deployTarget = currentTargetProvider.getDeployTarget(getProject());
    }

    return deployTarget;
  }

  @Nullable
  public ApplicationIdProvider getApplicationIdProvider() {
    final Module module = getConfigurationModule().getModule();
    if (module == null) {
      return null;
    }

    final Project project = module.getProject();
    if (AndroidProjectInfo.getInstance(project).requiredAndroidModelMissing()) {
      return null;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }

    if (facet.isDisposed()) {
      Logger.getInstance(AndroidRunConfigurationBase.class).warn("Can't get application ID: Facet already disposed");
      return null;
    }

    return getApplicationIdProvider(facet);
  }

  @NotNull
  public ApplicationIdProvider getApplicationIdProvider(@NotNull AndroidFacet facet) {
    if (facet.getModel() != null && facet.getModel() instanceof AndroidModuleModel) {
      return new GradleApplicationIdProvider(facet, myOutputProvider);
    }
    return new NonGradleApplicationIdProvider(facet);
  }

  @NotNull
  protected abstract ApkProvider getApkProvider(@NotNull AndroidFacet facet,
                                                @NotNull ApplicationIdProvider applicationIdProvider,
                                                @NotNull List<AndroidDevice> targetDevices);

  @NotNull
  protected abstract ConsoleProvider getConsoleProvider();

  @Nullable
  protected abstract LaunchTask getApplicationLaunchTask(@NotNull ApplicationIdProvider applicationIdProvider,
                                                         @NotNull AndroidFacet facet,
                                                         @NotNull String contributorsAmStartOptions,
                                                         boolean waitForDebugger,
                                                         @NotNull LaunchStatus launchStatus);

  @NotNull
  protected ApkProvider createGradleApkProvider(@NotNull AndroidFacet facet,
                                                @NotNull ApplicationIdProvider applicationIdProvider,
                                                boolean test,
                                                @NotNull List<AndroidDevice> targetDevices) {
    Computable<GradleApkProvider.OutputKind> outputKindProvider = () -> {
      if (DynamicAppUtils.useSelectApksFromBundleBuilder(facet.getModule(), this, targetDevices)) {
        return GradleApkProvider.OutputKind.AppBundleOutputModel;
      }
      else {
        return GradleApkProvider.OutputKind.Default;
      }
    };
    return new GradleApkProvider(facet, applicationIdProvider, myOutputProvider, test, outputKindProvider);
  }

  @NotNull
  public final DeviceCount getDeviceCount(boolean debug) {
    return DeviceCount.fromBoolean(supportMultipleDevices() && !debug);
  }

  /**
   * @return true iff this run configuration supports deploying to multiple devices.
   */
  protected abstract boolean supportMultipleDevices();

  public void updateExtraRunStats(RunStats runStats) {

  }

  public void setOutputModel(@NotNull PostBuildModel outputModel) {
    myOutputProvider.setOutputModel(outputModel);
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
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
}
