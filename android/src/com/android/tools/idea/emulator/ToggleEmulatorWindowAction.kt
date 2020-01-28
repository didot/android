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
package com.android.tools.idea.emulator

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class ToggleEmulatorWindowAction : AnAction("Toggle Emulator Window") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val activateWindow = true
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(EmulatorToolWindow.ID)
    if (toolWindow.isVisible) {
      toolWindow.hide(null)
    } else {
      toolWindow.show(null)
      if (activateWindow && !toolWindow.isActive) {
        toolWindow.activate(null)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val enabled = StudioFlags.EMBEDDED_EMULATOR_ENABLED.get()
    e.presentation.isEnabled = enabled
    e.presentation.isVisible = enabled
  }
}