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
package com.android.tools.idea.logcat.service

import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatHeaderParser
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.folding.StackTraceExpander
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

private const val SYSTEM_LINE_PREFIX = "--------- beginning of "

/**
 * Last message in batch will be posted after a delay, to allow for more log lines if another batch is pending.
 */
private const val DELAY_MILLIS = 100L

/**
 * Receives batches of lines from an `adb logcat -v long` process and assembles them into complete [LogCatMessage]'s.
 *
 * A logcat entry starts with a header:
 *
 * `[          1650901603.644  1505: 1539 W/BroadcastQueue ]`
 *
 * And is followed by one or more lines. The entry is terminated by an empty line but there is nothing preventing users from including an
 * empty line in a proper message so there is no reliable way to determine the end of a Logcat entry. The only reliable trigger that an
 * entry has ended is when a new header is parsed.
 *
 * This means that the last entry cannot be assembled until the next entry is emitted, which could cause an unwanted delay.
 *
 * To avoid this delay, after finishing precessing a batch of lines, we schedule a delayed task to flush the last message if nothing arrives
 * in time.
 *
 * This class is derived from [com.android.tools.idea.logcat.AndroidLogcatReceiver]
 */
internal class LogcatMessageAssembler(
  disposableParent: Disposable,
  private val serialNumber: String,
  private val channel: SendChannel<List<LogCatMessage>>,
  private val processNameMonitor: ProcessNameMonitor,
  coroutineContext: CoroutineContext,
) : Disposable {
  private val coroutineScope = AndroidCoroutineScope(this, coroutineContext)

  /**
   * Keep a lambda se we don't allocate a new one for each parsed line
   */
  private val applicationIdGetter = this::getApplicationId

  private val previousState = AtomicReference<PartialMessage?>()

  private val headerParser = LogCatHeaderParser()

  init {
    Disposer.register(disposableParent, this)
  }

  /**
   * Parse a batch of new lines.
   *
   * Lines are expected to arrive in the following structure:
   * ```
   *   [ header 1 ]
   *   Line 1
   *   Line 2
   *   ...
   *
   *   [ header 2]
   *   Line 1
   *   Line 2
   *   ...
   * ```
   *
   * There seems to be no way to deterministically detect the end of a log entry because the EOM is indicated by an empty line but an empty
   * line can be also be a valid part of a log entry. A valid header is a good indication that the previous entry has ended, but we can't
   * just hold on to an entry until we get a new header because there could be a long pause between entries.
   *
   * We address this issue by posting a delayed job that will flush the last entry in a batch. If we get another batch before the delayed
   * job executes, we cancel it.
   */
  suspend fun processNewLines(newLines: List<String>) {
    // New batch has arrived so effectively cancel the pending job by resetting previousState
    val state = previousState.getAndSet(null)

    // Parse new lines and send log messages
    val batch: Batch = parseNewLines(state, newLines)
    if (batch.messages.isNotEmpty()) {
      channel.send(batch.messages)
    }

    // Save the last header/lines to handle in the next batch or in the delayed job
    previousState.set(PartialMessage(batch.lastHeader, batch.lastLines))

    // If there is a valid last message in the batch, queue it for sending in case there is no imminent next batch coming
    if (batch.lastHeader != null && batch.lastLines.isNotEmpty()) {
      coroutineScope.launch {
        delay(DELAY_MILLIS)
        val message = getAndResetPendingMessage()
        if (message != null) {
          channel.send(listOf(message))
        }
      }
    }
  }

  fun getAndResetPendingMessage(): LogCatMessage? {
    val pendingState = previousState.getAndSet(null)
    return when {
      pendingState?.header != null -> LogCatMessage(pendingState.header, pendingState.lines.toMessage())
      else -> null
    }
  }

  override fun dispose() {}

  private fun parseNewLines(
    state: PartialMessage?, newLines: List<String>): Batch {

    var lastHeader = state?.header
    val lastLines = state?.lines?.toMutableList() ?: mutableListOf()
    val batchMessages = mutableListOf<LogCatMessage>()

    for (line in newLines.map { it.fixLine() }) {
      if (line.isSystemLine()) {
        batchMessages.add(LogCatMessage(SYSTEM_HEADER, line))
        continue
      }
      val header = headerParser.parseHeader(line, applicationIdGetter)
      if (header != null) {
        // It's a header, flush active lines.
        if (lastHeader != null && lastLines.isNotEmpty()) {
          batchMessages.add(LogCatMessage(lastHeader, lastLines.toMessage()))
        }
        // previous lines without a previous header are discarded
        lastLines.clear()
        lastHeader = header
      }
      else {
        lastLines.add(line)
      }
    }
    return Batch(batchMessages, lastHeader, lastLines)
  }

  private fun getApplicationId(pid: Int): String {
    val names = processNameMonitor.getProcessNames(serialNumber, pid)
    return when {
      names == null -> "pid-$pid"
      names.applicationId.isEmpty() -> names.processName
      else -> names.applicationId
    }
  }

  /**
   * A batch consists of the first n-1 log entries in a batch. The last entry can be incomplete and is stored as a header and a list of
   * lines.
   */
  private class Batch(val messages: List<LogCatMessage>, val lastHeader: LogCatHeader?, val lastLines: List<String>)

  /**
   * A header and lines of a possibly unfinished message.
   */
  private class PartialMessage(val header: LogCatHeader?, val lines: List<String>)
}

private fun String.isSystemLine(): Boolean {
  return startsWith(SYSTEM_LINE_PREFIX)

}

/**
 * Really, the user's log should never put any system characters in it ever - that will cause
 * it to get filtered by our strict regex patterns (see AndroidLogcatFormatter). The reason
 * this might happen in practice is due to a bug where either adb or logcat (not sure which)
 * is too aggressive about converting \n's to \r\n's, including those that are quoted. This
 * means that a user's log, if it uses \r\n itself, is converted to \r\r\n. Then, when
 * MultiLineReceiver, which expects valid input, strips out \r\n, it leaves behind an extra \r.
 *
 * Unfortunately this isn't a case where we can fix the root cause because adb and logcat are
 * both external to Android Studio. In fact, the latest adb/logcat versions have already fixed
 * this issue! But we still need to run properly with older versions. Also, putting this fix in
 * MultiLineReceiver isn't right either because it is used for more than just receiving logcat.
 */
private fun String.fixLine(): String {
  return replace("\r", "")
}

private fun List<String>.toMessage(): String = StackTraceExpander.process(this).joinToString("\n").trimEnd('\n')
