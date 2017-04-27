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

import com.android.tools.adtui.common.EnumColors;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;

import java.awt.*;

public class ProfilerColors {

  // Collections of constant, do not instantiate.
  private ProfilerColors() {
  }

  public static final Color CPU_USAGE = new JBColor(0x71D2B5, 0x386550);

  //TODO: Remove *_LEGEND colors as they should be the same as the line colors.
  public static final Color CPU_USAGE_LEGEND = CPU_USAGE;

  public static final Color CPU_OTHER_USAGE = new JBColor(0xD8EAF0, 0x363E46);

  public static final Color CPU_OTHER_LEGEND = CPU_OTHER_USAGE;

  public static final Color THREADS_COUNT = new JBColor(0x6AA78A, 0x5E9A7D);

  public static final Color THREADS_COUNT_LEGEND = THREADS_COUNT;

  // TODO: define final color
  public static final Color CPU_CAPTURE_EVENT = new JBColor(0x888888, 0x888888);

  /**
   * Represents pair of colors of non-selected and selected states of a thread.
   * The first color is for a non-selected thread, the second one is for a selected thread.
   */
  public static final EnumColors<CpuProfilerStage.ThreadState> THREAD_STATES = new EnumColors.Builder<CpuProfilerStage.ThreadState>(2)
    .add(CpuProfilerStage.ThreadState.RUNNING,
         CPU_USAGE,
         new JBColor(0x57D9B2, 0x387358))
    .add(CpuProfilerStage.ThreadState.RUNNING_CAPTURED,
         new JBColor(0x53B5A0, 0x44B67F),
         new JBColor(0x84DEA7, 0x84DEA7))
    .add(CpuProfilerStage.ThreadState.WAITING,
         new JBColor(0xD4E675, 0x94A244),
         new JBColor(0xD4E675, 0x94A244))
    .add(CpuProfilerStage.ThreadState.WAITING_CAPTURED,
         new JBColor(0xEFF35C, 0xDCF35C),
         new JBColor(0xEFF35C, 0xDCF35C))
    .add(CpuProfilerStage.ThreadState.SLEEPING,
         new JBColor(0xEDEFF1, 0x3B3E42),
         new JBColor(0x7BA6E9, 0x7BA6E9))
    .add(CpuProfilerStage.ThreadState.SLEEPING_CAPTURED,
         new JBColor(0xD4D7DA, 0x4B4E52),
         new JBColor(0x8FB3EA, 0x8FB3EA))
    .add(CpuProfilerStage.ThreadState.DEAD,
         Gray.TRANSPARENT,
         Gray.TRANSPARENT)
    .add(CpuProfilerStage.ThreadState.DEAD_CAPTURED,
         Gray.TRANSPARENT,
         Gray.TRANSPARENT)
    // TODO: remove UNKNOWN mapping when all states are covered.
    .add(CpuProfilerStage.ThreadState.UNKNOWN,
         new JBColor(0xC1D6F6, 0x5A6E7D),
         new JBColor(0xC1D6F6, 0x5A6E7D))
    .build();

  public static final Color TRANSPARENT_COLOR = new JBColor(new Color(0, 0, 0, 0), new Color(0, 0, 0, 0));

  public static final Color THREAD_HOVER_OVERLAY = new JBColor(new Color(0x171650C5, true), new Color(0x0CFFFFFF, true));

  public static final Color CPU_AXIS_GUIDE_COLOR = Gray._150.withAlpha(50);

  public static final Color THREAD_SELECTED_BACKGROUND = new JBColor(0x3476DC, 0x3476DC);

  public static final Color THREAD_LABEL_TEXT = new JBColor(0x545454, 0x9C9C9C);

  public static final Color SELECTED_THREAD_LABEL_TEXT = Gray.xFF;

  public static final Color THREAD_LABEL_BACKGROUND = new JBColor(new Color(0xBFFFFFFF, true), new Color(0xBF2B2D2E, true));

  public static final Color THREAD_LABEL_BORDER = new JBColor(new Color(0x0C000000, true), new Color(0x0CFFFFFF, true));

  public static final Color CPU_TREECHART_VENDOR = new JBColor(0xA2DEFF, 0x7EB1CC);

