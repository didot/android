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
package com.android.tools.idea.retention.actions

import com.android.annotations.concurrency.Slow
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.emulator.control.SnapshotPackage
import com.android.tools.idea.emulator.DummyStreamObserver
import com.android.tools.idea.emulator.EmulatorController
import com.android.tools.idea.emulator.RunningEmulatorCatalog
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.run.editor.AndroidDebugger
import com.android.tools.idea.run.editor.AndroidJavaDebugger
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_FILE_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_ID_KEY
import com.android.tools.idea.testartifacts.instrumented.PACKAGE_NAME_KEY
import com.android.tools.idea.testartifacts.instrumented.RETENTION_AUTO_CONNECT_DEBUGGER_KEY
import com.android.tools.idea.testartifacts.instrumented.RETENTION_ON_FINISH_KEY
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.ClientResponseObserver
import org.jetbrains.android.actions.AndroidConnectDebuggerAction
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch

// Const values for the progress bar
private const val PUSH_SNAPSHOT_FRACTION = 0.6
private const val LOAD_SNAPSHOT_FRACTION = 0.7
private const val CLIENTS_READY_FRACTION = 0.8
private const val DEBUGGER_CONNECTED_FRACTION = 0.9

/**
 * An action to load an Android Test Retention snapshot.
 */
class FindEmulatorAndSetupRetention : AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val dataContext = event.dataContext
    val project = dataContext.getData<Project>(CommonDataKeys.PROJECT) ?: return
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(project, "Loading retained test failure", true) {
        override fun onFinished() {
          dataContext.getData(RETENTION_ON_FINISH_KEY)?.run()
        }

        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = false
          indicator.fraction = 0.0
          // TODO(b/154140562): we currently don't have the emulator ID, so just use the first running emulator.
          val catalog = RunningEmulatorCatalog.getInstance()
          val emulators = catalog.emulators
          if (emulators != null) {
            val emulatorController = RunningEmulatorCatalog.getInstance().emulators.iterator().next()
            if (emulatorController.connectionState != EmulatorController.ConnectionState.CONNECTED) {
              emulatorController.connect()
            }
            val emulatorSerialString = "emulator-${emulatorController.emulatorId.serialPort}"
            val snapshotId = dataContext.getData(EMULATOR_SNAPSHOT_ID_KEY) ?: return
            val snapshotFile = dataContext.getData(EMULATOR_SNAPSHOT_FILE_KEY) ?: return
            val shouldAttachDebugger = dataContext.getData(RETENTION_AUTO_CONNECT_DEBUGGER_KEY) ?: false
            val packageName = dataContext.getData(PACKAGE_NAME_KEY) ?: return
            if (!shouldAttachDebugger) {
              emulatorController.pushAndLoadSync(snapshotId, snapshotFile, indicator)
              return
            }

            val devices = AndroidDebugBridge.getBridge()?.devices
            if (devices != null) {
              for (device in devices) {
                if (device.serialNumber == emulatorSerialString) {
                  device.getClient(packageName)?.kill()
                }
              }
            }
            var adbDevice: IDevice? = null
            val deviceReadySignal = CountDownLatch(1)
            // After loading a snapshot, the following events will happen:
            // Device disconnects -> device reconnects-> device client list changes
            // But the device client list changes callback does not really catch all client changes.
            val deviceChangeListener: AndroidDebugBridge.IDeviceChangeListener = object : AndroidDebugBridge.IDeviceChangeListener {
              override fun deviceDisconnected(device: IDevice) {}

              override fun deviceConnected(device: IDevice) {
                if (emulatorSerialString == device.serialNumber) {
                  adbDevice = device
                  deviceReadySignal.countDown()
                  AndroidDebugBridge.removeDeviceChangeListener(this)
                }
              }

              override fun deviceChanged(device: IDevice, changeMask: Int) { }
            }
            AndroidDebugBridge.addDeviceChangeListener(deviceChangeListener)
            try {
              if (!emulatorController.pushAndLoadSync(snapshotId, snapshotFile, indicator)) {
                LOG.warn("Failed to import snapshots.")
                return
              }
              indicator.fraction = LOAD_SNAPSHOT_FRACTION
              ProgressIndicatorUtils.awaitWithCheckCanceled(deviceReadySignal)
            }
            catch (exception: Throwable) {
              AndroidDebugBridge.removeDeviceChangeListener(deviceChangeListener)
              throw exception
            }
            if (adbDevice == null) {
              // TODO(b/156287594): pop up error dialogues
              LOG.warn("Failed to connect to device.")
              return
            }

            // Check if the ddm client is ready.
            // Alternatively we can register a callback to check clients. But the IDeviceChangeListener does not really deliver all new
            // client events. Also, because of the implementation of ProgressIndicatorUtils.awaitWithCheckCanceled, it is going to poll
            // and wait even if we use callbacks.
            ProgressIndicatorUtils.awaitWithCheckCanceled {
              if (adbDevice!!.getClient(packageName) == null) {
                Thread.sleep(10)
                false
              } else {
                true
              }
            }
            indicator.fraction = CLIENTS_READY_FRACTION

            val debugSessionReadySignal = CountDownLatch(1)
            val messageBusConnection = project.messageBus.connect()
            try {
              messageBusConnection.subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
                override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
                  if (currentSession != null) {
                    messageBusConnection.disconnect()
                    debugSessionReadySignal.countDown()
                  }
                }
              })
              connectDebugger(adbDevice!!, dataContext)
              // Wait for debug session ready.
              ProgressIndicatorUtils.awaitWithCheckCanceled(debugSessionReadySignal)
              indicator.fraction = DEBUGGER_CONNECTED_FRACTION
            }
            catch (exception: Exception) {
              messageBusConnection.disconnect()
              throw exception
            }
            val currentSession = XDebuggerManager.getInstance(getProject()).currentSession!!
            val pauseSignal = CountDownLatch(1)
            // Pause the current session. It turns out that the session can only be paused after some setting updates. So we need to send
            // pause commands in settingsChanged() as well as in the current thread, in case it is updated before we set up the callbacks.
            currentSession.addSessionListener(object : XDebugSessionListener {
              override fun sessionPaused() {
                currentSession.removeSessionListener(this)
                pauseSignal.countDown()
              }

              override fun settingsChanged() {
                currentSession.pause()
              }
            })
            currentSession.pause()
            ProgressIndicatorUtils.awaitWithCheckCanceled(pauseSignal)
          }
        }
      })
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    val snapshotId = event.dataContext.getData(EMULATOR_SNAPSHOT_ID_KEY)
    val snapshotFile = event.dataContext.getData(EMULATOR_SNAPSHOT_FILE_KEY)
    event.presentation.isEnabledAndVisible = (snapshotId != null && snapshotFile != null)
  }
}

