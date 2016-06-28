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
package com.android.tools.idea.monitor.ui.visual.data;

import com.android.tools.idea.monitor.datastore.DataAdapter;
import gnu.trove.TLongArrayList;

import javax.swing.*;

/**
 * Simulated data generator.
 */
public abstract class TestDataGenerator<T> implements DataAdapter<T> {

  private static final int GENERATE_DATA_THREAD_DELAY = 100;

  protected TLongArrayList mTime = new TLongArrayList();

  protected long mStartTimeUs;

  private Thread mDataThread;

  @Override
  public void stop() {
    if (mDataThread != null) {
      mDataThread.interrupt();
      mDataThread = null;
    }
  }

  @Override
  public void reset(long deviceStartTimeUs, long studioStartTimeUs) {
    stop();
    mStartTimeUs = deviceStartTimeUs;
    mDataThread = new Thread() {
      @Override
      public void run() {
        try {
          while (true) {
            // TODO: come up with a better way of handling thread issues
            SwingUtilities.invokeLater(() -> generateData());

            Thread.sleep(getSleepTime());
          }
        }
        catch (InterruptedException ignored) {
        }
      }
    };
    mDataThread.start();
  }

  @Override
  public int getClosestTimeIndex(long timeUs) {
    int index = mTime.binarySearch(timeUs + mStartTimeUs);
    if (index < 0) {
      // No exact match, returns position to the left of the insertion point.
      // NOTE: binarySearch returns -(insertion point + 1) if not found.
      index = -index - 2;
    }

    return Math.max(0, Math.min(mTime.size() - 1, index));
  }

  /**
   * Returns the amount of time before the next iteration of the generateData thread.
   */
  public int getSleepTime() {
    return GENERATE_DATA_THREAD_DELAY;
  }

  /**
   * Function for test to override, this function gets called on its own thread and is used to simulate
   * new data coming from the device.
   */
  protected abstract void generateData();
}
