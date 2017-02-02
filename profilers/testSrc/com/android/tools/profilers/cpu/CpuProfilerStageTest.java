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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class CpuProfilerStageTest extends AspectObserver {

  private final FakeCpuService myCpuService = new FakeCpuService();
  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("CpuProfilerStageTestChannel", myCpuService, new FakeProfilerService());

  private CpuProfilerStage myStage;

  private FakeIdeProfilerServices myServices;

  private boolean myCaptureDetailsCalled;

  @Before
  public void setUp() throws Exception {
    FakeTimer timer = new FakeTimer();
    myServices = new FakeIdeProfilerServices();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), myServices, timer);
    myStage = new CpuProfilerStage(profilers);
    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
  }

  @Test
  public void testDefaultValues() throws IOException {
    assertNotNull(myStage.getCpuTraceDataSeries());
    assertNotNull(myStage.getThreadStates());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    assertNull(myStage.getCapture());
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    assertNotNull(myStage.getAspect());
  }

  @Test
  public void testStartCapturing() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());

    // Start a successful capture
    startCapturingSuccess();

    // Start a failing capture
    myCpuService.setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status.FAILURE);
    startCapturing();
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
  }

  @Test
  public void testStopCapturingInvalidTrace() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());

    // Start a successful capture
    startCapturingSuccess();

    // Stop capturing, but don't include a trace in the response.
    myServices.setOnExecute(() -> {
      // First, the main executor is going to be called to execute stopCapturingCallback,
      // which should set the capture state to PARSING
      assertEquals(CpuProfilerStage.CaptureState.PARSING, myStage.getCaptureState());
      // Then, the next time the main executor is called, it will try to parse the capture unsuccessfully
      // and set the capture state to IDLE
      myServices.setOnExecute(() -> {
        assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
        // Capture was stopped successfully, but capture should still be null as the response has no valid trace
        assertNull(myStage.getCapture());
      });
    });
    myCpuService.setStopProfilingStatus(com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setValidTrace(false);
    stopCapturing();
  }

  @Test
  public void testStopCapturingInvalidTraceFailureStatus() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());

    // Start a successful capture
    startCapturingSuccess();

    // Stop a capture unsuccessfully
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.FAILURE);
    myCpuService.setValidTrace(false);
    myServices.setOnExecute(() -> {
      assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
      assertNull(myStage.getCapture());
    });
    stopCapturing();
  }

  @Test
  public void testStopCapturingValidTraceFailureStatus() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());

    // Start a successful capture
    startCapturingSuccess();

    // Stop a capture unsuccessfully, but with a valid trace
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.FAILURE);
    myCpuService.setValidTrace(true);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    myServices.setOnExecute(() -> {
      assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
      // Despite the fact of having a valid trace, we first check for the response status.
      // As it wasn't SUCCESS, capture should not be set.
      assertNull(myStage.getCapture());
    });
    stopCapturing();
  }

  @Test
  public void testStopCapturingSuccessfully() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    captureSuccessfully();
  }

  @Test
  public void testSelectedThread() {
    myStage.setSelectedThread(0);
    assertEquals(0, myStage.getSelectedThread());

    myStage.setSelectedThread(42);
    assertEquals(42, myStage.getSelectedThread());
  }

  @Test
  public void testCaptureDetails() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());

    captureSuccessfully();

    myStage.setSelectedThread(myStage.getCapture().getMainThreadId());

    AspectObserver observer = new AspectObserver();
    myStage.getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_DETAILS, () -> myCaptureDetailsCalled = true);

    // Top Down
    myCaptureDetailsCalled = false;
    myStage.changeCaptureDetails(CpuProfilerStage.TopDown::new);
    assertTrue(myCaptureDetailsCalled = true);

    CpuProfilerStage.CaptureDetails details = myStage.getCaptureDetails();
    assertTrue(details instanceof CpuProfilerStage.TopDown);
    assertNotNull(((CpuProfilerStage.TopDown)details).getModel());

    // Bottom Up
    myCaptureDetailsCalled = false;
    myStage.changeCaptureDetails(CpuProfilerStage.BottomUp::new);
    assertTrue(myCaptureDetailsCalled);

    details = myStage.getCaptureDetails();
    assertTrue(details instanceof CpuProfilerStage.BottomUp);
    assertNotNull(((CpuProfilerStage.BottomUp)details).getModel());

    // Chart
    myCaptureDetailsCalled = false;
    myStage.changeCaptureDetails(CpuProfilerStage.TreeChart::new);
    assertTrue(myCaptureDetailsCalled);

    details = myStage.getCaptureDetails();
    assertTrue(details instanceof CpuProfilerStage.TreeChart);
    assertNotNull(((CpuProfilerStage.TreeChart)details).getNode());

    // null
    myCaptureDetailsCalled = false;
    myStage.changeCaptureDetails(null);
    assertTrue(myCaptureDetailsCalled);
    assertNull(myStage.getCaptureDetails());

    // HNode is null, as a result the model is null as well
    myStage.setSelectedThread(-1);
    myCaptureDetailsCalled = false;
    myStage.changeCaptureDetails(CpuProfilerStage.BottomUp::new);
    assertTrue(myCaptureDetailsCalled);
    details = myStage.getCaptureDetails();
    assertTrue(details instanceof CpuProfilerStage.BottomUp);
    assertNull(((CpuProfilerStage.BottomUp)details).getModel());
  }

  private void captureSuccessfully() throws InterruptedException {
    // Start a successful capture
    startCapturingSuccess();

    // Stop a capture successfully with a valid trace
    myServices.setOnExecute(() -> {
      // First, the main executor is going to be called to execute stopCapturingCallback,
      // which should set the capture state to PARSING
      assertEquals(CpuProfilerStage.CaptureState.PARSING, myStage.getCaptureState());
      // Then, the next time the main executor is called, it will parse the capture successfully
      // and set the capture state to IDLE
      myServices.setOnExecute(() -> {
        assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
        assertNotNull(myStage.getCapture());
      });
    });
    myCpuService.setStopProfilingStatus(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS);
    myCpuService.setValidTrace(true);
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS);
    stopCapturing();
  }

  private void startCapturingSuccess() throws InterruptedException {
    assertEquals(CpuProfilerStage.CaptureState.IDLE, myStage.getCaptureState());
    myCpuService.setStartProfilingStatus(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS);
    myServices.setPrePoolExecutor(() -> assertEquals(CpuProfilerStage.CaptureState.STARTING, myStage.getCaptureState()));
    startCapturing();
    assertEquals(CpuProfilerStage.CaptureState.CAPTURING, myStage.getCaptureState());
  }

  private void startCapturing() {
    myServices.setPrePoolExecutor(() -> assertEquals(CpuProfilerStage.CaptureState.STARTING, myStage.getCaptureState()));
    myStage.startCapturing();
  }

  private void stopCapturing() {
    myServices.setPrePoolExecutor(() -> assertEquals(CpuProfilerStage.CaptureState.STOPPING, myStage.getCaptureState()));
    myStage.stopCapturing();
  }

}
