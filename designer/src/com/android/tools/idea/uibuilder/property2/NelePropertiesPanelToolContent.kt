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

import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.idea.common.property2.api.EditorProvider
import com.android.tools.idea.common.property2.api.PropertiesPanel
import com.android.tools.idea.common.property2.api.PropertiesView
import com.android.tools.idea.common.property2.impl.ui.registerKeyAction
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.property2.inspector.*
import com.android.tools.idea.uibuilder.property2.support.NeleControlTypeProvider
import com.android.tools.idea.uibuilder.property2.support.NeleEnumSupportProvider
import com.android.tools.idea.uibuilder.property2.support.ToggleShowResolvedValueAction
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JPanel

private const val BASIC_PAGE = "Basic"

/**
 * Create the models and views for the properties tool content.
 */
class NelePropertiesPanelToolContent(facet: AndroidFacet) : JPanel(BorderLayout()), ToolContent<DesignSurface> {
  private val model = NelePropertiesModel(this, facet)
  private val view = PropertiesView(model)
  private val properties = PropertiesPanel(model)
  private val controlTypeProvider = NeleControlTypeProvider()
  private val enumSupportProvider = NeleEnumSupportProvider()
  private val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)
  private val filterKeyListener = createFilterKeyListener()
  private val showResolvedValueAction = ToggleShowResolvedValueAction(model)

  init {
    add(properties.component, BorderLayout.CENTER)
    properties.addView(view)
    val basic = view.addTab(BASIC_PAGE)
    basic.builders.add(IdInspectorBuilder(editorProvider))
    basic.builders.add(LayoutInspectorBuilder(facet.module.project, editorProvider))
    basic.builders.add(ViewInspectorBuilder(facet.module.project, editorProvider))
    basic.builders.add(TextViewInspectorBuilder(editorProvider))
    basic.builders.add(ProgressBarInspectorBuilder(editorProvider))
    basic.builders.add(FavoritesInspectorBuilder(editorProvider))
    registerKeyAction(showResolvedValueAction, ToggleShowResolvedValueAction.SHORTCUT.firstKeyStroke, "toggleResolvedValues",
                      WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
  }

  override fun setToolContext(toolContext: DesignSurface?) {
    model.surface = toolContext as? NlDesignSurface
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
