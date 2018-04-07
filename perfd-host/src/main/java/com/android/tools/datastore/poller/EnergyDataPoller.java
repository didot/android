/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.datastore.database.EnergyTable;
import com.android.tools.datastore.energy.BatteryModel;
import com.android.tools.datastore.energy.CpuConfig;
import com.android.tools.datastore.energy.PowerProfile;
import com.android.tools.profiler.proto.*;
import com.android.tools.profiler.proto.CpuProfiler.CpuCoreConfigResponse;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * This class hosts an EnergyService that will provide callers access to all cached energy data.
 *
 * NOTE: This poller depends on other services (e.g. CPU, Network). Be sure that those service's
 * pollers get called first.
 */
public final class EnergyDataPoller extends PollRunner {
  @NotNull private final Common.Session mySession;
  @NotNull private final BatteryModel myBatteryModel;
  @NotNull private final EnergyTable myEnergyTable;
  @NotNull private final EnergyServiceGrpc.EnergyServiceBlockingStub myEnergyService;
  private long myDataRequestStartTimestampNs;
  @Nullable
  private CpuProfiler.CpuUsageData myLastData = null;
  private PowerProfile.NetworkType myLastKnownNetworkType = PowerProfile.NetworkType.NONE;

  @NotNull private ProfilerServiceGrpc.ProfilerServiceBlockingStub myProfilerService;
  @NotNull private CpuServiceGrpc.CpuServiceBlockingStub myCpuService;
  @NotNull private NetworkServiceGrpc.NetworkServiceBlockingStub myNetworkService;

  private long myLastRetryTime = 0;
  private int myCpuConfigRetries = 5; // Initial value is maximum number of retries.
  @NotNull private CpuConfig myCpuConfig;

  // TODO: Once we move away from fake data, don't rely on the profilerService anymore
  public EnergyDataPoller(@NotNull Common.Session session,
                          @NotNull BatteryModel batteryModel,
                          @NotNull EnergyTable eventTable,
                          @NotNull ProfilerServiceGrpc.ProfilerServiceBlockingStub profilerService,
                          @NotNull CpuServiceGrpc.CpuServiceBlockingStub cpuService,
                          @NotNull NetworkServiceGrpc.NetworkServiceBlockingStub networkService,
                          @NotNull EnergyServiceGrpc.EnergyServiceBlockingStub energyService) {
    super(POLLING_DELAY_NS);
    myBatteryModel = batteryModel;
    myEnergyTable = eventTable;
    myProfilerService = profilerService;
    myCpuService = cpuService;
    myNetworkService = networkService;
    myEnergyService = energyService;
    mySession = session;

    myDataRequestStartTimestampNs = queryCurrentTime();

    CpuCoreConfigResponse response = CpuCoreConfigResponse.getDefaultInstance();
    try {
      myLastRetryTime = System.currentTimeMillis();
      // TODO: Test on single core phones to see if they report data via "cpu0" or "cpu".
      response = myCpuService.getCpuCoreConfig(CpuProfiler.CpuCoreConfigRequest.newBuilder().setDeviceId(session.getDeviceId()).build());
    }
    catch (StatusRuntimeException e) {
      getLog().debug("Unable to parse CPU frequency files.", e);
    }

    myCpuConfig = new CpuConfig(response);
  }

  // TODO: Remove this temporary function once we're not creating fake data anymore
  private long queryCurrentTime() {
    Profiler.TimeRequest timeRequest = Profiler.TimeRequest.newBuilder().setDeviceId(mySession.getDeviceId()).build();
    return myProfilerService.getCurrentTime(timeRequest).getTimestampNs();
  }

  @Override
  public void poll() throws StatusRuntimeException {
    // TODO: Set endTimestamp with last timestamp from queried data (right now we're creating fake data)
    long endTimestampNs = queryCurrentTime();

    EnergyProfiler.EnergyRequest request = EnergyProfiler.EnergyRequest.newBuilder()
      .setSession(mySession)
      .setStartTimestamp(myDataRequestStartTimestampNs)
      .setEndTimestamp(endTimestampNs) // TODO: Replace with Long.MAX_VALUE when grabbing data from device (see other pollers)
      .build();

    addLatestEvents(request); // Update events before samples, so any event with an effect on samples will get reflected in the samples.
    addLatestSamples(request);

    myDataRequestStartTimestampNs = endTimestampNs;
  }

