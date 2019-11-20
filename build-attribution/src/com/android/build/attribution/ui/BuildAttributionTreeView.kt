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
package com.android.build.attribution.ui

import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.TaskIssueBuganizerReporter
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.panels.TreeLinkListener
import com.android.build.attribution.ui.tree.AbstractBuildAttributionNode
import com.android.build.attribution.ui.tree.AnnotationProcessorsRoot
import com.android.build.attribution.ui.tree.BuildAttributionNodeRenderer
import com.android.build.attribution.ui.tree.BuildSummaryNode
import com.android.build.attribution.ui.tree.CriticalPathPluginsRoot
import com.android.build.attribution.ui.tree.CriticalPathTasksRoot
import com.android.build.attribution.ui.tree.PluginConfigurationTimeRoot
import com.android.build.attribution.ui.tree.TaskIssuesRoot
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.CachingSimpleNode
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTreeStructure
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.CardLayout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

@NonNls
private const val SPLITTER_PROPERTY = "BuildAttribution.Splitter.Proportion"

class BuildAttributionTreeView(
  private val reportData: BuildAttributionReportUiData,
  private val issueReporter: TaskIssueBuganizerReporter
) : ComponentContainer, TreeNodeSelector {

  private val disposed = AtomicBoolean()
  private val rootNode = RootNode()
  private val treeModel: StructureTreeModel<SimpleTreeStructure>
  private val panel = JPanel()
  private val tree: Tree
  private val handler: InfoViewHandler

  val isDisposed: Boolean
    get() = disposed.get()

  init {
    val treeStructure = SimpleTreeStructure.Impl(rootNode)
    treeModel = StructureTreeModel(treeStructure, this)
    tree = initTree(AsyncTreeModel(treeModel, this))

    panel.layout = BorderLayout()
    val componentsSplitter = OnePixelSplitter(SPLITTER_PROPERTY, 0.33f)
    componentsSplitter.setHonorComponentsMinimumSize(true)
    componentsSplitter.firstComponent = JPanel(CardLayout()).apply {
      add(ScrollPaneFactory.createScrollPane(tree, SideBorder.NONE), "Tree")
    }
    handler = InfoViewHandler(tree)
    componentsSplitter.secondComponent = handler.component
    panel.add(componentsSplitter, BorderLayout.CENTER)
  }

  private fun initTree(model: AsyncTreeModel): Tree {
    val tree = Tree(model)
    tree.isRootVisible = false
    TreeSpeedSearch(tree).comparator = SpeedSearchComparator(false)
    TreeUtil.installActions(tree)
    tree.cellRenderer = BuildAttributionNodeRenderer()
    return tree
  }

  override fun getComponent(): JComponent = panel

  override fun getPreferredFocusableComponent(): JComponent = tree

  override fun dispose() = disposed.set(true)

  override fun selectNode(node: SimpleNode) {
    treeModel.select(node, tree) { t: TreePath? ->
      Logger.getInstance(BuildAttributionTreeView::class.java).debug("Path selected with link: ${t}")
    }
  }

  fun setInitialSelection() {
    // We want CriticalPathPluginsRoot to be initially selected
    // as BuildSummary node does not have enough information to catch the user's attention at the moment.
    rootNode.children.find { it is CriticalPathPluginsRoot }?.let {
      treeModel.select(it, tree) {}
    }
  }

  /**
   * This class updates info shown on the right in response to tree nodes selection.
   */
  private class InfoViewHandler(tree: Tree) {
    private val viewMap = ContainerUtil.newConcurrentMap<String, JComponent>()
    private val enabledViewRef = AtomicReference<String>()
    private val panel: JPanel = JPanel(CardLayout())

    val component: JComponent
      get() = panel

    init {
      tree.addTreeSelectionListener { e ->
        if (e.path != null || e.isAddedPath) {
          updateViewFromNode(tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)
        }
      }
      tree.selectionPath?.let {
        updateViewFromNode(it.lastPathComponent as DefaultMutableTreeNode)
      }
    }

    private fun updateViewFromNode(node: DefaultMutableTreeNode?) {
      node?.userObject?.let { selectedNode ->
        if (selectedNode is AbstractBuildAttributionNode) {
          val name = selectedNode.nodeId
          if (name == enabledViewRef.get()) {
            return
          }
          if (!viewMap.containsKey(name)) {
            val infoPanel = wrapInfoPanel(selectedNode.component)
            viewMap[name] = infoPanel
            panel.add(infoPanel, name)
          }

          enabledViewRef.set(name)
          if (panel.componentCount > 1) {
            (panel.layout as CardLayout).show(panel, name)
          }
          else {
            // CardLayout.show does not trigger validation when there is just one component.
            panel.validate()
          }
        }
      }
    }

    private fun wrapInfoPanel(infoPanel: JComponent): JComponent = JBScrollPane(infoPanel).apply {
      border = BorderFactory.createEmptyBorder()
    }
  }

  private inner class RootNode : CachingSimpleNode(null) {
    val taskIssueLinkListener = object : TreeLinkListener<TaskIssueUiData> {
      override fun clickedOn(target: TaskIssueUiData) {
        findIssueRoot(target.type)?.findNodeForIssue(target)?.let { selectNode(it) }
      }
    }

    private fun findIssueRoot(type: TaskIssueType): TaskIssuesRoot? =
      children.asSequence().filterIsInstance<TaskIssuesRoot>().firstOrNull { it.issuesGroup.type == type }

    override fun buildChildren(): Array<SimpleNode> {
      val nodes = mutableListOf<SimpleNode>()
      nodes.add(BuildSummaryNode(reportData.buildSummary, this))
      nodes.add(CriticalPathPluginsRoot(reportData.criticalPathPlugins, this, this@BuildAttributionTreeView, issueReporter))
      nodes.add(CriticalPathTasksRoot(reportData.criticalPathTasks, this, taskIssueLinkListener))
      reportData.issues.forEach {
        nodes.add(TaskIssuesRoot(it, this, this@BuildAttributionTreeView, issueReporter))
      }
      nodes.add(PluginConfigurationTimeRoot(reportData.configurationTime, this, this@BuildAttributionTreeView))
      nodes.add(AnnotationProcessorsRoot(reportData.annotationProcessors, this, this@BuildAttributionTreeView))

      return nodes.toTypedArray()
    }
  }
}

interface TreeNodeSelector {
  fun selectNode(node: SimpleNode)
}
