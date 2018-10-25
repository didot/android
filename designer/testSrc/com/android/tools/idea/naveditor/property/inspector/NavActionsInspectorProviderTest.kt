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
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property.NavActionsProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.scene.decorator.HIGHLIGHTED_CLIENT_PROPERTY
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBList
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import java.awt.Component
import java.awt.Container
import java.awt.event.FocusEvent
import java.awt.event.MouseEvent

class NavActionsInspectorProviderTest : NavTestCase() {
  fun testIsApplicable() {
    val provider = NavActionsInspectorProvider()
    val surface = mock(NavDesignSurface::class.java)
    Disposer.register(myRootDisposable, surface)
    val manager = NavPropertiesManager(myFacet, surface, myRootDisposable)
    val component1 = mock(NlComponent::class.java)
    val component2 = mock(NlComponent::class.java)
    // Simple case: one component, actions property
    assertTrue(provider.isApplicable(listOf(component1), mapOf("Actions" to NavActionsProperty(listOf(component1))), manager))
    // One component, actions + other property
    assertTrue(provider.isApplicable(listOf(component1),
        mapOf("Actions" to NavActionsProperty(listOf(component1)), "foo" to mock(NlProperty::class.java)), manager))
    // Two components
    assertFalse(provider.isApplicable(listOf(component1, component2),
        mapOf("Actions" to NavActionsProperty(listOf(component1, component2))), manager))
    // zero components
    assertFalse(provider.isApplicable(listOf(), mapOf("Actions" to NavActionsProperty(listOf())), manager))
    // Non-actions property only
    assertFalse(provider.isApplicable(listOf(component1), mapOf("foo" to mock(NlProperty::class.java)), manager))
    Disposer.dispose(surface)
    Disposer.dispose(manager)
  }

