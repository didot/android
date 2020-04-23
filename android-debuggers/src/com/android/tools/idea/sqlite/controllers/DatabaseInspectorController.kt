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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.DatabaseInspectorProjectService
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.controllers.DatabaseInspectorController.SavedUiState
import com.android.tools.idea.sqlite.databaseConnection.jdbc.selectAllAndRowIdFromTable
import com.android.tools.idea.sqlite.databaseConnection.live.LiveInspectorException
import com.android.tools.idea.sqlite.model.FileSqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.createSqliteStatement
import com.android.tools.idea.sqlite.ui.DatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.ui.mainView.AddColumns
import com.android.tools.idea.sqlite.ui.mainView.AddTable
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorView
import com.android.tools.idea.sqlite.ui.mainView.IndexedSqliteColumn
import com.android.tools.idea.sqlite.ui.mainView.IndexedSqliteTable
import com.android.tools.idea.sqlite.ui.mainView.RemoveColumns
import com.android.tools.idea.sqlite.ui.mainView.RemoveTable
import com.android.tools.idea.sqlite.ui.mainView.SchemaDiffOperation
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.text.trimMiddle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import javax.swing.JComponent

/**
 * Implementation of the application logic related to viewing/editing sqlite databases.
 *
 * All methods are assumed to run on the UI (EDT) thread.
 */
