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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profilers.FakeGrpcChannel;
import com.intellij.util.containers.ImmutableList;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class VmStatsDataSeriesTest {
  private final FakeMemoryService myService = new FakeMemoryService();

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("VmStatsDataSeriesTest", myService);

  @Test
  public void testGetDataForXRange() throws Exception {
    MemoryProfiler.MemoryData memoryData = MemoryProfiler.MemoryData.newBuilder()
      .setEndTimestamp(1)
      .addVmStatsSamples(
        MemoryProfiler.MemoryData.VmStatsSample.newBuilder().setTimestamp(TimeUnit.MICROSECONDS.toNanos(3)).setJavaAllocationCount(1000)
          .setJavaFreeCount(2000))
      .addVmStatsSamples(
        MemoryProfiler.MemoryData.VmStatsSample.newBuilder().setTimestamp(TimeUnit.MICROSECONDS.toNanos(14)).setJavaAllocationCount(1500)
          .setJavaFreeCount(2500))
      .build();
    myService.setMemoryData(memoryData);

    VmStatsDataSeries series =
      new VmStatsDataSeries(myGrpcChannel.getClient().getMemoryClient(), 1, sample -> (long)sample.getJavaAllocationCount());
    ImmutableList<SeriesData<Long>> dataList = series.getDataForXRange(new Range(0, Double.MAX_VALUE));
    assertEquals(2, dataList.size());
    assertEquals(3, dataList.get(0).x);
    assertEquals(1000, dataList.get(0).value.longValue());
    assertEquals(14, dataList.get(1).x);
    assertEquals(1500, dataList.get(1).value.longValue());

    series = new VmStatsDataSeries(myGrpcChannel.getClient().getMemoryClient(), 1, sample -> (long)sample.getJavaFreeCount());
    dataList = series.getDataForXRange(new Range(0, Double.MAX_VALUE));
    assertEquals(2, dataList.size());
    assertEquals(3, dataList.get(0).x);
    assertEquals(2000, dataList.get(0).value.longValue());
    assertEquals(14, dataList.get(1).x);
    assertEquals(2500, dataList.get(1).value.longValue());
  }
}