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
package com.android.tools.idea.uibuilder.property2.inspector

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.PREFIX_ANDROID
import com.android.SdkConstants.TEXT_VIEW
import com.android.SdkConstants.VALUE_WRAP_CONTENT
import com.android.tools.adtui.ptable2.PTableColumn
import com.android.tools.adtui.ptable2.PTableItem
import com.android.tools.adtui.ptable2.PTableModel
import com.android.tools.adtui.ptable2.PTableModelUpdateListener
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.NeleNewPropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.support.NeleEnumSupportProvider
import com.android.tools.idea.uibuilder.property2.testutils.FakeTableLine
import com.android.tools.idea.uibuilder.property2.testutils.InspectorTestUtil
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@RunsInEdt
class DeclaredAttributesInspectorBuilderTest {
  @JvmField @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @JvmField @Rule
  val edtRule = EdtRule()

  @Test
  fun testDeclaredAttributes() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(2)
    assertThat(util.inspector.lines[0].type).isEqualTo(LineType.TITLE)
    assertThat(util.inspector.lines[1].type).isEqualTo(LineType.TABLE)

    assertThat(util.inspector.lines[0].title).isEqualTo("Declared Attributes")
    assertThat(util.inspector.lines[0].expandable).isTrue()

    // Check that there are 3 attributes
    assertThat(util.inspector.lines[1].tableModel?.items?.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_TEXT).inOrder()

