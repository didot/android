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
package com.android.tools.profilers.cpu

import com.android.tools.profiler.proto.CpuProfiler
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class ProfilingConfigurationTest {

  @get:Rule
  val myThrown = ExpectedException.none()

  @Test
  fun fromProto() {
    val proto = CpuProfiler.CpuProfilerConfiguration.newBuilder()
      .setName("MyConfiguration")
      .setProfilerMode(CpuProfiler.CpuProfilerMode.INSTRUMENTED)
      .setProfilerType(CpuProfiler.CpuProfilerType.ART)
      .setSamplingIntervalUs(123)
      .setBufferSizeInMb(12)
      .build()
    val config = ProfilingConfiguration.fromProto(proto)

    assertThat(config.name).isEqualTo("MyConfiguration")
    assertThat(config.mode).isEqualTo(CpuProfiler.CpuProfilerMode.INSTRUMENTED)
    assertThat(config.profilerType).isEqualTo(CpuProfiler.CpuProfilerType.ART)
    assertThat(config.profilingSamplingIntervalUs).isEqualTo(123)
    assertThat(config.profilingBufferSizeInMb).isEqualTo(12)
  }

  @Test
  fun toProto() {
    val configuration = ProfilingConfiguration("MyConfiguration", CpuProfiler.CpuProfilerType.SIMPLEPERF,
                                               CpuProfiler.CpuProfilerMode.SAMPLED).apply {
      profilingBufferSizeInMb = 12
      profilingSamplingIntervalUs = 1234
    }
    val proto = configuration.toProto()

    assertThat(proto.name).isEqualTo("MyConfiguration")
    assertThat(proto.profilerMode).isEqualTo(CpuProfiler.CpuProfilerMode.SAMPLED)
    assertThat(proto.profilerType).isEqualTo(CpuProfiler.CpuProfilerType.SIMPLEPERF)
    assertThat(proto.samplingIntervalUs).isEqualTo(1234)
    assertThat(proto.bufferSizeInMb).isEqualTo(12)
  }

  @Test
  fun artSampledTechnologyName() {
    val artSampledConfiguration = ProfilingConfiguration("MyConfiguration", CpuProfiler.CpuProfilerType.ART,
                                                         CpuProfiler.CpuProfilerMode.SAMPLED)
    assertThat(ProfilingConfiguration.getTechnologyName(artSampledConfiguration)).isEqualTo(ProfilingConfiguration.ART_SAMPLED_NAME)
  }

  @Test
  fun artInstrumentedTechnologyName() {
    val artInstrumentedConfiguration = ProfilingConfiguration("MyConfiguration", CpuProfiler.CpuProfilerType.ART,
                                                         CpuProfiler.CpuProfilerMode.INSTRUMENTED)
    assertThat(ProfilingConfiguration.getTechnologyName(artInstrumentedConfiguration))
      .isEqualTo(ProfilingConfiguration.ART_INSTRUMENTED_NAME)
  }

  @Test
  fun artUnspecifiedTechnologyName() {
    val artUnspecifiedConfiguration = ProfilingConfiguration("MyConfiguration", CpuProfiler.CpuProfilerType.ART,
                                                             CpuProfiler.CpuProfilerMode.UNSPECIFIED_MODE)
    assertThat(ProfilingConfiguration.getTechnologyName(artUnspecifiedConfiguration)).isEqualTo(ProfilingConfiguration.ART_UNSPECIFIED_NAME)
  }

  @Test
  fun simpleperfTechnologyName() {
    val simpleperfConfiguration = ProfilingConfiguration("MyConfiguration", CpuProfiler.CpuProfilerType.SIMPLEPERF,
                                                         CpuProfiler.CpuProfilerMode.SAMPLED)
    assertThat(ProfilingConfiguration.getTechnologyName(simpleperfConfiguration)).isEqualTo(ProfilingConfiguration.SIMPLEPERF_NAME)
  }

  @Test
  fun atraceTechnologyName() {
    val atraceConfiguration = ProfilingConfiguration("MyConfiguration", CpuProfiler.CpuProfilerType.ATRACE,
                                                     CpuProfiler.CpuProfilerMode.SAMPLED)
    assertThat(ProfilingConfiguration.getTechnologyName(atraceConfiguration)).isEqualTo(ProfilingConfiguration.ATRACE_NAME)
  }

  @Test
  fun unexpectedTechnologyName() {
    val unexpectedConfiguration = ProfilingConfiguration("MyConfiguration", CpuProfiler.CpuProfilerType.UNSPECIFIED_PROFILER,
                                                         CpuProfiler.CpuProfilerMode.SAMPLED)
    myThrown.expect(IllegalStateException::class.java)
    assertThat(ProfilingConfiguration.getTechnologyName(unexpectedConfiguration)).isEqualTo("any config. it should fail before.")
  }
}