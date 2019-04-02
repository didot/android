/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint

import com.android.SdkConstants
import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.refactoring.rtl.RtlSupportProcessor
import com.intellij.ui.components.JBList
import com.intellij.ui.paint.EffectPainter2D
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.util.Vector
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingConstants
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

private const val PREFERRED_WIDTH = 280
private const val COMPONENT_HEIGHT = 20

private val ERROR_TEXT_COLOR = Color(0x969696)

// TODO: remove this interface after StudioFlags.NELE_CONSTRAINT_SECTION is removed.
abstract class WidgetSection : AdtSecondaryPanel() {
  abstract fun configureUi()
}

class WidgetConstraintSection(private val widgetModel : WidgetConstraintModel) : WidgetSection() {

  private val sectionTitle = SectionTitle()
  private val list: JList<ConstraintCellData>
  private val listData = Vector<ConstraintCellData>()
  private val warningPanel = WarningPanel()

  private var expanded: Boolean = false

  private var previousComponent: NlComponent? = null

  init {
    border = JBUI.Borders.empty(4, 4, 4, 4)
    layout = BorderLayout()

    list = JBList<ConstraintCellData>().apply { setEmptyText("") }
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.cellRenderer = ConstraintItemRenderer()

    list.addListSelectionListener(object: ListSelectionListener {
      override fun valueChanged(e: ListSelectionEvent) {
        val index = list.selectedIndex
        if (index < 0 || index >= listData.size) {
          return
        }
        val itemData = listData[index] ?: return
        val scene = widgetModel.surface?.scene ?: return
        val apiLevel = scene.renderedApiLevel
        val rtl = scene.isInRTL
        val constraint = getConstraintForAttribute(itemData.attribute, apiLevel, rtl)
        widgetModel.surface?.selectionModel?.setSecondarySelection(widgetModel.component, constraint)
        widgetModel.surface?.invalidate()
      }
    })

    list.addKeyListener(object: KeyAdapter() {
      override fun keyReleased(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_DELETE || e.keyCode == KeyEvent.VK_BACK_SPACE) {
          val index = list.selectedIndex
          val item = listData.removeAt(index)

          widgetModel.removeAttributes(item.namespace, item.attribute)
          list.clearSelection()
          e.consume()
        }
        else if (e.keyCode == KeyEvent.VK_ESCAPE) {
          list.clearSelection()
          e.consume()
        }
        else {
          super.keyReleased(e)
        }
      }
    })

