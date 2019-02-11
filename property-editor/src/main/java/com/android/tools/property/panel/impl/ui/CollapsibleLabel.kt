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

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.impl.model.CollapsibleLabelModel
import com.google.common.html.HtmlEscapers
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

/**
 * A label to show text with or without trailing ellipsis and an optional expansion control.
 *
 * When the text on this label is too wide for the allowable space, this label can can either
 * show training ellipsis or clip the text. In this way the control can be used in connection
 * with an [ExpandableItemsHandler].
 *
 * An expansion control is optionally shown and expansion logic is included.
 */
class CollapsibleLabel(val model: CollapsibleLabelModel) : JBLabel(model.name) {
  // A JBLabel wil automatically display ellipsis at the end of a string that is too long for the width
  private val valueWithTrailingEllipsis = model.name
  // In html the value will not have ellipsis at the end but simply cut off when the string is too long
  private val valueWithoutEllipsis = toHtml(model.name)
  private var listenerInstalled = false

  private val mouseListener = object : MouseAdapter() {
    override fun mouseClicked(event: MouseEvent) {
      model.expanded = !model.expanded
    }
  }

  var actionPanel: JComponent? = null

  init {
    model.addValueChangedListener(ValueChangedListener { valueChanged() })
    font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    valueChanged()
  }

  private fun valueChanged() {
    val revalidateParent = if (isVisible != model.visible) parent else null
    isVisible = model.visible
    actionPanel?.isVisible = isVisible
    text = if (model.showEllipses) valueWithTrailingEllipsis else valueWithoutEllipsis
    icon = model.icon
    installMouseListener(model.expandable)
    revalidateParent?.revalidate()
    revalidateParent?.repaint()
  }

  override fun contains(x: Int, y: Int): Boolean {
    return isVisible && super.contains(x, y)
  }

  private fun installMouseListener(expandable: Boolean) {
    if (expandable && !listenerInstalled) {
      addMouseListener(mouseListener)
      listenerInstalled = true
    }
    else if (!expandable && listenerInstalled) {
      removeMouseListener(mouseListener)
      listenerInstalled = false
    }
  }

  private fun toHtml(text: String): String {
    return "<html>" + HtmlEscapers.htmlEscaper().escape(text) + "</html>"
  }
}
