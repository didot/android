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
package com.android.tools.idea.logcat;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatTimestamp;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.intellij.execution.impl.ConsoleBuffer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * {@link AndroidLogcatService} is the class that manages logs in all connected devices and emulators.
 * Other classes can call {@link AndroidLogcatService#addListener(IDevice, LogLineListener)} to listen for logs of specific device/emulator.
 * Listeners invoked in a pooled thread and this class is thread safe.
 */
@ThreadSafe
public final class AndroidLogcatService implements AndroidDebugBridge.IDeviceChangeListener, Disposable {
  private static Logger getLog() {
    return Logger.getInstance(AndroidLogcatService.class);
  }

  private static class LogcatBuffer {
    private int myBufferSize;
    private final LinkedList<LogCatMessage> myMessages = new LinkedList<>();

    public void addMessage(@NotNull LogCatMessage message) {
      myMessages.add(message);
      myBufferSize += message.getMessage().length();
      if (ConsoleBuffer.useCycleBuffer()) {
        while (myBufferSize > ConsoleBuffer.getCycleBufferSize()) {
          myBufferSize -= myMessages.removeFirst().getMessage().length();
        }
      }
    }

    @NotNull
    public List<LogCatMessage> getMessages() {
      return myMessages;
    }
  }

  public interface LogLineListener {
    void receiveLogLine(@NotNull LogCatMessage line);
  }

  private final Object myLock = new Object();

  @GuardedBy("myLock")
  private final Map<IDevice, List<LogLineListener>> myListeners = new HashMap<>();

  @GuardedBy("myLock")
  private final Map<IDevice, LogcatBuffer> myLogBuffers = new HashMap<>();

  @GuardedBy("myLock")
  private final Map<IDevice, AndroidLogcatReceiver> myLogReceivers = new HashMap<>();

  /**
   * This is a list of commands to execute per device. We use a newSingleThreadExecutor
   * to model a single queue of tasks to run, but that is poorly reflected in the
   * type of the variable.
   */
  @GuardedBy("myLock")
  private final Map<IDevice, ExecutorService> myExecutors = new HashMap<>();

  @NotNull
  public static AndroidLogcatService getInstance() {
    return ServiceManager.getService(AndroidLogcatService.class);
  }

  @TestOnly
  AndroidLogcatService() {
    AndroidDebugBridge.addDeviceChangeListener(this);
  }

  private void startReceiving(@NotNull final IDevice device) {
    synchronized (myLock) {
      if (myLogReceivers.containsKey(device)) {
        return;
      }
      connect(device);
      final AndroidLogcatReceiver receiver = createReceiver(device);
      myLogReceivers.put(device, receiver);
      myLogBuffers.put(device, new LogcatBuffer());
      ExecutorService executor = myExecutors.get(device);
      executor.submit((() -> {
        try {
          AndroidUtils.executeCommandOnDevice(device, "logcat -v long", receiver, true);
        }
        catch (Exception e) {
          getLog().info(String.format(
            "Caught exception when capturing logcat output from the device %1$s. Receiving logcat output from this device will be " +
            "stopped, and the listeners will be notified with this exception as the last message", device.getName()), e);
          LogCatHeader dummyHeader = new LogCatHeader(Log.LogLevel.ERROR, 0, 0, "?", "Internal", LogCatTimestamp.ZERO);
          receiver.notifyLine(dummyHeader, e.getMessage());
        }
      }));
    }
  }

  @NotNull
  private AndroidLogcatReceiver createReceiver(@NotNull final IDevice device) {
    final LogLineListener logLineListener = new LogLineListener() {
      @Override
      public void receiveLogLine(@NotNull LogCatMessage line) {
        synchronized (myLock) {
          if (myListeners.containsKey(device)) {
            for (LogLineListener listener : myListeners.get(device)) {
              listener.receiveLogLine(line);
            }
          }
          if (myLogBuffers.containsKey(device)) {
            myLogBuffers.get(device).addMessage(line);
          }
        }
      }
    };
    return new AndroidLogcatReceiver(device, logLineListener);
  }

  private void connect(@NotNull IDevice device) {
    synchronized (myLock) {
      if (!myExecutors.containsKey(device)) {
        ThreadFactory factory = new ThreadFactoryBuilder()
            .setNameFormat("logcat-" + device.getName()).build();
        myExecutors.put(device, Executors.newSingleThreadExecutor(factory));
      }
    }
  }

  private void disconnect(@NotNull IDevice device) {
    synchronized (myLock) {
      stopReceiving(device);
      myExecutors.remove(device);
    }
  }

  private void stopReceiving(@NotNull IDevice device) {
    synchronized (myLock) {
      if (myLogReceivers.containsKey(device)) {
        myLogReceivers.get(device).cancel();
        myLogReceivers.remove(device);
        myLogBuffers.remove(device);
      }
    }
  }

  /**
   * Clears logs for the current device.
   */
  public void clearLogcat(@NotNull IDevice device, @NotNull Project project) {
    // In theory, we only need to clear the buffer. However, due to issues in the platform, clearing logcat via "logcat -c" could
    // end up blocking the current logcat readers. As a result, we need to issue a restart of the logging to work around the platform bug.
    // See https://code.google.com/p/android/issues/detail?id=81164 and https://android-review.googlesource.com/#/c/119673
    // NOTE: We can avoid this and just clear the console if we ever decide to stop issuing a "logcat -c" to the device or if we are
    // confident that https://android-review.googlesource.com/#/c/119673 doesn't happen anymore.
    synchronized (myLock) {
      ExecutorService executor = myExecutors.get(device);
      // If someone keeps a reference to a device that is disconnected, executor will be null.
      if (executor != null) {
        stopReceiving(device);
        executor.submit(() -> AndroidLogcatUtils.clearLogcat(project, device));
        startReceiving(device);
      }
    }
  }

  /**
   * Add a listener which receives each line, unfiltered, that comes from the specified device. If {@code addOldLogs} is true,
   * this will also notify the listener of every log message received so far.
   * Multi-line messages will be parsed into single lines and sent with the same header.
   * For example, Log.d(tag, "Line1\nLine2") will be sent to listeners in two iterations,
   * first: "Line1" with a header, second: "Line2" with the same header.
   * Listeners are invoked in a pooled thread, and they are triggered A LOT. You should be very careful if delegating this text
   * to a UI thread. For example, don't directly invoke a runnable on the UI thread per line, but consider batching many log lines first.
   */
  public void addListener(@NotNull IDevice device, @NotNull LogLineListener listener, boolean addOldLogs) {
    synchronized (myLock) {
      if (addOldLogs && myLogBuffers.containsKey(device)) {
        for (LogCatMessage line : myLogBuffers.get(device).getMessages()) {
          listener.receiveLogLine(line);
        }
      }

      if (!myListeners.containsKey(device)) {
        myListeners.put(device, new ArrayList<>());
      }

      myListeners.get(device).add(listener);

      if (device.isOnline()) {
        startReceiving(device);
      }
    }
  }

  /**
   * @see #addListener(IDevice, LogLineListener, boolean)
   */
  public void addListener(@NotNull IDevice device, @NotNull LogLineListener listener) {
    addListener(device, listener, false);
  }

  public void removeListener(@NotNull IDevice device, @NotNull LogLineListener listener) {
    synchronized (myLock) {
      if (myListeners.containsKey(device)) {
        myListeners.get(device).remove(listener);

        if (myListeners.get(device).isEmpty()) {
          stopReceiving(device);
        }
      }
    }
  }

  @Override
  public void deviceConnected(@NotNull IDevice device) {
    if (device.isOnline()) {
      // TODO Evaluate if we really need to start getting logs as soon as we connect, or whether a connect would suffice.
      startReceiving(device);
    }
  }

  @Override
  public void deviceDisconnected(@NotNull IDevice device) {
    disconnect(device);
  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {
    if (device.isOnline()) {
      startReceiving(device);
    }
    else {
      disconnect(device);
    }
  }

  @Override
  public void dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(this);
    synchronized (myLock) {
      for (AndroidLogcatReceiver receiver : myLogReceivers.values()) {
        receiver.cancel();
      }
    }
  }
}
