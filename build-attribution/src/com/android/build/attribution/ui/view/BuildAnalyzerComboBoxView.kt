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

import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CardLayoutPanel
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.JComponent

/**
 * Main view of Build Analyzer report that is based on ComboBoxes navigation on the top level.
 */
class BuildAnalyzerComboBoxView(
  private val model: BuildAnalyzerViewModel,
  private val actionHandlers: ViewActionHandlers
) {

  // Flag to prevent triggering calls to action handler on pulled from the model updates.
  private var fireActionHandlerEvents = true

  val dataSetCombo = ComboBox(EnumComboBoxModel(BuildAnalyzerViewModel.DataSet::class.java)).apply {
    name = "dataSetCombo"
    renderer = SimpleListCellRenderer.create { label, value, index -> label.text = value.uiName }
    selectedItem = this@BuildAnalyzerComboBoxView.model.selectedData
    addItemListener { event ->
      if (fireActionHandlerEvents && event.stateChange == ItemEvent.SELECTED) {
        actionHandlers.dataSetComboBoxSelectionUpdated(event.item as BuildAnalyzerViewModel.DataSet)
      }
    }
  }

  private val overviewPage = BuildOverviewPageView(model, actionHandlers)
  private val tasksPage = TasksPageView(model.tasksPageModel, actionHandlers)
  private val warningsPage = WarningsPageView(model.warningsPageModel, actionHandlers)

  private fun pageViewByDataSet(dataSet: BuildAnalyzerViewModel.DataSet): BuildAnalyzerDataPageView = when (dataSet) {
    BuildAnalyzerViewModel.DataSet.OVERVIEW -> overviewPage
    BuildAnalyzerViewModel.DataSet.TASKS -> tasksPage
    BuildAnalyzerViewModel.DataSet.WARNINGS -> warningsPage
  }

  private val pagesPanel = object : CardLayoutPanel<BuildAnalyzerViewModel.DataSet, BuildAnalyzerViewModel.DataSet, JComponent>() {
    override fun prepare(key: BuildAnalyzerViewModel.DataSet): BuildAnalyzerViewModel.DataSet = key

    override fun create(dataSet: BuildAnalyzerViewModel.DataSet): JComponent = pageViewByDataSet(dataSet).component
  }

  private val additionalControlsPanel = object : CardLayoutPanel<BuildAnalyzerViewModel.DataSet, BuildAnalyzerViewModel.DataSet, JComponent>() {
    override fun prepare(key: BuildAnalyzerViewModel.DataSet): BuildAnalyzerViewModel.DataSet = key

    override fun create(dataSet: BuildAnalyzerViewModel.DataSet): JComponent = pageViewByDataSet(dataSet).additionalControls
  }

  /**
   * Main panel that contains all the UI.
   */
  val wholePanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
    val controlsPanel = JBPanel<JBPanel<*>>(HorizontalLayout(10)).apply {
      border = JBUI.Borders.emptyLeft(4)
    }
    controlsPanel.add(dataSetCombo)
    controlsPanel.add(additionalControlsPanel)
    add(controlsPanel, BorderLayout.NORTH)
    add(pagesPanel, BorderLayout.CENTER)
  }

  init {
    pagesPanel.select(model.selectedData, true)
    additionalControlsPanel.select(model.selectedData, true)

    model.dataSetSelectionListener = {
      fireActionHandlerEvents = false
      model.selectedData.let {
        pagesPanel.select(it, true)
        additionalControlsPanel.select(it, true)
        dataSetCombo.selectedItem = it
      }
      fireActionHandlerEvents = true
    }
  }
}
