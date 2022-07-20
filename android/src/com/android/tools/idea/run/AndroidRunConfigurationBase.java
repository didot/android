// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.run.configuration.user.settings.AndroidConfigurationExecutionSettings;
import com.android.tools.idea.run.editor.*;
import com.android.tools.idea.run.tasks.AppLaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.LaunchUtils;
import com.android.tools.idea.run.util.SwapInfo;
import com.android.tools.idea.stats.RunStats;
import com.android.tools.idea.stats.RunStatsService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.Futures;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.SourceProviderManager;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static com.android.AndroidProjectTypes.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Base {@link com.intellij.execution.configurations.RunConfiguration} for all Android run configs.
 * <p>
 * This class serves as the base model of Android build + run execution data.
 * <p>
 * Note this class inherits from {@link RunConfigurationWithSuppressedDefaultDebugAction} so as to
 * prevent the {@link com.intellij.debugger.impl.GenericDebuggerRunner} from recognizing this run
 * config as runnable. This allows Studio to disable the debug button when this type of run config
 * is selected, but the Debug action doesn't support running on multiple devices.
 */
public abstract class AndroidRunConfigurationBase extends ModuleBasedConfiguration<AndroidRunConfigurationModule, Element>
  implements PreferGradleMake, RunConfigurationWithSuppressedDefaultRunAction, RunConfigurationWithSuppressedDefaultDebugAction {

  /**
   * Element name used to group the {@link ProfilerState} settings
   */
  private static final String PROFILERS_ELEMENT_NAME = "Profilers";

  public boolean CLEAR_LOGCAT = false;
  public boolean SHOW_LOGCAT_AUTOMATICALLY = false;
  public boolean INSPECTION_WITHOUT_ACTIVITY_RESTART = false; // set global attributes at launch time

  private final ProfilerState myProfilerState;

  private final DeployTargetContext myDeployTargetContext = new DeployTargetContext();
  private final AndroidDebuggerContext myAndroidDebuggerContext = new AndroidDebuggerContext(AndroidJavaDebugger.ID);
  private final boolean myIsTestConfiguration;

  public AndroidRunConfigurationBase(Project project, ConfigurationFactory factory, boolean isTestConfiguration) {
    super(new AndroidRunConfigurationModule(project, isTestConfiguration), factory);

    myIsTestConfiguration = isTestConfiguration;
    myProfilerState = new ProfilerState();
    getOptions().setAllowRunningInParallel(true);
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
    List<ValidationError> errors = new ArrayList<>();
    JavaRunConfigurationModule configurationModule = getConfigurationModule();
    try {
      configurationModule.checkForWarning();
    }
    catch (RuntimeConfigurationException e) {
      errors.add(ValidationError.fromException(e));
    }
    Module module = configurationModule.getModule();
    if (module == null) {
      // Can't proceed, and fatal error has been caught in ConfigurationModule#checkForWarnings
      return errors;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      // Can't proceed.
      return ImmutableList.of(ValidationError.fatal(AndroidBundle.message("no.facet.error", module.getName())));
    }

    int projectType = facet.getConfiguration().getProjectType();
    switch (projectType) {
      // Supported project types.
      case PROJECT_TYPE_APP:
      case PROJECT_TYPE_INSTANTAPP:
      case PROJECT_TYPE_TEST:
        break;

      // Project types that need further check for the eligibility.
      case PROJECT_TYPE_LIBRARY:
      case PROJECT_TYPE_FEATURE:
      case PROJECT_TYPE_DYNAMIC_FEATURE:
        Pair<Boolean, String> result = supportsRunningLibraryProjects(facet);
        if (!result.getFirst()) {
          errors.add(ValidationError.fatal(result.getSecond()));
        }
        break;

      // Unsupported types.
      default:
        errors.add(ValidationError.fatal(AndroidBundle.message("run.error.apk.not.valid")));
        return errors;
    }

    if (AndroidPlatform.getInstance(module) == null) {
      errors.add(ValidationError.fatal(AndroidBundle.message("select.platform.error")));
    }
    if (projectType != PROJECT_TYPE_INSTANTAPP && !isManifestValid(facet)) {
      errors.add(ValidationError.fatal(AndroidBundle.message("android.manifest.not.found.error")));
    }
    errors.addAll(getDeployTargetContext().getCurrentDeployTargetState().validate(facet));

    if (getApplicationIdProvider() == null) {
      errors.add(ValidationError.fatal(AndroidBundle.message("android.run.configuration.not.supported.applicationid", getName())));
    }

    ApkProvider apkProvider = getApkProvider();
    if (apkProvider != null) {
      errors.addAll(apkProvider.validate());
    }
    else {
      errors.add(ValidationError.fatal(AndroidBundle.message("android.run.configuration.not.supported", getName())));
    }

    errors.addAll(checkConfiguration(facet));
    AndroidDebuggerState androidDebuggerState = myAndroidDebuggerContext.getAndroidDebuggerState();
    if (androidDebuggerState != null) {
      errors.addAll(androidDebuggerState.validate(facet, executor));
    }

    errors.addAll(myProfilerState.validate());

    return errors;
  }

  private boolean isManifestValid(@NotNull AndroidFacet facet) {
    VirtualFile manifestFile = SourceProviderManager.getInstance(facet).getMainManifestFile();
    if (manifestFile == null) {
      return false;
    }
    ProgressManager.checkCanceled();
    try (InputStream stream = manifestFile.getInputStream()) {
      KXmlParser parser = new KXmlParser();
      parser.setInput(stream, UTF_8.name());
      parser.nextTag();
      return "manifest".equals(parser.getName());
    }
    catch (IOException | XmlPullParserException e) {
      return false;
    }
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
      .setClearLogcatBeforeStart(CLEAR_LOGCAT);
  }

  @Override
  public Collection<Module> getValidModules() {
    List<Module> result = new ArrayList<>();
    // Return list of holder modules with android facets. On the moment of writing (22 Sep 2021) there seem to be no usages
    // of this method. In case it changes in the future please make sure correct modules are returned here.
    for (AndroidFacet facet : ProjectSystemUtil.getAndroidFacets(getProject())) {
      result.add(facet.getModule());
    }
    return result;
  }

  public abstract @NotNull List<DeployTargetProvider> getApplicableDeployTargetProviders();

  protected void validateBeforeRun(@NotNull Executor executor) throws ExecutionException {
    List<ValidationError> errors = validate(executor);
    ValidationUtil.promptAndQuickFixErrors(getProject(), errors);
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
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

    if (StudioFlags.NEW_EXECUTION_FLOW_ENABLED.get()
        && !isTestConfiguration()
        && env.getUserData(SwapInfo.SWAP_INFO_KEY) == null
        && AndroidConfigurationExecutionSettings.getInstance().getState().getEnableNewConfigurationFlow()
    ) {
      // TODO: implement new flow
    }

    Module module = getConfigurationModule().getModule();
    assert module != null : "Enforced by fatal validation check in checkConfiguration.";
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null : "Enforced by fatal validation check in checkConfiguration.";

    stats.setDebuggable(LaunchUtils.canDebugApp(facet));
    stats.setExecutor(executor.getId());

    updateExtraRunStats(stats);

    boolean isDebugging = executor instanceof DefaultDebugExecutor;
    DeployTargetContext context = getDeployTargetContext();
    stats.setUserSelectedTarget(context.getCurrentDeployTargetProvider().requiresRuntimePrompt(facet.getModule().getProject()));

    // Figure out deploy target, prompt user if needed (ignore completely if user chose to hotswap).
    DeployTarget deployTarget = getDeployTarget(facet);
    if (deployTarget == null) { // if user doesn't select a deploy target from the dialog
      return null;
    }

    DeployTargetState deployTargetState = context.getCurrentDeployTargetState();
    if (deployTarget.hasCustomRunProfileState(executor)) {
      return deployTarget.getRunProfileState(executor, env, deployTargetState);
    }

    DeviceFutures deviceFutures = deployTarget.getDevices(facet);
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

    ApplicationIdProvider applicationIdProvider = getApplicationIdProvider();
    if (applicationIdProvider == null) {
      throw new RuntimeException("Cannot get ApplicationIdProvider");
    }

    LaunchOptions.Builder launchOptions = getLaunchOptions().setDebug(isDebugging);

    if (executor instanceof LaunchOptionsProvider) {
      launchOptions.addExtraOptions(((LaunchOptionsProvider)executor).getLaunchOptions());
    }

    ApkProvider apkProvider = getApkProvider();
    if (apkProvider == null) return null;
    LaunchTasksProvider launchTasksProvider =
      createLaunchTasksProvider(env, facet, applicationIdProvider, apkProvider, launchOptions.build());

    return new AndroidRunState(env, getName(), module, applicationIdProvider,
                               getConsoleProvider(deviceFutures.getDevices().size() > 1), deviceFutures, launchTasksProvider);
  }

  /**
   * Subclasses should override to adjust the LaunchTaskProvider
   */
  private LaunchTasksProvider createLaunchTasksProvider(@NotNull ExecutionEnvironment env,
                                                        @NotNull AndroidFacet facet,
                                                        @NotNull ApplicationIdProvider applicationIdProvider,
                                                        @NotNull ApkProvider apkProvider,
                                                        @NotNull LaunchOptions launchOptions) {
    Optional<LaunchTasksProvider> provided = LaunchTasksProvider.Provider.EP_NAME.getExtensionList().stream()
      .map(it -> it.createLaunchTasksProvider(this, env, facet, applicationIdProvider, apkProvider, launchOptions))
      .filter(Objects::nonNull)
      .findFirst();
    return provided.orElseGet(() -> new AndroidLaunchTasksProvider(this, env, facet, applicationIdProvider, apkProvider, launchOptions));
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
          IDevice device = Futures.getUnchecked(androidDevice.getLaunchedDevice());
          deviceName = device.getName();
        }
        return AndroidBundle.message("android.cannot.debug.noDebugPermissions", moduleName, deviceName);
      }
    }

    return null;
  }

  @Nullable
  private DeployTarget getDeployTarget(@NotNull AndroidFacet facet) {
    DeployTargetProvider currentTargetProvider = getDeployTargetContext().getCurrentDeployTargetProvider();
    Project project = getProject();

    return currentTargetProvider.requiresRuntimePrompt(project) ?
           currentTargetProvider.showPrompt(facet) : currentTargetProvider.getDeployTarget(project);
  }

  @Nullable
  public ApplicationIdProvider getApplicationIdProvider() {
    return ProjectSystemUtil.getProjectSystem(getProject()).getApplicationIdProvider(this);
  }

  @Nullable
  public final ApkProvider getApkProvider() {
    return ProjectSystemUtil.getProjectSystem(getProject()).getApkProvider(this);
  }

  public boolean isTestConfiguration() {
    return myIsTestConfiguration;
  }

  @NotNull
  protected abstract ConsoleProvider getConsoleProvider(boolean runOnMultipleDevices);

  @Nullable
  protected abstract AppLaunchTask getApplicationLaunchTask(@NotNull ApplicationIdProvider applicationIdProvider,
                                                            @NotNull AndroidFacet facet,
                                                            @NotNull String contributorsAmStartOptions,
                                                            boolean waitForDebugger,
                                                            @NotNull LaunchStatus launchStatus,
                                                            @NotNull ApkProvider apkProvider,
                                                            @NotNull ConsolePrinter consolePrinter,
                                                            @NotNull IDevice device);

  /**
   * @return true iff this configuration can run while out of sync with the build system.
   */
  public boolean canRunWithoutSync() {
    return false;
  }

  public void updateExtraRunStats(RunStats runStats) {

  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);

    myDeployTargetContext.readExternal(element);
    myAndroidDebuggerContext.readExternal(element);
    myAndroidDebuggerContext.setDebuggeeModuleProvider(() -> getConfigurationModule().getModule());

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

  /**
   * Returns whether this configuration can run in Android Profiler.
   */
  public boolean isProfilable() {
    return true;
  }
}
