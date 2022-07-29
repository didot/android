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
package com.android.tools.idea.logcat.filters

import com.android.tools.idea.logcat.FakePackageNamesProvider
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.filters.LogcatFilterField.APP
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.MESSAGE
import com.android.tools.idea.logcat.filters.LogcatFilterField.TAG
import com.android.tools.idea.logcat.logcatMessage
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogLevel.ASSERT
import com.android.tools.idea.logcat.message.LogLevel.ERROR
import com.android.tools.idea.logcat.message.LogLevel.INFO
import com.android.tools.idea.logcat.message.LogLevel.VERBOSE
import com.android.tools.idea.logcat.message.LogLevel.WARN
import com.android.tools.idea.logcat.message.LogcatMessage
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.TextRange.EMPTY_RANGE
import com.intellij.testFramework.UsefulTestCase.assertThrows
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

private val TIMESTAMP = Instant.ofEpochMilli(1000)
private val ZONE_ID = ZoneId.of("UTC")
private val MESSAGE1 = logcatMessage(WARN, pid = 1, tid = 1, "app1", "Tag1", TIMESTAMP, "message1")
private val MESSAGE2 = logcatMessage(WARN, pid = 2, tid = 2, "app2", "Tag2", TIMESTAMP, "message2")

/**
 * Tests for [LogcatFilter] implementations.
 */
class LogcatFilterTest {

  @Test
  fun logcatMasterFilter() {
    val filter = object : LogcatFilter(EMPTY_RANGE) {
      override fun matches(message: LogcatMessageWrapper) = message.logcatMessage == MESSAGE1
    }
    assertThat(LogcatMasterFilter(filter).filter(listOf(MESSAGE1, MESSAGE2))).containsExactly(MESSAGE1)
  }

  @Test
  fun logcatMasterFilter_systemMessages() {
    val filter = object : LogcatFilter(EMPTY_RANGE) {
      override fun matches(message: LogcatMessageWrapper) = false
    }
    val systemMessage = LogcatMessage(SYSTEM_HEADER, "message")

    assertThat(LogcatMasterFilter(filter).filter(listOf(systemMessage))).containsExactly(systemMessage)
  }

  @Test
  fun logcatMasterFilter_nullFilter() {
    val messages = listOf(MESSAGE1, MESSAGE2)

    assertThat(LogcatMasterFilter(null).filter(messages)).isEqualTo(messages)
  }

  @Test
  fun andLogcatFilter_allTrue() {
    assertThat(
      AndLogcatFilter(
        TrueFilter(),
        TrueFilter(),
        TrueFilter(),
        TrueFilter(),
        TrueFilter(),
      ).matches(MESSAGE1)).isTrue()
  }

  @Test
  fun andLogcatFilter_oneFalse() {
    assertThat(
      AndLogcatFilter(
        TrueFilter(),
        TrueFilter(),
        FalseFilter(),
        TrueFilter(),
        TrueFilter(),
      ).matches(MESSAGE1)).isFalse()
  }

  @Test
  fun orLogcatFilter_allFalse() {
    assertThat(
      OrLogcatFilter(
        FalseFilter(),
        FalseFilter(),
        FalseFilter(),
        FalseFilter(),
        FalseFilter(),
      ).matches(MESSAGE1)).isFalse()
  }

  @Test
  fun orLogcatFilter_oneTrue() {
    assertThat(
      OrLogcatFilter(
        FalseFilter(),
        FalseFilter(),
        TrueFilter(),
        FalseFilter(),
        FalseFilter(),
      ).matches(MESSAGE1)).isTrue()
  }

  @Test
  fun logcatFilterField() {
    assertThat(TAG.getValue(LogcatMessageWrapper(MESSAGE1))).isEqualTo("Tag1")
    assertThat(APP.getValue(LogcatMessageWrapper(MESSAGE1))).isEqualTo("app1")
    assertThat(MESSAGE.getValue(LogcatMessageWrapper(MESSAGE1))).isEqualTo("message1")
    assertThat(LINE.getValue(LogcatMessageWrapper(MESSAGE1, ZONE_ID)))
      .isEqualTo("1970-01-01 00:00:01.000 1-1 Tag1 app1 W: message1")
  }

  @Test
  fun stringFilter() {
    assertThat(StringFilter("tag1", TAG, EMPTY_RANGE).matches(MESSAGE1)).isTrue()
    assertThat(StringFilter("tag2", TAG, EMPTY_RANGE).matches(MESSAGE1)).isFalse()
  }

  @Test
  fun negatedStringFilter() {
    assertThat(NegatedStringFilter("tag1", TAG, EMPTY_RANGE).matches(MESSAGE1)).isFalse()
    assertThat(NegatedStringFilter("tag2", TAG, EMPTY_RANGE).matches(MESSAGE1)).isTrue()
  }

