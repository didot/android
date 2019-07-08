/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers;

import static com.android.tools.profiler.proto.Common.Event.EventGroupIds.NETWORK_RX_VALUE;
import static com.android.tools.profiler.proto.Common.Event.EventGroupIds.NETWORK_TX_VALUE;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Energy;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Network;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profilers.network.httpdata.HttpData;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * Profiler test data holder class.
 */
public final class ProfilersTestData {

  // Un-initializable.
  private ProfilersTestData() {
  }

  public static final Common.Session SESSION_DATA = Common.Session.newBuilder()
    .setSessionId(4321)
    .setStreamId(1234)
    .setPid(5678)
    .build();

  public static final Common.AgentData DEFAULT_AGENT_ATTACHED_RESPONSE =
    Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build();

  public static final Common.AgentData DEFAULT_AGENT_DETACHED_RESPONSE =
    Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.UNATTACHABLE).build();

  @NotNull
  public static Common.Event.Builder generateNetworkTxEvent(long timestampUs, int throughput) {
    return Common.Event.newBuilder()
      .setTimestamp(TimeUnit.MICROSECONDS.toNanos(timestampUs))
      .setKind(Common.Event.Kind.NETWORK_SPEED)
      .setGroupId(NETWORK_TX_VALUE)
      .setNetworkSpeed(Network.NetworkSpeedData.newBuilder().setThroughput(throughput));
  }

  @NotNull
  public static Common.Event.Builder generateNetworkRxEvent(long timestampUs, int throughput) {
    return Common.Event.newBuilder()
      .setTimestamp(TimeUnit.MICROSECONDS.toNanos(timestampUs))
      .setKind(Common.Event.Kind.NETWORK_SPEED)
      .setGroupId(NETWORK_RX_VALUE)
      .setNetworkSpeed(Network.NetworkSpeedData.newBuilder().setThroughput(throughput));
  }

  @NotNull
  public static EventGroup.Builder generateNetworkConnectionData(@NotNull HttpData data) {
    long connectionId = data.getId();
    EventGroup.Builder builder = EventGroup.newBuilder().setGroupId(connectionId);
    long requestStartNs = TimeUnit.MICROSECONDS.toNanos(data.getRequestStartTimeUs());
    long requestCompleteNs = TimeUnit.MICROSECONDS.toNanos(data.getRequestCompleteTimeUs());
    long responseStartNs = TimeUnit.MICROSECONDS.toNanos(data.getResponseStartTimeUs());
    long responseCompleteNs = TimeUnit.MICROSECONDS.toNanos(data.getResponseCompleteTimeUs());
    long connectionEndNs = TimeUnit.MICROSECONDS.toNanos(data.getConnectionEndTimeUs());
    if (requestStartNs > 0) {
      builder.addEvents(
        Common.Event.newBuilder().setGroupId(connectionId).setTimestamp(requestStartNs).setKind(Common.Event.Kind.NETWORK_HTTP_CONNECTION)
          .setNetworkHttpConnection(Network.NetworkHttpConnectionData.newBuilder().setHttpRequestStarted(
            Network.NetworkHttpConnectionData.HttpRequestStarted.newBuilder()
              .setUrl(data.getUrl()).setMethod(data.getMethod()).setFields(data.getRequestHeader().getRawFields())
              .setTrace(data.getTrace()))));
      if (requestCompleteNs > 0) {
        builder.addEvents(Common.Event.newBuilder().setGroupId(connectionId).setTimestamp(requestCompleteNs)
                            .setKind(Common.Event.Kind.NETWORK_HTTP_CONNECTION)
                            .setNetworkHttpConnection(Network.NetworkHttpConnectionData.newBuilder().setHttpRequestCompleted(
                              Network.NetworkHttpConnectionData.HttpRequestCompleted.newBuilder()
                                .setPayloadId(data.getRequestPayloadId()))));
        if (responseStartNs > 0) {
          builder.addEvents(Common.Event.newBuilder().setGroupId(connectionId).setTimestamp(responseStartNs)
                              .setKind(Common.Event.Kind.NETWORK_HTTP_CONNECTION)
                              .setNetworkHttpConnection(Network.NetworkHttpConnectionData.newBuilder().setHttpResponseStarted(
                                Network.NetworkHttpConnectionData.HttpResponseStarted.newBuilder()
                                  .setFields(data.getResponseHeader().getRawFields()))));
          if (responseCompleteNs > 0) {
            builder.addEvents(Common.Event.newBuilder().setGroupId(connectionId).setTimestamp(responseCompleteNs)
                                .setKind(Common.Event.Kind.NETWORK_HTTP_CONNECTION)
                                .setNetworkHttpConnection(Network.NetworkHttpConnectionData.newBuilder().setHttpResponseCompleted(
                                  Network.NetworkHttpConnectionData.HttpResponseCompleted.newBuilder()
                                    .setPayloadId(data.getResponsePayloadId()).setPayloadSize(data.getResponsePayloadSize()))));
          }
        }
      }

      if (connectionEndNs > 0) {
        builder.addEvents(Common.Event.newBuilder().setGroupId(connectionId).setTimestamp(connectionEndNs)
                            .setKind(Common.Event.Kind.NETWORK_HTTP_CONNECTION).setIsEnded(true)
                            .setNetworkHttpConnection(Network.NetworkHttpConnectionData.newBuilder().setHttpClosed(
                              Network.NetworkHttpConnectionData.HttpClosed.newBuilder())));
      }
    }

    return builder;
  }

  @NotNull
  public static Common.Event.Builder generateNetworkThreadData(@NotNull HttpData data) {
    assert !data.getJavaThreads().isEmpty();
    HttpData.JavaThread thread = data.getJavaThreads().get(0);
    long timestampNs = TimeUnit.MICROSECONDS.toNanos(data.getRequestStartTimeUs());
    return Common.Event.newBuilder().setGroupId(data.getId()).setKind(Common.Event.Kind.NETWORK_HTTP_THREAD).setTimestamp(timestampNs)
      .setNetworkHttpThread(Network.NetworkHttpThreadData.newBuilder().setId(thread.getId()).setName(thread.getName()));
  }

  @NotNull
  public static Common.Event.Builder generateMemoryUsageData(long timestampUs, Memory.MemoryUsageData memoryUsageData) {
    long timestampNs = TimeUnit.MICROSECONDS.toNanos(timestampUs);
    return Common.Event.newBuilder().setTimestamp(timestampNs).setKind(Common.Event.Kind.MEMORY_USAGE).setMemoryUsage(memoryUsageData);
  }

  @NotNull
  public static Common.Event.Builder generateMemoryGcData(long timestampUs, Memory.MemoryGcData gcData) {
    long timestampNs = TimeUnit.MICROSECONDS.toNanos(timestampUs);
    return Common.Event.newBuilder().setTimestamp(timestampNs).setKind(Common.Event.Kind.MEMORY_GC).setMemoryGc(gcData);
  }

  @NotNull
  public static Common.Event.Builder generateMemoryHeapDumpData(long groupId, long timestampUs, Memory.HeapDumpInfo info) {
    long timestampNs = TimeUnit.MICROSECONDS.toNanos(timestampUs);
    return Common.Event.newBuilder().setTimestamp(timestampNs).setGroupId(groupId).setKind(Common.Event.Kind.MEMORY_HEAP_DUMP)
      .setIsEnded(true).setMemoryHeapdump(Memory.MemoryHeapDumpData.newBuilder().setInfo(info));
  }

  @NotNull
  public static Common.Event.Builder generateCpuThreadEvent(long timestampSeconds, int tid, String name, Cpu.CpuThreadData.State state) {
    return Common.Event.newBuilder()
      .setPid(SESSION_DATA.getPid())
      .setTimestamp(SECONDS.toNanos(timestampSeconds))
      .setKind(Common.Event.Kind.CPU_THREAD)
      .setGroupId(tid)
      .setIsEnded(state == Cpu.CpuThreadData.State.DEAD)
      .setCpuThread(Cpu.CpuThreadData.newBuilder().setTid(tid).setName(name).setState(state));
  }

  public static void populateThreadData(@NotNull FakeTransportService service, long streamId) {
    service.addEventToStream(streamId,
                             ProfilersTestData.generateCpuThreadEvent(1, 1, "Thread 1", Cpu.CpuThreadData.State.RUNNING)
                               .build());
    service.addEventToStream(streamId,
                             ProfilersTestData.generateCpuThreadEvent(8, 1, "Thread 1", Cpu.CpuThreadData.State.DEAD)
                               .build());
    service.addEventToStream(streamId,
                             ProfilersTestData.generateCpuThreadEvent(6, 2, "Thread 2", Cpu.CpuThreadData.State.RUNNING)
                               .build());
    service.addEventToStream(streamId,
                             ProfilersTestData.generateCpuThreadEvent(8, 2, "Thread 2", Cpu.CpuThreadData.State.STOPPED)
                               .build());
    service.addEventToStream(streamId,
                             ProfilersTestData.generateCpuThreadEvent(10, 2, "Thread 2", Cpu.CpuThreadData.State.SLEEPING)
                               .build());
    service.addEventToStream(streamId,
                             ProfilersTestData.generateCpuThreadEvent(12, 2, "Thread 2", Cpu.CpuThreadData.State.WAITING)
                               .build());
    service.addEventToStream(streamId,
                             ProfilersTestData.generateCpuThreadEvent(15, 2, "Thread 2", Cpu.CpuThreadData.State.DEAD)
                               .build());
  }

  // W = Wake lock, J = Job
  // t: 100--150--200--250--300--350--400--450--500
  //     |    |    |    |    |    |    |    |    |
  // 1:  W=========]
  // 2:       J==============]
  // 3:          W======]
  // 4:                           J=========]
  // 5:                                J=========]
  // 6:                                   W====]
  public static List<Common.Event> generateEnergyEvents(int pid) {
    return Arrays.asList(
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(1)
        .setTimestamp(SECONDS.toNanos(100))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockAcquired(Energy.WakeLockAcquired.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(2)
        .setTimestamp(SECONDS.toNanos(150))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobStarted(Energy.JobStarted.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(3)
        .setTimestamp(SECONDS.toNanos(170))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockAcquired(Energy.WakeLockAcquired.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(1)
        .setTimestamp(SECONDS.toNanos(200))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockReleased(Energy.WakeLockReleased.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(3)
        .setTimestamp(SECONDS.toNanos(250))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockReleased(Energy.WakeLockReleased.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(2)
        .setTimestamp(SECONDS.toNanos(300))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobFinished(Energy.JobFinished.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(4)
        .setTimestamp(SECONDS.toNanos(350))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobStarted(Energy.JobStarted.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(5)
        .setTimestamp(SECONDS.toNanos(400))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobStarted(Energy.JobStarted.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(6)
        .setTimestamp(SECONDS.toNanos(420))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockAcquired(Energy.WakeLockAcquired.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(4)
        .setTimestamp(SECONDS.toNanos(450))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobFinished(Energy.JobFinished.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(6)
        .setTimestamp(SECONDS.toNanos(480))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockReleased(Energy.WakeLockReleased.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setPid(pid)
        .setGroupId(5)
        .setTimestamp(SECONDS.toNanos(500))
        .setKind(Common.Event.Kind.ENERGY_EVENT)
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobFinished(Energy.JobFinished.getDefaultInstance()))
        .build()
    );
  }
}