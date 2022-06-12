/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.monitor.ui

import com.android.ddmlib.ClientData
import com.android.tools.adtui.common.ColumnTreeBuilder
import com.android.tools.adtui.common.ColumnTreeBuilder.ColumnBuilder
import com.android.tools.idea.device.monitor.ProcessInfoTreeNode
import com.android.tools.idea.device.monitor.ProcessInfoTreeNode.Companion.fromNode
import com.android.tools.idea.device.monitor.processes.isPidOnly
import com.android.tools.idea.device.monitor.processes.safeProcessName
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.tree.TreePath

class ProcessListTreeBuilder {
  fun build(myTree: Tree): JComponent {
    val treeSpeedSearch = TreeSpeedSearch(myTree, { path: TreePath ->
      fromNode(path.lastPathComponent)?.processInfo?.processName
    }, true)

    val builder = ColumnTreeBuilder(myTree)
      .setBackground(UIUtil.getTreeBackground())
      .addColumn(
        ColumnBuilder()
          .setName("Process Name")
          .setPreferredWidth(JBUI.scale(600))
          .setHeaderAlignment(SwingConstants.CENTER)
          .setHeaderBorder(
            JBUI.Borders.empty(
              DeviceMonitorPanel.TEXT_RENDERER_VERT_PADDING,
              DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING
            )
          )
          .setRenderer(NameRenderer(treeSpeedSearch))
      )
      .addColumn(
        ColumnBuilder()
          .setName("PID")
          .setPreferredWidth(JBUI.scale(150))
          .setHeaderAlignment(SwingConstants.CENTER)
          .setHeaderBorder(
            JBUI.Borders.empty(
              DeviceMonitorPanel.TEXT_RENDERER_VERT_PADDING,
              DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING
            )
          )
          .setRenderer(PidRenderer())
      )
      .addColumn(
        ColumnBuilder()
          .setName("ABI")
          .setPreferredWidth(JBUI.scale(200))
          .setHeaderAlignment(SwingConstants.CENTER)
          .setHeaderBorder(
            JBUI.Borders.empty(
              DeviceMonitorPanel.TEXT_RENDERER_VERT_PADDING,
              DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING
            )
          )
          .setRenderer(AbiRenderer())
      )
      .addColumn(
        ColumnBuilder()
          .setName("VM")
          .setPreferredWidth(JBUI.scale(200))
          .setHeaderAlignment(SwingConstants.CENTER)
          .setHeaderBorder(
            JBUI.Borders.empty(
              DeviceMonitorPanel.TEXT_RENDERER_VERT_PADDING,
              DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING
            )
          )
          .setRenderer(VmIdentifierRenderer())
      )
      .addColumn(
        ColumnBuilder()
          .setName("User ID")
          .setPreferredWidth(JBUI.scale(100))
          .setHeaderAlignment(SwingConstants.CENTER)
          .setHeaderBorder(
            JBUI.Borders.empty(
              DeviceMonitorPanel.TEXT_RENDERER_VERT_PADDING,
              DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING
            )
          )
          .setRenderer(UserIdRenderer())
      )
      .addColumn(
        ColumnBuilder()
          .setName("Debugger")
          .setPreferredWidth(JBUI.scale(100))
          .setHeaderAlignment(SwingConstants.CENTER)
          .setHeaderBorder(
            JBUI.Borders.empty(
              DeviceMonitorPanel.TEXT_RENDERER_VERT_PADDING,
              DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING
            )
          )
          .setRenderer(StatusRenderer())
      )
      .addColumn(
        ColumnBuilder()
          .setName("Native")
          .setPreferredWidth(JBUI.scale(100))
          .setHeaderAlignment(SwingConstants.CENTER)
          .setHeaderBorder(
            JBUI.Borders.empty(
              DeviceMonitorPanel.TEXT_RENDERER_VERT_PADDING,
              DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING
            )
          )
          .setRenderer(SupportsNativeDebuggingRenderer())
      )
    return builder.build()
  }

