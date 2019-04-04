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
package com.android.tools.idea.naveditor.property.editors

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_GRAPH
import com.android.SdkConstants.ATTR_LABEL
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_START_DESTINATION
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property.editors.NonEditableEditor
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property.TYPE_EDITOR_PROPERTY_LABEL
import com.android.tools.idea.naveditor.property.inspector.SimpleProperty
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DESTINATION
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_ENTER_ANIM
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_UP_TO
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class NavPropertyEditorsTest : NavTestCase() {
  fun testCreate() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation("root") {
        fragment("f1", label = "fragment1") {
          action("action1", destination = "f1")
        }
      }
    }

    var component = model.find("action1")!!

    val navPropertyEditors = NavPropertyEditors.getInstance(project)
    var editor = navPropertyEditors.create(SimpleProperty(TYPE_EDITOR_PROPERTY_LABEL, listOf(component)))
    assertInstanceOf(editor, NonEditableEditor::class.java)

    editor = navPropertyEditors.create(SimpleProperty(NavigationSchema.ATTR_DESTINATION, listOf(component)))
    assertInstanceOf(editor, VisibleDestinationsEditor::class.java)

    val propertyItem = mock(NlPropertyItem::class.java)
    `when`(propertyItem.name).thenReturn(ATTR_LABEL)
    `when`(propertyItem.namespace).thenReturn(ANDROID_URI)
    `when`(propertyItem.model).thenReturn(model)
    `when`(propertyItem.components).thenReturn(listOf(component))

    editor = navPropertyEditors.create(propertyItem)
    assertInstanceOf(editor, TextEditor::class.java)

    // normal SimpleProperties should be non-editable
    val simpleProperty = SimpleProperty(ATTR_LABEL, listOf(component), ANDROID_URI, "foo")
    editor = navPropertyEditors.create(simpleProperty)
    assertInstanceOf(editor, NonEditableEditor::class.java)

    // Try something else just to make sure it doesn't blow up
    editor = navPropertyEditors.create(SimpleProperty("foo", listOf(component)))
    assertNotNull(editor)

  }

  fun testInvalid() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation("root")
    }

    testInvalid(model, "fragment", ATTR_ENTER_ANIM)
    testInvalid(model, "fragment", ATTR_GRAPH)
    testInvalid(model, "fragment", ATTR_DESTINATION)
    testInvalid(model, "fragment", ATTR_START_DESTINATION)
    testInvalid(model, "fragment", ATTR_POP_UP_TO)
    testInvalid(model, "action", ATTR_LAYOUT)
    testInvalid(model, "action", ATTR_NAME)
  }

  private fun testInvalid(model: NlModel, tag: String, name: String) {
    val component = mock(NlComponent::class.java)
    `when`(component.model).thenReturn(model)
    `when`(component.tagName).thenReturn(tag)

    val propertyItem = mock(NlPropertyItem::class.java)
    `when`(propertyItem.name).thenReturn(name)
    `when`(propertyItem.namespace).thenReturn(ANDROID_URI)
    `when`(propertyItem.model).thenReturn(model)
    `when`(propertyItem.components).thenReturn(listOf(component))

    val navPropertyEditors = NavPropertyEditors.getInstance(project)

    val editor = navPropertyEditors.create(propertyItem)
    assertInstanceOf(editor, TextEditor::class.java)
  }
}