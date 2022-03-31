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

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.devices.DeviceEvent.Added
import com.android.tools.idea.logcat.devices.DeviceEvent.StateChanged
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes.ERROR_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import icons.StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
import icons.StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import javax.swing.JList

/**
 * A [ComboBox] for selecting a device.
 *
 * The items are populated by devices as they come online. When a device goes offline, it's not removed from the combo, rather, it's
 * representation changes to reflect its state.
 *
 * An initial device can optionally be provided. This initial device will become the selected item. If no initial device is provided, the
 * first device added will be selected.
 */
internal class DeviceComboBox @TestOnly internal constructor(
  parentDisposable: Disposable,
  private val initialDevice: Device?,
  private val deviceTracker: IDeviceComboBoxDeviceTracker,
  autostart: Boolean = true
) : ComboBox<Device>() {

  constructor(parentDisposable: Disposable, initialDevice: Device?, deviceTracker: IDeviceComboBoxDeviceTracker)
    : this(parentDisposable, initialDevice, deviceTracker, true)

  private val selectionChannel = Channel<Device>(1)
  private val coroutineScope = AndroidCoroutineScope(parentDisposable, workerThread)

  init {
    AccessibleContextUtil.setName(this, LogcatBundle.message("logcat.device.combo.accessible.name"))
    renderer = DeviceComboBoxRenderer()
    model = DeviceModel()

    if (autostart) {
      startTrackingDevices()
    }
  }

  @TestOnly
  fun startTrackingDevices() {
    coroutineScope.launch {
      deviceTracker.trackDevices().collect {
        when (it) {
          is Added -> deviceAdded(it.device)
          is StateChanged -> deviceStateChanged(it.device)
        }
      }
      selectionChannel.close()
    }
  }

  fun trackSelectedDevice(): Flow<Device> = selectionChannel.consumeAsFlow()

  private suspend fun deviceAdded(device: Device) {
    (model as DeviceModel).add(device)
    when {
      selectedItem != null -> return
      initialDevice == null -> selectDevice(device)
      device.deviceId == initialDevice.deviceId -> selectDevice(device)
    }
  }

  private suspend fun selectDevice(device: Device) {
    selectedItem = device
    selectionChannel.send(device)
  }

  private suspend fun deviceStateChanged(device: Device) {
    val setSelected = device.deviceId == (selectedItem as? Device)?.deviceId
    if (setSelected) {
      selectionChannel.send(device)
    }
    (model as DeviceModel).replaceItem(device, setSelected)
  }

  // Renders a Device.
  //
  // Examples:
  // Online physical device:  "Google Pixel 2 (HT85F1A236612) Android 11, API 30"
  // Offline physical device: "Google Pixel 2 (HT85F1A236612) Android 11, API 30 [OFFLINE]"
  // Online emulator:         "Pixel 4 API 30 (emulator-5554) Android 11, API 30"
  // Offline emulator:        "Pixel 4 API 30 Android 11, API 30 [OFFLINE]"
  //
  // Notes
  //   Physical device name is based on the manufacturer and model while emulator name is based on the AVD name.
  //   Offline emulator does not include the serial number because it is irrelevant while the device offline.
  private class DeviceComboBoxRenderer : ColoredListCellRenderer<Device>() {
    override fun customizeCellRenderer(list: JList<out Device>, device: Device?, index: Int, selected: Boolean, hasFocus: Boolean) {
      if (device == null) {
        append(LogcatBundle.message("logcat.device.combo.no.connected.devices"), ERROR_ATTRIBUTES)
        return
      }
      icon = if (device.isEmulator) VIRTUAL_DEVICE_PHONE else PHYSICAL_DEVICE_PHONE

      append(device.name, REGULAR_ATTRIBUTES)
      if (device.isOnline) {
        append(" (${device.serialNumber})", REGULAR_ATTRIBUTES)
      }
      append(LogcatBundle.message("logcat.device.combo.version", device.release, device.sdk), GRAY_ATTRIBUTES)
      if (!device.isOnline) {
        append(LogcatBundle.message("logcat.device.combo.offline"), GRAYED_BOLD_ATTRIBUTES)
      }
    }
  }

  private class DeviceModel : CollectionComboBoxModel<Device>() {
    private val idToIndexMap = mutableMapOf<String, Int>()

    override fun add(device: Device) {
      super.add(device)
      idToIndexMap[device.deviceId] = idToIndexMap.size
    }

    fun replaceItem(device: Device, setSelected: Boolean) {
      val index = idToIndexMap[device.deviceId]
      if (index == null) {
        thisLogger().warn("Device ${device.deviceId} expected to exist but was not found")
        return
      }
      setElementAt(device, index)
      if (setSelected) {
        selectedItem = device
      }
    }
  }
}
