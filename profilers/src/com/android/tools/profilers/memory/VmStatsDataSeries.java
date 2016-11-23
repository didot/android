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

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class VmStatsDataSeries implements DataSeries<Long> {
  @NotNull
  private MemoryServiceGrpc.MemoryServiceBlockingStub myClient;

  private final int myProcessId;

  @NotNull
  private Function<MemoryProfiler.MemoryData.VmStatsSample, Long> myFilter;

  public VmStatsDataSeries(@NotNull MemoryServiceGrpc.MemoryServiceBlockingStub client, int id,
                           @NotNull Function<MemoryProfiler.MemoryData.VmStatsSample, Long> filter) {
    myClient = client;
    myProcessId = id;
    myFilter = filter;
  }

  @Override
  public ImmutableList<SeriesData<Long>> getDataForXRange(@NotNull Range timeCurrentRangeUs) {
    // TODO: Change the Memory API to allow specifying padding in the request as number of samples.
    long bufferNs = TimeUnit.SECONDS.toNanos(1);
    MemoryProfiler.MemoryRequest.Builder dataRequestBuilder = MemoryProfiler.MemoryRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTime(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()) - bufferNs)
      .setEndTime(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax()) + bufferNs);
    MemoryProfiler.MemoryData response = myClient.getData(dataRequestBuilder.build());

    List<SeriesData<Long>> seriesData = new ArrayList<>();
    for (MemoryProfiler.MemoryData.VmStatsSample sample : response.getVmStatsSamplesList()) {
      long dataTimestamp = TimeUnit.NANOSECONDS.toMicros(sample.getTimestamp());
      seriesData.add(new SeriesData<>(dataTimestamp, myFilter.apply(sample)));
    }
    return ContainerUtil.immutableList(seriesData);
  }

  @Override
  public SeriesData<Long> getClosestData(long x) {
    // TODO: Change the Memory API to allow specifying padding in the request as number of samples.
    long xNs = TimeUnit.MICROSECONDS.toNanos(x);
    long bufferNs = TimeUnit.SECONDS.toNanos(1);
    MemoryProfiler.MemoryRequest.Builder dataRequestBuilder = MemoryProfiler.MemoryRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTime(xNs - bufferNs)
      .setEndTime(xNs + bufferNs);
    MemoryProfiler.MemoryData response = myClient.getData(dataRequestBuilder.build());

    List<MemoryProfiler.MemoryData.VmStatsSample> list = response.getVmStatsSamplesList();
    if (list.size() == 0) {
      return null;
    }

    MemoryProfiler.MemoryData.VmStatsSample sample = MemoryProfiler.MemoryData.VmStatsSample.newBuilder().setTimestamp(xNs).build();
    int index = Collections.binarySearch(list, sample, (left, right) -> {
      long diff = left.getTimestamp() - right.getTimestamp();
      return (diff == 0) ? 0 : (diff < 0) ? -1 : 1;
    });

    index = DataSeries.convertBinarySearchIndex(index, list.size());
    long timestamp = TimeUnit.NANOSECONDS.toMicros(list.get(index).getTimestamp());
    return new SeriesData<>(timestamp, myFilter.apply(list.get(index)));
  }
}
