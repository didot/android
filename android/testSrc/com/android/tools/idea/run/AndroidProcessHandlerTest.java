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
package com.android.tools.idea.run;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.logcat.AndroidLogcatReceiver;
import com.android.tools.idea.logcat.AndroidLogcatService;
import com.android.tools.idea.logcat.output.LogcatOutputConfigurableProvider;
import com.android.tools.idea.logcat.output.LogcatOutputSettings;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.command.impl.DummyProject;
import com.intellij.openapi.util.Key;
import org.easymock.EasyMock;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

public class AndroidProcessHandlerTest extends AndroidTestCase {
  private static final String DEVICE_NAME = "myDevice";
  private static final int DEVICE_API_LEVEL = 25;
  private static final String DEVICE_SERIAL_NUMBER = "device-1";
  private static final String APPLICATION_ID = "FooApp";
  private static final int CLIENT_PID = 1493;

  private AndroidLogcatService myLogcatService;
  private CountDownLatch myExecuteShellCommandLatch;
  private IDevice myDevice;
  private Client myClient;


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.override(true);

    myDevice = EasyMock.createMock(IDevice.class);
    EasyMock.expect(myDevice.getName()).andReturn(DEVICE_NAME).anyTimes();
    EasyMock.expect(myDevice.getVersion()).andReturn(new AndroidVersion(DEVICE_API_LEVEL)).anyTimes();
    EasyMock.expect(myDevice.getClientName(EasyMock.anyInt())).andReturn(APPLICATION_ID).anyTimes();
    EasyMock.expect(myDevice.getClient(APPLICATION_ID)).andAnswer(() -> myClient).anyTimes();
    EasyMock.expect(myDevice.getSerialNumber()).andReturn(DEVICE_SERIAL_NUMBER).anyTimes();
    EasyMock.expect(myDevice.isOnline()).andReturn(true).anyTimes();

    myDevice.executeShellCommand(EasyMock.eq("logcat --help"), EasyMock.anyObject(), EasyMock.eq(10L), EasyMock.eq(TimeUnit.SECONDS));

    EasyMock.expectLastCall().andAnswer(() -> {
      sendTextLine((IShellOutputReceiver)EasyMock.getCurrentArguments()[1], "epoch");
      myExecuteShellCommandLatch.countDown();

      return null;
    }).anyTimes();

    myDevice.executeShellCommand(EasyMock.eq("logcat -v long -v epoch"), EasyMock.anyObject(), EasyMock.eq(0L),
                                 EasyMock.eq(TimeUnit.MILLISECONDS));

    EasyMock.expectLastCall().andAnswer(() -> {
      AndroidLogcatReceiver receiver = (AndroidLogcatReceiver)EasyMock.getCurrentArguments()[1];
      sendTextLine(receiver, "[ 1503099551.439 1493:1595 W/DummyFirst     ]");
      sendTextLine(receiver, "First Line1");
      sendTextLine(receiver, "First Line2");
      sendTextLine(receiver, "First Line3");
      sendTextLine(receiver, "[ 1505950751.439 1493:1595 W/DummySecond     ]");
      sendTextLine(receiver, "Second Line1");
      receiver.cancel();
      myExecuteShellCommandLatch.countDown();
      return null;
    }).anyTimes();

    ClientData clientData = EasyMock.createMock(ClientData.class);
    EasyMock.expect(clientData.getClientDescription()).andReturn(APPLICATION_ID).anyTimes();
    EasyMock.expect(clientData.getPid()).andReturn(CLIENT_PID).anyTimes();

    myClient = EasyMock.createMock(Client.class);
    EasyMock.expect(myClient.getDevice()).andReturn(myDevice).anyTimes();
    EasyMock.expect(myClient.getClientData()).andReturn(clientData).anyTimes();
    EasyMock.expect(myClient.isValid()).andReturn(true).anyTimes();

    EasyMock.replay(myDevice, myClient, clientData);

    myLogcatService = AndroidLogcatService.getInstance();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myLogcatService != null) {
        myLogcatService.shutdown();
      }
      StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.clearOverride();
      LogcatOutputSettings.getInstance().reset();
    }
    finally {
      super.tearDown();
    }
  }

  public void testLogcatMessagesAreForwardedAsProcessEvents() throws Exception {
    // Prepare
    myExecuteShellCommandLatch = new CountDownLatch(2);

    AndroidProcessHandler processHandler = new AndroidProcessHandler.Builder(DummyProject.getInstance())
      .setApplicationId(APPLICATION_ID)
      .build();
    Collection<String> text = new ArrayList<>();

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        text.add(event.getText());
      }
    });

    processHandler.addTargetDevice(myDevice);
    processHandler.clientChanged(myClient, Client.CHANGE_NAME);

    // Act
    myLogcatService.deviceConnected(myDevice);
    myExecuteShellCommandLatch.await();

    assertThat(text).isEqualTo(Arrays.asList(
      "Connected to process 1493 on device myDevice\n",
      LogcatOutputConfigurableProvider.BANNER_MESSAGE + '\n',
      "W/DummyFirst: First Line1\n",
      "    First Line2\n",
      "    First Line3\n",
      "W/DummySecond: Second Line1\n"));
  }

  public void testLogcatMessagesAreNotForwardedIfFeatureDisabled() throws Exception {
    // Prepare
    myExecuteShellCommandLatch = new CountDownLatch(2);
    StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.override(false);

    AndroidProcessHandler processHandler = new AndroidProcessHandler.Builder(DummyProject.getInstance())
      .setApplicationId(APPLICATION_ID)
      .build();

    List<ProcessEvent> events = new ArrayList<>();
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        events.add(event);
      }
    });
    processHandler.addTargetDevice(myDevice);
    processHandler.clientChanged(myClient, Client.CHANGE_NAME);

    // Act
    myLogcatService.deviceConnected(myDevice);
    myExecuteShellCommandLatch.await();

    // Assert
    assertThat(events.size()).isEqualTo(1);
    assertThat(events.get(0).getText()).isEqualTo("Connected to process 1493 on device myDevice\n");
  }

  public void testLogcatMessagesAreNotForwardedIfSettingDisabled() throws Exception {
    // Prepare
    myExecuteShellCommandLatch = new CountDownLatch(2);

    LogcatOutputSettings.getInstance().setRunOutputEnabled(false);
    LogcatOutputSettings.getInstance().setDebugOutputEnabled(false);

    AndroidProcessHandler processHandler = new AndroidProcessHandler.Builder(DummyProject.getInstance())
      .setApplicationId(APPLICATION_ID)
      .build();

    List<ProcessEvent> events = new ArrayList<>();
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        events.add(event);
      }
    });
    processHandler.addTargetDevice(myDevice);
    processHandler.clientChanged(myClient, Client.CHANGE_NAME);

    // Act
    myLogcatService.deviceConnected(myDevice);
    myExecuteShellCommandLatch.await();

    // Assert
    assertThat(events.size()).isEqualTo(1);
    assertThat(events.get(0).getText()).isEqualTo("Connected to process 1493 on device myDevice\n");
  }

  private static void sendTextLine(@NotNull IShellOutputReceiver receiver, @NotNull String s) {
    byte[] bytes = (s + "\n").getBytes(StandardCharsets.UTF_8);
    receiver.addOutput(bytes, 0, bytes.length);
  }
}
