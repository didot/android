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

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.FakeTraceParser;
import com.android.tools.profilers.cpu.art.ArtTraceParser;
import com.android.tools.profilers.cpu.atrace.AtraceParser;
import com.android.tools.profilers.cpu.simpleperf.SimpleperfTraceParser;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

// TODO: Add more variation of trace files (e.g trace with no threads)
public class CpuCaptureTest {

  @Test
  public void validCapture() throws IOException, ExecutionException, InterruptedException {
    CpuCapture capture = CpuProfilerTestUtils.getValidCapture();
    assertThat(capture).isNotNull();

    Range captureRange = capture.getRange();
    assertThat(captureRange).isNotNull();
    assertThat(captureRange.isEmpty()).isFalse();
    assertThat(capture.getDurationUs()).isEqualTo((long)captureRange.getLength());

    int main = capture.getMainThreadId();
    assertThat(capture.containsThread(main)).isTrue();
    CaptureNode mainNode = capture.getCaptureNode(main);
    assertThat(mainNode).isNotNull();
    assertThat(mainNode.getData()).isNotNull();
    assertThat(mainNode.getData().getName()).isEqualTo("main");

    Set<CpuThreadInfo> threads = capture.getThreads();
    assertThat(threads).isNotEmpty();
    for (CpuThreadInfo thread : threads) {
      assertThat(capture.getCaptureNode(thread.getId())).isNotNull();
      assertThat(capture.containsThread(thread.getId())).isTrue();
    }

    int inexistentThreadId = -1;
    assertThat(capture.containsThread(inexistentThreadId)).isFalse();
    assertThat(capture.getCaptureNode(inexistentThreadId)).isNull();
  }

  @Test
  public void corruptedTraceFileThrowsException() throws IOException, InterruptedException {
    CpuCapture capture = null;
    try {
      ByteString corruptedTrace = CpuProfilerTestUtils.traceFileToByteString("corrupted_trace.trace"); // Malformed trace file.
      capture = CpuProfilerTestUtils.getCapture(corruptedTrace, CpuProfiler.CpuProfilerType.ART);
      fail();
    } catch (ExecutionException e) {
      // An ExecutionException should happen when trying to get a capture.
      // It should be caused by an expected IllegalStateException thrown while parsing the trace bytes.
      Throwable executionExceptionCause = e.getCause();
      assertThat(executionExceptionCause).isInstanceOf(IllegalStateException.class);

      // Expected BufferUnderflowException to be thrown in VmTraceParser.
      assertThat(executionExceptionCause.getCause()).isInstanceOf(BufferUnderflowException.class);
      // CpuCaptureParser#traceBytesToCapture catches the BufferUnderflowException and throw an IllegalStateException instead.
    }
    assertThat(capture).isNull();
  }

  @Test
  public void emptyTraceFileThrowsException() throws IOException, InterruptedException {
    CpuCapture capture = null;
    try {
      ByteString emptyTrace = CpuProfilerTestUtils.traceFileToByteString("empty_trace.trace");
      capture = CpuProfilerTestUtils.getCapture(emptyTrace, CpuProfiler.CpuProfilerType.ART);
      fail();
    } catch (ExecutionException e) {
      // An ExecutionException should happen when trying to get a capture.
      // It should be caused by an expected IllegalStateException thrown while parsing the trace bytes.
      Throwable executionExceptionCause = e.getCause();
      assertThat(executionExceptionCause).isInstanceOf(IllegalStateException.class);

      // Expected IOException to be thrown in VmTraceParser.
      assertThat(executionExceptionCause.getCause()).isInstanceOf(IOException.class);
      // CpuCaptureParser#traceBytesToCapture catches the IOException and throw an IllegalStateException instead.
    }
    assertThat(capture).isNull();
  }

  @Test
  public void profilerTypeMustBeSpecified() throws IOException, InterruptedException {
    try {
      CpuProfilerTestUtils.getCapture(CpuProfilerTestUtils.readValidTrace(), CpuProfiler.CpuProfilerType.UNSPECIFIED_PROFILER);
      fail();
    }
    catch (ExecutionException e) {
      // An ExecutionException should happen when trying to get a capture.
      // It should be caused by an expected IllegalStateException thrown while parsing the trace bytes.
      Throwable executionExceptionCause = e.getCause();
      assertThat(executionExceptionCause).isInstanceOf(IllegalStateException.class);

      // Exception expected to be thrown because a valid profiler type was not set.
      assertThat(executionExceptionCause.getMessage()).contains("Trace file cannot be parsed");
    }
  }

