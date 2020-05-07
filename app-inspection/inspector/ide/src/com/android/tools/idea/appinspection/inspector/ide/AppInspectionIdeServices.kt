/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspector.ide

import com.android.annotations.concurrency.UiThread

/**
 * A set of utility methods used for communicating requests to the IDE.
 */
interface AppInspectionIdeServices {
  enum class Severity {
    INFORMATION,
    ERROR,
  }

  /**
   * Shows the App Inspection tool window.
   * @param callback A callback executed right after the window shows up. The call is asynchronous since it may require animation.
   */
  @UiThread
  fun showToolWindow(@UiThread callback: () -> Unit = { })

  /**
   * Shows a notification which will be reported by the tool window with UX that is consistent across all inspectors.
   *
   * @param content Content text for this notification, which can contain html. If an `<a href=.../>` tag is present
   *   and the user clicks on it, the [hyperlinkClicked] parameter will be triggered.
   * @param title A title to show for this notification, which can be empty
   * @param hyperlinkClicked If the notification contains a hyperlink, this callback will be fired if the user clicks it.
   */
  @UiThread
  fun showNotification(content: String,
                       title: String = "",
                       severity: Severity = Severity.INFORMATION,
                       @UiThread hyperlinkClicked: () -> Unit = {})
}