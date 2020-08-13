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

package com.android.tools.idea.sqlite

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.sqlite.controllers.DatabaseInspectorController
import com.android.tools.idea.sqlite.controllers.DatabaseInspectorControllerImpl
import com.android.tools.idea.sqlite.databaseConnection.jdbc.openJdbcDatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.live.LiveDatabaseConnection
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.DatabaseInspectorModel
import com.android.tools.idea.sqlite.model.DatabaseInspectorModelImpl
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.repository.DatabaseRepositoryImpl
import com.android.tools.idea.sqlite.ui.DatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.ui.DatabaseInspectorViewsFactoryImpl
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.ide.PooledThreadExecutor
import java.io.IOException
import java.util.concurrent.Executor
import javax.swing.JComponent

/**
 * Intellij Project Service that holds the reference to the [DatabaseInspectorControllerImpl].
 */
interface DatabaseInspectorProjectService {
  companion object {
    @JvmStatic fun getInstance(project: Project): DatabaseInspectorProjectService {
      return ServiceManager.getService(project, DatabaseInspectorProjectService::class.java)
    }
  }

  /**
   * [JComponent] that contains the view of the Database Inspector.
   */
  val sqliteInspectorComponent: JComponent

  /**
   * The base coroutine scope for this [DatabaseInspectorProjectService].
   */
  val projectScope: CoroutineScope

  /**
   * Opens a connection to the database contained in the file passed as argument. The database is then shown in the Database Inspector.
   */
  @AnyThread
  fun openSqliteDatabase(databaseFileData: DatabaseFileData): ListenableFuture<SqliteDatabaseId.FileSqliteDatabaseId>

  /**
   * Shows the given database in the inspector
   */
  @AnyThread
  fun openSqliteDatabase(databaseId: SqliteDatabaseId, databaseConnection: LiveDatabaseConnection) : ListenableFuture<Unit>

  /**
   * Runs the query passed as argument in the Sqlite Inspector.
   */
  @UiThread
  fun runSqliteStatement(databaseId: SqliteDatabaseId, sqliteStatement: SqliteStatement)

  /**
   * Returns true if the Sqlite Inspector has an open database, false otherwise.
   */
  @AnyThread
  fun hasOpenDatabase(): Boolean

  /**
   * Returns a list of the currently open databases.
   */
  @AnyThread
  fun getOpenDatabases(): List<SqliteDatabaseId>

  /**
   * Shows the error in the Database Inspector.
   *
   * This method is used to handle asynchronous errors from the on-device inspector.
   * An on-device inspector can send an error as a response to a command (synchronous) or as an event (asynchronous).
   * When detected, synchronous errors are thrown as exceptions so that they become part of the usual flow for errors:
   * they cause the futures to fail and are shown in the views.
   * Asynchronous errors are delivered to this method that takes care of showing them.
   */
  @AnyThread
  fun handleError(message: String, throwable: Throwable?)

  /**
   * Called when a `DatabasePossiblyChanged` event is received from the on-device inspector.
   * Which tells us that the data in a database might have changed (schema, tables or both).
   */
  @AnyThread
  fun databasePossiblyChanged()

  /**
   * IDE services useful for interacting with the app inspection tool window that contains the Database Inspector.
   */
  fun getIdeServices(): AppInspectionIdeServices?

  /**
   * Called when Database Inspector is connected to new process.
   *
   * @param previousState state of UI from previous session if available
   */
  @UiThread
  suspend fun startAppInspectionSession(
    databaseInspectorClientCommandsChannel: DatabaseInspectorClientCommandsChannel,
    appInspectionIdeServices: AppInspectionIdeServices
  )

  /**
   * Called when Database Inspector is disconnected from app,
   * takes as argument the [ProcessDescriptor] of the process that has been disconnected.
   * @return an object that describes state of UI on this moment. This object can be passed later in [startAppInspectionSession]
   */
  @UiThread
  fun stopAppInspectionSession(processDescriptor: ProcessDescriptor)

  @UiThread
  fun handleDatabaseClosed(databaseId: SqliteDatabaseId)
}

