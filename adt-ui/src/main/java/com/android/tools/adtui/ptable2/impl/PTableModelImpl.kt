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
package com.android.tools.adtui.ptable2.impl

import com.android.tools.adtui.ptable2.*
import javax.swing.table.AbstractTableModel

/**
 * A table model implementation for a JTable.
 */
class PTableModelImpl(val tableModel: PTableModel) : AbstractTableModel() {
  private val items = mutableListOf<PTableItem>()
  private val expandedItems = mutableSetOf<PTableGroupItem>()

  init {
    items.addAll(tableModel.items)
    tableModel.addListener(object : PTableModelUpdateListener {
      override fun itemsUpdated(modelChanged: Boolean) {
        if (modelChanged) {
          items.clear()
          items.addAll(tableModel.items)
          expandedItems.retainAll { isGroupItem(it) }
          expandedItems.forEach { restoreExpanded(it) }
        }
        fireTableDataChanged()
      }
    })
  }

  override fun getRowCount() = items.count()

  override fun getColumnCount() = 2

  override fun getValueAt(rowIndex: Int, columnIndex: Int): PTableItem = items[rowIndex]

  override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
    return tableModel.isCellEditable(items[rowIndex], PTableColumn.fromColumn(columnIndex))
  }

  fun isGroupItem(item: PTableItem): Boolean {
    // This allows an implementation to mutate an item from/to an item that is considered a group.
    // It is currently used in fabricated new items where the state is unknown up front.
    return item is PTableGroupItem && item.children.isNotEmpty()
  }

  fun isExpanded(item: PTableGroupItem): Boolean {
    return expandedItems.contains(item)
  }

  fun toggle(index: Int) {
    val item = groupAt(index) ?: return
    if (expandedItems.contains(item)) {
      collapse(item, index)
    }
    else {
      expand(item, index)
    }
  }

  fun expand(index: Int) {
    val item = groupAt(index) ?: return
    expand(item, index)
  }

  fun collapse(index: Int) {
    val item = groupAt(index) ?: return
    collapse(item, index)
  }

  private fun groupAt(index: Int): PTableGroupItem? {
    if (index < 0 || index >= items.size) {
      return null
    }
    val item = items[index]
    return if (isGroupItem(item)) item as PTableGroupItem else null
  }

  private fun restoreExpanded(item: PTableGroupItem) {
    val index = items.indexOf(item)
    if (index >= 0) {
      items.addAll(index + 1, item.children)
    }
  }

  private fun expand(item: PTableGroupItem, index: Int) {
    if (expandedItems.add(item)) {
      items.addAll(index + 1, item.children)
      fireTableDataChanged()
    }
  }

  private fun collapse(item: PTableGroupItem, row: Int) {
    if (expandedItems.remove(item)) {
      items.subList(row + 1, row + 1 + item.children.size).clear()
      fireTableDataChanged()
    }
  }
}