class DatabaseInspectorControllerImpl(
  private val project: Project,
  private val model: DatabaseInspectorController.Model,
  private val viewFactory: DatabaseInspectorViewsFactory,
  private val edtExecutor: Executor,
  private val taskExecutor: Executor
) : DatabaseInspectorController {

  private val uiThread = edtExecutor.asCoroutineDispatcher()
  private val workerThread = taskExecutor.asCoroutineDispatcher()
  private val view = viewFactory.createDatabaseInspectorView(project)
  private val tabsToRestore = mutableListOf<TabDescription>()

  /**
   * Controllers for all open tabs, keyed by id.
   *
   * <p>Multiple tables can be open at the same time in different tabs.
   * This map keeps track of corresponding controllers.
   */
  private val resultSetControllers = mutableMapOf<TabId, DatabaseInspectorController.TabController>()

  private val sqliteViewListener = SqliteViewListenerImpl()

  private var evaluatorTabCount = 0

  private val databaseInspectorAnalyticsTracker = DatabaseInspectorAnalyticsTracker.getInstance(project)

  override val component: JComponent
    get() = view.component

  @UiThread
  override fun setUp() {
    view.addListener(sqliteViewListener)
  }

  override suspend fun addSqliteDatabase(deferredDatabase: Deferred<SqliteDatabase>) = withContext(uiThread) {
    view.startLoading("Getting database...")

    val database = try {
      deferredDatabase.await()
    }
    catch (e: Exception) {
      ensureActive()
      view.reportError("Error getting database", e)
      throw e
    }
    addSqliteDatabase(database)
  }

  override suspend fun addSqliteDatabase(database: SqliteDatabase) = withContext(uiThread) {
    Disposer.register(this@DatabaseInspectorControllerImpl, database.databaseConnection)
    val schema = readDatabaseSchema(database) ?: return@withContext
    addNewDatabase(database, schema)
  }

  override suspend fun runSqlStatement(database: SqliteDatabase, sqliteStatement: SqliteStatement) = withContext(uiThread) {
    openNewEvaluatorTab().evaluateSqlStatement(database, sqliteStatement).await()
  }

  override suspend fun closeDatabase(database: SqliteDatabase) = withContext(uiThread) {
    // TODO(b/143873070) when a database is closed with the close button the corresponding file is not deleted.
    if (!model.getOpenDatabases().contains(database)) return@withContext

    val openDatabases = model.getOpenDatabases()
    val tabsToClose = if (openDatabases.size == 1 && openDatabases.first() == database) {
      // close all tabs
      resultSetControllers.keys.toList()
    }
    else {
      // only close tabs associated with this database
      resultSetControllers.keys
        .filterIsInstance<TabId.TableTab>()
        .filter { it.database == database }
    }

    tabsToClose.forEach { closeTab(it) }

    val index = model.getOpenDatabases().sortedBy { it.name }.indexOf(database)
    resultSetControllers.values
      .asSequence()
      .filterIsInstance<SqliteEvaluatorController>()
      .forEach { it.removeDatabase(index) }

    view.removeDatabaseSchema(database)

    model.remove(database)

    withContext(workerThread) {
      Disposer.dispose(database.databaseConnection)
    }
  }

  @UiThread
  override fun showError(message: String, throwable: Throwable?) {
    view.reportError(message, throwable)
  }

  override fun restoreSavedState(previousState: SavedUiState?) {
    val savedState = previousState as? SavedUiStateImpl
    tabsToRestore.clear()
    savedState?.let { tabsToRestore.addAll(savedState.tabs) }
  }

  override fun saveState(): SavedUiState {
    val tabs = resultSetControllers.mapNotNull {
      when (val tabId = it.key) {
        is TabId.TableTab -> TabDescription.TableTab(tabId.database.path, tabId.tableName)
        is TabId.AdHocQueryTab -> null
      }
    }
    return SavedUiStateImpl(tabs)
  }

  override suspend fun databasePossiblyChanged() = withContext(uiThread) {
    // update schemas
    model.getOpenDatabases().forEach { updateDatabaseSchema(it) }
    // update tabs
    resultSetControllers.values.forEach { it.notifyDataMightBeStale() }
  }

  override fun dispose() = invokeAndWaitIfNeeded {
    view.removeListener(sqliteViewListener)

    resultSetControllers.values
      .asSequence()
      .filterIsInstance<SqliteEvaluatorController>()
      .forEach { it.removeListeners() }
  }

  private suspend fun readDatabaseSchema(database: SqliteDatabase): SqliteSchema? = withContext(workerThread) {
    try {
      val schema = database.databaseConnection.readSchema().await()
      withContext(uiThread) { view.stopLoading() }
      schema
    }
    catch (e: LiveInspectorException) {
      ensureActive()
      withContext(uiThread) {
        view.reportError("Error reading Sqlite database", e)
      }
      null
    }
    catch (e: Exception) {
      ensureActive()
      withContext(uiThread) {
        view.reportError("Error reading Sqlite database", e)
      }
      throw e
    }
  }

  private fun addNewDatabase(database: SqliteDatabase, sqliteSchema: SqliteSchema) {
    val index = (model.getOpenDatabases() + database).sortedBy { it.name }.indexOf(database)
    view.addDatabaseSchema(database, sqliteSchema, index)

    resultSetControllers.values
      .asSequence()
      .filterIsInstance<SqliteEvaluatorController>()
      .forEach { it.addDatabase(database, index) }

    model.add(database, sqliteSchema)
    restoreTabs(database, sqliteSchema)
  }

  private fun restoreTabs(database: SqliteDatabase, schema: SqliteSchema) {
    tabsToRestore.filter { it.databasePath == database.path }
      .also { tabsToRestore.removeAll(it) }
      .filterIsInstance<TabDescription.TableTab>()
      .mapNotNull { tabDescription -> schema.tables.find { tabDescription.tableName == it.name } }
      .forEach { openTableTab(database, it) }
  }

  private fun closeTab(tabId: TabId) {
    view.closeTab(tabId)
    val controller = resultSetControllers.remove(tabId)
    controller?.let(Disposer::dispose)
  }

  private suspend fun updateDatabaseSchema(database: SqliteDatabase) {
    // TODO(b/154733971) this only works because the suspending function is called first, otherwise we have concurrency issues
    val newSchema = readDatabaseSchema(database) ?: return
    val oldSchema = model.getDatabaseSchema(database) ?: return
    withContext(uiThread) {
      if (oldSchema != newSchema) {
        model.add(database, newSchema)
        updateExistingDatabaseSchemaView(database, oldSchema, newSchema)
        resultSetControllers.values.filterIsInstance<SqliteEvaluatorController>().forEach { it.schemaChanged(database) }
      }
    }
  }

  private fun updateExistingDatabaseSchemaView(database: SqliteDatabase, oldSchema: SqliteSchema, newSchema: SqliteSchema) {
    val diffOperations = mutableListOf<SchemaDiffOperation>()

    oldSchema.tables.forEach { oldTable ->
      val newTable = newSchema.tables.find { it.name == oldTable.name }
      if (newTable == null) {
        diffOperations.add(RemoveTable(oldTable.name))
      }
      else {
        val columnsToRemove = oldTable.columns - newTable.columns
        if (columnsToRemove.isNotEmpty()) {
          diffOperations.add(RemoveColumns(oldTable.name, columnsToRemove, newTable))
        }
      }
    }

    newSchema.tables.sortedBy { it.name }.forEachIndexed { tableIndex, newTable ->
      val indexedSqliteTable = IndexedSqliteTable(newTable, tableIndex)
      val oldTable = oldSchema.tables.firstOrNull { it.name == newTable.name }
      if (oldTable == null) {
        val indexedColumnsToAdd = newTable.columns
          .sortedBy { it.name }
          .mapIndexed { colIndex, sqliteColumn -> IndexedSqliteColumn(sqliteColumn, colIndex) }

        diffOperations.add(AddTable(indexedSqliteTable, indexedColumnsToAdd))
      }
      else if (oldTable != newTable) {
        val indexedColumnsToAdd = newTable.columns
          .sortedBy { it.name }
          .mapIndexed { colIndex, sqliteColumn -> IndexedSqliteColumn(sqliteColumn, colIndex) }
          .filterNot { oldTable.columns.contains(it.sqliteColumn) }

        diffOperations.add(AddColumns(newTable.name, indexedColumnsToAdd, newTable))
      }
    }

    try {
      view.updateDatabaseSchema(database, diffOperations)
    } catch (e: Exception) {
      view.removeDatabaseSchema(database)

      val index = model.getOpenDatabases().sortedBy { it.name }.indexOf(database)
      view.addDatabaseSchema(database, newSchema, index)
    }
  }

  private fun openNewEvaluatorTab(): SqliteEvaluatorController {
    evaluatorTabCount += 1

    val tabId = TabId.AdHocQueryTab()

    val sqliteEvaluatorView = viewFactory.createEvaluatorView(
      project,
      object : SchemaProvider { override fun getSchema(database: SqliteDatabase) = model.getDatabaseSchema(database) },
      viewFactory.createTableView()
    )

    view.openTab(tabId, "New Query [$evaluatorTabCount]", sqliteEvaluatorView.component)

    val sqliteEvaluatorController = SqliteEvaluatorController(
      project,
      sqliteEvaluatorView,
      viewFactory,
      { closeTab(tabId) },
      edtExecutor,
      taskExecutor
    )
    Disposer.register(project, sqliteEvaluatorController)
    sqliteEvaluatorController.setUp()

    sqliteEvaluatorController.addListener(SqliteEvaluatorControllerListenerImpl())

    resultSetControllers[tabId] = sqliteEvaluatorController

    model.getOpenDatabases().sortedBy { it.name }.forEachIndexed { index, sqliteDatabase ->
      sqliteEvaluatorController.addDatabase(sqliteDatabase, index)
    }

    return sqliteEvaluatorController
  }

  @UiThread
  private fun openTableTab(database: SqliteDatabase, table: SqliteTable) {
    val tabId = TabId.TableTab(database, table.name)
    if (tabId in resultSetControllers) {
      view.focusTab(tabId)
      return
    }

    val tableView = viewFactory.createTableView()
    view.openTab(tabId, table.name, tableView.component)

    val tableController = TableController(
      closeTabInvoked = { closeTab(tabId) },
      project = project,
      view = tableView,
      tableSupplier = { model.getDatabaseSchema(database)?.tables?.firstOrNull{ it.name == table.name } },
      databaseConnection = database.databaseConnection,
      sqliteStatement = createSqliteStatement(project, selectAllAndRowIdFromTable(table)),
      edtExecutor = edtExecutor,
      taskExecutor = taskExecutor
    )
    Disposer.register(project, tableController)
    resultSetControllers[tabId] = tableController

    tableController.setUp().addCallback(edtExecutor, object : FutureCallback<Unit> {
      override fun onSuccess(result: Unit?) {
      }

      override fun onFailure(t: Throwable) {
        view.reportError("Error reading Sqlite table \"${table.name}\"", t)
        closeTab(tabId)
      }
    })
  }

  private inner class SqliteViewListenerImpl : DatabaseInspectorView.Listener {

    /** [CoroutineScope] used for scheduling asynchronous tasks in response to UI events. */
    private val scope = AndroidCoroutineScope(this@DatabaseInspectorControllerImpl)

    override fun tableNodeActionInvoked(database: SqliteDatabase, table: SqliteTable) {
      databaseInspectorAnalyticsTracker.trackStatementExecuted(
        AppInspectionEvent.DatabaseInspectorEvent.StatementContext.SCHEMA_STATEMENT_CONTEXT
      )
      openTableTab(database, table)
    }

    override fun openSqliteEvaluatorTabActionInvoked() {
      openNewEvaluatorTab()
    }

    override fun closeTabActionInvoked(tabId: TabId) {
      closeTab(tabId)
    }

    override fun reDownloadDatabaseFileActionInvoked(database: FileSqliteDatabase) {
      val downloadFuture = DatabaseInspectorProjectService.getInstance(project).reDownloadAndOpenFile(database, object : DownloadProgress {
        override val isCancelled: Boolean
          get() = false

        override fun onStarting(entryFullPath: String) {
          view.reportSyncProgress("${entryFullPath.trimMiddle(20, true)}: start sync")
        }

        override fun onProgress(entryFullPath: String, currentBytes: Long, totalBytes: Long) {
          view.reportSyncProgress("${entryFullPath.trimMiddle(20, true)}: sync progress $currentBytes/$totalBytes")
        }

        override fun onCompleted(entryFullPath: String) {
          view.reportSyncProgress("${entryFullPath.trimMiddle(20, true)}: sync completed")
        }
      })

      downloadFuture.transform(edtExecutor) {
        view.reportSyncProgress("")
      }
    }

    override fun refreshAllOpenDatabasesSchemaActionInvoked() {
      databaseInspectorAnalyticsTracker.trackTargetRefreshed(AppInspectionEvent.DatabaseInspectorEvent.TargetType.SCHEMA_TARGET)
      scope.launch(uiThread) {
        model.getOpenDatabases().forEach { updateDatabaseSchema(it) }
      }
    }
  }

  inner class SqliteEvaluatorControllerListenerImpl : SqliteEvaluatorController.Listener {
    private val scope = AndroidCoroutineScope(this@DatabaseInspectorControllerImpl)

    override fun onSqliteStatementExecuted(database: SqliteDatabase) {
      scope.launch(uiThread) {
        updateDatabaseSchema(database)
      }
    }
  }

  private class SavedUiStateImpl(val tabs: List<TabDescription>) : SavedUiState

  @UiThread
  private sealed class TabDescription(val databasePath: String) {
    class TableTab(path: String, val tableName: String) : TabDescription(path)
  }
}

