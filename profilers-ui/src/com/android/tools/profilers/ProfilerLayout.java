/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers;

import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/**
 * Common layout constants that are shared across profiler views.
 */
public class ProfilerLayout {

  /**
   * Common length for spacing between axis tick markers
   */
  public static final int MARKER_LENGTH = JBUI.scale(5);

  public static final int TIME_AXIS_HEIGHT = JBUI.scale(20);

  public static final float TOOLTIP_FONT_SIZE = 12.5f;

  /**
   * Common space left on top of a vertical axis to make sure label text can fit there
   */
  public static final int Y_AXIS_TOP_MARGIN = JBUI.scale(30);

  public static final Border MONITOR_LABEL_PADDING = BorderFactory.createEmptyBorder(5, 10, 5, 10);

  public static final Border MONITOR_BORDER = BorderFactory.createCompoundBorder(
    new MatteBorder(0, 0, 1, 0, ProfilerColors.MONITOR_BORDER),
    new EmptyBorder(0, 0, 0, 0));

  private ProfilerLayout() {
    // Static class designed to hold constants only
  }
}
