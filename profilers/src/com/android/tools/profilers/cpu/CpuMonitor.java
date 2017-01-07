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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class CpuMonitor extends ProfilerMonitor {

  private static final SingleUnitAxisFormatter CPU_USAGE_FORMATTER = new SingleUnitAxisFormatter(1, 2, 10, "%");

  @NotNull private final CpuUsage myThisProcessCpuUsage;
  @NotNull private final AxisComponentModel myCpuUsageAxis;
  @NotNull private final CpuMonitorLegends myLegends;

  public CpuMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);

    myThisProcessCpuUsage = new CpuUsage(profilers);

    myCpuUsageAxis = new AxisComponentModel(myThisProcessCpuUsage.getCpuRange(), CPU_USAGE_FORMATTER);
    myCpuUsageAxis.setClampToMajorTicks(true);

    myLegends = new CpuMonitorLegends(myThisProcessCpuUsage, profilers.getTimeline().getDataRange());
  }

  public String getName() {
    return "CPU";
  }

  @Override
  public void exit() {
    myProfilers.getUpdater().unregister(myThisProcessCpuUsage);
    myProfilers.getUpdater().unregister(myCpuUsageAxis);
    myProfilers.getUpdater().unregister(myLegends);
  }

  @Override
  public void enter() {
    myProfilers.getUpdater().register(myThisProcessCpuUsage);
    myProfilers.getUpdater().register(myCpuUsageAxis);
    myProfilers.getUpdater().register(myLegends);
  }

  public void expand() {
    myProfilers.setStage(new CpuProfilerStage(myProfilers));
  }

  @NotNull
  public AxisComponentModel getCpuUsageAxis() {
    return myCpuUsageAxis;
  }

  @NotNull
  public CpuMonitorLegends getLegends() {
    return myLegends;
  }

  @NotNull
  public CpuUsage getThisProcessCpuUsage() {
    return myThisProcessCpuUsage;
  }

  public static class CpuMonitorLegends extends LegendComponentModel {

    @NotNull
    private final SeriesLegend myCpuLegend;

    public CpuMonitorLegends(@NotNull CpuUsage usage, @NotNull Range range) {
      myCpuLegend = new SeriesLegend(usage.getCpuSeries(), CPU_USAGE_FORMATTER, range);
      add(myCpuLegend);
    }

    @NotNull
    public SeriesLegend getCpuLegend() {
      return myCpuLegend;
    }
  }
}
