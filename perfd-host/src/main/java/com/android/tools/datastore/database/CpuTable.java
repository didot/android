/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler.*;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profiler.protobuf3jarjar.InvalidProtocolBufferException;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CpuTable extends DataStoreTable<CpuTable.CpuStatements> {
  private static final int DATA_COLUMN = 1;

  /**
   * Profiler type column number when querying trace data.
   */
  private static final int PROFILER_TYPE_COLUMN_TRACE_DATA = 2;

  public enum CpuStatements {
    INSERT_THREAD_ACTIVITY,
    QUERY_THREAD_ACTIVITIES,
    INSERT_CPU_DATA,
    QUERY_CPU_DATA,
    QUERY_TRACE_INFO,
    FIND_TRACE_DATA,
    INSERT_TRACE_DATA,
    INSERT_TRACE_INFO,
    INSERT_PROFILING_STATE,
    QUERY_PROFILING_STATE,
  }

  @Override
  public void initialize(@NotNull Connection connection) {
    super.initialize(connection);
    try {
      createTable("Cpu_Data",
                  "Session INTEGER NOT NULL",
                  "Timestamp INTEGER NOT NULL",
                  "Data BLOB");
      createTable("Thread_Activities",
                  "Session INTEGER NOT NULL", "ThreadId INTEGER NOT NULL", "Timestamp INTEGER",
                  "State TEXT, Name TEXT");
      createTable("Cpu_Trace",
                  // TraceId is unique within an app, so just traceId is not enough
                  "Session INTEGER NOT NULL",
                  "TraceId INTEGER NOT NULL",
                  // We need profilerType to choose parser
                  "ProfilerType TEXT",
                  "Data BLOB");
      createTable("Cpu_Trace_Info",
                  "Session INTEGER NOT NULL",
                  "StartTime INTEGER",
                  "EndTime INTEGER",
                  "TraceInfo BLOB");
      createTable("Profiling_State",
                  "Session INTEGER NOT NULL",
                  "Timestamp INTEGER NOT NULL",
                  "Data BLOB");
      createUniqueIndex("Cpu_Data", "Session", "Timestamp");
      createUniqueIndex("Cpu_Trace", "Session", "TraceId");
      createUniqueIndex("Thread_Activities", "Session", "ThreadId", "Timestamp");
      createUniqueIndex("Profiling_State", "Session", "Timestamp");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  @Override
  public void prepareStatements() {
    try {
      createStatement(CpuTable.CpuStatements.INSERT_CPU_DATA,
                      "INSERT OR REPLACE INTO Cpu_Data (Session, Timestamp, Data) values (?, ?, ?)");
      createStatement(CpuTable.CpuStatements.QUERY_CPU_DATA,
                      "SELECT Data from Cpu_Data WHERE Session = ? AND Timestamp > ? AND Timestamp <= ? ");
      createStatement(CpuTable.CpuStatements.QUERY_TRACE_INFO,
                      "SELECT TraceInfo from Cpu_Trace_Info WHERE " +
                      "Session = ? AND ((StartTime < ? AND ? <= EndTime) OR (StartTime > ? AND EndTime = 0));");
      createStatement(CpuTable.CpuStatements.FIND_TRACE_DATA,
                      "SELECT Data, ProfilerType from Cpu_Trace WHERE Session = ? AND TraceId = ?");
      createStatement(CpuTable.CpuStatements.INSERT_TRACE_DATA,
                      "INSERT INTO Cpu_Trace (Session, TraceId, ProfilerType, Data) values (?, ?, ?, ?)");
      createStatement(CpuTable.CpuStatements.INSERT_TRACE_INFO,
                      "INSERT OR REPLACE INTO Cpu_Trace_Info (Session, StartTime, EndTime, TraceInfo) values (?, ?, ?, ?)");
      createStatement(CpuTable.CpuStatements.INSERT_THREAD_ACTIVITY,
                      "INSERT OR REPLACE INTO Thread_Activities " +
                      "(Session, ThreadId, Timestamp, State, Name) VALUES (?, ?, ?, ?, ?)");
      createStatement(CpuTable.CpuStatements.QUERY_THREAD_ACTIVITIES,
                      // First make sure to fetch the states of all threads that were alive at request's start timestamp
                      "SELECT t1.ThreadId, t1.Name, t1.State, ? as ReqStart FROM Thread_Activities AS t1 " +
                      "JOIN (SELECT ThreadId, MAX(Timestamp) AS Timestamp " +
                      "FROM Thread_Activities WHERE Session = ? AND Timestamp <= ? GROUP BY ThreadId) AS t2 " +
                      "ON t1.ThreadId = t2.ThreadId AND t1.Timestamp = t2.Timestamp AND t1.State <> 'DEAD' " +
                      "UNION ALL " +
                      // Then fetch all the activities that happened in the request interval
                      "SELECT ThreadId, Name, State, Timestamp FROM Thread_Activities " +
                      "WHERE Session = ? AND Timestamp > ? AND Timestamp <= ?;");
      createStatement(CpuTable.CpuStatements.INSERT_PROFILING_STATE,
                      "INSERT INTO Profiling_State (Session, Timestamp, Data) values (?, ?, ?)");
      createStatement(CpuTable.CpuStatements.QUERY_PROFILING_STATE,
                      "SELECT Data from Profiling_State WHERE Session = ? ORDER BY Timestamp DESC LIMIT 1 ");
    }
    catch (SQLException ex) {
      onError(ex);
    }
  }

  public void insert(Common.Session session, CpuUsageData data) {
    execute(CpuStatements.INSERT_CPU_DATA, session.getSessionId(), data.getEndTimestamp(), data.toByteArray());
  }

  public List<CpuUsageData> getCpuDataByRequest(CpuDataRequest request) {
    List<CpuUsageData> cpuData = new ArrayList<>();
    try {
      ResultSet results =
        executeQuery(CpuStatements.QUERY_CPU_DATA, request.getSession().getSessionId(), request.getStartTimestamp(),
                     request.getEndTimestamp());
      while (results.next()) {
        CpuUsageData.Builder data = CpuUsageData.newBuilder();
        data.mergeFrom(results.getBytes(DATA_COLUMN));
        cpuData.add(data.build());
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }
    return cpuData;
  }

  public void insertActivities(Common.Session session,
                               int tid,
                               String name,
                               List<GetThreadsResponse.ThreadActivity> activities) {
    for (GetThreadsResponse.ThreadActivity activity : activities) {
      // TODO: optimize it by adding the states in batches
      execute(CpuStatements.INSERT_THREAD_ACTIVITY, session.getSessionId(), tid, activity.getTimestamp(), activity.getNewState().toString(),
              name);
    }
  }

  public void insertSnapshot(Common.Session session,
                             long timestamp,
                             List<GetThreadsResponse.ThreadSnapshot.Snapshot> snapshots) {
    // For now, insert it as activity. TODO: differentiate the concepts of snapshot and activity
    for (GetThreadsResponse.ThreadSnapshot.Snapshot snapshot : snapshots) {
      execute(CpuStatements.INSERT_THREAD_ACTIVITY,
              session.getSessionId(), snapshot.getTid(), timestamp, snapshot.getState().toString(), snapshot.getName());
    }
  }

  public List<GetThreadsResponse.Thread> getThreadsDataByRequest(GetThreadsRequest request) {
    // Use a TreeMap to preserve the threads sorting order (by tid)
    Map<Integer, GetThreadsResponse.Thread.Builder> threads = new TreeMap<>();
    try {
      ResultSet activities = executeQuery(CpuStatements.QUERY_THREAD_ACTIVITIES,
                                          // Used as the timestamp of the states that happened before the request
                                          request.getStartTimestamp(),
                                          request.getSession().getSessionId(),
                                          // Used to get the the states that happened before the request
                                          request.getStartTimestamp(),
                                          request.getSession().getSessionId(),
                                          // The start and end timestamps below are used to get the activities that
                                          // happened in the interval (start, end]
                                          request.getStartTimestamp(),
                                          request.getEndTimestamp());
      while (activities.next()) {
        // Thread id should be the first column
        int tid = activities.getInt(1);
        if (!threads.containsKey(tid)) {
          // Thread name should be the second column
          GetThreadsResponse.Thread.Builder thread = createThreadBuilder(tid, activities.getString(2));
          threads.put(tid, thread);
        }
        // State should be the third column
        GetThreadsResponse.State state = GetThreadsResponse.State.valueOf(activities.getString(3));
        // Timestamp should be the fourth column
        GetThreadsResponse.ThreadActivity.Builder activity =
          GetThreadsResponse.ThreadActivity.newBuilder().setNewState(state).setTimestamp(activities.getLong(4));
        threads.get(tid).addActivities(activity.build());
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }

    // Add all threads that should be included in the response.
    List<GetThreadsResponse.Thread> cpuData = new ArrayList<>();
    for (GetThreadsResponse.Thread.Builder thread : threads.values()) {
      cpuData.add(thread.build());
    }
    return cpuData;
  }

  public List<TraceInfo> getTraceInfo(GetTraceInfoRequest request) {
    List<TraceInfo> traceInfo = new ArrayList<>();
    try {
      ResultSet results =
        executeQuery(CpuStatements.QUERY_TRACE_INFO, request.getSession().getSessionId(), request.getToTimestamp(),
                     request.getFromTimestamp(),
                     request.getFromTimestamp());
      while (results.next()) {
        // QUERY_TRACE_INFO will return only one column.
        byte[] data = results.getBytes(1);
        if (data != null) {
          traceInfo.add(TraceInfo.parseFrom(data));
        }
      }
    }
    catch (SQLException | InvalidProtocolBufferException ex) {
      onError(ex);
    }

    return traceInfo;
  }

  public TraceData getTraceData(Common.Session session, int traceId) {
    try {
      ResultSet results = executeQuery(CpuStatements.FIND_TRACE_DATA, session.getSessionId(), traceId);
      if (results.next()) {
        byte[] data = results.getBytes(DATA_COLUMN);
        if (data != null) {
          CpuProfilerType profilerType =
            CpuProfilerType.valueOf(results.getString(PROFILER_TYPE_COLUMN_TRACE_DATA));
          return new TraceData(ByteString.copyFrom(data), profilerType);
        }
      }
    }
    catch (SQLException ex) {
      onError(ex);
    }
    return null;
  }

  public void insertTrace(Common.Session session, int traceId, CpuProfilerType profilerType, ByteString data) {
    execute(CpuStatements.INSERT_TRACE_DATA, session.getSessionId(), traceId, profilerType.toString(), data.toByteArray());
  }

  public void insertTraceInfo(Common.Session session, TraceInfo trace) {
    execute(CpuStatements.INSERT_TRACE_INFO, session.getSessionId(), trace.getFromTimestamp(), trace.getToTimestamp(), trace.toByteArray());
  }

  public void insertProfilingStateData(Common.Session session, ProfilingStateResponse data) {
    execute(CpuStatements.INSERT_PROFILING_STATE, session.getSessionId(), data.getCheckTimestamp(), data.toByteArray());
  }

  public ProfilingStateResponse getProfilingStateData(Common.Session session) {
    try {
      ResultSet results = executeQuery(CpuStatements.QUERY_PROFILING_STATE, session.getSessionId());
      if (results.next()) {
        return ProfilingStateResponse.parseFrom(results.getBytes(DATA_COLUMN));
      }
    }
    catch (InvalidProtocolBufferException | SQLException ex) {
      onError(ex);
    }
    return null;
  }

  private static GetThreadsResponse.Thread.Builder createThreadBuilder(int tid, String name) {
    GetThreadsResponse.Thread.Builder thread = GetThreadsResponse.Thread.newBuilder();
    thread.setTid(tid);
    thread.setName(name);
    return thread;
  }

  /**
   * Trace data wrapper that contains the trace bytes and the profiler type (e.g. simpleperf, ART) used to generate it.
   */
  public static class TraceData {
    private final ByteString myTraceBytes;
    private final CpuProfilerType myProfilerType;

    public TraceData(ByteString traceBytes, CpuProfilerType profilerType) {
      myTraceBytes = traceBytes;
      myProfilerType = profilerType;
    }

    public ByteString getTraceBytes() {
      return myTraceBytes;
    }

    public CpuProfilerType getProfilerType() {
      return myProfilerType;
    }
  }
}
