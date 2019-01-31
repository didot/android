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
package com.android.tools.datastore.database;

import com.android.annotations.VisibleForTesting;
import com.android.tools.datastore.DeviceId;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.Event;
import com.android.tools.profiler.proto.Transport.AgentStatusRequest;
import com.android.tools.profiler.proto.Transport.BytesRequest;
import com.android.tools.profiler.proto.Transport.BytesResponse;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profiler.proto.Transport.GetDevicesResponse;
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetProcessesRequest;
import com.android.tools.profiler.proto.Transport.GetProcessesResponse;
import com.android.tools.profiler.protobuf3jarjar.InvalidProtocolBufferException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnifiedEventsTable extends DataStoreTable<UnifiedEventsTable.Statements> {
  public enum Statements {
    // Since no data should be updated after it has been inserted we drop any duplicated request from the poller.
    INSERT_EVENT(
      "INSERT OR IGNORE INTO [UnifiedEventsTable] (StreamId, ProcessId, GroupId, Kind, Timestamp, Data) VALUES (?, ?, ?, ?, ?, ?)"),
    // Only used for test.
    QUERY_EVENTS("SELECT Data FROM [UnifiedEventsTable]"),
    INSERT_DEVICE("INSERT OR REPLACE INTO [DevicesTable] (DeviceId, Data) values (?, ?)"),
    INSERT_PROCESS("INSERT OR REPLACE INTO [ProcessesTable] (DeviceId, ProcessId, Name, State, StartTime, Arch) " +
                   "values (?, ?, ?, ?, ?, ?)"),
    UPDATE_PROCESS_STATE("UPDATE [ProcessesTable] Set State = ? WHERE DeviceId = ? AND ProcessId = ?"),
    SELECT_PROCESSES("SELECT DeviceId, ProcessId, Name, State, StartTime, Arch from [ProcessesTable] WHERE DeviceId = ?"),
    SELECT_PROCESS_BY_ID("SELECT ProcessId from [ProcessesTable] WHERE DeviceId = ? AND ProcessId = ?"),
    SELECT_DEVICE("SELECT Data from [DevicesTable]"),
    FIND_AGENT_STATUS("SELECT AgentStatus from [ProcessesTable] WHERE DeviceId = ? AND ProcessId = ?"),
    UPDATE_AGENT_STATUS("UPDATE [ProcessesTable] SET AgentStatus = ? WHERE DeviceId = ? AND ProcessId = ?"),
    INSERT_BYTES("INSERT OR IGNORE INTO [BytesTable] (StreamId, Id, Data) VALUES (?, ?, ?)"),
    GET_BYTES("SELECT Data FROM [BytesTable] WHERE StreamId = ? AND Id = ?");

    @NotNull private final String mySqlStatement;

    Statements(@NotNull String sqlStatement) {
      mySqlStatement = sqlStatement;
    }

    @NotNull
    public String getStatement() {
      return mySqlStatement;
    }
  }

  @Override
  public void prepareStatements() {
    try {
      for (Statements statement : Statements.values()) {
        createStatement(statement, statement.getStatement());
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      createTable("UnifiedEventsTable",
                  "StreamId INTEGER NOT NULL", // Optional filter, required for all data.
                  "ProcessId INTEGER NOT NULL", // Optional filter, not required for data (eg device/process).
                  "GroupId INTEGER NOT NULL", // Optional filter, not required for data.
                  "Kind INTEGER NOT NULL", // Required filter, required for all data.
                  "Timestamp INTEGER NOT NULL", // Optional filter, required for all data.
                  "Data BLOB");
      createTable("DevicesTable", "DeviceId INTEGER", "Data BLOB");
      createTable("ProcessesTable", "DeviceId INTEGER", "ProcessId INTEGER", "Name STRING NOT NULL", "State INTEGER",
                  "StartTime INTEGER", "Arch STRING NOT NULL", "AgentStatus INTEGER");
      createTable("BytesTable", "StreamId INTEGER NOT NULL", "Id STRING NOT NULL", "Data BLOB");
      createUniqueIndex("UnifiedEventsTable", "StreamId", "ProcessId", "GroupId", "Kind", "Timestamp");
      createUniqueIndex("UnifiedEventsTable", "StreamId", "Kind", "Timestamp");
      createUniqueIndex("DevicesTable", "DeviceId");
      createUniqueIndex("ProcessesTable", "DeviceId", "ProcessId");
      createUniqueIndex("BytesTable", "StreamId", "Id");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  public void insertUnifiedEvent(long streamId, @NotNull Event event) {
    execute(Statements.INSERT_EVENT,
            streamId,
            event.getPid(),
            event.getGroupId(),
            event.getKind().getNumber(),
            event.getTimestamp(),
            event.toByteArray());
  }

  @VisibleForTesting
  public List<Event> queryUnifiedEvents() {
    return queryUnifiedEvents(Statements.QUERY_EVENTS);
  }

  /**
   * Queries for set of events then groups them by {@link Event#getGroupId()}
   * <p>
   * The query filters data on {@link Event#getKind()}, {@link Event#getSessionId()}, {@link Event#getGroupId()},
   * and {@link Event#getTimestamp()}.
   * <p>
   * The timestamp is filtered by the optional parameters of {@link GetEventGroupsRequest#getFromTimestamp()} and
   * {@link GetEventGroupsRequest#getToTimestamp()}.
   * <p>
   * If the parameter {@link GetEventGroupsRequest#getFromTimestamp()} is supplied then in addition to all events after the
   * supplied timestamp the latest event before the timestamp is also returned. The exception to this is if the
   * {@link Event#getIsEnded()} returns true, then these events are discarded and no X-1 event is returned.
   * <p>
   * If the parameter {@link GetEventGroupsRequest#getToTimestamp()} is supplied then in addition to all events before the
   * supplied timestamp the first event after the timestamp is also returned. The exception to this is if the event at X+1 does not
   * previously have any elements in its group this event is not returned.
   * <p>
   * FromTimestamp X - ToTimestamp Y Example
   * <p>
   * Events
   *  1:  i----i----i-----i
   *  2:----e
   *  3:               i---
   *  4:       i-----i
   *  5:  i---------------
   *
   *  Query: X -> Y
   *          x-----y
   *  Results:
   *  1:  i----i----i-----i
   *  4:       i-----i
   *  5:  i---------------
   * Note: Group 1 has all elements returned due to +1/-1 behavior.
   * Note: Group 2 and group 3 do not get returned. Group 2 only has an end event before our from timestamp, while Group 3 only has data
   * after.
   * Note: Group 5 gets returned as it has a single event before our from timestamp that does not ended, or ends after our to timestamp.
   *
   * @param request
   */
  public List<EventGroup> queryUnifiedEventGroups(@NotNull GetEventGroupsRequest request) {
    ArrayList<Object> baseParams = new ArrayList<>();
    List<Object> beforeRangeParams = null;
    List<Object> afterRangeParams = null;

    HashMap<Long, EventGroup.Builder> builderGroups = new HashMap<>();
    // The string format allows for altering the group by results for +1 and -1 queries.
    String sql = "SELECT [Data]%s From [UnifiedEventsTable] WHERE Kind = ? %s";
    StringBuilder filter = new StringBuilder();
    baseParams.add(request.getKind().getNumber());

    if (request.getStreamId() != 0) {
      filter.append(" AND StreamId = ?");
      baseParams.add(request.getStreamId());
    }

    if (request.getPid() != 0) {
      filter.append(" AND ProcessId = ?");
      baseParams.add(request.getPid());
    }

    if (request.getGroupId() != 0) {
      filter.append(" AND GroupId = ?");
      baseParams.add(request.getGroupId());
    }

    String sqlBefore = String.format(sql, ", MAX(Timestamp), MAX(ROWID)", filter.toString() + " AND Timestamp < ? GROUP BY GroupId");
    String sqlAfter = String.format(sql, ", MIN(Timestamp), MIN(ROWID)", filter.toString() + " AND Timestamp > ? GROUP BY GroupId");
    ArrayList<Object> inRangeQueryParams = new ArrayList<>(baseParams);

    if (request.getFromTimestamp() != 0) {
      beforeRangeParams = new ArrayList<>(baseParams);
      beforeRangeParams.add(request.getFromTimestamp());
      filter.append(" AND Timestamp >= ?");
      inRangeQueryParams.add(request.getFromTimestamp());
    }

    if (request.getToTimestamp() != 0) {
      afterRangeParams = new ArrayList<>(baseParams);
      afterRangeParams.add(request.getToTimestamp());
      filter.append(" AND Timestamp <= ?");
      inRangeQueryParams.add(request.getToTimestamp());
    }

    // Gather before range events if needed.
    // Query before example:
    // SELECT [Data], MAX(Timestamp), MAX(ROWID) From [UnifiedEventsTable] WHERE Kind = ? AND Timestamp < ? GROUP BY GroupId;
    if (beforeRangeParams != null) {
      gatherEvents(sqlBefore, beforeRangeParams, builderGroups, (event) -> !event.getIsEnded());
    }

    // Query example:
    // SELECT [Data] From [UnifiedEventsTable] WHERE Kind = ? AND Timestamp >= ? AND Timestamp <= ?;
    String query = String.format(sql, "", filter.toString());
    gatherEvents(query, inRangeQueryParams, builderGroups, (unused) -> true);

    // Gather after range events if needed.
    // Query after example:
    // SELECT [Data], MIN(Timestamp), MIN(ROWID) From [UnifiedEventsTable] WHERE Kind = ? AND Timestamp > ? GROUP BY GroupId;
    if (afterRangeParams != null) {
      gatherEvents(sqlAfter, afterRangeParams, builderGroups, (event) -> builderGroups.containsKey(event.getGroupId()));
    }

    List<EventGroup> groups = new ArrayList<>();
    builderGroups.values().forEach((builder) -> {
      groups.add(builder.build());
    });
    return groups;
  }


  @NotNull
  public GetDevicesResponse getDevices() {
    if (isClosed()) {
      return GetDevicesResponse.getDefaultInstance();
    }

    GetDevicesResponse.Builder responseBuilder = GetDevicesResponse.newBuilder();
    try {
      ResultSet results = executeQuery(Statements.SELECT_DEVICE);
      while (results.next()) {
        responseBuilder.addDevice(Common.Device.parseFrom(results.getBytes(1)));
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }
    return responseBuilder.build();
  }

  @NotNull
  public GetProcessesResponse getProcesses(@NotNull GetProcessesRequest request) {
    if (isClosed()) {
      return GetProcessesResponse.getDefaultInstance();
    }

    GetProcessesResponse.Builder responseBuilder = GetProcessesResponse.newBuilder();
    try {
      ResultSet results = executeQuery(Statements.SELECT_PROCESSES, request.getDeviceId());
      while (results.next()) {
        long deviceId = results.getLong(1);
        int pid = results.getInt(2);
        String name = results.getString(3);
        int state = results.getInt(4);
        long startTimeNs = results.getLong(5);
        String arch = results.getString(6);
        Common.Process process = Common.Process.newBuilder()
          .setDeviceId(deviceId)
          .setPid(pid)
          .setName(name)
          .setState(Common.Process.State.forNumber(state))
          .setStartTimestampNs(startTimeNs)
          .setAbiCpuArch(arch)
          .build();
        responseBuilder.addProcess(process);
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
    return responseBuilder.build();
  }

  public void insertOrUpdateDevice(@NotNull Common.Device device) {
    // TODO: Update start/end times with times polled from device.
    // End time always equals now, start time comes from device. This way if we get disconnected we still have an accurate end time.
    execute(Statements.INSERT_DEVICE, device.getDeviceId(), device.toByteArray());
  }

  public void insertOrUpdateProcess(@NotNull DeviceId devicdId, @NotNull Common.Process process) {
    try {
      ResultSet results = executeQuery(Statements.SELECT_PROCESS_BY_ID, devicdId.get(), process.getPid());
      if (results.next()) {
        execute(Statements.UPDATE_PROCESS_STATE, process.getStateValue(), devicdId.get(), process.getPid());
      }
      else {
        execute(Statements.INSERT_PROCESS, devicdId.get(), process.getPid(), process.getName(), process.getStateValue(),
                process.getStartTimestampNs(), process.getAbiCpuArch());
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  /**
   * NOTE: Currently an assumption is made such that the agent lives and dies along with the process it is attached to.
   * If for some reason the agent freezes and we stop receiving a valid heartbeat momentarily, this will not downgrade the HasAgent status
   * in the process entry.
   */
  public void updateAgentStatus(@NotNull DeviceId devicdId,
                                @NotNull Common.Process process,
                                @NotNull Common.AgentData agentData) {
    try {
      ResultSet results = executeQuery(Statements.FIND_AGENT_STATUS, devicdId.get(), process.getPid());
      if (results.next()) {
        execute(Statements.UPDATE_AGENT_STATUS, agentData.getStatus().ordinal(),
                devicdId.get(), process.getPid());
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @NotNull
  public Common.AgentData getAgentStatus(@NotNull AgentStatusRequest request) {
    Common.AgentData.Builder responseBuilder = Common.AgentData.newBuilder();
    try {
      ResultSet results = executeQuery(Statements.FIND_AGENT_STATUS, request.getDeviceId(), request.getPid());
      if (results.next()) {
        responseBuilder.setStatusValue(results.getInt(1));
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }

    return responseBuilder.build();
  }

  public void insertBytes(@NotNull long streamId, @NotNull String id, @NotNull BytesResponse response) {
    execute(Statements.INSERT_BYTES, streamId, id, response.toByteArray());
  }

  @Nullable
  public BytesResponse getBytes(@NotNull BytesRequest request) {
    try {
      ResultSet results = executeQuery(Statements.GET_BYTES, request.getStreamId(), request.getId());
      if (results.next()) {
        return BytesResponse.parseFrom(results.getBytes(1));
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }

    return null;
  }

  /**
   * Executes the sql statement and passes each event through the filter. If the filter returns true, the event is added
   * to the hashmap. Otherwise it is ignored.
   *
   * @param sql           Statement to execute and gather a list of events
   * @param params        List of params to pass to the sql statement.
   * @param builderGroups map of event group ids to event groups used to collect events into respective groups.
   * @param filter        predicate to determine which events are included in the event group.
   */
  private void gatherEvents(String sql,
                            List<Object> params,
                            HashMap<Long, EventGroup.Builder> builderGroups,
                            Predicate<Event> filter) {
    try {
      ResultSet results = executeOneTimeQuery(sql, params.toArray());
      while (results.next()) {
        Event event = Event.parser().parseFrom(results.getBytes(1));
        if (!filter.test(event)) {
          continue;
        }
        EventGroup.Builder group =
          builderGroups.computeIfAbsent(event.getGroupId(), key -> EventGroup.newBuilder().setGroupId(key));
        group.addEvents(event);
      }
    }
    catch (SQLException | InvalidProtocolBufferException ex) {
      onError(ex);
    }
  }

  private List<Event> queryUnifiedEvents(Statements stmt, Object... args) {
    List<Event> records = new ArrayList<>();
    try {
      ResultSet results = executeQuery(stmt, args);
      while (results.next()) {
        records.add(Event.parser().parseFrom(results.getBytes(1)));
      }
    }
    catch (SQLException | InvalidProtocolBufferException ex) {
      onError(ex);
    }
    return records;
  }
}