  @Test
  public void parsingTraceWithWrongProfilerTypeShouldFail() throws IOException, InterruptedException {
    try {
      // Try to create a capture by passing an ART trace and simpleperf profiler type
      CpuProfilerTestUtils.getCapture(CpuProfilerTestUtils.readValidTrace() /* Valid ART trace */, CpuProfiler.CpuProfilerType.SIMPLEPERF);
      fail();
    } catch (ExecutionException e) {
      // An ExecutionException should happen when trying to get a capture.
      // It should be caused by an expected IllegalStateException thrown while parsing the trace bytes.
      Throwable executionExceptionCause = e.getCause();
      assertThat(executionExceptionCause).isInstanceOf(IllegalStateException.class);
      assertThat(executionExceptionCause.getMessage()).contains("magic number mismatch");
    }
  }

  @Test
  public void dualClockPassedInConstructor() {
    CpuThreadInfo info = new CpuThreadInfo(10, "main");
    Range range = new Range(0, 30);
    Map<CpuThreadInfo, CaptureNode> captureTrees =
      new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>().put(info, new CaptureNode(new StubCaptureNodeModel())).build();
    CpuCapture capture =
      new CpuCapture(new FakeTraceParser(range, captureTrees, true), 20, CpuProfiler.CpuProfilerType.UNSPECIFIED_PROFILER);
    assertThat(capture.isDualClock()).isTrue();

    capture = new CpuCapture(new FakeTraceParser(range, captureTrees, false), 20, CpuProfiler.CpuProfilerType.UNSPECIFIED_PROFILER);
    assertThat(capture.isDualClock()).isFalse();
  }

  @Test
  public void traceIdPassedInConstructor() {
    int traceId1 = 20;
    CpuThreadInfo info = new CpuThreadInfo(10, "main");
    Range range = new Range(0, 30);
    Map<CpuThreadInfo, CaptureNode> captureTrees =
      new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>().put(info, new CaptureNode(new StubCaptureNodeModel())).build();
    TraceParser parser = new FakeTraceParser(range, captureTrees, false);

    CpuCapture capture = new CpuCapture(parser, traceId1, CpuProfiler.CpuProfilerType.UNSPECIFIED_PROFILER);
    assertThat(capture.getTraceId()).isEqualTo(traceId1);

    int traceId2 = 50;
    capture = new CpuCapture(parser, traceId2, CpuProfiler.CpuProfilerType.UNSPECIFIED_PROFILER);
    assertThat(capture.getTraceId()).isEqualTo(traceId2);
  }

  @Test
  public void profilerTypePassedInConstructor() {
    int traceId = 20;
    CpuThreadInfo info = new CpuThreadInfo(10, "main");
    Range range = new Range(0, 30);
    Map<CpuThreadInfo, CaptureNode> captureTrees =
      new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>().put(info, new CaptureNode(new StubCaptureNodeModel())).build();
    TraceParser parser = new FakeTraceParser(range, captureTrees, false);

    CpuCapture capture = new CpuCapture(parser, traceId, CpuProfiler.CpuProfilerType.ART);
    assertThat(capture.getType()).isEqualTo(CpuProfiler.CpuProfilerType.ART);

    capture = new CpuCapture(parser, traceId, CpuProfiler.CpuProfilerType.SIMPLEPERF);
    assertThat(capture.getType()).isEqualTo(CpuProfiler.CpuProfilerType.SIMPLEPERF);

    capture = new CpuCapture(parser, traceId, CpuProfiler.CpuProfilerType.ATRACE);
    assertThat(capture.getType()).isEqualTo(CpuProfiler.CpuProfilerType.ATRACE);

    capture = new CpuCapture(parser, traceId, CpuProfiler.CpuProfilerType.UNSPECIFIED_PROFILER);
    assertThat(capture.getType()).isEqualTo(CpuProfiler.CpuProfilerType.UNSPECIFIED_PROFILER);
  }

  @Test
  public void dualClockSupportDiffersFromParser() {
    ArtTraceParser artParser = new ArtTraceParser();
    assertThat(artParser.supportsDualClock()).isTrue();

    SimpleperfTraceParser simpleperfTraceParser = new SimpleperfTraceParser("any.id");
    assertThat(simpleperfTraceParser.supportsDualClock()).isFalse();

    AtraceParser atraceParser = new AtraceParser(123);
    assertThat(atraceParser.supportsDualClock()).isFalse();
  }
}
