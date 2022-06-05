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
package com.android.tools.idea

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.idea.device.DEVICE_CONTROLLER_KEY
import com.android.tools.idea.device.DEVICE_VIEW_KEY
import com.android.tools.idea.device.DeviceView
import com.android.tools.idea.emulator.AbstractDisplayView
import com.android.tools.idea.emulator.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.emulator.EMULATOR_VIEW_KEY
import com.android.tools.idea.emulator.EmulatorView
import com.google.common.truth.Truth
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project

/**
 * Executes an action related to device mirroring.
 */
internal fun executeDeviceAction(actionId: String, displayView: AbstractDisplayView, project: Project) {
  val actionManager = ActionManager.getInstance()
  val action = actionManager.getAction(actionId)
  val presentation = Presentation()
  val event = AnActionEvent(null, TestDataContext(displayView, project), ActionPlaces.UNKNOWN, presentation, actionManager, 0)
  action.update(event)
  Truth.assertThat(presentation.isEnabledAndVisible).isTrue()
  action.actionPerformed(event)
}

internal fun getDeviceActionPresentation(actionId: String, displayView: AbstractDisplayView, project: Project): Presentation {
  val actionManager = ActionManager.getInstance()
  val action = actionManager.getAction(actionId)
  val presentation = Presentation()
  val event = AnActionEvent(null, TestDataContext(displayView, project), ActionPlaces.UNKNOWN, presentation, actionManager, 0)
  action.update(event)
  return presentation
}

private class TestDataContext(private val displayView: AbstractDisplayView, private val project: Project) : DataContext {
  private val emulatorView = displayView as? EmulatorView
  private val deviceView = displayView as? DeviceView

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      EMULATOR_VIEW_KEY.name -> emulatorView
      EMULATOR_CONTROLLER_KEY.name -> emulatorView?.emulator
      DEVICE_VIEW_KEY.name -> deviceView
      DEVICE_CONTROLLER_KEY.name -> deviceView?.deviceController
      ZOOMABLE_KEY.name -> displayView
      CommonDataKeys.PROJECT.name -> project
      else -> null
    }
  }
}