    list.setListData(listData)
    add(sectionTitle, BorderLayout.NORTH)
    sectionTitle.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        setExpand(!expanded)
        e.consume()
      }
    })
    add(list, BorderLayout.CENTER)
    add(warningPanel, BorderLayout.SOUTH)

    val hasConstraintSelection = updateConstraintSelection()
    // Force expand the list if there is constraint selection
    setExpand(if (hasConstraintSelection) true else expanded)
  }

  private fun setExpand(expanded: Boolean) {
    this.expanded = expanded
    sectionTitle.updateTitle()
    warningPanel.updateWarningMessage()

    warningPanel.isVisible = expanded
    list.isVisible = if (listData.size == 0) false else expanded

    sectionTitle.updateUI()
    list.updateUI()
    warningPanel.updateUI()

    invalidate()
  }

  override fun getPreferredSize(): Dimension {
    val titleSize = sectionTitle.preferredSize
    if (!expanded) {
      return JBDimension(PREFERRED_WIDTH, titleSize.height)
    }
    else {
      val listHeight = list.preferredSize.height + COMPONENT_HEIGHT / 2
      val warningTextHeight = warningPanel.preferredSize.height
      return JBDimension(PREFERRED_WIDTH, titleSize.height + listHeight + warningTextHeight)
    }
  }

  override fun configureUi() {
    listData.clear()

    val component = widgetModel.component
    if (component != null) {
      for (item in CONSTRAINT_WIDGET_SECTION_ITEMS) {
        if (item.condition(component)) {
          val boldText = item.boldTextFunc(component)
          val fadingText = item.fadingTextFuc(component)
          listData.add(ConstraintCellData(item.namespace, item.attribute, item.displayName, boldText, fadingText))
        }
      }
    }
    list.visibleRowCount = listData.size

    val hasConstraintSelection = updateConstraintSelection()
    // Force expand the list if there is constraint selection
    setExpand(if (hasConstraintSelection) true else expanded)
    repaint()
  }

  // Return true if list has constraint selection, otherwise false.
  private fun updateConstraintSelection(): Boolean {
    val selectionModel = widgetModel.surface?.selectionModel ?: return false
    val widgetComponent = widgetModel.component
    val secondarySelection = selectionModel.secondarySelection
    if (previousComponent != widgetModel.component && secondarySelection == null) {
      // Component is changed and new component doesn't have secondary selection.
      list.clearSelection()
      previousComponent = widgetComponent
      return false
    }
    previousComponent = widgetComponent
    if (secondarySelection is SecondarySelector.Constraint) {
      val scene = widgetModel.surface?.scene ?: return false
      val apiLevel = scene.renderedApiLevel
      val rtl = scene.isInRTL
      val attributes = getAttributesForConstraint(secondarySelection, apiLevel, rtl)

      for (index in listData.indices) {
        if (listData[index].attribute in attributes) {
          if (list.selectedIndex != index) {
            list.selectedIndex = index
          }
          return true
        }
      }
    }
    // Didn't find any secondary constraint
    list.clearSelection()
    return false
  }

  private inner class SectionTitle: JPanel(BorderLayout()) {

    private val icon = JLabel()
    private val constraintText = object: JLabel() {
      override fun paint(g: Graphics) {
        super.paint(g)
        val hasWarning = widgetModel.isMissingHorizontalConstrained ||
                         widgetModel.isMissingVerticalConstrained ||
                         widgetModel.isOverConstrained
        if (!expanded && hasWarning) {
          val originalColor = g.color
          g.color = Color.RED
          val shiftY = 0.8 * height
          EffectPainter2D.WAVE_UNDERSCORE.paint(g as Graphics2D, 0.0, (y + shiftY), width.toDouble(), (height - shiftY), null)
          g.color = originalColor
        }
      }
    }

    private val constraintNumberText = JLabel()

    init {
      preferredSize = JBUI.size(PREFERRED_WIDTH, COMPONENT_HEIGHT)
      border = JBUI.Borders.empty(4)
      background = secondaryPanelBackground

      icon.icon = if (expanded) UIUtil.getTreeExpandedIcon() else UIUtil.getTreeCollapsedIcon()
      icon.border = JBUI.Borders.emptyRight(4)
      constraintText.text = "Constraints"

      val title = JPanel(BorderLayout())
      title.background = secondaryPanelBackground
      title.add(icon, BorderLayout.WEST)
      title.add(constraintText, BorderLayout.CENTER)

      add(title, BorderLayout.WEST)
      add(constraintNumberText, BorderLayout.CENTER)
      constraintNumberText.foreground = ERROR_TEXT_COLOR
    }

    fun updateTitle() {
      icon.icon = if (expanded) UIUtil.getTreeExpandedIcon() else UIUtil.getTreeCollapsedIcon()
      if (expanded) {
        icon.icon = UIUtil.getTreeExpandedIcon()
        constraintText
        constraintNumberText.text = ""
      }
      else {
        icon.icon = UIUtil.getTreeCollapsedIcon()
        constraintNumberText.text = "(${listData.size})"
      }
    }
  }

  private inner class WarningPanel: JPanel(BorderLayout()) {

    private val horizontalWarning = JLabel()
    private val verticalWarning = JLabel()
    private val overConstrainedWarning = JLabel()

    private val mouseListener = object: MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        val component = widgetModel.component ?: return
        widgetModel.surface?.issuePanel?.showIssueForComponent(component, true)
      }
    }

    init {
      background = secondaryPanelBackground

      horizontalWarning.icon = StudioIcons.Common.ERROR_INLINE
      horizontalWarning.text = "Not Horizontally Constrained"
      horizontalWarning.border = JBUI.Borders.empty(2)
      horizontalWarning.foreground = ERROR_TEXT_COLOR
      horizontalWarning.addMouseListener(mouseListener)

      verticalWarning.icon = StudioIcons.Common.ERROR_INLINE
      verticalWarning.text = "Not Vertically Constrained"
      verticalWarning.border = JBUI.Borders.empty(2)
      verticalWarning.foreground = ERROR_TEXT_COLOR
      verticalWarning.addMouseListener(mouseListener)

      overConstrainedWarning.icon = StudioIcons.Common.ERROR_INLINE
      overConstrainedWarning.text = "Over Constrained"
      overConstrainedWarning.border = JBUI.Borders.empty(2)
      overConstrainedWarning.foreground = ERROR_TEXT_COLOR
      overConstrainedWarning.addMouseListener(mouseListener)

      add(horizontalWarning, BorderLayout.NORTH)
      add(verticalWarning, BorderLayout.CENTER)
      add(overConstrainedWarning, BorderLayout.SOUTH)
    }

    fun updateWarningMessage() {
      horizontalWarning.isVisible = widgetModel.isMissingHorizontalConstrained
      verticalWarning.isVisible = widgetModel.isMissingVerticalConstrained
      overConstrainedWarning.isVisible = widgetModel.isOverConstrained

      isVisible = components.any { it.isVisible }
    }

    override fun getPreferredSize(): Dimension {
      val horizontal = if (horizontalWarning.isVisible) horizontalWarning.preferredSize.height else 0
      val vertical = if (verticalWarning.isVisible) verticalWarning.preferredSize.height else 0
      val over = if (overConstrainedWarning.isVisible) overConstrainedWarning.preferredSize.height else 0

      return JBDimension(PREFERRED_WIDTH, horizontal + vertical + over)
    }
  }
}

