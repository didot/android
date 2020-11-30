/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.attribution.ui.view

import com.android.build.attribution.ui.emptyIcon
import com.android.build.attribution.ui.model.PluginDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TaskDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TasksTreePresentableNodeDescriptor
import com.android.build.attribution.ui.model.WarningsTreePresentableNodeDescriptor
import com.android.build.attribution.ui.panels.CriticalPathChartLegend
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation.NodeIconState
import com.android.build.attribution.ui.warningIcon
import com.android.tools.adtui.common.ColoredIconGenerator.generateWhiteIcon
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.render.RenderingHelper.SHRINK_LONG_RENDERER
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Shape
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class BuildAnalyzerMasterTreeCellRenderer : NodeRenderer() {

  private val colorIconSize: Int = JBUIScale.scale(12)
  private val rightAlignedFont = JBUI.Fonts.create(Font.MONOSPACED, 11)
  private var durationTextPresentation: RightAlignedDurationTextPresentation? = null

  init {
    putClientProperty(SHRINK_LONG_RENDERER, true)
  }

  override fun customizeCellRenderer(
    tree: JTree,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean
  ) {
    cleanup()

    val node = value as DefaultMutableTreeNode
    val userObj = node.userObject
    if (userObj is TasksTreePresentableNodeDescriptor) {
      val chartKeyColor = tasksTreeNodesChartKeyColor(userObj)
      customize(userObj.presentation, selected, hasFocus, chartKeyColor)
    }
    else if (userObj is WarningsTreePresentableNodeDescriptor) {
      customize(userObj.presentation, selected, hasFocus, null)
    }
    else {
      super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
    }
  }

  private fun cleanup() {
    durationTextPresentation = null
  }

  private fun customize(nodePresentation: BuildAnalyzerTreeNodePresentation, selected: Boolean, hasFocus: Boolean, chartKeyColor: Color?) {
    icon = when (nodePresentation.nodeIconState) {
      NodeIconState.NO_ICON -> null
      NodeIconState.EMPTY_PLACEHOLDER -> emptyIcon()
      NodeIconState.WARNING_ICON -> if (selected && hasFocus) generateWhiteIcon(warningIcon()) else warningIcon()
    }
    append(nodePresentation.mainText, SimpleTextAttributes.REGULAR_ATTRIBUTES, true)
    append(" ${nodePresentation.suffix}", SimpleTextAttributes.GRAYED_ATTRIBUTES)

    durationTextPresentation = nodePresentation.rightAlignedSuffix.let { text ->
      val metrics = getFontMetrics(rightAlignedFont)
      RightAlignedDurationTextPresentation(
        durationText = text,
        durationWidth = metrics.stringWidth(text),
        durationOffset = metrics.height / 2,
        durationColor = if (selected) UIUtil.getTreeSelectionForeground(hasFocus)
        else SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor,
        chartIconColor = chartKeyColor
      )
    }
  }

  private fun tasksTreeNodesChartKeyColor(tasksNodeDescriptor: TasksTreePresentableNodeDescriptor): Color? {
    val nodePresentation = tasksNodeDescriptor.presentation
    return if (nodePresentation.showChartKey) {
      when (tasksNodeDescriptor) {
        is PluginDetailsNodeDescriptor ->
          CriticalPathChartLegend.pluginColorPalette.getColor(tasksNodeDescriptor.pluginData.name).baseColor
        is TaskDetailsNodeDescriptor ->
          CriticalPathChartLegend.resolveTaskColor(tasksNodeDescriptor.taskData).baseColor
      }
    }
    else null
  }

  override fun paintComponent(g: Graphics) {
    setupAntialiasing(g)
    var clip: Shape? = null
    var width = width
    val height = height
    if (isOpaque) {
      // paint background for expanded row
      g.color = background
      g.fillRect(0, 0, width, height)
    }
    durationTextPresentation?.let {
      width -= it.durationWidth + it.durationOffset + colorIconSize + it.durationOffset / 2
      if (width > 0 && height > 0) {
        g.color = it.durationColor
        g.font = rightAlignedFont
        g.drawString(it.durationText, width + it.durationOffset / 2, SimpleColoredComponent.getTextBaseLine(g.fontMetrics, height))
        if (it.chartIconColor != null) {
          g.color = it.chartIconColor
          g.fillRect(width + it.durationWidth + it.durationOffset, (height - colorIconSize) / 2, colorIconSize, colorIconSize)
        }
        clip = g.clip
        g.clipRect(0, 0, width, height)
      }
    }
    super.paintComponent(g)
    // restore clip area if needed
    if (clip != null) g.clip = clip
  }

  private data class RightAlignedDurationTextPresentation(
    val durationText: String,
    val durationColor: Color,
    val durationWidth: Int,
    val durationOffset: Int,
    val chartIconColor: Color? = null
  )
}

/**
 * Tasks tree node presentation used by [BuildAnalyzerMasterTreeCellRenderer] to render the node.
 */
data class BuildAnalyzerTreeNodePresentation(
  /** Node main text rendered in standard font. */
  val mainText: String,
  /** Additional text after main rendered in grey. Used to show warnings counter. */
  val suffix: String = "",
  /** Text that is rendered on the right side. Used to show the execution time. */
  val rightAlignedSuffix: String = "",
  /** What kind of icon should be rendered on the left of the node. */
  val nodeIconState: NodeIconState = NodeIconState.NO_ICON,
  /** If color icon matching chart should be rendered on the right. */
  val showChartKey: Boolean = false
) {
  enum class NodeIconState {
    /** No Icon should be rendered for this node. */
    NO_ICON,
    /** Render empty placeholder icon for this nod. This is required to align node text when other nodes on same level have an icon. */
    EMPTY_PLACEHOLDER,
    /** Render warning icon for this node. */
    WARNING_ICON
  }
}
