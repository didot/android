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
package com.android.tools.profilers.perfetto

import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.MainProcessSelector
import com.android.tools.profilers.cpu.TraceParser
import com.android.tools.profilers.cpu.atrace.AtraceParser
import com.android.tools.profilers.cpu.atrace.SystemTraceCpuCaptureBuilder
import com.android.tools.profilers.cpu.atrace.SystemTraceSurfaceflingerManager
import com.android.tools.profilers.systemtrace.ProcessListSorter
import java.io.File

class PerfettoParser(private val mainProcessSelector: MainProcessSelector,
                     private val ideProfilerServices: IdeProfilerServices) : TraceParser {

  override fun parse(file: File, traceId: Long): CpuCapture {
    if (ideProfilerServices.featureConfig.isUseTraceProcessor) {
      return parseUsingTraceProcessor(file, traceId)
    } else {
      return parseUsingTrebuchet(file, traceId)
    }
  }

  private fun parseUsingTrebuchet(file: File, traceId: Long): CpuCapture {
    val atraceParser = AtraceParser(Cpu.CpuTraceType.PERFETTO, mainProcessSelector)
    return atraceParser.parse(file, traceId)
  }

  private fun parseUsingTraceProcessor(file: File, traceId: Long): CpuCapture {
    val traceProcessor = ideProfilerServices.traceProcessorService

    val processList = traceProcessor.loadTrace(traceId, file)
    check(processList.isNotEmpty()) { "Invalid trace without any process information." }

    val processListSorter = ProcessListSorter(mainProcessSelector.nameHint)
    val selectedProcess = mainProcessSelector.apply(processListSorter.sort(processList))
    checkNotNull(selectedProcess) { "It was not possible to select a process for this trace." }

    val pidsToQuery = mutableListOf(selectedProcess)
    processList.find { it.name.endsWith(SystemTraceSurfaceflingerManager.SURFACEFLINGER_PROCESS_NAME) }?.let{ pidsToQuery.add(it.id) }

    val model = traceProcessor.loadCpuData(traceId, pidsToQuery)

    val builder = SystemTraceCpuCaptureBuilder(model)
    return builder.build(traceId, selectedProcess)
  }
}