  fun testListContent() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1") {
          action("a1", destination = "f2")
          action("a2", destination = "activity")
        }
        fragment("f2")
        activity("activity")
      }
    }

    val manager = mock(NavPropertiesManager::class.java)
    val navInspectorProviders = spy(NavInspectorProviders(manager, myRootDisposable))
    val provider = NavActionsInspectorProvider()
    `when`(navInspectorProviders.providers).thenReturn(listOf(provider))
    `when`(manager.getInspectorProviders(any())).thenReturn(navInspectorProviders)
    `when`(manager.facet).thenReturn(myFacet)
    `when`(manager.designSurface).thenReturn(model.surface)

    val panel = NavInspectorPanel(myRootDisposable)
    val f1 = model.find("f1")!!
    val f2 = model.find("f2")!!
    panel.setComponent(listOf(f1), HashBasedTable.create<String, String, NlProperty>(), manager)

    @Suppress("UNCHECKED_CAST")
    val actionsList = flatten(panel).find { it.name == NAV_LIST_COMPONENT_NAME }!! as JBList<NlProperty>

    assertEquals(2, actionsList.itemsCount)
    val propertiesList = listOf(actionsList.model.getElementAt(0), actionsList.model.getElementAt(1))
    assertSameElements(propertiesList.map { it.components[0].id }, listOf("a1", "a2"))
    assertSameElements(propertiesList.map { it.name }, listOf("f2", "activity"))
    assertEquals("activity (a2)", getElementText(actionsList, 0))
    assertEquals("f2 (a1)", getElementText(actionsList, 1))

    panel.setComponent(listOf(f2), HashBasedTable.create<String, String, NlProperty>(), manager)
    assertEquals(0, actionsList.itemsCount)

    val dialog = spy(AddActionDialog(AddActionDialog.Defaults.NORMAL, null, f2))
    `when`(dialog.destination).thenReturn(f1)
    doReturn(true).`when`(dialog).showAndGet()

    provider.showAndUpdateFromDialog(dialog, manager.designSurface)

    assertEquals(1, actionsList.itemsCount)
    val newAction = model.find("action_f2_to_f1")!!
    assertTrue(model.surface.selectionModel.selection.contains(newAction))
    dialog.close(0)
  }

  private fun getElementText(
    actionsList: JBList<NlProperty>,
    index: Int
  ) = actionsList.cellRenderer.getListCellRendererComponent(
      actionsList,
      actionsList.model.getElementAt(index),
      index,
      false,
      false
  ).toString()

  fun testPopupContents() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1") {
          action("a1", destination = "f2")
          action("a2", destination = "activity")
        }
        fragment("f2")
        activity("activity")
      }
    }

    val manager = mock(NavPropertiesManager::class.java)
    val navInspectorProviders = spy(NavInspectorProviders(manager, myRootDisposable))
    val provider = spy(NavActionsInspectorProvider())
    `when`(navInspectorProviders.providers).thenReturn(listOf(provider))
    `when`(manager.getInspectorProviders(any())).thenReturn(navInspectorProviders)
    `when`(manager.facet).thenReturn(myFacet)

    @Suppress("UNCHECKED_CAST")
    val answer = object: Answer<NavListInspectorProvider<NavActionsProperty>.NavListInspectorComponent> {
      var result: NavListInspectorProvider<NavActionsProperty>.NavListInspectorComponent? = null

      override fun answer(invocation: InvocationOnMock?): NavListInspectorProvider<NavActionsProperty>.NavListInspectorComponent =
          (invocation?.callRealMethod() as NavListInspectorProvider<NavActionsProperty>.NavListInspectorComponent).also { result = it }
    }
    doAnswer(answer).`when`(provider).createCustomInspector(any(), any(), any())
    val panel = NavInspectorPanel(myRootDisposable)
    panel.setComponent(listOf(model.find("f1")!!), HashBasedTable.create<String, String, NlProperty>(), manager)

    @Suppress("UNCHECKED_CAST")
    var actionsList = flatten(panel).find { it.name == NAV_LIST_COMPONENT_NAME } as JBList<NlProperty>
    actionsList = spy(actionsList)
    `when`(actionsList.isShowing).thenReturn(true)

    val cell0Location = actionsList.indexToLocation(0)

    actionsList.selectedIndices = intArrayOf(0)
    var group: ActionGroup = answer.result?.createPopupContent(MouseEvent(
        actionsList, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
        cell0Location.x, cell0Location.y, 1, true))!!

    assertEquals(3, group.getChildren(null).size)
    assertEquals("Edit", group.getChildren(null)[0].templatePresentation.text)
    assertInstanceOf(group.getChildren(null)[1], Separator::class.java)
    assertEquals("Delete", group.getChildren(null)[2].templatePresentation.text)
    assertArrayEquals(intArrayOf(0), actionsList.selectedIndices)

    actionsList.selectedIndices = intArrayOf(0, 1)
    group = answer.result?.createPopupContent(MouseEvent(
        actionsList, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
        cell0Location.x, cell0Location.y, 1, true))!!

    assertEquals(1, group.getChildren(null).size)
    assertEquals("Delete", group.getChildren(null)[0].templatePresentation.text)
    assertArrayEquals(intArrayOf(0, 1), actionsList.selectedIndices)

    actionsList.selectedIndices = intArrayOf(1)
    group = answer.result?.createPopupContent(MouseEvent(
        actionsList, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
        cell0Location.x, cell0Location.y, 1, true))!!

    assertEquals(3, group.getChildren(null).size)
    assertArrayEquals(intArrayOf(0), actionsList.selectedIndices)
  }

  fun testSelectionHighlighted() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1") {
          action("a1", destination = "f2")
          action("a2", destination = "activity")
        }
        fragment("f2")
        activity("activity")
      }
    }

    val manager = mock(NavPropertiesManager::class.java)
    val navInspectorProviders = spy(NavInspectorProviders(manager, myRootDisposable))
    `when`(navInspectorProviders.providers).thenReturn(listOf(NavActionsInspectorProvider()))
    `when`(manager.getInspectorProviders(any())).thenReturn(navInspectorProviders)
    `when`(manager.facet).thenReturn(myFacet)
    `when`(manager.designSurface).thenReturn(model.surface)

    val panel = NavInspectorPanel(myRootDisposable)
    val f1 = model.find("f1")!!
    model.surface.selectionModel.setSelection(listOf(f1))
    panel.setComponent(listOf(f1), HashBasedTable.create<String, String, NlProperty>(), manager)

    @Suppress("UNCHECKED_CAST")
    val actionsList = flatten(panel).find { it.name == NAV_LIST_COMPONENT_NAME }!! as JBList<NlProperty>
    actionsList.addSelectionInterval(1, 1)

    assertEquals(true, model.find("a1")!!.getClientProperty(HIGHLIGHTED_CLIENT_PROPERTY))
    assertNotEquals(true, model.find("a2")!!.getClientProperty(HIGHLIGHTED_CLIENT_PROPERTY))

    actionsList.focusListeners.forEach { it.focusLost(FocusEvent(actionsList, FocusEvent.FOCUS_LOST)) }
    assertNotEquals(true, model.find("a1")!!.getClientProperty(HIGHLIGHTED_CLIENT_PROPERTY))
  }

  fun testPlusContents() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        navigation("subnav")
      }
    }

    val provider = NavActionsInspectorProvider()
    val surface = model.surface as NavDesignSurface
    val actions = provider.getPopupActions(listOf(model.find("f1")!!), surface)
    assertEquals(4, actions.size)
    assertEquals("Add Action...", actions[0].templatePresentation.text)
    assertEquals("Return to Source...", actions[1].templatePresentation.text)
    assertInstanceOf(actions[2], Separator::class.java)
    assertEquals("Add Global...", actions[3].templatePresentation.text)

    `when`(surface.currentNavigation).thenReturn(model.find("subnav"))
    val rootActions = provider.getPopupActions(listOf(model.find("subnav")!!), surface)
    assertEquals(2, rootActions.size)
    assertEquals("Add Action...", rootActions[0].templatePresentation.text)
    assertEquals("Return to Source...", rootActions[1].templatePresentation.text)
  }

}

private fun <T> any(): T {
  Mockito.any<T>()
  return uninitialized()
}
@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun flatten(component: Component): List<Component> {
  if (component !is Container) {
    return listOf(component)
  }
  return component.components.flatMap { flatten(it) }.plus(component)
}