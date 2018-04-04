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
package com.android.tools.idea.naveditor.property.inspector

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.editors.EnumEditor
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property.NavDestinationArgumentsProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.property.editors.TextEditor
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.uibuilder.property.editors.NlTableCellEditor
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.InplaceButton
import com.intellij.ui.table.JBTable
import org.mockito.Mockito.*
import java.awt.Component
import java.awt.Container

class NavDestinationArgumentsInspectorProviderTest : NavTestCase() {
  fun testIsApplicable() {
    val provider = NavDestinationArgumentsInspectorProvider()
    val surface = mock(NavDesignSurface::class.java)
    val manager = NavPropertiesManager(myFacet, surface)
    Disposer.register(myRootDisposable, surface)
    Disposer.register(myRootDisposable, manager)
    val component1 = mock(NlComponent::class.java)
    val component2 = mock(NlComponent::class.java)
    val model = mock(NlModel::class.java)
    `when`(component1.model).thenReturn(model)
    `when`(component2.model).thenReturn(model)
    `when`(model.facet).thenReturn(myFacet)

    // Simple case: one component, arguments property
    assertTrue(provider.isApplicable(listOf(component1),
        mapOf("Arguments" to NavDestinationArgumentsProperty(listOf(component1), manager)), manager))
    // One component, arguments + other property
    assertTrue(provider.isApplicable(listOf(component1),
        mapOf("Arguments" to NavDestinationArgumentsProperty(listOf(component1), manager), "foo" to mock(NlProperty::class.java)), manager))
    // Two components
    assertFalse(provider.isApplicable(listOf(component1, component2),
        mapOf("Arguments" to NavDestinationArgumentsProperty(listOf(component1, component2), manager)), manager))
    // zero components
    assertFalse(provider.isApplicable(listOf(), mapOf("Arguments" to NavDestinationArgumentsProperty(listOf(), manager)), manager))
    // Non-arguments property only
    assertFalse(provider.isApplicable(listOf(component1), mapOf("foo" to mock(NlProperty::class.java)), manager))
  }

  fun testListContent() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          argument("arg1", type = "reference", value = "value1")
          argument("arg2", value = "value2")
        }
        fragment("f2")
        activity("activity")
      }
    }

    val manager = NavPropertiesManager(myFacet, model.surface)
    Disposer.register(myRootDisposable, manager)
    val navInspectorProviders = spy(NavInspectorProviders(manager, myRootDisposable))
    `when`(navInspectorProviders.providers).thenReturn(listOf(NavDestinationArgumentsInspectorProvider()))

    val panel = NavInspectorPanel(myRootDisposable)
    panel.setComponent(listOf(model.find("f1")!!), HashBasedTable.create<String, String, NlProperty>(), manager)

    @Suppress("UNCHECKED_CAST")
    val argumentsTable = flatten(panel).find { it.name == NAV_ARGUMENTS_COMPONENT_NAME }!! as JBTable
    val plusButton = flatten(panel).find { it is InplaceButton && it.toolTipText == "Add Argument" } as InplaceButton

    assertEquals(2, argumentsTable.rowCount)
    assertEquals(3, argumentsTable.columnCount)
    assertEquals("arg1", (argumentsTable.getValueAt(0, 0) as NlProperty).value)
    assertEquals("reference", (argumentsTable.getValueAt(0, 1) as NlProperty).value)
    assertEquals("value1", (argumentsTable.getValueAt(0, 2) as NlProperty).value)
    assertEquals("arg2", (argumentsTable.getValueAt(1, 0) as NlProperty).value)
    assertEquals(null, (argumentsTable.getValueAt(1, 1) as NlProperty).value)
    assertEquals("value2", (argumentsTable.getValueAt(1, 2) as NlProperty).value)

    // add a new row
    plusButton.doClick()

    // edit the new item row
    setValue("foo", 2, 0, argumentsTable)

    assertEquals(3, argumentsTable.rowCount)
    assertEquals("foo", (argumentsTable.getValueAt(2, 0) as NlProperty).value)
    (argumentsTable.getValueAt(2, 1) as NlProperty).setValue("integer")
    (argumentsTable.getValueAt(2, 2) as NlProperty).setValue("bar")
    assertEquals(3, argumentsTable.rowCount)
    assertEquals("integer", (argumentsTable.getValueAt(2, 1) as NlProperty).value)
    assertEquals("bar", (argumentsTable.getValueAt(2, 2) as NlProperty).value)

    // another one
    plusButton.doClick()
    setValue("foo2", 3, 0, argumentsTable)
    assertEquals(4, argumentsTable.rowCount)
    assertEquals("foo2", (argumentsTable.getValueAt(3, 0) as NlProperty).value)

    // Now start deleting one
    setValue("", 0, 0, argumentsTable)
    setValue("", 0, 2, argumentsTable)

    // Shouldn't be enough yet
    assertEquals(4, argumentsTable.rowCount)
    setValue(null, 0, 1, argumentsTable)

    // now it's enough
    assertEquals(3, argumentsTable.rowCount)
    assertEquals("arg2", (argumentsTable.getValueAt(0, 0) as NlProperty).value)
    assertEquals(null, (argumentsTable.getValueAt(0, 1) as NlProperty).value)
    assertEquals("value2", (argumentsTable.getValueAt(0, 2) as NlProperty).value)
    assertEquals("foo", (argumentsTable.getValueAt(1, 0) as NlProperty).value)
    assertEquals("integer", (argumentsTable.getValueAt(1, 1) as NlProperty).value)
    assertEquals("bar", (argumentsTable.getValueAt(1, 2) as NlProperty).value)
    assertEquals("foo2", (argumentsTable.getValueAt(2, 0) as NlProperty).value)
  }

  private fun setValue(value: String?, row: Int, column: Int, argumentsTable: JBTable) {
    val editor = argumentsTable.getCellEditor(row, column) as NlTableCellEditor
    argumentsTable.editCellAt(row, column)
    ApplicationManager.getApplication().runWriteAction {
      (editor.editor as? TextEditor)?.setText(value.orEmpty())
      (editor.editor as? EnumEditor)?.selectItem(value)
    }
    editor.stopCellEditing()
  }
}

private fun flatten(component: Component): List<Component> {
  if (component !is Container) {
    return listOf(component)
  }
  return component.components.flatMap { flatten(it) }.plus(component)
}