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
package com.android.tools.idea.run.configuration

import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.idea.run.configuration.execution.AndroidWatchFaceConfigurationExecutor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle

class AndroidWatchFaceConfigurationType :
  ConfigurationTypeBase(
    ID,
    AndroidBundle.message("android.watchface.configuration.type.name"),
    AndroidBundle.message("android.run.configuration.type.description"),
    StudioIcons.Wear.WATCH_FACE_RUN_CONFIG
  ), DumbAware {
  companion object {
    const val ID = "AndroidWatchFaceConfigurationType"
  }

  init {
    addFactory(object : ConfigurationFactory(this) {
      override fun getId() = "AndroidWatchFaceConfigurationFactory"
      override fun createTemplateConfiguration(project: Project) = AndroidWatchFaceConfiguration(project, this)
    })
  }
}

class AndroidWatchFaceConfiguration(project: Project, factory: ConfigurationFactory) : AndroidWearConfiguration(project, factory) {
  override val componentType = ComponentType.WATCH_FACE
  override val userVisibleComponentTypeName: String = AndroidBundle.message("android.run.configuration.watchface")
  override val componentBaseClassesFqNames = WearBaseClasses.WATCH_FACES
  override fun getExecutor(environment: ExecutionEnvironment) = AndroidWatchFaceConfigurationExecutor(environment)
}

