/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys

import com.android.tools.idea.gradle.util.GradleProjects.getGradleModulePath
import com.intellij.ide.projectView.impl.ModuleGroup.ARRAY_DATA_KEY
import com.intellij.openapi.actionSystem.LangDataKeys.MODULE_CONTEXT_ARRAY

class AndroidNewModuleInGroupAction : AndroidNewModuleAction("Module", "Adds a new module to the project", null) {
  override fun update(e: AnActionEvent) {
    super.update(e)

    if (!e.presentation.isVisible) {
      return  // Nothing to do, if above call to parent update() has disable the action
    }

    val moduleGroups = e.getData(ARRAY_DATA_KEY)
    val modules = e.getData(MODULE_CONTEXT_ARRAY)
    e.presentation.isVisible = !moduleGroups.isNullOrEmpty() || !modules.isNullOrEmpty()
  }

  override fun getModulePath(e: AnActionEvent): String? {
    val module = LangDataKeys.MODULE.getData(e.dataContext) ?: return null

    return getGradleModulePath(module)?.removePrefix(":") ?: ""
  }
}
