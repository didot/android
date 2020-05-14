/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.sqlite.controllers

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.cancelOnDispose
import com.android.tools.idea.concurrency.catching
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.model.DatabaseInspectorModel
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.model.createSqliteStatement
import com.android.tools.idea.sqlite.sqlLanguage.hasParsingError
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorView
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.Futures.immediateFailedFuture
import com.google.common.util.concurrent.ListenableFuture
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.lang.IllegalStateException
import java.util.concurrent.Executor

/**
 * Implementation of the application logic related to running queries and updates on a sqlite database.
 *
 * All methods are assumed to run on the UI (EDT) thread.
 */
@UiThread
class SqliteEvaluatorController(
  private val project: Project,
  private val model: DatabaseInspectorModel,
  private val view: SqliteEvaluatorView,
  override val closeTabInvoked: () -> Unit,
  private val edtExecutor: Executor,
  private val taskExecutor: Executor
) : DatabaseInspectorController.TabController {
  private var currentTableController: TableController? = null
  private val sqliteEvaluatorViewListener: SqliteEvaluatorView.Listener = SqliteEvaluatorViewListenerImpl()
  private val listeners = mutableListOf<Listener>()
  private val openDatabases = mutableListOf<SqliteDatabaseId>()
  // database currently active in combobox + current text in textfield
  private var currentEvaluationParams: EvaluationParams = EvaluationParams(null, "")

  // database + query that were used for last query/exec
  private var lastUsedEvaluationParams : EvaluationParams? = null

  private val modelListener = object : DatabaseInspectorModel.Listener {
    override fun onDatabasesChanged(openDatabaseIds: List<SqliteDatabaseId>, closeDatabaseIds: List<SqliteDatabaseId>) {
      openDatabases.clear()
      openDatabases.addAll(openDatabaseIds.sortedBy { it.name })

      if (currentEvaluationParams.databaseId !in openDatabaseIds) {
        currentEvaluationParams =  currentEvaluationParams.copy(databaseId = openDatabases.firstOrNull())
      }
      if (lastUsedEvaluationParams?.databaseId !in openDatabaseIds) {
        resetTable()
      }

      view.setDatabases(ArrayList(openDatabases), currentEvaluationParams.databaseId)
      updateRunSqliteStatementButtonState()
    }

    override fun onSchemaChanged(databaseId: SqliteDatabaseId, oldSchema: SqliteSchema, newSchema: SqliteSchema) {
      view.schemaChanged(databaseId)
    }
  }

  fun setUp(evaluationParams: EvaluationParams? = null) {
    view.addListener(sqliteEvaluatorViewListener)
    view.tableView.setEditable(false)
    model.addListener(modelListener)
    if (evaluationParams != null) {
      val statement = createSqliteStatement(project, evaluationParams.statementText)
      // we don't want to automatically run a statement that can modify a database
      if (evaluationParams.databaseId in openDatabases && statement.isQueryStatement) {
        showAndExecuteSqlStatement(evaluationParams.databaseId!!, statement)
      }
      else {
        currentEvaluationParams = currentEvaluationParams.copy(statementText = evaluationParams.statementText)
        view.showSqliteStatement(currentEvaluationParams.statementText)
      }
    }
  }

  override fun refreshData(): ListenableFuture<Unit> {
    return currentTableController?.refreshData() ?: Futures.immediateFuture(Unit)
  }

  override fun notifyDataMightBeStale() {
    currentTableController?.notifyDataMightBeStale()
  }

  override fun dispose() {
    view.removeListener(sqliteEvaluatorViewListener)
    listeners.clear()
    model.removeListener(modelListener)
  }

  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }

  fun removeListeners() {
    listeners.clear()
  }

  fun showAndExecuteSqlStatement(databaseId: SqliteDatabaseId, sqliteStatement: SqliteStatement): ListenableFuture<Unit> {
    if (databaseId !in openDatabases) {
      return immediateFailedFuture(IllegalStateException("Can't evaluate SQLite statement, unknown database: '${databaseId?.path}'"))
    }

    currentEvaluationParams = EvaluationParams(databaseId, sqliteStatement.sqliteStatementWithInlineParameters)
    view.showSqliteStatement(sqliteStatement.sqliteStatementWithInlineParameters)
    view.setDatabases(ArrayList(openDatabases), currentEvaluationParams.databaseId)
    return executeSqlStatement(databaseId!!, sqliteStatement)
  }

  fun saveEvaluationParams(): EvaluationParams {
    // prefer newly entered data over last evaluated
    val databaseId = lastUsedEvaluationParams?.takeIf { currentEvaluationParams.statementText == it.statementText }?.databaseId
    return EvaluationParams(databaseId, currentEvaluationParams.statementText)
  }

  private fun executeSqlStatement(databaseId: SqliteDatabaseId, sqliteStatement: SqliteStatement): ListenableFuture<Unit> {
    resetTable()
    val databaseConnection = model.getDatabaseConnection(databaseId)!!
    lastUsedEvaluationParams = EvaluationParams(databaseId, sqliteStatement.sqliteStatementWithInlineParameters)
    return if (sqliteStatement.isQueryStatement) {
      runQuery(databaseConnection, sqliteStatement)
    }
    else {
      runUpdate(databaseId, databaseConnection, sqliteStatement)
    }
  }

  private fun resetTable() {
    if (lastUsedEvaluationParams != null) {
      lastUsedEvaluationParams = null
      view.tableView.resetView()
    }
    if (currentTableController != null) {
      Disposer.dispose(currentTableController!!)
      currentTableController = null
    }
  }

  private fun updateRunSqliteStatementButtonState() {
    view.setRunSqliteStatementEnabled(currentEvaluationParams.databaseId != null && !hasParsingError(project, currentEvaluationParams.statementText))
  }

  private fun runQuery(databaseConnection: DatabaseConnection, sqliteStatement: SqliteStatement): ListenableFuture<Unit> {
    currentTableController = TableController(
      closeTabInvoked = closeTabInvoked,
      project = project,
      view = view.tableView,
      tableSupplier = { null },
      databaseConnection = databaseConnection,
      sqliteStatement = sqliteStatement,
      edtExecutor = edtExecutor,
      taskExecutor = taskExecutor
    )
    Disposer.register(this@SqliteEvaluatorController, currentTableController!!)
    return currentTableController!!.setUp()
  }

  private fun runUpdate(
    databaseId: SqliteDatabaseId,
    databaseConnection: DatabaseConnection,
    sqliteStatement: SqliteStatement
  ): ListenableFuture<Unit> {
    return databaseConnection.execute(sqliteStatement)
      .transform(edtExecutor) {
        view.tableView.setEmptyText("The statement was run successfully.")
        listeners.forEach { it.onSqliteStatementExecuted(databaseId) }
      }.catching(edtExecutor, Throwable::class.java) { throwable ->
        view.tableView.setEmptyText("An error occurred while running the statement.")
        view.tableView.reportError("Error executing SQLite statement", throwable)
      }.cancelOnDispose(this)
  }

  private inner class SqliteEvaluatorViewListenerImpl : SqliteEvaluatorView.Listener {
    override fun evaluateCurrentStatement() {
      DatabaseInspectorAnalyticsTracker.getInstance(project).trackStatementExecuted(
        AppInspectionEvent.DatabaseInspectorEvent.StatementContext.USER_DEFINED_STATEMENT_CONTEXT
      )
      executeSqlStatement(currentEvaluationParams.databaseId!!, createSqliteStatement(project, currentEvaluationParams.statementText))
    }

    override fun sqliteStatementTextChangedInvoked(newSqliteStatement: String) {
      currentEvaluationParams = currentEvaluationParams.copy(statementText = newSqliteStatement)
      updateRunSqliteStatementButtonState()
    }

    override fun onDatabaseSelected(databaseId: SqliteDatabaseId) {
      currentEvaluationParams = currentEvaluationParams.copy(databaseId = databaseId)
    }
  }

  interface Listener {
    /**
     * Called when an user-defined SQLite statement is successfully executed
     * @param databaseId The database on which the statement was executed.
     * */
    fun onSqliteStatementExecuted(databaseId: SqliteDatabaseId)
  }

  data class EvaluationParams(val databaseId: SqliteDatabaseId?, val statementText: String)
}

private val SqliteStatement.isQueryStatement
  get() = statementType == SqliteStatementType.SELECT || statementType == SqliteStatementType.EXPLAIN