@Slow
private fun connectDebugger(device: IDevice, dataContext: DataContext) {
  val packageName = dataContext.getData(PACKAGE_NAME_KEY)
  val client = device.getClient(packageName)
  if (client == null) {
    LOG.warn("Cannot connect to ${packageName}")
    return
  }
  val project = dataContext.getData<Project>(CommonDataKeys.PROJECT) ?: return
  val androidDebugger = AndroidDebugger.EP_NAME.extensions.find {
    it.supportsProject(project) && it.id == AndroidJavaDebugger.ID
  }
  if (androidDebugger == null) {
    LOG.warn("Cannot find java debuggers.")
    return
  }
  AndroidConnectDebuggerAction.closeOldSessionAndRun(project, androidDebugger, client, null)
}

/**
 * Pushes a snapshot file into the emulator and load it.
 *
 * @param snapshotId a snapshot name which must match the snapshot in the snapshot file.
 * @param snapshotFile a file of an exported snapshot.
 *
 * @return true if succeeds.
 */
@Slow
private fun EmulatorController.pushAndLoadSync(snapshotId: String, snapshotFile: File, indicator: ProgressIndicator): Boolean {
  return pushSnapshotSync(snapshotId, snapshotFile, indicator) && loadSnapshotSync(snapshotId)
}

/**
 * Loads a snapshot in the emulator.
 *
 * @param snapshotId a name of a snapshot in the emulator.
 *
 * @return true if succeeds.
 */
@Slow
private fun EmulatorController.loadSnapshotSync(snapshotId: String): Boolean {
  val doneSignal = CountDownLatch(1)
  var succeeded = true
  loadSnapshot(snapshotId, object : DummyStreamObserver<SnapshotPackage>() {
    override fun onCompleted() {
      doneSignal.countDown()
    }

    override fun onError(throwable: Throwable) {
      succeeded = false
      doneSignal.countDown()
    }
  })
  ProgressIndicatorUtils.awaitWithCheckCanceled(doneSignal)
  return succeeded
}

/**
 * Pushes a snapshot file into the emulator.
 *
 * @param snapshotId a snapshot name which must match the snapshot in the snapshot file.
 * @param snapshotFile a file of an exported snapshot.
 *
 * @return true if succeeds.
 */
@Slow
@Throws(IOException::class)
private fun EmulatorController.pushSnapshotSync(snapshotId: String, snapshotFile: File, indicator: ProgressIndicator): Boolean {
  snapshotFile.inputStream().use { inputStream ->
    val fileSize = snapshotFile.length()
    var totalBytesSent = 0L
    var succeeded = true
    val doneSignal = CountDownLatch(1)
    pushSnapshot(object : ClientResponseObserver<SnapshotPackage, SnapshotPackage> {
      override fun onCompleted() {
        doneSignal.countDown()
      }

      override fun onError(throwable: Throwable) {
        succeeded = false
        doneSignal.countDown()
      }

      override fun beforeStart(clientCallStreamObserver: ClientCallStreamObserver<SnapshotPackage>) {
        var snapshotIdSent = false
        var completionRequested = false
        val bytes = ByteArray(2 * 1024 * 1024)
        clientCallStreamObserver.setOnReadyHandler {
          // https://grpc.github.io/grpc-java/javadoc/io/grpc/stub/CallStreamObserver.html#setOnReadyHandler-java.lang.Runnable-
          // Get rid of "spurious" notifications first.
          if (!clientCallStreamObserver.isReady) {
            return@setOnReadyHandler
          }
          if (!snapshotIdSent) {
            clientCallStreamObserver.onNext(SnapshotPackage.newBuilder().setSnapshotId(snapshotId).build())
            snapshotIdSent = true;
          }
          var bytesRead = 0;
          while (clientCallStreamObserver.isReady) {
            bytesRead = inputStream.read(bytes)
            if (bytesRead <= 0) {
              break
            }
            clientCallStreamObserver.onNext(SnapshotPackage.newBuilder().setPayload(ByteString.copyFrom(bytes, 0, bytesRead)).build())
            totalBytesSent += bytesRead
            indicator.fraction = totalBytesSent.toDouble() / fileSize * PUSH_SNAPSHOT_FRACTION
          }
          if (bytesRead < 0 && !completionRequested) {
            completionRequested = true
            clientCallStreamObserver.onCompleted()
          }
        }
      }

      override fun onNext(snapshotPackage: SnapshotPackage) {}
    })

    // Slow
    ProgressIndicatorUtils.awaitWithCheckCanceled(doneSignal)
    return succeeded
  }
}

private val LOG = logger<FindEmulatorAndSetupRetention>()
