/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.run;

import com.android.SdkConstants;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.Variant;
import com.android.ddmlib.*;
import com.android.prefs.AndroidLocation;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.ddms.DevicePanel;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.service.notification.CustomNotificationListener;
import com.android.tools.idea.gradle.service.notification.SyncProjectHyperlink;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.CommonBundle;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.ui.content.Content;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.android.compiler.artifact.AndroidArtifactUtil;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.AvdsNotSupportedException;
import org.jetbrains.android.logcat.AndroidLogcatUtil;
import org.jetbrains.android.logcat.AndroidLogcatView;
import org.jetbrains.android.logcat.AndroidToolWindowFactory;
import org.jetbrains.android.run.testing.AndroidTestRunConfiguration;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.sdk.AvdManagerLog;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;
import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

/**
 * @author coyote
 */
public class AndroidRunningState implements RunProfileState, AndroidDebugBridge.IClientChangeListener, AndroidExecutionState {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.AndroidRunningState");

  @NonNls private static final String ANDROID_TARGET_DEVICES_PROPERTY = "AndroidTargetDevices";
  private static final IDevice[] EMPTY_DEVICE_ARRAY = new IDevice[0];

  public static final int WAITING_TIME = 20;

  private static final Pattern FAILURE = Pattern.compile("Failure\\s+\\[(.*)\\]");
  private static final Pattern TYPED_ERROR = Pattern.compile("Error\\s+[Tt]ype\\s+(\\d+).*");
  private static final String ERROR_PREFIX = "Error";

  static final int NO_ERROR = -2;
  private static final int UNTYPED_ERROR = -1;

  /** Default suffix for test packages (as added by Android Gradle plugin) */
  private static final String DEFAULT_TEST_PACKAGE_SUFFIX = ".test";

  private String myPackageName;

  // In non gradle projects, test packages belong to a separate module, so their name is equal to
  // the package name of the module. i.e. myPackageName = myTestPackageName.
  // In gradle projects, tests are part of the same module, and their package name is either specified
  // in build.gradle or generated automatically by Android Gradle plugin
  private String myTestPackageName;

  private String myTargetPackageName;
  private final AndroidFacet myFacet;
  private final String myCommandLine;
  private final AndroidApplicationLauncher myApplicationLauncher;
  private Map<AndroidFacet, String> myAdditionalFacet2PackageName;
  private final AndroidRunConfigurationBase myConfiguration;

  private final Object myDebugLock = new Object();

  @NotNull
  private volatile IDevice[] myTargetDevices = EMPTY_DEVICE_ARRAY;

  private volatile String myAvdName;
  private volatile boolean myDebugMode;
  private volatile boolean myOpenLogcatAutomatically;

  private volatile DebugLauncher myDebugLauncher;

  private final ExecutionEnvironment myEnv;

  private volatile boolean myStopped;
  private volatile ProcessHandler myProcessHandler;
  private final Object myLock = new Object();

  private volatile boolean myDeploy = true;
  private volatile String myArtifactName;

  private volatile boolean myApplicationDeployed = false;

  private ConsoleView myConsole;
  private Runnable myRestarter;
  private TargetChooser myTargetChooser;
  private final boolean mySupportMultipleDevices;
  private final boolean myClearLogcatBeforeStart;
  private final List<AndroidRunningStateListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final boolean myNonDebuggableOnDevice;

  public void setDebugMode(boolean debugMode) {
    myDebugMode = debugMode;
  }

  public void setDebugLauncher(@NotNull DebugLauncher debugLauncher) {
    myDebugLauncher = debugLauncher;
  }

  public boolean isDebugMode() {
    return myDebugMode;
  }

  public void setRestarter(@NotNull Runnable restarter) {
    myRestarter = restarter;
  }