class DatabaseInspectorProjectServiceImpl @NonInjectable @TestOnly constructor(
  private val project: Project,
  private val edtExecutor: Executor = EdtExecutorService.getInstance(),
  private val taskExecutor: Executor = PooledThreadExecutor.INSTANCE,
  private val databaseRepository: DatabaseRepositoryImpl = DatabaseRepositoryImpl(project, taskExecutor),
  private val viewFactory: DatabaseInspectorViewsFactory = DatabaseInspectorViewsFactoryImpl(),
  private val offlineDatabaseManager: OfflineDatabaseManager = OfflineDatabaseManagerImpl(project),
  private val model: DatabaseInspectorModel = DatabaseInspectorModelImpl(),
  private val createController: (DatabaseInspectorModel, DatabaseRepositoryImpl, OfflineDatabaseManager) -> DatabaseInspectorController =
    { myModel, myRepository, myOfflineDatabaseManager ->
      DatabaseInspectorControllerImpl(
        project,
        myModel,
        myRepository,
        viewFactory,
        myOfflineDatabaseManager,
        edtExecutor,
        taskExecutor
      ).also {
        it.setUp()
        Disposer.register(project, it)
      }
    }
) : DatabaseInspectorProjectService {

  @NonInjectable
  @TestOnly
  constructor(project: Project, edtExecutor: Executor, taskExecutor: Executor, viewFactory: DatabaseInspectorViewsFactory) : this (
    project,
    edtExecutor,
    taskExecutor,
    DatabaseRepositoryImpl(project, taskExecutor),
    viewFactory,
    OfflineDatabaseManagerImpl(project),
    DatabaseInspectorModelImpl(),
    { myModel, myRepository, myOfflineDatabaseManager ->
      DatabaseInspectorControllerImpl(
        project,
        myModel,
        myRepository,
        viewFactory,
        myOfflineDatabaseManager,
        edtExecutor,
        taskExecutor
      ).also {
        it.setUp()
        Disposer.register(project, it)
      }
    }
  )

  constructor(project: Project) : this (
    project,
    EdtExecutorService.getInstance(),
    PooledThreadExecutor.INSTANCE,
    DatabaseInspectorViewsFactoryImpl()
  )

  private val uiThread = edtExecutor.asCoroutineDispatcher()
  private val workerThread = taskExecutor.asCoroutineDispatcher()
  override val projectScope = AndroidCoroutineScope(project, uiThread)

  private val controller: DatabaseInspectorController by lazy @UiThread {
    ApplicationManager.getApplication().assertIsDispatchThread()
    createController(model, databaseRepository, offlineDatabaseManager)
  }

  private var ideServices: AppInspectionIdeServices? = null

  private var downloadOfflineDatabases: Job? = null

  override val sqliteInspectorComponent
    @UiThread get() = controller.component

  @AnyThread
  override fun openSqliteDatabase(
    databaseFileData: DatabaseFileData
  ): ListenableFuture<SqliteDatabaseId.FileSqliteDatabaseId> = projectScope.future {
    val databaseId = async {
      val databaseConnection = openJdbcDatabaseConnection(project, databaseFileData.mainFile, taskExecutor, workerThread)
      val databaseId = SqliteDatabaseId.fromFileDatabase(databaseFileData)

      databaseRepository.addDatabaseConnection(databaseId, databaseConnection)
      databaseId
    }

    controller.addSqliteDatabase(databaseId)
    databaseId.await() as SqliteDatabaseId.FileSqliteDatabaseId
  }

  @AnyThread
  override fun openSqliteDatabase(
    databaseId: SqliteDatabaseId,
    databaseConnection: LiveDatabaseConnection
  ): ListenableFuture<Unit> = projectScope.future {
    databaseRepository.addDatabaseConnection(databaseId, databaseConnection)
    controller.addSqliteDatabase(databaseId)
  }

  @UiThread
  override suspend fun startAppInspectionSession(
    databaseInspectorClientCommandsChannel: DatabaseInspectorClientCommandsChannel,
    appInspectionIdeServices: AppInspectionIdeServices
  ) = withContext(uiThread) {
    withContext(workerThread) { downloadOfflineDatabases?.cancelAndJoin() }
    // close all databases when a new session starts
    model.getOpenDatabaseIds().forEach { controller.closeDatabase(it) }
    model.clearDatabases()

    ideServices = appInspectionIdeServices
    controller.startAppInspectionSession(databaseInspectorClientCommandsChannel, appInspectionIdeServices)
  }

  @UiThread
  override fun stopAppInspectionSession(processDescriptor: ProcessDescriptor) {
    ideServices = null
    controller.stopAppInspectionSession()

    downloadOfflineDatabases = projectScope.launch {
      val openDatabases = model.getOpenDatabaseIds()

      openDatabases.forEach {
        controller.closeDatabase(it)
      }
      model.clearDatabases()

      if (DatabaseInspectorFlagController.isOfflineModeEnabled) {
        for (liveSqliteDatabaseId in openDatabases.filterIsInstance<SqliteDatabaseId.LiveSqliteDatabaseId>()) {
          try {
            val databaseFileData = offlineDatabaseManager.loadDatabaseFileData(processDescriptor, liveSqliteDatabaseId)
            openSqliteDatabase(databaseFileData).await()
          }
          catch (e: IOException) {
            handleError("Can't open offline database `${liveSqliteDatabaseId.path}`", e)
          }
        }
      }
    }
  }

  @UiThread
  override fun handleDatabaseClosed(databaseId: SqliteDatabaseId) {
    model.removeDatabaseSchema(databaseId)
  }

  @UiThread
  override fun runSqliteStatement(databaseId: SqliteDatabaseId, sqliteStatement: SqliteStatement) {
    projectScope.launch { controller.runSqlStatement(databaseId, sqliteStatement) }
  }

  @UiThread
  override fun hasOpenDatabase() = model.getOpenDatabaseIds().isNotEmpty()

  @UiThread
  override fun getOpenDatabases(): List<SqliteDatabaseId> = model.getOpenDatabaseIds()

  @AnyThread
  override fun handleError(message: String, throwable: Throwable?) {
    invokeAndWaitIfNeeded {
      controller.showError(message, throwable)
    }
  }

  @AnyThread
  override fun databasePossiblyChanged() {
    projectScope.launch { controller.databasePossiblyChanged() }
  }

  override fun getIdeServices(): AppInspectionIdeServices? {
    return ideServices
  }
}