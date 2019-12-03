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
package com.android.tools.idea.uibuilder.visual.analytics

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.visual.ConfigurationSet
import com.google.wireless.android.sdk.stats.MultiViewEvent.MultiViewEventType

fun trackOpenConfigSet(surface: DesignSurface, configSet: ConfigurationSet) {
  when (configSet) {
    ConfigurationSet.PIXEL_DEVICES -> track(surface, MultiViewEventType.OPEN_PIXEL_DEVICES)
    ConfigurationSet.PROJECT_LOCALES -> track(surface, MultiViewEventType.OPEN_PROJECT_LOCALES)
    ConfigurationSet.CUSTOM -> track(surface, MultiViewEventType.OPEN_CUSTOM_CONFIGURATION_SETS)
    ConfigurationSet.COLOR_BLIND_MODE -> track(surface, MultiViewEventType.OPEN_COLOR_BLIND_MODE)
    ConfigurationSet.LARGE_FONT -> track(surface, MultiViewEventType.OPEN_LARGE_FONT)
  }
}

private fun track(surface: DesignSurface, eventType: MultiViewEventType) {
  InternalMultiViewMetricTrackerFactory.getInstance(surface).track(eventType)
}
