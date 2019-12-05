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
package com.android.tools.idea.appinspection.api

import com.android.ddmlib.IDevice
import com.android.tools.idea.transport.TransportServiceProxy
import com.android.tools.profiler.proto.Common

data class AutoPreferredProcess(
  val manufacturer: String,
  val model: String,
  val serialNumber: String,
  val packageName: String?
) {

  constructor(device: IDevice, packageName: String?)
    : this(TransportServiceProxy.getDeviceManufacturer(device),
           TransportServiceProxy.getDeviceModel(device),
           device.serialNumber, packageName)

  /**
   * Returns true if a device from the transport layer matches the device profile stored.
   */
  fun isDeviceMatch(device: Common.Device): Boolean {
    return device.manufacturer == manufacturer &&
           device.model == model &&
           device.serial == serialNumber
  }
}
