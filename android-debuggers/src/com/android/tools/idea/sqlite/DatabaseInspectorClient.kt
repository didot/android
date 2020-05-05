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

import androidx.sqlite.inspection.SqliteInspectorProtocol
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.sqlite.databaseConnection.live.LiveDatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.live.getErrorMessage
import com.android.tools.idea.sqlite.model.LiveSqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.Executor

/**
 * Class used to receive asynchronous events from the on-device inspector.
 * @param messenger Communication channel with the on-device inspector.
 * @param onErrorEventListener Function called when a ErrorOccurred event is received.
 * @param onDatabaseAddedListener Function called when a DatabaseOpened event is received.
 * @param taskExecutor to parse responses from on-device inspector
 * @param errorsSideChannel side channel to error logging
 */
class DatabaseInspectorClient constructor(
  messenger: CommandMessenger,
  private val onErrorEventListener: (errorMessage: String) -> Unit,
  private val onDatabaseAddedListener: (SqliteDatabase) -> Unit,
  private val onDatabasePossiblyChanged: () -> Unit,
  private val taskExecutor: Executor,
  errorsSideChannel: ErrorsSideChannel = {}
) : AppInspectorClient(messenger) {

  private val dbMessenger = DatabaseInspectorMessenger(messenger, taskExecutor, errorsSideChannel)

  override val rawEventListener = object : RawEventListener {
    override fun onRawEvent(eventData: ByteArray) {
      val event = SqliteInspectorProtocol.Event.parseFrom(eventData)
      when {
        event.hasDatabaseOpened() -> {
          val openedDatabase = event.databaseOpened
          ApplicationManager.getApplication().invokeLater {
            val connection = LiveDatabaseConnection(dbMessenger, openedDatabase.databaseId, taskExecutor)
            onDatabaseAddedListener(
              LiveSqliteDatabase(SqliteDatabaseId.fromLiveDatabase(openedDatabase.name, openedDatabase.databaseId), connection)
            )
          }
        }
        event.hasDatabasePossiblyChanged() -> {
          onDatabasePossiblyChanged()
        }
        event.hasErrorOccurred() -> {
          val errorContent = event.errorOccurred.content
          val errorMessage = getErrorMessage((errorContent))
          onErrorEventListener(errorMessage)
        }
      }
    }
  }

  /**
   * Sends a command to the on-device inspector to start looking for database connections.
   * When the on-device inspector discovers a connection, it sends back an asynchronous databaseOpen event.
   */
  fun startTrackingDatabaseConnections() {
    dbMessenger.sendCommand(
      SqliteInspectorProtocol.Command.newBuilder()
        .setTrackDatabases(SqliteInspectorProtocol.TrackDatabasesCommand.getDefaultInstance())
        .build()
    )
  }
}