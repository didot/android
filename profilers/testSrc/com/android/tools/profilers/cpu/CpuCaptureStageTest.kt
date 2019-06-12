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

import com.android.tools.profiler.protobuf3jarjar.ByteString
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.BufferedReader
import java.io.FileReader

class CpuCaptureStageTest {

  @Test
  fun savingCaptureHasData() {
    val data = "Some Data"
    val traceId = 1234L
    val file = CpuCaptureStage.saveCapture(traceId, ByteString.copyFromUtf8(data))
    assertThat(file.name).matches("cpu_trace_$traceId.trace")
    val reader = BufferedReader(FileReader(file))
    assertThat(reader.readLine()).isEqualTo(data)
  }
}