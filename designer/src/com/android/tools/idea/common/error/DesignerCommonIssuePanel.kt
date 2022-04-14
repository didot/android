/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.common.error

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.error.IssuePanelService.Companion.SELECTED_ISSUES
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.visual.VisualizationToolWindowFactory
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

private const val TOOLBAR_ACTIONS_ID = "Android.Designer.IssuePanel.ToolbarActions"
/**
 * The id of pop action group which is shown when right-clicking a tree node.
 */
private const val POPUP_HANDLER_ACTION_ID = "Android.Designer.IssuePanel.TreePopup"
private val KEY_DETAIL_VISIBLE = DesignerCommonIssuePanel::class.java.name + "_detail_visibility"

/**
 * The issue panel to load the issues from Layout Editor and Layout Validation Tool.
 */
class DesignerCommonIssuePanel(parentDisposable: Disposable, private val project: Project, private val treeModel: DesignerCommonIssueModel,
                               val issueProvider: DesignerCommonIssueProvider<Any>) : Disposable {

  var sidePanelVisible = PropertiesComponent.getInstance(project).getBoolean(KEY_DETAIL_VISIBLE, true)
    set(value) {
      field = value
      setSidePanelVisibility(value)
      PropertiesComponent.getInstance(project).setValue(KEY_DETAIL_VISIBLE, value)
    }

  private val rootPanel = object : JPanel(BorderLayout()), DataProvider {
    override fun getData(dataId: String): Any? {
      val node = getSelectedNode() ?: return null
      if (CommonDataKeys.NAVIGATABLE.`is`(dataId)) {
        return node.getNavigatable()
      }
      if (PlatformDataKeys.SELECTED_ITEM.`is`(dataId)) {
        return node
      }
      if (PlatformDataKeys.VIRTUAL_FILE.`is`(dataId)) {
        return node.getVirtualFile()
      }
      if (SELECTED_ISSUES.`is`(dataId)) {
        return when (node) {
          is IssuedFileNode -> node.issues
          is NoFileNode -> node.issues
          is IssueNode -> listOf(node.issue)
          else -> emptyList()
        }
      }
      return null
    }
  }

  private val tree: Tree
  private val splitter: OnePixelSplitter

  init {
    Disposer.register(parentDisposable, this)

    treeModel.root = DesignerCommonIssueRoot(project, issueProvider)
    issueProvider.registerUpdateListener {
      updateTree()
    }
    val asyncModel = AsyncTreeModel(treeModel, this)
    tree = Tree(asyncModel)
    tree.emptyText.text = "No design issue is found"
    PopupHandler.installPopupMenu(tree, POPUP_HANDLER_ACTION_ID, "Android.Designer.IssuePanel.TreePopup")

    tree.isRootVisible = false
    tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

    EditSourceOnDoubleClickHandler.install(tree)
    EditSourceOnEnterKeyHandler.install(tree)

    val toolbarActionGroup = ActionManager.getInstance().getAction(TOOLBAR_ACTIONS_ID) as ActionGroup
    val toolbar = ActionManager.getInstance().createActionToolbar(javaClass.name, toolbarActionGroup, false)
    toolbar.targetComponent = rootPanel
    UIUtil.addBorder(toolbar.component, CustomLineBorder(JBUI.insetsRight(1)))
    rootPanel.add(toolbar.component, BorderLayout.WEST)

    splitter = OnePixelSplitter(false, 0.5f, 0.3f, 0.7f)
    splitter.proportion = 0.5f
    splitter.firstComponent = ScrollPaneFactory.createScrollPane(tree, true)
    splitter.secondComponent = null
    splitter.setResizeEnabled(true)
    rootPanel.add(splitter, BorderLayout.CENTER)

    tree.addTreeSelectionListener {
      val newSelectedNode = it?.newLeadSelectionPath?.lastPathComponent
      if (newSelectedNode == null) {
        splitter.secondComponent = null
        splitter.revalidate()
        return@addTreeSelectionListener
      }
      val selectedNode = newSelectedNode as? DesignerCommonIssueNode ?: return@addTreeSelectionListener
      if (sidePanelVisible) {
        val sidePanel = createSidePanel(selectedNode)
        splitter.secondComponent = sidePanel
        splitter.revalidate()
      }
      // TODO(b/222110455): Can we have better way to trigger the refreshing of Layout Validation or other design tools?
      //                    Refactor to remove the dependency of VisualizationToolWindowFactory.
      val window = ToolWindowManager.getInstance(project).getToolWindow(VisualizationToolWindowFactory.TOOL_WINDOW_ID)
      if (window != null) {
        DataManager.getInstance().getDataContext(window.component).getData(DESIGN_SURFACE)?.let { surface ->
          (selectedNode as? IssueNode)?.issue?.let { issue ->
            if (issue.source is VisualLintIssueProvider.VisualLintIssueSource) {
              surface.issueListener.onIssueSelected(issue)
            }
          }
          surface.revalidateScrollArea()
          surface.repaint()
        }
      }
    }
  }

  fun getComponent(): JComponent = rootPanel

  private fun updateTree() {
    val selectedNode = tree.lastSelectedPathComponent as? DesignerCommonIssueNode
    treeModel.structureChanged(null)
    val promiseExpand = TreeUtil.promiseExpandAll(tree)
    if (selectedNode != null) {
      promiseExpand.onSuccess {
        TreeUtil.promiseSelect(tree, DesignerIssueNodeVisitor(selectedNode))
      }
    }
  }

  fun setHiddenSeverities(hiddenSeverities: Set<Int>) {
    val wasEmpty = treeModel.root?.getChildren()?.isEmpty() ?: true
    issueProvider.filter = { issue ->
      !hiddenSeverities.contains(issue.severity.myVal)
    }
    treeModel.structureChanged(null)
    if (wasEmpty) {
      TreeUtil.promiseExpandAll(tree)
    }
  }

  private fun getSelectedNode(): DesignerCommonIssueNode? {
    return TreeUtil.getLastUserObject(DesignerCommonIssueNode::class.java, tree.selectionPath)
  }

  private fun setSidePanelVisibility(visible: Boolean) {
    if (!visible) {
      splitter.secondComponent = null
    }
    else {
      splitter.secondComponent = createSidePanel(tree.lastSelectedPathComponent as? DesignerCommonIssueNode)
    }
    splitter.revalidate()
  }

  private fun createSidePanel(node: DesignerCommonIssueNode?): JComponent? {
    val issueNode = node as? IssueNode ?: return null

    val sidePanel = DesignerCommonIssueSidePanel(project, issueNode.issue, issueNode.getVirtualFile(), this)
    val previewEditor = sidePanel.editor
    val navigable = issueNode.getNavigatable()
    if (previewEditor != null && navigable != null) {
      navigable.navigateIn(previewEditor)
    }

    return sidePanel
  }

  override fun dispose() = Unit
}