  class NameRenderer(private val mySpeedSearch: TreeSpeedSearch) : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean
    ) {
      toolTipText = null
      icon = null
      ipad = JBUI.insets(0, 0, 0, DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING)
      fromNode(value)?.let { node ->
        icon = getIconFor(node)
        if (node.processInfo.isPidOnly) {
          append(node.processInfo.safeProcessName, SimpleTextAttributes.ERROR_ATTRIBUTES)
        }
        else {
          // Add name fragment (with speed search support)
          val attr = SimpleTextAttributes.REGULAR_ATTRIBUTES
          SearchUtil.appendFragments(
            mySpeedSearch.enteredPrefix, node.processInfo.processName, attr.style,
            attr.fgColor,
            attr.bgColor, this
          )
        }
      }
    }

    companion object {
      private fun getIconFor(@Suppress("UNUSED_PARAMETER") node: ProcessInfoTreeNode): Icon {
        return StudioIcons.Shell.Filetree.ACTIVITY
      }
    }
  }

  class PidRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(tree: JTree,
                                       value: Any?,
                                       selected: Boolean,
                                       expanded: Boolean,
                                       leaf: Boolean,
                                       row: Int,
                                       hasFocus: Boolean) {
      fromNode(value)?.let {
        append(it.processInfo.pid.toString())
        setTextAlign(SwingConstants.TRAILING)
      }
      ipad = JBUI.insets(0, DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING, 0, DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING)
    }
  }

  class AbiRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(tree: JTree,
                                       value: Any?,
                                       selected: Boolean,
                                       expanded: Boolean,
                                       leaf: Boolean,
                                       row: Int,
                                       hasFocus: Boolean) {
      fromNode(value)?.let {
        if (it.processInfo.isPidOnly) {
          append("-")
        }
        else {
          append(it.processInfo.abi ?: "-")
        }
      }
      ipad = JBUI.insets(0, DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING, 0, DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING)
    }
  }

  class StatusRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean
    ) {
      fromNode(value)?.let {
        if (it.processInfo.isPidOnly) {
          append("-")
        }
        else {
          val status = when (it.processInfo.debuggerStatus) {
            ClientData.DebuggerStatus.DEFAULT -> "No"
            ClientData.DebuggerStatus.WAITING -> "Waiting"
            ClientData.DebuggerStatus.ATTACHED -> "Attached"
            ClientData.DebuggerStatus.ERROR -> "<Error>"
          }
          append(status)
        }
      }
      ipad = JBUI.insets(0, DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING, 0, DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING)
    }
  }

  class SupportsNativeDebuggingRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean
    ) {
      fromNode(value)?.let {
        if (it.processInfo.isPidOnly) {
          append("-")
        }
        else {
          append(if (it.processInfo.supportsNativeDebugging) "Yes" else "No")
        }
        setTextAlign(SwingConstants.CENTER)
      }
      ipad = JBUI.insets(0, DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING, 0, DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING)
    }
  }

  class UserIdRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(tree: JTree,
                                       value: Any?,
                                       selected: Boolean,
                                       expanded: Boolean,
                                       leaf: Boolean,
                                       row: Int,
                                       hasFocus: Boolean) {
      fromNode(value)?.let {
        if (it.processInfo.isPidOnly) {
          append("-")
        }
        else {
          append(it.processInfo.userId?.toString() ?: "n/a")
        }
        setTextAlign(SwingConstants.TRAILING)
      }
      ipad = JBUI.insets(0, DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING, 0, DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING)
    }
  }

  class VmIdentifierRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(tree: JTree,
                                       value: Any?,
                                       selected: Boolean,
                                       expanded: Boolean,
                                       leaf: Boolean,
                                       row: Int,
                                       hasFocus: Boolean) {
      fromNode(value)?.let {
        if (it.processInfo.isPidOnly) {
          append("-")
        }
        else {
          append(it.processInfo.vmIdentifier ?: "-")
        }
      }
      ipad = JBUI.insets(0, DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING, 0, DeviceMonitorPanel.TEXT_RENDERER_HORIZ_PADDING)
    }
  }
}