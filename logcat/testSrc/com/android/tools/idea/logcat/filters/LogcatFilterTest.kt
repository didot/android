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

import com.android.ddmlib.Log.LogLevel
import com.android.ddmlib.Log.LogLevel.ASSERT
import com.android.ddmlib.Log.LogLevel.ERROR
import com.android.ddmlib.Log.LogLevel.INFO
import com.android.ddmlib.Log.LogLevel.WARN
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.logcat.FakePackageNamesProvider
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.filters.LogcatFilterField.APP
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.MESSAGE
import com.android.tools.idea.logcat.filters.LogcatFilterField.TAG
import com.android.tools.idea.logcat.logCatMessage
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.UsefulTestCase.assertThrows
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

private val TIMESTAMP = Instant.ofEpochMilli(1000)
private val ZONE_ID = ZoneId.of("UTC")
private val MESSAGE1 = logCatMessage(WARN, pid = 1, tid = 1, "app1", "Tag1", TIMESTAMP, "message1")
private val MESSAGE2 = logCatMessage(WARN, pid = 2, tid = 2, "app2", "Tag2", TIMESTAMP, "message2")

/**
 * Tests for [LogcatFilter] implementations.
 */
class LogcatFilterTest {

  @Test
  fun logcatMasterFilter() {
    val filter = object : LogcatFilter {
      override fun matches(message: LogcatMessageWrapper) = message.logCatMessage == MESSAGE1
    }
    assertThat(LogcatMasterFilter(filter).filter(listOf(MESSAGE1, MESSAGE2))).containsExactly(MESSAGE1)
  }

  @Test
  fun logcatMasterFilter_systemMessages() {
    val filter = object : LogcatFilter {
      override fun matches(message: LogcatMessageWrapper) = false
    }
    val systemMessage = LogCatMessage(SYSTEM_HEADER, "message")

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
    assertThat(StringFilter("tag1", TAG).matches(MESSAGE1)).isTrue()
    assertThat(StringFilter("tag2", TAG).matches(MESSAGE1)).isFalse()
  }

  @Test
  fun negatedStringFilter() {
    assertThat(NegatedStringFilter("tag1", TAG).matches(MESSAGE1)).isFalse()
    assertThat(NegatedStringFilter("tag2", TAG).matches(MESSAGE1)).isTrue()
  }

  @Test
  fun regexFilter() {
    assertThat(RegexFilter("tag1.*message", LINE).matches(MESSAGE1)).isTrue()
    assertThat(RegexFilter("tag2.*message", LINE).matches(MESSAGE1)).isFalse()
  }

  @Test
  fun regexFilter_invalidRegex() {
    assertThrows(LogcatFilterParseException::class.java) { RegexFilter("""\""", LINE) }
  }

  @Test
  fun negatedRegexFilter() {
    assertThat(NegatedRegexFilter("tag1.*message", LINE).matches(MESSAGE1)).isFalse()
    assertThat(NegatedRegexFilter("tag2.*message", LINE).matches(MESSAGE1)).isTrue()
  }

  @Test
  fun negatedRegexFilter_invalidRegex() {
    assertThrows(LogcatFilterParseException::class.java) { NegatedRegexFilter("""\""", LINE) }
  }

  @Test
  fun levelFilter() {
    val levelFilter = LevelFilter(INFO)
    for (logLevel in LogLevel.values()) {
      assertThat(levelFilter.matches(logCatMessage(logLevel))).named(logLevel.name).isEqualTo(logLevel.ordinal >= INFO.ordinal)
    }
  }

  @Test
  fun ageFilter() {
    val clock = Clock.fixed(Instant.EPOCH, ZONE_ID)
    val message = logCatMessage(timestamp = clock.instant())

    assertThat(AgeFilter(Duration.ofSeconds(10), Clock.offset(clock, Duration.ofSeconds(5))).matches(message)).isTrue()
    assertThat(AgeFilter(Duration.ofSeconds(10), Clock.offset(clock, Duration.ofSeconds(15))).matches(message)).isFalse()
  }

  @Test
  fun appFilter_matches() {
    val message1 = logCatMessage(appName = "foo")
    val message2 = logCatMessage(appName = "bar")
    val message3 = logCatMessage(appName = "foobar")

    assertThat(ProjectAppFilter(FakePackageNamesProvider("foo", "bar")).filter(listOf(message1, message2, message3)))
      .containsExactly(
        message1,
        message2
      ).inOrder()
  }

  @Test
  fun appFilter_emptyMatchesAll() {
    val message1 = logCatMessage(appName = "foo")
    val message2 = logCatMessage(appName = "bar")
    val message3 = logCatMessage(appName = "foobar")

    assertThat(ProjectAppFilter(FakePackageNamesProvider()).filter(listOf(message1, message2, message3)))
      .containsExactly(
        message1,
        message2,
        message3
      ).inOrder()
  }

  @Test
  fun appFilter_matchedMessageText() {
    val message1 = logCatMessage(logLevel = ASSERT, message = "Assert message from com.app1")
    val message2 = logCatMessage(logLevel = ERROR, message = "Error message from com.app2")
    val message3 = logCatMessage(logLevel = WARN, message = "Warning message from com.app2")
    val message4 = logCatMessage(logLevel = ERROR, message = "Error message from com.app3")

    assertThat(ProjectAppFilter(FakePackageNamesProvider("app1", "app2")).filter(listOf(message1, message2, message3, message4)))
      .containsExactly(
        message1,
        message2,
      ).inOrder()
  }
}

private class TrueFilter : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) = true
}

private class FalseFilter : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) = false
}

private fun LogcatFilter.filter(messages: List<LogCatMessage>) = LogcatMasterFilter(this).filter(messages)

private fun LogcatFilter.matches(logCatMessage: LogCatMessage) = matches(LogcatMessageWrapper(logCatMessage))