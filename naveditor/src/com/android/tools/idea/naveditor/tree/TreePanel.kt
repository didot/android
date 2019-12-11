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
package com.android.tools.idea.naveditor.tree

import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ComponentTreeModel
import com.android.tools.componenttree.api.ComponentTreeSelectionModel
import com.android.tools.componenttree.api.ViewNodeType
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionListener
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.isNavigation
import com.android.tools.idea.naveditor.model.uiName
import com.intellij.openapi.application.ApplicationManager
import icons.StudioIcons
import javax.swing.Icon
import javax.swing.JComponent

class TreePanel : ToolContent<DesignSurface> {
  private var designSurface: DesignSurface? = null
  private val componentTree: JComponent
  private val componentTreeModel: ComponentTreeModel
  private val componentTreeSelectionModel: ComponentTreeSelectionModel
  private val contextSelectionListener = SelectionListener { _, _ -> contextSelectionChanged() }
  private var shouldScroll = true
  private val modelListener = NlModelListener()

  init {
    val builder = ComponentTreeBuilder()
      .withNodeType(NlComponentNodeType())
      .withContextMenu { _, x: Int, y: Int -> showContextMenu(x, y) }
      .withDoubleClick { activateComponent() }
      .withInvokeLaterOption { ApplicationManager.getApplication().invokeLater(it) }

    val (tree, model, selectionModel) = builder.build()
    componentTree = tree
    componentTreeModel = model
    componentTreeSelectionModel = selectionModel
    selectionModel.addSelectionListener {
      if (shouldScroll) {
        designSurface?.let {
          val list = selectionModel.selection.filterIsInstance<NlComponent>()
          it.selectionModel.setSelection(list)
          it.scrollToCenter(list.filter { c -> c.isDestination && !c.isNavigation })
        }
      }
    }
  }

  override fun setToolContext(toolContext: DesignSurface?) {
    designSurface?.let {
      it.selectionModel?.removeListener(contextSelectionListener)
      it.model?.removeListener(modelListener)

    }
    designSurface = toolContext

    designSurface?.let {
      it.selectionModel?.addListener(contextSelectionListener)
      it.models.firstOrNull()?.let { model ->
        model.addListener(modelListener)
        update(model)
      }
    }
  }

  private fun contextSelectionChanged() {
    shouldScroll = false
    try {
      componentTreeSelectionModel.selection = designSurface?.selectionModel?.selection ?: emptyList()
    }
    finally {
      shouldScroll = true
    }
  }

  override fun getComponent() = componentTree

  private fun showContextMenu(x: Int, y: Int) {
    val node = componentTreeSelectionModel.selection.singleOrNull() as NlComponent? ?: return
    designSurface?.actionManager?.showPopup(componentTree, x, y, node)
  }

  private fun activateComponent() {
    val node = componentTreeSelectionModel.selection.singleOrNull() as NlComponent? ?: return
    designSurface?.notifyComponentActivate(node)
  }

  override fun dispose() {
    setToolContext(null)
  }

  private fun update(model: NlModel) {
    componentTreeModel.treeRoot = model.components.firstOrNull()
  }

  private class NlComponentNodeType : ViewNodeType<NlComponent>() {
    override val clazz = NlComponent::class.java

    override fun tagNameOf(node: NlComponent) = node.tagName

    override fun idOf(node: NlComponent) = node.id

    override fun textValueOf(node: NlComponent) = node.uiName

    override fun iconOf(node: NlComponent): Icon = node.mixin?.icon ?: StudioIcons.LayoutEditor.Palette.UNKNOWN_VIEW

    override fun isEnabled(node: NlComponent) = true

    override fun parentOf(node: NlComponent) = node.parent

    override fun childrenOf(node: NlComponent) = node.children.filter { it.isDestination || it.isAction }
  }

  private inner class NlModelListener : ModelListener {
    override fun modelDerivedDataChanged(model: NlModel) = updateLater(model)
    override fun modelLiveUpdate(model: NlModel, animate: Boolean) = updateLater(model)

    private fun updateLater(model: NlModel) = ApplicationManager.getApplication().invokeLater { update(model) }
  }
}