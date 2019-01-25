/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property2

import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.adtui.stdui.registerAnActionKey
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.adtui.workbench.ToolWindowCallback
import com.android.tools.idea.common.property2.api.PropertiesPanel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionLayoutAttributesModel
import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionLayoutAttributesView
import com.android.tools.idea.uibuilder.property2.support.ToggleShowResolvedValueAction
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JPanel

/**
 * Create the models and views for the properties tool content.
 */
class NelePropertiesPanelToolContent(facet: AndroidFacet, parentDisposable: Disposable)
  : JPanel(BorderLayout()), ToolContent<DesignSurface> {
  private val componentModel = NelePropertiesModel(this, facet)
  private val componentView = NelePropertiesView(componentModel)
  private val motionModel = MotionLayoutAttributesModel(this, facet)
  private val motionEditorView = MotionLayoutAttributesView(motionModel)
  private val properties = PropertiesPanel<NelePropertyItem>(componentModel)
  private val filterKeyListener = createFilterKeyListener()
  private val showResolvedValueAction = ToggleShowResolvedValueAction(componentModel)
  private var toolWindow: ToolWindowCallback? = null

  init {
    Disposer.register(parentDisposable, this)
    add(properties.component, BorderLayout.CENTER)
    properties.addView(componentView)
    properties.addView(motionEditorView)
    registerActionKey({ toolWindow?.startFiltering("") }, KeyStrokes.CMD_F, "search", { true }, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    registerAnActionKey({ showResolvedValueAction }, ToggleShowResolvedValueAction.SHORTCUT.firstKeyStroke, "toggleResolvedValues",
                        WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
  }

  override fun setToolContext(toolContext: DesignSurface?) {
    componentModel.surface = toolContext as? NlDesignSurface
    motionModel.surface = toolContext as? NlDesignSurface
  }

  override fun registerCallbacks(callback: ToolWindowCallback) {
    toolWindow = callback
  }

  override fun getComponent() = this

  override fun dispose() = Unit

  override fun supportsFiltering() = true

  override fun setFilter(filter: String) {
    properties.filter = filter
  }

  override fun getFilterKeyListener() = filterKeyListener

  private fun createFilterKeyListener() = object : KeyAdapter() {
    override fun keyPressed(event: KeyEvent) {
      if (!properties.filter.isEmpty() && event.keyCode == KeyEvent.VK_ENTER && event.modifiers == 0 && properties.enterInFilter()) {
        event.consume()
      }
    }
  }
}
