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

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;

public class ProfilersTestData {
  public static final Common.Session SESSION_DATA = Common.Session.newBuilder()
    .setSessionId(4321)
    .setDeviceId(1234)
    .setPid(5678)
    .build();

  public static final Profiler.AgentStatusResponse DEFAULT_AGENT_ATTACHED_RESPONSE =
    Profiler.AgentStatusResponse.newBuilder().setStatus(Profiler.AgentStatusResponse.Status.ATTACHED).build();

  public static final Profiler.AgentStatusResponse DEFAULT_AGENT_DETACHED_RESPONSE =
    Profiler.AgentStatusResponse.newBuilder().setStatus(Profiler.AgentStatusResponse.Status.DETACHED).build();
}
