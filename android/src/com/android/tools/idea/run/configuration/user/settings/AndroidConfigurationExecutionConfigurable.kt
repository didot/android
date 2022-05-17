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
package com.android.tools.idea.run.configuration.user.settings

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.PropertyBinding
import com.intellij.ui.layout.panel

class AndroidConfigurationExecutionConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable {
    return AndroidConfigurationExecutionConfigurable()
  }
}

class AndroidConfigurationExecutionConfigurable : BoundConfigurable("Android Configurations") {

  private val settings = AndroidConfigurationExecutionSettings.getInstance()

  private val checkboxDescriptor = CheckboxDescriptor(
    "Enable new execution flow for Android configurations",
    PropertyBinding({ settings.state.enableNewConfigurationFlow }, { settings.state.enableNewConfigurationFlow = it })
  )

  override fun createPanel(): DialogPanel {
    return panel {
      titledRow("New Execution Flow") {
        row {
          checkBox(checkboxDescriptor)
        }
      }
    }
  }
}

@Service
@State(name = "AndroidConfigurationExecutionSettings", storages = [Storage("android-configuration-execution-settings.xml")])
class AndroidConfigurationExecutionSettings :
  SimplePersistentStateComponent<AndroidConfigurationExecutionState>(AndroidConfigurationExecutionState()) {
  companion object {
    @JvmStatic
    fun getInstance(): AndroidConfigurationExecutionSettings = ApplicationManager.getApplication().getService(
      AndroidConfigurationExecutionSettings::class.java)
  }
}

class AndroidConfigurationExecutionState : BaseState() {
  var enableNewConfigurationFlow by property(true)
}
