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
package com.android.tools.datastore.database;

import com.android.tools.datastore.DataStoreDatabase;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class ProfilerTableTest {

  private File myDbFile;
  private ProfilerTable myTable;
  private DataStoreDatabase myDatabase;

  @Before
  public void setUp() throws Exception {
    // TODO: Update to work on windows. PathUtil.getTempPath() fails with bazel
    myDbFile = new File("/tmp/ProfilerTableTestDb");
    myDatabase = new DataStoreDatabase(myDbFile.getAbsolutePath());
    myTable = new ProfilerTable();
    myDatabase.registerTable(myTable);
  }

  @After
  public void tearDown() throws Exception {
    myDatabase.disconnect();
    myDbFile.delete();
  }

  @Test
  public void testAgentStatusCannotDowngrade() throws Exception {
    Common.Session session = Common.Session.newBuilder()
      .setBootId("BootId")
      .setDeviceSerial("DeviceSerial")
      .build();
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(99)
      .setName("FakeProcess")
      .build();

    // Setup initial process and status
    Profiler.AgentStatusResponse status =
      Profiler.AgentStatusResponse.newBuilder().setStatus(Profiler.AgentStatusResponse.Status.DETACHED).build();
    myTable.insertOrUpdateProcess(session, process);
    myTable.updateAgentStatus(session, process, status);

    Profiler.AgentStatusRequest request =
      Profiler.AgentStatusRequest.newBuilder().setProcessId(process.getPid()).setSession(session).build();
    assertEquals(Profiler.AgentStatusResponse.Status.DETACHED, myTable.getAgentStatus(request).getStatus());

    // Upgrading status to attach should work
    status = Profiler.AgentStatusResponse.newBuilder().setStatus(Profiler.AgentStatusResponse.Status.ATTACHED).build();
    myTable.updateAgentStatus(session, process, status);
    assertEquals(Profiler.AgentStatusResponse.Status.ATTACHED, myTable.getAgentStatus(request).getStatus());

    // Attempt to downgrade status
    status = Profiler.AgentStatusResponse.newBuilder().setStatus(Profiler.AgentStatusResponse.Status.DETACHED).build();
    myTable.updateAgentStatus(session, process, status);
    assertEquals(Profiler.AgentStatusResponse.Status.ATTACHED, myTable.getAgentStatus(request).getStatus());
  }

  @Test
  public void testExistingProcessIsUpdated() throws Exception {
    Common.Session session = Common.Session.newBuilder()
      .setBootId("BootId")
      .setDeviceSerial("DeviceSerial")
      .build();
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(99)
      .setName("FakeProcess")
      .setState(Profiler.Process.State.ALIVE)
      .build();

    // Setup initial process and status.
    Profiler.AgentStatusResponse status =
      Profiler.AgentStatusResponse.newBuilder().setStatus(Profiler.AgentStatusResponse.Status.ATTACHED).build();
    myTable.insertOrUpdateProcess(session, process);
    myTable.updateAgentStatus(session, process, status);

    // Double-check status has been set.
    Profiler.AgentStatusRequest request =
      Profiler.AgentStatusRequest.newBuilder().setProcessId(process.getPid()).setSession(session).build();
    assertEquals(Profiler.AgentStatusResponse.Status.ATTACHED, myTable.getAgentStatus(request).getStatus());

    // Update the process entry and verify that the agent status remains the same.
    process = process.toBuilder().setState(Profiler.Process.State.DEAD).build();
    myTable.insertOrUpdateProcess(session, process);
    assertEquals(Profiler.AgentStatusResponse.Status.ATTACHED, myTable.getAgentStatus(request).getStatus());
  }
}