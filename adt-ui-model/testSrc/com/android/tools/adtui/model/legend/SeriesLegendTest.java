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
package com.android.tools.adtui.model.legend;

import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.MockAxisFormatter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SeriesLegendTest {

  @Test
  public void legendNameIsProperlyGot() {
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 100), new Range(0, 100), new LongDataSeries());
    SeriesLegend legend = new SeriesLegend(series, new MockAxisFormatter(1, 1, 1), new Range(0, 100));
    assertEquals("test", legend.getName());
  }

  @Test
  public void legendValueIsNullGivenNoData() {
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 100), new Range(0, 100), new LongDataSeries());
    SeriesLegend legend = new SeriesLegend(series, new MockAxisFormatter(1, 1, 1), new Range(0, 100));
    assertNull(legend.getValue());
  }

  @Test
  public void legendValueGotFromMatchedTime() {
    TestDataSeries dataSeries = new TestDataSeries(ContainerUtil.immutableList(
        new SeriesData<>(TimeUnit.MICROSECONDS.toNanos(100), 123L)));
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 100), new Range(0, 100), dataSeries);
    SeriesLegend legend = new SeriesLegend(series, new MockAxisFormatter(1, 1, 1), new Range(0, 100));
    assertEquals("12.3cm", legend.getValue());
  }

  @Test
  public void legendValueIsClosestRightGivenNoPreviousData() {
    TestDataSeries dataSeries = new TestDataSeries(ContainerUtil.immutableList(
        new SeriesData<>(TimeUnit.MICROSECONDS.toNanos(100), 333L), new SeriesData<>(TimeUnit.MICROSECONDS.toNanos(110), 444L)));
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 100), new Range(0, 100), dataSeries);
    SeriesLegend legend = new SeriesLegend(series, new MockAxisFormatter(1, 1, 1), new Range(0, 99));
    assertEquals("33.3cm", legend.getValue());
  }

  @Test
  public void legendValueIsClosestLeftGivenNoLaterData() {
    TestDataSeries dataSeries = new TestDataSeries(ContainerUtil.immutableList(
        new SeriesData<>(0L, 111L), new SeriesData<>(TimeUnit.MICROSECONDS.toNanos(10), 222L)));
    RangedContinuousSeries series = new RangedContinuousSeries("test", new Range(0, 100), new Range(0, 100), dataSeries);
    SeriesLegend legend = new SeriesLegend(series, new MockAxisFormatter(1, 1, 1), new Range(1, 100));
    assertEquals("22.2cm", legend.getValue());
  }

  private static class TestDataSeries implements DataSeries<Long> {

    @NotNull ImmutableList<SeriesData<Long>> myDataList;

    public TestDataSeries(@NotNull ImmutableList<SeriesData<Long>> data) {
      myDataList = data;
    }

    @Override
    public ImmutableList<SeriesData<Long>> getDataForXRange(Range xRange) {
      return myDataList;
    }
  }
}
