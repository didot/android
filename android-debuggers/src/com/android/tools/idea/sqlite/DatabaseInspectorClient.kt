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

import com.android.tools.idea.appinspection.api.AppInspectionTarget
import com.android.tools.idea.appinspection.api.AppInspectorClient
import com.android.tools.idea.appinspection.ide.AppInspectionClientsService
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.transport.DeployableFile
import com.android.tools.sql.protocol.SqliteInspection
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.ide.PooledThreadExecutor
import java.util.concurrent.Executor

/**
 * Class used to send and receive messages from an on-device inspector instance.
 */
class DatabaseInspectorClient private constructor(
  private val databaseInspectorProjectService: DatabaseInspectorProjectService,
  messenger: CommandMessenger
) : AppInspectorClient(messenger) {

  companion object {
    private const val inspectorId = "appinspector.sqlite"
    private val deployableFile = DeployableFile.Builder("live_sql_viewer_dex.jar")
      .setDevDir("../../bazel-bin/tools/base/experimental/live-sql-inspector")
      .build()

    /**
     * Starts listening for the creation of new connections to a device.
     */
    fun startListeningForPipelineConnections(databaseInspectorProjectService: DatabaseInspectorProjectService, taskExecutor: Executor) {
      AppInspectionClientsService.discovery.addTargetListener(PooledThreadExecutor.INSTANCE) { target ->
        launch(databaseInspectorProjectService, target, FutureCallbackExecutor.wrap(taskExecutor))
      }
    }

    /**
     * Starts the database inspector in the connected app and begins tracking open databases.
     */
    private fun launch(
      databaseInspectorProjectService: DatabaseInspectorProjectService,
      target: AppInspectionTarget,
      taskExecutor: FutureCallbackExecutor
    ): ListenableFuture<DatabaseInspectorClient> {
      val launchInspectorFuture = target.launchInspector(inspectorId, deployableFile) { messenger ->
        DatabaseInspectorClient(databaseInspectorProjectService, messenger)
      }

      taskExecutor.catching(launchInspectorFuture, Throwable::class.java) {
        // TODO databaseInspectorProjectService.showError(message)
        it
      }

      return taskExecutor.transform(launchInspectorFuture) { it.trackDatabases(); it }
    }

    @VisibleForTesting
    fun createDatabaseInspectorClient(
      databaseInspectorProjectService: DatabaseInspectorProjectService,
      messenger: CommandMessenger
    ): DatabaseInspectorClient {
      return DatabaseInspectorClient(databaseInspectorProjectService, messenger)
    }

    @VisibleForTesting
    fun launchInspector(
      databaseInspectorProjectService: DatabaseInspectorProjectService,
      target: AppInspectionTarget,
      taskExecutor: FutureCallbackExecutor
    ): ListenableFuture<DatabaseInspectorClient> {
      return launch(databaseInspectorProjectService, target, taskExecutor)
    }
  }

  override val eventListener: EventListener = object : EventListener {
    override fun onRawEvent(eventData: ByteArray) {
      val event = SqliteInspection.Events.parseFrom(eventData)
      when {
        event.hasDatabaseOpen() -> {
          val openedDatabase = event.databaseOpen
          ApplicationManager.getApplication().invokeLater {
            databaseInspectorProjectService.openSqliteDatabase(messenger, openedDatabase.id, openedDatabase.name)
          }
        }
        event.hasTableUpdate() -> {
          TODO()
        }
      }
    }

    override fun onCrashEvent(message: String) {
      // TODO databaseInspectorProjectService.showError(message)
    }

    override fun onDispose() {
      // TODO databaseInspectorProjectService.closeAllLiveDatabase()
    }
  }

  /**
   * Tells on-device inspector to start looking for database connections. When a connection is discovered a databaseOpen event is sent.
   */
  private fun trackDatabases() {
    messenger.sendRawCommand(
      SqliteInspection.Commands.newBuilder()
        .setTrackDatabases(SqliteInspection.TrackDatabasesCommand.getDefaultInstance())
        .build()
        .toByteArray()
    )

    // TODO(blocked) error handling.
  }
}