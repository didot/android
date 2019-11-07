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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * This class is responsible for querying CPU thread data from unified pipeline {@link Common.Event} and extract thread count data.
 */
public class CpuThreadCountDataSeries implements DataSeries<Long> {
  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myClient;
  private final long myStreamId;
  private final int myPid;

  public CpuThreadCountDataSeries(@NotNull TransportServiceGrpc.TransportServiceBlockingStub client, long streamId, int pid) {
    myClient = client;
    myStreamId = streamId;
    myPid = pid;
  }

  @Override
  public List<SeriesData<Long>> getDataForXRange(Range xRangeUs) {
    long minNs = TimeUnit.MICROSECONDS.toNanos((long)xRangeUs.getMin());

    GetEventGroupsRequest request = GetEventGroupsRequest.newBuilder()
      .setStreamId(myStreamId)
      .setPid(myPid)
      .setKind(Common.Event.Kind.CPU_THREAD)
      // TODO(b/122110659): set from_timestamp and to_timestamp when GetEventGroups works as intended.
      .build();
    GetEventGroupsResponse response = myClient.getEventGroups(request);

    TreeMap<Long, Long> timestampToCountMap = new TreeMap<>();
    for (EventGroup group : response.getGroupsList()) {
      if (group.getEventsCount() > 0) {
        Common.Event first = group.getEvents(0);
        Common.Event last = group.getEvents(group.getEventsCount() - 1);
        if (last.getTimestamp() < minNs && last.getIsEnded()) {
          continue;
        }
        Long count = timestampToCountMap.get(first.getTimestamp());
        timestampToCountMap.put(first.getTimestamp(), count == null ? 1 : count + 1);
        if (last.getIsEnded()) {
          count = timestampToCountMap.get(last.getTimestamp());
          timestampToCountMap.put(last.getTimestamp(), count == null ? -1 : count - 1);
        }
      }
    }

    List<SeriesData<Long>> data = new ArrayList<>();
    long total = 0;
    for (Map.Entry<Long, Long> entry : timestampToCountMap.entrySet()) {
      total += entry.getValue();
      data.add(new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(entry.getKey()), total));
    }
    // When no threads are found within the requested range, we add the threads count (0)
    // to both range's min and max. Otherwise we wouldn't add any information to the data series
    // within timeCurrentRangeUs and nothing would be added to the chart.
    if (timestampToCountMap.isEmpty()) {
      data.add(new SeriesData<>((long)xRangeUs.getMin(), total));
      data.add(new SeriesData<>((long)xRangeUs.getMax(), total));
    }
    // If the last timestamp added to the data series is less than timeCurrentRangeUs.getMax(),
    // we need to replicate the last value in timeCurrentRangeUs.getMax(), so the chart renders this value
    // until the end of the selected range.
    else if (data.get(data.size() - 1).x < xRangeUs.getMax()) {
      data.add(new SeriesData<>((long)xRangeUs.getMax(), total));
    }

    return data;
  }
}
