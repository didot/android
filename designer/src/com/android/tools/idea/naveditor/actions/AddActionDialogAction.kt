/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.actions

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.naveditor.property.inspector.AddActionDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

open class AddActionDialogAction(
  private val surface: DesignSurface,
  val text: String,
  private val parent: NlComponent,
  private val action: NlComponent?
) :
  AnAction(text) {
  override fun actionPerformed(e: AnActionEvent?) {
    val addActionDialog = AddActionDialog(AddActionDialog.Defaults.NORMAL, action, parent)
    if (addActionDialog.showAndGet()) {
      val action = addActionDialog.writeUpdatedAction()
      surface.selectionModel.setSelection(listOf(action))
    }
  }
}

class ToDestinationAction(
  val surface: DesignSurface,
  val parent: NlComponent
) : AddActionDialogAction(surface, "To Destination...", parent, null)

class EditExistingAction(
  val surface: DesignSurface,
  val parent: NlComponent,
  val action: NlComponent
) : AddActionDialogAction(surface, "Edit", parent, action)