/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.deploy;

import static com.google.common.truth.Truth.assertThat;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler;
import com.android.testutils.TestUtils;
import com.android.tools.deployer.DeployerTestUtils;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.FakeDeviceHandler;
import com.android.tools.deployer.devices.FakeDeviceLibrary;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.run.AndroidProcessHandler;
import com.android.tools.idea.run.deployable.DeviceBinder;
import com.android.tools.idea.run.deployable.SwappableProcessHandler;
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxAction;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.timing.Wait;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class DeploymentTest {
  // Project name for studio deployment dashboards
  private static final String PROJECT_NAME = "simple";
  private static final String APKS_LOCATION = "tools/base/deploy/deployer/src/test/resource/apks";
  private static final String DEPLOY_APK_NAME = "simple.apk";
  private static final String PACKAGE_NAME = "com.example.simpleapp";
  private static final int WAIT_TIME = 30;

  private enum APK {
    NONE(""),
    BASE("simple.apk"),
    CODE("simple+code.apk"),
    RESOURCE("simple+res.apk"),
    CODE_RESOURCE("simple+code+res.apk"),
    VERSION("simple+ver.apk");

    @NotNull public final String myFileName;

    APK(@NotNull String fileName) {
      myFileName = fileName;
    }
  }

  @Rule public final GuiTestRule myGuiTest = new GuiTestRule();
  private final FakeDevice myDevice = new FakeDeviceLibrary().build(FakeDeviceLibrary.DeviceId.API_26);
  private final FakeDeviceHandler myHandler = new FakeDeviceHandler(myDevice);
  private FakeAdbServer myAdbServer;
  private AndroidDebugBridge myBridge;
  private Project myProject;

  @Before
  public void before() throws Exception {
    DeployerTestUtils.prepareStudioInstaller();

    // Build the server and configure it to use the default ADB command handlers.
    myAdbServer = new FakeAdbServer.Builder()
      .installDefaultCommandHandlers()
      .addDeviceHandler(myHandler)
      .addDeviceHandler(new JdwpCommandHandler())
      .build();

    myDevice.connectTo(myAdbServer);

    // Start server execution.
    myAdbServer.start();

    // Terminate the service if it's already started (it's a UI test, so there might be no shutdown between tests).
    AdbService.getInstance().dispose();

    // Start ADB with fake server and its port.
    AndroidDebugBridge.enableFakeAdbServerMode(myAdbServer.getPort());

    myProject = myGuiTest.openProject(PROJECT_NAME);

    // Get the bridge synchronously, since we're in test mode.
    myBridge = AdbService.getInstance().getDebugBridge(AndroidSdkUtils.getAdb(myProject)).get();

    // Wait for ADB.
    Wait.seconds(WAIT_TIME).expecting("Android Debug Bridge to connect").until(() -> myBridge.isConnected());
    Wait.seconds(WAIT_TIME).expecting("Initial device list is available").until(() -> myBridge.hasInitialDeviceList());
  }

  @After
  public void after() throws Exception {
    myGuiTest.ideFrame().closeProject();
    Wait.seconds(WAIT_TIME)
      .expecting("Project to close")
      .until(() -> ProjectManagerEx.getInstanceEx().getOpenProjects().length == 0);
    myProject = null;

    if (myAdbServer != null) {
      myAdbServer.stop().get();
    }

    AdbService.getInstance().dispose();
    AndroidDebugBridge.disableFakeAdbServerMode();

    DeployerTestUtils.removeStudioInstaller();
  }

  @Test
  public void runOnDevice() throws Exception {
    myDevice.setShellBridge(DeployerTestUtils.getShell());
    setActiveApk(myProject, APK.BASE);

    ActionManager manager = ActionManager.getInstance();
    DeviceAndSnapshotComboBoxAction action = (DeviceAndSnapshotComboBoxAction)manager.getAction("DeviceAndSnapshotComboBox");

    Wait.seconds(WAIT_TIME)
      .expecting("device to show up in ddmlib")
      .until(() -> {
        try {
          return !myAdbServer.getDeviceListCopy().get().isEmpty();
        }
        catch (InterruptedException | ExecutionException e) {
          return false;
        }
      });

    List<DeviceState> deviceStates = myAdbServer.getDeviceListCopy().get();
    for (DeviceState deviceState : deviceStates) {
      DeviceBinder deviceBinder = new DeviceBinder(deviceState);

      Wait.seconds(WAIT_TIME)
        .expecting("device to show up in combo box")
        .until(() -> GuiActionRunner.execute(new GuiQuery<Boolean>() {
          @Nullable
          @Override
          protected Boolean executeInEDT() {
            return action.setSelectedDevice(myProject, deviceBinder.getIDevice().getSerialNumber());
          }
        }));

      // Run the find and click all in EDT, since there is a potential race condition between finding
      // the button and clicking it.
      myGuiTest.ideFrame().findRunApplicationButton().click();

      Wait.seconds(WAIT_TIME)
        .expecting("launched client to appear")
        .until(() -> deviceBinder.getIDevice().getClient(PACKAGE_NAME) != null);
      Wait.seconds(WAIT_TIME)
        .expecting("launched client to appear")
        .until(() ->
                 RunContentManager.getInstance(myProject).getAllDescriptors().stream()
                   .filter(descriptor -> descriptor.getProcessHandler() instanceof AndroidProcessHandler)
                   .map(descriptor -> (AndroidProcessHandler)descriptor.getProcessHandler())
                   .filter(handler -> handler.getCopyableUserData(SwappableProcessHandler.EXTENSION_KEY).getExecutor() ==
                                      DefaultRunExecutor.getRunExecutorInstance())
                   .anyMatch(handler -> handler.getClient(deviceBinder.getIDevice()) != null)
        );
      myAdbServer.disconnectDevice(deviceBinder.getState().getDeviceId());
    }
  }

  private void setActiveApk(@NotNull Project project, @NotNull APK apk) throws IOException {
    File baseDir = new File(project.getBasePath());
    File targetApkFile = new File(baseDir, DEPLOY_APK_NAME);

    FileUtilRt.delete(targetApkFile);
    assertThat(targetApkFile.exists()).isFalse();

    if (apk == APK.NONE) {
      return;
    }

    File apkFile = TestUtils.getWorkspaceFile(new File(APKS_LOCATION, apk.myFileName).getPath());

    FileUtilRt.copy(apkFile, targetApkFile);
    assertThat(targetApkFile.isFile()).isTrue();
  }
}
