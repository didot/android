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
package com.android.tools.idea.gradle.structure.configurables.ui.properties

import com.android.tools.idea.gradle.structure.model.VariablesProvider
import com.android.tools.idea.gradle.structure.model.meta.ModelMapProperty
import com.android.tools.idea.gradle.structure.model.meta.ModelSimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.PropertyEditorFactory
import com.intellij.util.ui.AbstractTableCellEditor
import java.awt.Component
import java.awt.TextField
import javax.swing.JTable
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

/**
 * A property editor [ModelPropertyEditor] for properties of simple map types.
 */
class MapPropertyEditor<ContextT, ModelT, ValueT : Any, out ModelPropertyT : ModelMapProperty<ContextT, ModelT, ValueT>>(
  context: ContextT,
  model: ModelT,
  property: ModelPropertyT,
  editor: PropertyEditorFactory<ContextT, Unit, ModelSimpleProperty<ContextT, Unit, ValueT>, ValueT>,
  variablesProvider: VariablesProvider?
) : CollectionPropertyEditor<ContextT, ModelT, ModelPropertyT, ValueT>(context, model, property, editor, variablesProvider),
    ModelPropertyEditor<ModelT, Map<String, ValueT>> {

  init {
    loadValue()
  }

  override fun updateProperty() = throw UnsupportedOperationException()

  override fun dispose() = Unit

  override fun getValueAt(row: Int): ParsedValue<ValueT> {
    val entryKey = keyAt(row)
    val entryValue = if (entryKey == "") modelValueAt(row) else property.getEditableValues(model)[entryKey]?.getParsedValue(Unit)
    return entryValue ?: ParsedValue.NotSet
  }

  override fun setValueAt(row: Int, value: ParsedValue<ValueT>) {
    val entryKey = keyAt(row)
    // If entryKey == "", we don't need to store the value in the property. It is, however, automatically stored in the table model and
    // it will be transferred to the property when the key value is set.
    if (entryKey != "") {
      (property.getEditableValues(model)[entryKey] ?: property.addEntry(model, entryKey)).setParsedValue(Unit, value)
    }
  }

  override fun addItem() {
    tableModel?.let { tableModel ->
      val index = tableModel.rowCount
      tableModel.addRow(arrayOf("", ParsedValue.NotSet.toTableModelValue()))
      table.selectionModel.setSelectionInterval(index, index)
      table.editCellAt(index, 0)
    }
  }

  override fun removeItem() {
    tableModel?.let { tableModel ->
      table.removeEditor()
      val selection = table.selectionModel
      for (index in selection.maxSelectionIndex downTo selection.minSelectionIndex) {
        if (table.selectionModel.isSelectedIndex(index)) {
          val key = (tableModel.getValueAt(index, 0) as String?).orEmpty()
          if (key != "") {
            property.deleteEntry(model, key)
            tableModel.removeRow(index)
          }
        }
      }
    }
  }

  override fun createTableModel(): DefaultTableModel {
    val tableModel = DefaultTableModel()
    tableModel.addColumn("key")
    tableModel.addColumn("value")
    val value = property.getEditableValues(model)
    for ((k, v) in value.entries) {
      tableModel.addRow(arrayOf(k, v.getParsedValue(Unit).toTableModelValue()))
    }
    return tableModel
  }

  override fun createColumnModel(): TableColumnModel {
    return DefaultTableColumnModel().apply {
      addColumn(TableColumn(0, 50).apply {
        headerValue = "K"
        cellEditor = MyKeyCellEditor()
      })
      addColumn(TableColumn(1).apply {
        headerValue = "V"
        cellEditor = MyCellEditor()
        cellRenderer = MyCellRenderer()
      })
    }
  }

  override fun getValueText(): String = throw UnsupportedOperationException()
  override fun getValue(): ParsedValue<Map<String, ValueT>> = throw UnsupportedOperationException()

  private fun keyAt(row: Int) = (table.model.getValueAt(row, 0) as? String).orEmpty()

  private fun modelValueAt(row: Int) =
    @Suppress("UNCHECKED_CAST")  // If it is of type Value, then generic type arguments are correct.
    (table.model.getValueAt(row, 1) as? CollectionPropertyEditor<ContextT, ModelT, ModelPropertyT, ValueT>.Value)?.value

  inner class MyKeyCellEditor : AbstractTableCellEditor() {
    private var currentRow: Int = -1
    private var currentKey: String? = null
    private var lastEditor: TextField? = null

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component? {
      currentRow = row
      currentKey = keyAt(row)
      lastEditor = TextField().apply {
        text = currentKey
      }
      return lastEditor
    }

    override fun stopCellEditing(): Boolean {
      return super.stopCellEditing().also {
        if (it) {
          val oldKey = currentKey!!
          val newKey = lastEditor!!.text!!
          when {
            oldKey == "" -> {
              val addedEntry = property.addEntry(model, newKey)
              @Suppress("UNCHECKED_CAST")
              val modelValue: Value? =
                table.model.getValueAt(currentRow, 1) as? CollectionPropertyEditor<ContextT, ModelT, ModelPropertyT, ValueT>.Value
              if (modelValue != null) {
                addedEntry.setParsedValue(Unit, modelValue.value)
              }
            }
            newKey == "" -> property.deleteEntry(model, oldKey)
            else -> property.changeEntryKey(model, oldKey, newKey)
          }
          currentRow = -1
          currentKey = null
        }
      }
    }

    override fun cancelCellEditing() {
      currentRow = -1
      currentKey = null
      super.cancelCellEditing()
    }

    override fun getCellEditorValue(): Any = lastEditor!!.text
  }
}

fun <ContextT, ModelT, ValueT : Any, ModelPropertyT : ModelMapProperty<ContextT, ModelT, ValueT>> mapPropertyEditor(
  editor: PropertyEditorFactory<ContextT, Unit, ModelSimpleProperty<ContextT, Unit, ValueT>, ValueT>
):
    PropertyEditorFactory<ContextT, ModelT, ModelPropertyT, Map<String, ValueT>> =
  { context, model, property, variablesProvider -> MapPropertyEditor(context, model, property, editor, variablesProvider) }
