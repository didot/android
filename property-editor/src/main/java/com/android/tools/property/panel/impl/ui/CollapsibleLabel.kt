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
package com.android.tools.property.panel.impl.ui

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.impl.model.CollapsibleLabelModel
import com.google.common.html.HtmlEscapers
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.border.Border

/**
 * A label to show text with or without trailing ellipsis and an optional expansion control.
 *
 * When the text on this label is too wide for the allowable space, this label can can either
 * show training ellipsis or clip the text. In this way the control can be used in connection
 * with an [ExpandableItemsHandler].
 *
 * An expansion control is optionally shown and expansion logic is included.
 */
class CollapsibleLabel(
  val model: CollapsibleLabelModel,
  font: Font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL),
  vararg actions: AnAction
) : JPanel(BorderLayout()) {
  private val label = MyLabel(model.name)

  // The label wil automatically display ellipsis at the end of a string that is too long for the width
  private val valueWithTrailingEllipsis = model.name
  // As html the value will not have ellipsis at the end but simply cut off when the string is too long
  private val valueWithoutEllipsis = toHtml(model.name)

  private val expandAction = object : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
      model.expanded = !model.expanded
    }
  }

  private val button = IconWithFocusBorder { if (model.expandable) expandAction else null }

  val text: String?
    get() = label.text

  val icon: Icon?
    get() = button.icon

  var innerBorder: Border?
    get() = label.border
    set(value) { label.border = value}

  init {
    background = secondaryPanelBackground
    button.border = JBUI.Borders.emptyRight(2)
    add(button, BorderLayout.WEST)
    add(label, BorderLayout.CENTER)
    model.addValueChangedListener(ValueChangedListener { valueChanged() })
    label.font = font
    if (actions.isNotEmpty()) {
      val buttons = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(2), 0))
      actions.forEach { buttons.add(FocusableActionButton(it)) }
      add(buttons, BorderLayout.EAST)
    }
    valueChanged()
  }

  private fun valueChanged() {
    val revalidateParent = if (isVisible != model.visible) parent else null
    isVisible = model.visible
    label.isVisible = isVisible
    label.text = if (model.showEllipses) valueWithTrailingEllipsis else valueWithoutEllipsis
    button.icon = model.icon
    revalidateParent?.revalidate()
    revalidateParent?.repaint()
  }

  private fun toHtml(text: String): String {
    return "<html>" + HtmlEscapers.htmlEscaper().escape(text) + "</html>"
  }

  private class MyLabel(label: String): JBLabel(label) {
    override fun contains(x: Int, y: Int): Boolean {
      return isVisible && super.contains(x, y)
    }
  }
}