  @Test
  fun regexFilter() {
    assertThat(RegexFilter("Tag1.*message", LINE, EMPTY_RANGE).matches(MESSAGE1)).isTrue()
    assertThat(RegexFilter("Tag2.*message", LINE, EMPTY_RANGE).matches(MESSAGE1)).isFalse()
  }

  @Test
  fun regexFilter_invalidRegex() {
    assertThrows(LogcatFilterParseException::class.java) { RegexFilter("""\""", LINE, EMPTY_RANGE) }
  }

  @Test
  fun negatedRegexFilter() {
    assertThat(NegatedRegexFilter("Tag1.*message", LINE, EMPTY_RANGE).matches(MESSAGE1)).isFalse()
    assertThat(NegatedRegexFilter("Tag2.*message", LINE, EMPTY_RANGE).matches(MESSAGE1)).isTrue()
  }

  @Test
  fun negatedRegexFilter_invalidRegex() {
    assertThrows(LogcatFilterParseException::class.java) { NegatedRegexFilter("""\""", LINE, EMPTY_RANGE) }
  }

  @Test
  fun exactFilter() {
    val message = logcatMessage(tag = "MyTag1")

    assertThat(ExactStringFilter("Tag", TAG, EMPTY_RANGE).matches(message)).isFalse()
    assertThat(ExactStringFilter("Tag1", TAG, EMPTY_RANGE).matches(message)).isFalse()
    assertThat(ExactStringFilter("MyTag", TAG, EMPTY_RANGE).matches(message)).isFalse()
    assertThat(ExactStringFilter("MyTag1", TAG, EMPTY_RANGE).matches(message)).isTrue()
  }

  @Test
  fun negatedExactFilter() {
    val message = logcatMessage(tag = "MyTag1")

    assertThat(NegatedExactStringFilter("Tag", TAG, EMPTY_RANGE).matches(message)).isTrue()
    assertThat(NegatedExactStringFilter("Tag1", TAG, EMPTY_RANGE).matches(message)).isTrue()
    assertThat(NegatedExactStringFilter("MyTag", TAG, EMPTY_RANGE).matches(message)).isTrue()
    assertThat(NegatedExactStringFilter("MyTag1", TAG, EMPTY_RANGE).matches(message)).isFalse()
  }

  @Test
  fun levelFilter() {
    val levelFilter = LevelFilter(INFO, EMPTY_RANGE)
    for (logLevel in LogLevel.values()) {
      assertThat(levelFilter.matches(logcatMessage(logLevel))).named(logLevel.name).isEqualTo(logLevel.ordinal >= INFO.ordinal)
    }
  }

  @Test
  fun ageFilter() {
    val clock = Clock.fixed(Instant.EPOCH, ZONE_ID)
    val message = logcatMessage(timestamp = clock.instant())

    assertThat(AgeFilter(Duration.ofSeconds(10), Clock.offset(clock, Duration.ofSeconds(5)), EMPTY_RANGE).matches(message)).isTrue()
    assertThat(AgeFilter(Duration.ofSeconds(10), Clock.offset(clock, Duration.ofSeconds(15)), EMPTY_RANGE).matches(message)).isFalse()
  }

  @Test
  fun appFilter_matches() {
    val message1 = logcatMessage(appId = "foo")
    val message2 = logcatMessage(appId = "bar")
    val message3 = logcatMessage(appId = "foobar")

    assertThat(ProjectAppFilter(FakePackageNamesProvider("foo", "bar"), EMPTY_RANGE).filter(listOf(message1, message2, message3)))
      .containsExactly(
        message1,
        message2
      ).inOrder()
  }

  @Test
  fun appFilter_emptyMatchesNone() {
    val message1 = logcatMessage(appId = "foo")
    val message2 = logcatMessage(appId = "bar")
    val message3 = logcatMessage(appId = "error", logLevel = ERROR)

    assertThat(ProjectAppFilter(FakePackageNamesProvider(), EMPTY_RANGE).filter(listOf(message1, message2, message3))).isEmpty()
  }

  @Test
  fun appFilter_matchedMessageText() {
    val message1 = logcatMessage(logLevel = ASSERT, message = "Assert message from com.app1")
    val message2 = logcatMessage(logLevel = ERROR, message = "Error message from com.app2")
    val message3 = logcatMessage(logLevel = WARN, message = "Warning message from com.app2")
    val message4 = logcatMessage(logLevel = ERROR, message = "Error message from com.app3")

    assertThat(ProjectAppFilter(FakePackageNamesProvider("app1", "app2"), EMPTY_RANGE).filter(listOf(message1, message2, message3, message4)))
      .containsExactly(
        message1,
        message2,
      ).inOrder()
  }

  @Test
  fun stackTraceFilter() {
    val message = """
      Failed metering RPC
        io.grpc.StatusRuntimeException: UNAVAILABLE
          at io.grpc.stub.ClientCalls.toStatusRuntimeException(ClientCalls.java:262)
          at io.grpc.stub.ClientCalls.getUnchecked(ClientCalls.java:243)
    """.trimIndent()
    val message1 = logcatMessage(logLevel = ERROR, message = message)
    val message2 = logcatMessage(logLevel = VERBOSE, message = message)
    val message3 = logcatMessage(logLevel = INFO, message = "Not a stacktrace")

    assertThat(StackTraceFilter(EMPTY_RANGE).filter(listOf(message1, message2, message3)))
      .containsExactly(
        message1,
        message2,
      ).inOrder()
  }

  @Test
  fun crashFilter_jvm() {
    val message1 = logcatMessage(tag = "AndroidRuntime", logLevel = ERROR, message = "FATAL EXCEPTION")
    val message2 = logcatMessage(tag = "Foo", logLevel = ERROR, message = "FATAL EXCEPTION")
    val message3 = logcatMessage(tag = "AndroidRuntime", logLevel = ASSERT, message = "FATAL EXCEPTION")
    val message4 = logcatMessage(tag = "AndroidRuntime", logLevel = ERROR, message = "Not a FATAL EXCEPTION")

    assertThat(CrashFilter(EMPTY_RANGE).filter(listOf(message1, message2, message3, message4))).containsExactly(message1)
  }

  @Test
  fun crashFilter_native() {
    val message1 = logcatMessage(tag = "libc", logLevel = ASSERT, message = "Native crash")
    val message2 = logcatMessage(tag = "DEBUG", logLevel = ASSERT, message = "Native crash")
    val message3 = logcatMessage(tag = "libc", logLevel = ERROR, message = "Not a native crash")
    val message4 = logcatMessage(tag = "DEBUG", logLevel = ERROR, message = "Not a native crash")

    assertThat(CrashFilter(EMPTY_RANGE).filter(listOf(message1, message2, message3, message4)))
      .containsExactly(
        message1,
        message2,
      ).inOrder()
  }

  @Test
  fun nameFilter_matches() {
    assertThat(NameFilter("name", EMPTY_RANGE).matches(logcatMessage(message = "whatever"))).isTrue()
  }

  @Test
  fun getFilterName_nameFilter() {
    assertThat(NameFilter("name", EMPTY_RANGE).getFilterName()).isEqualTo("name")
  }

  @Test
  fun getFilterName_simpleFilters() {
    assertThat(StringFilter("string", TAG, EMPTY_RANGE).getFilterName()).isNull()
    assertThat(NegatedStringFilter("string", TAG, EMPTY_RANGE).getFilterName()).isNull()
    assertThat(ExactStringFilter("string", TAG, EMPTY_RANGE).getFilterName()).isNull()
    assertThat(NegatedExactStringFilter("string", TAG, EMPTY_RANGE).getFilterName()).isNull()
    assertThat(RegexFilter("string", TAG, EMPTY_RANGE).getFilterName()).isNull()
    assertThat(NegatedRegexFilter("string", TAG, EMPTY_RANGE).getFilterName()).isNull()
    assertThat(LevelFilter(INFO, EMPTY_RANGE).getFilterName()).isNull()
    assertThat(AgeFilter(Duration.ofSeconds(60), Clock.systemDefaultZone(), EMPTY_RANGE).getFilterName()).isNull()
    assertThat(CrashFilter(EMPTY_RANGE).getFilterName()).isNull()
    assertThat(StackTraceFilter(EMPTY_RANGE).getFilterName()).isNull()
  }

  @Test
  fun getFilterName_compoundFilter() {
    assertThat(AndLogcatFilter(StringFilter("string", TAG, EMPTY_RANGE), LevelFilter(INFO, EMPTY_RANGE)).getFilterName()).isNull()
    assertThat(OrLogcatFilter(StringFilter("string", TAG, EMPTY_RANGE), LevelFilter(INFO, EMPTY_RANGE)).getFilterName()).isNull()
    assertThat(AndLogcatFilter(
      NameFilter("name1", EMPTY_RANGE),
      StringFilter("string", TAG, EMPTY_RANGE),
      LevelFilter(INFO, EMPTY_RANGE),
      NameFilter("name2", EMPTY_RANGE),
    ).getFilterName()).isEqualTo("name2")
    assertThat(OrLogcatFilter(
      NameFilter("name1", EMPTY_RANGE),
      StringFilter("string", TAG, EMPTY_RANGE),
      LevelFilter(INFO, EMPTY_RANGE),
      NameFilter("name2", EMPTY_RANGE),
    ).getFilterName()).isEqualTo("name2")
  }
}

private class TrueFilter : LogcatFilter(EMPTY_RANGE) {
  override fun matches(message: LogcatMessageWrapper) = true
}

private class FalseFilter : LogcatFilter(EMPTY_RANGE) {
  override fun matches(message: LogcatMessageWrapper) = false
}

private fun LogcatFilter.filter(messages: List<LogcatMessage>) = LogcatMasterFilter(this).filter(messages)

private fun LogcatFilter.matches(logcatMessage: LogcatMessage) = matches(LogcatMessageWrapper(logcatMessage))