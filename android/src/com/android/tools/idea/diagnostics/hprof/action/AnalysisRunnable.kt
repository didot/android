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
package com.android.tools.idea.diagnostics.hprof.action

import com.android.tools.idea.diagnostics.crash.StudioCrashReporter
import com.android.tools.idea.diagnostics.hprof.analysis.HProfAnalysis
import com.android.tools.idea.diagnostics.hprof.util.HeapDumpAnalysisNotificationGroup
import com.android.tools.idea.diagnostics.report.HeapReport
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.util.AndroidBundle
import java.awt.BorderLayout
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.event.HyperlinkEvent

class AnalysisRunnable(private val hprofPath: Path,
                       private val deleteAfterAnalysis: Boolean) : Runnable {

  companion object {
    private val LOG = Logger.getInstance(AnalysisRunnable::class.java)
  }

  override fun run() {
    AnalysisTask().queue()
  }

  inner class AnalysisTask : Task.Backgroundable(null, AndroidBundle.message("heap.dump.analysis.task.title"), false) {

    override fun onThrowable(error: Throwable) {
      LOG.error(error)
      val notification = HeapDumpAnalysisNotificationGroup.GROUP.createNotification(AndroidBundle.message("heap.dump.analysis.exception"),
                                                                                    NotificationType.INFORMATION)
      notification.notify(null)
      if (deleteAfterAnalysis) {
        deleteHprofFileAsync()
      }
    }

    override fun onFinished() {
      val nextCheckMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
      PropertiesComponent.getInstance().setValue(HeapDumpSnapshotRunnable.NEXT_CHECK_TIMESTAMP_KEY, nextCheckMs.toString())
    }

    private fun deleteHprofFileAsync() {
      CompletableFuture.runAsync { Files.deleteIfExists(hprofPath) }
    }

    override fun run(indicator: ProgressIndicator) {
      indicator.isIndeterminate = false
      indicator.text = "Analyze Heap"
      indicator.fraction = 0.0

      val openOptions: Set<OpenOption>
      if (deleteAfterAnalysis) {
        openOptions = setOf(StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE)
      }
      else {
        openOptions = setOf(StandardOpenOption.READ)
      }
      val reportString = FileChannel.open(hprofPath, openOptions).use { channel ->
        HProfAnalysis(channel, SystemTempFilenameSupplier()).analyze(indicator)
      }
      if (deleteAfterAnalysis) {
        deleteHprofFileAsync()
      }

      val notification = HeapDumpAnalysisNotificationGroup.GROUP.createNotification(
        AndroidBundle.message("heap.dump.analysis.notification.title"),
        null,
        AndroidBundle.message("heap.dump.analysis.notification.ready.content"),
        NotificationType.INFORMATION)
      notification.isImportant = true
      notification.addAction(ReviewReportAction(reportString))

      notification.notify(null)
    }
  }

  class ReviewReportAction(private val reportString: String) :
    NotificationAction(AndroidBundle.message("heap.dump.analysis.notification.action.title"))
  {
    private var reportShown = false

    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      if (reportShown) return

      reportShown = true
      UIUtil.invokeLaterIfNeeded {
        notification.expire()

        val reportDialog = ShowReportDialog(reportString)
        val canSend = reportDialog.showAndGet()
        if (canSend) {
          // User can modify the report text and add/remove text. Get the updated contents.
          uploadReport(reportDialog.textArea.text)
        }
      }
    }

    private fun uploadReport(report: String) {
      // No need to check for AnalyticsSettings.hasOptedIn() as user agreed to the privacy policy by
      // clicking "Send" in ShowReportDialog.
      StudioCrashReporter.getInstance().submit(HeapReport(report).asCrashReport(), true)
        .whenCompleteAsync { _, throwable: Throwable? ->
          if (throwable == null) {
            HeapDumpAnalysisNotificationGroup.GROUP.createNotification(
              AndroidBundle.message("heap.dump.analysis.notification.title"),
              AndroidBundle.message("heap.dump.analysis.notification.submitted.content"),
              NotificationType.INFORMATION,
              null
            ).setImportant(false).notify(null)
          }
          else {
            LOG.error(throwable)
            HeapDumpAnalysisNotificationGroup.GROUP.createNotification(
              AndroidBundle.message("heap.dump.analysis.notification.title"),
              AndroidBundle.message("heap.dump.analysis.notification.submit.error.content"),
              NotificationType.ERROR,
              null
            ).setImportant(false).notify(null)
          }
        }
    }
  }
}

class ShowReportDialog(report: String) : DialogWrapper(false) {
  val textArea: JTextArea = JTextArea(30, 130)

  init {
    textArea.text = report
    textArea.caretPosition = 0
    init()
    title = AndroidBundle.message("heap.dump.analysis.report.dialog.title")
    isModal = true
  }

  override fun createCenterPanel(): JComponent? {
    val pane = JPanel(BorderLayout(0,5))

    pane.add(JLabel(AndroidBundle.message("heap.dump.analysis.report.dialog.header")), BorderLayout.PAGE_START)
    pane.add(JBScrollPane(textArea), BorderLayout.CENTER)
    with (SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK)) {
      isOpaque = false
      isFocusable = false
      addHyperlinkListener {
        if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          it.url?.let(BrowserUtil::browse)
        }
      }
      text = AndroidBundle.message("heap.dump.analysis.report.dialog.footer")
      pane.add(this, BorderLayout.PAGE_END)
    }

    return pane
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, cancelAction)
  }

  override fun createDefaultActions() {
    super.createDefaultActions()
    okAction.putValue(Action.NAME, AndroidBundle.message("heap.dump.analysis.report.dialog.action.send"))
    cancelAction.putValue(Action.NAME, AndroidBundle.message("heap.dump.analysis.report.dialog.action.dont.send"))
  }
}

class SystemTempFilenameSupplier : HProfAnalysis.TempFilenameSupplier {
  override fun getTempFilePath(type: String): Path {
    return FileUtil.createTempFile("studio-analysis-", "-$type.tmp").toPath()
  }
}
