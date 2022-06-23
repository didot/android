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
package com.android.tools.idea.logcat.devices

import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceInfo
import com.android.adblib.DevicePropertyNames.RO_BOOT_QEMU_AVD_NAME
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_RELEASE
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_SDK
import com.android.adblib.DevicePropertyNames.RO_KERNEL_QEMU_AVD_NAME
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MANUFACTURER
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MODEL
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState.ONLINE
import com.android.adblib.shellAsText
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.logcat.devices.DeviceEvent.Added
import com.android.tools.idea.logcat.devices.DeviceEvent.StateChanged
import com.android.tools.idea.logcat.devices.DeviceEvent.TrackingReset
import com.android.tools.idea.logcat.util.LOGGER
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.CoroutineContext

private val ADB_TIMEOUT = Duration.ofMillis(1000)

/**
 * An implementation of IDeviceComboBoxDeviceTracker that uses an [AdbLibSession]
 */
internal class DeviceComboBoxDeviceTracker(
  project: Project,
  private val preexistingDevice: Device?,
  private val adbSession: AdbLibSession = AdbLibService.getSession(project),
  private val coroutineContext: CoroutineContext = Dispatchers.IO,
) : IDeviceComboBoxDeviceTracker {

  override suspend fun trackDevices(): Flow<DeviceEvent> {
    return flow {
      while (true) {
        // TODO(b/228224334): This should be handled internally by AdbLib
        try {
          trackDevicesInternal()
        }
        catch (e: IOException) {
          LOGGER.info("Device tracker exception, restarting it...", e)
          emit(TrackingReset(e))
          continue
        }
        break
      }
    }.flowOn(coroutineContext)
  }

  private suspend fun FlowCollector<DeviceEvent>.trackDevicesInternal() {
    val onlineDevicesBySerial = mutableMapOf<String, Device>()
    val allDevicesById = mutableMapOf<String, Device>()

    // Initialize state by reading all current devices
    coroutineScope {
      adbSession.hostServices.devices().filter { it.isOnline() }.map { async { it.toDevice() } }.awaitAll().forEach {
        onlineDevicesBySerial[it.serialNumber] = it
        allDevicesById[it.deviceId] = it
        emit(Added(it))
      }
    }

    // Add the preexisting device.
    if (preexistingDevice != null && !allDevicesById.containsKey(preexistingDevice.deviceId)) {
      onlineDevicesBySerial[preexistingDevice.serialNumber] = preexistingDevice
      allDevicesById[preexistingDevice.deviceId] = preexistingDevice
      emit(Added(preexistingDevice))
    }

    // Track devices changes:
    // There are 3 distinct cases:
    // 1. A device that has not been seen before comes online -> callback.deviceAdded()
    // 2. A device that was seen before and is now offline comes online -> callback.deviceStateChanged()
    // 3. A device that is currently online goes offline -> callback.deviceStateChanged()
    adbSession.hostServices.trackDevices().collect { deviceList ->
      for (deviceInfo in deviceList) {
        val serialNumber = deviceInfo.serialNumber
        val isOnline = deviceInfo.isOnline()
        if (isOnline) {
          if (onlineDevicesBySerial.containsKey(serialNumber)) {
            continue
          }

          val deviceId = deviceInfo.getDeviceId()
          val existingDevice = allDevicesById[deviceId]
          if (existingDevice != null) {
            val copy = if (existingDevice.isEmulator) {
              existingDevice.copy(isOnline = true, serialNumber = serialNumber)
            }
            else {
              val properties = deviceInfo.getProperties(RO_BUILD_VERSION_RELEASE, RO_BUILD_VERSION_SDK)
              existingDevice.copy(
                isOnline = true,
                release = properties.getValue(RO_BUILD_VERSION_RELEASE).toIntOrNull() ?: 0,
                sdk = properties.getValue(RO_BUILD_VERSION_SDK).toIntOrNull() ?: 0)

            }
            onlineDevicesBySerial[serialNumber] = copy
            allDevicesById[serialNumber] = copy
            emit(StateChanged(copy))
          }
          else {
            val newDevice = deviceInfo.toDevice()
            allDevicesById[deviceId] = newDevice
            onlineDevicesBySerial[serialNumber] = newDevice
            emit(Added(newDevice))
          }
        }
        else {
          val device = onlineDevicesBySerial[serialNumber]
          if (device != null) {
            val copy = device.copy(isOnline = false)
            onlineDevicesBySerial.remove(serialNumber)
            allDevicesById[device.serialNumber] = copy
            emit(StateChanged(copy))
          }
        }
      }
    }
  }

  private suspend fun DeviceInfo.toDevice(): Device {
    if (serialNumber.startsWith("emulator-")) {
      val properties = getProperties(RO_BUILD_VERSION_RELEASE, RO_BUILD_VERSION_SDK, RO_BOOT_QEMU_AVD_NAME, RO_KERNEL_QEMU_AVD_NAME)
      return Device.createEmulator(
        serialNumber,
        isOnline = true,
        properties.getValue(RO_BUILD_VERSION_RELEASE).toIntOrNull() ?: 0,
        properties.getValue(RO_BUILD_VERSION_SDK).toIntOrNull() ?: 0,
        getAvdName(properties))
    }
    else {
      val properties = getProperties(RO_BUILD_VERSION_RELEASE, RO_BUILD_VERSION_SDK, RO_PRODUCT_MANUFACTURER, RO_PRODUCT_MODEL)
      return Device.createPhysical(
        serialNumber,
        isOnline = true,
        properties.getValue(RO_BUILD_VERSION_RELEASE).toIntOrNull() ?: 0,
        properties.getValue(RO_BUILD_VERSION_SDK).toIntOrNull() ?: 0,
        properties.getValue(RO_PRODUCT_MANUFACTURER),
        properties.getValue(RO_PRODUCT_MODEL))
    }
  }

  private fun DeviceInfo.getAvdName(properties: Map<String, String>): String =
    properties.getValue(RO_BOOT_QEMU_AVD_NAME).ifBlank { properties.getValue(RO_KERNEL_QEMU_AVD_NAME) }.ifBlank {
      LOGGER.warn("Emulator has no avd_name property")
      serialNumber
    }

  private suspend fun DeviceInfo.getDeviceId(): String {
    return when {
      serialNumber.startsWith("emulator-") -> getAvdName(getProperties(RO_BOOT_QEMU_AVD_NAME, RO_KERNEL_QEMU_AVD_NAME))
      else -> serialNumber
    }
  }

  private suspend fun DeviceInfo.getProperties(vararg properties: String): Map<String, String> {
    val selector = DeviceSelector.fromSerialNumber(serialNumber)
    val command = properties.joinToString(" ; ") { "getprop $it" }
    val lines = adbSession.deviceServices.shellAsText(selector, command, commandTimeout = ADB_TIMEOUT).split("\n")
    return properties.withIndex().associate { it.value to lines[it.index].trimEnd('\r') }
  }
}

private fun DeviceInfo.isOnline(): Boolean = deviceState == ONLINE