private class ConstraintCellData(val namespace: String,
                                 val attribute: String,
                                 val displayName: String,
                                 val boldValue: String?,
                                 val fadingValue: String?)

private val constraintIcon = StudioIcons.LayoutEditor.Palette.CONSTRAINT_LAYOUT
private val highlightConstraintIcon = ColoredIconGenerator.generateWhiteIcon(constraintIcon)

private val FADING_LABEL_COLOR = Color(0x999999)

private class ConstraintItemRenderer : DefaultListCellRenderer() {
  private val panel = JPanel()
  private val iconLabel = JLabel(StudioIcons.LayoutEditor.Palette.CONSTRAINT_LAYOUT)
  private val nameLabel = JLabel()
  private val boldLabel = JLabel()
  private val fadingLabel = JLabel()

  init {
    horizontalAlignment = SwingConstants.LEADING

    preferredSize = JBDimension(PREFERRED_WIDTH, COMPONENT_HEIGHT)

    panel.layout = BorderLayout()
    panel.background = secondaryPanelBackground

    val centerPanel = JPanel(BorderLayout()).apply { isOpaque = true}

    iconLabel.border = JBUI.Borders.empty(2)
    iconLabel.isOpaque = true

    nameLabel.border = JBUI.Borders.empty(2)
    nameLabel.isOpaque = true

    boldLabel.border = JBUI.Borders.empty(2)
    boldLabel.isOpaque = true
    // font of value should be bold
    val valueFont = boldLabel.font
    boldLabel.font = valueFont.deriveFont(valueFont.style or Font.BOLD)

    fadingLabel.border = JBUI.Borders.empty(2)
    fadingLabel.foreground = Color.LIGHT_GRAY
    fadingLabel.isOpaque = true

    centerPanel.add(nameLabel, BorderLayout.WEST)

    val valuePanel = JPanel(BorderLayout())
    valuePanel.add(boldLabel, BorderLayout.WEST)
    valuePanel.add(fadingLabel, BorderLayout.CENTER)
    valuePanel.isOpaque = true

    centerPanel.add(valuePanel, BorderLayout.CENTER)

    panel.add(iconLabel, BorderLayout.WEST)
    panel.add(centerPanel, BorderLayout.CENTER)
  }

  override fun getListCellRendererComponent(list: JList<*>,
                                            value: Any?,
                                            index: Int,
                                            selected: Boolean,
                                            expanded: Boolean): Component {
    val item = value as ConstraintCellData
    nameLabel.text = item.displayName
    boldLabel.text = if (item.boldValue != null) item.boldValue.removePrefix("@+id/").removePrefix("@id/") else ""
    fadingLabel.text = if (item.fadingValue != null) "(${item.fadingValue})" else ""
    panel.toolTipText = "${item.displayName}=${item.boldValue}"

    if (selected) {
      iconLabel.icon = highlightConstraintIcon

      iconLabel.background = list.selectionBackground
      panel.background = list.selectionBackground
      nameLabel.background = list.selectionBackground
      boldLabel.background = list.selectionBackground
      fadingLabel.background = list.selectionBackground

      iconLabel.foreground = list.selectionForeground
      panel.foreground = list.selectionForeground
      nameLabel.foreground = list.selectionForeground
      boldLabel.foreground = list.selectionForeground
      fadingLabel.foreground = list.selectionForeground
    }
    else {
      iconLabel.icon = constraintIcon

      iconLabel.background = secondaryPanelBackground
      panel.background = secondaryPanelBackground
      nameLabel.background = secondaryPanelBackground
      boldLabel.background = secondaryPanelBackground
      fadingLabel.background = secondaryPanelBackground

      iconLabel.foreground = list.foreground
      panel.foreground = list.foreground
      nameLabel.foreground = list.foreground
      boldLabel.foreground = list.foreground
      fadingLabel.foreground = FADING_LABEL_COLOR
    }

    return panel
  }
}

