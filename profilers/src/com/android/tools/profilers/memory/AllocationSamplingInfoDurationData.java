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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.model.DurationData;
import com.android.tools.profiler.proto.MemoryProfiler;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

final class AllocationSamplingRangeDurationData implements DurationData {
  @NotNull private final MemoryProfiler.AllocationSamplingRange mySamplingInfo;
  private final long myDuraitonUs;

  public AllocationSamplingRangeDurationData(@NotNull MemoryProfiler.AllocationSamplingRange samplingInfo) {
    mySamplingInfo = samplingInfo;
    myDuraitonUs = TimeUnit.NANOSECONDS.toMicros(samplingInfo.getEndTime() - samplingInfo.getStartTime());
  }

  @NotNull
  public MemoryProfiler.AllocationSamplingRange getSamplingInfo() {
    return mySamplingInfo;
  }

  @Override
  public long getDurationUs() {
    return myDuraitonUs;
  }
}
