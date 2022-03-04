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
package com.android.tools.idea.device

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.utils.Base128InputStream
import com.android.utils.Base128OutputStream
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Controls the device by sending control messages to it.
 */
class DeviceController(
  disposableParent: Disposable,
  controlChannel: SuspendingSocketChannel
) : Disposable {

  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(javaClass.simpleName, 1)
  private val outputStream = Base128OutputStream(newOutputStream(controlChannel, CONTROL_MSG_BUFFER_SIZE))
  private val suspendingInputStream = newInputStream(controlChannel, CONTROL_MSG_BUFFER_SIZE)
  private val inputStream = Base128InputStream(suspendingInputStream)
  private val receiverScope = AndroidCoroutineScope(this)
  private val deviceClipboardListeners: MutableList<DeviceClipboardListener> = ContainerUtil.createLockFreeCopyOnWriteList()

  init {
    Disposer.register(disposableParent, this)
    startReceivingMessages()
  }

  fun sendControlMessage(message: ControlMessage) {
    executor.submit {
      send(message)
    }
  }

  private fun send(message:ControlMessage) {
    message.serialize(outputStream)
    outputStream.flush()
  }

  override fun dispose() {
    executor.shutdown()
    executor.awaitTermination(2, TimeUnit.SECONDS)
  }

  fun addDeviceClipboardListener(listener: DeviceClipboardListener) {
    deviceClipboardListeners.add(listener)
  }

  fun removeDeviceClipboardListener(listener: DeviceClipboardListener) {
    deviceClipboardListeners.remove(listener)
  }

  private fun startReceivingMessages() {
    receiverScope.launch {
      while (true) {
        suspendingInputStream.waitForData(1)
        when (val message = ControlMessage.deserialize(inputStream)) {
          is ClipboardChangedMessage -> onDeviceClipboardChanged(message)
          else -> thisLogger().error("Unexpected type of a received message: ${message.type}")
        }
      }
    }
  }

  private fun onDeviceClipboardChanged(message: ClipboardChangedMessage) {
    val text = message.text
    for (listener in deviceClipboardListeners) {
      listener.onDeviceClipboardChanged(text)
    }
  }

  interface DeviceClipboardListener {
    fun onDeviceClipboardChanged(text: String)
  }
}

private const val CONTROL_MSG_BUFFER_SIZE = 4096