  private void addLatestEvents(@NotNull EnergyProfiler.EnergyRequest request) {
    for (EnergyProfiler.EnergyEvent event : myEnergyService.getEvents(request).getEventsList()) {
      // Location-related events.
      if (event.hasLocationUpdateRequested()) {
        myBatteryModel.handleEvent(
          event.getTimestamp(),
          BatteryModel.Event.LOCATION_REGISTER,
          new PowerProfile.LocationEvent(
            event.getEventId(), PowerProfile.LocationType.from(event.getLocationUpdateRequested().getRequest().getProvider())));
      }
      if (event.hasLocationChanged()) {
        myBatteryModel.handleEvent(
          event.getTimestamp(),
          BatteryModel.Event.LOCATION_USAGE,
          new PowerProfile.LocationEvent(
            event.getEventId(), PowerProfile.LocationType.from(event.getLocationChanged().getLocation().getProvider())));
      }
      if (event.hasLocationUpdateRemoved()) {
        myBatteryModel.handleEvent(
          event.getTimestamp(),
          BatteryModel.Event.LOCATION_UNREGISTER,
          new PowerProfile.LocationEvent(
            event.getEventId(), PowerProfile.LocationType.NONE));
      }

      myEnergyTable.insertOrReplace(mySession, event);
    }
  }

  private void addLatestSamples(@NotNull EnergyProfiler.EnergyRequest request) {
    // Network-related samples
    {
      NetworkProfiler.NetworkDataRequest networkDataRequest =
        NetworkProfiler.NetworkDataRequest.newBuilder()
          .setSession(request.getSession())
          .setStartTimestamp(request.getStartTimestamp())
          .setEndTimestamp(request.getEndTimestamp())
          .setType(NetworkProfiler.NetworkDataRequest.Type.ALL)
          .build();

      NetworkProfiler.NetworkDataResponse networkDataResponse = myNetworkService.getData(networkDataRequest);
      for (NetworkProfiler.NetworkProfilerData networkData : networkDataResponse.getDataList()) {
        switch (networkData.getDataCase()) {
          case CONNECTIVITY_DATA:
            // Don't send an event for connection change. Leave it for the next speed data.
            myLastKnownNetworkType = PowerProfile.NetworkType.from(networkData.getConnectivityData().getDefaultNetworkType());
            break;
          case SPEED_DATA:
            NetworkProfiler.SpeedData speedData = networkData.getSpeedData();
            myBatteryModel.handleEvent(networkData.getEndTimestamp(),
                                       BatteryModel.Event.NETWORK_USAGE,
                                       new PowerProfile.NetworkStats(myLastKnownNetworkType, speedData.getReceived(), speedData.getSent()));
            break;
          default:
            break;
        }
      }
    }

    // CPU-related samples
    {
      // Try to retrieve the CPU min/max frequency files again if there was a parsing error.
      if (!myCpuConfig.getIsMinMaxCoreFreqValid() &&
          myCpuConfigRetries > 0 &&
          (myLastRetryTime = System.currentTimeMillis()) - myLastRetryTime > TimeUnit.SECONDS.toMillis(1)) { // Retry once every second.
        myCpuConfigRetries--;
        myLastRetryTime = System.currentTimeMillis();

        try {
          // TODO: Test on single core phones to see if they report data via "cpu0" or "cpu".
          myCpuConfig = new CpuConfig(
            myCpuService.getCpuCoreConfig(CpuProfiler.CpuCoreConfigRequest.newBuilder().setDeviceId(mySession.getDeviceId()).build()));
        }
        catch (StatusRuntimeException e) {
          getLog().debug(String.format("Unable to parse CPU frequency files. Retries remaining: %d", myCpuConfigRetries), e);
        }
      }

      CpuProfiler.CpuDataRequest cpuDataRequest =
        CpuProfiler.CpuDataRequest.newBuilder()
          .setSession(request.getSession())
          .setStartTimestamp(request.getStartTimestamp())
          .setEndTimestamp(request.getEndTimestamp())
          .build();
      CpuProfiler.CpuDataResponse cpuDataResponse = myCpuService.getData(cpuDataRequest);
      CpuProfiler.CpuUsageData prevUsageData = myLastData;

      for (CpuProfiler.CpuUsageData currUsageData : cpuDataResponse.getDataList()) {
        if (prevUsageData == null) {
          prevUsageData = currUsageData;
          continue;
        }

        myBatteryModel.handleEvent(currUsageData.getEndTimestamp(), BatteryModel.Event.CPU_USAGE,
                                   myCpuConfig.getCpuCoreUsages(prevUsageData, currUsageData));
        prevUsageData = currUsageData;
      }
    }

    for (EnergyProfiler.EnergySample sample : myBatteryModel.getSamplesBetween(request.getStartTimestamp(), request.getEndTimestamp())) {
      myEnergyTable.insertOrReplace(mySession, sample);
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(EnergyDataPoller.class);
  }
}
