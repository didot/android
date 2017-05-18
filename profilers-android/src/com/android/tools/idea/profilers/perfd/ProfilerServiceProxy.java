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
package com.android.tools.idea.profilers.perfd;

import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.ddms.DevicePropertyUtil;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.stream.Collectors;

import static com.android.ddmlib.Client.CHANGE_NAME;

/**
 * A proxy ProfilerService on host that intercepts grpc requests from perfd-host to device perfd.
 * This enables us to support legacy workflows based on device's API levels.
 */
public class ProfilerServiceProxy extends PerfdProxyService
  implements AndroidDebugBridge.IClientChangeListener, AndroidDebugBridge.IDeviceChangeListener {

  private static Logger getLog() {
    return Logger.getInstance(ProfilerServiceProxy.class);
  }

  private static final String EMULATOR = "Emulator";

  private final ProfilerServiceGrpc.ProfilerServiceBlockingStub myServiceStub;
  @NotNull private final IDevice myDevice;
  @NotNull private final Profiler.Device myProfilerDevice;
  private final Map<Client, Profiler.Process> myCachedProcesses = Collections.synchronizedMap(new HashMap<>());
  private final boolean myIsDeviceApiSupported;

  public ProfilerServiceProxy(@NotNull IDevice device, @NotNull ManagedChannel channel) {
    super(ProfilerServiceGrpc.getServiceDescriptor());
    myIsDeviceApiSupported = device.getVersion().getApiLevel() >= AndroidVersion.VersionCodes.LOLLIPOP;
    myDevice = device;
    myServiceStub = ProfilerServiceGrpc.newBlockingStub(channel);

    if (myIsDeviceApiSupported) {
      // if device API is supported, use grpc to obtain the device
      Profiler.GetDevicesResponse devices = myServiceStub.getDevices(Profiler.GetDevicesRequest.getDefaultInstance());

      //TODO Remove set functions when we move functionality over to perfd.
      assert devices.getDeviceList().size() == 1;
      myProfilerDevice = profilerDeviceFromIDevice(device, devices.getDevice(0).toBuilder());
    }
    else {
      // if device API level is not supported, sets an arbitrary boot id to be used in the device session
      myProfilerDevice =
        profilerDeviceFromIDevice(device, Profiler.Device.newBuilder().setBootId(String.valueOf(device.getSerialNumber().hashCode())));
    }

    updateProcesses();

    AndroidDebugBridge.addDeviceChangeListener(this);
    AndroidDebugBridge.addClientChangeListener(this);
  }

  /**
   * Receives a {@link Profiler.Device.Builder} and converts it into a {@link Profiler.Device}.
   */
  private static Profiler.Device profilerDeviceFromIDevice(IDevice device, Profiler.Device.Builder builder) {
    return builder.setSerial(device.getSerialNumber())
      .setModel(getDeviceModel(device))
      .setVersion(StringUtil.notNullize(device.getProperty(IDevice.PROP_BUILD_VERSION)))
      .setApiLevel(device.getVersion().getApiLevel())
      .setFeatureLevel(device.getVersion().getFeatureLevel())
      .setManufacturer(DevicePropertyUtil.getManufacturer(device, device.isEmulator() ? EMULATOR : ""))
      .setIsEmulator(device.isEmulator())
      .setState(convertState(device.getState()))
      .build();
  }

  @TestOnly
  public static Profiler.Device profilerDeviceFromIDevice(IDevice device) {
    return profilerDeviceFromIDevice(device, Profiler.Device.newBuilder());
  }

  private static Profiler.Device.State convertState(IDevice.DeviceState state) {
    switch (state) {
      case OFFLINE:
        return Profiler.Device.State.OFFLINE;

      case ONLINE:
        return Profiler.Device.State.ONLINE;

      case DISCONNECTED:
        return Profiler.Device.State.DISCONNECTED;

      default:
        return Profiler.Device.State.UNSPECIFIED;
    }
  }

  private static String getDeviceModel(@NotNull IDevice device) {
    return device.isEmulator() ? StringUtil.notNullize(device.getAvdName(), "Unknown") : DevicePropertyUtil.getModel(device, "Unknown");
  }

  @Override
  public void disconnect() {
    AndroidDebugBridge.removeDeviceChangeListener(this);
    AndroidDebugBridge.removeClientChangeListener(this);
  }

  public void getDevices(Profiler.GetDevicesRequest request, StreamObserver<Profiler.GetDevicesResponse> responseObserver) {
    Profiler.GetDevicesResponse response = Profiler.GetDevicesResponse.newBuilder().addDevice(myProfilerDevice).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  public void getCurrentTime(Profiler.TimeRequest request, StreamObserver<Profiler.TimeResponse> responseObserver) {
    Profiler.TimeResponse response;
    if (myIsDeviceApiSupported) {
      // if device API is supported, use grpc to get the current time
      response = myServiceStub.getCurrentTime(request);
    }
    else {
      // otherwise, return a default (any) instance of TimeResponse
      response = Profiler.TimeResponse.getDefaultInstance();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  public void getProcesses(Profiler.GetProcessesRequest request, StreamObserver<Profiler.GetProcessesResponse> responseObserver) {
    Profiler.GetProcessesResponse response = Profiler.GetProcessesResponse.newBuilder().addAllProcess(myCachedProcesses.values()).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void deviceConnected(@NonNull IDevice device) {
    // Don't care
  }

  @Override
  public void deviceDisconnected(@NonNull IDevice device) {
    // Don't care
  }

  @Override
  public void deviceChanged(@NonNull IDevice device, int changeMask) {
    // This event can be triggered when a device goes offline. However, updateProcesses expects
    // an online device, so just ignore the event in that case.
    if (device == myDevice && (changeMask & IDevice.CHANGE_CLIENT_LIST) != 0) {
      updateProcesses();
    }
  }

  @Override
  public void clientChanged(@NonNull Client client, int changeMask) {
    if ((changeMask & CHANGE_NAME) != 0 && client.getDevice() == myDevice && client.getClientData().getClientDescription() != null) {
      updateProcesses(Collections.singletonList(client));
    }
  }

  @Override
  public ServerServiceDefinition getServiceDefinition() {
    Map<MethodDescriptor, ServerCallHandler> overrides = Maps.newHashMap();
    overrides.put(ProfilerServiceGrpc.METHOD_GET_DEVICES,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getDevices((Profiler.GetDevicesRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(ProfilerServiceGrpc.METHOD_GET_PROCESSES,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getProcesses((Profiler.GetProcessesRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(ProfilerServiceGrpc.METHOD_GET_CURRENT_TIME,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getCurrentTime((Profiler.TimeRequest)request, (StreamObserver)observer);
                  }));
    return generatePassThroughDefinitions(overrides, myServiceStub);
  }

  /**
   * Note: This method is called from the ddmlib thread.
   */
  private void updateProcesses() {
    if (!myIsDeviceApiSupported) {
      return; // Device not supported. Do nothing.
    }

    // Retrieve the list of Clients, but skip any that doesn't have a proper name yet.
    Set<Client> updatedClients =
      Arrays.stream(myDevice.getClients()).filter(c -> c.getClientData().getClientDescription() != null).collect(Collectors.toSet());
    Set<Client> existingClients = myCachedProcesses.keySet();
    ImmutableSet<Client> removedClients = Sets.difference(existingClients, updatedClients).immutableCopy();
    ImmutableSet<Client> addedClients = Sets.difference(updatedClients, existingClients).immutableCopy();

    // Only request the time if we added processes, this is needed as:
    // 1) the service test calls this function without setting up a full profiler service, and
    // 2) this is potentially being called as the device is being shut down.
    if (!addedClients.isEmpty()) {
      updateProcesses(addedClients);
    }

    for (Client client : removedClients) {
      myCachedProcesses.remove(client);
    }
  }

  /**
   * Note: This method is called from the ddmlib thread.
   */
  private void updateProcesses(@NotNull Collection<Client> clients) {
    if (!myIsDeviceApiSupported) {
      return; // Device not supported. Do nothing.
    }

    assert myDevice.isOnline();

    Profiler.TimeResponse times;
    try {
      // TODO: getTimes should take the device
      times = myServiceStub.getCurrentTime(Profiler.TimeRequest.getDefaultInstance());
    }
    catch (Exception e) {
      // Most likely the destination server went down, and we're in shut down/disconnect mode.
      getLog().info(e);
      return;
    }

    for (Client client : clients) {
      String description = client.getClientData().getClientDescription();
      if (description == null) {
        continue; // Process is still starting up and not ready yet
      }

      // TODO: Set this to the applications actual start time.
      myCachedProcesses.put(client, Profiler.Process.newBuilder().setName(client.getClientData().getClientDescription())
        .setPid(client.getClientData().getPid()).setState(Profiler.Process.State.ALIVE).setStartTimestampNs(times.getTimestampNs())
        .build());
    }
  }

  @TestOnly
  @NotNull
  Map<Client, Profiler.Process> getCachedProcesses() {
    return myCachedProcesses;
  }
}
