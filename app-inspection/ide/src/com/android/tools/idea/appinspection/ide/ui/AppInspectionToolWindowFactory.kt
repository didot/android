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
package com.android.tools.idea.appinspection.ide.ui

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import icons.StudioIcons

private const val APP_INSPECTION_TITLE = "App Inspection"

class AppInspectionToolWindowFactory : DumbAware, ToolWindowFactory, Condition<Project> {

  override fun createToolWindowContent(project: Project,
                                       toolWindow: ToolWindow) {
    val appInspectionToolWindow = AppInspectionToolWindow(toolWindow, project)
    val contentFactory = ContentFactory.SERVICE.getInstance()
    val content = contentFactory.createContent(appInspectionToolWindow.component, "", false)
    toolWindow.contentManager.addContent(content)
    toolWindow.setIcon(StudioIcons.Shell.ToolWindows.INSPECTION)
    Disposer.register(project, appInspectionToolWindow)
    toolWindow.show(null)
  }

  override fun init(toolWindow: ToolWindow) {
    toolWindow.isToHideOnEmptyContent = true
    toolWindow.hide(null)
    toolWindow.isShowStripeButton = false
    toolWindow.stripeTitle = APP_INSPECTION_TITLE
  }

  override fun value(project: Project) = StudioFlags.ENABLE_APP_INSPECTION_TOOL_WINDOW.get()
}