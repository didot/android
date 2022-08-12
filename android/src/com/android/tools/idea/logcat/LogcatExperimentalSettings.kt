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
package com.android.tools.idea.logcat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Experimental Logcat settings
 */
@State(name = "LogcatExperimentalSettings", storages = [(Storage("logcat.experimental.xml"))])
data class LogcatExperimentalSettings(
  var logcatV2Enabled: Boolean = true,
  var bannerDismissed: Boolean = false,
)
  : PersistentStateComponent<LogcatExperimentalSettings> {
  override fun getState(): LogcatExperimentalSettings = this

  override fun loadState(state: LogcatExperimentalSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    @JvmStatic
    fun getInstance(): LogcatExperimentalSettings {
      return ApplicationManager.getApplication().getService(LogcatExperimentalSettings::class.java)
    }
  }
}