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
package com.android.tools.idea.explorer.fs

import java.io.File
import java.util.function.Supplier

/**
 * Abstraction over ADB devices and their corresponding file system.
 *
 * The service is meant to be called on the EDT thread, where long pending operations suspend,
 * while state changes (e.g. new device discovered, existing device disconnected, etc.) fire events on
 * the registered [DeviceFileSystemServiceListener] instances. Events are always fired on the EDT
 * thread.
 */
interface DeviceFileSystemService<S : DeviceFileSystem> {
  fun addListener(listener: DeviceFileSystemServiceListener)
  fun removeListener(listener: DeviceFileSystemServiceListener)

  /**
   * Starts the service, usually after registering one or more [DeviceFileSystemServiceListener].
   */
  suspend fun start(adbSupplier: Supplier<File?>)

  /**
   * Returns the list of currently known devices.
   */
  val devices: List<S>
}