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

import com.android.annotations.VisibleForTesting
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionListener
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.naveditor.model.uiName
import com.android.tools.idea.naveditor.structure.DestinationList.ROOT_NAME
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class BackPanel(private val surface: NavDesignSurface, private val updateCallback: () -> Unit, parentDisposable: Disposable)
  : JPanel(BorderLayout()), Disposable, ModelListener, SelectionListener {

  @VisibleForTesting
  val label: JLabel = JLabel("", StudioIcons.Common.BACK_ARROW, SwingConstants.LEFT)

  init {
    Disposer.register(parentDisposable, this)
    val colorSet = SceneContext.get(surface.currentSceneView).colorSet
    background = colorSet.subduedBackground
    border = BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, colorSet.frames),
                                                BorderFactory.createEmptyBorder(5, 5, 5, 5))
    isVisible = false
    add(label, BorderLayout.WEST)
    label.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        goBack()
      }
    })
    surface.model?.addListener(this)
    surface.selectionModel.addListener(this)
  }

  override fun dispose() {
    surface.model?.removeListener(this)
    surface.selectionModel.removeListener(this)
  }

  @VisibleForTesting
  fun goBack() {
    surface.currentNavigation = surface.currentNavigation.parent!!
    updateCallback()
    update()
  }

  override fun selectionChanged(model: SelectionModel, selection: MutableList<NlComponent>) {
    update()
  }

  override fun modelChanged(model: NlModel) {
    update()
  }

  private fun update() {
    isVisible = false
    surface.currentNavigation.parent?.let {
      label.text = if (it.parent == null) ROOT_NAME else it.uiName
      isVisible = true
    }
  }
}