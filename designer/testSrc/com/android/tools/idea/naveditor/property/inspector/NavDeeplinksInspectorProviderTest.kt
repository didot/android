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

import com.android.SdkConstants.*
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property.NavDeeplinkProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBList
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.*
import java.awt.Component
import java.awt.Container

class NavDeeplinksInspectorProviderTest : NavTestCase() {
  private val uri1 = "http://www.example.com"
  private val uri2 = "http://www.example2.com/and/then/some/long/stuff/after"

  fun testIsApplicable() {
    val provider = NavDeeplinkInspectorProvider()
    val surface = Mockito.mock(NavDesignSurface::class.java)
    val manager = NavPropertiesManager(myFacet, surface)
    val component1 = Mockito.mock(NlComponent::class.java)
    val component2 = Mockito.mock(NlComponent::class.java)
    // Simple case: one component, deeplink property
    assertTrue(provider.isApplicable(listOf(component1), mapOf("Deeplinks" to NavDeeplinkProperty(listOf(component1))), manager))
    // One component, deeplink + other property
    assertTrue(provider.isApplicable(listOf(component1),
        mapOf("Deeplinks" to NavDeeplinkProperty(listOf(component1)), "foo" to Mockito.mock(NlProperty::class.java)), manager))
    // Two components
    assertFalse(provider.isApplicable(listOf(component1, component2),
        mapOf("Deeplinks" to NavDeeplinkProperty(listOf(component1, component2))), manager))
    // zero components
    assertFalse(provider.isApplicable(listOf(), mapOf("Deeplinks" to NavDeeplinkProperty(listOf())), manager))
    // Non-deeplink property only
    assertFalse(provider.isApplicable(listOf(component1), mapOf("foo" to Mockito.mock(NlProperty::class.java)), manager))
    Disposer.dispose(surface)
    Disposer.dispose(manager)
  }

  fun testListContent() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          deeplink(uri1)
          deeplink(uri2)
        }
        fragment("f2")
        activity("a1")
      }
    }
    val manager = Mockito.mock(NavPropertiesManager::class.java)
    val navInspectorProviders = Mockito.spy(NavInspectorProviders(manager, myRootDisposable))
    Mockito.`when`(navInspectorProviders.providers).thenReturn(listOf(NavDeeplinkInspectorProvider()))
    Mockito.`when`(manager.getInspectorProviders(any())).thenReturn(navInspectorProviders)
    Mockito.`when`(manager.facet).thenReturn(myFacet)

    val panel = NavInspectorPanel(myRootDisposable)
    panel.setComponent(listOf(model.find("f1")!!), HashBasedTable.create<String, String, NlProperty>(), manager)

    @Suppress("UNCHECKED_CAST")
    val deeplinkList = flatten(panel).find { it.name == NAV_LIST_COMPONENT_NAME }!! as JBList<NlProperty>

    assertEquals(2, deeplinkList.itemsCount)
    val propertiesList = listOf(deeplinkList.model.getElementAt(0), deeplinkList.model.getElementAt(1))
    assertSameElements(propertiesList.map { it.name }, listOf(uri1, uri2))
  }

  fun testAddNew() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
      }
    }
    val fragment = model.find("f1")!!
    val dialog = spy(AddDeeplinkDialog(null, fragment))
    `when`(dialog.uri).thenReturn("http://example.com")
    `when`(dialog.autoVerify).thenReturn(true)
    doReturn(true).`when`(dialog).showAndGet()

    NavDeeplinkInspectorProvider { _, _ -> dialog }.addItem(null, listOf(fragment), null)
    assertEquals(1, fragment.childCount)
    val deeplink = fragment.getChild(0)!!
    assertEquals(TAG_DEEP_LINK, deeplink.tagName)
    assertEquals("http://example.com", deeplink.getAttribute(AUTO_URI, ATTR_URI))
    assertEquals("true", deeplink.getAndroidAttribute(ATTR_AUTO_VERIFY))
    dialog.close(0)
  }

  fun testModify() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          deeplink("http://example.com")
        }
      }
    }
    val fragment = model.find("f1")!!
    val dialog = spy(AddDeeplinkDialog(model.find("f1")!!.children[0], fragment))
    `when`(dialog.uri).thenReturn("http://example.com")
    `when`(dialog.autoVerify).thenReturn(true)
    doReturn(true).`when`(dialog).showAndGet()

    NavDeeplinkInspectorProvider { _, _ -> dialog }.addItem(null, listOf(fragment), null)
    assertEquals(1, fragment.childCount)
    val deeplink = fragment.getChild(0)!!
    assertEquals(TAG_DEEP_LINK, deeplink.tagName)
    assertEquals("http://example.com", deeplink.getAttribute(AUTO_URI, ATTR_URI))
    assertEquals("true", deeplink.getAndroidAttribute(ATTR_AUTO_VERIFY))
    dialog.close(0)

  }
}

private fun flatten(component: Component): List<Component> {
  if (component !is Container) {
    return listOf(component)
  }
  return component.components.flatMap { flatten(it) }.plus(component)
}