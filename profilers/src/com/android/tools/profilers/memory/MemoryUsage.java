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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profiler.proto.*;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.MemorySample;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class MemoryUsage extends LineChartModel {

  @NotNull private final Range myMemoryRange;
  @NotNull private final RangedContinuousSeries myTotalMemorySeries;

  public MemoryUsage(@NotNull StudioProfilers profilers) {

    myMemoryRange = new Range(0, 0);
    myTotalMemorySeries = createRangedSeries(profilers, "Memory", myMemoryRange, MemorySample::getTotalMem);

    add(myTotalMemorySeries);
  }

  protected RangedContinuousSeries createRangedSeries(StudioProfilers profilers, String name, Range range, Function<MemorySample, Long> getter) {
    MemoryServiceGrpc.MemoryServiceBlockingStub client = profilers.getClient().getMemoryClient();
    MemoryDataSeries series = new MemoryDataSeries(client, profilers.getProcessId(), profilers.getDeviceSerial(), getter);
    return new RangedContinuousSeries(name, profilers.getTimeline().getViewRange(), range, series);
  }

  @NotNull
  public Range getMemoryRange() {
    return myMemoryRange;
  }

  @NotNull
  public RangedContinuousSeries getTotalMemorySeries() {
    return myTotalMemorySeries;
  }
}
