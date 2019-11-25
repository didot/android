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

import com.android.tools.idea.appinspection.api.AppInspectionPipelineConnection
import com.android.tools.idea.appinspection.api.AppInspectorClient
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.transport.DeployableFile
import com.android.tools.sql.protocol.SqliteInspection
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.ide.PooledThreadExecutor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class DatabaseInspectorClientTest : PlatformTestCase() {
  private lateinit var databaseInspectorClient: DatabaseInspectorClient
  private lateinit var mockMessenger: AppInspectorClient.CommandMessenger
  private lateinit var mockDatabaseInspectorProjectService: DatabaseInspectorProjectService

  private val taskExecutor: FutureCallbackExecutor = FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE)

  override fun setUp() {
    super.setUp()

    mockDatabaseInspectorProjectService = mock(DatabaseInspectorProjectService::class.java)
    mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)

    databaseInspectorClient = DatabaseInspectorClient.createDatabaseInspectorClient(
      mockDatabaseInspectorProjectService,
      mockMessenger
    )
  }

  fun testLaunchSendsTrackDatabasesCommand() {
    // Prepare
    val mockPipelineConnection = object : AppInspectionPipelineConnection {
      override fun <T : AppInspectorClient> launchInspector(
        inspectorId: String,
        inspectorJar: DeployableFile,
        creator: (AppInspectorClient.CommandMessenger) -> T
      ): ListenableFuture<T> {
        return Futures.immediateFuture(creator(mockMessenger))
      }
    }

    val trackDatabasesCommand = SqliteInspection.Commands.newBuilder()
      .setTrackDatabases(SqliteInspection.TrackDatabasesCommand.getDefaultInstance())
      .build()
      .toByteArray()

    // Act
    pumpEventsAndWaitForFuture(
      DatabaseInspectorClient.launchInspector(mockDatabaseInspectorProjectService, mockPipelineConnection, taskExecutor)
    )

    // Assert
    verify(mockMessenger).sendRawCommand(trackDatabasesCommand)
  }

  fun testOnDatabaseOpenedEventOpensDatabase() {
    // Prepare
    val databaseOpenEvent = SqliteInspection.DatabaseOpenedEvent.newBuilder().setId(1).setName("name").build()
    val event = SqliteInspection.Events.newBuilder().setDatabaseOpen(databaseOpenEvent).build()

    // Act
    databaseInspectorClient.eventListener.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockDatabaseInspectorProjectService).openSqliteDatabase(mockMessenger, 1, "name")
  }
}