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

import com.android.tools.property.ptable2.PTableModel
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesViewTab
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.property.panel.api.TableLineModel
import com.android.tools.property.panel.api.TableUIProvider
import com.android.tools.property.panel.impl.model.CollapsibleLabelModel
import com.android.tools.property.panel.impl.model.GenericInspectorLineModel
import com.android.tools.property.panel.impl.model.InspectorPanelModel
import com.android.tools.property.panel.impl.model.SeparatorLineModel
import com.android.tools.property.panel.impl.model.TableLineModelImpl
import com.android.tools.property.panel.impl.model.TitleLineModel
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.lang.Integer.max
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

private const val TITLE_SEPARATOR_HEIGHT = 4
const val MIN_VERTICAL_SCROLLING_UNIT_INCREMENT = 40

/**
 * Provides a page for a tab defined by a [PropertiesViewTab].
 */
class PropertiesPage(parentDisposable: Disposable) : InspectorPanel {
  @VisibleForTesting
  val inspectorModel = InspectorPanelModel()
  private val inspector = InspectorPanelImpl(inspectorModel, parentDisposable)
  private val boldFont = UIUtil.getLabelFont().deriveFont(Font.BOLD)
  private var lastAddedLine: InspectorLineModel? = null
  private var lastTitleLine: CollapsibleLabelModel? = null

  val component = createScrollPane(inspector)

  var filter
    get() = inspectorModel.filter
    set(value) { inspectorModel.filter = value }

  val isEmpty
    get() = inspectorModel.lines.isEmpty()

  fun enterInFilter():Boolean {
    return inspectorModel.enterInFilter()
  }

  fun clear() {
    inspectorModel.clear()
    inspector.removeAll()
    lastAddedLine = null
    lastTitleLine = null
  }

  fun propertyValuesChanged() {
    inspectorModel.propertyValuesChanged()
  }

  fun repaint() {
    inspector.revalidate()
    inspector.repaint()
  }

  fun addSeparatorBeforeTabs() {
    addSeparatorBeforeTitle()
  }