/**
 * Return a list of attributes which have same direction of the given [SecondarySelector.Constraint].
 */
fun getAttributesForConstraint(constraint: SecondarySelector.Constraint, apiLevel: Int, isRtl: Boolean): List<String> {
  return when (constraint) {
    SecondarySelector.Constraint.LEFT -> when {
      apiLevel < RtlSupportProcessor.RTL_TARGET_SDK_START -> LEFT_ATTRIBUTES
      isRtl -> END_ATTRIBUTES + LEFT_ATTRIBUTES
      else -> START_ATTRIBUTES + LEFT_ATTRIBUTES
    }
    SecondarySelector.Constraint.RIGHT -> when {
      apiLevel < RtlSupportProcessor.RTL_TARGET_SDK_START -> RIGHT_ATTRIBUTES
      isRtl -> START_ATTRIBUTES + RIGHT_ATTRIBUTES
      else -> END_ATTRIBUTES + RIGHT_ATTRIBUTES
    }
    SecondarySelector.Constraint.TOP -> TOP_ATTRIBUTES
    SecondarySelector.Constraint.BOTTOM -> BOTTOM_ATTRIBUTES
    else -> BASELINE_ATTRIBUTES
  }
}

/**
 * Return [SecondarySelector.Constraint] which represent the same direction of the given attribute.
 */
fun getConstraintForAttribute(attribute: String, apiLevel: Int, isRtl: Boolean): SecondarySelector.Constraint? {
  when {
    apiLevel < RtlSupportProcessor.RTL_TARGET_SDK_START -> return when (attribute) {
      in LEFT_ATTRIBUTES -> SecondarySelector.Constraint.LEFT
      in RIGHT_ATTRIBUTES -> SecondarySelector.Constraint.RIGHT
      in TOP_ATTRIBUTES -> SecondarySelector.Constraint.TOP
      in BOTTOM_ATTRIBUTES -> SecondarySelector.Constraint.BOTTOM
      in BASELINE_ATTRIBUTES -> SecondarySelector.Constraint.BASELINE
      else -> null
    }
    isRtl -> return when (attribute) {
      in LEFT_ATTRIBUTES, in END_ATTRIBUTES -> SecondarySelector.Constraint.LEFT
      in RIGHT_ATTRIBUTES, in START_ATTRIBUTES -> SecondarySelector.Constraint.RIGHT
      in TOP_ATTRIBUTES -> SecondarySelector.Constraint.TOP
      in BOTTOM_ATTRIBUTES -> SecondarySelector.Constraint.BOTTOM
      in BASELINE_ATTRIBUTES -> SecondarySelector.Constraint.BASELINE
      else -> null
    }
    else -> return when (attribute) {
      in LEFT_ATTRIBUTES, in START_ATTRIBUTES -> SecondarySelector.Constraint.LEFT
      in RIGHT_ATTRIBUTES, in END_ATTRIBUTES -> SecondarySelector.Constraint.RIGHT
      in TOP_ATTRIBUTES -> SecondarySelector.Constraint.TOP
      in BOTTOM_ATTRIBUTES -> SecondarySelector.Constraint.BOTTOM
      in BASELINE_ATTRIBUTES -> SecondarySelector.Constraint.BASELINE
      else -> null
    }
  }
}

private val START_ATTRIBUTES = listOf(
  SdkConstants.ATTR_LAYOUT_START_TO_START_OF,
  SdkConstants.ATTR_LAYOUT_START_TO_END_OF
)

private val END_ATTRIBUTES = listOf(
  SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
  SdkConstants.ATTR_LAYOUT_END_TO_END_OF
)

private val LEFT_ATTRIBUTES = listOf(
  SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF,
  SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF
)

private val RIGHT_ATTRIBUTES = listOf(
  SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
  SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF
)

private val TOP_ATTRIBUTES = listOf(
  SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF,
  SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF
)

private val BOTTOM_ATTRIBUTES = listOf(
  SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
  SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF
)

private val BASELINE_ATTRIBUTES = listOf(
  SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF
)

val CONSTRAINT_ATTRIBUTES = listOf(
  SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF,
  SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
  SdkConstants.ATTR_LAYOUT_START_TO_START_OF,
  SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
  SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
  SdkConstants.ATTR_LAYOUT_END_TO_END_OF,
  SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
  SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
  SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF,
  SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
  SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
  SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
  SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF
)

class WidgetEmptySection : WidgetSection() {
  init {
    preferredSize = JBDimension(0, 0)
  }

  override fun configureUi() = Unit
}