/**
 * Used to find the target [DesignerCommonIssueNode] in the [com.intellij.ui.treeStructure.Tree].
 */
class DesignerIssueNodeVisitor(private val node: DesignerCommonIssueNode) : TreeVisitor {

  override fun visit(path: TreePath) : TreeVisitor.Action {
    return when (val visitedNode = path.lastPathComponent) {
      !is DesignerCommonIssueNode -> TreeVisitor.Action.CONTINUE
      else -> compareNode(visitedNode, node)
    }
  }

  private fun compareNode(node1: DesignerCommonIssueNode?, node2: DesignerCommonIssueNode?) : TreeVisitor.Action {
    if (node1 == null || node2 == null) {
      return if (node1 == null && node2 == null) TreeVisitor.Action.INTERRUPT else TreeVisitor.Action.CONTINUE
    }
    if (node1::class != node2::class) {
      return TreeVisitor.Action.CONTINUE
    }
    return when (node1) {
      is IssuedFileNode -> visitIssuedFileNode(node1, node2 as IssuedFileNode)
      is NoFileNode -> visitNoFileNode(node1, node2 as NoFileNode)
      is IssueNode -> visitIssueNode(node1, node2 as IssueNode)
      is DesignerCommonIssueRoot -> if (node1 === node2) TreeVisitor.Action.INTERRUPT else TreeVisitor.Action.CONTINUE
      else -> TreeVisitor.Action.CONTINUE
    }
  }

  private fun visitIssuedFileNode(node1: IssuedFileNode, node2: IssuedFileNode): TreeVisitor.Action {
    return if (node1.file != node2.file) TreeVisitor.Action.CONTINUE else {
      compareNode(node1.parentDescriptor?.element as? DesignerCommonIssueNode,
                  node2.parentDescriptor?.element as? DesignerCommonIssueNode)
    }
  }

  private fun visitNoFileNode(node1: NoFileNode, node2: NoFileNode): TreeVisitor.Action {
    return if (node1.name != node2.name) TreeVisitor.Action.CONTINUE else {
      compareNode(node1.parentDescriptor?.element as? DesignerCommonIssueNode,
                  node2.parentDescriptor?.element as? DesignerCommonIssueNode)
    }
  }

  private fun visitIssueNode(node1: IssueNode, node2: IssueNode): TreeVisitor.Action {
    val issue1 = node1.issue
    val issue2 = node2.issue

    // It would be complicated if the issues are VisualLintRenderIssue, compare the parents first.
    val actionAfterComparingParents = compareNode(node1.parentDescriptor?.element as? DesignerCommonIssueNode,
                                                  node2.parentDescriptor?.element as? DesignerCommonIssueNode)
    if (actionAfterComparingParents == TreeVisitor.Action.CONTINUE) {
      return TreeVisitor.Action.CONTINUE
    }

    if (issue1 is VisualLintRenderIssue || issue2 is VisualLintRenderIssue) {
      if (issue1 !is VisualLintRenderIssue || issue2 !is VisualLintRenderIssue) {
        return TreeVisitor.Action.CONTINUE
      }
      val files1 = issue1.models.toList()
      val files2 = issue2.models.toList()
      if (files1 != files2) {
        return TreeVisitor.Action.CONTINUE
      }
      val comparator = compareBy<NlComponent>({ it.id }, { it.tagName }, { createIndexString(it) })

      val visitedNodeIterator = issue1.components.sortedWith(comparator).iterator()
      val nodeIterator = issue2.components.sortedWith(comparator).iterator()

      while (visitedNodeIterator.hasNext() && nodeIterator.hasNext()) {
        if (comparator.compare(visitedNodeIterator.next(), nodeIterator.next()) != 0) {
          return TreeVisitor.Action.CONTINUE
        }
      }
      return if (visitedNodeIterator.hasNext() || nodeIterator.hasNext()) TreeVisitor.Action.CONTINUE else TreeVisitor.Action.INTERRUPT
    }

    return if (issue1 == issue2) TreeVisitor.Action.INTERRUPT else TreeVisitor.Action.CONTINUE
  }

  /**
   * Sequence of index to serialize the relative position of a [NlComponent] in the XML file.
   */
  private fun createIndexString(component: NlComponent): String {
    val orders = mutableListOf<Int>()
    var current: NlComponent? = component
    while (true) {
      val parent = current?.parent ?: break
      val order = parent.children.indexOf(current)
      orders.add(order)
      current = parent
    }
    return orders.reversed().joinToString(",")
  }
}
