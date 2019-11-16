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

/**
 * A collection of values that configure various features inside the profiler. This is often a way
 * to allow the IDE to communicate to the profilers that a feature should be on or off.
 */
public interface FeatureConfig {
  boolean isAtraceEnabled();
  boolean isCpuApiTracingEnabled();
  boolean isCpuCaptureStageEnabled();
  boolean isCpuNewRecordingWorkflowEnabled();
  boolean isEnergyProfilerEnabled();
  boolean isExportCpuTraceEnabled();
  boolean isFragmentsEnabled();
  boolean isImportCpuTraceEnabled();
  boolean isJniReferenceTrackingEnabled();
  boolean isLiveAllocationsEnabled();
  boolean isLiveAllocationsSamplingEnabled();
  boolean isMemoryCaptureFilterEnabled();
  boolean isMemorySnapshotEnabled();
  boolean isPerfettoEnabled();
  boolean isPerformanceMonitoringEnabled();
  boolean isAuditsEnabled();
  boolean isCustomEventVisualizationEnabled();
  boolean isSessionImportEnabled();
  boolean isStartupCpuProfilingEnabled();
  boolean isUnifiedPipelineEnabled();
  // Add new features alphabetically instead of at the end of the list
  // This reduces the chance of having to deal with an annoying merge conflict.
}
