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

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.Profiler;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class NullMonitorStageViewTest {


  private FakeProfilerService myRPCService = new FakeProfilerService(false);
  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("NullMonitorStageTest", myRPCService);

  @Test
  public void testCorrectStringDisplayed() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), timer);
    StudioProfilersView profilersView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    NullMonitorStage stage = new NullMonitorStage(profilers);
    NullMonitorStageView stageView = new NullMonitorStageView(profilersView, stage);
    assertEquals(stageView.getMessage(), NullMonitorStageView.NO_DEVICE_MESSAGE);

    // Add a device and force it to be updated.
    myRPCService.addDevice(Profiler.Device.getDefaultInstance());
    profilers.update(TimeUnit.SECONDS.toNanos(2));

    assertEquals(stageView.getMessage(), NullMonitorStageView.NO_DEBUGGABLE_PROCESS_MESSAGE);
  }
}
