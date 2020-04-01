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

import com.android.tools.idea.appinspection.ide.AppInspectionHostService
import com.android.tools.idea.model.AndroidModuleInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import javax.swing.JComponent

class AppInspectionToolWindow(window: ToolWindow, private val project: Project) : Disposable {
  /**
   * This dictates the names of the preferred processes. They are drawn from the android applicationIds of the modules in this [project].
   */
  private fun getPreferredProcesses(): List<String> = ModuleManager.getInstance(project).modules
    .mapNotNull { AndroidModuleInfo.getInstance(it)?.`package` }
    .toList()

  private val appInspectionView = AppInspectionView(project, AppInspectionHostService.instance.discoveryHost, ::getPreferredProcesses)
  val component: JComponent = appInspectionView.component

  override fun dispose() {
    // Although we do nothing here, because this class is disposable, other components can register
    // against it
  }
}