/**
 * Interface that defines the contract of a SqliteController.
 */
interface DatabaseInspectorController : Disposable {
  val component: JComponent

  @UiThread
  fun setUp()

  /**
   * Waits for [deferredDatabase] to be completed and adds it to the inspector UI.
   *
   * A loading UI is displayed while waiting for [deferredDatabase] to be ready.
   */
  suspend fun addSqliteDatabase(deferredDatabase: Deferred<SqliteDatabase>)

  /**
   * Adds a database that is immediately ready
   */
  suspend fun addSqliteDatabase(database: SqliteDatabase)

  suspend fun runSqlStatement(database: SqliteDatabase, sqliteStatement: SqliteStatement)
  suspend fun closeDatabase(database: SqliteDatabase)

  /**
   * Updates schema of all open databases and notifies each tab that its data might be stale.
   *
   * This method is called when a `DatabasePossiblyChanged` event is received from the the on-device inspector
   * which tells us that the data in a database might have changed (schema, tables or both).
   */
  suspend fun databasePossiblyChanged()

  /**
   * Shows the error in the view.
   */
  @UiThread
  fun showError(message: String, throwable: Throwable?)

  @UiThread
  fun restoreSavedState(previousState: SavedUiState?)

  @UiThread
  fun saveState(): SavedUiState

