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
package com.android.tools.datastore;

import com.android.tools.datastore.profilerclient.DeviceProfilerService;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

public abstract class Poller implements RunnableFuture<Void> {

  /**
   * Delay between data requests (in nanoseconds).
   */
  public static final long POLLING_DELAY_NS = TimeUnit.MILLISECONDS.toNanos(250);

  private static Logger getLog() { return Logger.getInstance(Poller.class); }

  @NotNull
  protected final DeviceProfilerService myService;

  @NotNull
  protected final SeriesDataStore myDataStore;

  private long myPollPeriodNs;

  private CountDownLatch myRunning = new CountDownLatch(1);

  private CountDownLatch myIsDone = new CountDownLatch(1);

  public Poller(@NotNull SeriesDataStore dataStore, long pollPeriodNs) {
    myDataStore = dataStore;
    myService = myDataStore.getDeviceProfilerService();
    myPollPeriodNs = pollPeriodNs;
  }

  /**
   * Will be called upon initialization on the polling thread.
   * Implementor should throw StatusRuntimeException if there is ever an gRPC communication error.
   * @throws StatusRuntimeException
   */
  protected abstract void asyncInit() throws StatusRuntimeException;

  /**
   * Will be called upon termination on the polling thread.
   * Implementor should throw StatusRuntimeException if there is ever an gRPC communication error.
   * @throws StatusRuntimeException
   */
  protected abstract void asyncShutdown() throws StatusRuntimeException;

  /**
   * Will be called every time the poller needs to poll information from device.
   * Implementor should throw StatusRuntimeException if there is ever an gRPC communication error.
   * @throws StatusRuntimeException
   */
  protected abstract void poll() throws StatusRuntimeException;

  /**
   * Runs the polling loop and handles any gRPC exceptions with graceful failure, so
   * methods communicating with device through gRPC wouldn't have to.
   */
  @Override
  public void run() {
    try {
      asyncInit();
      while (myRunning.getCount() > 0) {
        long startTimeNs = System.nanoTime();
        poll();
        long sleepTime = Math.max(myPollPeriodNs - (System.nanoTime() - startTimeNs), 0L);
        myRunning.await(sleepTime, TimeUnit.NANOSECONDS);
      }
    }
    catch (StatusRuntimeException e) {
      // Don't do anything except log, go straight to finally block which handles this anyways.
      getLog().info("Error during gRPC communication. Poller exiting now.");
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    finally {
      try {
        asyncShutdown();
      }
      catch (StatusRuntimeException e) {
        getLog().info("Error sending shutdown signal to on-device poller.");
      }
      finally {
        // Delegate back to the EDT thread to deinitialize the UI and disconnect the profiler service.
        SwingUtilities.invokeLater(() -> myDataStore.getEventDispatcher().getMulticaster().profilerServerDisconnected());
        myIsDone.countDown();
      }
    }
  }

  public void stop() {
    cancel(true);
    try {
      get();
    }
    catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    myRunning.countDown();
    return true;
  }

  @Override
  public boolean isCancelled() {
    return myRunning.getCount() == 0;
  }

  @Override
  public boolean isDone() {
    return myIsDone.getCount() == 0;
  }

  @Override
  public Void get() throws InterruptedException {
    myIsDone.await();
    return null;
  }

  @Override
  public Void get(long timeout, TimeUnit unit) throws InterruptedException {
    myIsDone.await(timeout, unit);
    return null;
  }
}
