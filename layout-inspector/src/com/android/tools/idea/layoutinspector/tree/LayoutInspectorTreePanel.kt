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
package com.android.tools.idea.layoutinspector.tree

import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ComponentTreeModel
import com.android.tools.componenttree.api.ComponentTreeSelectionModel
import com.android.tools.componenttree.api.ViewNodeType
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.SkiaParser
import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.InspectorView
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorEvent
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.View
import com.android.tools.profiler.proto.Common
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider
import java.awt.Image
import java.util.Collections
import javax.swing.Icon
import javax.swing.JComponent

class LayoutInspectorTreePanel : ToolContent<LayoutInspector> {
  private var layoutInspector: LayoutInspector? = null
  private var client: InspectorClient? = null
  private val componentTree: JComponent
  private val componentTreeModel: ComponentTreeModel
  private val componentTreeSelectionModel: ComponentTreeSelectionModel

  init {
    val (tree, model, selectionModel) = ComponentTreeBuilder()
      .withNodeType(InspectorViewNodeType())
      .withInvokeLaterOption { ApplicationManager.getApplication().invokeLater(it) }
      .build()
    componentTree = tree
    componentTreeModel = model
    componentTreeSelectionModel = selectionModel
    selectionModel.addSelectionListener { layoutInspector?.layoutInspectorModel?.selection = it.firstOrNull() as? ViewNode }
  }

  // TODO: There probably can only be 1 layout inspector per project. Do we need to handle changes?
  override fun setToolContext(toolContext: LayoutInspector?) {
    layoutInspector?.layoutInspectorModel?.modificationListeners?.remove(this::modelModified)
    layoutInspector?.modelChangeListeners?.remove(this::modelChanged)
    layoutInspector = toolContext
    layoutInspector?.modelChangeListeners?.add(this::modelChanged)
    layoutInspector?.layoutInspectorModel?.modificationListeners?.add(this::modelModified)
    client = layoutInspector?.client
    client?.register(Common.Event.EventGroupIds.COMPONENT_TREE, ::loadComponentTree)
    client?.registerProcessEnded(::clearComponentTree)
    if (toolContext != null) {
      modelChanged(toolContext.layoutInspectorModel, toolContext.layoutInspectorModel)
    }
  }

  override fun getComponent() = componentTree

  override fun dispose() {
  }

  private fun clearComponentTree() {
    val application = ApplicationManager.getApplication()
    application.invokeLater {
      val emptyRoot = ViewNode.EMPTY
      layoutInspector?.layoutInspectorModel?.update(emptyRoot)
    }
  }

  private fun loadComponentTree(event: LayoutInspectorEvent) {
    val application = ApplicationManager.getApplication()
    application.executeOnPooledThread {
      val loader = ComponentTreeLoader(event.tree)
      val root = loader.loadRootView()
      val bytes = client?.getPayload(event.tree.payloadId) ?: return@executeOnPooledThread
      var viewRoot: InspectorView? = null
      if (bytes.isNotEmpty()) {
        viewRoot = SkiaParser().getViewTree(bytes)
      }
      if (viewRoot != null) {
        val imageLoader = ComponentImageLoader(root, viewRoot)
        imageLoader.loadImages()
      }

      application.invokeLater {
        layoutInspector?.layoutInspectorModel?.update(root)
      }
    }
  }

  class ComponentImageLoader(root: ViewNode, viewRoot: InspectorView) {
    private val nodeMap = root.flatten().associateBy { it.drawId }
    private val viewMap = viewRoot.flatten().associateBy { it.id.toLong() }

    fun loadImages() {
      for ((drawId, node) in nodeMap) {
        val view = viewMap[drawId] ?: continue
        node.imageBottom = view.image
        addChildNodeImages(node, view)
      }
    }

    private fun addChildNodeImages(node: ViewNode, view: InspectorView) {
      var beforeChildren = true
      for (child in view.children.values) {
        val isChildNode = view.id != child.id && nodeMap.containsKey(child.id.toLong())
        when {
          isChildNode -> beforeChildren = false
          beforeChildren -> node.imageBottom = combine(node.imageBottom, child)
          else -> node.imageTop = combine(node.imageTop, child)
        }
        if (!isChildNode) {
          // Some Skia views are several levels deep:
          addChildNodeImages(node, child)
        }
      }
    }

    private fun combine(image: Image?, view: InspectorView): Image? =
      when {
        view.image == null -> image
        image == null -> view.image
        else -> {
          // Combine the images...
          val g = image.graphics
          UIUtil.drawImage(g, view.image!!, 0, 0, null)
          g.dispose()
          image
        }
      }
  }

  private class ComponentTreeLoader(private val tree: ComponentTreeEvent) {
    val stringTable = StringTable(tree.stringList)

    fun loadRootView(): ViewNode {
      return loadView(tree.root, null)
    }

    fun loadView(view: View, parent: ViewNode?): ViewNode {
      val qualifiedName = "${stringTable[view.packageName]}.${stringTable[view.className]}"
      val viewId = stringTable[view.viewId]
      val textValue = stringTable[view.textValue]
      val layout = stringTable[view.layout]
      val x = view.x + (parent?.x ?: 0)
      val y = view.y + (parent?.y ?: 0)
      val node = ViewNode(view.drawId, qualifiedName, layout, x, y, view.width, view.height, viewId, textValue)
      view.subViewList.map { loadView(it, node) }.forEach {
        node.children.add(it)
        it.parent = node
      }
      return node
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun modelModified(oldView: ViewNode?, newView: ViewNode?, structuralChange: Boolean) {
    if (structuralChange) {
      componentTreeModel.treeRoot = newView
    }
  }

  private fun modelChanged(oldView: InspectorModel, newView: InspectorModel) {
    componentTreeModel.treeRoot = newView.root
    oldView.selectionListeners.remove(this::selectionChanged)
    newView.selectionListeners.add(this::selectionChanged)
  }

  @Suppress("UNUSED_PARAMETER")
  private fun selectionChanged(oldView: ViewNode?, newView: ViewNode?) {
    if (newView == null) {
      componentTreeSelectionModel.selection = emptyList()
    }
    else {
      componentTreeSelectionModel.selection = Collections.singletonList(newView)
    }
  }

  private class InspectorViewNodeType : ViewNodeType<ViewNode>() {
    override val clazz = ViewNode::class.java

    override fun tagNameOf(node: ViewNode) = node.qualifiedName

    override fun idOf(node: ViewNode) = node.viewId?.name

    override fun textValueOf(node: ViewNode) = node.textValue

    override fun iconOf(node: ViewNode): Icon =
      AndroidDomElementDescriptorProvider.getIconForViewTag(node.unqualifiedName) ?: StudioIcons.LayoutEditor.Palette.UNKNOWN_VIEW

    override fun parentOf(node: ViewNode) = node.parent

    override fun childrenOf(node: ViewNode) = node.children
  }
}
