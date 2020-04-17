/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.emulator.control.VmRunState
import com.android.tools.idea.concurrency.AndroidIoManager
import com.android.tools.idea.emulator.ConfigurationOverrider.getDefaultConfiguration
import com.google.common.collect.ImmutableSet
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil.parseInt
import com.intellij.util.Alarm
import gnu.trove.TObjectLongHashMap
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min
import kotlin.streams.toList

/**
 * Keeps track of Android Emulators running on the local machine under the current user account.
 */
class RunningEmulatorCatalog : Disposable.Parent {
  // TODO: Use WatchService instead of polling.
  @Volatile var emulators: Set<EmulatorController> = ImmutableSet.of()

  private val fileNamePattern = Pattern.compile("pid_\\d+.ini")
  private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  @Volatile private var isDisposing = false
  private val updateLock = Object()
  @GuardedBy("updateLock")
  private var lastUpdateStartTime: Long = 0
  @GuardedBy("updateLock")
  private var lastUpdateDuration: Long = 0
  @GuardedBy("updateLock")
  private var nextScheduledUpdateTime: Long = Long.MAX_VALUE
  @GuardedBy("updateLock")
  private var listeners: List<Listener> = emptyList()
  @GuardedBy("updateLock")
  private val updateIntervalsByListener = TObjectLongHashMap<Listener>()
  /** Long.MAX_VALUE means no updates. A negative value means that the update interval needs to be calculated. */
  @GuardedBy("updateLock")
  private var updateInterval: Long = Long.MAX_VALUE
  @GuardedBy("updateLock")
  private var pendingFutures: MutableList<SettableFuture<Set<EmulatorController>>> = mutableListOf()

  /**
   * Adds a listener that will be notified when new Emulators start and running Emulators shut down.
   * The [updateIntervalMillis] parameter determines the level of data freshness required by the listener.
   *
   * @param listener the listener to be notified
   * @param updateIntervalMillis a positive number of milliseconds
   */
  @AnyThread
  fun addListener(listener: Listener, updateIntervalMillis: Int) {
    require(updateIntervalMillis > 0)
    synchronized(updateLock) {
      listeners = listeners.plus(listener)
      updateIntervalsByListener.put(listener, updateInterval)
      if (updateIntervalMillis < updateInterval) {
        updateInterval = updateIntervalMillis.toLong()
      }
      scheduleUpdate(updateInterval)
    }
  }

  /**
   * Removes a listener add by the [addListener] method.
   */
  @AnyThread
  fun removeListener(listener: Listener) {
    synchronized(updateLock) {
      listeners = listeners.minus(listener)
      val interval = updateIntervalsByListener.remove(listener)
      if (interval == updateInterval) {
        updateInterval = -1
      }
    }
  }

  private fun scheduleUpdate(delay: Long) {
    synchronized(updateLock) {
      val updateTime = System.currentTimeMillis() + delay
      // Check if an update is already scheduled soon enough.
      if (nextScheduledUpdateTime > updateTime) {
        if (nextScheduledUpdateTime != Long.MAX_VALUE) {
          alarm.cancelAllRequests()
        }
        nextScheduledUpdateTime = updateTime
        alarm.addRequest({ update() }, delay)
      }
    }
  }

  @GuardedBy("updateLock")
  private fun scheduleUpdate() {
    val delay = getUpdateInterval()
    if (delay != Long.MAX_VALUE) {
      scheduleUpdate(max(delay, min(lastUpdateDuration * 2, 1000)))
    }
  }

  @GuardedBy("updateLock")
  private fun getUpdateInterval(): Long {
    if (updateInterval < 0) {
      var value = Long.MAX_VALUE
      for (interval in updateIntervalsByListener.values) {
        value = value.coerceAtMost(interval)
      }
      updateInterval = value
    }
    return updateInterval
  }

  /**
   * Triggers an immediate update and returns a future for the updated set of running emulators.
   */
  fun updateNow(): ListenableFuture<Set<EmulatorController>> {
    synchronized(updateLock) {
      val future: SettableFuture<Set<EmulatorController>> = SettableFuture.create()
      pendingFutures.add(future)
      scheduleUpdate(0)
      return future
    }
  }

