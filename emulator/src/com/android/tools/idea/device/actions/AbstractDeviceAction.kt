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
package com.android.tools.idea.device.actions

import com.android.tools.idea.device.DEVICE_CONTROLLER_KEY
import com.android.tools.idea.device.DEVICE_VIEW_KEY
import com.android.tools.idea.device.DeviceController
import com.android.tools.idea.device.DeviceView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

/**
 * Common superclass for toolbar actions of the Device tool window panel.
 */
internal abstract class AbstractDeviceAction : AnAction(), DumbAware {

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = isEnabled(event)
  }

  protected open fun isEnabled(event: AnActionEvent): Boolean =
    isDeviceConnected(event)
}

internal fun getProject(event: AnActionEvent): Project =
  event.getRequiredData(CommonDataKeys.PROJECT)

internal fun getDeviceController(event: AnActionEvent): DeviceController? =
  event.dataContext.getData(DEVICE_CONTROLLER_KEY)

internal fun getDeviceView(event: AnActionEvent): DeviceView? =
  event.dataContext.getData(DEVICE_VIEW_KEY)

internal fun isDeviceConnected(event: AnActionEvent) =
  getDeviceView(event)?.isConnected == true