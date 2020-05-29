/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.sqlite.repository

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.SqliteValue
import com.android.tools.idea.sqlite.model.createSqliteStatement
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.ide.PooledThreadExecutor
import java.util.concurrent.Executor

/**
 * Classed used to access the database
 */
interface DatabaseRepository {
  suspend fun addDatabaseConnection(databaseId: SqliteDatabaseId, databaseConnection: DatabaseConnection)
  suspend fun closeDatabase(databaseId: SqliteDatabaseId)
  suspend fun fetchSchema(databaseId: SqliteDatabaseId): SqliteSchema
  fun runQuery(databaseId: SqliteDatabaseId, sqliteStatement: SqliteStatement): ListenableFuture<SqliteResultSet>
  fun executeStatement(databaseId: SqliteDatabaseId, sqliteStatement: SqliteStatement): ListenableFuture<Unit>
  fun updateTable(
    databaseId: SqliteDatabaseId,
    targetTable: SqliteTable,
    targetRow: SqliteRow,
    targetColumnName: String,
    newValue: SqliteValue
  ): ListenableFuture<Unit>
  suspend fun release()
}

class DatabaseRepositoryImpl(private val project: Project, taskExecutor: Executor = PooledThreadExecutor.INSTANCE) : DatabaseRepository {
  private val workerDispatcher = taskExecutor.asCoroutineDispatcher()
  private val projectScope = AndroidCoroutineScope(project, workerDispatcher)

  private val repositoryChannel = Channel<RepositoryActions>()

  // TODO replace with CoroutineScope.actor once it comes out of experimental phase
  private val job = projectScope.launch {
    val databaseConnections = mutableMapOf<SqliteDatabaseId, DatabaseConnection>()

    while (isActive) {
      when (val action = repositoryChannel.receive()) {
        is RepositoryActions.GetConnection -> {
          val connection = databaseConnections[action.databaseId]
          action.deferredConnection.complete(connection)
        }
        is RepositoryActions.AddConnection -> {
          databaseConnections[action.databaseId] = action.databaseConnection
        }
        is RepositoryActions.CloseConnection -> {
          val connection = databaseConnections.remove(action.databaseId)
          connection?.close()
        }
        is RepositoryActions.CloseAllConnections -> {
          databaseConnections.values.forEach { it.close() }
          databaseConnections.clear()
        }
      }
    }
  }

  override suspend fun addDatabaseConnection(databaseId: SqliteDatabaseId, databaseConnection: DatabaseConnection) = withContext(workerDispatcher) {
    repositoryChannel.send(RepositoryActions.AddConnection(databaseId, databaseConnection))
  }

  override suspend fun closeDatabase(databaseId: SqliteDatabaseId) = withContext(workerDispatcher) {
    repositoryChannel.send(RepositoryActions.CloseConnection(databaseId))
  }

  override suspend fun fetchSchema(databaseId: SqliteDatabaseId): SqliteSchema = withContext(workerDispatcher) {
    val databaseConnection = getDatabaseConnection(databaseId)
    databaseConnection.readSchema().await()
  }

  override fun runQuery(databaseId: SqliteDatabaseId, sqliteStatement: SqliteStatement): ListenableFuture<SqliteResultSet> = projectScope.future {
    val databaseConnection = getDatabaseConnection(databaseId)
    databaseConnection.query(sqliteStatement).await()
  }

  override fun executeStatement(databaseId: SqliteDatabaseId, sqliteStatement: SqliteStatement): ListenableFuture<Unit> = projectScope.future {
    val databaseConnection = getDatabaseConnection(databaseId)
    databaseConnection.execute(sqliteStatement).await()
  }

  override fun updateTable(
    databaseId: SqliteDatabaseId,
    targetTable: SqliteTable,
    targetRow: SqliteRow,
    targetColumnName: String,
    newValue: SqliteValue
  ): ListenableFuture<Unit> = projectScope.future {
    val databaseConnection = getDatabaseConnection(databaseId)
    val whereExpression = getWhereExpression(targetTable, targetRow) ?: error("No primary keys or rowid column")

    val updateStatement =
      "UPDATE ${AndroidSqlLexer.getValidName(targetTable.name)} " +
      "SET ${AndroidSqlLexer.getValidName(targetColumnName)} = ? " +
      "WHERE ${whereExpression.expression}"

    withContext(uiThread) {
      val sqliteStatement = createSqliteStatement(project, updateStatement, listOf(newValue) + whereExpression.parameters)
      withContext(workerDispatcher) {
        databaseConnection.execute(sqliteStatement).await()
      }
    }
  }

  override suspend fun release() = withContext(workerDispatcher) {
    repositoryChannel.send(RepositoryActions.CloseAllConnections)
  }

  private fun getWhereExpression(targetTable: SqliteTable, targetRow: SqliteRow): WhereExpression? {
    val rowIdColumnValue = targetRow.values.firstOrNull { it.columnName == targetTable.rowIdName?.stringName }

    return if (rowIdColumnValue != null) {
      // use rowid
      WhereExpression("${AndroidSqlLexer.getValidName(rowIdColumnValue.columnName)} = ?", listOf(rowIdColumnValue.value))
    } else {
      // use primary key
      val tablePrimaryKeyColumnNames = targetTable.columns.filter { it.inPrimaryKey }.map { it.name }
      val targetRowPrimaryKeyColumnNames = targetRow.values.filter { it.columnName in tablePrimaryKeyColumnNames }

      if (tablePrimaryKeyColumnNames.isEmpty() || tablePrimaryKeyColumnNames.size != targetRowPrimaryKeyColumnNames.size) {
        return null
      }

      val parameters = targetRowPrimaryKeyColumnNames.map { it.value }
      val expression = targetRowPrimaryKeyColumnNames.joinToString(separator = " AND ") {
        "${AndroidSqlLexer.getValidName(it.columnName)} = ?"
      }

      WhereExpression(expression, parameters)
    }
  }

  private suspend fun getDatabaseConnection(databaseId: SqliteDatabaseId): DatabaseConnection = withContext(workerDispatcher) {
    val completableDeferred = CompletableDeferred<DatabaseConnection?>()
    repositoryChannel.send(RepositoryActions.GetConnection(databaseId, completableDeferred))
    completableDeferred.await() ?: error("Database '$databaseId not found")
  }

  private data class WhereExpression(val expression: String, val parameters: List<SqliteValue>)

  private sealed class RepositoryActions {
    data class GetConnection(
      val databaseId: SqliteDatabaseId,
      val deferredConnection: CompletableDeferred<DatabaseConnection?>
    ) : RepositoryActions()
    data class AddConnection(val databaseId: SqliteDatabaseId, val databaseConnection: DatabaseConnection) : RepositoryActions()
    data class CloseConnection(val databaseId: SqliteDatabaseId) : RepositoryActions()
    object CloseAllConnections : RepositoryActions()
  }
}