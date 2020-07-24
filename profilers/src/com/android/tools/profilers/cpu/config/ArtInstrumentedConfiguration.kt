/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu.config

import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Cpu.CpuTraceMode

/**
 * Configuration for art traces.
 */

class ArtInstrumentedConfiguration(name: String) : ProfilingConfiguration(name) {
  var profilingBufferSizeInMb = DEFAULT_BUFFER_SIZE_MB

  override fun buildUserOptions(): Cpu.CpuTraceConfiguration.UserOptions.Builder {
    return Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
      .setTraceMode(CpuTraceMode.INSTRUMENTED)
      .setBufferSizeInMb(profilingBufferSizeInMb)
  }

  override fun getTraceType(): Cpu.CpuTraceType {
    return Cpu.CpuTraceType.ART
  }

  override fun getRequiredDeviceLevel(): Int {
    return 0
  }
}