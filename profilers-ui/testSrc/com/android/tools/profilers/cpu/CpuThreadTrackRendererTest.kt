/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.adtui.chart.hchart.HTreeChart
import com.android.tools.adtui.chart.statechart.StateChart
import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.MultiSelectionModel
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilerTrackRendererType
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class CpuThreadTrackRendererTest {
  private val timer = FakeTimer()
  private val services = FakeIdeProfilerServices()
  private val transportService = FakeTransportService(timer, true)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuCaptureStageTestChannel", FakeCpuService(), FakeProfilerService(timer), transportService)
  private val profilerClient = ProfilerClient(grpcChannel.name)

  private lateinit var profilers: StudioProfilers

  @Before
  fun setUp() {
    profilers = StudioProfilers(profilerClient, services, timer)
  }

  @Test
  fun renderComponentsForThreadTrack() {
    val threadInfo = CpuThreadInfo(1, "Thread-1")
    val multiSelectionModel = MultiSelectionModel<CpuAnalyzable<*>>()
    val captureNode = CaptureNode(StubCaptureNodeModel())
    val mockCapture = Mockito.mock(CpuCapture::class.java)
    Mockito.`when`(mockCapture.range).thenReturn(Range())
    Mockito.`when`(mockCapture.getCaptureNode(1)).thenReturn(captureNode)
    val threadTrackModel = TrackModel.newBuilder(
      CpuThreadTrackModel(
        profilers,
        Range(),
        mockCapture,
        threadInfo,
        DefaultTimeline(),
        multiSelectionModel
      ),
      ProfilerTrackRendererType.CPU_THREAD, "Foo").build()
    val renderer = CpuThreadTrackRenderer()
    val component = renderer.render(threadTrackModel)
    assertThat(component.componentCount).isEqualTo(2)
    assertThat(component.components[0]).isInstanceOf(StateChart::class.java)
    assertThat(component.components[1]).isInstanceOf(HTreeChart::class.java)

    // Verify trace event chart selection is updated.
    val traceEventChart = component.components[1] as HTreeChart<CaptureNode>
    assertThat(traceEventChart.selectedNode).isNull()
    traceEventChart.selectedNode = captureNode
    assertThat(traceEventChart.selectedNode).isSameAs(captureNode)
    multiSelectionModel.clearSelection()
    assertThat(traceEventChart.selectedNode).isNull()
  }
}