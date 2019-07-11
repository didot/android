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
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class AllocationInfosDataSeriesTest {

  private final FakeMemoryService myService = new FakeMemoryService();

  @NotNull private final FakeIdeProfilerServices myIdeProfilerServices = new FakeIdeProfilerServices();

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("AllocationInfosDataSeriesTest", myService);

  @Test
  public void testGetDataForXRange() throws Exception {
    MemoryData memoryData = MemoryData.newBuilder()
      .setEndTimestamp(1)
      .addAllocationsInfo(
        AllocationsInfo.newBuilder()
          .setStartTime(TimeUnit.MICROSECONDS.toNanos(2)).setEndTime(TimeUnit.MICROSECONDS.toNanos(7)).setLegacy(true))
      .addAllocationsInfo(
        AllocationsInfo.newBuilder()
          .setStartTime(TimeUnit.MICROSECONDS.toNanos(17)).setEndTime(Long.MAX_VALUE).setLegacy(true))
      .build();
    myService.setMemoryData(memoryData);

    AllocationInfosDataSeries series =
      new AllocationInfosDataSeries(new ProfilerClient(myGrpcChannel.getName()), ProfilersTestData.SESSION_DATA,
                                    myIdeProfilerServices.getFeatureTracker(), null);
    List<SeriesData<CaptureDurationData<CaptureObject>>> dataList = series.getDataForRange(new Range(0, Double.MAX_VALUE));

    assertEquals(2, dataList.size());
    SeriesData<CaptureDurationData<CaptureObject>> data1 = dataList.get(0);
    assertEquals(2, data1.x);
    assertEquals(5, data1.value.getDurationUs());
    CaptureObject capture1 = data1.value.getCaptureEntry().getCaptureObject();
    assertEquals(TimeUnit.MICROSECONDS.toNanos(2), capture1.getStartTimeNs());
    assertEquals(TimeUnit.MICROSECONDS.toNanos(7), capture1.getEndTimeNs());

    SeriesData<CaptureDurationData<CaptureObject>> data2 = dataList.get(1);
    assertEquals(17, data2.x);
    assertEquals(Long.MAX_VALUE, data2.value.getDurationUs());
    CaptureObject capture2 = data2.value.getCaptureEntry().getCaptureObject();
    assertEquals(TimeUnit.MICROSECONDS.toNanos(17), capture2.getStartTimeNs());
    assertEquals(Long.MAX_VALUE, capture2.getEndTimeNs());
  }
}