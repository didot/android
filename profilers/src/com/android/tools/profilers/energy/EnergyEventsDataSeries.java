/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.energy;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profilers.ProfilerClient;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * A data series of all energy events in a time range.
 */
public final class EnergyEventsDataSeries implements DataSeries<Common.Event> {

  @NotNull private final ProfilerClient myClient;
  @NotNull private final Common.Session mySession;

  public EnergyEventsDataSeries(@NotNull ProfilerClient client, @NotNull Common.Session session) {
    myClient = client;
    mySession = session;
  }

  @Override
  public List<SeriesData<Common.Event>> getDataForXRange(Range xRange) {
    EnergyProfiler.EnergyRequest.Builder builder = EnergyProfiler.EnergyRequest.newBuilder().setSession(mySession);
    builder.setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long) xRange.getMin()));
    builder.setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long) xRange.getMax()));
    EnergyProfiler.EnergyEventsResponse response = myClient.getEnergyClient().getEvents(builder.build());

    return response.getEventsList()
      .stream().map(evt -> new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(evt.getTimestamp()), evt)).collect(Collectors.toList());
  }
}
