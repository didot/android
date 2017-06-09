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
package com.android.tools.idea.profilers;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.*;
import com.android.sdklib.AndroidVersion;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.profilers.perfd.PerfdProxy;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.profiler.proto.Agent;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.net.NetUtils;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.android.ddmlib.IDevice.CHANGE_STATE;

/**
 * Manages the interactions between DDMLIB provided devices, and what is needed to spawn ProfilerClient's.
 * On device connection it will spawn the performance daemon on device, and will notify the profiler system that
 * a new device has been connected. *ALL* interaction with IDevice is encapsulated in this class.
 */
class StudioProfilerDeviceManager implements AndroidDebugBridge.IDebugBridgeChangeListener, AndroidDebugBridge.IDeviceChangeListener,
                                             IdeSdks.IdeSdkChangeListener {

  private static Logger getLogger() {
    return Logger.getInstance(StudioProfilerDeviceManager.class);
  }

  private static final String BOOT_COMPLETE_PROPERTY = "dev.bootcomplete";
  private static final String BOOT_COMPLETE_MESSAGE = "1";

  private static final int MAX_MESSAGE_SIZE = 512 * 1024 * 1024 - 1;
  private static final int DEVICE_PORT = 12389;
  // On-device daemon uses Unix abstract socket for O and future devices.
  private static final String DEVICE_SOCKET_NAME = "AndroidStudioProfiler";

  @NotNull
  private final DataStoreService myDataStoreService;
  private boolean isAdbInitialized;
  /**
   * Maps a device to its correspondent {@link PerfdProxy}.
   */
  private Map<IDevice, PerfdProxy> myDeviceProxies;

  public StudioProfilerDeviceManager(@NotNull DataStoreService dataStoreService) {
    myDataStoreService = dataStoreService;
    AndroidDebugBridge.addDebugBridgeChangeListener(this);
    AndroidDebugBridge.addDeviceChangeListener(this);
    // TODO: Once adb API doesn't require a project, move initialization to constructor and remove this flag.
    isAdbInitialized = false;
    myDeviceProxies = new HashMap<>();
  }

  @Override
  public void sdkPathChanged(@NotNull File newSdkPath) {
    isAdbInitialized = false;
  }

  public void initialize(@NotNull Project project) {
    if (isAdbInitialized) {
      return;
    }

    final File adb = AndroidSdkUtils.getAdb(project);
    if (adb != null) {
      Futures.addCallback(AdbService.getInstance().getDebugBridge(adb), new FutureCallback<AndroidDebugBridge>() {
        @Override
        public void onSuccess(AndroidDebugBridge result) {
          isAdbInitialized = true;
        }

        @Override
        public void onFailure(Throwable t) {
          getLogger().warn(String.format("getDebugBridge %s failed", adb.getAbsolutePath()));
        }
      }, EdtExecutor.INSTANCE);
    }
    else {
      getLogger().warn("No adb available");
    }
  }

  public void dispose() {
    AndroidDebugBridge.removeDebugBridgeChangeListener(this);
    AndroidDebugBridge.removeDeviceChangeListener(this);
  }

  @Override
  public void bridgeChanged(@Nullable AndroidDebugBridge bridge) {
    if (bridge != null) {
      for (IDevice device : bridge.getDevices()) {
        deviceConnected(device);
      }
    }
  }

  @Override
  public void deviceConnected(@NonNull IDevice device) {
    if (device.isOnline()) {
      spawnPerfd(device);
    }
  }

  @Override
  public void deviceDisconnected(@NonNull IDevice device) {
  }

  @Override
  public void deviceChanged(@NonNull IDevice device, int changeMask) {
    if ((changeMask & CHANGE_STATE) != 0 && device.isOnline()) {
      spawnPerfd(device);
    }
  }

  private void spawnPerfd(@NonNull IDevice device) {
    PerfdThread thread = new PerfdThread(device, myDataStoreService);
    thread.start();
  }

  private class PerfdThread extends Thread {
    private final DataStoreService myDataStore;
    private final IDevice myDevice;
    private int myLocalPort;
    private PerfdProxy myPerfdProxy;

    public PerfdThread(@NotNull IDevice device, @NotNull DataStoreService datastore) {
      super("Perfd Thread: " + device.getSerialNumber());
      myDataStore = datastore;
      myDevice = device;
      myLocalPort = 0;
    }

    @Override
    public void run() {
      try {
        // Waits to make sure the device has completed boot sequence.
        if (!waitForBootComplete()) {
          throw new TimeoutException("Timed out waiting for device to be ready.");
        }

        String deviceDir = "/data/local/tmp/perfd/";
        copyFileToDevice("perfd", "plugins/android/resources/perfd", "../../out/studio/native/out/release", deviceDir, true);
        copyFileToDevice("libperfa.so", "plugins/android/resources/perfd", "../../out/studio/native/out/release", deviceDir, true);
        copyFileToDevice("perfa.jar", "plugins/android/resources", "../../out/studio/perfa/libs", deviceDir, false);
        // Simpleperf can be used by CPU profiler for method tracing, if it is supported by target device.
        pushSimpleperfIfSupported(deviceDir);
        pushAgentConfig("agent.config", deviceDir);

        myDevice.executeShellCommand(deviceDir + "perfd", new IShellOutputReceiver() {
          @Override
          public void addOutput(byte[] data, int offset, int length) {
            String s = new String(data, offset, length, Charsets.UTF_8);
            getLogger().info("[perfd]: " + s);
            if (myDeviceProxies.containsKey(myDevice)) {
              // PerfdProxy for the current device was already created.
              return;
            }
            createPerfdProxy();
          }

          @Override
          public void flush() {
            // flush does not always get called. So we need to perform the proxy server/channel clean up after the perfd process has died.
          }

          @Override
          public boolean isCancelled() {
            return false;
          }
        }, 0, null);

        getLogger().info("Terminating perfd thread");
      }
      catch (TimeoutException | ShellCommandUnresponsiveException | InterruptedException | SyncException e) {
        throw new RuntimeException(e);
      }
      catch (AdbCommandRejectedException | IOException e) {
        // AdbCommandRejectedException and IOException happen when unplugging the device shortly after plugging it in.
        // We don't want to crash in this case.
        getLogger().warn("Error when trying to spawn perfd:");
        getLogger().warn(e);
      }
    }

    /**
     * Copies a file from host (where Studio is running) to the device.
     * If executable, then the abi is taken into account.
     */
    private void copyFileToDevice(String fileName, String hostReleaseDir, String hostDevDir, String deviceDir, boolean executable)
      throws AdbCommandRejectedException, IOException {
      try {
        File dir = new File(PathManager.getHomePath(), hostReleaseDir);
        if (!dir.exists()) {
          // Development mode
          dir = new File(PathManager.getHomePath(), hostDevDir);
        }

        File file = null;
        if (executable) {
          for (String abi : myDevice.getAbis()) {
            File candidate = new File(dir, abi + "/" + fileName);
            if (candidate.exists()) {
              file = candidate;
              break;
            }
          }
        }
        else {
          File candidate = new File(dir, fileName);
          if (candidate.exists()) {
            file = candidate;
          }
        }

        // TODO: Handle the case where we don't have file for this platform.
        // TODO: In case of simpleperf, remember the device doesn't support it, so we don't try to use it to profile the device.
        assert file != null;
        // TODO: Add debug support for development
        /*
         * If copying the agent fails, we will attach the previous version of the agent
         * Hence we first delete old agent before copying new one
         */
        myDevice.executeShellCommand("rm -f " + deviceDir + fileName, new NullOutputReceiver());
        myDevice.executeShellCommand("mkdir -p " + deviceDir, new NullOutputReceiver());
        myDevice.pushFile(file.getAbsolutePath(), deviceDir + fileName);

        if (executable) {
          /*
           * In older devices, chmod letter usage isn't fully supported but CTS tests have been added for it since.
           * Hence we first try the letter scheme which is guaranteed in newer devices, and fall back to the octal scheme only if necessary.
           */
          ChmodOutputListener chmodListener = new ChmodOutputListener();
          myDevice.executeShellCommand("chmod +x " + deviceDir + fileName, chmodListener);
          if (chmodListener.hasErrors()) {
            myDevice.executeShellCommand("chmod 777 " + deviceDir + fileName, new NullOutputReceiver());
          }
        }
      }
      catch (TimeoutException | SyncException | ShellCommandUnresponsiveException e) {
        throw new RuntimeException(e);
      }
    }

    /**
     * Pushes simpleperf binary to device if it is supported (i.e. it's running O or newer APIs and has a supported architecture).
     */
    private void pushSimpleperfIfSupported(String devicePath) throws AdbCommandRejectedException, IOException {
      // Simpleperf tracing is not supported in devices older than O.
      if (!(isAtLeastO(myDevice) && StudioFlags.PROFILER_USE_SIMPLEPERF.get())) {
        return;
      }
      copyFileToDevice("simpleperf", "plugins/android/resources/simpleperf", "../../prebuilts/tools/common/simpleperf", devicePath, true);
    }

    /**
     * Creates and pushes a config file that lives in perfd but is shared bewteen both perfd + perfa
     */
    private void pushAgentConfig(@NotNull String fileName, @NotNull String devicePath)
      throws AdbCommandRejectedException, IOException, TimeoutException, SyncException, ShellCommandUnresponsiveException {
      // TODO: remove profiler.jvmti after agent uses only JVMTI to instrument bytecode on O+ devices.
      Agent.AgentConfig agentConfig = Agent.AgentConfig.newBuilder().setUseJvmti(StudioFlags.PROFILER_USE_JVMTI.get())
        .setUseLiveAlloc(StudioFlags.PROFILER_USE_LIVE_ALLOCATIONS.get()).build();

      File configFile = FileUtil.createTempFile(fileName, null, true);
      OutputStream oStream = new FileOutputStream(configFile);
      agentConfig.writeTo(oStream);
      myDevice.executeShellCommand("rm -f " + devicePath + fileName, new NullOutputReceiver());
      myDevice.pushFile(configFile.getAbsolutePath(), devicePath + fileName);
    }

    private void createPerfdProxy() {
      try {
        myLocalPort = NetUtils.findAvailableSocketPort();
        if (isAtLeastO(myDevice) && StudioFlags.PROFILER_USE_JVMTI.get()) {
          myDevice.createForward(myLocalPort, DEVICE_SOCKET_NAME,
                                 IDevice.DeviceUnixSocketNamespace.ABSTRACT);
        }
        else {
          myDevice.createForward(myLocalPort, DEVICE_PORT);
        }
        if (myLocalPort < 0) {
          return;
        }
        /*
          Creates the channel that is used to connect to the device perfd.

          TODO: investigate why ant build fails to find the ManagedChannel-related classes
          The temporary fix is to stash the currently set context class loader,
          so ManagedChannelProvider can find an appropriate implementation.
         */
        ClassLoader stashedContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(NettyChannelBuilder.class.getClassLoader());
        ManagedChannel perfdChannel = NettyChannelBuilder
          .forAddress("localhost", myLocalPort)
          .usePlaintext(true)
          .maxMessageSize(MAX_MESSAGE_SIZE)
          .build();
        Thread.currentThread().setContextClassLoader(stashedContextClassLoader);

        // Creates a proxy server that the datastore connects to.
        String channelName = myDevice.getSerialNumber();
        myPerfdProxy = new PerfdProxy(myDevice, perfdChannel, channelName);
        myPerfdProxy.connect();
        // Add the proxy to the proxies map.
        myDeviceProxies.put(myDevice, myPerfdProxy);
        myPerfdProxy.setOnDisconnectCallback(() -> {
          if (myDeviceProxies.containsKey(myDevice)) {
            myDeviceProxies.remove(myDevice);
          }
        });

        // TODO using directexecutor for this channel freezes up grpc calls that are redirected to the device (e.g. GetTimes)
        // We should otherwise do it for performance reasons, so we should investigate why.
        ManagedChannel proxyChannel = InProcessChannelBuilder.forName(channelName).build();
        myDataStore.connect(proxyChannel);
      }
      catch (TimeoutException | AdbCommandRejectedException | IOException e) {
        // If some error happened after PerfdProxy was created, make sure to disconnect it
        if (myPerfdProxy != null) {
          myPerfdProxy.disconnect();
        }
        throw new RuntimeException(e);
      }
    }

    /**
     * Whether the device is running O or higher APIs
     */
    private boolean isAtLeastO(IDevice device) {
      return device.getVersion().getFeatureLevel() >= AndroidVersion.VersionCodes.O;
    }

    /**
     * A helper method to check whether the device has completed the boot sequence.
     * In emulator userdebug builds, the device can appear online before boot has finished, and pushing and running perfd on device at that
     * point would result in a failure. Therefore we poll a device property (dev.bootcomplete) at regular intervals to make sure the device
     * is ready for perfd. Whe problem only seems to manifest in emulators but not real devices. Here we check the property in both cases to
     * be sure, as this is only called once when the device comes online.
     */
    private boolean waitForBootComplete() throws InterruptedException {
      // This checks the flag for a minute before giving up.
      // TODO: move ProfilerServiceProxy to support user-triggered retries, in cases where 1m isn't enough for the emulator to boot.
      for (int i = 0; i < 60; i++) {
        String state = myDevice.getProperty(BOOT_COMPLETE_PROPERTY);
        if (BOOT_COMPLETE_MESSAGE.equals(state)) {
          return true;
        }
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      }

      return false;
    }
  }

  private static class ChmodOutputListener implements IShellOutputReceiver {
    /**
     * When chmod fails to modify permissions, the following "Bad mode" error string is output.
     * This listener checks if the string is present to validate if chmod was successful.
     */
    private static final String BAD_MODE = "Bad mode";

    private boolean myHasErrors;

    @Override
    public void addOutput(byte[] data, int offset, int length) {
      String s = new String(data, Charsets.UTF_8);
      myHasErrors = s.contains(BAD_MODE);
    }

    @Override
    public void flush() {

    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    private boolean hasErrors() {
      return myHasErrors;
    }
  }
}