  public static final Color CPU_TREECHART_VENDOR_BORDER = new JBColor(0x62BDEB, 0x7EB1CC);

  public static final Color CPU_TREECHART_APP = new JBColor(0x9FEAAD, 0x92D09F);

  public static final Color CPU_TREECHART_APP_BORDER = new JBColor(0x80BFAE, 0x92D09F);

  public static final Color CPU_TREECHART_PLATFORM = new JBColor(0xFECC82, 0xD0AA6F);

  public static final Color CPU_TREECHART_PLATFORM_BORDER = new JBColor(0xD5963C, 0xD0AA6F);

  public static final Color DEFAULT_BACKGROUND = new JBColor(0xFFFFFF, 0x313335);

  public static final Color DEFAULT_STAGE_BACKGROUND = new JBColor(0xFFFFFF, 0x2B2D2E);

  public static final Color MONITOR_FOCUSED = new JBColor(0xF5F7F8, 0x2B2C2D);

  public static final Color MONITOR_DISABLED = new JBColor(0xF8F8F8, 0x333436);

  public static final Color MONITOR_MAX_LINE = new JBColor(0xCCCDCD, 0x494949);

  public static final Color MONITOR_BORDER = new JBColor(0xC9C9C9, 0x3F4142);

  public static final Color NETWORK_CONNECTIONS_COLOR = new JBColor(new Color(0xB4E6A082, true), new Color(0x7FAC8C7E, true));

  public static final Color NETWORK_RECEIVING_COLOR = new JBColor(0x6B98E7, 0x6895E5);

  public static final Color NETWORK_RECEIVING_SELECTED_COLOR = new JBColor(0x8ebdff, 0x8ebdff);

  public static final Color NETWORK_SENDING_COLOR = new JBColor(0xF4AF6F, 0xF4AF6F);

  public static final Color NETWORK_WAITING_COLOR = new JBColor(0xAAAAAA, 0xAAAAAA);

  public static final Color NETWORK_RADIO_WIFI = new JBColor(0xCAD3E2, 0x4E4F50);

  public static final Color NETWORK_RADIO_IDLE = new JBColor(0xB2CAF5, 0x384460);

  public static final Color NETWORK_RADIO_LOW = new JBColor(0x6A99FE, 0x567CCF);

  public static final Color NETWORK_RADIO_HIGH = new JBColor(0x396EE7, 0x3D5FAB);

  public static final Color NETWORK_TABLE_AXIS = new JBColor(Gray._103, Gray._120);

  public static final Color NETWORK_TABLE_HOVER_COLOR = new JBColor(new Color(0x171650C5, true), new Color(0x0CFFFFFF, true));

  public static final Color NETWORK_TABLE_AXIS_SELECTED = JBColor.BLACK;

  public static final Color NETWORK_TABLE_CONNECTIONS_NAME = new JBColor(new Color(255, 255, 255, (int)(0.74 * 255)), new Color(0x2b2d2e));

  public static final Color MEMORY_TOTAL = new JBColor(new Color(0x56BFEC), new Color(0x2B7DA2));

  public static final Color MEMORY_JAVA = new JBColor(new Color(0x56BFEC), new Color(0x2B7DA2));

  public static final Color MEMORY_NATIVE = new JBColor(new Color(0x56A5CB), new Color(0x226484));

  public static final Color MEMORY_CODE = new JBColor(new Color(0x80EDDC), new Color(0x4EA783));

  public static final Color MEMORY_STACK = new JBColor(new Color(0x50CBB8), new Color(0x348866));

  public static final Color MEMORY_GRAPHICS = new JBColor(new Color(0xF4DEA2), new Color(0xA8825C));

  public static final Color MEMORY_OTHERS = new JBColor(new Color(0x517C91), new Color(0x4E616B));

  public static final Color MEMORY_OBJECTS = new JBColor(new Color(0x5C7A86), new Color(0xD8DBDE));

  public static final Color MESSAGE_COLOR = new JBColor(0x787878, 0xC8C8C8);

  public static final Color MONITORS_HEADER_TEXT = new JBColor(0x545454, 0xCACACA);
}
