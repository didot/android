/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.configuration.execution

import com.android.ddmlib.IDevice
import com.android.ddmlib.NullOutputReceiver
import com.intellij.execution.process.ProcessHandler
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.TimeUnit

/**
 * Subclasses should implement [destroyProcessOnDevice]
 */
abstract class AndroidProcessHandlerForDevices : ProcessHandler() {
  private val DEBUG_SURFACE_CLEAR =
    "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'clear-debug-app'"
  private val ACTIVITY_MANAGER_CLEAR = "am clear-debug-app"
  open val isDebug = false

  val devices = mutableListOf<IDevice>()

  open fun addDevice(device: IDevice) {
    devices.add(device)
  }

  final override fun destroyProcessImpl() {
    AppExecutorUtil.getAppExecutorService().submit {
      devices.forEach { destroyProcessOnDevice(it) }
      notifyProcessTerminated(0)
    }
  }

  abstract fun stopSurface(device: IDevice)

  private fun stopDebugApp(device: IDevice) {
    val dummyReceiver = NullOutputReceiver()
    device.executeShellCommand(DEBUG_SURFACE_CLEAR, dummyReceiver, 5, TimeUnit.SECONDS)
    device.executeShellCommand(ACTIVITY_MANAGER_CLEAR, dummyReceiver, 5, TimeUnit.SECONDS)
  }

  private fun destroyProcessOnDevice(device: IDevice) {
    stopSurface(device)
    if (isDebug) {
      stopDebugApp(device)
    }
  }
  override fun detachProcessImpl() = notifyProcessDetached()
  override fun detachIsDefault() = false
  override fun getProcessInput() = null
}


