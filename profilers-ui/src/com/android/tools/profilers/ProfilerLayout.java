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

import com.android.tools.adtui.common.AdtUiUtils;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Common layout constants that are shared across profiler views.
 */
public class ProfilerLayout {

  /**
   * Common length for spacing between axis tick markers
   */
  public static final int MARKER_LENGTH = JBUI.scale(5);

  public static final int TIME_AXIS_HEIGHT = JBUI.scale(15);

  public static final float TOOLTIP_FONT_SIZE = 11f;

  /**
   * Common space left on top of a vertical axis to make sure label text can fit there
   */
  public static final int Y_AXIS_TOP_MARGIN = JBUI.scale(30);

  public static final Border MONITOR_LABEL_PADDING = BorderFactory.createEmptyBorder(5, 10, 5, 10);

  public static final Border MONITOR_BORDER = BorderFactory.createMatteBorder(0, 0, 1, 0, ProfilerColors.MONITOR_BORDER);

  public static final int MONITOR_LEGEND_RIGHT_PADDING = JBUI.scale(12);

  /**
   * Space on the right for all legends when inside a profiler. Chosen so it lines up with the right axis units.
   */
  public static final int PROFILER_LEGEND_RIGHT_PADDING = JBUI.scale(9);

  public static final int ROW_HEIGHT_PADDING = JBUI.scale(4);

  public static final Border TABLE_ROW_BORDER = new EmptyBorder(0, 10, 0, 0);
  public static final Border TABLE_COLUMN_HEADER_BORDER = new EmptyBorder(3, 10, 3, 0);
  public static final Border TABLE_COLUMN_RIGHT_ALIGNED_HEADER_BORDER = new EmptyBorder(3, 0, 3, 10);

  public static final Insets TABLE_COLUMN_CELL_INSETS = new Insets(3, 10, 3, 0);
  public static final Insets TABLE_COLUMN_RIGHT_ALIGNED_CELL_INSETS = new Insets(3, 0, 3, 10);

  public static final int TABLE_COLUMN_CELL_SPARKLINE_LEFT_PADDING = JBUI.scale(2);
  public static final int TABLE_COLUMN_CELL_SPARKLINE_TOP_BOTTOM_PADDING = JBUI.scale(1);

  public static final Insets LIST_ROW_INSETS = new Insets(2, 10, 0, 0);

  public static final Border TOOLTIP_BORDER = new JBEmptyBorder(8, 10, 8, 10);

  public static final int PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER = JBUI.scale(16);
  public static final int PROFILING_INSTRUCTIONS_ICON_PADDING = JBUI.scale(1);
  public static final Font PROFILING_INSTRUCTIONS_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(15f);
  public static final Font INFO_MESSAGE_HEADER_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(14f);
  public static final Font INFO_MESSAGE_DESCRIPTION_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(12f);

  public static final int FILTER_TEXT_FIELD_WIDTH = JBUI.scale(245);
  public static final int FILTER_TEXT_FIELD_TRIGGER_DELAY_MS = 250;
  public static final int FILTER_TEXT_HISTORY_SIZE = 5;

  // There is a border of 5 pixels that is not scaled.
  public static final int TOOLBAR_HEIGHT = JBUI.scale(25) + 5;
  public static final Border TOOLBAR_LABEL_BORDER = JBUI.Borders.empty(3, 8, 3, 3);
  public static final Border TOOLBAR_ICON_BORDER = JBUI.Borders.empty(4);
  public static final Font TOOLBAR_LABEL_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(11f);

  private ProfilerLayout() {
    // Static class designed to hold constants only
  }

  @NotNull
  public static FlowLayout createToolbarLayout() {
    return new FlowLayout(FlowLayout.CENTER, 0, 1);
  }
}
