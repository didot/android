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
package com.android.tools.idea.uibuilder.actions

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.rendering.RefreshRenderAction
import com.android.tools.idea.rendering.RenderSettings
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.android.tools.idea.uibuilder.api.actions.ToggleViewAction
import com.intellij.util.ui.LafIconLookup

class ToggleShowDecorationsAction(label: String = "Show system UI") :
  ToggleViewAction(null, LafIconLookup.getIcon("checkmark"), label, label) {
  override fun isSelected(editor: ViewEditor,
                          handler: ViewHandler,
                          parent: NlComponent,
                          selectedChildren: MutableList<NlComponent>): Boolean = RenderSettings.getProjectSettings(editor.model.project).showDecorations
  override fun setSelected(editor: ViewEditor,
                           handler: ViewHandler,
                           parent: NlComponent,
                           selectedChildren: MutableList<NlComponent>,
                           selected: Boolean) {
    // We also persist the settings to the RenderSettings
    RenderSettings.getProjectSettings(editor.model.project).showDecorations = selected
    RefreshRenderAction.clearCacheAndRefreshSurface(editor.scene.designSurface)
  }

}