    // Also check the values
    assertThat(util.inspector.lines[1].tableModel?.items?.map { it.value })
      .containsExactly(VALUE_WRAP_CONTENT, VALUE_WRAP_CONTENT, "Testing").inOrder()
  }

  @Test
  fun testInspectorWithAddedNewProperty() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    performAddNewRowAction(util)

    assertThat(util.inspector.lines).hasSize(2)
    assertThat(util.inspector.lines[0].type).isEqualTo(LineType.TITLE)
    assertThat(util.inspector.lines[1].type).isEqualTo(LineType.TABLE)

    assertThat(util.inspector.lines[0].title).isEqualTo("Declared Attributes")
    assertThat(util.inspector.lines[0].expandable).isTrue()
    assertThat(util.inspector.lines[0].actions.size).isEqualTo(2)

    // Check that there are 4 attributes
    assertThat(util.inspector.lines[1].tableModel?.items?.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_TEXT, "").inOrder()

    // Also check the values
    assertThat(util.inspector.lines[1].tableModel?.items?.map { it.value })
      .containsExactly(VALUE_WRAP_CONTENT, VALUE_WRAP_CONTENT, "Testing", null).inOrder()
  }

  @Test
  fun testAcceptMoveToNextEditorWithEmptyNewPropertyValue() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    performAddNewRowAction(util)

    val declared = util.inspector.lines[1].tableModel!!
    assertThat(declared.acceptMoveToNextEditor(declared.items[0], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[0], PTableColumn.VALUE)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[1], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[1], PTableColumn.VALUE)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[2], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[2], PTableColumn.VALUE)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[3], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[3], PTableColumn.VALUE)).isTrue()
  }

  @Test
  fun testAcceptMoveToNextEditorWithSpecifiedNewPropertyValue() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    performAddNewRowAction(util)

    val declared = util.inspector.lines[1].tableModel!!
    val newProperty = declared.items.last() as NeleNewPropertyItem
    newProperty.name = PREFIX_ANDROID + ATTR_TEXT_SIZE
    newProperty.delegate?.value = "10sp"

    assertThat(declared.acceptMoveToNextEditor(declared.items[0], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[0], PTableColumn.VALUE)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[1], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[1], PTableColumn.VALUE)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[2], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[2], PTableColumn.VALUE)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[3], PTableColumn.NAME)).isTrue()
    assertThat(declared.acceptMoveToNextEditor(declared.items[3], PTableColumn.VALUE)).isFalse()
  }

  @Test
  fun testUpdateItemsWhenNoChanges() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    performAddNewRowAction(util)

    val declared = util.inspector.lines[1].tableModel!!
    val listener = mock(PTableModelUpdateListener::class.java)
    declared.addListener(listener)

    util.inspector.refresh()
    verify(listener).itemsUpdated(ArgumentMatchers.eq(false), ArgumentMatchers.any())
  }

  @Test
  fun testUpdateItemsWhenPropertyAdded() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    performAddNewRowAction(util)

    val declared = util.inspector.lines[1].tableModel!!
    val listener = mock(PTableModelUpdateListener::class.java)
    declared.addListener(listener)

    util.properties[ANDROID_URI, ATTR_TEXT_SIZE].value = "12sp"
    util.inspector.refresh()
    verify(listener).itemsUpdated(ArgumentMatchers.eq(true), ArgumentMatchers.any())
  }

  @Test
  fun testDeletePropertyItem() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    val tableLine = util.inspector.lines[1] as FakeTableLine
    val model = tableLine.tableModel
    tableLine.selectedItem = model.items[2] // select ATTR_TEXT
    performDeleteRowAction(util)

    // Check that there are only 2 declared attributes left
    assertThat(model.items.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT).inOrder()

    assertThat(util.components[0].getAttribute(ANDROID_URI, ATTR_TEXT)).isNull()
  }

  @Test
  fun testDeleteNewlyAddedPropertyItem() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)
    performAddNewRowAction(util)
    performDeleteRowAction(util)

    // Check that there are only the 3 declared attributes left (the place holder is gone)
    assertThat(util.inspector.lines[1].tableModel?.items?.map { it.name })
      .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_TEXT).inOrder()
  }

  @Test
  fun testListenersAreConcurrentModificationSafe() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = LINEAR_LAYOUT)
    addProperties(util)
    val builder = createBuilder(util.model)
    builder.attachToInspector(util.inspector, util.properties)

    val declared = util.inspector.lines[1].tableModel!!
    val listener = RecursiveUpdateListener(declared)
    declared.addListener(listener)

    performAddNewRowAction(util)
    assertThat(listener.called).isTrue()
  }

  private fun createBuilder(model: NelePropertiesModel): DeclaredAttributesInspectorBuilder {
    val enumSupportProvider = NeleEnumSupportProvider()
    return DeclaredAttributesInspectorBuilder(model, enumSupportProvider)
  }

  private class RecursiveUpdateListener(private val model: PTableModel) : PTableModelUpdateListener {
    var called = false

    override fun itemsUpdated(modelChanged: Boolean, nextEditedItem: PTableItem?) {
      model.addListener(RecursiveUpdateListener(model))
      called = true
    }
  }

  private fun addProperties(util: InspectorTestUtil) {
    util.addProperty(ANDROID_URI, ATTR_TEXT, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_TEXT_SIZE, NelePropertyType.FONT_SIZE)
    util.addProperty(ANDROID_URI, ATTR_TEXT_COLOR, NelePropertyType.COLOR)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_WIDTH, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_HEIGHT, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_CONTENT_DESCRIPTION, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_START, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_END, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_VISIBILITY, NelePropertyType.ENUM)

    util.properties[ANDROID_URI, ATTR_TEXT].value = "Testing"
    util.properties[ANDROID_URI, ATTR_LAYOUT_WIDTH].value = VALUE_WRAP_CONTENT
    util.properties[ANDROID_URI, ATTR_LAYOUT_HEIGHT].value = VALUE_WRAP_CONTENT
  }

  private fun performAddNewRowAction(util: InspectorTestUtil) {
    // Add a new property line in the table
    val addNewPropertyAction = util.inspector.lines[0].actions[0]
    val event = mock(AnActionEvent::class.java)
    addNewPropertyAction.actionPerformed(event)
  }

  private fun performDeleteRowAction(util: InspectorTestUtil) {
    // Remove the selected row in the table
    val addNewPropertyAction = util.inspector.lines[0].actions[1]
    val event = mock(AnActionEvent::class.java)
    addNewPropertyAction.actionPerformed(event)
  }
}
