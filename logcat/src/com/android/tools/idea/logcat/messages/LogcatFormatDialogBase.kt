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
package com.android.tools.idea.logcat.messages

import com.android.ddmlib.Log
import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.logcat.LogcatBundle.message
import com.android.tools.idea.logcat.util.createLogcatEditor
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.Cell
import com.intellij.ui.layout.CellBuilder
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.ui.layout.applyToComponent
import com.intellij.ui.layout.enableIf
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.VisibleForTesting
import java.awt.GridLayout
import java.awt.event.ItemEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
import javax.swing.event.ChangeEvent
import kotlin.reflect.KMutableProperty0

private const val MIN_TAG_LENGTH = 10
private const val MAX_TAG_LENGTH = 35
private const val MIN_APP_NAME_LENGTH = 10
private const val MAX_APP_NAME_LENGTH = 45
private const val GRID_COLUMN_GAP = 50

private val sampleZoneId = ZoneId.of("GMT")
private val sampleTimestamp = Instant.from(ZonedDateTime.of(2021, 10, 4, 11, 0, 14, 234000000, sampleZoneId))
private val sampleMessages = listOf(
  LogCatMessage(LogCatHeader(Log.LogLevel.DEBUG, 27217, 3814, "com.example.app1", "ExampleTag1", sampleTimestamp),
                "Sample logcat message 1."),
  LogCatMessage(LogCatHeader(Log.LogLevel.INFO, 27217, 3814, "com.example.app1", "ExampleTag1", sampleTimestamp),
                "Sample logcat message 2."),
  LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 24395, 24395, "com.example.app2", "ExampleTag2", sampleTimestamp),
                "Sample logcat message 3."),
  LogCatMessage(LogCatHeader(Log.LogLevel.ERROR, 24395, 24395, "com.example.app2", "ExampleTag2", sampleTimestamp),
                "Sample logcat multiline\nmessage."),
)

private val MAX_SAMPLE_DOCUMENT_TEXT_LENGTH =
  TimestampFormat.Style.DATETIME.width +
  ProcessThreadFormat.Style.BOTH.width +
  MAX_TAG_LENGTH + 1 +
  MAX_APP_NAME_LENGTH + 1 +
  3 + 1 +
  "Sample logcat message #.".length
private const val MAX_SAMPLE_DOCUMENT_BUFFER_SIZE = Int.MAX_VALUE

/**
 * A base class for a Formatting Options dialog.
 */
