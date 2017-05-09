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
package com.android.tools.profilers;

import com.android.tools.profilers.analytics.FeatureTracker;
import org.jetbrains.annotations.NotNull;

public final class FakeFeatureTracker implements FeatureTracker {
  @Override
  public void trackEnterStage(@NotNull Class<? extends Stage> stage) {

  }

  @Override
  public void trackProfilingStarted() {

  }

  @Override
  public void trackAdvancedProfilingStarted() {

  }

  @Override
  public void trackChangeDevice() {

  }

  @Override
  public void trackChangeProcess() {

  }

  @Override
  public void trackGoBack() {

  }

  @Override
  public void trackSelectMonitor() {

  }

  @Override
  public void trackZoomIn() {

  }

  @Override
  public void trackZoomOut() {

  }

  @Override
  public void trackResetZoom() {

  }

  @Override
  public void trackToggleStreaming() {

  }

  @Override
  public void trackNavigateToCode() {

  }

  @Override
  public void trackSelectRange() {

  }

  @Override
  public void trackTraceArtSampled() {

  }

  @Override
  public void trackTraceArtInstrumented() {

  }

  @Override
  public void trackSelectThread() {

  }

  @Override
  public void trackSelectCaptureTopDown() {

  }

  @Override
  public void trackSelectCaptureBottomUp() {

  }

  @Override
  public void trackSelectCaptureFlameChart() {

  }

  @Override
  public void trackForceGc() {

  }

  @Override
  public void trackDumpHeap() {

  }

  @Override
  public void trackRecordAllocations() {

  }

  @Override
  public void trackExportHeap() {

  }

  @Override
  public void trackExportAllocation() {

  }

  @Override
  public void trackChangeClassArrangment() {

  }

  @Override
  public void trackSelectMemoryStack() {

  }

  @Override
  public void trackSelectMemoryReferences() {

  }

  @Override
  public void trackSelectNetworkRequest() {

  }

  @Override
  public void trackSelectNetworkDetailsResponse() {

  }

  @Override
  public void trackSelectNetworkDetailsHeaders() {

  }

  @Override
  public void trackSelectNetworkDetailsStack() {

  }
}
