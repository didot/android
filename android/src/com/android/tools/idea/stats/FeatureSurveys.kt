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
package com.android.tools.idea.stats

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.idea.serverflags.FEATURE_SURVEY_CONFIG
import com.android.tools.idea.serverflags.FEATURE_SURVEY_ROOT
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.FeatureSurveyConfig
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.ide.IdeEventQueue

object FeatureSurveys {
  private var isFeatureSurveyPending = false
  private val lock = Any()

  fun processEvent(event: AndroidStudioEvent.Builder) {
    val name = "${FEATURE_SURVEY_ROOT}${event.kind.valueDescriptor.name}"
    val survey = ServerFlagService.instance
                   .getProtoOrNull(name, DEFAULT_SATISFACTION_SURVEY)
                 ?: return

    if (!shouldInvokeFeatureSurvey(name)) {
      return
    }

    val eventQueue = IdeEventQueue.getInstance()
    lateinit var runner: Runnable

    runner = Runnable {
      // Ensure we're invoked only once.
      eventQueue.removeIdleListener(runner)

      val dialog = createDialog(survey)
      dialog.show()

      var generalInterval = config.generalIntervalCancelled
      var specificInterval = config.specificIntervalCancelled

      if (dialog.isOK) {
        generalInterval = config.generalIntervalCompleted
        specificInterval = config.specificIntervalCompleted
      }

      featureSurveyInvoked(name, generalInterval, specificInterval)
      AnalyticsSettings.saveSettings()
    }

    eventQueue.addIdleListener(runner, config.idleIntervalMs)
  }

  // Determines whether the specified feature survey should be invoked.
  // If so, sets the isFeatureSurveyPending flag to true.
  @VisibleForTesting
  fun shouldInvokeFeatureSurvey(name: String): Boolean {
    if (!AnalyticsSettings.optedIn || isFeatureSurveyPending) {
      return false
    }

    val now = AnalyticsSettings.dateProvider.now()

    // Is it too early to invoke any feature survey?
    AnalyticsSettings.nextFeatureSurveyDate?.let {
      if (now.before(it)) {
        return false
      }
    }

    // Is it too early to invoke this specific feature survey?
    AnalyticsSettings.nextFeatureSurveyDateMap?.let { map ->
      map[name]?.let {
        if (now.before(it)) {
          return false
        }
      }
    }

    synchronized(lock) {
      if (isFeatureSurveyPending) {
        return false
      }

      isFeatureSurveyPending = true
    }

    return true
  }

  // Indicates that a feature survey has been invoked, and the countdowns until
  // the next feature survey should be updated
  fun featureSurveyInvoked(name: String, generalInterval: Int, specificInterval: Int) {
    val now = AnalyticsSettings.dateProvider.now()

    AnalyticsSettings.nextFeatureSurveyDate = AndroidStudioUsageTracker.daysFromNow(now, generalInterval)

    val map = AnalyticsSettings.nextFeatureSurveyDateMap ?: mutableMapOf()
    map[name] = AndroidStudioUsageTracker.daysFromNow(now, specificInterval)
    AnalyticsSettings.nextFeatureSurveyDateMap = map

    synchronized(lock) {
      isFeatureSurveyPending = false
    }
  }

  private val config: FeatureSurveyConfig by lazy {
    val config = ServerFlagService.instance.getProtoOrNull(FEATURE_SURVEY_CONFIG, DEFAULT_FEATURE_SURVEY_CONFIG)

    when {
      config == null ||
      !config.hasGeneralIntervalCompleted() ||
      !config.hasGeneralIntervalCancelled() ||
      !config.hasSpecificIntervalCompleted() ||
      !config.hasSpecificIntervalCancelled() ||
      !config.hasIdleIntervalMs() -> DEFAULT_FEATURE_SURVEY_CONFIG
      else -> config
    }
  }
}