  private fun createScrollPane(component: JComponent): JComponent {
    val scrollPane = ScrollPaneFactory.createScrollPane(
      component,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    scrollPane.border = BorderFactory.createEmptyBorder()
    scrollPane.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(event: ComponentEvent?) {
        // unitIncrement affects the scroll wheel speed
        scrollPane.verticalScrollBar.unitIncrement = max(scrollPane.height, MIN_VERTICAL_SCROLLING_UNIT_INCREMENT * JBUI.scale(16)) / 4

        // blockIncrement affects the page down speed, when clicking above/under the scroll thumb
        scrollPane.verticalScrollBar.blockIncrement = scrollPane.height
      }
    })
    return scrollPane
  }

  override fun addTitle(title: String, vararg actions: AnAction): InspectorLineModel {
    addSeparatorBeforeTitle()
    val model = TitleLineModel(title)
    val label = CollapsibleLabel(model)
    label.font = boldFont
    label.isOpaque = true
    label.border = JBUI.Borders.empty(TITLE_SEPARATOR_HEIGHT, LEFT_HORIZONTAL_CONTENT_BORDER_SIZE, TITLE_SEPARATOR_HEIGHT, 0)
    val outerBorder = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
    var component: JComponent = label
    if (actions.isNotEmpty()) {
      val buttons = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(2), 0))
      actions.forEach { buttons.add(ActionButton(it, it.templatePresentation.clone(), "", NAVBAR_MINIMUM_BUTTON_SIZE)) }
      component = JPanel(BorderLayout())
      component.add(label, BorderLayout.CENTER)
      component.add(buttons, BorderLayout.EAST)
      component.border = outerBorder
      label.actionPanel = component
    }
    else {
      label.border = JBUI.Borders.merge(label.border, outerBorder, true)
    }
    addLine(model, null)
    inspector.addLineElement(component)
    component.background = UIUtil.getPanelBackground()
    lastTitleLine = model
    return model
  }

  override fun addCustomEditor(editorModel: PropertyEditorModel, editor: JComponent, parent: InspectorLineModel?): InspectorLineModel {
    addSeparatorAfterTitle(parent)
    val model = CollapsibleLabelModel(editorModel.property.name, editorModel)
    val label = CollapsibleLabel(model)
    label.border = JBUI.Borders.emptyLeft(LEFT_HORIZONTAL_CONTENT_BORDER_SIZE)
    editorModel.lineModel = model
    addLine(model, parent)
    inspector.addLineElement(label, editor)
    return model
  }

  override fun addTable(tableModel: PTableModel,
                        searchable: Boolean,
                        tableUI: TableUIProvider,
                        parent: InspectorLineModel?): TableLineModel {
    // Do NOT call addSeparatorAfterTitle since tables should not be preceded with spacing after a title
    val model = TableLineModelImpl(tableModel, searchable)
    val editor = TableEditor(model, tableUI.tableCellRendererProvider, tableUI.tableCellEditorProvider)
    addLine(model, parent)
    inspector.addLineElement(editor.component)
    return model
  }

  override fun addComponent(component: JComponent, parent: InspectorLineModel?): InspectorLineModel {
    addSeparatorAfterTitle(parent)
    val model = GenericInspectorLineModel()
    val wrapper = GenericLinePanel(component, model)
    addLine(model, parent)
    inspector.addLineElement(wrapper)
    return model
  }

  private fun addLine(model: GenericInspectorLineModel, parent: InspectorLineModel?) {
    addAsChild(model, parent)
    inspectorModel.add(model)
    lastAddedLine = model
  }

  private fun addAsChild(model: GenericInspectorLineModel, parent: InspectorLineModel?) {
    when (parent) {
      null -> {}
      lastAddedLine -> checkNewParent(parent).addChild(model)
      else -> checkExistingParent(parent).addChild(model)
    }
  }

  private fun checkNewParent(parent: InspectorLineModel): CollapsibleLabelModel {
    val label = parent as? CollapsibleLabelModel ?: throw IllegalArgumentException()
    if (!label.expandable) {
      throw IllegalArgumentException()
    }
    return label
  }

  private fun checkExistingParent(parent: InspectorLineModel): CollapsibleLabelModel {
    var lastParentLine = lastAddedLine?.parent
    while (lastParentLine != null) {
      if (parent == lastParentLine) {
        return lastParentLine as CollapsibleLabelModel
      }
      lastParentLine = lastParentLine.parent
    }
    // Cannot add children to this parent !
    throw IllegalArgumentException()
  }

  private fun addSeparatorBeforeTitle() {
    if (lastAddedLine == null || lastAddedLine == lastTitleLine) {
      return
    }
    var topParent: InspectorLineModel? = lastAddedLine
    while (topParent?.parent != null) {
      topParent = topParent.parent
    }
    val parent = if (topParent == lastTitleLine) lastTitleLine else null
    addSeparator(bottomDivider = true, parent = parent)
  }

  private fun addSeparatorAfterTitle(parent: InspectorLineModel?) {
    if (lastAddedLine == null || lastAddedLine == lastTitleLine) {
      addSeparator(bottomDivider = false, parent = parent)
    }
  }

  private fun addSeparator(bottomDivider: Boolean, parent: InspectorLineModel? = null): GenericInspectorLineModel {
    val component = JPanel()
    val bottom = if (bottomDivider) 1 else 0
    component.preferredSize = JBDimension(0, TITLE_SEPARATOR_HEIGHT)
    component.background = inspector.background
    component.border = JBUI.Borders.customLine(JBColor.border(), 0, 0, bottom, 0)
    val model = SeparatorLineModel()
    val wrapper = GenericLinePanel(component, model)
    addLine(model, parent)
    inspector.addLineElement(wrapper)
    return model
  }
}
