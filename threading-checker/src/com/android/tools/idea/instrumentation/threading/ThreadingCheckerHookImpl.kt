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
package com.android.tools.idea.instrumentation.threading

import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerHook
import com.google.common.annotations.VisibleForTesting
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.thisLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.swing.SwingUtilities

/**
 * Connects to the threading java agent from Android Studio.
 */
class ThreadingCheckerHookImpl : ThreadingCheckerHook {
  private val logger = thisLogger()

  @VisibleForTesting
  val threadingViolations: ConcurrentMap<String, AtomicLong> = ConcurrentHashMap()

  override fun verifyOnUiThread() {
    if (SwingUtilities.isEventDispatchThread()) {
      return
    }
    recordViolation("Methods annotated with @UiThread should be called on the UI thread")
  }

  private fun recordViolation(warningMessage: String) {
    val stackTrace = Thread.currentThread().stackTrace
    // Index of an annotated method. We need to skip Thread#getStackTrace, ThreadingCheckerHookImpl#recordViolation,
    // ThreadingCheckerHookImpl#verifyOnUiThread, ThreadingCheckerTrampoline#verifyOnUiThread stack frames.
    val annotatedMethodIndex = 4
    val methodSignature =
      stackTrace[annotatedMethodIndex].className + "#" + stackTrace[annotatedMethodIndex].methodName
    val violationCount = threadingViolations.computeIfAbsent(methodSignature) { AtomicLong() }.incrementAndGet()
    val loggedStackTrace = Stream.of(*stackTrace).skip(annotatedMethodIndex.toLong()).map { it.toString() }
      .collect(Collectors.joining("\n  "))
    logger.warn(
      "$warningMessage\nViolating method: $methodSignature\nStack trace:\n$loggedStackTrace"
    )

    // Only show one notification per method signature
    if (violationCount == 1L) {
      NotificationGroupManager.getInstance()
        .getNotificationGroup("Threading Violation Notification")
        .createNotification(
          "Threading violation",
          "$warningMessage<p>Violating method: $methodSignature",
          NotificationType.ERROR)
        .notify(null)
    }
  }
}