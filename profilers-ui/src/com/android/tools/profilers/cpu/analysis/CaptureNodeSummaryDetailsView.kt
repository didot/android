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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.TabularLayout
import com.android.tools.profilers.StudioProfilersView
import com.google.common.annotations.VisibleForTesting
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JPanel

class CaptureNodeSummaryDetailsView(profilersView: StudioProfilersView,
                                    tabModel: CaptureNodeAnalysisSummaryTabModel)
  : SummaryDetailsViewBase<CaptureNodeAnalysisSummaryTabModel>(profilersView, tabModel) {
  @get:VisibleForTesting
  val timeRangeLabel = JLabel()

  @get:VisibleForTesting
  val dataTypeLabel = JLabel()

  init {
    val range = tabModel.selectionRange
    timeRangeLabel.text = formatTimeRangeAsString(range)
    dataTypeLabel.text = tabModel.label

    addRowToCommonSection("Time Range", timeRangeLabel)
    addRowToCommonSection("Data Type", dataTypeLabel)
    addSection(buildSelectedNodeTable())
  }

  private fun buildSelectedNodeTable() = JPanel(TabularLayout("*").setVGap(8)).apply {
    val selectedNodes = tabModel.dataSeries.map { it.node }
    add(JLabel("Selected event(s)".apply {
      font = font.deriveFont(Font.BOLD)
      isOpaque = false
    }), TabularLayout.Constraint(0, 0))
    add(CaptureNodeDetailTable(selectedNodes, tabModel.captureRange).component, TabularLayout.Constraint(1, 0))
  }
}