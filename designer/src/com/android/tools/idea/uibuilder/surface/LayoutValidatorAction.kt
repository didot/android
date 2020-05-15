/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.actions.DesignerActions
import com.android.tools.idea.actions.LAYOUT_VALIDATOR_KEY
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons

/**
 * Action to toggle layout validation in [NlDesignSurface].
 * For now, all icons are temporary.
 */
class LayoutValidatorAction: DumbAwareAction(
  "Run Layout Validation", "Run the layout validation",
  StudioIcons.Shell.Toolbar.RUN) {

  companion object {
    @JvmStatic
    fun getInstance(): LayoutValidatorAction {
      return ActionManager.getInstance().getAction(
        DesignerActions.ACTION_RUN_LAYOUT_VALIDATOR) as LayoutValidatorAction
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = StudioFlags.NELE_LAYOUT_VALIDATOR_IN_EDITOR.get()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val controller = e.getData(LAYOUT_VALIDATOR_KEY) ?: return
    controller.runLayoutValidation()
  }
}

/**
 * Controller for Layout Validator. It can run layout validation.
 */
interface LayoutValidatorControl {
  fun runLayoutValidation()
}