  private fun update() {
    if (isDisposing) return

    val futures: List<SettableFuture<Set<EmulatorController>>>

    synchronized(updateLock) {
      nextScheduledUpdateTime = Long.MAX_VALUE

      if (pendingFutures.isEmpty()) {
        futures = emptyList()
      }
      else {
        futures = pendingFutures
        pendingFutures = mutableListOf()
      }
    }

    try {
      val start = System.currentTimeMillis()
      val files = readRegistrationDirectory()
      val oldEmulators = emulators.associateBy { it.emulatorId }
      val newEmulators = ConcurrentHashMap<EmulatorId, EmulatorController>()
      if (files.isNotEmpty() && !isDisposing) {
        val latch = CountDownLatch(files.size)
        val executor = AndroidIoManager.getInstance().getBackgroundDiskIoExecutor()
        for (file in files) {
          executor.submit {
            var emulator: EmulatorController? = null
            var created = false
            if (!isDisposing) {
              val emulatorId = readEmulatorInfo(file)
              if (emulatorId != null && emulatorId.isEmbedded) {
                emulator = oldEmulators[emulatorId]
                if (emulator == null) {
                  emulator = EmulatorController(emulatorId, this)
                  created = true
                }
                if (!isDisposing) {
                  newEmulators[emulator.emulatorId] = emulator
                }
              }
            }

            latch.countDown()

            // Connect to the running Emulator asynchronously.
            if (emulator != null && created) {
              emulator.connect()
            }
          }
        }
        latch.await()
      }

      val removedEmulators = oldEmulators.minus(newEmulators.keys).values
      val addedEmulators = newEmulators.minus(oldEmulators.keys).values
      val listenersSnapshot: List<Listener>

      synchronized(updateLock) {
        if (isDisposing) return
        lastUpdateStartTime = start
        lastUpdateDuration = System.currentTimeMillis() - start
        emulators = ImmutableSet.copyOf(newEmulators.values)
        listenersSnapshot = listeners
        for (future in futures) {
          future.set(emulators)
        }
        if (!isDisposing) {
          scheduleUpdate()
        }
      }

      // Notify listeners.
      if (listenersSnapshot.isNotEmpty()) {
        for (emulator in removedEmulators) {
          for (listener in listenersSnapshot) {
            if (isDisposing) break
            listener.emulatorRemoved(emulator)
          }
        }
        for (emulator in addedEmulators) {
          for (listener in listenersSnapshot) {
            if (isDisposing) break
            listener.emulatorAdded(emulator)
          }
        }
      }

      // Dispose removed Emulators.
      for (emulator in removedEmulators) {
        Disposer.dispose(emulator)
      }
    }
    catch (e: IOException) {
      logger.error("Running Emulator detection failed", e)

      synchronized(updateLock) {
        for (future in futures) {
          future.setException(e)
        }
        if (!isDisposing) {
          // TODO: Implement exponential backoff for retries.
          scheduleUpdate()
        }
      }
    }
  }

  private fun readRegistrationDirectory(): List<Path> {
    return try {
      Files.list(getDefaultConfiguration().emulatorRegistrationDirectory).use {
        it.filter { fileNamePattern.matcher(it.fileName.toString()).matches() }.toList()
      }
    } catch (e: NoSuchFileException) {
      emptyList() // The registration directory hasn't been created yet.
    }
  }

  /**
   * Reads and interprets the registration file of an Emulator (pid_NNNN.ini).
   */
  private fun readEmulatorInfo(file: Path): EmulatorId? {
    var grpcPort = 0
    var grpcCertificate: String? = null
    var grpcToken: String? = null
    var avdId: String? = null
    var avdName: String? = null
    var avdFolder: Path? = null
    var serialPort = 0
    var adbPort = 0
    var commandLine = emptyList<String>()
    try {
      for (line in Files.readAllLines(file)) {
        when {
          line.startsWith("grpc.port=") -> {
            grpcPort = parseInt(line.substring("grpc.port=".length), 0)
          }
          line.startsWith("grpc.certificate=") -> {
            grpcCertificate = line.substring("grpc.certificate=".length)
          }
          line.startsWith("grpc.token=") -> {
            grpcToken = line.substring("grpc.token=".length)
          }
          line.startsWith("avd.id=") -> {
            avdId = line.substring("add.id=".length)
          }
          line.startsWith("avd.name=") -> {
            avdName = line.substring("avd.name=".length).replace('_', ' ')
          }
          line.startsWith("avd.dir=") -> {
            avdFolder = Paths.get(line.substring("add.dir=".length))
          }
          line.startsWith("port.serial=") -> {
            serialPort = parseInt(line.substring("port.serial=".length), 0)
          }
          line.startsWith("port.adb=") -> {
            adbPort = parseInt(line.substring("port.adb=".length), 0)
          }
          line.startsWith("cmdline=") -> {
            commandLine = decodeCommandLine(line.substring ("cmdline=".length))
          }
        }
      }
    }
    catch (ignore: IOException) {
    }

    if (grpcPort <= 0 || avdId == null || avdName == null || serialPort <= 0 && adbPort <= 0) {
      return null
    }

    return EmulatorId(grpcPort = grpcPort, grpcCertificate = grpcCertificate, grpcToken = grpcToken,
                      avdId = avdId, avdName = avdName, avdFolder = avdFolder,
                      serialPort = serialPort, adbPort = adbPort, commandLine = commandLine,
                      registrationFileName = file.fileName.toString())
  }

  override fun beforeTreeDispose() {
    isDisposing = true

    // Shut down all embedded Emulators.
    synchronized(updateLock) {
      for (emulator in emulators) {
        val vmRunState = VmRunState.newBuilder().setState(VmRunState.RunState.SHUTDOWN).build()
        emulator.setVmState(vmRunState)
      }
    }
  }

  override fun dispose() {
  }

  /**
   * Defines interface for an object that receives notifications when a connection to a running Emulator
   * is established or an Emulator shuts down.
   */
  interface Listener {
    /**
     * Called when a connection to a running Emulator is established. Must be quick to avoid delaying catalog updates.
     * Due to asynchronous nature of the call, it may happen after calling the [removeListener] method.
     */
    @AnyThread
    fun emulatorAdded(emulator: EmulatorController)

    /**
     * Called when an Emulator shuts down. Must be quick to avoid delaying catalog updates.
     * Due to asynchronous nature of the call, it may happen after calling the [removeListener] method.
     */
    @AnyThread
    fun emulatorRemoved(emulator: EmulatorController)
  }

  companion object {
    @JvmStatic
    fun getInstance(): RunningEmulatorCatalog {
      return ServiceManager.getService(RunningEmulatorCatalog::class.java)
    }
  }
}