  /**
   * Model for DatabaseInspectorController. Used to store and access currently open [SqliteDatabase]s and their [SqliteSchema]s.
   * Implementations of this interface can be accessed from different threads, therefore should be thread-safe.
   */
  interface Model {
    @AnyThread
    fun getOpenDatabases(): List<SqliteDatabase>
    @AnyThread
    fun getDatabaseSchema(database: SqliteDatabase): SqliteSchema?

    @AnyThread
    fun add(database: SqliteDatabase, sqliteSchema: SqliteSchema)
    @AnyThread
    fun remove(database: SqliteDatabase)

    @AnyThread
    fun addListener(modelListener: Listener)
    @AnyThread
    fun removeListener(modelListener: Listener)

    interface Listener {
      @AnyThread
      fun onDatabaseAdded(database: SqliteDatabase)
      @AnyThread
      fun onDatabaseRemoved(database: SqliteDatabase)
    }
  }

  interface TabController : Disposable {
    val closeTabInvoked: () -> Unit
    /**
     * Triggers a refresh operation in this tab.
     * If called multiple times in sequence, this method is re-executed only once the future from the first invocation completes.
     * While the future of the first invocation is not completed, the future from the first invocation is returned to following invocations.
     */
    fun refreshData(): ListenableFuture<Unit>

    /**
     * Notify this tab that its data might be stale.
     */
    fun notifyDataMightBeStale()
  }

  /**
   * Marker interface for opaque object that has UI state that should be restored once DatabaseInspector is reconnected.
   */
  interface SavedUiState
}

sealed class TabId {
  data class TableTab(val database: SqliteDatabase, val tableName: String) : TabId()
  class AdHocQueryTab : TabId()
}