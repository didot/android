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
package com.android.tools.idea.uibuilder.property.assistant

import com.android.tools.idea.flags.StudioFlags.NELE_WIDGET_ASSISTANT
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Provides the assistant panel for the properties. This assistant allows [com.android.tools.idea.uibuilder.api.ViewHandler]s
 * to provide custom shortcuts for configuring some component aspects.
 */
class ComponentAssistant(private val myProject: Project) : JPanel(BorderLayout()), DesignSurfaceListener {
  private var currentComponent : JComponent? = null

  /**
   * Interface that allows [com.android.tools.idea.uibuilder.api.ViewHandler]s providing the assistant component.
   */
  interface PanelFactory {
    fun createComponent(component : NlComponent, close : () -> Unit) : JComponent
  }

  private fun closeAssistant() {
    currentComponent?.isVisible = false
    removeAll()
    currentComponent = null
    isVisible = false
  }

  override fun componentSelectionChanged(surface: DesignSurface, newSelection: List<NlComponent>) {
    closeAssistant()

    // The assistant is not available if the flag is disabled or if more than one component is selected
    if (newSelection.size != 1) {
      return
    }

    val panel = ViewHandlerManager.get(myProject)
        .getHandler(newSelection[0].tagName)?.getComponentAssistant(surface, newSelection[0])
    val component = panel?.createComponent(newSelection[0], {
      closeAssistant()
    })
    currentComponent = component
    if (component == null) {
      return
    }
    add(component, BorderLayout.CENTER)
    isVisible = component.isVisible
  }

  override fun getMinimumSize(): Dimension = currentComponent?.minimumSize ?: super.getMinimumSize()
}