  private static void runInDispatchedThread(@NotNull Runnable r, boolean blocking) {
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      r.run();
    }
    else if (blocking) {
      application.invokeAndWait(r, ModalityState.defaultModalityState());
    }
    else {
      application.invokeLater(r);
    }
  }

  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    myProcessHandler = new DefaultDebugProcessHandler();
    AndroidProcessText.attach(myProcessHandler);
    ConsoleView console;
    if (isDebugMode()) {
      Project project = myFacet.getModule().getProject();
      final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
      console = builder.getConsole();
      if (console != null) {
        console.attachToProcess(myProcessHandler);
      }
    }
    else {
      console = myConfiguration.attachConsole(this, executor);
    }
    myConsole = console;

    if (myTargetChooser instanceof ManualTargetChooser) {
      if (myConfiguration.USE_LAST_SELECTED_DEVICE) {
        Set<String> devicesUsedInLastLaunch = myConfiguration.getDevicesUsedInLastLaunch();

        if (devicesUsedInLastLaunch != null) {
          myTargetDevices = getDevicesStillOnline(devicesUsedInLastLaunch);
        }

        if (myTargetDevices.length > 1 && !mySupportMultipleDevices) {
          myTargetDevices = EMPTY_DEVICE_ARRAY;
        }
      }

      if (myTargetDevices.length == 0) {
        final ExtendedDeviceChooserDialog chooser = new ExtendedDeviceChooserDialog(myFacet, mySupportMultipleDevices,
                                                                                    true, myConfiguration.USE_LAST_SELECTED_DEVICE);
        chooser.show();
        if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
          return null;
        }

        if (chooser.isToLaunchEmulator()) {
          final String selectedAvd = chooser.getSelectedAvd();
          if (selectedAvd == null) {
            return null;
          }
          myTargetChooser = new EmulatorTargetChooser(selectedAvd);
          myAvdName = selectedAvd;
        }
        else {
          final IDevice[] selectedDevices = chooser.getSelectedDevices();
          if (selectedDevices.length == 0) {
            return null;
          }
          myTargetDevices = selectedDevices;

          if (chooser.useSameDevicesAgain()) {
            myConfiguration.USE_LAST_SELECTED_DEVICE = true;
            myConfiguration.setDevicesUsedInLaunch(getDeviceNames(selectedDevices));
          }
        }
      }
    }

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        start(true);
      }
    });
    //noinspection ConstantConditions
    return new DefaultExecutionResult(console, myProcessHandler);
  }

  private static Set<String> getDeviceNames(@NotNull IDevice[] selectedDevices) {
    Set<String> s = new HashSet<String>(selectedDevices.length);

    for (IDevice d : selectedDevices) {
      String name = d.getName();
      if (name != null) {
        s.add(name);
      }
    }

    return s;
  }

  private IDevice[] getDevicesStillOnline(@NotNull Set<String> devicesUsedInLastLaunch) {
    AndroidDebugBridge debugBridge = myFacet.getDebugBridge();
    if (debugBridge == null) {
      return EMPTY_DEVICE_ARRAY;
    }

    IDevice[] devices = debugBridge.getDevices();
    List<IDevice> onlineDevices = new ArrayList<IDevice>(devices.length);

    for (IDevice d : devices) {
      if (devicesUsedInLastLaunch.contains(d.getName())) {
        onlineDevices.add(d);
      }
    }

    return onlineDevices.toArray(new IDevice[onlineDevices.size()]);
  }

  @Nullable
  private String computePackageName(@NotNull final AndroidFacet facet) {
    if (facet.getProperties().USE_CUSTOM_MANIFEST_PACKAGE) {
      return facet.getProperties().CUSTOM_MANIFEST_PACKAGE;
    }
    else {
      File manifestCopy = null;
      final VirtualFile manifestVFile;
      final String manifestLocalPath;

      try {
        if (facet.getProperties().USE_CUSTOM_COMPILER_MANIFEST) {
          final Pair<File,String> pair = AndroidRunConfigurationBase.getCopyOfCompilerManifestFile(facet, getProcessHandler());
          manifestCopy = pair != null ? pair.getFirst() : null;
          manifestVFile = manifestCopy != null ? LocalFileSystem.getInstance().findFileByIoFile(manifestCopy) : null;
          manifestLocalPath = pair != null ? pair.getSecond() : null;
        }
        else {
          manifestVFile = AndroidRootUtil.getManifestFile(facet);
          manifestLocalPath = manifestVFile != null ? PathUtil.getLocalPath(manifestVFile) : null;
        }
        final Module module = facet.getModule();
        final String moduleName = module.getName();

        if (manifestVFile == null) {
          message("Cannot find " + SdkConstants.FN_ANDROID_MANIFEST_XML + " file for module " + moduleName, STDERR);
          return null;
        }
        manifestVFile.refresh(false, false);

        return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
          @Override
          public String compute() {
            final Manifest manifest = AndroidUtils.loadDomElement(module, manifestVFile, Manifest.class);

            if (manifest == null) {
              message("[" + moduleName + "] File " + manifestLocalPath + " is not a valid manifest file", STDERR);
              //noinspection ConstantConditions
              return null;
            }
            final GenericAttributeValue<String> packageAttrValue = manifest.getPackage();
            final String aPackage = packageAttrValue.getValue();

            if (aPackage == null || aPackage.length() == 0) {
              message("[" + moduleName + "] Main package is not specified in file " + manifestLocalPath, STDERR);
              //noinspection ConstantConditions
              return null;
            }
            return aPackage;
          }
        });
      }
      finally {
        if (manifestCopy != null) {
          FileUtil.delete(manifestCopy.getParentFile());
        }
      }
    }
  }

  private boolean fillRuntimeAndTestDependencies(@NotNull Module module,
                                                 @NotNull Map<AndroidFacet, String> module2PackageName) {
    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
        Module depModule = moduleOrderEntry.getModule();
        if (depModule != null) {
          AndroidFacet depFacet = AndroidFacet.getInstance(depModule);
          if (depFacet != null &&
              !module2PackageName.containsKey(depFacet) &&
              !depFacet.isLibraryProject()) {
            String packageName = computePackageName(depFacet);
            if (packageName == null) {
              return false;
            }
            module2PackageName.put(depFacet, packageName);
            if (!fillRuntimeAndTestDependencies(depModule, module2PackageName)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  @Override
  @NotNull
  public AndroidRunConfigurationBase getConfiguration() {
    return myConfiguration;
  }

  public ExecutionEnvironment getEnvironment() {
    return myEnv;
  }

  public boolean isStopped() {
    return myStopped;
  }

  public Object getRunningLock() {
    return myLock;
  }

  public String getPackageName() {
    return myPackageName;
  }

  public String getTestPackageName() {
    return myTestPackageName;
  }

  public Module getModule() {
    return myFacet.getModule();
  }

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  @Override
  public IDevice[] getDevices() {
    return myTargetDevices;
  }

  @Nullable
  @Override
  public ConsoleView getConsoleView() {
    return myConsole;
  }

  public class MyReceiver extends AndroidOutputReceiver {
    private int errorType = NO_ERROR;
    private String failureMessage = null;
    private final StringBuilder output = new StringBuilder();

    @Override
    protected void processNewLine(String line) {
      if (line.length() > 0) {
        Matcher failureMatcher = FAILURE.matcher(line);
        if (failureMatcher.matches()) {
          failureMessage = failureMatcher.group(1);
        }
        Matcher errorMatcher = TYPED_ERROR.matcher(line);
        if (errorMatcher.matches()) {
          errorType = Integer.parseInt(errorMatcher.group(1));
        }
        else if (line.startsWith(ERROR_PREFIX) && errorType == NO_ERROR) {
          errorType = UNTYPED_ERROR;
        }
      }
      output.append(line).append('\n');
    }

    public int getErrorType() {
      return errorType;
    }

    @Override
    public boolean isCancelled() {
      return myStopped;
    }

    public StringBuilder getOutput() {
      return output;
    }
  }

  public AndroidRunningState(@NotNull ExecutionEnvironment environment,
                             @NotNull AndroidFacet facet,
                             @Nullable TargetChooser targetChooser,
                             @NotNull String commandLine,
                             AndroidApplicationLauncher applicationLauncher,
                             boolean supportMultipleDevices,
                             boolean clearLogcatBeforeStart,
                             @NotNull AndroidRunConfigurationBase configuration,
                             boolean nonDebuggableOnDevice) throws ExecutionException {
    myFacet = facet;
    myCommandLine = commandLine;
    myConfiguration = configuration;

    myTargetChooser = targetChooser;
    mySupportMultipleDevices = supportMultipleDevices;

    myAvdName = targetChooser instanceof EmulatorTargetChooser
                ? ((EmulatorTargetChooser)targetChooser).getAvd()
                : null;
      
    myEnv = environment;
    myApplicationLauncher = applicationLauncher;
    myClearLogcatBeforeStart = clearLogcatBeforeStart;
    myNonDebuggableOnDevice = nonDebuggableOnDevice;
  }

  public void setDeploy(boolean deploy) {
    myDeploy = deploy;
  }

  public void setArtifactName(@Nullable String artifactName) {
    myArtifactName = artifactName;
  }

  public void setTargetPackageName(String targetPackageName) {
    synchronized (myDebugLock) {
      myTargetPackageName = targetPackageName;
    }
  }

  @Nullable
  private IDevice[] chooseDevicesAutomatically() {
    final List<IDevice> compatibleDevices = getAllCompatibleDevices();

    if (compatibleDevices.size() == 0) {
      return EMPTY_DEVICE_ARRAY;
    }
    else if (compatibleDevices.size() == 1) {
      return new IDevice[] {compatibleDevices.get(0)};
    }
    else {
      final IDevice[][] devicesWrapper = {null};
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          devicesWrapper[0] = chooseDevicesManually(new Condition<IDevice>() {
            @Override
            public boolean value(IDevice device) {
              return isCompatibleDevice(device) != Boolean.FALSE;
            }
          });
        }
      }, ModalityState.defaultModalityState());
      return devicesWrapper[0].length > 0 ? devicesWrapper[0] : null;
    }
  }

  @NotNull
  List<IDevice> getAllCompatibleDevices() {
    final List<IDevice> compatibleDevices = new ArrayList<IDevice>();
    final AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();

    if (bridge != null) {
      IDevice[] devices = bridge.getDevices();

      for (IDevice device : devices) {
        if (isCompatibleDevice(device) != Boolean.FALSE) {
          compatibleDevices.add(device);
        }
      }
    }
    return compatibleDevices;
  }

  private void chooseAvd() {
    IAndroidTarget buildTarget = myFacet.getConfiguration().getAndroidTarget();
    assert buildTarget != null;
    AvdInfo[] avds = myFacet.getValidCompatibleAvds();
    if (avds.length > 0) {
      myAvdName = avds[0].getName();
    }
    else {
      final Project project = myFacet.getModule().getProject();
      AvdManager manager = null;
      try {
        manager = myFacet.getAvdManager(new AvdManagerLog() {
          @Override
          public void error(Throwable t, String errorFormat, Object... args) {
            super.error(t, errorFormat, args);

            if (errorFormat != null) {
              final String msg = String.format(errorFormat, args);
              message(msg, STDERR);
            }
          }
        });
      }
      catch (AvdsNotSupportedException e) {
        // can't be
        LOG.error(e);
      }
      catch (final AndroidLocation.AndroidLocationException e) {
        LOG.info(e);
        runInDispatchedThread(new Runnable() {
          @Override
          public void run() {
            Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
          }
        }, false);
        return;
      }
      final AvdManager finalManager = manager;
      runInDispatchedThread(new Runnable() {
        @Override
        public void run() {
          CreateAvdDialog dialog = new CreateAvdDialog(project, myFacet, finalManager, true, true);
          dialog.show();
          if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
            AvdInfo createdAvd = dialog.getCreatedAvd();
            if (createdAvd != null) {
              myAvdName = createdAvd.getName();
            }
          }
        }
      }, true);
    }
  }

  void start(boolean chooseTargetDevice) {
    LocalFileSystem.getInstance().refresh(false);

    myPackageName = computePackageName(myFacet);
    if (myPackageName == null) {
      getProcessHandler().destroyProcess();
      return;
    }

    myPackageName = getPackageNameFromGradle(myPackageName, myFacet);
    assert myPackageName != null;
    myTestPackageName = computeTestPackageName(myFacet, myPackageName);

    setTargetPackageName(myPackageName);
    final HashMap<AndroidFacet, String> depFacet2PackageName = new HashMap<AndroidFacet, String>();

    if (!fillRuntimeAndTestDependencies(getModule(), depFacet2PackageName)) {
      getProcessHandler().destroyProcess();
      return;
    }
    myAdditionalFacet2PackageName = depFacet2PackageName;

    if (chooseTargetDevice) {
      message("Waiting for device.", STDOUT);

      if (myTargetDevices.length == 0 && !chooseOrLaunchDevice()) {
        getProcessHandler().destroyProcess();
        fireExecutionFailed();
        return;
      }
    }
    doStart();
  }

  private void doStart() {
    if (myDebugMode) {
      AndroidDebugBridge.addClientChangeListener(this);
    }
    final MyDeviceChangeListener[] deviceListener = {null};
    getProcessHandler().addProcessListener(new ProcessAdapter() {
      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        if (myDebugMode) {
          AndroidDebugBridge.removeClientChangeListener(AndroidRunningState.this);
        }
        if (deviceListener[0] != null) {
          Disposer.dispose(deviceListener[0]);
          AndroidDebugBridge.removeDeviceChangeListener(deviceListener[0]);
        }
        myStopped = true;
        synchronized (myLock) {
          myLock.notifyAll();
        }
      }
    });
    deviceListener[0] = prepareAndStartAppWhenDeviceIsOnline();
  }

  private boolean chooseOrLaunchDevice() {
    IDevice[] targetDevices = chooseDevicesAutomatically();
    if (targetDevices == null) {
      message("Canceled", STDERR);
      return false;
    }

    if (targetDevices.length > 0) {
      myTargetDevices = targetDevices;
    }
    else if (myTargetChooser instanceof EmulatorTargetChooser) {
      if (myAvdName == null) {
        chooseAvd();
      }
      if (myAvdName != null) {
        myFacet.launchEmulator(myAvdName, myCommandLine, getProcessHandler());
      }
      else if (getProcessHandler().isStartNotified()) {
        message("Canceled", STDERR);
        return false;
      }
    }
    else {
      message("USB device not found", STDERR);
      return false;
    }
    return true;
  }

  @NotNull
  private IDevice[] chooseDevicesManually(@Nullable Condition<IDevice> filter) {
    final Project project = myFacet.getModule().getProject();
    String value = PropertiesComponent.getInstance(project).getValue(ANDROID_TARGET_DEVICES_PROPERTY);
    String[] selectedSerials = value != null ? fromString(value) : null;
    DeviceChooserDialog chooser = new DeviceChooserDialog(myFacet, mySupportMultipleDevices, selectedSerials, filter);
    chooser.show();
    IDevice[] devices = chooser.getSelectedDevices();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE || devices.length == 0) {
      return DeviceChooser.EMPTY_DEVICE_ARRAY;
    }
    PropertiesComponent.getInstance(project).setValue(ANDROID_TARGET_DEVICES_PROPERTY, toString(devices));
    return devices;
  }

  @NotNull
  public static String toString(@NotNull IDevice[] devices) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0, n = devices.length; i < n; i++) {
      builder.append(devices[i].getSerialNumber());
      if (i < n - 1) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  @NotNull
  private static String[] fromString(@NotNull String s) {
    return s.split(" ");
  }

  private void message(@NotNull String message, @NotNull Key outputKey) {
    getProcessHandler().notifyTextAvailable(message + '\n', outputKey);
  }

  @Override
  public void clientChanged(Client client, int changeMask) {
    synchronized (myDebugLock) {
      if (myDebugLauncher == null) {
        return;
      }
      if (myDeploy && !myApplicationDeployed) {
        return;
      }
      IDevice device = client.getDevice();
      if (isMyDevice(device) && device.isOnline()) {
        if (myTargetDevices.length == 0) {
          myTargetDevices = new IDevice[]{device};
        }
        ClientData data = client.getClientData();
        if (myDebugLauncher != null && isToLaunchDebug(data)) {
          launchDebug(client);
        }
      }
    }
  }

  private boolean isToLaunchDebug(@NotNull ClientData data) {
    if (data.getDebuggerConnectionStatus() == ClientData.DebuggerStatus.WAITING) {
      return true;
    }
    String description = data.getClientDescription();
    if (description == null) {
      return false;
    }
    return description.equals(myTargetPackageName) && myApplicationLauncher.isReadyForDebugging(data, getProcessHandler());
  }

  private void launchDebug(Client client) {
    String port = Integer.toString(client.getDebuggerListenPort());
    myDebugLauncher.launchDebug(client.getDevice(), port);
    myDebugLauncher = null;
  }

  @Nullable
  Boolean isCompatibleDevice(@NotNull IDevice device) {
    if (myTargetChooser instanceof EmulatorTargetChooser) {
      if (device.isEmulator()) {
        String avdName = device.isEmulator() ? device.getAvdName() : null;
        if (myAvdName != null) {
          return myAvdName.equals(avdName);
        }
        return myFacet.isCompatibleDevice(device);
      }
    }
    else if (myTargetChooser instanceof UsbDeviceTargetChooser) {
      return !device.isEmulator();
    }
    return false;
  }

  private boolean isMyDevice(@NotNull IDevice device) {
    if (myTargetDevices.length > 0) {
      return ArrayUtilRt.find(myTargetDevices, device) >= 0;
    }
    Boolean compatible = isCompatibleDevice(device);
    return compatible == null || compatible.booleanValue();
  }

  public void setTargetDevices(@NotNull IDevice[] targetDevices) {
    myTargetDevices = targetDevices;
  }

  public void setConsole(@NotNull ConsoleView console) {
    myConsole = console;
  }

  @Nullable
  private MyDeviceChangeListener prepareAndStartAppWhenDeviceIsOnline() {
    if (myTargetDevices.length > 0) {
      for (IDevice targetDevice : myTargetDevices) {
        if (targetDevice.isOnline()) {
          if (!prepareAndStartApp(targetDevice) && !myStopped) {
            // todo: check: it may be we don't need to assign it directly
            myStopped = true;
            getProcessHandler().destroyProcess();
            break;
          }
        }
      }
      if (!myDebugMode && !myStopped) {
        getProcessHandler().destroyProcess();
      }
      return null;
    }
    final MyDeviceChangeListener deviceListener = new MyDeviceChangeListener();
    AndroidDebugBridge.addDeviceChangeListener(deviceListener);
    return deviceListener;
  }

  public synchronized void setProcessHandler(ProcessHandler processHandler) {
    myProcessHandler = processHandler;
  }

  public synchronized ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  private boolean prepareAndStartApp(IDevice device) {
    if (myDebugMode && myNonDebuggableOnDevice && !device.isEmulator()) {
      message("Cannot debug the application " + myPackageName + " on device '" + device.getName() + "',\n" +
              "because 'debuggable' attribute is set to 'false' in AndroidManifest.xml.\nYou may remove the attribute " +
              "and the IDE will automatically assign it during debug and release builds.", STDERR);
      return false;
    }
    if (!doPrepareAndStart(device)) {
      fireExecutionFailed();
      return false;
    }
    return true;
  }

  private void fireExecutionFailed() {
    for (AndroidRunningStateListener listener : myListeners) {
      listener.executionFailed();
    }
  }

  public void setOpenLogcatAutomatically(boolean openLogcatAutomatically) {
    myOpenLogcatAutomatically = openLogcatAutomatically;
  }

  private boolean doPrepareAndStart(@NotNull final IDevice device) {
    if (myClearLogcatBeforeStart) {
      clearLogcatAndConsole(getModule().getProject(), device);
    }

    message("Target device: " + device.getName(), STDOUT);
    try {
      if (myDeploy) {
        if (!checkPackageNames()) return false;
        IdeaAndroidProject ideaAndroidProject = myFacet.getIdeaAndroidProject();
        if (ideaAndroidProject == null) {
          if (!uploadAndInstall(device, myPackageName, myFacet)) return false;
          if (!uploadAndInstallDependentModules(device)) return false;
        } else {
          Variant selectedVariant = ideaAndroidProject.getSelectedVariant();

          // install apk (note that variant.getOutputFile() will point to a .aar in the case of a library)
          if (!ideaAndroidProject.getDelegate().isLibrary()) {
            File apk = selectedVariant.getMainArtifact().getOutputFile();
            if (!uploadAndInstallApk(device, myPackageName, apk.getAbsolutePath())) {
              return false;
            }
          }

          // install test apk
          if (getConfiguration() instanceof AndroidTestRunConfiguration) {
            AndroidArtifact testArtifactInfo = ideaAndroidProject.findInstrumentationTestArtifactInSelectedVariant();
            if (testArtifactInfo != null) {
              File testApk = testArtifactInfo.getOutputFile();
              if (!uploadAndInstallApk(device, myTestPackageName, testApk.getAbsolutePath())) {
                return false;
              }
            }
          }
        }
        myApplicationDeployed = true;
      }
      final AndroidApplicationLauncher.LaunchResult launchResult =
        myApplicationLauncher.launch(this, device);

      if (launchResult == AndroidApplicationLauncher.LaunchResult.STOP) {
        return false;
      }
      else if (launchResult == AndroidApplicationLauncher.LaunchResult.SUCCESS) {
        checkDdms();
      }

      synchronized (myDebugLock) {
        Client client = device.getClient(myTargetPackageName);
        if (myDebugLauncher != null) {
          if (client != null &&
              myApplicationLauncher.isReadyForDebugging(client.getClientData(), getProcessHandler())) {
            launchDebug(client);
          }
          else {
            message("Waiting for process: " + myTargetPackageName, STDOUT);
          }
        }
      }
      if (!myDebugMode && myOpenLogcatAutomatically) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            final ToolWindow androidToolWindow = ToolWindowManager.getInstance(myEnv.getProject()).
              getToolWindow(AndroidToolWindowFactory.TOOL_WINDOW_ID);

            // Activate the tool window, and once activated, make sure the right device is selected
            androidToolWindow.activate(new Runnable() {
              @Override
              public void run() {
                int count = androidToolWindow.getContentManager().getContentCount();
                for (int i = 0; i < count; i++) {
                  Content content = androidToolWindow.getContentManager().getContent(i);
                  DevicePanel devicePanel = content.getUserData(AndroidToolWindowFactory.DEVICES_PANEL_KEY);
                  if (devicePanel != null) {
                    devicePanel.selectDevice(device);
                    break;
                  }
                }
              }
            }, false);
          }
        });
      }
      return true;
    }
    catch (TimeoutException e) {
      LOG.info(e);
      message("Error: Connection to ADB failed with a timeout", STDERR);
      return false;
    }
    catch (AdbCommandRejectedException e) {
      LOG.info(e);
      message("Error: Adb refused a command", STDERR);
      return false;
    }
    catch (IOException e) {
      LOG.info(e);
      String message = e.getMessage();
      message("I/O Error" + (message != null ? ": " + message : ""), STDERR);
      return false;
    }
  }

  private boolean checkPackageNames() {
    final Map<String, List<String>> packageName2ModuleNames = new HashMap<String, List<String>>();
    packageName2ModuleNames.put(myPackageName, new ArrayList<String>(Arrays.asList(myFacet.getModule().getName())));

    for (Map.Entry<AndroidFacet, String> entry : myAdditionalFacet2PackageName.entrySet()) {
      final String moduleName = entry.getKey().getModule().getName();
      final String packageName = entry.getValue();
      List<String> list = packageName2ModuleNames.get(packageName);

      if (list == null) {
        list = new ArrayList<String>();
        packageName2ModuleNames.put(packageName, list);
      }
      list.add(moduleName);
    }
    boolean result = true;

    for (Map.Entry<String, List<String>> entry : packageName2ModuleNames.entrySet()) {
      final String packageName = entry.getKey();
      final List<String> moduleNames = entry.getValue();

      if (moduleNames.size() > 1) {
        final StringBuilder messageBuilder = new StringBuilder("Applications have the same package name ");
        messageBuilder.append(packageName).append(":\n    ");

        for (Iterator<String> it = moduleNames.iterator(); it.hasNext(); ) {
          String moduleName = it.next();
          messageBuilder.append(moduleName);
          if (it.hasNext()) {
            messageBuilder.append(", ");
          }
        }
        message(messageBuilder.toString(), STDERR);
        result = false;
      }
    }
    return result;
  }

  protected static void clearLogcatAndConsole(@NotNull final Project project, @NotNull final IDevice device) {
    final boolean[] result = {true};

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AndroidToolWindowFactory.TOOL_WINDOW_ID);
        if (toolWindow == null) {
          result[0] = false;
          return;
        }

        for (Content content : toolWindow.getContentManager().getContents()) {
          final AndroidLogcatView view = content.getUserData(AndroidLogcatView.ANDROID_LOGCAT_VIEW_KEY);

          if (view != null && device == view.getSelectedDevice()) {
            view.getLogConsole().clear();
          }
        }
      }
    }, ModalityState.defaultModalityState());

    if (result[0]) {
      AndroidLogcatUtil.clearLogcat(project, device);
    }
  }

  private boolean checkDdms() {
    AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
    if (myDebugMode && bridge != null && AndroidSdkUtils.canDdmsBeCorrupted(bridge)) {
      message(AndroidBundle.message("ddms.corrupted.error"), STDERR);
      if (myConsole != null && myRestarter != null) {
        final Runnable r = myRestarter;
        myConsole.printHyperlink(AndroidBundle.message("restart.adb.fix.text"), new HyperlinkInfo() {
          @Override
          public void navigate(Project project) {
            AndroidSdkUtils.restartDdmlib(project);

            final ProcessHandler processHandler = getProcessHandler();
            if (!processHandler.isProcessTerminated()) {
              processHandler.destroyProcess();
            }
            r.run();
          }
        });
        myConsole.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
      }
      return false;
    }
    return true;
  }

  private boolean uploadAndInstallDependentModules(@NotNull IDevice device)
    throws IOException, AdbCommandRejectedException, TimeoutException {
    for (AndroidFacet depFacet : myAdditionalFacet2PackageName.keySet()) {
      String packageName = myAdditionalFacet2PackageName.get(depFacet);
      packageName = getPackageNameFromGradle(packageName, depFacet);
      assert packageName != null;
      if (!uploadAndInstall(device, packageName, depFacet)) {
        return false;
      }
    }
    return true;
  }

  private static String computeTestPackageName(@NotNull AndroidFacet facet, @NotNull String packageName) {
    IdeaAndroidProject ideaAndroidProject = facet.getIdeaAndroidProject();
    if (ideaAndroidProject == null) {
      return packageName;
    }

    // In the case of Gradle projects, either the merged flavor provides a test package name,
    // or we just append ".test" to the source package name
    Variant selectedVariant = ideaAndroidProject.getSelectedVariant();
    String testPackageName = selectedVariant.getMergedFlavor().getTestPackageName();
    return (testPackageName != null) ? testPackageName : packageName + DEFAULT_TEST_PACKAGE_SUFFIX;
  }

  @Nullable
  private static String getPackageNameFromGradle(@NotNull String packageNameInManifest, @NotNull AndroidFacet facet) {
    IdeaAndroidProject ideaAndroidProject = facet.getIdeaAndroidProject();
    if (ideaAndroidProject == null) {
      return packageNameInManifest;
    }

    return ideaAndroidProject.computePackageName(packageNameInManifest);
  }

  private boolean uploadAndInstall(@NotNull IDevice device, @NotNull String packageName, AndroidFacet facet)
    throws IOException, AdbCommandRejectedException, TimeoutException {
    final Module module = facet.getModule();
    String localPath;

    if (myArtifactName != null && myArtifactName.length() > 0) {
      final Artifact artifact = ArtifactManager.getInstance(myEnv.getProject()).findArtifact(myArtifactName);

      if (artifact == null) {
        message("ERROR: cannot find artifact \"" + myArtifactName + '"', STDERR);
        return false;
      }
      if (!AndroidArtifactUtil.isRelatedArtifact(artifact, module)) {
        message("ERROR: artifact \"" + myArtifactName + "\" doesn't contain packaged module \"" + module.getName() + '"', STDERR);
        return false;
      }
      final String artifactOutPath = artifact.getOutputFilePath();

      if (artifactOutPath == null || artifactOutPath.length() == 0) {
        message("ERROR: output path is not specified for artifact \"" + myArtifactName + '"', STDERR);
        return false;
      }
      localPath = FileUtil.toSystemDependentName(artifactOutPath);
    }
    else {
      localPath = AndroidRootUtil.getApkPath(facet);
    }
    if (localPath == null) {
      message("ERROR: APK path is not specified for module \"" + module.getName() + '"', STDERR);
      return false;
    }
    return uploadAndInstallApk(device, packageName, localPath);
  }

  private boolean uploadAndInstallApk(@NotNull IDevice device, @NotNull String packageName, @NotNull String localPath)
    throws IOException, AdbCommandRejectedException, TimeoutException {
    String remotePath = "/data/local/tmp/" + packageName;
    if (!uploadApp(device, remotePath, localPath)) return false;
    if (!installApp(device, remotePath, packageName)) return false;
    return true;
  }

  private class MyISyncProgressMonitor implements SyncService.ISyncProgressMonitor {
    @Override
    public void start(int totalWork) {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isCanceled() {
      return myStopped;
    }

    @Override
    public void startSubTask(String name) {
    }

    @Override
    public void advance(int work) {
    }
  }

  private boolean uploadApp(@NotNull IDevice device, @NotNull String remotePath, @NotNull String localPath) throws IOException {
    if (myStopped) return false;
    message("Uploading file\n\tlocal path: " + localPath + "\n\tremote path: " + remotePath, STDOUT);
    String exceptionMessage;
    String errorMessage;
    try {
      SyncService service = device.getSyncService();
      if (service == null) {
        message("Can't upload file: device is not available.", STDERR);
        return false;
      }
      service.pushFile(localPath, remotePath, new MyISyncProgressMonitor());
      return true;
    }
    catch (TimeoutException e) {
      LOG.info(e);
      exceptionMessage = e.getMessage();
      errorMessage = "Connection timeout";
    }
    catch (AdbCommandRejectedException e) {
      LOG.info(e);
      exceptionMessage = e.getMessage();
      errorMessage = "ADB refused the command";
    }
    catch (final SyncException e) {
      LOG.info(e);
      final SyncException.SyncError errorCode = e.getErrorCode();

      if (SyncException.SyncError.NO_LOCAL_FILE.equals(errorCode)) {
        // Sometimes, users see the issue that for Gradle projects, the apk location used is incorrect (points to build/classes/?.apk
        // instead of build/apk/?.apk).
        // This happens reasonably often, but isn't reproducible, so we add this workaround here to show a popup to 'Sync Project with
        // Gradle Files' if it is a gradle project.
        // See https://code.google.com/p/android/issues/detail?id=59018 for more info.

        // The problem is that at this point, the project maybe a Gradle-based project, but its IdeaAndroidProject may be null.
        // We can check if there is a top-level build.gradle or settings.gradle file.
        DataManager.getInstance().getDataContextFromFocus().doWhenDone(new AsyncResult.Handler<DataContext>() {
          @Override
          public void run(DataContext dataContext) {
            if (dataContext != null) {
              Project project = CommonDataKeys.PROJECT.getData(dataContext);
              if (project != null && hasGradleFiles(project)) {
                AndroidGradleNotification notification = AndroidGradleNotification.getInstance(project);
                String message =
                  errorCode.getMessage() + '\n' + e.getMessage() + '\n' + "The project may need to be synced with Gradle files.";
                notification.showBalloon("Unexpected Error", message, NotificationType.ERROR,
                                         new CustomNotificationListener(project, new SyncProjectHyperlink()));
              }
            }
          }

          private boolean hasGradleFiles(@NotNull Project project) {
            File rootDirPath = new File(FileUtil.toSystemDependentName(project.getBasePath()));
            return GradleUtil.getGradleBuildFilePath(rootDirPath).isFile() || GradleUtil.getGradleSettingsFilePath(rootDirPath).isFile();
          }
        });
      }

      errorMessage = errorCode.getMessage();
      exceptionMessage = e.getMessage();
    }
    if (errorMessage.equals(exceptionMessage) || exceptionMessage == null) {
      message(errorMessage, STDERR);
    }
    else {
      message(errorMessage + '\n' + exceptionMessage, STDERR);
    }
    return false;
  }

  @SuppressWarnings({"DuplicateThrows"})
  public void executeDeviceCommandAndWriteToConsole(@NotNull IDevice device,
                                                    @NotNull String command,
                                                    @NotNull AndroidOutputReceiver receiver)
    throws IOException, TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException {
    message("DEVICE SHELL COMMAND: " + command, STDOUT);
    AndroidUtils.executeCommandOnDevice(device, command, receiver, false);
  }

  private boolean installApp(@NotNull IDevice device, @NotNull String remotePath, @NotNull String packageName)
    throws IOException, AdbCommandRejectedException, TimeoutException {
    message("Installing " + packageName, STDOUT);

    InstallResult result = null;
    boolean retry = true;
    while (!myStopped && retry) {
      result = installApp(device, remotePath);
      if (result.installOutput != null) {
        message(result.installOutput, result.failureCode == InstallFailureCode.NO_ERROR ? STDOUT : STDERR);
      }

      switch (result.failureCode) {
        case DEVICE_NOT_RESPONDING:
          message("Device is not ready. Waiting for " + WAITING_TIME + " sec.", STDOUT);
          synchronized (myLock) {
            try {
              myLock.wait(WAITING_TIME * 1000);
            }
            catch (InterruptedException e) {
              LOG.info(e);
            }
          }
          retry = true;
          break;
        case INCONSISTENT_CERTIFICATES:
          retry = promptUninstallExistingApp() && uninstallPackage(device, packageName);
          break;
        case NO_CERTIFICATE:
          message(AndroidBundle.message("deployment.failed.no.certificates.explanation"), STDERR);
          showMessageDialog(AndroidBundle.message("deployment.failed.no.certificates.explanation"));
          retry = false;
          break;
        default:
          retry = false;
          break;
      }
    }

    return result != null && result.failureCode == InstallFailureCode.NO_ERROR;
  }

  private boolean uninstallPackage(@NotNull IDevice device, @NotNull String packageName) {
    message("DEVICE SHELL COMMAND: pm uninstall " + packageName, STDOUT);
    String output;
    try {
      output = device.uninstallPackage(packageName);
    }
    catch (InstallException e) {
      return false;
    }

    if (output != null) {
      message(output, STDERR);
      return false;
    }
    return true;
  }

  private boolean promptUninstallExistingApp() {
    final AtomicBoolean uninstall = new AtomicBoolean(false);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        int result = Messages.showOkCancelDialog(myFacet.getModule().getProject(),
                                                 AndroidBundle.message("deployment.failed.uninstall.prompt.text"),
                                                 AndroidBundle.message("deployment.failed.title"),
                                                 Messages.getQuestionIcon());
        uninstall.set(result == 0);
      }
    }, ModalityState.defaultModalityState());

    return uninstall.get();
  }

  private void showMessageDialog(@NotNull final String message) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog(myFacet.getModule().getProject(), message, AndroidBundle.message("deployment.failed.title"));
      }
    });
  }

  private enum InstallFailureCode { NO_ERROR, DEVICE_NOT_RESPONDING, INCONSISTENT_CERTIFICATES, NO_CERTIFICATE, UNTYPED_ERROR, }

  private static class InstallResult {
    public final InstallFailureCode failureCode;
    @Nullable public final String failureMessage;
    @Nullable public final String installOutput;

    public InstallResult(InstallFailureCode failureCode, @Nullable String failureMessage, @Nullable String installOutput) {
      this.failureCode = failureCode;
      this.failureMessage = failureMessage;
      this.installOutput = installOutput;
    }
  }

  private InstallResult installApp(@NotNull IDevice device, @NotNull String remotePath)
    throws AdbCommandRejectedException, TimeoutException, IOException {

    MyReceiver receiver = new MyReceiver();
    try {
      executeDeviceCommandAndWriteToConsole(device, "pm install -r \"" + remotePath + "\"", receiver);
    }
    catch (ShellCommandUnresponsiveException e) {
      LOG.info(e);
      return new InstallResult(InstallFailureCode.DEVICE_NOT_RESPONDING, null, null);
    }

    return new InstallResult(getFailureCode(receiver),
                             receiver.failureMessage,
                             receiver.output.toString());
  }

  private InstallFailureCode getFailureCode(MyReceiver receiver) {
    if (receiver.errorType == NO_ERROR && receiver.failureMessage == null) {
      return InstallFailureCode.NO_ERROR;
    }

    if ("INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES".equals(receiver.failureMessage)) {
      return InstallFailureCode.INCONSISTENT_CERTIFICATES;
    } else if ("INSTALL_PARSE_FAILED_NO_CERTIFICATES".equals(receiver.failureMessage)) {
      return InstallFailureCode.NO_CERTIFICATE;
    }

    return InstallFailureCode.UNTYPED_ERROR;
  }

  public void addListener(@NotNull AndroidRunningStateListener listener) {
    myListeners.add(listener);
  }

  private class MyDeviceChangeListener implements AndroidDebugBridge.IDeviceChangeListener, Disposable {
    private final MergingUpdateQueue myQueue =
      new MergingUpdateQueue("ANDROID_DEVICE_STATE_UPDATE_QUEUE", 1000, true, null, this, null, false);
    private volatile boolean installed;

    public MyDeviceChangeListener() {
      installed = false;
    }

    @Override
    public void deviceConnected(final IDevice device) {
      // avd may be null if usb device is used, or if it didn't set by ddmlib yet
      if (device.getAvdName() == null || isMyDevice(device)) {
        message("Device connected: " + device.getSerialNumber(), STDOUT);

        // we need this, because deviceChanged is not triggered if avd is set to the emulator
        myQueue.queue(new MyDeviceStateUpdate(device));
      }
    }

    @Override
    public void deviceDisconnected(IDevice device) {
      if (isMyDevice(device)) {
        message("Device disconnected: " + device.getSerialNumber(), STDOUT);
      }
    }

    @Override
    public void deviceChanged(final IDevice device, int changeMask) {
      myQueue.queue(new Update(device.getSerialNumber()) {
        @Override
        public void run() {
          onDeviceChanged(device);
        }
      });
    }

    private synchronized void onDeviceChanged(IDevice device) {
      if (!installed && isMyDevice(device) && device.isOnline()) {
        if (myTargetDevices.length == 0) {
          myTargetDevices = new IDevice[]{device};
        }
        message("Device is online: " + device.getSerialNumber(), STDOUT);
        installed = true;
        if ((!prepareAndStartApp(device) || !myDebugMode) && !myStopped) {
          getProcessHandler().destroyProcess();
        }
      }
    }

    @Override
    public void dispose() {
    }

    private class MyDeviceStateUpdate extends Update {
      private final IDevice myDevice;

      public MyDeviceStateUpdate(IDevice device) {
        super(device.getSerialNumber());
        myDevice = device;
      }

      @Override
      public void run() {
        onDeviceChanged(myDevice);
        myQueue.queue(new MyDeviceStateUpdate(myDevice));
      }
    }
  }
}
