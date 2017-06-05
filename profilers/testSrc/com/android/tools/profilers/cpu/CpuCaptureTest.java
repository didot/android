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
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.google.protobuf3jarjar.ByteString;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.Set;

import static com.android.tools.profilers.cpu.CpuProfilerTestUtils.traceFileToByteString;
import static org.junit.Assert.*;

// TODO: Add more variation of trace files (e.g trace with no threads)
public class CpuCaptureTest {

  @Test
  public void validCapture() throws IOException {
    CpuCapture capture = new CpuCapture(readValidTrace());
    assertNotNull(capture);

    Range captureRange = capture.getRange();
    assertNotNull(captureRange);
    assertFalse(captureRange.isEmpty());
    assertEquals((long)captureRange.getLength(), capture.getDuration());

    int main = capture.getMainThreadId();
    assertTrue(capture.containsThread(main));
    CaptureNode mainNode = capture.getCaptureNode(main);
    assertNotNull(mainNode);
    assertNotNull(mainNode.getData());
    assertEquals("main", mainNode.getData().getClassName());

    Set<ThreadInfo> threads = capture.getThreads();
    assertFalse(threads.isEmpty());
    for (ThreadInfo thread : threads) {
      assertNotNull(capture.getCaptureNode(thread.getId()));
      assertTrue(capture.containsThread(thread.getId()));
    }

    int inexistentThreadId = -1;
    assertFalse(capture.containsThread(inexistentThreadId));
    assertNull(capture.getCaptureNode(inexistentThreadId));
  }

  @NotNull
  public static ByteString readValidTrace() throws IOException {
    return traceFileToByteString("valid_trace.trace");
  }

  @Test
  public void corruptedTraceFileThrowsException() throws IOException {
    CpuCapture capture = null;
    ByteString corruptedTrace = traceFileToByteString("corrupted_trace.trace"); // Malformed trace file.
    assertNotNull(corruptedTrace);
    try {
      capture = new CpuCapture(corruptedTrace);
      fail();
    } catch (IllegalStateException e) {
      // Expected BufferUnderflowException to be thrown in VmTraceParser.
      assertTrue(e.getCause() instanceof BufferUnderflowException);
      // CpuCapture constructor catches the BufferUnderflowException and throw an IllegalStateException instead.
    }
    assertNull(capture);
  }

  @Test
  public void emptyTraceFileThrowsException() throws IOException {
    CpuCapture capture = null;
    ByteString emptyTrace = traceFileToByteString("empty_trace.trace"); // Empty file
    assertNotNull(emptyTrace);

    try {
      capture = new CpuCapture(emptyTrace);
      fail();
    } catch (IllegalStateException e) {
      // Expected IOException to be thrown in VmTraceParser.
      assertTrue(e.getCause() instanceof IOException);
      // CpuCapture constructor catches the IOException and throw an IllegalStateException instead.
    }
    assertNull(capture);
  }
}
