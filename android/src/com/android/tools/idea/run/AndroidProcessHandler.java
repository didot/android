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
package com.android.tools.idea.run;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.logcat.AndroidLogcatFormatter;
import com.android.tools.idea.logcat.AndroidLogcatService;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * {@link AndroidProcessHandler} is a {@link ProcessHandler} that corresponds to a single Android app
 * potentially running on multiple connected devices after a launch of the app from Studio.
 * <br/><br/>
 * It encodes the following behavior:<br/>
 *  - Provides an option to connect and monitor the processes running on the device(s).<br/>
 *  - If the processes are being monitored, then:<br/>
 *     - destroyProcess provides a way to kill the processes (typically, this is connected to the stop button in the UI).<br/>
 *     - if all of the processes die, then the handler terminates as well.
 */
public class AndroidProcessHandler extends ProcessHandler implements AndroidDebugBridge.IDeviceChangeListener,
                                                                     AndroidDebugBridge.IClientChangeListener {
  private static final Logger LOG = Logger.getInstance(AndroidProcessHandler.class);

  // If the client is not present on the monitored devices after this time, then it is assumed to have died.
  // We are keeping it so long because sometimes (for cold-swap) it seems to take a while..
  private static final long TIMEOUT_MS = 10000;

  @NotNull private final String myApplicationId;
  private final boolean myMonitoringRemoteProcess;

  @NotNull private final List<String> myDevices;
  @NotNull private final Set<Client> myClients;
  @NotNull private final LogcatOutputCapture myLogcatOutputCapture;

  private long myDeviceAdded;
  private boolean myNoKill;

  public AndroidProcessHandler(@NotNull String applicationId) {
    this(applicationId, true);
  }

  public AndroidProcessHandler(@NotNull String applicationId, boolean monitorRemoteProcess) {
    myApplicationId = applicationId;
    myDevices = new SmartList<>();
    myClients = Sets.newHashSet();
    myLogcatOutputCapture = new LogcatOutputCapture(applicationId);

    myMonitoringRemoteProcess = monitorRemoteProcess;
    if (myMonitoringRemoteProcess) {
      AndroidDebugBridge.addClientChangeListener(this);
      AndroidDebugBridge.addDeviceChangeListener(this);
    }
  }

  public void addTargetDevice(@NotNull final IDevice device) {
    myDevices.add(device.getSerialNumber());

    setMinDeviceApiLevel(device.getVersion());

    Client client = device.getClient(myApplicationId);
    if (client != null) {
      addClient(client);
    } else {
      notifyTextAvailable("Client not ready yet..", ProcessOutputTypes.STDOUT);
    }

    LOG.info("Adding device " + device.getName() + " to monitor for launched app: " + myApplicationId);
    myDeviceAdded = System.currentTimeMillis();
  }

  private void addClient(@NotNull final Client client) {
    if (!myClients.add(client)) {
      return;
    }
    IDevice device = client.getDevice();
    notifyTextAvailable("Connected to process " + client.getClientData().getPid() + " on device " + device.getName() + "\n",
                        ProcessOutputTypes.STDOUT);

    myLogcatOutputCapture.startCapture(device, client, this::notifyTextAvailable);
  }

  private void setMinDeviceApiLevel(@NotNull AndroidVersion deviceVersion) {
    AndroidVersion apiLevel = getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL);
    if (apiLevel == null || apiLevel.compareTo(deviceVersion) > 0) {
      putUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL, deviceVersion);
    }
  }

  @Override
  public boolean detachIsDefault() {
    return false;
  }

  @Override
  public boolean isSilentlyDestroyOnClose() {
    return true;
  }

  @Override
  public OutputStream getProcessInput() {
    return null;
  }

  @Override
  protected void detachProcessImpl() {
    notifyProcessDetached();
    cleanup();
  }

  @Override
  protected void destroyProcessImpl() {
    notifyProcessTerminated(0);
    killProcesses();
    cleanup();
  }

  private void killProcesses() {
    if (myNoKill) {
      return;
    }

    AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
    if (bridge == null) {
      return;
    }

    for (IDevice device : bridge.getDevices()) {
      if (myDevices.contains(device.getSerialNumber())) {
        // Workaround https://code.google.com/p/android/issues/detail?id=199342
        // Sometimes, just calling client.kill() could end up with the app dying and then coming back up
        // Very likely, this is because of how cold swap restarts the process (maybe it is using some persistent pending intents?)
        // However, calling am force-stop seems to solve that issue, so we do that first..
        try {
          device.executeShellCommand("am force-stop " + myApplicationId, new NullOutputReceiver());
        }
        catch (Exception ignored) {
        }

        Client client = device.getClient(myApplicationId);
        if (client != null) {
          client.kill();
        }
      }
    }
  }

  public void setNoKill() {
    myNoKill = true;
  }

  private void cleanup() {
    myDevices.clear();
    myClients.clear();
    myLogcatOutputCapture.stopAll();

    if (myMonitoringRemoteProcess) {
      AndroidDebugBridge.removeClientChangeListener(this);
      AndroidDebugBridge.removeDeviceChangeListener(this);
    }
  }

  @Override
  public void deviceConnected(@NotNull IDevice device) {
  }

  @Override
  public void deviceDisconnected(@NotNull IDevice device) {
    if (!myDevices.contains(device.getSerialNumber())) {
      return;
    }

    print("Device " + device.getName() + "disconnected, monitoring stopped.");
    stopMonitoring(device);
  }

  private void stopMonitoring(@NotNull IDevice device) {
    myLogcatOutputCapture.stopCapture(device);

    myDevices.remove(device.getSerialNumber());

    if (myDevices.isEmpty()) {
      detachProcess();
    }
  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {
    if ((changeMask & IDevice.CHANGE_CLIENT_LIST) != IDevice.CHANGE_CLIENT_LIST) {
      return;
    }

    if (!myDevices.contains(device.getSerialNumber())) {
      return;
    }

    Client client = device.getClient(myApplicationId);
    if (client != null) {
      addClient(client);
      return;
    }

    // sometimes, the application crashes before TIMEOUT_MS. So if we already knew of the app, and it is not there anymore, then assume
    // it got killed
    if (!myClients.isEmpty()) {
      for (Client c : myClients) {
        if (device.equals(c.getDevice())) {
          stopMonitoring(device);
          print("Application terminated.");
          return;
        }
      }
    }

    if ((System.currentTimeMillis() - myDeviceAdded) > TIMEOUT_MS) {
      print("Timed out waiting for process to appear on " + device.getName());
      stopMonitoring(device);
    } else {
      print("Waiting for process to come online");
    }
  }

  @Override
  public void clientChanged(@NotNull Client client, int changeMask) {
    if ((changeMask & Client.CHANGE_NAME) != Client.CHANGE_NAME) {
      return;
    }

    if (!myDevices.contains(client.getDevice().getSerialNumber())) {
      return;
    }

    if (StringUtil.equals(myApplicationId, client.getClientData().getClientDescription())) {
      addClient(client);
    }

    String name = client.getClientData().getClientDescription();
    if (name != null && myApplicationId.equals(name) && !client.isValid()) {
      print("Process " + client.getClientData().getPid() + " is not valid anymore!");
      stopMonitoring(client.getDevice());
    }
  }

  @NotNull
  public List<IDevice> getDevices() {
    Set<IDevice> devices = Sets.newHashSet();
    for (Client client : myClients) {
      devices.add(client.getDevice());
    }

    return Lists.newArrayList(devices);
  }

  @Nullable
  public Client getClient(@NotNull IDevice device) {
    String serial = device.getSerialNumber();

    for (Client client : myClients) {
      if (StringUtil.equals(client.getDevice().getSerialNumber(), serial)) {
        return client;
      }
    }

    return null;
  }

  @NotNull
  public Set<Client> getClients() {
    return myClients;
  }

  private void print(@NotNull String s) {
    notifyTextAvailable(s + "\n", ProcessOutputTypes.STDOUT);
  }

  public void reset() {
    myDevices.clear();
    myClients.clear();
    myLogcatOutputCapture.stopAll();
  }

  /**
   * Capture logcat messages of all known client processes and dispatch them so that
   * they are shown in the Run Console window.
   */
  static class LogcatOutputCapture {
    @NotNull private final String myApplicationId;
    /**
     * Keeps track of the registered listener associated to each device running the application.
     *
     * <p>Note: We need to serialize access to this field because calls to {@link #cleanup} and
     * {@link #stopMonitoring(IDevice)} come from different threads (EDT and Monitor Thread respectively).
     */
    @GuardedBy("myLock")
    @NotNull private final Map<IDevice, AndroidLogcatService.LogcatListener> myLogListeners = new HashMap<>();
    @NotNull private final Object myLock = new Object();

    public LogcatOutputCapture(@NotNull String applicationId) {
      myApplicationId = applicationId;
    }

    public void startCapture(@NotNull final IDevice device, @NotNull final Client client, @NotNull BiConsumer<String, Key> consumer) {
      if (!StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.get()) {
        return;
      }
      LOG.info(String.format("startCapture(\"%s\")", device.getName()));
      AndroidLogcatService.LogcatListener logListener = new ApplicationLogListener(myApplicationId, client.getClientData().getPid()) {
        private final String SIMPLE_FORMAT = AndroidLogcatFormatter.createCustomFormat(false, false, false, true);

        @Override
        protected String formatLogLine(@NotNull LogCatMessage line) {
          String message = AndroidLogcatFormatter.formatMessage(SIMPLE_FORMAT, line.getHeader(), line.getMessage());
          synchronized (myLock) {
            if (myLogListeners.size() > 1) {
              return String.format("[%1$s]: %2$s", device.getName(), message);
            }
            else {
              return message;
            }
          }
        }

        @Override
        protected void notifyTextAvailable(@NotNull String message, @NotNull Key key) {
          consumer.accept(message, key);
        }
      };

      AndroidLogcatService.getInstance().addListener(device, logListener, true);

      // Remember the listener for later cleanup
      synchronized (myLock) {
        // This should not happen (and we have never seen it happening), but removing the existing listener
        // ensures there are no memory leaks.
        if (myLogListeners.containsKey(device)) {
          LOG.warn(String.format("The device \"%s\" already has a registered logcat listener for application \"%s\". Removing it",
                                 device.getName(), myApplicationId));
          AndroidLogcatService.getInstance().removeListener(device, myLogListeners.get(device));
          myLogListeners.remove(device);
        }
        myLogListeners.put(device, logListener);
      }
    }

    public void stopCapture(@NotNull IDevice device) {
      LOG.info(String.format("stopCapture(\"%s\")", device.getName()));
      synchronized (myLock) {
        if (myLogListeners.containsKey(device)) {
          AndroidLogcatService.getInstance().removeListener(device, myLogListeners.get(device));
          myLogListeners.remove(device);
        }
      }
    }

    public void stopAll() {
      LOG.info("stopAll()");
      synchronized (myLock) {
        for (IDevice device : myLogListeners.keySet()) {
          AndroidLogcatService.getInstance().removeListener(device, myLogListeners.get(device));
        }
        myLogListeners.clear();
      }
    }
  }
}
