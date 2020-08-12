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
package com.android.tools.idea.appinspection.inspectors.workmanager.view

import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import com.android.tools.adtui.HoverRowTable
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorksTableModel
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.ui.InplaceButton
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.table.JBTable
import com.intellij.util.containers.ComparatorUtil.min
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

private const val CLOSE_BUTTON_SIZE = 24 // Icon is 16x16. This gives it some padding, so it doesn't touch the border.

/**
 * View class for the WorkManger Inspector Tab with a table of all active works.
 */
class WorkManagerInspectorTab(private val client: WorkManagerInspectorClient,
                              ideServices: AppInspectionIdeServices,
                              scope: CoroutineScope) {

  private class WorksTableCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(table: JTable?,
                                               value: Any?,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component =
      when (table?.convertColumnIndexToModel(column)) {
        // TODO(163343710): Add icons on the left of state text
        WorksTableModel.Column.STATE.ordinal -> {
          val text = WorkInfo.State.forNumber(value as Int).name
          val capitalizedText = text[0] + text.substring(1).toLowerCase(Locale.getDefault())
          super.getTableCellRendererComponent(table, capitalizedText, isSelected, hasFocus, row, column)
        }
        WorksTableModel.Column.TIME_STARTED.ordinal -> {
          val formatter = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
          val time = value as Long
          val timeText = if (time == -1L) "-" else formatter.format(Date(value))
          super.getTableCellRendererComponent(table, timeText, isSelected, hasFocus, row, column)
        }
        else -> super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
      }
  }

  private val classNameProvider = ClassNameProvider(ideServices, scope)
  private val timeProvider = TimeProvider()
  private val enqueuedAtProvider = EnqueuedAtProvider(ideServices, scope)
  private val stringListProvider = StringListProvider()
  private val constraintProvider = ConstraintProvider()
  private val outputDataProvider = OutputDataProvider()

  private val splitter = JBSplitter(false).apply {
    border = AdtUiUtils.DEFAULT_VERTICAL_BORDERS
    isOpaque = true
    firstComponent = JScrollPane(buildWorksTable())
    secondComponent = null
  }

  val component: JComponent = splitter

  var selectedRow = -1

  private fun buildWorksTable(): JBTable {
    val model = WorksTableModel(client)
    val table: JBTable = HoverRowTable(model)

    table.autoCreateRowSorter = true
    table.setDefaultRenderer(Object::class.java, WorksTableCellRenderer())

    // Adjusts width for each column.
    table.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        for (column in WorksTableModel.Column.values()) {
          table.columnModel.getColumn(column.ordinal).preferredWidth = (table.width * column.widthPercentage).toInt()
        }
      }
    })

    model.addTableModelListener {
      ApplicationManager.getApplication().invokeLater {
        if (splitter.secondComponent == null) {
          return@invokeLater
        }
        if (selectedRow != -1) {
          table.addRowSelectionInterval(selectedRow, selectedRow)
          splitter.secondComponent = buildDetailedPanel(table, client.getWorkInfoOrNull(table.convertRowIndexToModel(selectedRow)))
        }
        else {
          splitter.secondComponent = buildDetailedPanel(table, null)
        }
      }
    }

    table.selectionModel.addListSelectionListener {
      if (table.selectedRow != -1) {
        selectedRow = table.selectedRow
        splitter.secondComponent = buildDetailedPanel(table, client.getWorkInfoOrNull(table.convertRowIndexToModel(selectedRow)))
      }
    }
    return table
  }

  private fun buildDetailedPanel(table: JTable, work: WorkInfo?): JComponent? {
    if (work == null) return splitter.secondComponent

    val headingPanel = JPanel(BorderLayout())
    val instanceViewLabel = JLabel("Work Details")
    instanceViewLabel.border = BorderFactory.createEmptyBorder(0, 5, 0, 0)
    headingPanel.add(instanceViewLabel, BorderLayout.WEST)
    val closeButton = CloseButton(ActionListener {
      splitter.secondComponent = null
    })
    headingPanel.add(closeButton, BorderLayout.EAST)


    val panel = JPanel(TabularLayout("*", "Fit,Fit,*"))
    panel.add(headingPanel, TabularLayout.Constraint(0, 0))
    panel.add(JSeparator(), TabularLayout.Constraint(1, 0))
    val detailPanel = object : ScrollablePanel(VerticalLayout(2)) {
      override fun getScrollableTracksViewportWidth() = false
      override fun getPreferredScrollableViewportSize(): Dimension {
        val result = super.getPreferredScrollableViewportSize()
        return Dimension(min(700, result.width), result.height)
      }
    }

    val idListProvider = IdListProvider(client, table, work)
    detailPanel.preferredScrollableViewportSize
    val scrollPane = JBScrollPane(detailPanel)
    scrollPane.border = BorderFactory.createEmptyBorder()
    detailPanel.add(buildKeyValuePair("Class", work.workerClassName, classNameProvider))
    detailPanel.add(buildKeyValuePair("Periodicity", if (work.isPeriodic) "Periodic" else "One Time"))
    if (work.namesCount > 0) {
      val uniqueName = work.namesList[0]
      detailPanel.add(buildKeyValuePair("Unique Work Name", uniqueName))
      detailPanel.add(buildKeyValuePair("", client.getWorkIdsWithUniqueName(uniqueName), idListProvider))
    }
    detailPanel.add(buildKeyValuePair("Tags", work.tagsList, stringListProvider))
    detailPanel.add(buildKeyValuePair("Enqueued At", work.callStack, enqueuedAtProvider))
    detailPanel.add(buildKeyValuePair("Time Started", work.scheduleRequestedAt, timeProvider))
    detailPanel.add(buildKeyValuePair("Retry Count", work.runAttemptCount))
    detailPanel.add(buildKeyValuePair("Prerequisites", work.prerequisitesList.toList(), idListProvider))
    detailPanel.add(buildKeyValuePair("Dependencies", work.dependentsList.toList(), idListProvider))
    detailPanel.add(buildKeyValuePair("Constraints", work.constraints, constraintProvider))
    detailPanel.add(buildKeyValuePair("State", work.state.name))
    detailPanel.add(buildKeyValuePair("Output Data", work.data, outputDataProvider))
    panel.add(scrollPane, TabularLayout.Constraint(2, 0))
    return panel
  }

  private fun <T> buildKeyValuePair(key: String,
                                    value: T,
                                    componentProvider: ComponentProvider<T> = ToStringProvider()): JPanel {
    val panel = JPanel(TabularLayout("180px,*"))
    val keyPanel = JPanel(BorderLayout())
    keyPanel.add(JBLabel(key), BorderLayout.NORTH) // If value is multi-line, key should stick to the top of its cell
    panel.add(keyPanel, TabularLayout.Constraint(0, 0))
    panel.add(componentProvider.convert(value), TabularLayout.Constraint(0, 1))
    return panel
  }
}

class CloseButton(actionListener: ActionListener?) : InplaceButton(
  IconButton("Close", AllIcons.Ide.Notification.Close,
             AllIcons.Ide.Notification.CloseHover), actionListener) {

  init {
    preferredSize = Dimension(JBUI.scale(CLOSE_BUTTON_SIZE), JBUI.scale(CLOSE_BUTTON_SIZE))
    minimumSize = preferredSize // Prevent layout phase from squishing this button
  }
}
