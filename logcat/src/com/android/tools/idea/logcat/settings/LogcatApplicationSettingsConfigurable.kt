/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.settings

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.LogcatToolWindowFactory
import com.android.tools.idea.logcat.filters.parser.LogcatFilterFileType
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtilRt.LARGE_FOR_CONTENT_LOADING
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GridBag
import org.jetbrains.annotations.VisibleForTesting
import java.awt.GridBagConstraints.NORTHWEST
import java.awt.GridBagConstraints.WEST
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

private const val MAX_BUFFER_SIZE_MB = 100
private const val MAX_BUFFER_SIZE_KB = 1024 * MAX_BUFFER_SIZE_MB

// TODO(aalbert): Maybe change this to be a ConfigurableUi and use SimpleConfigurable?
internal class LogcatApplicationSettingsConfigurable(private val logcatSettings: AndroidLogcatSettings) : Configurable, Configurable.NoScroll {
  @VisibleForTesting
  internal val cycleBufferSizeTextField = JTextField(10).apply {
    text = (logcatSettings.bufferSize / 1024).toString()
  }

  @VisibleForTesting
  internal val defaultFilterTextField = EditorTextField(ProjectManager.getInstance().defaultProject, LogcatFilterFileType).apply {
    isEnabled = !logcatSettings.mostRecentlyUsedFilterIsDefault
  }

  @VisibleForTesting
  internal val mostRecentlyUsedFilterIsDefaultCheckbox = JCheckBox(LogcatBundle.message("logcat.settings.default.filter.mru")).apply {
    isSelected = logcatSettings.mostRecentlyUsedFilterIsDefault
    addActionListener { defaultFilterTextField.isEnabled = !isSelected }
  }

  @VisibleForTesting
  internal val cyclicBufferSizeWarningLabel = JLabel()

  @VisibleForTesting
  internal val filterHistoryAutocompleteCheckbox =
    JCheckBox(LogcatBundle.message("logcat.settings.history.autocomplete"), logcatSettings.filterHistoryAutocomplete)

  @VisibleForTesting
  internal val enableNamedFiltersCheckbox =
    JCheckBox(LogcatBundle.message("logcat.settings.enable.named.filters"), logcatSettings.namedFiltersEnabled)

  private val component = JPanel(GridBagLayout()).apply {
    cyclicBufferSizeWarningLabel.foreground = JBColor.red
    cycleBufferSizeTextField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        updateWarningLabel()
      }
    })
    val gridBag = GridBag().anchor(NORTHWEST)
    add(JLabel(LogcatBundle.message("logcat.settings.buffer.size")), gridBag.nextLine().next().anchor(WEST))
    add(Box.createHorizontalStrut(JBUIScale.scale(20)), gridBag.next())
    add(cycleBufferSizeTextField, gridBag.next().anchor(WEST))
    add(JLabel(LogcatBundle.message("logcat.settings.buffer.kb")), gridBag.next().weightx(1.0).anchor(WEST))
    add(cyclicBufferSizeWarningLabel, gridBag.nextLine().next().coverLine().anchor(NORTHWEST))

    add(JLabel(LogcatBundle.message("logcat.settings.default.filter")), gridBag.nextLine().next().anchor(WEST))
    add(Box.createHorizontalStrut(JBUIScale.scale(20)), gridBag.next())
    add(defaultFilterTextField, gridBag.next().anchor(WEST).fillCellHorizontally().weightx(1.0).coverLine())
    add(mostRecentlyUsedFilterIsDefaultCheckbox, gridBag.nextLine().setColumn(2).coverLine().anchor(WEST))

    add(filterHistoryAutocompleteCheckbox, gridBag.nextLine().next().coverLine().anchor(NORTHWEST))

    if (StudioFlags.LOGCAT_NAMED_FILTERS_ENABLE.get()) {
      add(enableNamedFiltersCheckbox, gridBag.nextLine().next().coverLine().anchor(NORTHWEST))
    }
    // Add an empty panel that consumes all vertical space bellow.
    add(JPanel(), gridBag.nextLine().next().weighty(1.0))
  }

  override fun createComponent() = component.apply {
    // Changing EditorTextField.text requires EDT, so we don't touch it during construction.
    defaultFilterTextField.text = logcatSettings.defaultFilter
  }

  private fun updateWarningLabel() {
    val value = getBufferSizeKb()
    cyclicBufferSizeWarningLabel.text = when {
      value == null || !isValidBufferSize(value) ->
        LogcatBundle.message("logcat.settings.buffer.warning.invalid", MAX_BUFFER_SIZE_KB.toString(), MAX_BUFFER_SIZE_MB.toString())
      value > LARGE_FOR_CONTENT_LOADING / 1024 -> LogcatBundle.message("logcat.settings.buffer.warning.tooLarge")
      else -> ""
    }
  }

  override fun getDisplayName(): String = LogcatBundle.message("logcat.settings.title")

  override fun isModified(): Boolean {
    val bufferSizeKb = getBufferSizeKb()
    return (bufferSizeKb != null && isValidBufferSize(bufferSizeKb) && bufferSizeKb != logcatSettings.bufferSize / 1024)
           || defaultFilterTextField.text != logcatSettings.defaultFilter
           || mostRecentlyUsedFilterIsDefaultCheckbox.isSelected != logcatSettings.mostRecentlyUsedFilterIsDefault
           || filterHistoryAutocompleteCheckbox.isSelected != logcatSettings.filterHistoryAutocomplete
           || enableNamedFiltersCheckbox.isSelected != logcatSettings.namedFiltersEnabled
  }

  override fun apply() {
    logcatSettings.bufferSize = getBufferSizeKb()?.times(1024) ?: return
    logcatSettings.defaultFilter = defaultFilterTextField.text
    logcatSettings.mostRecentlyUsedFilterIsDefault = mostRecentlyUsedFilterIsDefaultCheckbox.isSelected
    logcatSettings.filterHistoryAutocomplete = filterHistoryAutocompleteCheckbox.isSelected
    logcatSettings.namedFiltersEnabled = enableNamedFiltersCheckbox.isSelected

    LogcatToolWindowFactory.logcatPresenters.forEach {
      it.applyLogcatSettings(logcatSettings)
    }
  }

  private fun getBufferSizeKb() = try {
    cycleBufferSizeTextField.text.toInt()
  }
  catch (e: NumberFormatException) {
    null
  }
}

private fun isValidBufferSize(value: Int) = value in (1..MAX_BUFFER_SIZE_KB)
