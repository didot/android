/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.profilers.commands

import androidx.tracing.perfetto.PerfettoHandshake
import com.android.ddmlib.IShellOutputReceiver
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.io.grpc.ManagedChannel
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.nio.charset.Charset


class CpuTraceInterceptCommandHandlerTest {
  private val timer = FakeTimer()
  private val service = FakeTransportService(timer)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuTraceInterceptCommandHandlerTest", service)
  private val channel: ManagedChannel = InProcessChannelBuilder.forName(grpcChannel.name).usePlaintext().directExecutor().build()

  @Test
  fun `ShouldHandle filters request non perfetto`() {
    val testPid = 1
    val cmdId = 1
    val commandHandler = setupInterceptForTest(testPid)

    var command = buildCommand(cmdId, Cpu.CpuTraceType.PERFETTO)
    Truth.assertThat(commandHandler.shouldHandle(command)).isTrue()
    command = buildCommand(cmdId, Cpu.CpuTraceType.ART)
    Truth.assertThat(commandHandler.shouldHandle(command)).isFalse()
    command = buildCommand(cmdId, Cpu.CpuTraceType.SIMPLEPERF)
    Truth.assertThat(commandHandler.shouldHandle(command)).isFalse()
  }

  @Test
  fun `Trace command is forwarded`() {
    val testPid = 1
    val commandHandler = setupInterceptForTest(testPid)

    var command = buildCommand(1, Cpu.CpuTraceType.ART)
    var returnValue = commandHandler.execute(command)
    Truth.assertThat(returnValue.commandId).isEqualTo(1)

    var eventStream = service.getListForStream(0L)
    Truth.assertThat(eventStream).hasSize(1)
    Truth.assertThat(eventStream.first { it.kind == Common.Event.Kind.CPU_TRACE_STATUS }.commandId).isEqualTo(1)

    command = buildCommand(2, Cpu.CpuTraceType.PERFETTO)
    returnValue = commandHandler.execute(command)
    Truth.assertThat(returnValue.commandId).isEqualTo(2)

    eventStream = service.getListForStream(0L)
    Truth.assertThat(eventStream).hasSize(2)
    Truth.assertThat(eventStream.last { it.kind == Common.Event.Kind.CPU_TRACE_STATUS }.commandId).isEqualTo(2)
  }

  @Test
  fun `Trace command triggers handler`() {
    val testPid = 1
    val cmdId = 1
    val commandHandler = setupInterceptForTest(testPid)
    val startTrackCommand = buildCommand(cmdId, Cpu.CpuTraceType.PERFETTO)
    val returnValue = commandHandler.execute(startTrackCommand)
    Truth.assertThat(returnValue.commandId).isEqualTo(cmdId)
    val captor = ArgumentCaptor.forClass(String::class.java)
    verify(commandHandler.device, times(1)).executeShellCommand(captor.capture(), MockitoKt.any())
    Truth.assertThat(captor.value).contains("broadcast")
  }

  @Test
  fun `Trace failed request log to logger`() {
    val testPid = 1
    val cmdId = 1
    // Exit code 2 == SDK already enabled.
    val broadcastFailed = ("Broadcasting: Intent { act=androidx.tracing.perfetto.action.ENABLE_TRACING flg=0x400000" +
                           "cmp=androidx.compose.samples.crane/androidx.tracing.perfetto.TracingReceiver }\n" +
                           "Broadcast completed: result=2, data=\"{\"exitCode\":2,\"requiredVersion\":\"1.0.0-alpha01\"" +
                           "}\"\n")
    val commandHandler = setupInterceptForTest(testPid, broadcastFailed)
    val startTrackCommand = buildCommand(cmdId, Cpu.CpuTraceType.PERFETTO)
    val returnValue = commandHandler.execute(startTrackCommand)
    Truth.assertThat(returnValue.commandId).isEqualTo(cmdId)
    val captor = ArgumentCaptor.forClass(String::class.java)
    verify(commandHandler.device, times(1)).executeShellCommand(captor.capture(), MockitoKt.any())
    Truth.assertThat(captor.value).contains("broadcast")
    Truth.assertThat(commandHandler.lastResponseCode).isEqualTo(PerfettoHandshake.ResponseExitCodes.RESULT_CODE_ALREADY_ENABLED)
  }

  private fun setupInterceptForTest(testPid: Int): CpuTraceInterceptCommandHandler {
    val broadcast = ("Broadcasting: Intent { act=androidx.tracing.perfetto.action.ENABLE_TRACING flg=0x400000" +
                     "cmp=androidx.compose.samples.crane/androidx.tracing.perfetto.TracingReceiver }\n" +
                     "Broadcast completed: result=1, data=\"{\"exitCode\":1,\"requiredVersion\":\"1.0.0-alpha01\"" +
                     "}\"\n")
    return setupInterceptForTest(testPid, broadcast)
  }

  private fun setupInterceptForTest(testPid: Int, broadcast: String): CpuTraceInterceptCommandHandler {
    val mockClient = LegacyCpuTraceCommandHandlerTest.createMockClient(testPid)
    val commandCaptor = ArgumentCaptor.forClass(String::class.java)
    val shellCaptor = ArgumentCaptor.forClass(IShellOutputReceiver::class.java)
    whenever(mockClient.device.executeShellCommand(commandCaptor.capture(), shellCaptor.capture())).then {
      val data = broadcast.toByteArray(Charset.defaultCharset())
      shellCaptor.value.addOutput(data, 0, data.size)
    }
    val commandHandler = CpuTraceInterceptCommandHandler(mockClient.device,
                                                         TransportServiceGrpc.newBlockingStub(channel))
    return commandHandler
  }

  fun buildCommand(cmdId: Int, cpuTraceType: Cpu.CpuTraceType) = Commands.Command.newBuilder().apply {
    type = Commands.Command.CommandType.START_CPU_TRACE
    commandId = cmdId
    startCpuTrace = Cpu.StartCpuTrace.newBuilder().apply {
      configuration = Cpu.CpuTraceConfiguration.newBuilder().apply {
        abiCpuArch = "FakeAbi"
        userOptions = Cpu.CpuTraceConfiguration.UserOptions.newBuilder().apply {
          traceType = cpuTraceType
        }.build()
      }.build()
    }.build()
  }.build()
}