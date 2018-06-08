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
package com.android.tools.idea.gradle.structure.configurables.variables

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.structure.configurables.PsContextImpl
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.ui.JBColor
import org.hamcrest.CoreMatchers.*
import org.junit.Assert.assertThat
import java.awt.Color
import java.util.function.Consumer
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class VariablesTableTest : AndroidGradleTestCase() {

  fun testModuleNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContextImpl(PsProjectImpl(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val rootNode = tableModel.root as DefaultMutableTreeNode
    assertThat(rootNode.childCount, equalTo(4))

    val projectNode = rootNode.getChildAt(0) as DefaultMutableTreeNode
    assertThat(tableModel.getValueAt(projectNode, 0) as String, equalTo("testModuleNodeDisplay"))
    assertThat(tableModel.getValueAt(projectNode, 1) as String, equalTo(""))
    assertThat(projectNode.childCount, not(0))

    val appNode = rootNode.getChildAt(1) as DefaultMutableTreeNode
    assertThat(tableModel.getValueAt(appNode, 0) as String, equalTo("app"))
    assertThat(tableModel.getValueAt(appNode, 1) as String, equalTo(""))
    assertThat(appNode.childCount, not(0))

    val javNode = rootNode.getChildAt(2) as DefaultMutableTreeNode
    assertThat(tableModel.getValueAt(javNode, 0) as String, equalTo("jav"))
    assertThat(tableModel.getValueAt(javNode, 1) as String, equalTo(""))
    assertThat(javNode.childCount, equalTo(0))

    val libNode = rootNode.getChildAt(3) as DefaultMutableTreeNode
    assertThat(tableModel.getValueAt(libNode, 0) as String, equalTo("lib"))
    assertThat(tableModel.getValueAt(libNode, 1) as String, equalTo(""))
    assertThat(libNode.childCount, equalTo(0))

    val row = variablesTable.tree.getRowForPath(TreePath(appNode.path))
    for (column in 0..2) {
      val component = variablesTable.getCellRenderer(row, column)
        .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
      assertThat<Color?>(component.background, equalTo(variablesTable.background))
    }
  }

  fun testStringVariableNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContextImpl(PsProjectImpl(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
    val variableNode =
      appNode.children().asSequence().find { "anotherVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    assertThat(variableNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.STRING))
    assertThat(variableNode.childCount, equalTo(0))
    assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("anotherVariable"))
    assertThat(tableModel.getValueAt(variableNode, 1) as String, equalTo("\"3.0.1\""))

    val row = variablesTable.tree.getRowForPath(TreePath(variableNode.path))
    for (column in 0..2) {
      val component = variablesTable.getCellRenderer(row, column)
        .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
      assertThat(component.background, equalTo(variablesTable.background))
    }
  }

  fun testBooleanVariableNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContextImpl(PsProjectImpl(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
    val variableNode =
      appNode.children().asSequence().find { "varBool" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    variablesTable.tree.expandPath(TreePath(variableNode.path))

    assertThat(variableNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.BOOLEAN))
    assertThat(variableNode.childCount, equalTo(0))
    assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("varBool"))
    assertThat(tableModel.getValueAt(variableNode, 1) as String, equalTo("true"))
  }

  fun testVariableVariableNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContextImpl(PsProjectImpl(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
    val variableNode =
      appNode.children().asSequence().find { "varRefString" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    variablesTable.tree.expandPath(TreePath(variableNode.path))

    assertThat(variableNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.REFERENCE))
    assertThat(variableNode.variable.resolvedValueType, equalTo(GradlePropertyModel.ValueType.STRING))
    assertThat(variableNode.childCount, equalTo(0))
    assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("varRefString"))
    assertThat(tableModel.getValueAt(variableNode, 1) as String, equalTo("variable1"))
    assertThat(tableModel.getValueAt(variableNode, 2) as String, equalTo("\"1.3\""))
  }

  fun testListNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContextImpl(PsProjectImpl(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val listNode =
      appNode.children().asSequence().find { "varProGuardFiles" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(listNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.LIST))
    assertThat(listNode.childCount, equalTo(3))
    assertThat(tableModel.getValueAt(listNode, 0) as String, equalTo("varProGuardFiles"))
    assertThat(tableModel.getValueAt(listNode, 1) as String, equalTo("[proguard-rules.txt, proguard-rules2.txt]"))

    variablesTable.tree.expandPath(TreePath(listNode.path))
    assertThat(tableModel.getValueAt(listNode, 0) as String, equalTo("varProGuardFiles"))
    assertThat(tableModel.getValueAt(listNode, 1) as String, equalTo(""))

    val firstElementNode = listNode.getChildAt(0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("0"))
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"proguard-rules.txt\""))

    val secondElementNode = listNode.getChildAt(1)
    assertThat(tableModel.getValueAt(secondElementNode, 0) as String, equalTo("1"))
    assertThat(tableModel.getValueAt(secondElementNode, 1) as String, equalTo("\"proguard-rules2.txt\""))

    val emptyElement = listNode.getChildAt(2)
    assertThat(tableModel.getValueAt(emptyElement, 0) as String, equalTo(""))
    assertThat(tableModel.getValueAt(emptyElement, 1) as String, equalTo(""))

    val row = variablesTable.tree.getRowForPath(TreePath(listNode.path))
    for (column in 0..2) {
      val component = variablesTable.getCellRenderer(row, column)
        .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
      assertThat(component.background, equalTo(variablesTable.background))

      val firstChild = variablesTable.getCellRenderer(row + 1, column)
        .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row + 1,  column), false, false, row + 1, column)
      assertThat(firstChild.background.rgb, equalTo(JBColor.LIGHT_GRAY.rgb))

      val secondChild = variablesTable.getCellRenderer(row + 2, column)
        .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row + 2,  column), false, false, row + 2, column)
      assertThat(secondChild.background, equalTo(variablesTable.background))
    }
  }

  fun testMapNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContextImpl(PsProjectImpl(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val mapNode =
      appNode.children().asSequence().find { "mapVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(mapNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.MAP))
    assertThat(mapNode.childCount, equalTo(3))
    assertThat(tableModel.getValueAt(mapNode, 0) as String, equalTo("mapVariable"))
    assertThat(tableModel.getValueAt(mapNode, 1) as String, equalTo("[a=\"double\" quotes, b='single' quotes]"))

    variablesTable.tree.expandPath(TreePath(mapNode.path))
    assertThat(tableModel.getValueAt(mapNode, 0) as String, equalTo("mapVariable"))
    assertThat(tableModel.getValueAt(mapNode, 1) as String, equalTo(""))

    val firstElementNode = mapNode.getChildAt(0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("a"))
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"\"double\" quotes\""))

    val secondElementNode = mapNode.getChildAt(1)
    assertThat(tableModel.getValueAt(secondElementNode, 0) as String, equalTo("b"))
    assertThat(tableModel.getValueAt(secondElementNode, 1) as String, equalTo("\"'single' quotes\""))

    val emptyElement = mapNode.getChildAt(2)
    assertThat(tableModel.getValueAt(emptyElement, 0) as String, equalTo(""))
    assertThat(tableModel.getValueAt(emptyElement, 1) as String, equalTo(""))

    val row = variablesTable.tree.getRowForPath(TreePath(mapNode.path))
    for (column in 0..2) {
      val component = variablesTable.getCellRenderer(row, column)
        .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
      assertThat(component.background, equalTo(variablesTable.background))

      val firstChild = variablesTable.getCellRenderer(row + 1, column)
        .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row + 1,  column), false, false, row + 1, column)
      assertThat(firstChild.background.rgb, equalTo(JBColor.LIGHT_GRAY.rgb))

      val secondChild = variablesTable.getCellRenderer(row + 2, column)
        .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row + 2,  column), false, false, row + 2, column)
      assertThat(secondChild.background, equalTo(variablesTable.background))
    }
  }

  fun testModuleNodeRename() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContextImpl(PsProjectImpl(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild
    assertThat(tableModel.isCellEditable(appNode, 0), equalTo(false))
  }

  fun testVariableNodeRename() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val psContext = PsContextImpl(psProject, testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    val variableNode =
      appNode.children().asSequence().find { "anotherVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    variablesTable.tree.expandPath(TreePath(variableNode.path))
    assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("anotherVariable"))
    assertThat(tableModel.isCellEditable(variableNode, 0), equalTo(true))

    tableModel.setValueAt("renamed", variableNode, 0)
    assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("renamed"))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    val variableNames = newAppNode.children().asSequence().map { it.toString() }.toList()
    assertThat(variableNames, hasItem("renamed"))
    assertThat(variableNames, not(hasItem("anotherVariable")))
  }

  fun testListNodeRename() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val psContext = PsContextImpl(psProject, testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val listNode =
      appNode.children().asSequence().find { "varProGuardFiles" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(listNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.LIST))

    variablesTable.tree.expandPath(TreePath(listNode.path))
    val firstElementNode = listNode.getChildAt(0)
    assertThat(tableModel.isCellEditable(firstElementNode, 0), equalTo(false))
  }

  fun testMapNodeRename() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val psContext = PsContextImpl(psProject, testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val mapNode =
      appNode.children().asSequence().find { "mapVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(mapNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.MAP))

    variablesTable.tree.expandPath(TreePath(mapNode.path))
    val firstElementNode = mapNode.getChildAt(0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("a"))
    assertThat(tableModel.isCellEditable(firstElementNode, 0), equalTo(true))

    tableModel.setValueAt("renamed", firstElementNode, 0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("renamed"))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    val newMapNode =
      newAppNode.children().asSequence().find { "mapVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    val keyNames = newMapNode.children().asSequence().map { it.toString() }.toList()
    assertThat(keyNames, hasItem("renamed"))
    assertThat(keyNames, not(hasItem("a")))
  }

  fun testModuleNodeSetValue() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val psContext = PsContextImpl(psProject, testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild
    assertThat(tableModel.isCellEditable(appNode, 1), equalTo(false))
  }

  fun testVariableNodeSetValue() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val psContext = PsContextImpl(psProject, testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    val variableNode =
      appNode.children().asSequence().find { "anotherVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    variablesTable.tree.expandPath(TreePath(variableNode.path))
    assertThat(tableModel.getValueAt(variableNode, 1) as String, equalTo("\"3.0.1\""))
    assertThat(tableModel.isCellEditable(variableNode, 1), equalTo(true))

    tableModel.setValueAt("\"3.0.1\"", variableNode, 1)
    assertThat(variableNode.variable.model.isModified, equalTo(false))

    tableModel.setValueAt("new value", variableNode, 1)
    assertThat(tableModel.getValueAt(variableNode, 1) as String, equalTo("\"new value\""))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    val newVariableNode =
      newAppNode.children().asSequence().find { "anotherVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(newVariableNode.getUnresolvedValue(false), equalTo("\"new value\""))
  }

  fun testListNodeSetValue() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val psContext = PsContextImpl(psProject, testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val listNode =
      appNode.children().asSequence().find { "varProGuardFiles" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(listNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.LIST))
    assertThat(tableModel.isCellEditable(listNode, 1), equalTo(false))

    variablesTable.tree.expandPath(TreePath(listNode.path))
    val firstElementNode = listNode.getChildAt(0) as VariablesTable.ListItemNode
    assertThat(tableModel.isCellEditable(listNode, 1), equalTo(false))
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"proguard-rules.txt\""))
    assertThat(tableModel.isCellEditable(firstElementNode, 1), equalTo(true))

    tableModel.setValueAt("\"proguard-rules.txt\"", firstElementNode, 1)
    assertThat(firstElementNode.variable.model.isModified, equalTo(false))

    tableModel.setValueAt("new value", firstElementNode, 1)
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"new value\""))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    val newListNode =
      newAppNode.children().asSequence().find { "varProGuardFiles" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat((newListNode.getChildAt(0) as VariablesTable.ListItemNode).getUnresolvedValue(false), equalTo("\"new value\""))
  }

  fun testMapNodeSetValue() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val psContext = PsContextImpl(psProject, testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val mapNode =
      appNode.children().asSequence().find { "mapVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(mapNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.MAP))
    assertThat(tableModel.isCellEditable(mapNode, 1), equalTo(false))

    variablesTable.tree.expandPath(TreePath(mapNode.path))
    val firstElementNode = mapNode.getChildAt(0) as VariablesTable.MapItemNode
    assertThat(tableModel.isCellEditable(mapNode, 1), equalTo(false))
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"\"double\" quotes\""))
    assertThat(tableModel.isCellEditable(firstElementNode, 1), equalTo(true))

    tableModel.setValueAt("\"\"double\" quotes\"", firstElementNode, 1)
    assertThat(firstElementNode.variable.model.isModified, equalTo(false))

    tableModel.setValueAt("new value", firstElementNode, 1)
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"new value\""))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    val newMapNode =
      newAppNode.children().asSequence().find { "mapVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat((newMapNode.getChildAt(0) as VariablesTable.MapItemNode).getUnresolvedValue(false), equalTo("\"new value\""))
  }

  fun testAddSimpleVariable() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val psContext = PsContextImpl(psProject, testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    assertThat(appNode.children().asSequence().map { it.toString() }.toSet(), not(hasItem("newVariable")))

    variablesTable.tree.selectionPath = TreePath(appNode.path)
    variablesTable.addVariable(GradlePropertyModel.ValueType.STRING)
    val editorComp = variablesTable.editorComponent as JPanel
    val textBox = editorComp.components.first { it is VariableAwareTextBox } as VariableAwareTextBox
    textBox.setText("newVariable")
    variablesTable.editingStopped(null)

    val variableNode =
      appNode.children().asSequence().find { "newVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    variableNode.setValue("new value")

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    val newVariableNode =
      newAppNode.children().asSequence().find { "newVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(newVariableNode.getUnresolvedValue(false), equalTo("\"new value\""))
  }

  fun testAddList() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val psContext = PsContextImpl(psProject, testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    assertThat(appNode.children().asSequence().map { it.toString() }.toSet(), not(hasItem("newList")))

    variablesTable.tree.selectionPath = TreePath(appNode.path)
    variablesTable.addVariable(GradlePropertyModel.ValueType.LIST)
    val editorComp = variablesTable.editorComponent as JPanel
    val textBox = editorComp.components.first { it is VariableAwareTextBox } as VariableAwareTextBox
    textBox.setText("newList")
    variablesTable.editingStopped(null)

    val variableNode =
      appNode.children().asSequence().find { "newList" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(variableNode.childCount, equalTo(1))
    assertThat(variablesTable.tree.isExpanded(TreePath(variableNode.path)), equalTo(true))

    tableModel.setValueAt("list item", variableNode.getChildAt(0), 1)
    assertThat(variableNode.childCount, equalTo(2))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    val newListNode =
      newAppNode.children().asSequence().find { "newList" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode

    val firstElementNode = newListNode.getChildAt(0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("0"))
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"list item\""))

    val secondElementNode = newListNode.getChildAt(1)
    assertThat(tableModel.getValueAt(secondElementNode, 0) as String, equalTo(""))
    assertThat(tableModel.getValueAt(secondElementNode, 1) as String, equalTo(""))
  }

  fun testAddMap() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val psContext = PsContextImpl(psProject, testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    assertThat(appNode.children().asSequence().map { it.toString() }.toSet(), not(hasItem("newMap")))

    variablesTable.tree.selectionPath = TreePath(appNode.path)
    variablesTable.addVariable(GradlePropertyModel.ValueType.MAP)
    val editorComp = variablesTable.editorComponent as JPanel
    val textBox = editorComp.components.first { it is VariableAwareTextBox } as VariableAwareTextBox
    textBox.setText("newMap")
    variablesTable.editingStopped(null)

    val variableNode =
      appNode.children().asSequence().find { "newMap" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(variableNode.childCount, equalTo(1))
    assertThat(variablesTable.tree.isExpanded(TreePath(variableNode.path)), equalTo(true))

    tableModel.setValueAt("key", variableNode.getChildAt(0), 0)
    tableModel.setValueAt("value", variableNode.getChildAt(0), 1)
    assertThat(variableNode.childCount, equalTo(2))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    val newMapNode =
      newAppNode.children().asSequence().find { "newMap" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode

    val firstElementNode = newMapNode.getChildAt(0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("key"))
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"value\""))

    val secondElementNode = newMapNode.getChildAt(1)
    assertThat(tableModel.getValueAt(secondElementNode, 0) as String, equalTo(""))
    assertThat(tableModel.getValueAt(secondElementNode, 1) as String, equalTo(""))
  }

  fun testVariableNodeDelete() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val psContext = PsContextImpl(psProject, testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    val childCount = appNode.childCount
    val variableNode =
      appNode.children().asSequence().find { "anotherVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    variablesTable.tree.selectionPath = TreePath(variableNode.path)
    variablesTable.deleteSelectedVariables()

    val variableNames = appNode.children().asSequence().map { it.toString() }.toList()
    assertThat(variableNames, not(hasItem("anotherVariable")))
    assertThat(appNode.childCount, equalTo(childCount - 1))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    val newVariableNames = newAppNode.children().asSequence().map { it.toString() }.toList()
    assertThat(newVariableNames, not(hasItem("anotherVariable")))
    assertThat(newAppNode.childCount, equalTo(childCount - 1))
  }

  fun testListNodeDelete() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val psContext = PsContextImpl(psProject, testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val listNode =
      appNode.children().asSequence().find { "varProGuardFiles" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(listNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.LIST))
    val childCount = listNode.childCount

    variablesTable.tree.expandPath(TreePath(listNode.path))
    val firstElementNode = listNode.getChildAt(0) as VariablesTable.ListItemNode
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("0"))
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"proguard-rules.txt\""))

    variablesTable.tree.selectionPath = TreePath(firstElementNode.path)
    variablesTable.deleteSelectedVariables()

    val listNodeFirstChild = listNode.getChildAt(0) as VariablesTable.ListItemNode
    assertThat(tableModel.getValueAt(listNodeFirstChild, 0) as String, equalTo("0"))
    assertThat(tableModel.getValueAt(listNodeFirstChild, 1) as String, equalTo("\"proguard-rules2.txt\""))
    assertThat(listNode.childCount, equalTo(childCount - 1))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    val newListNode =
      newAppNode.children().asSequence().find { "varProGuardFiles" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    variablesTable.tree.expandPath(TreePath(listNode.path))
    val newFirstElementNode = newListNode.getChildAt(0) as VariablesTable.ListItemNode
    assertThat(tableModel.getValueAt(newFirstElementNode, 0) as String, equalTo("0"))
    assertThat(tableModel.getValueAt(newFirstElementNode, 1) as String, equalTo("\"proguard-rules2.txt\""))
    assertThat(newListNode.childCount, equalTo(childCount - 1))
  }

  fun testMapNodeDelete() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProjectImpl(project)
    val psContext = PsContextImpl(psProject, testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val mapNode =
      appNode.children().asSequence().find { "mapVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(mapNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.MAP))
    val childCount = mapNode.childCount

    variablesTable.tree.expandPath(TreePath(mapNode.path))
    val firstElementNode = mapNode.getChildAt(0) as VariablesTable.MapItemNode
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("a"))
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"\"double\" quotes\""))

    variablesTable.tree.selectionPath = TreePath(firstElementNode.path)
    variablesTable.deleteSelectedVariables()

    val mapNodeFirstChild = mapNode.getChildAt(0) as VariablesTable.MapItemNode
    assertThat(tableModel.getValueAt(mapNodeFirstChild, 0) as String, equalTo("b"))
    assertThat(tableModel.getValueAt(mapNodeFirstChild, 1) as String, equalTo("\"'single' quotes\""))
    assertThat(mapNode.childCount, equalTo(childCount - 1))

    psProject.applyAllChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as VariablesTable.ModuleNode
    val newMapNode =
      newAppNode.children().asSequence().find { "mapVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    val newFirstElementNode = mapNode.getChildAt(0) as VariablesTable.MapItemNode
    assertThat(tableModel.getValueAt(newFirstElementNode, 0) as String, equalTo("b"))
    assertThat(tableModel.getValueAt(newFirstElementNode, 1) as String, equalTo("\"'single' quotes\""))
    assertThat(newMapNode.childCount, equalTo(childCount - 1))
  }
}

private val DefaultMutableTreeNode.appModuleChild: Any?
  get() = children().asSequence().find { it.toString() == "app" } as VariablesTable.ModuleNode

private fun PsProject.applyAllChanges() {
  if (isModified) {
    applyChanges()
  }
  forEachModule(Consumer { module ->
    if (module.isModified) {
      module.applyChanges()
    }
  })
}
