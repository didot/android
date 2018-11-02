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
package com.android.tools.idea.uibuilder.property2.testutils

import com.android.tools.adtui.ptable2.PTableItem
import com.android.tools.adtui.ptable2.PTableModel
import com.android.tools.idea.common.property2.api.ControlType
import com.android.tools.idea.common.property2.api.EditorProvider
import com.android.tools.idea.common.property2.api.FlagsPropertyItem
import com.android.tools.idea.common.property2.api.InspectorLineModel
import com.android.tools.idea.common.property2.api.InspectorPanel
import com.android.tools.idea.common.property2.api.PropertiesTable
import com.android.tools.idea.common.property2.api.PropertyEditorModel
import com.android.tools.idea.common.property2.api.TableLineModel
import com.android.tools.idea.common.property2.api.TableUIProvider
import com.android.tools.idea.common.property2.impl.model.BooleanPropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.ColorFieldPropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.ComboBoxPropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.FlagPropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.TextFieldPropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.ThreeStateBooleanPropertyEditorModel
import com.android.tools.idea.common.property2.impl.support.PropertiesTableImpl
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.NelePropertiesProvider
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.support.NeleControlTypeProvider
import com.android.tools.idea.uibuilder.property2.support.NeleEnumSupportProvider
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.JComponent
import javax.swing.JPanel

class InspectorTestUtil(projectRule: AndroidProjectRule, tag: String, parentTag: String = "")
  : SupportTestUtil(projectRule, tag, parentTag) {
  private val _properties: Table<String, String, NelePropertyItem> = HashBasedTable.create()

  val properties: PropertiesTable<NelePropertyItem> = PropertiesTableImpl(_properties)

  val editorProvider = FakeEditorProviderImpl()

  val inspector = FakeInspectorPanel()

  init {
    model.setPropertiesInTest(properties)
  }

  fun addProperty(namespace: String, name: String, type: NelePropertyType) {
    _properties.put(namespace, name, makeProperty(namespace, name, type))
  }

  fun addFlagsProperty(namespace: String, name: String, values: List<String>) {
    _properties.put(namespace, name, makeFlagsProperty(namespace, name, values))
  }

  fun removeProperty(namespace: String, name: String) {
    _properties.remove(namespace, name)
  }

  fun loadProperties() {
    val provider = NelePropertiesProvider(model.facet)
    for (propertyItem in provider.getProperties(model, null, components).values) {
      _properties.put(propertyItem.namespace, propertyItem.name, propertyItem)
    }
  }
}

enum class LineType {
  TITLE, PROPERTY, TABLE, PANEL, SEPARATOR
}

open class FakeInspectorLine(val type: LineType) : InspectorLineModel {
  override var visible = true
  override var hidden = false
  override var focusable = true
  override var parent: InspectorLineModel? = null
  var actions = listOf<AnAction>()
  open val tableModel: PTableModel? = null
  var title: String? = null
  var editorModel: PropertyEditorModel? = null
  var expandable = false
  var expanded = false
  val children = mutableListOf<InspectorLineModel>()
  val childProperties: List<String>
    get() = children.map { it as FakeInspectorLine }.map { it.editorModel!!.property.name }

  var focusWasRequested = false
    private set

  var gotoNextLineWasRequested = false
    private set

  override fun requestFocus() {
    focusWasRequested = true
  }

  override var gotoNextLine: (InspectorLineModel) -> Unit
    get() = { gotoNextLineWasRequested = true }
    set(value) {}

  override fun makeExpandable(initiallyExpanded: Boolean) {
    expandable = true
    expanded = initiallyExpanded
  }
}

class FakeTableLine(override val tableModel: PTableModel) : FakeInspectorLine(LineType.TABLE), TableLineModel {
  override var selectedItem: PTableItem? = null

  override fun requestFocus(item: PTableItem) {
    selectedItem = item
  }

  override fun stopEditing() {
    selectedItem = null
  }
}

class FakeInspectorPanel : InspectorPanel {
  val lines = mutableListOf<FakeInspectorLine>()

  override fun addTitle(title: String, vararg actions: AnAction): InspectorLineModel {
    val line = FakeInspectorLine(LineType.TITLE)
    line.title = title
    line.actions = actions.asList()
    lines.add(line)
    return line
  }

  override fun addCustomEditor(editorModel: PropertyEditorModel, editor: JComponent, parent: InspectorLineModel?): InspectorLineModel {
    val line = FakeInspectorLine(LineType.PROPERTY)
    editorModel.lineModel = line
    line.editorModel = editorModel
    lines.add(line)
    addAsChild(line, parent)
    return line
  }

  override fun addTable(tableModel: PTableModel,
                        searchable: Boolean,
                        tableUI: TableUIProvider,
                        parent: InspectorLineModel?): TableLineModel {
    val line = FakeTableLine(tableModel)
    lines.add(line)
    addAsChild(line, parent)
    return line
  }

  override fun addComponent(component: JComponent, parent: InspectorLineModel?): InspectorLineModel {
    val line = FakeInspectorLine(LineType.PANEL)
    lines.add(line)
    addAsChild(line, parent)
    return line
  }

  private fun addAsChild(child: FakeInspectorLine, parent: InspectorLineModel?) {
    val group = parent as? FakeInspectorLine ?: return
    group.children.add(child)
    child.parent = group
  }
}

class FakeEditorProviderImpl: EditorProvider<NelePropertyItem> {
  private val enumSupportProvider = NeleEnumSupportProvider()
  private val controlTypeProvider = NeleControlTypeProvider(enumSupportProvider)

  override fun createEditor(property: NelePropertyItem, asTableCellEditor: Boolean): Pair<PropertyEditorModel, JComponent> {
    val enumSupport = enumSupportProvider(property)
    val controlType = controlTypeProvider(property)

    when (controlType) {
      ControlType.COMBO_BOX ->
        return Pair(ComboBoxPropertyEditorModel(property, enumSupport!!, true), JPanel())

      ControlType.DROPDOWN ->
        return Pair(ComboBoxPropertyEditorModel(property, enumSupport!!, false), JPanel())

      ControlType.TEXT_EDITOR ->
        return Pair(TextFieldPropertyEditorModel(property, true), JPanel())

      ControlType.THREE_STATE_BOOLEAN ->
        return Pair(ThreeStateBooleanPropertyEditorModel(property), JPanel())

      ControlType.FLAG_EDITOR ->
        return Pair(FlagPropertyEditorModel(property as FlagsPropertyItem<*>), JPanel())

      ControlType.BOOLEAN ->
        return Pair(BooleanPropertyEditorModel(property), JPanel())

      ControlType.COLOR_EDITOR ->
        return Pair(ColorFieldPropertyEditorModel(property), JPanel())
    }
  }
}
