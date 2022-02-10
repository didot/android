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
package com.android.tools.idea.logcat.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

private const val DEFAULT_BUFFER_SIZE = 1024 * 1024

@State(name = "LogcatSettings", storages = [Storage("logcatSettings.xml")])
internal data class LogcatSettings(
  var bufferSize: Int = DEFAULT_BUFFER_SIZE,
  var namedFiltersEnabled: Boolean = false,
) {

  companion object {
    fun getInstance(): LogcatSettings = ApplicationManager.getApplication().getService(LogcatSettings::class.java)
  }
}
