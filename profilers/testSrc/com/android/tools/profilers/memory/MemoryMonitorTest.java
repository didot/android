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
package com.android.tools.profilers.memory;

import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.StudioProfilers;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class MemoryMonitorTest {

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryMonitorTestChannel", new FakeMemoryService());

  @Test
  public void testName() {
    MemoryMonitor monitor = new MemoryMonitor(new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices()));
    assertEquals("Memory", monitor.getName());
  }

  @Test
  public void testExpand() {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices());
    MemoryMonitor monitor = new MemoryMonitor(profilers);
    assertNull(profilers.getStage());
    monitor.expand();
    assertThat(profilers.getStage(), instanceOf(MemoryProfilerStage.class));
  }
}
