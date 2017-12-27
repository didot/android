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
package com.android.tools.adtui.model;

import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public abstract class BaseDataSeries<E> implements DataSeries<E> {
  @NotNull
  protected final TLongArrayList mX = new TLongArrayList();

  @NotNull
  private List<SeriesData<E>> getDataSubList(final int fromIndex, final int toIndex) {
    return IntStream.range(fromIndex, toIndex).mapToObj(index ->
      new SeriesData<>(getX(index), getY(index))
    ).collect(Collectors.toList());
  }

  @Override
  public List<SeriesData<E>> getDataForXRange(Range xRange) {
    //If the size of our data is 0, early return an empty list.
    if(size() == 0 || xRange.isEmpty()) {
      return getDataSubList(0, 0);
    }

    int fromIndex = getNearestXIndex((long)xRange.getMin());
    int toIndex = getNearestXIndex((long)xRange.getMax()) + 1;
    return getDataSubList(fromIndex, toIndex);
  }

  public List<SeriesData<E>> getAllData() {
    return getDataSubList(0, size());
  }

  /**
   * Implementations need to store both the x, and y values. For a given index the X value should correspond to the Y value.
   */
  public abstract void add(long x, E y);

  public int size() {
    return mX.size();
  }

  public long getX(int index) {
    return mX.get(index);
  }

  /**
   * Returns the value of Y at a given index.
   */
  public abstract E getY(int index);

  public int getNearestXIndex(long x) {
    int index = mX.binarySearch(x);

    if (index < 0) {
      // No exact match, returns position to the left of the insertion point.
      // NOTE: binarySearch returns -(insertion point + 1) if not found.
      // Example: Value = 2.5, data = 0,1,2,3,4.
      //    BinarySearch will return -4 = -(3 + 1) as 3 is the insertion point.
      //    Given our usage of the data we want to round down not up as such we step to 1 before the insertion point.
      index = -index - 2;
    }

    return Math.max(0, Math.min(index, size() - 1));
  }
}
