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
package com.android.tools.datastore.poller;

import com.android.tools.datastore.StreamId;
import com.android.tools.datastore.database.DeviceProcessTable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.AgentData;
import com.android.tools.profiler.proto.Transport.AgentStatusRequest;
import com.android.tools.profiler.proto.Transport.GetDevicesRequest;
import com.android.tools.profiler.proto.Transport.GetDevicesResponse;
import com.android.tools.profiler.proto.Transport.GetProcessesRequest;
import com.android.tools.profiler.proto.Transport.GetProcessesResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.google.common.collect.Sets;
import io.grpc.StatusRuntimeException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public class DeviceProcessPoller extends PollRunner {

  private static final class DeviceData {
    public final Common.Device device;
    public final Set<Common.Process> processes = new HashSet<>();

    public DeviceData(Common.Device device) {
      this.device = device;
    }
  }

  @NotNull private final DeviceProcessTable myTable;
  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myPollingService;
  @NotNull private final Map<StreamId, DeviceData> myDevices = new HashMap<>();

  public DeviceProcessPoller(@NotNull DeviceProcessTable table,
                             @NotNull TransportServiceGrpc.TransportServiceBlockingStub pollingService) {
    super(TimeUnit.SECONDS.toNanos(1));
    myTable = table;
    myPollingService = pollingService;
  }

  @Override
  public void poll() {
    try {
      GetDevicesRequest devicesRequest = GetDevicesRequest.newBuilder().build();
      GetDevicesResponse deviceResponse = myPollingService.getDevices(devicesRequest);
      for (Common.Device device : deviceResponse.getDeviceList()) {
        StreamId streamId = StreamId.of(device.getDeviceId());

        myTable.insertOrUpdateDevice(device);
        DeviceData deviceData = myDevices.computeIfAbsent(streamId, s -> new DeviceData(device));

        GetProcessesRequest processesRequest = GetProcessesRequest.newBuilder().setDeviceId(streamId.get()).build();
        GetProcessesResponse processesResponse = myPollingService.getProcesses(processesRequest);

        // Gather the list of last known active processes.
        Set<Common.Process> liveProcesses = new HashSet<>();

        for (Common.Process process : processesResponse.getProcessList()) {
          assert process.getDeviceId() == streamId.get();
          myTable.insertOrUpdateProcess(streamId, process);
          liveProcesses.add(process);

          AgentStatusRequest agentStatusRequest =
            AgentStatusRequest.newBuilder().setPid(process.getPid()).setDeviceId(streamId.get()).build();
          AgentData cachedData = myTable.getAgentStatus(agentStatusRequest);
          if (cachedData.getStatus() == AgentData.Status.UNSPECIFIED) {
            AgentData agentData = myPollingService.getAgentStatus(agentStatusRequest);
            myTable.updateAgentStatus(streamId, process, agentData);
          }

          deviceData.processes.add(process);
        }

        Set<Common.Process> deadProcesses = Sets.difference(deviceData.processes, liveProcesses);
        killProcesses(streamId, deadProcesses);
      }
    }
    catch (StatusRuntimeException ex) {
      // We expect this to get called when connection to the device is lost.
      // To properly clean up the state we first set all ALIVE processes to DEAD
      // then we disconnect the channel.
      disconnect();
    }
  }

  private void disconnect() {
    for (Map.Entry<StreamId, DeviceData> entry : myDevices.entrySet()) {
      disconnectDevice(entry.getValue().device);
      killProcesses(entry.getKey(), entry.getValue().processes);
    }
    myDevices.clear();
  }

  private void disconnectDevice(Common.Device device) {
    Common.Device disconnectedDevice = device.toBuilder().setState(Common.Device.State.DISCONNECTED).build();
    myTable.insertOrUpdateDevice(disconnectedDevice);
  }

  private void killProcesses(StreamId streamId, Set<Common.Process> processes) {
    for (Common.Process process : processes) {
      Common.Process updatedProcess = process.toBuilder().setState(Common.Process.State.DEAD).build();
      myTable.insertOrUpdateProcess(streamId, updatedProcess);

      // The process is already dead, just mark it as agent non-attachable.
      AgentData agentData =
        myTable.getAgentStatus(AgentStatusRequest.newBuilder().setDeviceId(streamId.get()).setPid(process.getPid()).build());
      myTable.updateAgentStatus(streamId, process, agentData.toBuilder().setStatus(AgentData.Status.UNATTACHABLE).build());
    }
  }
}
