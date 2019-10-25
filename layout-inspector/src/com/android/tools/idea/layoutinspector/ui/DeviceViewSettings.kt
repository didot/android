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
package com.android.tools.idea.layoutinspector.ui

import kotlin.properties.Delegates

class DeviceViewSettings(
  scalePercent: Int = 100,
  drawBorders: Boolean = true,
  drawLabel: Boolean = false
) {
  val modificationListeners = mutableListOf<() -> Unit>()

  /** Scale of the view in percentage: 100 = 100% */
  var scalePercent: Int by Delegates.observable(scalePercent) {
    _, _, _ -> modificationListeners.forEach { it() }
  }

  /** Scale of the view as a fraction: 1 = 100% */
  val scaleFraction: Double
    get() = scalePercent / 100.0

  var drawBorders: Boolean by Delegates.observable(drawBorders) {
    _, _, _ -> modificationListeners.forEach { it() }
  }

  var drawLabel: Boolean by Delegates.observable(drawLabel) {
    _, _, _ -> modificationListeners.forEach { it() }
  }
}