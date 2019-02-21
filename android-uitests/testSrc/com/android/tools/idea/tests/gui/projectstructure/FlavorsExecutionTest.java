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
package com.android.tools.idea.tests.gui.projectstructure;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.fakeadbserver.CommandHandler;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.ActivityManagerCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.SimpleShellHandler;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RunWith(GuiTestRemoteRunner.class)
public class FlavorsExecutionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  private static final String PROCESS_NAME = "google.simpleapplication";
  private static final String ACTIVITY_OUTPUT_PATTERN =
    ".*adb shell am start .*google\\.simpleapplication\\.Main_Activity.*Connected to process.*";
  private static final String FIRST_ACTIVITY_NAME = "F1MainActivity";
  private static final String SECOND_ACTIVITY_NAME = "F2MainActivity";

  private FakeAdbServer fakeAdbServer;

  @Before
  public void setUp() {
    StudioFlags.NEW_PSD_ENABLED.override(false);
  }

  @After
  public void tearDown() {
    StudioFlags.NEW_PSD_ENABLED.clearOverride();
  }

  @Before
  public void setupFakeAdbServer() throws IOException, InterruptedException, ExecutionException {
    ActivityManagerCommandHandler.ProcessStarter startCmdHandler = new ActivityManagerCommandHandler.ProcessStarter() {
      @NotNull
      @Override
      public String startProcess(@NotNull DeviceState deviceState) {
        deviceState.startClient(1234, 1235, PROCESS_NAME, false);
        return "";
      }
    };

    FakeAdbServer.Builder adbBuilder = new FakeAdbServer.Builder();
    adbBuilder.installDefaultCommandHandlers()
              .addDeviceHandler(new ActivityManagerCommandHandler(startCmdHandler))
              .addDeviceHandler(new LogcatCommandHandler())
              .addDeviceHandler(new JdwpCommandHandler());

    fakeAdbServer = adbBuilder.build();
    DeviceState fakeDevice = fakeAdbServer.connectDevice(
      "test_device",
      "Google",
      "Nexus 5X",
      "9.0",
      "28",
      DeviceState.HostConnectionType.LOCAL
    ).get();
    fakeDevice.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);

    fakeAdbServer.start();
    AndroidDebugBridge.enableFakeAdbServerMode(fakeAdbServer.getPort());
  }

  /***
   * To verify that the selected app flavor activity can be launched using build variants
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 5bf8bdbc-2ef1-4cd7-aa13-cbc91323cac9
   * <pre>
   *   Test Steps:
   *   1. Import a project
   *   2. Open Project Structure Dialog
   *   3. Select app module, add two new flavors (Flavor1 and Flavor2),
   *      and add flavorDimensions to build.gradle (Module: app)
   *   4. Switch to Project View
   *   5. Select app
   *   6. Add launcher activities under Flavor1 and Flavor2 and name them F1_Main_Activity and F2_Main_Activity
   *   7. Open Build variants window and select flavor1Debug
   *   8. Deploy the project on an AVD (Verify 1)
   *   9. Select flavor2Debug from Build variants
   *   10. Deploy the project on an AVD (Verify 2)
   *   Verification:
   *   1. Verify in Android Run tool window for the launch of F1_Main_Activity
   *   2. Verify in Android Run tool window for the launch of F2_Main_Activity
   * </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void runBuildFlavors() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleFlavoredApplication");

    ideFrameFixture
      .getBuildVariantsWindow()
      .selectVariantForModule("app", "flavor1Debug");

    ideFrameFixture
      .runApp("app")
      .selectDevice("Google Nexus 5X")
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture flavor1WindowContent = ideFrameFixture.getRunToolWindow().findContent("app");
    String flavor1LaunchPattern = ACTIVITY_OUTPUT_PATTERN.replace("Main_Activity", FIRST_ACTIVITY_NAME);
    flavor1WindowContent.waitForOutput(new PatternTextMatcher(Pattern.compile(flavor1LaunchPattern, Pattern.DOTALL)), 120);

    ideFrameFixture
      .getAndroidLogcatToolWindow()
      .selectDevicesTab()
      .selectProcess(PROCESS_NAME);

    ideFrameFixture
      .getBuildVariantsWindow()
      .selectVariantForModule("app", "flavor2Debug");

    ideFrameFixture
      .runApp("app")
      .selectDevice("Google Nexus 5X")
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture flavor2WindowContent = ideFrameFixture.getRunToolWindow().findContent("app");
    String flavor2LaunchPattern = ACTIVITY_OUTPUT_PATTERN.replace("Main_Activity", SECOND_ACTIVITY_NAME);
    flavor2WindowContent.waitForOutput(new PatternTextMatcher(Pattern.compile(flavor2LaunchPattern, Pattern.DOTALL)), 120);

    ideFrameFixture
      .getAndroidLogcatToolWindow()
      .selectDevicesTab()
      .selectProcess(PROCESS_NAME);
  }

  @After
  public void shutdownFakeAdb() throws Exception {
    AndroidDebugBridge.terminate();
    AndroidDebugBridge.disableFakeAdbServerMode();
    fakeAdbServer.close();
  }

  private static class LogcatCommandHandler extends SimpleShellHandler {

    private LogcatCommandHandler() {
      super("logcat");
    }

    @Override
    public void execute(@NotNull FakeAdbServer fakeAdbServer,
                       @NotNull Socket responseSocket,
                       @NotNull DeviceState device,
                       @Nullable String args) {
      try {
        OutputStream output = responseSocket.getOutputStream();

        if (args == null) {
          CommandHandler.writeFail(output);
          return;
        }

        CommandHandler.writeOkay(output);

        String response;
        if (args.startsWith("--help")) {
          response = "epoch";
        } else {
          response = "";
        }

        CommandHandler.writeString(output, response);
      }
      catch (IOException ignored) {
        // Unable to write to socket. Can't communicate anything with client. Just swallow
        // the exception and move on
      }
    }
  }
}
