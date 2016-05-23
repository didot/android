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

package com.android.tools.adtui.visual;

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.AnimatedTimeRange;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.model.RangedDiscreteSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.SeriesDataStore;
import com.android.tools.adtui.segment.CpuUsageSegment;
import com.android.tools.adtui.segment.ThreadsSegment;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.*;
import java.util.List;

public class CpuProfilerVisualTest extends VisualTest {

  private static final String CPU_PROFILER_NAME = "CPU Profiler";


  private static final int UPDATE_THREAD_SLEEP_DELAY_MS = 100;
  /**
   * The active threads should be copied into this array when getThreadGroup().enumerate() is called.
   * It is initialized with a safe size.
   */
  private static final Thread[] ACTIVE_THREADS = new Thread[1000];

  private CpuUsageSegment mCPULevel1Segment;

  private CpuUsageSegment mCPULevel2Segment;

  private ThreadsSegment mThreadsSegment;

  private long mStartTimeMs;
  /**
   * Stores the state series corresponding to each thread.
   */
  private Map<Thread, RangedDiscreteSeries<Thread.State>> mThreadsStateSeries;

  private Range mTimeRange;

  @Override
  protected void registerComponents(List<AnimatedComponent> components) {
    mCPULevel1Segment.registerComponents(components);
    mCPULevel2Segment.registerComponents(components);
    mThreadsSegment.registerComponents(components);
  }

  @Override
  public String getName() {
    return CPU_PROFILER_NAME;
  }

  @Override
  protected List<Animatable> createComponentsList() {
    mStartTimeMs = System.currentTimeMillis();
    mTimeRange = new Range();
    AnimatedTimeRange AnimatedTimeRange = new AnimatedTimeRange(mTimeRange, mStartTimeMs);

    //TODO Update test data for CPUUsageSegment to be exatly what it was.
    mThreadsStateSeries = new HashMap<>(); // TODO: maybe it's safer to keep insertion order

    mCPULevel1Segment = new CpuUsageSegment(mTimeRange, mDataStore);
    mCPULevel2Segment = new CpuUsageSegment(mTimeRange, mDataStore);
    mThreadsSegment = new ThreadsSegment(mTimeRange);

    List<Animatable> animatables = new ArrayList<>();
    animatables.add(AnimatedTimeRange);
    mCPULevel1Segment.createComponentsList(animatables);
    mCPULevel2Segment.createComponentsList(animatables);
    mThreadsSegment.createComponentsList(animatables);
    return animatables;
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    panel.setLayout(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weighty = 1f/3;
    constraints.weightx = 1;
    constraints.gridy = 0;
    mCPULevel1Segment.initializeComponents();
    panel.add(mCPULevel1Segment, constraints);
    constraints.gridy = 1;
    mCPULevel2Segment.initializeComponents();
    panel.add(mCPULevel2Segment, constraints);
    constraints.gridy = 2;
    mThreadsSegment.initializeComponents();
    panel.add(mThreadsSegment, constraints);
    simulateTestData();
  }

  private void simulateTestData() {
    Thread updateDataThread = new Thread() {
      @Override
      public void run() {
        try {
          while (true) {
            //  Insert new data point at now.
            long now = System.currentTimeMillis() - mStartTimeMs;

            // Copy active threads into ACTIVE_THREADS array
            int numActiveThreads = getThreadGroup().enumerate(ACTIVE_THREADS);
            int targetThreads = 0;
            for (int i = 0; i < numActiveThreads; i++) {
              // We're only interested in threads that are alive
              Thread thread = ACTIVE_THREADS[i];
              if (thread.isAlive()) {
                // Add new series to states map in case there's no series corresponding to the current thread.
                if (!mThreadsStateSeries.containsKey(thread)) {
                  RangedDiscreteSeries<Thread.State> threadSeries = new RangedDiscreteSeries<>(Thread.State.class, mTimeRange);
                  mThreadsStateSeries.put(thread, threadSeries);
                  // TODO: avoid this redundancy.
                  // Maybe pass a list of data to StateChart() on its creation, so it updates when a new series is added.
                  mThreadsSegment.addThreadStateSeries(threadSeries);
                }
                targetThreads++;
              }
            }

            for (Map.Entry<Thread, RangedDiscreteSeries<Thread.State>> threadStateSeries : mThreadsStateSeries.entrySet()) {
              threadStateSeries.getValue().getSeries().add(now, threadStateSeries.getKey().getState());
            }

            Thread.sleep(UPDATE_THREAD_SLEEP_DELAY_MS);
          }
        } catch (InterruptedException ignored) {}
      }
    };
    updateDataThread.start();
  }
}
