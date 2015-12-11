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
import com.android.ddmlib.IDevice;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** A utility class to wait for an emulator to be fully launched (ready for "pm install") and connected to adb. */
public class EmulatorConnectionListener {
  // Wait for a device corresponding to given emulator to come online for the given timeout period
  public static ListenableFuture<IDevice> getDeviceForEmulator(@NotNull String avdName,
                                                               @Nullable ProcessHandler emulatorProcessHandler,
                                                               long timeout,
                                                               @NotNull TimeUnit units) {
    if (emulatorProcessHandler == null) {
      return Futures.immediateFailedFuture(new RuntimeException("Emulator process for AVD " + avdName + " died."));
    }

    final SettableFuture<IDevice> future = SettableFuture.create();
    WaitForEmulatorTask task = new WaitForEmulatorTask(future, avdName, emulatorProcessHandler, timeout, units);
    ApplicationManager.getApplication().executeOnPooledThread(task);
    return future;
  }

  private static final class WaitForEmulatorTask implements Runnable {
    private static final TimeUnit POLL_TIMEUNIT = TimeUnit.SECONDS;

    private final SettableFuture<IDevice> myDeviceFuture;
    private final String myAvdName;
    private final ProcessHandler myEmulatorProcessHandler;
    private final long myTimeout; // in POLL_TIMEUNIT units

    private WaitForEmulatorTask(@NotNull SettableFuture<IDevice> device,
                                @NotNull String avdName,
                                @NotNull ProcessHandler emulatorProcessHandler,
                                long timeout,
                                @NotNull TimeUnit units) {
      myDeviceFuture = device;
      myAvdName = avdName;
      myEmulatorProcessHandler = emulatorProcessHandler;
      myTimeout = POLL_TIMEUNIT.convert(timeout, units);
    }

    @Override
    public void run() {
      for (long i = 0; i < myTimeout; i++) {
        if (myDeviceFuture.isCancelled()) {
          return;
        }

        if (myEmulatorProcessHandler.isProcessTerminated() || myEmulatorProcessHandler.isProcessTerminating()) {
          myDeviceFuture.setException(new RuntimeException("The emulator process for AVD " + myAvdName + " was killed."));
          return;
        }

        // We assume the bridge has been connected prior to this
        AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
        if (bridge == null || !bridge.isConnected()) {
          myDeviceFuture.setException(new RuntimeException("adb connection not available, or was terminated."));
          return;
        }

        for (IDevice device : bridge.getDevices()) {
          if (!device.isEmulator()) {
            continue;
          }

          if (!StringUtil.equals(device.getAvdName(), myAvdName)) {
            continue;
          }

          // now it looks like the AVD is online, but we still have to wait for the AVD to be ready for installation
          if (isEmulatorReady(device)) {
            LaunchUtils.initiateDismissKeyguard(device);
            myDeviceFuture.set(device);
            return;
          }
        }

        // sleep for a while
        Uninterruptibles.sleepUninterruptibly(1, POLL_TIMEUNIT);
      }

      String msg = "Timed out after " + POLL_TIMEUNIT.toSeconds(myTimeout) + "seconds waiting for emulator to come online.";
      myDeviceFuture.setException(new TimeoutException(msg));
      Logger.getInstance(EmulatorConnectionListener.class).warn(msg);
    }

    private static boolean isEmulatorReady(@NotNull IDevice device) {
      if (!device.isOnline()) {
        return false;
      }

      String bootComplete = device.getProperty("dev.bootcomplete");
      if (bootComplete == null) {
        Logger.getInstance(EmulatorConnectionListener.class).warn("Emulator not ready yet, dev.bootcomplete = null");
        return false;
      }

      // Emulators may be reported as online, but may not have services running yet. Attempting to install at this time
      // will result in an error like "Could not access the Package Manager". We use the following heuristic to check
      // that the system is in a reasonable state to install apps.
      return device.getClients().length > 5 ||
             device.getClient("android.process.acore") != null ||
             device.getClient("com.google.android.wearable.app") != null;
    }
  }
}
