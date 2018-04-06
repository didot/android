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
package com.android.tools.idea.naveditor.structure

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.workbench.*
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Factory
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class StructurePanel : AdtSecondaryPanel(BorderLayout()), ToolContent<DesignSurface> {

  private var backPanel: BackPanel? = null
  private var destinationList: DestinationList? = null

  override fun setToolContext(toolContext: DesignSurface?) {
    backPanel?.let {
      remove(it)
      Disposer.dispose(it)
    }
    destinationList?.let {
      remove(it)
      Disposer.dispose(it)
    }
    if (toolContext is NavDesignSurface) {
      val hostPanel = AdtSecondaryPanel(BorderLayout())
      val hostSeparator = TitledSeparator("HOST")
      hostSeparator.isEnabled = false
      hostSeparator.border = JBUI.Borders.empty(5)
      hostPanel.add(hostSeparator, BorderLayout.NORTH)
      hostPanel.add(HostPanel(toolContext), BorderLayout.SOUTH)
      add(hostPanel, BorderLayout.NORTH)

      val dl = DestinationList(this, toolContext)
      destinationList = dl
      val graphHeader = AdtSecondaryPanel(BorderLayout())
      val graphSeparator = TitledSeparator("GRAPH")
      graphSeparator.isEnabled = false
      graphSeparator.border = JBUI.Borders.empty(5)
      graphHeader.add(graphSeparator, BorderLayout.NORTH)
      backPanel = BackPanel(toolContext, dl::updateComponentList, this)
      graphHeader.add(backPanel, BorderLayout.SOUTH)
      val bottomPanel = JPanel(BorderLayout())
      bottomPanel.add(graphHeader, BorderLayout.NORTH)
      bottomPanel.add(destinationList, BorderLayout.CENTER)
      add(bottomPanel, BorderLayout.CENTER)
    }
  }

  override fun dispose() {}

  override fun getComponent(): JComponent {
    return this
  }

  override fun supportsFiltering() = true

  override fun setFilter(filter: String) {
    destinationList?.setFilter(filter)
  }

  override fun setStartFiltering(listener: StartFilteringListener) {
    destinationList?.setStartFiltering(listener)
  }

  override fun setStopFiltering(stopFilteringListener: Runnable) {
    destinationList?.setStopFiltering(stopFilteringListener)
  }


  class StructurePanelDefinition : ToolWindowDefinition<DesignSurface>("Destinations", AllIcons.Toolwindows.ToolWindowHierarchy,
                                                                       "structure", Side.LEFT, Split.TOP, AutoHide.DOCKED,
                                                                       Factory<ToolContent<DesignSurface>> { StructurePanel() })

}