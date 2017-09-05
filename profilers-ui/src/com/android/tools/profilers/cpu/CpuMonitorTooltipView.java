/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTooltipView;
import com.android.tools.profilers.StudioMonitorStageView;

import java.awt.*;

public class CpuMonitorTooltipView extends ProfilerTooltipView {
  private final CpuMonitor myMonitor;

  public CpuMonitorTooltipView(StudioMonitorStageView parent, CpuMonitor monitor) {
    super(monitor.getTimeline(), monitor.getName());
    myMonitor = monitor;
  }

  @Override
  public Component createTooltip() {
    CpuMonitor.Legends legends = myMonitor.getTooltipLegends();

    LegendComponent legend =
      new LegendComponent.Builder(legends).setVerticalPadding(0).setOrientation(LegendComponent.Orientation.VERTICAL).build();
    legend.configure(legends.getCpuLegend(), new LegendConfig(LegendConfig.IconType.BOX, ProfilerColors.CPU_USAGE));

    return legend;
  }
}
