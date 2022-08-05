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

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.TransportServiceTestImpl
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.sessions.SessionsManager
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.registerServiceInstance
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CpuCaptureSessionArtifactTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuCaptureSessionArtifactTestChannel", transportService)

  @get:Rule
  val applicationRule = ApplicationRule()

  private lateinit var sessionsManager: SessionsManager
  private lateinit var sessionItem: SessionItem

  @Before
  fun setUp() {
    ApplicationManager.getApplication().registerServiceInstance(TransportService::class.java, TransportServiceTestImpl(transportService))
    val ideProfilerServices = FakeIdeProfilerServices().also {
      it.enableEventsPipeline(true)
    }
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    sessionsManager = profilers.sessionsManager
    sessionsManager.beginSession(FakeTransportService.FAKE_DEVICE.deviceId, FakeTransportService.FAKE_DEVICE,
                                 FakeTransportService.FAKE_PROCESS)
    sessionsManager.update()
    assertThat(sessionsManager.sessionArtifacts.size).isEqualTo(1)
    sessionItem = sessionsManager.sessionArtifacts[0] as SessionItem
  }

  @Test
  fun testArtSampledCpuCaptureSessionName() {
    val artSampledTraceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                          .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
                                            .setTraceType(Cpu.CpuTraceType.ART)
                                            .setTraceMode(Cpu.CpuTraceMode.SAMPLED)))
      .build()
    addTraceInfo(artSampledTraceInfo)
    sessionsManager.update()
    assertThat(sessionItem.childArtifacts).hasSize(1)
    assertThat(sessionItem.childArtifacts[0].name).isEqualTo(ProfilingTechnology.ART_SAMPLED.getName())
  }

  @Test
  fun testArtInstrumentedCpuCaptureSessionName() {
    val artInstrumentedTraceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                          .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
                                            .setTraceType(Cpu.CpuTraceType.ART)
                                            .setTraceMode(Cpu.CpuTraceMode.INSTRUMENTED)))
      .build()
    addTraceInfo(artInstrumentedTraceInfo)
    sessionsManager.update()
    assertThat(sessionItem.childArtifacts).hasSize(1)
    assertThat(sessionItem.childArtifacts[0].name).isEqualTo(ProfilingTechnology.ART_INSTRUMENTED.getName())
  }

  @Test
  fun testImportedArtTraceCpuCaptureSessionName() {
    val artImportedTraceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                          .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
                                            .setTraceType(Cpu.CpuTraceType.ART)))
      .build()
    addTraceInfo(artImportedTraceInfo)
    sessionsManager.update()
    assertThat(sessionItem.childArtifacts).hasSize(1)
    assertThat(sessionItem.childArtifacts[0].name).isEqualTo(ProfilingTechnology.ART_UNSPECIFIED.getName())
  }

  @Test
  fun testSimpleperfCpuCaptureSessionName() {
    val simpleperfTraceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                          .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
                                            .setTraceType(Cpu.CpuTraceType.SIMPLEPERF)))
      .build()
    addTraceInfo(simpleperfTraceInfo)
    sessionsManager.update()
    assertThat(sessionItem.childArtifacts).hasSize(1)
    assertThat(sessionItem.childArtifacts[0].name).isEqualTo(ProfilingTechnology.SIMPLEPERF.getName())
  }

  @Test
  fun testAtraceCpuCaptureSessionName() {
    val atraceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                          .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
                                            .setTraceType(Cpu.CpuTraceType.ATRACE)))
      .build()
    addTraceInfo(atraceInfo)
    sessionsManager.update()
    assertThat(sessionItem.childArtifacts).hasSize(1)
    assertThat(sessionItem.childArtifacts[0].name).isEqualTo(ProfilingTechnology.SYSTEM_TRACE.getName())
  }

  private fun addTraceInfo(cpuTraceInfo: Cpu.CpuTraceInfo) {
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID,
                                      Common.Event.newBuilder()
                                        .setPid(FakeTransportService.FAKE_PROCESS.pid)
                                        .setKind(Common.Event.Kind.CPU_TRACE)
                                        .setCpuTrace(Cpu.CpuTraceData.newBuilder().setTraceStarted(
                                          Cpu.CpuTraceData.TraceStarted.newBuilder().setTraceInfo(cpuTraceInfo))).build())
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID,
                                      Common.Event.newBuilder()
                                        .setPid(FakeTransportService.FAKE_PROCESS.pid)
                                        .setKind(Common.Event.Kind.CPU_TRACE)
                                        .setIsEnded(true)
                                        .setCpuTrace(Cpu.CpuTraceData.newBuilder().setTraceEnded(
                                          Cpu.CpuTraceData.TraceEnded.newBuilder().setTraceInfo(cpuTraceInfo))).build())
  }
}