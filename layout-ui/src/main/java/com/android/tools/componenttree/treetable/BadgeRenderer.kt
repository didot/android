/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.componenttree.treetable

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.componenttree.api.BadgeItem
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * Renderer used for each [BadgeItem] specified.
 */
class BadgeRenderer(val badge: BadgeItem) : TableCellRenderer, JBLabel() {
  init {
    horizontalAlignment = JLabel.CENTER
    text = null
    // Add border of 2 left & right, and space for the left divider
    border = JBUI.Borders.empty(0, if (badge.leftDivider) 3 else 2, 0, 2)
  }

  override fun getTableCellRendererComponent(
    table: JTable,
    value: Any?,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int
  ): Component {
    val focused = isSelected && table.hasFocus()
    val hoverCell = table.hoverCell
    background = UIUtil.getTreeBackground(isSelected, focused)
    val hoverIcon = if (value != null && hoverCell?.equalTo(row, column) == true) badge.getHoverIcon(value) else null
    icon = (hoverIcon ?: value?.let { badge.getIcon(it) })?.white(focused) ?: EmptyIcon.ICON_16
    toolTipText = badge.getTooltipText(value)
    return this
  }

  private fun Icon.white(focused: Boolean): Icon =
    if (focused) ColoredIconGenerator.generateWhiteIcon(this) else this
}
