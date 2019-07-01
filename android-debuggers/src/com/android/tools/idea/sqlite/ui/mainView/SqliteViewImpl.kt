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
package com.android.tools.idea.sqlite.ui.mainView

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.renderers.SchemaTreeCellRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.UIBundle
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.UiDecorator
import com.intellij.ui.tabs.impl.JBEditorTabs
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.OverlayLayout
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

@UiThread
class SqliteViewImpl(
  project: Project,
  parentDisposable: Disposable
) : SqliteView {
  private val viewContext = SqliteViewContext()
  private val listeners = mutableListOf<SqliteViewListener>()

  private val rootPanel = JPanel()
  private val workBench: WorkBench<SqliteViewContext> = WorkBench(project, "Sqlite", null)
  private var sqliteEditorPanel = SqliteEditorPanel()
  private val defaultUiPanel = DefaultUiPanel()
  private val tabs = JBEditorTabs(project, ActionManager.getInstance(), IdeFocusManager.getInstance(project), project)

  override val component: JComponent = rootPanel

  private val openTabs = mutableMapOf<String, TabInfo>()

  init {
    Disposer.register(parentDisposable, workBench)

    val definitions = mutableListOf<ToolWindowDefinition<SqliteViewContext>>()
    definitions.add(SchemaPanelToolContent.getDefinition())
    workBench.init(sqliteEditorPanel.mainPanel, viewContext, definitions)

    rootPanel.layout = OverlayLayout(rootPanel)
    rootPanel.add(defaultUiPanel.rootPanel)
    rootPanel.add(workBench)
    workBench.isVisible = false

    defaultUiPanel.label.font = AdtUiUtils.EMPTY_TOOL_WINDOW_FONT
    defaultUiPanel.label.foreground = UIUtil.getInactiveTextColor()
  }

  override fun addListener(listener: SqliteViewListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: SqliteViewListener) {
    listeners.remove(listener)
  }

  override fun startLoading(text: String) {
    workBench.isVisible = true
    defaultUiPanel.rootPanel.isVisible = false
    workBench.setLoadingText(text)
  }

  override fun stopLoading() {
    sqliteEditorPanel.openSqlEvalDialog.addActionListener { listeners.forEach{ it.openSqliteEvaluatorActionInvoked() } }

    tabs.apply {
      isTabDraggingEnabled = true
      setUiDecorator { UiDecorator.UiDecoration(null, JBUI.insets(4, 10)) }
      addTabMouseListener(TabMouseListener())
    }

    sqliteEditorPanel.tabsRoot.add(tabs)
  }

  override fun displaySchema(schema: SqliteSchema) {
    viewContext.schema = schema
    viewContext.schemaTree?.removeKeyListener(keyAdapter)
    doubleClickListener.uninstall(viewContext.schemaTree)

    setupSchemaTree()
  }

  override fun displayTable(tableName: String, component: JComponent) {
    val tab = createSqliteExplorerTab(tableName, component)
    tabs.addTab(tab)
    tabs.select(tab, true)
    openTabs[tableName] = tab
  }

  override fun focusTable(tableName: String) {
    tabs.select(openTabs[tableName]!!, true)
  }

  override fun closeTable(tableName: String) {
    val tab = openTabs.remove(tableName)
    tabs.removeTab(tab)
  }

  override fun reportErrorRelatedToService(service: SqliteService, message: String, t: Throwable) {
    val errorMessage = if (t.message != null) "$message: ${t.message}" else message
    workBench.loadingStopped(errorMessage)
  }

  private fun createSqliteExplorerTab(tableName: String, tabContent: JComponent): TabInfo {
    val tab = TabInfo(tabContent)

    val tabActionGroup = DefaultActionGroup()
    tabActionGroup.add(object : AnAction("Close tabs", "Click to close tab", AllIcons.Actions.Close) {
      override fun actionPerformed(e: AnActionEvent) {
        listeners.forEach { it.closeTableActionInvoked(tableName) }
      }

      override fun update(e: AnActionEvent) {
        e.presentation.hoveredIcon = AllIcons.Actions.CloseHovered
        e.presentation.isVisible = true
        e.presentation.text = UIBundle.message("tabbed.pane.close.tab.action.name")
      }
    })
    tab.setTabLabelActions(tabActionGroup, ActionPlaces.EDITOR_TAB)
    tab.icon = AllIcons.Nodes.DataTables
    tab.text = tableName
    return tab
  }

  private fun setupSchemaTree() {
    if (viewContext.schemaTree == null || viewContext.schema == null)
      return

    val tree = viewContext.schemaTree!!
    val schema = viewContext.schema!!

    tree.cellRenderer = SchemaTreeCellRenderer()
    val root = DefaultMutableTreeNode("Tables")
    schema.tables.forEach { table ->
      val tableNode = DefaultMutableTreeNode(table)
      table.columns.forEach { column -> tableNode.add(DefaultMutableTreeNode(column)) }
      root.add(tableNode)
    }
    tree.model = DefaultTreeModel(root)
    tree.toggleClickCount = 0

    tree.addKeyListener(keyAdapter)
    doubleClickListener.installOn(tree)
  }

  private fun fireAction(tree: Tree, e: InputEvent) {
    val lastPathComponent = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode

    val userObject = lastPathComponent?.userObject
    if (userObject is SqliteTable) {
      listeners.forEach { l -> l.tableNodeActionInvoked(userObject) }
      e.consume()
    }
  }

  private val keyAdapter = object : KeyAdapter() {
    override fun keyPressed(event: KeyEvent) {
      if (event.keyCode == KeyEvent.VK_ENTER) {
        viewContext.schemaTree?.let { fireAction(it, event) }
      }
    }
  }

  private val doubleClickListener = object : DoubleClickListener() {
    override fun onDoubleClick(event: MouseEvent): Boolean {
      viewContext.schemaTree?.let { fireAction(it, event) }
      return true
    }
  }

  private inner class TabMouseListener : MouseAdapter() {
    override fun mouseReleased(e: MouseEvent) {
      if(e.button == 2) {
        // TODO (b/135525331)
        // mouse wheel click
        //tabs.removeTab()
      }
    }
  }

  class SchemaPanelToolContent : ToolContent<SqliteViewContext> {
    companion object {
      fun getDefinition(): ToolWindowDefinition<SqliteViewContext> {
        return ToolWindowDefinition(
          "Schema",
          AllIcons.Nodes.DataTables,
          "SCHEMA",
          Side.LEFT,
          Split.TOP,
          AutoHide.DOCKED
        ) { SchemaPanelToolContent() }
      }
    }

    private val schemaPanel = SqliteSchemaPanel()

    override fun getComponent(): JComponent {
      return schemaPanel.component
    }

    override fun dispose() {
    }

    /**
     * Initialize the UI from the passed in [SqliteViewContext]
     */
    override fun setToolContext(toolContext: SqliteViewContext?) {
      toolContext?.schemaTree = schemaPanel.tree
    }
  }

  class SqliteViewContext {
    var schema: SqliteSchema? = null
    var schemaTree: Tree? = null
  }
}