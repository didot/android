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

import com.android.tools.profiler.proto.Common;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Interface a {@link com.android.tools.datastore.ServicePassThrough} object returns to indicate this object is
 * storing results in a database.
 */
public abstract class DatastoreTable<T extends Enum> {
  private static final Logger LOG = Logger.getInstance(DatastoreTable.class.getCanonicalName());
  private static final long KEYS_ERROR = -1;

  private Connection myConnection;
  private ThreadLocal<Map<T, PreparedStatement>> myStatementMap = new ThreadLocal<>();
  protected final Map<Common.Session, Long> mySessionIdLookup;

  public DatastoreTable(@NotNull Map<Common.Session, Long> sesstionIdLookup) {
    mySessionIdLookup = sesstionIdLookup;
  }

  /**
   * Initialization function to create tables for the Database.
   *
   * @param connection an open connection to the database.
   */
  public void initialize(@NotNull Connection connection) {
    myConnection = connection;
  }

  /**
   * Helper function called after initialize to create {@link PreparedStatement} the implementor should cache
   * the statements for later use.
   */
  public abstract void prepareStatements();

  @NotNull
  protected Map<T, PreparedStatement> getStatementMap() {
    if (myStatementMap.get() == null) {
      myStatementMap.set(new HashMap<>());
      prepareStatements();
    }
    return myStatementMap.get();
  }

  protected void createTable(@NotNull String table, String... columns) throws SQLException {
    myConnection.createStatement().execute(String.format("DROP TABLE IF EXISTS %s ", table));
    StringBuilder statement = new StringBuilder();
    statement.append(String.format("CREATE TABLE %s", table));
    executeUniqueStatement(statement, columns);
  }

  protected void createUniqueIndex(@NotNull String table, String... indexList) throws SQLException {
    StringBuilder statement = new StringBuilder();
    statement.append(String.format("CREATE UNIQUE INDEX IF NOT EXISTS idx_%s_pk ON %s", table, table));
    executeUniqueStatement(statement, indexList);
  }

  protected void createIndex(@NotNull String table, int indexId, String... indexList) throws SQLException {
    StringBuilder statement = new StringBuilder();
    statement.append(String.format("CREATE INDEX IF NOT EXISTS idx_%s_%d_pk ON %s", table, indexId, table));
    executeUniqueStatement(statement, indexList);
  }

  private void executeUniqueStatement(@NotNull StringBuilder statement, @NotNull String[] params) throws SQLException {
    myConnection.createStatement().execute(String.format("%s ( %s )", statement, String.join(",", params)));
  }

  protected void createStatement(@NotNull T statement, @NotNull String stmt) throws SQLException {
    getStatementMap().put(statement, myConnection.prepareStatement(stmt));
  }

  protected void createStatement(@NotNull T statement, @NotNull String stmt, int statementFlags) throws SQLException {
    getStatementMap().put(statement, myConnection.prepareStatement(stmt, statementFlags));
  }

  protected void execute(@NotNull T statement, Object... params) {
    try {
      PreparedStatement stmt = getStatementMap().get(statement);
      applyParams(stmt, params);
      stmt.execute();
    }
    catch (SQLException ex) {
      LOG.error(ex);
    }
  }

  protected long executeWithGeneratedKeys(@NotNull T statement, Object... params) {
    try {
      PreparedStatement stmt = getStatementMap().get(statement);
      applyParams(stmt, params);
      stmt.execute();
      return stmt.getGeneratedKeys().getLong(1);
    }
    catch (SQLException ex) {
      LOG.error(ex);
    }
    return KEYS_ERROR;
  }

  protected ResultSet executeQuery(@NotNull T statement, Object... params) throws SQLException {
    PreparedStatement stmt = getStatementMap().get(statement);
    applyParams(stmt, params);
    return stmt.executeQuery();
  }

  protected void applyParams(@NotNull PreparedStatement statement, Object... params) throws SQLException {
    for (int i = 0; params != null && i < params.length; i++) {
      if (params[i] == null) {
        continue;
      }
      else if (params[i] instanceof String) {
        statement.setString(i + 1, (String)params[i]);
      }
      else if (params[i] instanceof Integer) {
        statement.setLong(i + 1, (int)params[i]);
      }
      else if (params[i] instanceof Long) {
        statement.setLong(i + 1, (long)params[i]);
      }
      else if (params[i] instanceof byte[]) {
        statement.setBytes(i + 1, (byte[])params[i]);
      }
      else if (params[i] instanceof Common.Session) {
        Common.Session session = (Common.Session)params[i];
        if (mySessionIdLookup.containsKey(session)) {
          statement.setLong(i + 1, mySessionIdLookup.get(session));
        }
        else {
          // TODO: Throw exception if a user attempts to insert / update a session id that is invalid
          LOG.warn("Session not found: " + params[i]);
          statement.setLong(i + 1, KEYS_ERROR);
        }
      }
      else {
        //Not implemented type cast
        assert false;
      }
    }
  }
}
