/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.devservices;

import com.intellij.ui.JBColor;

import java.awt.*;

/**
 * Set of utilities and constants commonly used for presenting Developer Services.
 */
final class DeveloperServicesUtils {
  public static final JBColor SEPARATOR_COLOR = new JBColor(new Color(0, 0, 0, 51), new Color(255, 255, 255, 51));

  /**
   * Determine if side panel functionality is enabled.
   */
  public static final boolean isEnabled() {
    return Boolean.getBoolean("com.google.services.side.panel.enabled");
  }
}
