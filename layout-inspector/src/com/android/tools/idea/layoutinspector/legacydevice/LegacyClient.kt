/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.legacydevice

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.ChunkHandler
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.HandleViewDebug
import com.android.ddmlib.IDevice
import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.layoutinspector.LayoutInspectorPreferredProcess
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Common
import com.google.common.collect.Lists
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

private const val MAX_RETRY_COUNT = 60

/**
 * [InspectorClient] that supports pre-api 28 devices.
 * Since it doesn't use [TransportService], some relevant event listeners are manually fired.
 */
class LegacyClient(private val project: Project) : InspectorClient {

  var selectedWindow: String? = null
  var selectedClient: Client? = null

  override var selectedStream: Common.Stream = Common.Stream.getDefaultInstance()
    private set
  override var selectedProcess: Common.Process = Common.Process.getDefaultInstance()
    private set

  override val isConnected: Boolean
    get() = selectedClient?.isValid == true

  override val isCapturing = false

  private val processChangedListeners: MutableList<() -> Unit> = ContainerUtil.createConcurrentList()

  private val processToClient: MutableMap<Common.Process, Client> = mutableMapOf()

  override val treeLoader = LegacyTreeLoader

  val eventListeners: MutableMap<Common.Event.EventGroupIds, MutableList<(Any) -> Unit>> = mutableMapOf()

  override fun registerProcessChanged(callback: () -> Unit) {
    processChangedListeners.add(callback)
  }

  override fun loadProcesses(): Map<Common.Stream, List<Common.Process>> {
    var maybeDebugBridge: AndroidDebugBridge? = null
    ApplicationManager.getApplication().invokeAndWait { maybeDebugBridge = AndroidSdkUtils.getDebugBridge(project) }
    val debugBridge = maybeDebugBridge ?: return mapOf()
    val result = mutableMapOf<Common.Stream, List<Common.Process>>()
    for (iDevice in debugBridge.devices) {
      val deviceProto = Common.Device.newBuilder().run {
        apiLevel = iDevice.version.apiLevel
        iDevice.version.codename?.let { codename = it }
        featureLevel = iDevice.version.featureLevel
        isEmulator = iDevice.isEmulator
        manufacturer = iDevice.getProperty(IDevice.PROP_DEVICE_MANUFACTURER)
        model = iDevice.avdName ?: iDevice.getProperty(IDevice.PROP_DEVICE_MODEL)
        serial = iDevice.serialNumber
        version = SdkVersionInfo.getVersionString(iDevice.version.apiLevel)
        build()
      }
      val stream = Common.Stream.newBuilder().run {
        device = deviceProto
        streamId = iDevice.hashCode().toLong()
        build()
      }

      val processes = iDevice.clients
        .filter { it.clientData.hasFeature(ClientData.FEATURE_VIEW_HIERARCHY) }
        .sortedBy { it.clientData.clientDescription }
        .map { client ->
          Common.Process.newBuilder().run {
            abiCpuArch = client.clientData.abi
            name = client.clientData.packageName
            pid = client.clientData.pid
            build().also { processToClient[it] = client }
          }
        }
      result[stream] = processes
    }

    return result
  }

  override fun attachIfSupported(preferredProcess: LayoutInspectorPreferredProcess): Boolean {
    ApplicationManager.getApplication().executeOnPooledThread { attachWithRetry(preferredProcess, 0) }
    return true
  }

  // TODO: It might be possible for attach() to be successful here before the process is actually ready to be inspected, causing the later
  // call to LegacyTreeLoader.capture to fail. If this is the case, this method should be changed to ensure the capture will work before
  // declaring success.
  // If it's not the case, this code is duplicated from DefaultClient and so should be factored out somewhere.
  private fun attachWithRetry(preferredProcess: LayoutInspectorPreferredProcess, timesAttempted: Int) {
    val processesMap = loadProcesses()
    for ((stream, processes) in processesMap) {
      if (preferredProcess.isDeviceMatch(stream.device)) {
        for (process in processes) {
          if (process.name == preferredProcess.packageName) {
            try {
              attach(stream, process)
              return
            }
            catch (ex: StatusRuntimeException) {
              // If the process is not found it may still be loading. Retry!
              if (ex.status.code != Status.Code.NOT_FOUND) {
                throw ex
              }
            }
          }
        }
      }
    }
    if (timesAttempted < MAX_RETRY_COUNT) {
      JobScheduler.getScheduler().schedule({ attachWithRetry(preferredProcess, timesAttempted + 1) }, 1, TimeUnit.SECONDS)
    }
    return
  }

  override fun attach(stream: Common.Stream, process: Common.Process) {
    val client = processToClient[process] ?: return
    selectedClient = client
    selectedWindow = ListViewRootsHandler().getWindows(client, 5, TimeUnit.SECONDS)[0]
    selectedProcess = process
    selectedStream = stream

    AndroidDebugBridge.addDeviceChangeListener(object: AndroidDebugBridge.IDeviceChangeListener {
      override fun deviceConnected(device: IDevice) {}

      override fun deviceDisconnected(device: IDevice) {}

      override fun deviceChanged(device: IDevice, changeMask: Int) {
        if (changeMask and IDevice.CHANGE_CLIENT_LIST > 0 && selectedClient?.isValid != true) {
          disconnect()
        }
      }
    })

    eventListeners[Common.Event.EventGroupIds.COMPONENT_TREE]?.forEach { it(Any()) }
  }

  override fun disconnect() {
    if (selectedClient != null) {
      selectedWindow = null
      selectedClient = null
      selectedProcess = Common.Process.getDefaultInstance()
      selectedStream = Common.Stream.getDefaultInstance()
      processChangedListeners.forEach { it() }
    }
  }

  override fun execute(command: LayoutInspectorProto.LayoutInspectorCommand) {}

  override fun register(groupId: Common.Event.EventGroupIds, callback: (Any) -> Unit) {
    eventListeners.getOrPut(groupId, { mutableListOf() }).add(callback)
  }

  private class ListViewRootsHandler :
    HandleViewDebug.ViewDumpHandler(HandleViewDebug.CHUNK_VULW) {

    private val viewRoots = Lists.newCopyOnWriteArrayList<String>()

    override fun handleViewDebugResult(data: ByteBuffer) {
      val nWindows = data.int

      for (i in 0 until nWindows) {
        val len = data.int
        viewRoots.add(ChunkHandler.getString(data, len))
      }
    }

    @Throws(IOException::class)
    fun getWindows(c: Client, timeout: Long, unit: TimeUnit): List<String> {
      HandleViewDebug.listViewRoots(c, this)
      waitForResult(timeout, unit)
      return viewRoots
    }
  }

}