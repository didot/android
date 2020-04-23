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
package com.android.tools.idea.run

import com.android.resources.Density

/**
 * Describes the specification of a device or the common specification of devices the application is to be launched on.
 */
interface AndroidDeviceSpec {
  val apiLevel: Int
  val featureLevel: Int
  val apiCodeName: String?
  val buildDensity: Density?
  val buildAbis: Collection<String>
  val languages: Collection<String>
}
