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
package com.android.build.attribution.ui

import com.android.build.attribution.ui.data.TimeWithPercentage
import com.intellij.icons.AllIcons
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import javax.swing.Icon
import javax.swing.JComponent


fun TimeWithPercentage.durationString() = durationString(timeMs)

fun TimeWithPercentage.percentageString() = "%.1f%%".format(percentage)

fun durationString(timeMs: Long) = "%.1fs".format(timeMs.toDouble() / 1000)

fun warningsCountString(warningsCount: Int) = when (warningsCount) {
  0 -> ""
  1 -> "1 warning"
  else -> "${warningsCount} warnings"
}

fun warningIcon(): Icon = AllIcons.General.BalloonWarning

/**
 * Label with auto-wrapping turned on that accepts html text.
 * Used in Build Analyzer to render long multi-line text.
 */
fun htmlTextLabelWithLinesWrap(htmlBodyContent: String): JComponent =
  SwingHelper.createHtmlViewer(true, null, null, null).apply {
    border = JBUI.Borders.empty()
    SwingHelper.setHtml(this, htmlBodyContent, null)
  }

fun htmlTextLabelWithFixedLines(htmlBodyContent: String): JComponent =
  SwingHelper.createHtmlViewer(false, null, null, null).apply {
    border = JBUI.Borders.empty()
    SwingHelper.setHtml(this, htmlBodyContent, null)
  }

/**
 * Wraps long path to spans to make it possible to auto-wrap to a new line
 */
fun wrapPathToSpans(text: String): String = "<p>${text.replace("/", "<span>/</span>")}</p>"