internal abstract class LogcatFormatDialogBase(
  private val project: Project,
  protected val applyAction: ApplyAction
) : Disposable {
  private val components = mutableListOf<JComponent>()

  private lateinit var showTimestampCheckbox: JBCheckBox
  private lateinit var timestampStyleComboBox: ComboBox<TimestampFormat.Style>
  private lateinit var showPidCheckbox: JBCheckBox
  private lateinit var includeTidCheckbox: JBCheckBox
  private lateinit var showTagsCheckbox: JBCheckBox
  private lateinit var tagWidthSpinner: JBIntSpinner
  private lateinit var showRepeatedTagsCheckbox: JBCheckBox
  private lateinit var showPackagesCheckbox: JBCheckBox
  private lateinit var packageWidthSpinner: JBIntSpinner
  private lateinit var showRepeatedPackagesCheckbox: JBCheckBox

  @VisibleForTesting
  var sampleEditor = createLogcatEditor(project).apply {
    scrollPane.horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
    scrollPane.verticalScrollBarPolicy = VERTICAL_SCROLLBAR_NEVER
  }

  private val sampleFormattingOptions = FormattingOptions()
  private val sampleMessageFormatter = MessageFormatter(LogcatColors(), sampleZoneId)

  val dialogWrapper: DialogWrapper by lazy {
    createDialogWrapper().apply {
      Disposer.register(disposable, this@LogcatFormatDialogBase)
      onComponentsChanged(isUserAction = false)
      pack()
    }
  }

  abstract fun createDialogWrapper(): DialogWrapper

  final override fun dispose() {
    EditorFactory.getInstance().releaseEditor(sampleEditor)
  }

  /**
   * Applies the state of the dialog to a [FormattingOptions] object.
   */
  fun applyToFormattingOptions(formattingOptions: FormattingOptions) {
    formattingOptions.apply {
      timestampFormat = TimestampFormat(timestampStyleComboBox.item, showTimestampCheckbox.isSelected)
      processThreadFormat = ProcessThreadFormat(
        if (includeTidCheckbox.isSelected) ProcessThreadFormat.Style.BOTH else ProcessThreadFormat.Style.PID,
        showPidCheckbox.isSelected)
      tagFormat = TagFormat(tagWidthSpinner.number, !showRepeatedTagsCheckbox.isSelected, showTagsCheckbox.isSelected)
      appNameFormat = AppNameFormat(packageWidthSpinner.number, !showRepeatedPackagesCheckbox.isSelected, showPackagesCheckbox.isSelected)
    }
  }

  protected fun applyToComponents(currentOptions: FormattingOptions) {
    showTimestampCheckbox.isSelected = currentOptions.timestampFormat.enabled
    timestampStyleComboBox.selectedItem = currentOptions.timestampFormat.style
    showPidCheckbox.isSelected = currentOptions.processThreadFormat.enabled
    includeTidCheckbox.isSelected = currentOptions.processThreadFormat.style == ProcessThreadFormat.Style.BOTH
    showTagsCheckbox.isSelected = currentOptions.tagFormat.enabled
    tagWidthSpinner.number = currentOptions.tagFormat.maxLength
    showRepeatedTagsCheckbox.isSelected = !currentOptions.tagFormat.hideDuplicates
    showPackagesCheckbox.isSelected = currentOptions.appNameFormat.enabled
    packageWidthSpinner.number = currentOptions.appNameFormat.maxLength
    showRepeatedPackagesCheckbox.isSelected = !currentOptions.appNameFormat.hideDuplicates
  }

  protected fun createPanel(formattingOptions: FormattingOptions): DialogPanel {
    val panel = panel {
      createComponents(this, formattingOptions)
    }
    val refresh: () -> Unit = { onComponentsChanged(isUserAction = true) }
    val itemEventListener: (e: ItemEvent) -> Unit = { refresh() }
    val changeEventListener: (e: ChangeEvent) -> Unit = { refresh() }

    components.forEach {
      when (it) {
        is JBCheckBox -> it.addItemListener(itemEventListener)
        is JBIntSpinner -> it.addChangeListener(changeEventListener)
        is ComboBox<*> -> it.addItemListener(itemEventListener)
      }
    }

    return panel
  }

  protected open fun createComponents(layoutBuilder: LayoutBuilder, formattingOptions: FormattingOptions) {
    layoutBuilder.row {
      component(JPanel())
        .constraints(growX, pushX)
        .applyToComponent {
          layout = GridLayout(0, 2).apply {
            hgap = GRID_COLUMN_GAP
          }
          add(panel { timestampGroup(formattingOptions.timestampFormat) })
          add(panel { processIdsGroup(formattingOptions.processThreadFormat) })
          add(panel { tagsGroup(formattingOptions.tagFormat) })
          add(panel { packageNamesGroup(formattingOptions.appNameFormat) })
        }
      layoutBuilder.footerGroup()
    }
  }

  private fun LayoutBuilder.timestampGroup(format: TimestampFormat) {
    titledRow(message("logcat.header.options.timestamp.title")) {
      subRowIndent = 0
      row {
        val showTimestamp = checkBox(message("logcat.header.options.timestamp.show"), format.enabled, ::showTimestampCheckbox)
          .constraints(growX, pushX)
        row {
          cell {
            label(message("logcat.header.options.timestamp.format"))
            val model = DefaultComboBoxModel(TimestampFormat.Style.values())
            val renderer = SimpleListCellRenderer.create<TimestampFormat.Style?>("") { it?.displayName }
            comboBox(model, renderer, format.style, ::timestampStyleComboBox)
          }
        }.enableIf(showTimestamp.selected)
      }
    }
  }

  private fun LayoutBuilder.processIdsGroup(format: ProcessThreadFormat) {
    titledRow(message("logcat.header.options.process.ids.title")) {
      subRowIndent = 0
      row {
        val showPid = checkBox(message("logcat.header.options.process.ids.show.pid"), format.enabled, ::showPidCheckbox)
        row {
          val includeTid = format.style == ProcessThreadFormat.Style.BOTH
          checkBox(message("logcat.header.options.process.ids.show.tid"), includeTid, ::includeTidCheckbox)
            .enableIf(showPid.selected)
        }
      }
    }
  }

  private fun LayoutBuilder.tagsGroup(format: TagFormat) {
    titledRow(message("logcat.header.options.tags.title")) {
      subRowIndent = 0
      row {
        val showTags = checkBox(message("logcat.header.options.tags.show"), format.enabled, ::showTagsCheckbox)
          .constraints(growX, pushX)
        row {
          cell {
            label(message("logcat.header.options.tags.width"))
            spinner(format.maxLength, MIN_TAG_LENGTH, MAX_TAG_LENGTH, ::tagWidthSpinner)
          }
        }.enableIf(showTags.selected)
        row {
          checkBox(message("logcat.header.options.tags.show.repeated"), !format.hideDuplicates, ::showRepeatedTagsCheckbox)
        }.enableIf(showTags.selected)
      }
    }
  }

  private fun LayoutBuilder.packageNamesGroup(format: AppNameFormat) {
    titledRow(message("logcat.header.options.packages.title")) {
      subRowIndent = 0
      row {
        val showPackageNames = checkBox(message("logcat.header.options.packages.show"), format.enabled, ::showPackagesCheckbox)
        row {
          cell {
            label(message("logcat.header.options.packages.width"))
            spinner(format.maxLength, MIN_APP_NAME_LENGTH, MAX_APP_NAME_LENGTH, ::packageWidthSpinner)
          }
        }.enableIf(showPackageNames.selected)
        row {
          checkBox(message("logcat.header.options.packages.show.repeated"), !format.hideDuplicates, ::showRepeatedPackagesCheckbox)
        }.enableIf(showPackageNames.selected)
      }
    }
  }

  private fun LayoutBuilder.footerGroup() {
    row {
      component(sampleEditor.component).applyToComponent {
        border = JBUI.Borders.customLine(UIUtil.getBoundsColor())
      }
    }
  }

  @Suppress("UnstableApiUsage")
  private fun Cell.checkBox(
    @NlsContexts.Checkbox text:
    String, value: Boolean,
    componentSetter: KMutableProperty0<JBCheckBox>): CellBuilder<JBCheckBox> {
    return checkBox(text, value).apply {
      componentSetter.set(component)
      components.add(component)
    }
  }

  private fun Cell.spinner(
    value: Int,
    minValue: Int,
    maxValue: Int,
    componentSetter: KMutableProperty0<JBIntSpinner>,
  ): CellBuilder<JBIntSpinner> {
    return spinner({ value }, {}, minValue, maxValue).apply {
      componentSetter.set(component)
      components.add(component)
    }
  }

  private inline fun <reified T : Any> Cell.comboBox(
    model: ComboBoxModel<T>,
    renderer: ListCellRenderer<T?>,
    value: T,
    componentSetter: KMutableProperty0<ComboBox<T>>,
  ): CellBuilder<ComboBox<T>> {
    return comboBox(model, { value }, {}, renderer).apply {
      componentSetter.set(this.component)
      components.add(component)
    }
  }

  protected open fun onComponentsChanged(isUserAction: Boolean) {
    applyToFormattingOptions(sampleFormattingOptions)
    val textAccumulator = TextAccumulator()
    sampleMessageFormatter.formatMessages(sampleFormattingOptions, textAccumulator, sampleMessages)
    sampleEditor.document.setReadOnly(false)
    sampleEditor.document.setText("")
    DocumentAppender(project, sampleEditor.document, MAX_SAMPLE_DOCUMENT_BUFFER_SIZE).appendToDocument(textAccumulator)
    sampleEditor.document.insertString(sampleEditor.document.textLength, " ".repeat(MAX_SAMPLE_DOCUMENT_TEXT_LENGTH))
    sampleEditor.document.setReadOnly(true)
  }

  interface ApplyAction {
    fun onApply(logcatFormatDialogBase: LogcatFormatDialogBase)
  }
}
