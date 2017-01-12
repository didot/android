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
package com.android.tools.idea.explorer.adbimpl;

import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.idea.explorer.FutureCallbackExecutor;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.android.tools.idea.explorer.fs.DeviceFileSystemService;
import com.android.tools.idea.explorer.fs.DeviceFileSystemServiceListener;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Abstraction over ADB devices and their file system.
 * The service is meant to be called on the EDT thread, where
 * long running operations either raise events or return a Future.
 */
public class AdbDeviceFileSystemService implements DeviceFileSystemService {
  public static Logger LOGGER = Logger.getInstance(AdbDeviceFileSystemService.class);

  @NotNull private final Project myProject;
  @NotNull private final FutureCallbackExecutor myEdtExecutor;
  @NotNull private final FutureCallbackExecutor myTaskExecutor;
  @NotNull private final List<DeviceFileSystem> myDevices = new ArrayList<>();
  @NotNull private final List<DeviceFileSystemServiceListener> myListeners = new ArrayList<>();
  private State myState = State.Initial;
  @Nullable private AndroidDebugBridge myBridge;
  @Nullable private DeviceChangeListener myDeviceChangeListener;
  @Nullable private DebugBridgeChangeListener myDebugBridgeChangeListener;
  @Nullable private File myAdb;

  public AdbDeviceFileSystemService(@NotNull Project project, @NotNull Executor edtExecutor, @NotNull Executor taskExecutor) {
    myProject = project;
    myEdtExecutor = new FutureCallbackExecutor(edtExecutor);
    myTaskExecutor = new FutureCallbackExecutor(taskExecutor);
  }

  public enum State {
    Initial,
    SetupRunning,
    SetupDone,
  }

  @Override
  public void addListener(DeviceFileSystemServiceListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(DeviceFileSystemServiceListener listener) {
    myListeners.remove(listener);
  }

  @NotNull
  public FutureCallbackExecutor getEdtExecutor() {
    return myEdtExecutor;
  }

  @NotNull
  public FutureCallbackExecutor getTaskExecutor() {
    return myTaskExecutor;
  }

  @Override
  @NotNull
  public ListenableFuture<Void> start() {
    checkState(State.Initial);

    final File adb = AndroidSdkUtils.getAdb(myProject);
    if (adb == null) {
      LOGGER.error("ADB not found");
      return Futures.immediateFailedFuture(new FileNotFoundException("Android Debug Bridge not found."));
    }

    myAdb = adb;
    myDeviceChangeListener = new DeviceChangeListener();
    myDebugBridgeChangeListener = new DebugBridgeChangeListener();
    AndroidDebugBridge.addDeviceChangeListener(myDeviceChangeListener);
    AndroidDebugBridge.addDebugBridgeChangeListener(myDebugBridgeChangeListener);

    return startDebugBridge();
  }

  @NotNull
  private ListenableFuture<Void> startDebugBridge() {
    assert myAdb != null;

    myState = State.SetupRunning;
    SettableFuture<Void> futureResult = SettableFuture.create();
    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(myAdb);
    myEdtExecutor.addCallback(future, new FutureCallback<AndroidDebugBridge>() {
      @Override
      public void onSuccess(@Nullable AndroidDebugBridge bridge) {
        LOGGER.info("Successfully obtained debug bridge");
        myState = State.SetupDone;
        futureResult.set(null);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        LOGGER.error("Unable to obtain debug bridge", t);
        myState = State.Initial;
        futureResult.setException(t);
      }
    });

    return futureResult;
  }

  @NotNull
  @Override
  public ListenableFuture<Void> restart() {
    if (myState == State.Initial) {
      return start();
    }

    checkState(State.SetupDone);
    SettableFuture<Void> futureResult = SettableFuture.create();
    getTaskExecutor().execute(() -> {
      try {
        AdbService.getInstance().terminateDdmlib();
      } catch(Throwable t) {
        futureResult.setException(t);
        return;
      }

      getEdtExecutor().execute(() -> {
        ListenableFuture<Void> futureStart = startDebugBridge();
        getEdtExecutor().addCallback(futureStart, new FutureCallback<Void>() {
          @Override
          public void onSuccess(@Nullable Void result) {
            futureResult.set(null);
          }

          @Override
          public void onFailure(@NotNull Throwable t) {
            futureResult.setException(t);
          }
        });
      });
    });
    return futureResult;
  }

  @Override
  public void dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(myDeviceChangeListener);
    AndroidDebugBridge.removeDebugBridgeChangeListener(myDebugBridgeChangeListener);
    myBridge = null;
    myDevices.clear();
  }

  private void setupBridge(@Nullable AndroidDebugBridge bridge) {
    if (myBridge != null) {
      myDevices.clear();
      myListeners.forEach(DeviceFileSystemServiceListener::updated);
      myBridge = null;
    }

    if (bridge != null) {
      myBridge = bridge;
      if (myBridge.hasInitialDeviceList()) {
        Arrays.stream(myBridge.getDevices())
          .map(d -> new AdbDeviceFileSystem(this, d))
          .forEach(myDevices::add);
      }
    }
  }

  @NotNull
  @Override
  public ListenableFuture<List<DeviceFileSystem>> getDevices() {
    checkState(State.SetupDone);
    return Futures.immediateFuture(myDevices);
  }

  private void checkState(State state) {
    if (myState != state) {
      throw new IllegalStateException();
    }
  }

  private class DebugBridgeChangeListener implements AndroidDebugBridge.IDebugBridgeChangeListener {
    @Override
    public void bridgeChanged(@Nullable AndroidDebugBridge bridge) {
      LOGGER.info("Debug bridge changed");
      myEdtExecutor.execute(() -> setupBridge(bridge));
    }
  }

  private class DeviceChangeListener implements AndroidDebugBridge.IDeviceChangeListener {
    @Override
    public void deviceConnected(@NonNull IDevice device) {
      LOGGER.info(String.format("Device connected: %s", device));
      myEdtExecutor.execute(() -> {
        DeviceFileSystem deviceFileSystem = findDevice(device);
        if (deviceFileSystem == null) {
          AdbDeviceFileSystem newDevice = new AdbDeviceFileSystem(AdbDeviceFileSystemService.this, device);
          myDevices.add(newDevice);
          myListeners.forEach(x -> x.deviceAdded(newDevice));
        }
      });
    }

    @Override
    public void deviceDisconnected(@NonNull IDevice device) {
      LOGGER.info(String.format("Device disconnected: %s", device));
      myEdtExecutor.execute(() -> {
        DeviceFileSystem deviceFileSystem = findDevice(device);
        if (deviceFileSystem != null) {
          myListeners.forEach(x -> x.deviceRemoved(deviceFileSystem));
          myDevices.remove(deviceFileSystem);
        }
      });
    }

    @Override
    public void deviceChanged(@NonNull IDevice device, int changeMask) {
      LOGGER.info(String.format("Device changed: %s", device));
      myEdtExecutor.execute(() -> {
        DeviceFileSystem deviceFileSystem = findDevice(device);
        if (deviceFileSystem != null) {
          myListeners.forEach(x -> x.deviceUpdated(deviceFileSystem));
        }
      });
    }

    @Nullable
    private DeviceFileSystem findDevice(@NonNull IDevice device) {
      return myDevices.stream()
        .filter(x -> ((AdbDeviceFileSystem)x).isDevice(device))
        .findFirst()
        .orElse(null);
    }
  }
}
