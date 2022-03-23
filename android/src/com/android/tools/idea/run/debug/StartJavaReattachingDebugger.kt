/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.debug

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.AndroidProcessHandler
import com.android.tools.idea.testartifacts.instrumented.orchestrator.MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.concurrency.Promise

/**
 * Starts JAVA debug session that attaches to new clients, ready for debug, if their app id is in [applicationIds].
 *
 * [masterAndroidProcessName] process name that should be destroyed when the stop button is pressed or
 * execution will be finished when [masterAndroidProcessName] is destroyed. Example: [MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME]
 */
@WorkerThread
fun startJavaReattachingDebugger(
  project: Project,
  device: IDevice,
  masterAndroidProcessName: String,
  applicationIds: Set<String>,
  environment: ExecutionEnvironment,
  consoleViewToReuse: ConsoleView? = null,
  onDebugProcessStarted: (() -> Unit)? = null,
): Promise<XDebugSessionImpl> {
  check(StudioFlags.NEW_EXECUTION_FLOW_FOR_JAVA_DEBUGGER.get()) { "New execution flow must be enabled." }

  fun startJavaSession(client: Client, onDebugProcessStarted: (() -> Unit)?) =
    attachJavaDebuggerToClient(project, client, environment, consoleViewToReuse, onDebugProcessStarted)

  return startReattachingDebugger(project, device, masterAndroidProcessName, applicationIds, ::startJavaSession, onDebugProcessStarted)
}


/**
 * Starts debug session via [startSession] that attaches to new clients, ready for debug, if their app id is in [applicationIds].
 *
 * Debug session exists until [masterAndroidProcessName] is alive.
 * Returns [Promise<XDebugSessionImpl>] for the first client, for consecutive clients replaces old debugger tab with a new one.
 */
@WorkerThread
private fun startReattachingDebugger(
  project: Project,
  device: IDevice,
  masterAndroidProcessName: String,
  applicationIds: Set<String>,
  startSession: (Client, (() -> Unit)?) -> Promise<XDebugSessionImpl>,
  onDebugProcessStarted: (() -> Unit)? = null
): Promise<XDebugSessionImpl> {
  // We wait for a client in this method, it shouldn't be on EDT.
  ApplicationManager.getApplication().assertIsNonDispatchThread()

  val LOG = Logger.getInstance("startJavaReattachingDebugger")

  val masterProcessHandler = AndroidProcessHandler(project, masterAndroidProcessName)
  masterProcessHandler.addTargetDevice(device)

  // We wait for the first client outside [reattachingListener] because there is case when client is already waiting for debug before we add
  // [reattachingListener].
  val client = waitForClientReadyForDebug(device, applicationIds, 200)
  fun startSessionFinal(client: Client, onDebugProcessStarted: (() -> Unit)?) = startSession(client, onDebugProcessStarted)
    .onSuccess { session ->
      session.runContentDescriptor.processHandler!!.addProcessListener(object : ProcessAdapter() {
        override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
          if (willBeDestroyed) {
            // user pressed stop button.
            masterProcessHandler.destroyProcess()
          }
        }
      })
    }
    .onError {
      masterProcessHandler.destroyProcess()
    }

  val reattachingListener = ReattachingDebuggerListener(project, masterProcessHandler, applicationIds, ::startSessionFinal)
  LOG.debug("Add reattaching listener")
  AndroidDebugBridge.addClientChangeListener(reattachingListener)

  masterProcessHandler.addProcessListener(object : ProcessAdapter() {
    override fun processTerminated(event: ProcessEvent) {
      // Stop the reattaching debug connector task as soon as the master process is terminated.
      LOG.debug("Delete reattaching listener")
      AndroidDebugBridge.removeClientChangeListener(reattachingListener)
    }
  })
  masterProcessHandler.startNotify()
  return startSessionFinal(client, onDebugProcessStarted)
}

/**
 * While [masterProcessHandler] is not terminated calls [startSession] for clients with a process name in [applicationIds].
 */
private class ReattachingDebuggerListener(
  private val project: Project,
  private val masterProcessHandler: AndroidProcessHandler,
  private val applicationIds: Set<String>,
  private val startSession: (Client, (() -> Unit)?) -> Promise<XDebugSessionImpl>
) : AndroidDebugBridge.IClientChangeListener {

  init {
    Disposer.register(project, { AndroidDebugBridge.removeClientChangeListener(this) })
  }
  companion object {
    /**
     * Changes to [Client] instances that mean a new debugger should be connected.
     *
     * The target application can either:
     * 1. Match our target name, and become available for debugging.
     * 2. Be available for debugging, and suddenly have its name changed to match.
     */
    const val CHANGE_MASK = Client.CHANGE_DEBUGGER_STATUS or Client.CHANGE_NAME
  }

  private val LOG = Logger.getInstance(this::class.java)

  private fun isClientForDebug(client: Client, changeMask: Int): Boolean {
    val clientDescription = client.clientData.clientDescription
    if (applicationIds.contains(clientDescription)) {
      if (changeMask and CHANGE_MASK != 0 && client.clientData.debuggerConnectionStatus == ClientData.DebuggerStatus.WAITING) {
        LOG.debug("Client waiting for debugger, PORT: ${client.debuggerListenPort}, PID: ${client.clientData.pid}\n")
        return true
      }
    }
    return false
  }

  private fun handleError(e: Throwable) {
    masterProcessHandler.destroyProcess()
    if (e is ExecutionException) {
      showError(project, e, "Debugging ${masterProcessHandler.targetApplicationId}")
    }
    else {
      LOG.error(e)
    }
  }

  override fun clientChanged(client: Client, changeMask: Int) {
    if (isClientForDebug(client, changeMask)) {
      if (!masterProcessHandler.isProcessTerminating && !masterProcessHandler.isProcessTerminated) {
        LOG.debug("Attaching debugger to a client, PORT: ${client.debuggerListenPort}")
        startSession(client, null)
          .onSuccess { session ->
            runInEdt {
              try {
                session.showSessionTab()
              }
              catch (e: Throwable) {
                handleError(e)
              }
            }
          }
      }
    }
  }
}